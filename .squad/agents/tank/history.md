# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — Groovy device drivers for Hubitat Elevation
- **Stack:** Groovy (Hubitat sandbox), Hubitat platform APIs
- **Created:** 2026-05-16

## Active Milestones Summary

### Touchstone Fireplace Driver (Current)

v0.1.4 shipped with optional power-on defaults + safety hardening (heater never auto-starts) + Hubitat sandbox reflection fixes. See .squad/orchestration-log/ for v0.1.3 + v0.1.4 batch details.

**Key learnings:**
- Power-on defaults: use runInMillis() for async delay (1500ms) to allow firmware settle post-power-on
- Heater safety: never auto-toggle hazardous hardware; keep behind explicit user commands
- Hubitat sandbox: blocks reflection (.getClass(), .metaClass, etc.) at runtime, not just imports
- Documentation for safety-critical features: be explicit, clear, and direct about intentional omissions

---

## 2026-05-17T19:29:40Z — Touchstone v0.1.5 paragraph() fix (App-only UI audit)

**Requested by:** Mads

### Completed

- Removed `paragraph` header from `preferences {}` block
- Moved power-on defaults explanation into per-field `description:` text
- Audited for app-only constructs (`section`, `href`, `app`, `mode`, `pageDefault`) — clean
- Bumped driver version to v0.1.5
- Consolidated Hubitat sandbox families into `.squad/skills/tuya-local-groovy/SKILL.md`

### Key Learning

Hubitat driver preferences are not the same as app preferences. Drivers should use only `input` fields; app UI helpers like `paragraph()`, `section()`, `href()`, `app()`, `mode()`, and `pageDefault()` will fail at install time in drivers and should be replaced with `description:` text on each field.

---

## 2026-05-17T19:29:40Z — Touchstone v0.1.4 shipped (Cross-Agent Batch Awareness)

**Collaborators:** Tank (2 runs), Link, Switch (test surface awareness)

### v0.1.3 + v0.1.4 are bundled in a single commit

v0.1.3 shipped optional power-on defaults (flame color, log color, flame brightness, temp setpoint, heat level). Link updated docs. Then immediately hardened v0.1.4: removed heater auto-apply per Mads's safety directive, fixed Hubitat sandbox reflection bugs. v0.1.3 was never released; users only see v0.1.4.

### Cross-Team Coverage

1. **Tank v0.1.3:** Added power-on defaults (runInMillis 1500ms delay for firmware settle window)
2. **Tank v0.1.4:** Removed defaultHeatLevel (fire/burn safety); removed 2 executable reflection calls (parse() exception logging, dpValueType() fallback)
3. **Link v0.1.4:** Updated README with Power-on Defaults + Safety sections; bumped packageManifest to v0.1.4; changelog omits v0.1.3
4. **Switch (test surface):** Aware that defaults apply ~1.5s after on(); heater never auto-toggles; v0.1.4 should install without sandbox reflection errors

### Key Decisions Captured in decisions.md

- User directive: heater must never auto-start (safety)
- Hubitat bug: sandbox rejects e.getClass() at line 449
- Documentation pattern: hardware safety > convenience; be explicit about intentional omissions

---

See history-archive.md for detailed earlier sessions (Gemstone, SunStat, Bosch feasibility).

## Learnings

- 2026-05-17T12:22:15-07:00 — Hubitat driver preferences are not the same as app preferences: drivers should use only `input` fields, and app-only UI helpers like `paragraph`, `section`, `href`, `app`, `mode`, and `pageDefault` will fail in drivers. Put explanatory copy into each input's `description:` instead.
- 2026-05-17T13:21:30-07:00 — The repo release workflow parses driver `Changelog:` entries with the regex in `.github/workflows/release.yml` line ~106, so each changelog line must use a plain `YYYY-MM-DD` date; ISO 8601 timestamps with time/offset will break release-note generation.
- 2026-05-17T15:41:32-07:00 — Cross-driver audit (Gemstone, SunStat, Touchstone) surfaced these anti-patterns to avoid in future drivers:
  1. **Synchronous HTTP on hot paths** — SunStat parent uses `httpGet/Post/Patch` (blocking) throughout polling and token refresh. With N children this stalls the hub thread for N×timeout. Always prefer `asynchttpGet/Post` for polling drivers; only use synchronous HTTP when the response is needed inline and there is no alternative (e.g. token bootstrap).
  2. **Nested blocking HTTP** — SunStat parent calls `refreshTokensSync()` (synchronous `httpPost`) inside an `httpGet` callback closure (line 487). This double-nests hub thread blocking. Token refresh triggered by 401 should be async or at minimum handled outside the callback.
  3. **`state.rxBuffer` persisted on every `parse()` call** — Touchstone writes the hex receive buffer to `state` on every incoming TCP chunk (line 479). Hubitat state writes are relatively expensive I/O; only persist the buffer when a partial frame remains after processing. Clear on next write rather than on every call.
  4. **Dead state writes** — Gemstone stores `state.idToken` (line 1121) on every Cognito auth but never reads it for any API call. `state.lastDps` in Touchstone (line 1109) is written but never read. Audit `state.*` for write-only fields; remove or make them explicitly "diagnostic only".
  5. **O(n) reverse-index scans** — `effectNameForPatternId` and `effectIndexForPatternId` in Gemstone do `.find {}` linear scans over the catalog on every effect activation. Build reverse lookup maps (patternId→name, patternId→index) at catalog finalization time to make lookups O(1).
  6. **`cloneMap` JSON round-trip overhead** — Gemstone's `cloneMap()` does `JsonSlurper().parseText(JsonOutput.toJson(source))` for every map copy (~14 call sites, hot paths). For shallow maps, `new LinkedHashMap(source)` is much faster. Reserve JSON round-trip deep-copy only for maps with nested mutable structures.
  7. **Boxed Integer in byte-copy inner loops** — Touchstone uses `for (Integer i = ...)` in `concatBytes`, `sliceBytes`, `startsWithBytes`, `protocol33HeaderBytes`. Use `int` (primitive) to avoid autoboxing. `System.arraycopy` (java.lang, sandbox-safe) is even better for bulk copies.
  8. **Guard block copy-paste** — Gemstone duplicates the same credential+catalog guard verbatim in 5 command handlers (`setEffect×2`, `setNextEffect`, `setPreviousEffect`, `refreshEffectCatalog`). Extract to a private helper to reduce maintenance surface.
  9. **`infoLog` double-negative guard** — Touchstone checks `settings.txtEnable != false` (line 1643). Prefer `settings.txtEnable == true` for clarity; a missing/null setting reads as enabled with the double-negative.
  10. **Missing `capability "Actuator"` on command-accepting parent** — SunStat parent accepts commands (setHome, setAway, setAwayMode, setRefreshToken, discoverDevices) but doesn't declare `capability "Actuator"`. Convention: any driver that accepts commands should declare Actuator.
  11. **`USER_AGENT` literal not linked to `DRIVER_VERSION`** — All three drivers hard-code the version in both `DRIVER_VERSION` and `USER_AGENT`. The sandbox prevents cross-@Field refs but a comment "keep in sync with DRIVER_VERSION" should appear on both lines (not just USER_AGENT).

---

## 2026-05-17T15:41:32Z — Cross-driver improvement scan (4-way)

Participated in 4-way driver improvement scan with Trinity, Cypher, Switch. Findings consolidated by Squad. Orchestration log: .squad/orchestration-log/2026-05-17T15-41-32-tank.md.
