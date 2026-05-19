# Cypher — Integration / Protocol Engineer

## Session Arc 2026-05-19: Fully Kiosk v0.6+ Parity Audit

**Task:** Mads asked: "what's left on the fully driver to make it truly awesome?" Conduct focused, opinionated audit against HA parity baseline and official REST API.

**Methodology:**
- Inventoried v0.5.0 driver: 21 commands, 12 attributes
- Fetched official Fully Kiosk REST API docs (www.fully-kiosk.com/#rest)
- Analyzed HA integration source (homeassistant/components/fully_kiosk): 7 entity types, 19+ exposed features
- Ranked gaps by leverage (impact-to-effort ratio)

**Key Findings:**
1. **v0.5.0 is production-ready** — security fixed, event hygiene fixed, rich sensor attributes from existing poll.
2. **12 identified gaps** — Storage/RAM sensors, timer controls, screenshots, media player, arbitrary config key-value setter.
3. **MVP for v0.6:** RAM/storage sensors + reboot button + screen/screensaver timers (~190 LOC, all 🟢 low risk).
4. **Out of scope (final):** MQTT (rollback correct), cloud APIs, video streaming, arbitrary settings UI, reflection/metaClass.

**Deliverable:** Full audit report (4 sections) posted to Mads for prioritization. NOT pushed to decisions.md (is for team decisions, not audit recommendations).

**FKB REST API learnings:**
- All data types already supported: integers (brightness, timers, volume), booleans (settings), strings (URLs, text), JSON objects (device info)
- 350+ config parameters via `setStringSetting`/`setBooleanSetting` — too many to safe-gate in UI; generic key-value setter is out-of-scope
- No video streaming; `getScreenshot` returns static JPEG only
- Password auth is LAN-local query param — cleartext but already documented as protocol design limitation

---

## Prior Audit Cycles Summary

**Five-Verdict Summary (2026-05-16 to 2026-05-18):**
1. ✅ **Bosch Home Connect** → INSTALL craigde/hubitat-homeconnect-v3 (67/100)
2. ✅ **Rainbird LNK** → IMPROVE-EXISTING MHedish (92/100)
3. ✅ **PurpleAir Cloud-API** → INSTALL pfmiller0 (88/100)
4. ✅ **MyQ Ecosystem** → BUILD ratgdo ESPHome HTTP (greenfield)
5. ✅ **Daikin BRP069B** → BUILD complete (v0.1.0–v0.1.7 shipped)

**Result:** Mads's complete driver stack now has **zero open BUILD candidates**. Detailed audit records moved to history-archive.md.

---

## Learnings

### 2026-05-18 — Gemstone Stale-Token Diagnostic (v0.4.16)

**Task:** Mads reported: "after a while of inactivity, I can't control the device. Not until I hit refresh."

**Finding:** Two compounding issues:

1. **Dedup-before-auth ordering** — `on()` (line 206-209), `setLevel()` (line 246-248), and `setColor()` (lines 283-288) all contain dedup guards that return early *before* `sendCommand()` is called. Since the token validity check lives inside `executeOrQueueRequest()` (line 803), any deduped command never triggers re-auth. `refresh()` has no dedup guard, so it always reaches the auth gate — that's why it "fixes" things.

2. **Proactive refresh failure leaves no new timer** — `scheduleTokenRefresh()` (line 1711) is only called on auth success (line 508). If the proactive `refreshAccessTokenTask` fires and both `REFRESH_TOKEN_AUTH` and the `USER_PASSWORD_AUTH` fallback fail, `handleAuthFailure()` clears the token AND the timer (via `clearAuthTokens()` line 1220), and no new timer is scheduled.

**Open question:** Whether Gemstone's API returns 401 or 403 on expired tokens (driver only retries on 401, line 578). Unknown without live testing.

**Deliverables:** `.squad/decisions/inbox/cypher-gemstone-stale-token.md`, `.squad/skills/cognito-token-lifecycle/SKILL.md`

**Pattern extracted:** Auth gate must come BEFORE dedup in Hubitat command handlers. Dedup is only safe to apply once the session is confirmed healthy.

### 2026-05-19 — Touchstone Fireplace Live Outage Triage

**Task:** Mads woke up to log spam: repeated `cmd 13` timeouts with "No response within 5s. Backing off before retrying." Driver stuck in perpetual retry loop; fireplace uncontrollable.

**Finding — What cmd 13 is:**
`TUYA_CMD_CONTROL_NEW = 13` (line 113, `touchstone-fireplace.groovy`) = `0x0D` in Tuya protocol = the "device22" status/query command. Used whenever the `deviceId` is 22 characters long. The driver auto-detects this: cmd 10 (`TUYA_CMD_DP_QUERY`) is sent first; if the device responds "data unvalid", the driver switches permanently to cmd 13 (lines 1643-1647). The presence of `Queued Tuya cmd 13 for device22 retry` in the logs means: the device DID respond to the initial cmd 10 (proving it's reachable at the IP/TCP level), then cmd 13 was queued, and THAT is what never gets a response.

**Finding — Why cmd 13 goes unanswered but heartbeats work:**
Tuya heartbeat (cmd 9, line 111) sends an empty payload — no AES encryption. The device echoes it back at the TCP level regardless of localKey correctness. Status/control queries (cmd 13) send an AES-128-ECB encrypted payload. If the `localKey` has rotated (firmware update, Smart Life re-pair), the device decrypts garbage and silently drops the frame. Result: heartbeat ACKs arrive → `parse()` → `consumeReceiveBuffer()` → `state.retryIndex = 0` reset (line 869) → the retry backoff never escalates beyond 5s/15s. This produces the exact alternating 5s/15s pattern seen in the logs.

**Finding — retryIndex reset on any frame (driver observation for Tank):**
`state.retryIndex = 0` (line 869) fires on ANY successfully parsed frame, including heartbeats. The intent is to reset backoff when the device is responsive, but a heartbeat ACK does not confirm the device is processing commands. This masks a stale/wrong-key situation: the retry loop runs forever at 5s/15s intervals, producing log spam, burning Hubitat scheduler slots, and never surfacing a "device unreachable" state. Tank should consider (a) only resetting retryIndex on a DP-bearing response frame, and (b) adding a maximum retry cap that surfaces `healthStatus = "offline"` and stops retrying until `initialize()` is called manually.

**Finding — no retry cap (driver observation for Tank):**
`RETRY_DELAYS_SECONDS = [5, 15, 30]` (line 123) is capped at index 2 (30s) forever. There is no counter that stops the retry loop after N consecutive failures. A production device that loses its key will retry indefinitely, generating log noise and consuming hub resources without ever resolving itself or alerting the user to take corrective action.

**Root cause order of probability (for this specific outage):**
1. **Wrong localKey** — device responds "data unvalid" to cmd 10 (normal for device22), then silently drops AES-encrypted cmd 13. Heartbeats still ACK. Likely cause: firmware OTA overnight.
2. **Device in bad state** — device has WiFi but is not processing Tuya queries; power cycle fixes.
3. **IP address changed** — less likely (device DID respond to cmd 10, proving reachability), but possible if cmd 10 was replied to by a different device at that IP.
4. **Hub-side socket half-open** — less likely given "data unvalid" was received in this session.

**Deliverables:** `.squad/decisions/inbox/cypher-touchstone-retry-cap.md`, field troubleshooting section added to `.squad/skills/tuya-local-groovy/SKILL.md`.

**Playbook extracted:** See SKILL.md "Field Troubleshooting" section.

