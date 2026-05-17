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
- **Error Capture:** Use
esponse.getErrorData() for 4xx responses; never skip diagnostic logs
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

## 2026-05-17T10:47:09Z — Touchstone Sideline Elite — Local LAN Control Confirmed (Coordinator Direct Mode)

**Topic:** touchstone-local-control-achieved

Coordinator walked Mads through end-to-end Tuya IoT setup and local device verification. Device facts are now locked; architecture proposal from Trinity is validated. Ready for driver scaffolding.

### Device Facts for Driver Implementation

**Device Credentials** (stored at C:\Users\madsk\devices.json; <see devices.json on Mads' machine> for local_key value)
- Product: Touchstone Sideline Elite electric LED fireplace
- Tuya productKey: nc1lwvgjse1ujlr
- Tuya category: qn (electric fireplace)
- Device ID: 70223053e8db84d10b53
- LAN IP: 192.168.1.38
- MAC: e8:db:84:d1:0b:53
- Protocol: v3.3, AES-encrypted

**Heater DPs (Official Tuya Schema)**

| DP | Type | Name | Range |
|---|---|---|---|
| 1 | bool | switch | on/off |
| 2 | int | temp_set | 19–30°C |
| 3 | int | temp_current | 0–50°C |
| 5 | enum | level | 0/1/2 (heat level) |
| 13 | enum | temp_unit_convert | c/f |
| 14 | int | temp_set_f | 67–88°F |
| 15 | int | temp_current_f | 32–122°F |

**Vendor LED DPs (Empirical, TBD Next Session)**

| DP | Type | Observed | Notes |
|---|---|---|---|
| 101 | string-enum | "1" | Likely flame color/effect |
| 102 | string-enum | "5" | Likely flame brightness |
| 103 | string-enum | "1" | Likely flame speed |
| 104 | string-enum | "4" | Likely log/ember color |
| 105 | string-enum | "5" | Likely log brightness |
| 107 | bool | false | TBD |
| 108 | bool | false | TBD |

### ACTION FOR TANK

1. Read `.squad/decisions.md` for full DP details + Trinity's corrected capability mapping (named commands, not ColorControl)
2. Scaffold driver with Tuya Local protocol layer (rawSocket + AES-128-ECB framing)
3. Implement Wire capabilities + custom commands per Trinity's architecture
4. Borrow Tuya Local protocol reference from kkossev/Hubitat if needed; acknowledge license per project policy
5. Ready for Switch integration testing once LED DP empirical mapping is complete

### Effort Estimate (Revised)

**Session 1:** Driver scaffold + heater DPs (1, 2, 3, 5, 13–15) + capability wiring
**Session 2:** LED DP mapping (101–108) via Tuya app interaction; Switch validates on real device; driver refinement
**Session 3 (conditional):** Protocol version v3.4/v3.5 support if discovered in Session 2

Pattern learned: Empirical DP mapping via app interaction is faster than reverse-engineering firmware; rely on user doing the interactive exploration before driver development. This validates assumptions early.

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
   - Check
esponse.getErrorData() for 4xx responses (getData() is null)
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

## 2026-05-17T18:55:16Z — Touchstone v0.1.2 Shipped (Scribe Cross-Agent Sync)

**Topic:** touchstone-driver-shipped

v0.1.1 (Sideline Elite scaffold) + v1.1 (Device Profile generalization) + v1.2 (critical CRC32 import fix) are now landed in main. Both Tank sessions are complete.

**v1.1 outcomes:**
- Driver renamed to `"Touchstone / Tuya Fireplace"` (Mads' Option C decision)
- Device Profile preference with three modes (Sideline Elite / Generic Tuya / Custom)
- Discovery commands: `discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()`
- All commands routed through `dpFor(role)` to respect active profile

**v1.2 outcomes:**
- Removed forbidden `java.util.zip.CRC32` import; replaced with pure-Groovy table-driven CRC32
- Removed `java.io.ByteArrayOutputStream`; replaced with `concatBytes()` helper
- Verified all remaining imports (groovy.transform.Field, groovy.json, javax.crypto) are Hubitat-safe
- **This was the blocking install error for v0.1.1; unblocked by v1.2**

**Manifest sync (by Scribe):**
- packageManifest.json version bumped from 0.1.1 → 0.1.2 to match driver code

**Documentation (by Link):**
- README (18.2 KB): device support, setup, discovery workflow, troubleshooting
- HPM manifest ready for publish

**Test plan (by Switch, v0.1.0):**
- 19 tests locked for Sideline Elite; smoke pass = 30 min for Mads
- v1.1 testing will expand to Generic/Custom profiles (queued for next batch)

**Next phase:**
- Mads: Re-test Hubitat install to confirm import allowlist fix works
- Mads: Review documentation; iterate if clarifications needed
- Switch: Expanded validation for Generic/Custom profiles (queued)
- Community: Users with other Touchstone models can now use Custom profile + discovery commands

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

## Learnings

### 2026-05-17T11:07:22.475-07:00 — Touchstone Tuya v3.3 scaffold

- Hubitat `interfaces.rawSocket.connect(..., byteInterface: true)` still feeds Tuya payloads into `parse(String message)` as hex text in practice; buffer the hex stream, split on `000055AA`, and validate CRC32 before decrypting.
- Tuya v3.3 control traffic is AES-128-ECB / PKCS5Padding over the JSON body. Most commands prepend ASCII `3.3` + 12 zero bytes **after** encryption; `DP_QUERY` (`0x0a`) and heartbeat (`0x09`) skip that protocol header.
- 22-character Tuya IDs are the `device22` edge case: status queries may need command `0x0d` plus a null-valued `dps` map instead of command `0x0a`. If the device answers with `data unvalid`, switch query mode and retry once.
- kkossev patterns adopted here are lifecycle + hygiene patterns, not the protocol layer itself: `installed()` sets defaults then `initialize()`, `updated()` unschedules + re-inits, debug logging auto-disables after 30 minutes, and `parse()` must log-and-bail on bad frames rather than crash.
- Touchstone's single-client Tuya socket is worth treating as scarce: queue requests, close the socket quickly after idle, and back off at 5s / 15s / 30s when another client (for example Smart Life) appears to be holding the TCP slot.
- DP 14 (°F setpoint) can lie immediately after a power transition. Keep optimistic setpoint state locally and schedule a delayed refresh instead of trusting the first post-power status frame.

### 2026-05-17T11:24:33-07:00 — Touchstone v1.1 generalization

- The safest cross-model Tuya pattern is **Tested + Generic + Custom**: keep the verified Sideline Elite DP map as the default, keep the Generic profile conservative (power / heat / setpoint only), and expose explicit DP-number overrides for Custom instead of guessing LED mappings.
- Hubitat driver preferences can gate Custom-only inputs with `if (settings?.deviceProfile == "Custom")`, but those extra fields only appear after the user saves/reopens the device page. The command/path logic still needs hardcoded fallbacks so an untouched Custom profile resolves to usable defaults.
- Let `dpFor(role)` resolve the active DP at command time and parse time, not once during `updated()`. That way a profile change or edited DP number takes effect on the very next command/status frame without rebooting the driver.
- In-driver discovery is practical on Hubitat: `discoverDPs()` logs the typed DP dump, `captureBaseline()` + `captureDiff()` isolate remote-button changes, and `setRawDP()` gives users an auditable way to experiment without leaving the device page.

### 2026-05-17T11:31:31-07:00 — Hubitat import allowlist / CRC32

- Hubitat driver imports are stricter than plain Groovy: `java.util.zip.CRC32` is rejected at install time, and simple `java.io.*` helpers are risky enough that a tiny pure-Groovy byte concatenation helper is the safer default.
- For reusable lookup tables in Hubitat drivers, `@Field static final` is the right pattern; it builds once at driver load instead of rebuilding inside every parse/send call.
- Tuya v3.3 packet checksums use canonical CRC-32/ISO-HDLC parameters: init `0xFFFFFFFF`, reversed polynomial `0xEDB88320`, reflected byte updates, xor-out `0xFFFFFFFF`, and the resulting unsigned 32-bit value still gets written big-endian into the `55AA` frame.

---

### 2026-05-17T18:24:33Z — Cross-Agent Decision Sync (Scribe)

**Topic:** touchstone-driver-shipped

**Naming Decision (Option C):** Driver display name is `"Touchstone / Tuya Fireplace"` (not just "Touchstone Sideline Elite"), and file path remains `drivers/touchstone-fireplace/touchstone-fireplace.groovy`. This threads the needle: community discoverability + honest scope + room for other Tuya WiFi fireplaces. (Decision merged from coordinator directive; Scribe appended for Tank + Switch awareness.)

**Generalization Scope:** Tank v1.1 (in flight) will add Device Profile dropdown + discovery commands for multi-Touchstone-model support. Scope deferred from v0.1.0 because driver is sealed and shipping. (Decision merged from Mads directive; Scribe appended for Tank awareness.)

**Test Plan Status:** Switch completed 19-test real-device plan (merged to decisions.md). Test coverage: pre-flight, happy path (9 tests), state sync (2 tests), recovery (3 tests), edge cases (3 tests), stability (1 test), cleanup. All with clear pass/fail criteria and known limitations documented. Ready for Mads smoke test after Tank v0.1.0 ships.

**v1.1 In Flight:** Tank parallel session is drafting Device Profile preference + discovery workflow (`discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()`). Scope: make driver work for other Touchstone models without manual DP discovery. Awaiting real-device validation from Mads before landing v1.1.


