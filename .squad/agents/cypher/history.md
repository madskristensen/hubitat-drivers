# Cypher — Integration / Protocol Engineer

Recent contributions documented in history-archive.md. Current session: SunStat v0.1.4 API diagnosis.

- 2026-05-17T04:24:00Z: SunStat v0.1.4 — diagnosed API envelope unwrap bug, shipped bootstrap script
- 2026-05-16: SunStat Connect Plus API research complete (Azure AD B2C, token refresh documented)

## 2026-05-17 (session cypher-3) — Gemstone setColor + setColorTemperature Both Broken

**Prior framing was wrong.** cypher-2 assumed setColorTemperature was a working baseline. Mads confirmed: neither setColor nor setColorTemperature works. This entry supersedes that framing.

### CT Silent-Fail Mode (Confirmed)

The silent-fail site is `executeOrQueueRequest` lines 712-716:
```groovy
if (!hasUsableAccessToken() || (request?.requiresDevice && !state.deviceId)) {
    queueRequest(request)   // NO LOGS — completely silent
    continueSessionSetup()
    return
}
```
CT hits this path when called before device discovery completes (`state.deviceId` null) or during the 5-minute token leeway window. The request enters `state.pendingRequests` silently. `continueSessionSetup()` no-ops if `state.authInFlight = true`. No error, no info, no debug emitted.

**This is NOT an effectCatalogStale/effectCatalogMissing gate** — those only affect `setEffect()` paths. CT bypasses them entirely.

### setColor 400 Root Cause (High Confidence)

Three candidate causes, all in `buildColorRequest` and `buildColorTemperatureRequest`:

1. **`pattern.id = generatePatternId()` produces non-UUID** — `"hubitat-{timestamp}-{n}"`. Gemstone API uses UUID-format pattern IDs (read-modify-write protocol). `buildLevelRequest` does NOT override `id` and presumably works. Removing the id override from color/CT builders is the primary fix.

2. **Colors use alpha=0 (`0x00RRGGBB`)** — `hubitatHueSatToArgb` and `kelvinToArgb` both omit the alpha byte. Gemstone API appears to use `0xFFRRGGBB` (alpha=255=opaque). Fix: OR with `(0xFF << 24)` in both functions.

3. **`referencePatternId = null` explicit null** — May need to be absent rather than null. Fix: `pattern.remove("referencePatternId")`.

### has-gemstone / pygemstone Reference Status

Neither `has-gemstone` (HA integration) nor pygemstone (sslivins) is available locally. The `.squad/research/` directory contains only encrypted ELAN/Control4 binaries. Pattern payload requirements inferred from code analysis and prior research ("read-modify-write required" note in history-summary-2026-05-16).

**Capture-and-respond path**: Ship Fix 1 (surface `response.getErrorData()` in 400 handler) in v0.4.2 first. The actual API error message will confirm which of the three candidates is the primary cause.

### Deliverable

Full report at: `.squad/decisions/inbox/cypher-gemstone-color-ct-both-broken.md`

## 2026-05-17 (session cypher-4) — Bosch Home Connect Fridge Driver Feasibility

**Full report:** `.squad/decisions/inbox/cypher-bosch-home-connect-feasibility.md`

**Verdict:** Feasible with caveats. SSE not usable in Hubitat; polling at ≥90s cadence is the path. 1,000 req/day hard limit is the binding constraint.

---

## 2026-05-17 (session cypher-5) — Bosch Home Connect Consumer Auth Landscape

**Full report:** `.squad/decisions/inbox/cypher-bosch-consumer-auth-options.md`

**Verdict:** No viable consumer-auth-only path exists for Hubitat. Developer portal remains the only feasible route.

---

## 2026-05-17 (session cypher-6) — Touchstone LED Fireplace / Tuya Feasibility

**Full report:** `.squad/decisions/inbox/cypher-touchstone-tuya-feasibility.md`

**Verdict:** Yes-with-caveats. Tuya Local (LAN) is the right path. DP map confirmed from reference implementation.

**Key finding:** `make-all/tuya-local` HA integration has a config file for the **Touchstone Sideline** specifically (product ID `qhwld7e4eqvu5fbp`). All DPs captured.

**Color zone correction:** Flame color (DP 101) and ember color (DP 104) are both **named palette indices** (6 and 12 options respectively), not free-form RGB or HSV. `ColorControl` is the wrong capability; named custom commands are the right design.

**Local key extraction:** Two paths — (a) `tinytuya wizard` via free Tuya IoT dev portal, or (b) `make-all/tuya-local` cloud-auth using SmartLife app credentials with NO developer portal required. Path (b) is the recommended UX, passes Mads' "no developer app" boundary if he has HA.

---

## 2026-05-17 (session cypher-7) — 2026 Tuya Portal-Free Key Extraction Audit

**Full report:** `.squad/decisions/inbox/cypher-tuya-portal-free-2026.md`

**Verdict: Yes-but-fragile.** One genuinely portal-free path exists in 2026. All others are broken or require iot.tuya.com.

**Correction to session cypher-6:** The prior entry said `make-all/tuya-local` cloud-auth "passes Mads' no developer app boundary if he has HA." That was under-flagged. The three constraints that were glossed over:
1. Requires Home Assistant to be installed — not a standalone tool
2. Relies on hardcoded Tuya-issued `client_id = "HA_3y9q4ak7g4ephrvke"` (`schema = "haauthorize"`) — revocable by Tuya at any time
3. Auth endpoint is `apigw.iotbing.com` (consumer Smart Life API) — not `iot.tuya.com`, which is why it's "portal-free," but this distinction was not explained

**Method map (2026 final state):**
- `make-all/tuya-local` cloud-auth: **Portal-free, but requires HA.** QR scan of SmartLife app against `apigw.iotbing.com`. Stable until Tuya revokes the HA client_id.
- `localtuya` (HA): **Requires iot.tuya.com** (Client ID + Secret + User ID). The "not mandatory" note means you skip it and enter keys manually — still need to get the key somehow.
- `tinytuya wizard`: **Requires iot.tuya.com.** Confirmed.
- `tuya-cli` MITM: **Broken.** Officially deprecated in tuyapi SETUP.md; Tuya encrypts app traffic since ~2022.
- Smart Life ADB backup: **Broken for most users.** `allowBackup=false` blocks ADB backup. Rooted-phone direct filesystem access is the only path and requires SQLCipher key derivation — impractical.
- BLE provisioning: **Not applicable.** Local key is generated server-side; BLE transmits WiFi credentials only, never the local_key.
- `tuyapi` consumer API (`a1.tuyaus.com`): **No working tool in 2026.** No maintained reverse-engineered path returns local_keys. MITM broken.

---

## Learnings Summary

### 2026-05-17 — Tuya Key-Extraction Landscape (Portal-Free Audit)

**Definitive status:**
- **Only viable portal-free method:** `make-all/tuya-local` cloud-auth (HA required; uses hardcoded Tuya `client_id` that can be revoked)
- **All alternatives:** MITM broken (2022), ADB blocked, BLE not applicable, no maintained reverse-engineered tools
- **For Mads (no HA):** iot.tuya.com portal signup is the correct, durable choice
- **Key insight:** "Portal-free" requires HA + SmartLife auth, not truly standalone. Fragility: client_id revocation breaks all HA users simultaneously.
- **Tuya 2025-2026 changes:** Portal trial limited to 1 month (renewable), but local_key persists indefinitely after extraction

### 2026-05-17 — Touchstone Tuya Local (LAN) Integration

**Protocol & Feasibility:**
- Product confirmed: Sideline v3.3 (AES-128-ECB, rawSocket stable with 20s keepalive)
- DP map complete: Power(1), Flame color palette(101, 6 effects), Brightness(102, 5 steps), Speed(103), Ember palette(104, 12 colors), Log brightness(105, 12 steps)
- **Palette correction:** NOT RGB/HSV — use named custom commands, not ColorControl
- Platform: Hubitat rawSocket + javax.crypto confirmed available; v3.4/v3.5 requires session-key negotiation (1 session cost if needed)

### 2026-05-17 — Bosch Home Connect API + Auth

**Developer API only path:** Device Flow OAuth2 (no redirect URI). 1,000 req/day hard limit (90-120s polling). Consumer WebSocket path blocked by missing Hubitat support (no persistent WebSocket, TLS-PSK unsupported, CAPTCHA breaks automation).

### Gemstone Lights — setColor 400 Investigation

**Status:** Root cause unconfirmed (response.getData() returns null for 4xx). Three candidates: synthetic pattern ID, missing alpha byte (0xFF), or stale fields in payload. Diagnostic path: capture error message + whitelist fields to 9 canonical ones.

---

### 2026-05-17 — Driver Gap Analysis (all three drivers)

**Touchstone (Tuya Local):**
- DP 103 (flame speed, enum ~5 steps), DP 105 (log brightness, 12 steps), and DP 108 (child lock, bool) are all discovered and tracked as raw attributes but have NO corresponding commands. Three easy command additions.
- Socket is deliberately closed 2s after each transaction (SOCKET_IDLE_CLOSE_SECONDS = 2). Consequence: the device-push path (device proactively sends DP updates when user presses physical remote) is NEVER received between polls. The driver is poll-only. Persistent-socket + heartbeat (CMD 9) would enable real-time push receipt.
- DP 107 is tracked raw but its semantic is unknown; not worth mapping until confirmed via captureDiff().
- v3.3 is hardcoded. "device22" detection (22-char device ID → TUYA_CMD_CONTROL_NEW) is present but no v3.4/v3.5 session-key negotiation exists.

**SunStat (Watts Home API):**
- setBoost / cancelBoost are explicit stubs (log.warn "not yet implemented"). This is the #1 missing user-facing feature for a floor heating thermostat.
- No 429 handling in pollChildDevice or sendDevicePatch — only 401 retry.
- N+1 sequential synchronous HTTP calls per poll cycle (1 GET /Location + N GET /Device). Blocking calls on Hubitat scheduler thread. 3+ thermostats will feel slow.
- Vacation mode (date-ranged away) not in the API surface at all.
- Schedule blocks (time-of-day setpoint programs) never read or written — only the on/off toggle (SchedEnable) is exposed.

**Gemstone (Cognito + REST):**
- Driver controls entire string as one entity. Gemstone API supports per-segment (zone) patterns; has-gemstone exposes each zone as a separate HA light. No zone support in Hubitat driver.
- colorMode attribute is NOT cleared on hard-off (pattern == null in handleRefreshResponse). Level and effectName are cleared; colorMode is not.
- warnColorTemperatureFallback() fires on every setColorTemperature() call — correct behavior but noisy in automation-heavy setups.
- No schedule/timer API calls (sunrise/sunset triggers, scheduled patterns) — Gemstone app has these but no endpoint is called in the driver.
- Discovery picks first controller; no preference to target a named controller for multi-controller accounts.


---

## 2026-05-17T15:41:32Z — Cross-driver improvement scan (4-way)

Participated in 4-way driver improvement scan with Trinity, Tank, Switch. Findings consolidated by Squad. Orchestration log: .squad/orchestration-log/2026-05-17T15-41-32-cypher.md.
