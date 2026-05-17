## 2026-05-16 — SunStat locationId discovery failure diagnosis & fix

# SunStat locationId Discovery Failure — Diagnosis & Fix

**Date:** 2026-05-16T21:24:48-07:00  
**Author:** Cypher (Integration / Protocol Engineer)  
**Status:** READY FOR TANK — diagnosis complete, code changes specified

---

## Section 1 — Unblock for Mads (PRIORITY)

### Where is your locationId?

Your `tokens.json` at `C:\Users\madsk\source\repos\homebridge-tekmar-wifi\tokens.json` stores
`access_token` and `refresh_token` but **not** `locationId` — that requires a live API call.
The access_token in your tokens.json expired ~13 minutes after login; you need to refresh it first.

### Runnable script (committed to repo)

A ready-to-run PowerShell script has been placed at:

```
drivers/sunstat-thermostat/scripts/get-location-id.ps1
```

**Instructions — run from any PowerShell window:**

```powershell
pwsh "C:\Users\madsk\GitHub\hubitat-drivers\drivers\sunstat-thermostat\scripts\get-location-id.ps1"
```

The script will:
1. Read `refresh_token` from `C:\Users\madsk\source\repos\homebridge-tekmar-wifi\tokens.json`
2. Exchange it for a fresh `access_token` at the Watts B2C endpoint
3. Save the rotated tokens back to `tokens.json` (keeps your refresh session alive)
4. Call `GET https://home.watts.com/api/Location` with correct headers
5. Print every locationId and location name

**Expected output:**

```
✓ Read refresh_token from tokens.json (1660 chars)
→ Refreshing access token at Watts B2C endpoint…
✓ Got fresh access_token (1941 chars)
✓ Saved rotated tokens back to tokens.json
→ Calling GET https://home.watts.com/api/Location …

===== LOCATIONS FOUND =====
  [1] locationId : <YOUR-LOCATION-ID-HERE>
       name       : Home
       isDefault  : True
       devices    : 1
============================

ACTION: Copy the locationId above and paste it into the
        'Watts Home location ID' preference on the SunStat parent device in Hubitat.
        Then press 'discoverDevices' again.
```

Paste the printed `locationId` value into the parent device → **Preferences** → `Watts Home location ID` → Save, then press **discoverDevices**.

> **Why is this necessary?** All three auto-discovery paths in the driver are broken by a response-envelope bug (see Section 2). The manual override via the preference field works because it bypasses those paths entirely.

---

## Section 2 — Diagnosis

### The Watts API Response Envelope

Every response from `https://home.watts.com/api` is wrapped in:

```json
{
  "errorNumber": 0,
  "errorMessage": null,
  "body": <actual payload>
}
```

**Reference:** `homebridge-tekmar-wifi/src/lib/api/client.ts` lines 56–66:
```typescript
const apiResponse = response.data;
if (apiResponse.errorNumber !== 0) {
  throw new Error(apiResponse.errorMessage || `API error: ${apiResponse.errorNumber}`);
}
return apiResponse.body;   // ← always unwraps .body
```

**Reference:** `homebridge-tekmar-wifi/src/types/api.ts` lines 28–32:
```typescript
export interface ApiResponse<T> {
  errorNumber: number;
  errorMessage: string | null;
  body: T;
}
```

For `GET /Location`, the actual payload is `ApiResponse<Location[]>`:
- `body` is an **array** of Location objects
- Each Location has `locationId`, `name`, `isDefault`, `devicesCount`, etc.
- There is no `locations` wrapper key — the array IS the body

### Hypotheses, ranked by probability

---

**Hypothesis 1 (CONFIRMED — probability: 100%)**
**`fetchFirstLocationId()` and `fetchAndParseLocationState()` both fail to unwrap the API envelope for GET /Location.**

Lines 334–336 in `sunstat-thermostat-parent.groovy`:
```groovy
def body = resp.data
List locations = body instanceof List ? body as List : []
```

When Hubitat parses the JSON response, `resp.data` is a `Map` (the outer envelope `{errorNumber, errorMessage, body: [...]}`) — **not** a `List`. The check `body instanceof List` is `false`, so `locations` is always `[]`.

Same bug at lines 411–412 in `fetchAndParseLocationState()`.

**Confirmed by:** `client.ts:66` — the reference implementation always accesses `apiResponse.body` before returning.

---

**Hypothesis 2 (CONFIRMED — probability: 100%)**
**`parseResponseBody()` also fails to unwrap the envelope when `resp.data` is a `Map`.**

`parseResponseBody()` at lines 674–696:
```groovy
def data = resp?.data
if (data instanceof Map) {
    return data as Map   // ← returns the ENVELOPE, not the inner body
}
```

When `resp.data` is the envelope Map `{errorNumber:0, errorMessage:null, body:{userId:..., defaultLocationId:...}}`, it returns the whole envelope. Callers then do `body?.defaultLocationId` — but `defaultLocationId` is nested one level deeper under `.body`, not at the top level. Result: `defaultLocId` is always null.

This affects `runDiscovery()` (line 298–300):
```groovy
Map body = parseResponseBody(resp)
String defaultLocId = safeStr(body?.defaultLocationId)  // ← null — key is in body.body
```

---

**Hypothesis 3 (LOW probability — ruled out)**
~~The User object doesn't have `defaultLocationId`~~

**Ruled out by:** `homebridge-tekmar-wifi/src/types/api.ts` line 38:
```typescript
export interface User {
  userId: string;
  emailAddress: string;
  defaultLocationId: string;   // ← field exists and is non-nullable
  ...
}
```

---

**Hypothesis 4 (LOW probability — ruled out)**
~~`GET /Location` returns a different wrapper like `{locations: [...]}`~~

**Ruled out by:** `client.ts:113` — `getLocations()` returns `this.request<Location[]>` which does `return apiResponse.body` where body is typed `Location[]`. No intermediate `locations` key.

---

### Root Cause Summary

The entire `discoverDevices` failure collapses to a **single root cause: `parseResponseBody()` and all direct `resp.data` usages never unwrap the `{errorNumber, errorMessage, body}` API envelope.** Every path that reads API data is affected.

The 0.7-second timing gap confirms both calls completed successfully at the HTTP level (no exception thrown, HTTP 200 returned). Both calls silently returned empty/null data due to the envelope bug.

---

## Section 3 — Recommended Code Changes for Tank

**Tank's job:** Implement these changes in `drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy`.
Cypher does not touch .groovy files.

### Change 1 — Fix `parseResponseBody()` to unwrap the envelope

**File:** `sunstat-thermostat-parent.groovy`  
**Lines:** 674–696

Replace:
```groovy
private Map parseResponseBody(resp) {
    try {
        def data = resp?.data
        if (data instanceof Map) {
            return data as Map
        }
        // API wraps responses: {"errorNumber":0,"body":{...}}
        // but the Hubitat JSON parser may already unwrap — handle both shapes
        if (data instanceof String) {
            def parsed = new JsonSlurper().parseText(data as String)
            if (parsed instanceof Map) {
                // Unwrap envelope if present
                Map envelope = parsed as Map
                if (envelope.containsKey("body") && envelope.body instanceof Map) {
                    return envelope.body as Map
                }
                return envelope
            }
        }
    } catch (Exception e) {
        debugLog "parseResponseBody exception: ${e.message}"
    }
    return [:]
}
```

With:
```groovy
private Map parseResponseBody(resp) {
    try {
        def data = resp?.data
        if (data instanceof String) {
            data = new JsonSlurper().parseText(data as String)
        }
        if (data instanceof Map) {
            Map m = data as Map
            // Unwrap ApiResponse envelope: { errorNumber, errorMessage, body: {...} }
            if (m.containsKey("body") && m.body instanceof Map) {
                return m.body as Map
            }
            return m
        }
    } catch (Exception e) {
        debugLog "parseResponseBody exception: ${e.message}"
    }
    return [:]
}
```

**Why:** The original code returns the raw envelope Map when `resp.data` is already parsed as a Map. The fix adds envelope unwrapping for the Map path, consistent with the existing String path logic. The String path is kept for defensive handling of unparsed responses.

---

### Change 2 — Add `parseResponseList()` helper for List endpoints

**File:** `sunstat-thermostat-parent.groovy`  
**Location:** Immediately after `parseResponseBody()` (after line 696)

Add new helper:
```groovy
private List parseResponseList(resp) {
    try {
        def data = resp?.data
        if (data instanceof String) {
            data = new JsonSlurper().parseText(data as String)
        }
        // Unwrap ApiResponse envelope: { errorNumber, errorMessage, body: [...] }
        if (data instanceof Map) {
            Map m = data as Map
            if (m.containsKey("body") && m.body instanceof List) {
                return m.body as List
            }
        }
        if (data instanceof List) {
            return data as List
        }
    } catch (Exception e) {
        debugLog "parseResponseList exception: ${e.message}"
    }
    return []
}
```

**Why:** GET /Location and GET /Location/{id}/Devices return `ApiResponse<Location[]>` / `ApiResponse<DeviceSummary[]>`. The body is a List, not a Map, so `parseResponseBody` can't handle it. A dedicated helper avoids duplicating the unwrapping logic.

---

### Change 3 — Fix `fetchFirstLocationId()` to use `parseResponseList()`

**File:** `sunstat-thermostat-parent.groovy`  
**Lines:** 327–345

Replace:
```groovy
private String fetchFirstLocationId() {
    String result = null
    Map params = buildApiParams("GET", "/Location", null)
    try {
        httpGet(params) { resp ->
            Integer status = safeStatus(resp)
            if (status == 200) {
                def body = resp.data
                List locations = body instanceof List ? body as List : []
                if (locations) {
                    result = safeStr(locations[0]?.locationId)
                }
            }
        }
    } catch (Exception e) {
        log.error "[SunStat] GET /Location exception: ${e.message}"
    }
    return result
}
```

With:
```groovy
private String fetchFirstLocationId() {
    String result = null
    Map params = buildApiParams("GET", "/Location", null)
    try {
        httpGet(params) { resp ->
            Integer status = safeStatus(resp)
            if (status == 200) {
                List locations = parseResponseList(resp)
                log.info "[SunStat] GET /Location returned ${locations.size()} location(s)"
                if (locations) {
                    result = safeStr(locations[0]?.locationId)
                    log.info "[SunStat] Auto-selected locationId: ${result}"
                } else {
                    log.warn "[SunStat] GET /Location returned empty list — check account has at least one location"
                }
            } else {
                log.error "[SunStat] GET /Location failed: HTTP ${status}"
            }
        }
    } catch (Exception e) {
        log.error "[SunStat] GET /Location exception: ${e.message}"
    }
    return result
}
```

**Note on logging:** The `log.info` calls here are NOT behind `logEnable` — they are diagnostic info that helps the next failure surface the actual response shape. Added per spec.

---

### Change 4 — Fix `fetchAndParseLocationState()` to use `parseResponseList()`

**File:** `sunstat-thermostat-parent.groovy`  
**Lines:** 402–423

Replace:
```groovy
        httpGet(params) { resp ->
            Integer status = safeStatus(resp)
            if (status != 200) {
                log.warn "[SunStat] GET /Location failed: HTTP ${status} — location away state unchanged"
                return
            }
            def data = resp.data
            List locations = data instanceof List ? data as List : []
            Map loc = locations.find { safeStr(it?.locationId) == locId } as Map
```

With:
```groovy
        httpGet(params) { resp ->
            Integer status = safeStatus(resp)
            if (status != 200) {
                log.warn "[SunStat] GET /Location failed: HTTP ${status} — location away state unchanged"
                return
            }
            List locations = parseResponseList(resp)
            Map loc = locations.find { safeStr(it?.locationId) == locId } as Map
```

---

### Change 5 — Fix `discoverDevicesAtLocation()` to use `parseResponseList()`

**File:** `sunstat-thermostat-parent.groovy`  
**Lines:** ~355–360 (the `body instanceof List` check for `/Location/{id}/Devices`)

Locate:
```groovy
            def body = resp.data
            List devices = body instanceof List ? body as List : []
```

Replace with:
```groovy
            List devices = parseResponseList(resp)
```

**Note:** The same `ApiResponse<T>` envelope applies to `/Location/{id}/Devices`. Same fix.

---

### Change 6 — Add info-level diagnostic logging in `runDiscovery()` (non-gated)

**File:** `sunstat-thermostat-parent.groovy`  
**Location:** Lines 302–315 (after extracting userId, defaultLocId, measurementScale)

Add after extracting `defaultLocId` (approximately after line 300):
```groovy
log.info "[SunStat] GET /User → userId=${userId}, defaultLocationId=${defaultLocId}, scale=${measurementScale}"
```

And after the final `!resolvedLocationId` check fails, add a breadcrumb:
```groovy
// Already present: log.error "[SunStat] Could not resolve a Watts location ID..."
// Add BEFORE that line:
log.info "[SunStat] locationId resolution: settings='${safeStr(settings.locationId)}', defaultFromUser='${defaultLocId}', fetchFirstResult='${resolvedLocationId}'"
```

**Why non-gated:** These surface the exact field values that caused the failure. Without them, the next support session is blind again.

---

### Version Bump

Bump `DRIVER_VERSION` to `"0.1.4"` and add changelog entry:
```
0.1.4 — 2026-05-16 — Fix API envelope unwrapping: GET /Location and GET /User responses were not
        being unwrapped from {errorNumber, errorMessage, body} envelope, causing discoverDevices to
        always fail with "Could not resolve a Watts location ID" when locationId preference is blank.
        Adds parseResponseList() helper; adds diagnostic info logging in discovery path.
```

---

## Appendix — Confirmed API Response Shapes

Verified via `homebridge-tekmar-wifi/src/types/api.ts` and `client.ts`:

### GET /User → ApiResponse\<User\>
```json
{
  "errorNumber": 0,
  "errorMessage": null,
  "body": {
    "userId": "...",
    "emailAddress": "user@example.com",
    "defaultLocationId": "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX",
    "languagePreference": "en",
    "userTypeId": 1,
    "measurementScale": "I",
    "mobilePhoneNumber": null,
    "firstName": "Mads",
    "lastName": "Kristensen",
    "smsNotificationEnabled": false,
    "emailNotificationEnabled": true,
    "pushNotificationEnabled": true,
    "defaultLocationDevices": ["device-id-1"],
    "voiceControlPlatform": ""
  }
}
```

### GET /Location → ApiResponse\<Location[]\>
```json
{
  "errorNumber": 0,
  "errorMessage": null,
  "body": [
    {
      "locationId": "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX",
      "ownerId": "...",
      "name": "Home",
      "address": { "address": "...", "city": "...", "state_province": "...", "zipcode": "...", "country": "..." },
      "awayState": 0,
      "isDefault": true,
      "isShared": false,
      "userType": 1,
      "supportsAway": true,
      "usersCount": 1,
      "devicesCount": 1,
      "hasDeviceInDemandResponseEvent": false
    }
  ]
}
```

### GET /Location/{id}/Devices → ApiResponse\<DeviceSummary[]\>
```json
{
  "errorNumber": 0,
  "errorMessage": null,
  "body": [
    {
      "deviceId": "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX",
      "name": "SunStat floor heating",
      "modelId": 562,
      "modelNumber": "...",
      "deviceType": "Thermostat",
      "deviceTypeId": 1,
      "location": { "locationId": "...", "name": "Home", "address": null, "awayState": 0, "userType": 1 },
      "imageUrl": null,
      "isShared": false
    }
  ]
}
```

**Key takeaway:** Every endpoint wraps in `ApiResponse<T>`. The driver must always access `.body` after parsing. No endpoint returns a bare object or bare array at the top level.

