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
