# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — Groovy device drivers for Hubitat Elevation
- **Stack:** Groovy (Hubitat sandbox), Hubitat platform APIs
- **Created:** 2026-05-16

## Active Milestones Summary

### 2026-05-17: Gemstone Lights v0.4.0–v0.4.8
✅ **Released.** HPM publishing infrastructure + community PR submitted.

Key learnings:
- **API Envelope Pattern:** Unwrap {errorNumber, errorMessage, body: <payload>} in dedicated helpers
- **WebCoRE Overload Shadowing:** Distinct command names avoid metadata conflicts (playEffectByName)
- **Error Capture:** Use esponse.getErrorData() for 4xx responses; never skip diagnostic logs
- **ARGB & Byte Order:** Gemstone uses ABGR (not ARGB); include 0xFF alpha; force long arithmetic with 0xFFL
- **State Hygiene:** Favorites-only caching; non-favorites are on-demand; transient state lives in locals
- **Log Discipline:** Count bounds even at debug; every sendEvent needs descriptionText

### 2026-05-16T20:01:41-07:00: SunStat Connect Plus v0.1.0–v0.1.4
✅ **Shipped.** Parent/child OAuth architecture; token refresh (24-hr TTL); polling + discovery.

Key learnings:
- **EnergyMeter:** Built-in nergy attribute; siblings must be declared explicitly; guard array access with instanceof
- **Hold/Schedule Enums:** Use string enums ("holding"/"following") not bools; lowercase Hubitat-side values; emit optimistic updates
- **Setpoint Steps:** Apply step-rounding *before* clamping; persist step to state each poll
- **Token Preference Size:** Tokens >1KB use command setRefreshToken(String) not password preference
- **API Envelope Unwrapping:** Two helpers—parseResponseBody() for Maps, parseResponseList() for Lists
- **URL Path Encoding:** Always .replace("+", "%20") after URLEncoder for path segments (not query strings)

---

## Bosch Home Connect (In Scoping)

### 2026-05-17T16:31:55Z — Cypher + Trinity Scoping
**Verdict:** Feasible on Hubitat.

**Key decisions:**
- Parent App + child Driver (like SunStat)
- Device Flow OAuth2 (no redirect URI blocker)
- Polling at 90-120s cadence (1000 req/day rate limit)
- ContactSensor per door

**ACTION:** When spawned for implementation, read .squad/decisions/decisions.md first. Effort: Medium (2 sessions).

### 2026-05-17T16:45:09Z — Bosch Home Connect Consumer Auth Investigation

**Decision:** Developer portal registration path is unavoidable.

**Context:** User requested elimination of developer portal onboarding. Cypher investigated 5 consumer-auth alternatives (hcpy, SingleKey ID, openHAB direct binding, Homebridge plugins). All blocked by:
- CAPTCHA on SingleKey ID login (2024)
- Local WebSocket protocol (no Hubitat support)
- No consumer REST API for state polling

**Verdict:** Official developer API (Device Flow) remains the only feasible path. One-time 5-minute registration is the cost of a working driver.

**Design implication:** When Tank implements, use Device Flow OAuth2 with user-registered client_id + client_secret (entered as preferences). The technical path is confirmed; no further consumer-auth alternatives exist.

---

## 2026-05-17T16:53:47Z — Touchstone LED Fireplace Tuya Feasibility (Cypher + Trinity)

**Topic:** touchstone-fireplace-feasibility

Feasibility pass completed. Implementation ready to proceed.

**KEY CORRECTION — ColorControl is incorrect; use named custom commands:**

Trinity's initial architecture proposed `ColorControl` for flame color. Cypher's DP analysis reveals that flame and ember colors are **named palette indices** (6 flame effects, 12 log colors), not free-form RGB/HSV. `ColorControl` with HSV input will produce confusing rounding behavior when mapping to palette entries.

**Corrected capability mapping for Tank:**
- `Switch` (DP 1)
- `SwitchLevel` (DP 102, map 0–100 → `"1"`–`"5"`)
- **Custom command `setFlameColor(name)`** (DP 101, palette: orange, blue, yellow, orange+blue, orange+yellow, blue+yellow)
- **Custom command `setLogColor(name)`** (DP 104, palette: 12 named colors)
- **Custom command `setLogBrightness(level)`** (DP 105, 12-step)
- **Custom command `setFlameSpeed(speed)`** (DP 103, Slow/Medium/Fast)
- `Refresh`, `Initialize` (standard)

**Architecture:** Single Groovy driver, Tuya Local (LAN) over rawSocket + AES-128-ECB. Effort: Medium (2–3 sessions).

**ACTION FOR TANK:** 
1. Read `.squad/decisions.md` for full DP map and capability context
2. Scaffold driver with corrected named-command approach (NOT ColorControl)
3. Borrow Tuya Local protocol layer from kkossev/Hubitat (rawSocket + AES framing)
4. Await Mads' tinytuya scan output to confirm protocol version and exact DP IDs

See `.squad/orchestration-log/2026-05-17T165347Z-trinity.md` for architecture details (with ColorControl correction flagged).

---

## Core Patterns (Reusable)

1. **Parent/Child OAuth:**
   - Parent holds state.accessToken, state.refreshToken, state.tokenExpiresAt
   - getValidToken() guard before every HTTP call; refresh if 
ow > expiresAt - 300
   - Children store cloud device IDs as DataValue("cloudDeviceId")
   - isComponent: false on child creation
   - Parent calls child.parseDeviceState(body) after each poll

2. **API Response Handling:**
   - Check esponse.getErrorData() for 4xx responses (getData() is null)
   - Unwrap envelopes in dedicated helpers (parseResponseBody(), parseResponseList())
   - Log diagnostic details (body type, sample data) on every response path
   - Never replace server-assigned IDs with synthetic strings

3. **Capability Metadata:**
   - Use distinct command names to avoid WebCoRE overload shadowing
   - Every sendEvent needs descriptionText
   - Standard capabilities + custom attributes for non-standard state
   - Emit optimistic updates before API calls

4. **State & Preference Hygiene:**
   - Tokens >1KB: use command parameter not preference (preference limit ~1024 chars)
   - Transient working data: local variables, not state.*
   - Persistent caches: favorites-only; on-demand non-favorites
   - Log bounds even at debug level for collections

---

**For detailed learning notes, see archived history.**

---

## 2026-05-17T17:14:00Z — Touchstone Tuya Portal-Free Key Extraction Audit (Cypher — Conclusion)

**Topic:** tuya-portal-free-2026

Cypher completed definitive audit of all 2026 Tuya local-key extraction methods. **Conclusion affects Touchstone driver UX.**

### KEY FINDING

**Portal-free path exists but is not applicable to Mads** (no Home Assistant). The iot.tuya.com portal signup (which Mads is already pursuing via Coordinator walkthrough) is the **durable and correct choice** for the Touchstone driver.

### Technical Summary

- **Portal-free path:** `make-all/tuya-local` cloud-auth (HA integration) via `apigw.iotbing.com`. Requires Home Assistant + SmartLife app scan. **Relies on hardcoded Tuya `client_id` that can be revoked unilaterally.**
- **All other portal-free methods:** Broken (MITM deprecated 2022), impractical (ADB backup blocked), or not applicable (BLE is setup-only, not for key material).
- **For Mads (no HA):** `tinytuya wizard` or direct iot.tuya.com setup is the only option. Mads is mid-flow on the latter.

### Impact on Touchstone README

Tank's driver will work with any local key source. Link should document **both** key-extraction methods in README:
1. **Preferred (if user has HA):** `make-all/tuya-local` cloud-auth (5 min, no dev account) — note the Tuya client_id dependency
2. **Fallback (no HA):** `tinytuya wizard` via iot.tuya.com (20 min, free dev account) — or direct signup like Mads

**No changes to Touchstone driver architecture.** Both key-extraction paths yield the same permanent `localKey` value for Tuya Local (LAN) protocol.

See `.squad/decisions.md` section "2026-05-17: 2026 Tuya Portal-Free Key Extraction Assessment" for full audit and SUPERSEDES note correcting prior session cypher-6 claims.


