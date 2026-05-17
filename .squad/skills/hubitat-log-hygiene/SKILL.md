---
name: "hubitat-log-hygiene"
description: "Avoid flooding Hubitat's live-log view by curating catalog dumps, large lists, and error messages."
domain: "hubitat-drivers"
confidence: "high"
source: "earned"
---

## Problem

Hubitat's live-log view has limited visible rows. A driver that emits a large list (dozens of preset/effect names) on every named-effect call floods the view, hiding useful diagnostic output.

## Pattern

### 1. Discovery / Catalog-Load Dumps → count only, even at `log.debug`

When a driver loads or refreshes a lookup table (effect catalog, preset list, device inventory), emit **only the count** at `log.debug`. Never `join()` a collection of unknown size into a single log line — even at debug level, 1457 names is multi-KB of noise that floods the live log:

```groovy
// BAD — even behind log.debug this is KBs of noise with 1457 entries
log.debug "[Gemstone] Other patterns (${otherNames.size()}): ${otherNames.join(', ')}"

// GOOD — count only; names are still discoverable via the warn-on-miss path
log.debug "[Gemstone] Other patterns: ${otherCount} non-favorite patterns available by name."
```

Small, curated sets (e.g. favorites — typically < 20) may still enumerate by name at `log.debug`:

```groovy
// gemstone-lights.groovy — favorites line (small list, kept as-is)
log.debug "[Gemstone] Loaded ${count} effects. Favorites (${favoriteNames.size()}): ${favoriteNames ? favoriteNames.join(', ') : '(none)'}"
```

Also demote the "available effects" helper used in startup/refresh callbacks:

```groovy
// gemstone-lights.groovy line 1818
log.debug "[Gemstone] Available effects: ${names ? names.join(', ') : '(none loaded)'}"
```

### 2. Successful Name → ID Resolution → short `log.info`

On a successful lookup, emit a single concise info line — never the full catalog list.

```groovy
// gemstone-lights.groovy line 900 (activateEffectByPattern)
infoLog "${device.displayName} effect → ${displayEffectName(resolvedName)}"
```

### 3. Name-Not-Found Error Path → `log.warn` with cap at 20

When a lookup fails, keep showing suggestions but:
- Demote from `log.info` to `log.warn`
- Cap the list at 20 names (sorted); append `… (N more)` if truncated
- Single line, comma-separated

```groovy
// gemstone-lights.groovy lines 1821–1828
private void warnAvailableEffectNames() {
    List names = orderedEffectDisplayNames().sort()
    Integer total = names.size()
    List shown = total > 20 ? names.take(20) : names
    String suffix = total > 20 ? ", … (${total - 20} more)" : ""
    log.warn "[Gemstone] Available effects: ${shown ? shown.join(', ') + suffix : '(none loaded)'}"
}
```

Also clean up the error log line itself — don't embed the full inline join in the `log.error` string:

```groovy
// gemstone-lights.groovy line 854 (activateEffectByName miss)
log.error "[Gemstone] No effect named '${requestedName}'."
warnAvailableEffectNames()
```

## Summary Table

| Situation | Level | List? | Notes |
|-----------|-------|-------|-------|
| Catalog loaded / large collection | `log.debug` | **Count only** | Even at debug, joining 1000+ names is multi-KB noise |
| Catalog loaded / small curated set | `log.debug` | Full join OK | Only when size is known-small (e.g. favorites < 20) |
| Effect found by name | `log.info` | None | One line: name → patternId |
| Effect NOT found by name | `log.error` + `log.warn` | Capped (20) | error = "not found"; warn = "available: …" |
| Index not found | `log.error` + `log.warn` | Capped (20) | Same pattern |

## Curate UI Surfaces

**Confidence:** medium

When a device API exposes 50+ items but the user only cares about a curated subset, filter the Hubitat capability attribute (e.g., `lightEffects`) to the curated subset. Keep the full set accessible via name-based commands so power users can still address everything.

- Build and populate two separate maps: one for the UI attribute (`lightEffects`, numeric index), one for full name lookup (`effectCatalog`, `effectPatterns`).
- Only advance `nextIndex` for curated items (e.g., favorites). Non-curated items are still accessible by name via on-demand catalog fetch, but do NOT get persisted to `state.effectCatalog` or `state.effectPatterns`.
- Name-based commands (`setEffect(String)`, `playEffectByName`) resolve from the favorites cache first; on a cache miss, trigger an on-demand full catalog fetch (see hubitat-state-hygiene skill).
- The dashboard dropdown and `setEffect(NUMBER)` stay clean and short.

```groovy
// Favorites get a numeric slot (UI dropdown + setEffect(NUMBER)) AND are cached in state
favorites.each { displayName, patternId ->
    effectCatalog[displayName] = patternId
    effectPatterns[patternId] = ...
    lightEffects[nextIndex.toString()] = displayEffectName(displayName)
    effectIndex[nextIndex.toString()] = patternId
    nextIndex++
}

// Non-favorites: count only — NOT written to state, NOT joined into a log line
Integer otherCount = 0
otherEntries.each { entry ->
    if (safeString(entry.displayName)) {
        otherCount++
    }
}
log.debug "[Gemstone] Other patterns: ${otherCount} non-favorite patterns available by name."
```
