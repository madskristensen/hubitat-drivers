# Skill: hubitat-parsejson-guards

**Confidence:** medium
**Validated:** 2026-05-18 — PurpleAir AQI v0.3.0 (`search_coords` blank crash + empty async JSON body guard)
**Author:** Tank

---

## Problem

Hubitat's `parseJson(String)` throws when the input string is null, empty, or whitespace. `AsyncResponse.getJson()` can fail the same way when the remote endpoint returns an empty body or non-JSON payload.

Because these failures happen inside driver lifecycle or callback code, one blank preference value can abort `refresh()` and one empty HTTP 200 body can kill the parse path.

---

## Rule

- Never call `parseJson(text)` until `text?.trim()` is non-empty.
- After parsing, validate the expected shape (`List` vs `Map`, required positions/keys, numeric leaf values).
- In async callbacks, wrap `getJson()` in `try/catch`, inspect the raw body, and `log.warn` + `return` on empty/invalid JSON.
- Do **not** silently fall back to a different data source when a JSON preference is blank; warn and keep the current driver state.

---

## Preference-backed JSON Pattern

```groovy
private Float[] parseSearchCoords() {
    if (!settings.search_coords?.trim()) {
        log.warn "[refresh] search_coords is empty — geolocation search requires lat/lng input"
        return null
    }
    try {
        def coords = parseJson(settings.search_coords)
        if (!(coords instanceof List) || coords.size() < 2) {
            log.warn "[refresh] search_coords must be JSON [lat, lng]"
            return null
        }
        String lat = coords[0]?.toString()
        String lng = coords[1]?.toString()
        if (!lat?.isNumber() || !lng?.isNumber()) {
            log.warn "[refresh] search_coords must be JSON [lat, lng]"
            return null
        }
        return [lat.toFloat(), lng.toFloat()] as Float[]
    } catch (Exception e) {
        log.warn "[refresh] search_coords must be JSON [lat, lng]"
        return null
    }
}
```

---

## Async JSON Callback Pattern

```groovy
private Map safeResponseJson(hubitat.scheduling.AsyncResponse resp) {
    try {
        def parsed = resp?.getJson()
        if (parsed instanceof Map) {
            return parsed as Map
        }
    } catch (Exception e) {
        String body = resp?.getData()?.toString()
        if (!body?.trim()) {
            log.warn "[callback] endpoint returned an empty response body"
        } else {
            log.warn "[callback] endpoint returned invalid JSON: ${e.message}"
        }
        return null
    }
    return null
}
```

Use the helper before touching `fields`, `data`, or nested keys.

---

## When to Use This Skill

- JSON text preferences (`search_coords`, header maps, endpoint catalogs, config blobs)
- Cloud or LAN drivers using `asynchttpGet` / `asynchttpPost`
- Any Hubitat parse path where the upstream endpoint may reply with HTML, an empty body, or a transient maintenance page

---

## Reference

- `drivers/purpleair-aqi/purpleair-aqi.groovy` v0.3.0 — blank `search_coords` guard + async empty-body guard
- Tank learning update in `.squad/agents/tank/history.md` (2026-05-18)
