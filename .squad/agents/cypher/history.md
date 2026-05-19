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

