# Skill: Hubitat Community Driver Fork Cleanup

**Confidence:** medium
**Source:** Honeywell T6 Pro fork (2026-05-18, Tank). Pattern will recur for any community driver that is orphaned but mostly correct.

---

## When to Use

Apply this pattern when:
- A community driver has 1–3 surgical bug fixes from a code audit (Trinity BLOCKER/MAJOR)
- The upstream maintainer is silent (>1 year no commits to that file)
- The driver is already installed on Mads's hardware (can't wait for upstream)

---

## File Layout

```
drivers/{slug}/
  {slug}.groovy          -- fixed driver
  packageManifest.json   -- HPM manifest (use daikin-wifi as template)
  README.md              -- install, what's fixed, upstream status, attribution
```

---

## Driver Header Pattern

1. Place the fork attribution block FIRST (before the original header):

```groovy
/**
 *  {Device Name} (Hubitat) - Fork
 *
 *  Fork by: Mads Kristensen - {YYYY-MM-DD}
 *  Source: github.com/{author}/{repo} ({original driver name})
 *  Forked because: maintainer silent {N}+ years, {severities and descriptions}
 *  Goal: ship the fixes as upstream PR to {author} once tested.
 *
 *  Version: 0.1.0 - {YYYY-MM-DD} - Initial fork; apply {audit name} audit fixes
 *
 *  [original {author} {license} copyright block preserved verbatim below]
 */
```

2. Preserve the original copyright/header comment verbatim immediately below.
3. Add `@Field static final String VERSION = "0.1.0"` in the constants section.

---

## Inline Fix Comments

For every changed line, add a comment citing severity and root cause:

```groovy
// FIX #N [SEVERITY]: one-line description of what was wrong
```

Example:
```groovy
// FIX #2a [MAJOR]: was device.currentValue=="cooling" (missing attribute arg - always false)
if (newstate=="idle" && (device.currentValue("thermostatOperatingState")=="heating" || ...))
```

This makes the diff self-documenting for the eventual upstream PR.

---

## packageManifest.json Template

Copy `drivers/daikin-wifi/packageManifest.json` and update:
- `packageName`: device name
- `author`: "Mads Kristensen (fork of {original author})"
- `version`: "0.1.0"
- `dateReleased`: fork date
- `drivers[0].id`: new UUID (generate randomly)
- `drivers[0].name`: original driver metadata name (keep namespace matching original)
- `drivers[0].location`: raw GitHub URL to the .groovy in this repo

---

## README.md Sections

1. **What this is** (1 para): fork rationale, original author, why forked
2. **Install**: HPM search-by-URL + manual import steps
3. **What's Fixed**: table with `# | Severity | Description` for each fix
4. **Upstream Status**: PR intent + maintainer responsiveness note
5. **Attribution**: link to original repo, MIT preservation note
6. **Changelog**: single 0.1.0 entry with date and fix summary

---

## Minimum-Change Discipline

- Apply ONLY the fixes from the audit (Trinity BLOCKER + MAJOR required; MINOR/NIT only if 1-line)
- Do NOT refactor, rename, reformat, or add features
- Do NOT change defaults or behavior beyond what the bug fix requires
- Safety net: thermostat drivers control home climate — wrong changes = physical impact

---

## Validated On

- Honeywell T6 Pro (2026-05-18): 3 fixes (txtEnable pref, currentValue arg, unschedule syncClock)

---

**Last updated:** 2026-05-18
