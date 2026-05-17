# Skill: Hubitat State Hygiene

**Confidence:** medium  
**Source:** gemstone-lights.groovy v0.4.6

## Problem

Hubitat surfaces **every `state.*` key** on the device page's "State Variables" panel. A driver that caches a full device-side catalog (50–150 patterns, each a multi-field JSON blob) fills that panel with kilobytes of noise. Users can't find the handful of entries that actually matter.

## Rule: Cap persistent state to the curated subset

Avoid caching large device-wide datasets in `state`. Reserve `state` for the **small curated set the user actually interacts with** (e.g., favorites). For non-curated items, prefer in-memory local variables or on-demand-fetch patterns — the data is used once and discarded without ever writing to `state`.

```groovy
// ✅ Favorites only → cached in state (small, user-curated)
favorites.each { displayName, patternId ->
    effectCatalog[displayName] = patternId
    effectPatterns[patternId] = cloneMap(catalogEntry.pattern as Map)
}
state.effectCatalog = effectCatalog    // ~5–15 entries
state.effectPatterns = effectPatterns  // ~5–15 blobs

// ✅ Non-favorites → NOT cached (collected for debug log only)
otherEntries.each { entry ->
    if (safeString(entry.displayName)) {
        otherNames << safeString(entry.displayName)
    }
}
// otherNames is logged at debug level; nothing written to state
```

## On-Demand Resolution for Non-Curated Items

When a command references a non-curated item by name:

1. Queue the request as a pending "name" request.
2. Trigger a catalog refresh (async HTTP fetch).
3. In the catalog-finalize callback, while the full catalog data is still in local variables, resolve the pending non-curated name from the in-memory data.
4. Play/use it directly — **do NOT write to `state.effectCatalog` or `state.effectPatterns`**.

```groovy
// finalizeEffectCatalogRefresh() — after building favorites-only state:
private void processPendingNonFavoriteNameRequests(Map mergedEntries, Map favoriteCatalog) {
    // for each pending "name" request NOT in favoriteCatalog:
    //   look up in mergedEntries (local, not state)
    //   if found → activateEffectWithPattern(patternId, displayName, pattern)  [no state write]
    //   if not found → log.error
    // remaining (favorites + non-name requests) → normal processPendingEffectRequests()
}
```

## Upgrade Cleanup

When driver version bumps, existing installs may have bloated `state.effectCatalog` / `state.effectPatterns` from the old version. Add a `pruneNonFavoriteStateEntries()` helper and call it from `updated()` and `initialize()`:

```groovy
private void pruneNonFavoriteStateEntries() {
    Map favorites = (state.favorites instanceof Map) ? state.favorites as Map : [:]
    Set favoritePatternIds = favorites.values() as Set
    Set favoriteNames = favorites.keySet() as Set

    if (state.effectCatalog instanceof Map) {
        Map cat = state.effectCatalog as Map
        cat.keySet().retainAll(favoriteNames)
    }
    if (state.effectPatterns instanceof Map) {
        Map pats = state.effectPatterns as Map
        pats.keySet().retainAll(favoritePatternIds)
    }
}
```

- Calling from `updated()` prunes before the full catalog clear+reload cycle (belt-and-suspenders).
- Calling from `initialize()` prunes on hub reboot, before any new catalog fetch — ensures the State Variables panel is clean immediately.

## Summary

| Data type | Strategy | Rationale |
|-----------|----------|-----------|
| Favorites (5–15 items) | Cache in `state` | Small, user-curated, needed by UI dropdown and index-based setEffect |
| Non-favorites (50–150 items) | On-demand fetch, local variable, discard | Avoid polluting State Variables panel; still accessible by name |
| Build-time accumulators | Temporary `state` build keys, cleared after finalize | Needed only during multi-page async fetch |
