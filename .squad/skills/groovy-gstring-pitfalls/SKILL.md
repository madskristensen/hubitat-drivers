# SKILL: Groovy GString Pitfalls in Hubitat Drivers

**Confidence:** low  
**Domain:** Groovy / Hubitat driver development  
**First observed:** daikin-wifi v0.1.5 hotfix (2026-05-18)

---

## The triple-quote-as-typo-for-empty-string trap

### What happens

In Groovy, `"""` opens a multiline GString (triple-double-quote string). A common typo when writing an empty-string fallback:

```groovy
// WRONG — opens a multiline GString, never closed
String foo = bar ?: """

// CORRECT — empty string fallback
String foo = bar ?: ""
```

The parse error does **not** appear at the `"""` line. Groovy silently consumes everything after it as string content until it finds a matching `"""`. If none exists, the parser fails at end-of-file with an opaque error. On Hubitat, this causes the driver to be rejected at load time — the device will appear installed but non-functional with a cryptic parse failure in the logs.

### Why it's easy to miss

- The extra quote is invisible in most fonts at normal zoom.
- IDEs may not flag it immediately (the triple-quote is valid Groovy syntax).
- Code review tends to scan for logic errors, not quote counts.

### Pre-commit check

After touching any GString fallback expression (`?: "..."`) in a Groovy driver file, run:

```
grep '"""' drivers/your-driver/your-driver.groovy
```

Expected result: **zero matches** in executable code. (Changelog comments using `"""` as prose decoration are acceptable if any, but the convention in this repo is `""` for empty fallbacks.)

### Related patterns to audit

- `?: ""` — safe empty-string fallback
- `?: """` — BUG: opens multiline GString
- `?: """"` — BUG: opens multiline GString followed by one literal `"`
- `log.warn "prefix: ${value}"` — correct interpolation
- `log.warn "prefix: "` — missing interpolation (useless log message)

---

## See also

- `.squad/decisions/inbox/tank-daikin-wifi-v015-hotfix.md` — the specific bug report and fix
- `drivers/daikin-wifi/daikin-wifi.groovy` line 706 — the corrected line
