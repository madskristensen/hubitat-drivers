# Skill: hubitat-hubaction-constructors

**Confidence:** medium  
**Validated:** 2026-05-18 — two independent firmware failures observed in daikin-wifi v0.1.1 (c28882f) and v0.1.2  
**Author:** Tank

---

## Summary

All Map-based `hubitat.device.HubAction` constructors are **unreliable across firmware versions** and have been observed failing on real user hardware. Both the 3-arg `(Map, Protocol, Map)` and 2-arg `(Map, Protocol)` forms throw `Could not find matching constructor` at runtime on current Hubitat firmware.

**Recommendation: Use `asynchttpGet` for HTTP-over-LAN instead.** See skill `hubitat-asynchttpget-pattern` for the canonical pattern.

---

## Observed Failures

| Constructor | Error |
|---|---|
| `HubAction(Map, Protocol, Map)` | `Could not find matching constructor for: hubitat.device.HubAction(java.util.LinkedHashMap, hubitat.device.Protocol, java.util.LinkedHashMap)` |
| `HubAction(Map, Protocol)` | `Could not find matching constructor for: hubitat.device.HubAction(java.util.LinkedHashMap, hubitat.device.Protocol)` |

Both forms were attempted in daikin-wifi (v0.1.1 → v0.1.2) and rejected on Mads's hardware. Two iterations of constructor-guessing failed. The Map-based API is broken on current firmware.

---

## String-based HubAction (may still work)

The String-based overloads are separate codepaths and have not been confirmed broken:

| Signature | Notes |
|---|---|
| `HubAction(String action)` | Simple string command |
| `HubAction(String action, Protocol protocol)` | String + protocol |
| `HubAction(String action, Protocol protocol, String dni)` | String + protocol + DNI |
| `HubAction(String action, Protocol protocol, String dni, Map options)` | Full 4-arg string form |

However, for HTTP GET/POST requests to LAN devices, `asynchttpGet` / `asynchttpPost` is the documented modern API and preferred over any HubAction form.

---

## Preferred Pattern

```groovy
// ✅ Use asynchttpGet for HTTP-over-LAN
private void sendGet(String path, String callbackMethod) {
    Map params = [
        uri         : "http://${settings.ip}${path}",
        timeout     : 10,
        contentType : "text/plain"
    ]
    asynchttpGet(callbackMethod, params, [path: path])
}
```

See skill `hubitat-asynchttpget-pattern` for the full callback pattern.

---

## DNI Note

With `asynchttpGet`, the DNI is **not required** for response routing — the full URI is in `params.uri`. Contrast with HubAction (LAN), which required the DNI to be set to the hex-encoded IP.

---

## References

- daikin-wifi v0.1.1 — 3-arg failure, commit c28882f
- daikin-wifi v0.1.2 — 2-arg failure, asynchttpGet fix, `drivers/daikin-wifi/daikin-wifi.groovy` lines 409–422
- Decision drop: `.squad/decisions/inbox/tank-daikin-wifi-v012-asynchttp.md`
