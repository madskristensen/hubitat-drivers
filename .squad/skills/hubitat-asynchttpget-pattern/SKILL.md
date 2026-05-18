# Skill: hubitat-asynchttpget-pattern

**Confidence:** medium  
**Validated:** 2026-05-18 — adopted in daikin-wifi v0.1.2 after two HubAction constructor failures  
**Author:** Tank

---

## Summary

`asynchttpGet` (and `asynchttpPost`) is the **modern, stable, documented Hubitat API for HTTP-over-LAN** on device drivers. It works on every Hubitat firmware from 2.2+, handles HTTP/1.1, parses responses, and does not suffer from `HubAction`'s constructor-signature instabilities.

**Key correction:** Early team memos claimed `asynchttpGet` was "for cloud HTTPS only." That is **incorrect**. `asynchttpGet` works for any HTTP URL including local LAN (e.g., `http://192.168.1.50/api`). Use it for all LAN HTTP polling drivers.

---

## Send Helper Pattern

```groovy
private void sendGet(String path, String callbackMethod = "parseResponse") {
    if (!settings?.ip) {
        log.warn "[Driver] Cannot send GET: no IP configured"
        return
    }

    Map params = [
        uri         : "http://${settings.ip}${path}",
        timeout     : 10,
        contentType : "text/plain"
    ]

    Map data = [path: path]

    if (logEnable) log.debug "[Driver] GET ${params.uri}"

    try {
        asynchttpGet(callbackMethod, params, data)
    } catch (Exception e) {
        log.warn "[Driver] sendGet exception (${path}): ${e.message}"
    }
}
```

**Key points:**
- `callbackMethod` is a **String** (method name). Hubitat looks it up by name.
- `params.uri` is the full URL including scheme and host — no HOST header needed.
- `data` is the optional third argument — passed verbatim to the callback as `Map data`. Use it to thread context (which endpoint, which fields you expect, etc.).
- `contentType` of `"text/plain"` is appropriate for Daikin-style `key=val,key=val` responses. Use `"application/json"` for JSON APIs.
- `timeout` is in seconds. 10 is a reasonable LAN timeout.

---

## Callback Handler Pattern

```groovy
def parseControlInfo(hubitat.scheduling.AsyncResponse response, Map data) {
    if (response == null) { log.warn "[Driver] No response from ${data?.path}"; return }
    if (response.hasError()) {
        log.warn "[Driver] HTTP error on ${data?.path}: ${response.getErrorMessage()}"
        return
    }

    String body = response.getData()   // raw body as String
    if (!body) { log.warn "[Driver] Empty body from ${data?.path}"; return }

    // For JSON APIs:
    // Map json = response.getJson()

    // Parse and emit events...
}
```

**AsyncResponse API:**

| Method | Returns | Notes |
|---|---|---|
| `hasError()` | `boolean` | `true` for network errors, timeouts, or non-2xx HTTP status |
| `getStatus()` | `int` | HTTP status code (200, 404, etc.) |
| `getErrorMessage()` | `String` | Human-readable error description |
| `getData()` | `String` | Raw response body as a String |
| `getJson()` | `Map/List` | Parsed JSON body (if `contentType` was JSON) |
| `getHeaders()` | `Map` | Response headers |

---

## Daikin BRP069B Body Parsing

The Daikin adapter responds with `text/plain` key=value pairs:

```
ret=OK,pow=1,mode=3,stemp=22.0,f_rate=A,f_dir=0,shum=0
```

Parse with a split-on-equals helper:

```groovy
private Map parseKV(String body) {
    Map result = [:]
    if (!body) { return result }
    body.split(",").each { String pair ->
        int idx = pair.indexOf("=")
        if (idx > 0) {
            result[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
        }
    }
    return result
}
```

---

## vs. HubAction (LAN)

| | `asynchttpGet` | `HubAction(Map, Protocol)` |
|---|---|---|
| URL format | Full URI in `params.uri` | `path` + `HOST` header |
| DNI required | No | Yes (hex-encoded IP) |
| Firmware stability | ✅ Stable 2.2+ | ❌ Map-based constructors broken on current firmware |
| Callback signature | `(AsyncResponse, Map)` | `(HubResponse)` (unreliable) |
| `parseLanMessage` needed | No | Yes (for raw LAN frame parsing) |

---

## References

- daikin-wifi v0.1.2 — `sendGet()` lines 409–422, `handleControlInfo()` line 448 (`drivers/daikin-wifi/daikin-wifi.groovy`)
- Decision drop: `.squad/decisions/inbox/tank-daikin-wifi-v012-asynchttp.md`
- Skill `hubitat-hubaction-constructors` — documents why HubAction Map constructors were abandoned
