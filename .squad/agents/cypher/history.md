# Cypher — Integration / Protocol Engineer

**⚠️ SUMMARIZED 2026-05-18T01:41:11Z — Main history moved to `history-archive.md` (file was 19,042 bytes).**

---

## Current Active Work (2026-05-18)

### HPM Multi-Driver Bundle Feasibility Research (Session cypher-4)
- **Shipped:** 2026-05-18
- **Verdict:** ✅ Feasible — HPM natively supports multiple drivers in one manifest
- **Deliverable:** `.squad/decisions/inbox/cypher-hpm-bundle-feasibility.md` (merged into decisions.md)
- **Duration:** 558s
- **Key Findings:**
  - In-repo precedent: SunStat already ships 2-driver manifest (parent + child)
  - Bundle manifest goes at repo root; per-driver manifests remain unchanged (additive)
  - UUID reuse mandatory for HPM Match-Up deduplication
  - Version coupling: bundle version independent of per-driver versions
  - Release workflow needs update: add root manifest path trigger, handle `driver_dir == "."` case
  - Unknown edge case flagged: does HPM show one update or two when user installs via both bundle and per-driver URLs? (Switch to test)

### Tuya Autodiscovery on Hubitat Feasibility Research (Session cypher-4)
- **Shipped:** 2026-05-18
- **Verdict:** ⚠️ Feasible-with-caveats — passive UDP blocked, but active TCP Plan B viable
- **Deliverable:** `.squad/decisions/inbox/cypher-tuya-autodiscovery-feasibility.md` (merged into decisions.md)
- **Duration:** 558s (combined with HPM research)
- **Key Findings:**
  - ❌ Passive UDP broadcast listening: NOT supported by Hubitat (staff confirmed 2018, unchanged)
  - ✅ Active TCP probe: feasible via sequential rawSocket.connect() on /24 subnet port 6668
  - Tuya v3.3 discovery via active TCP: DP_QUERY frame + gwId match (fail-closed)
  - Scan time: 2s/IP worst case → ~8 min full sweep; smart ±20 range typically <1 min
  - Fallback (primary recommendation): DHCP reservation in router (zero driver code)
  - Risks for Switch: gwId in response must be confirmed, hub rate-limiting on rapid connects
  - Sources: Hubitat staff (2018 UDP confirmation), tinytuya, HA tuya-local, Tuya v3.3 protocol docs

### Gemstone Zones / Segments — API Feasibility Research (Session cypher-3)
- **Shipped:** 2026-05-17 
- **Verdict:** ✅ Feasible via multi-instance + controllerName preference (Option A-lite)
- **Reference impls:** sslivins/pygemstone + sslivins/hass-gemstone
- **Deliverable:** Merged into decisions.md (used for Tank-15 Gemstone v0.4.10 design)

## Key Findings From Tank-15 Support

Cypher-4 research directly enabled two Tank-15 ships:
1. **HPM Bundle v1.0.0:** Research validated manifest schema, UUID reuse requirement, release workflow changes
2. **Touchstone v0.1.20 Discovery:** Research confirmed active TCP approach as only viable Hubitat path for Tuya autodiscovery

---

## Archive
**Previous sessions:** SunStat research, Bosch feasibility analysis, and detailed protocol learnings saved to `history-archive.md`

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

## 2026-05-17 (session cypher-8) — Gemstone Zones / Segments API Feasibility

**Full report:** `.squad/decisions/inbox/cypher-gemstone-zones-feasibility.md`

**Verdict:** ✅ Feasible — proceed.

### Key findings

1. **Zones = multiple physical controllers.** The Gemstone cloud API does NOT expose per-pixel segmentation within a single controller. "Zones" in the Gemstone app are separate physical controllers (hardware units), each with its own UUID (`deviceId`). All control endpoints already accept any `deviceId` via the `deviceOrGroupId` query parameter.

2. **No new API calls needed.** The current driver already calls `GET /homegroup/devices` and receives the full list of controllers; it silently discards all but `devices[0]`. The entire zone-support change is: stop discarding, let the user pick which controller to bind to via a new `controllerName` preference.

3. **Device groups** (`GET /deviceGroup/list?homegroupId=<id>`) — the multi-controller group endpoint exists in the API but the iOS app capture saw only an empty list. Schema is unknown. The `deviceOrGroupId` param naming implies groups are targetable; unconfirmed.

4. **pygemstone models confirm no segment fields.** `Pattern.from_api()` parses `colors` (a list of ABGR ints for the palette), `brightness`, `speed`, `direction`, `animation`, `backgroundColor` — no pixel-range, zone-id, or segment-index fields. Per-pixel addressing is not surfaced at all.

5. **hass-gemstone architecture confirms one entity per device.** `async_setup_entry()` iterates all devices across all homegroups and creates one `GemstoneCoordinator` + one `GemstoneLight` entity per physical controller. Hubitat's analog is one driver instance per controller.

### Recommended architecture

**Option A-lite — multi-instance with `controllerName` preference.** Same driver, new optional preference. Blank = backward-compatible first-device behavior. Non-blank = bind to named controller. User creates one Hubitat device per zone. No parent/child needed for the typical 2–4 controller case.

### Reference repos discovered

- `sslivins/pygemstone` — https://github.com/sslivins/pygemstone (low-level client, all endpoint models)
- `sslivins/hass-gemstone` — https://github.com/sslivins/hass-gemstone (HA integration, multi-device pattern)

---

## Learnings Summary

### 2026-05-17 — Gemstone zones: per-zone control is per-physical-controller

**Definitive finding from pygemstone + hass-gemstone capture analysis:**
- Gemstone "zones" = separate physical controllers (separate UUIDs). No per-pixel segmentation API exists.
- All control endpoints (`/deviceControl/onState`, `/deviceControl/play/pattern`, `/deviceControl/currentlyPlaying`) accept `?deviceOrGroupId=<id>` where `id` can be any discovered controller's UUID.
- The current driver already calls `GET /homegroup/devices` and receives all controllers; it only uses `devices[0]`.
- `GET /deviceGroup/list?homegroupId=<id>` is real but returned empty in the capture; schema unknown. Potential fan-out command target.
- `Pattern.colors` is a color palette list, not per-pixel addressing.
- **Minimum viable zone support**: add `controllerName` preference, bind by name after discovery, keep backward compat.



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

---

## 2026-05-17T15:50:06-07:00 — Watts Home Boost API Research

**Full report:** `.squad/decisions/inbox/cypher-sunstat-boost-endpoint.md`

**Verdict:** No boost endpoint exists in the Watts Home API.

---

## 2026-05-17 (session cypher-9) — HPM Bundle + Tuya Autodiscovery Feasibility

**Full reports:**
- `.squad/decisions/inbox/cypher-hpm-bundle-feasibility.md` — ✅ Feasible
- `.squad/decisions/inbox/cypher-tuya-autodiscovery-feasibility.md` — ⚠️ Feasible-with-caveats (Plan B only)

### HPM Bundle
- HPM `drivers` array accepts N entries natively; SunStat already uses 2 (in-repo precedent)
- `required: false` makes driver optional — user prompted per-entry during install
- All 3 driver IDs can be reused in bundle manifest (HPM matches on id+name+namespace)
- Bundle manifest at repo root; release.yml needs path trigger update + bundle-case handler (no .groovy → skip changelog extraction)
- Version coupling: top-level package `version` (independent of per-driver versions); bump bundle whenever any driver bumps
- Reference packages confirmed: gilderman/utec-lock (apps+drivers+libraries), spinrag/hubitat (full metadata)

### Hubitat UDP Capability (definitive)
- **Hubitat CANNOT passively receive UDP broadcasts.** Confirmed by Hubitat staff (community.hubitat.com/t/udp-broadcast-support/3957/11, December 2018), unchallenged since.
- `LAN_TYPE_UDPCLIENT` (sendHubCommand) = send UDP + receive reply to that specific send. Request-reply only.
- `interfaces.rawSocket` = TCP only. No UDP bind/listen possible.
- Tuya devices broadcast on 6666/6667 spontaneously; passive listening required; Hubitat blocks this path entirely.

### Plan B for Tuya Autodiscovery
- Active TCP probe on port 6668 via `interfaces.rawSocket` (driver already knows this API)
- Filter match by gwId in device response
- Sequential /24 scan from last-known IP; ~2s per IP worst case (full /24 ≤ 8 min)
- Ship as explicit "Discover" command button, NOT background autodiscovery
- DHCP reservation in router = primary/zero-code recommendation for users

---

## Learnings

### 2026-05-17T19:28:07-07:00 — Hubitat Platform: UDP Passive Listen Not Supported

- Hubitat staff confirmation (2018): `LAN_TYPE_UDPCLIENT` only for request-reply UDP. No passive UDP listener API exists. `interfaces.rawSocket` is TCP-only.
- For any feature requiring "listen for inbound UDP from unknown sources" (Tuya, LIFX, WiZ discovery): NOT feasible. Must use active TCP probing or user-supplied IP.
- Research method: search GitHub for `interfaces.rawSocket` patterns in Groovy drivers; cross-check community.hubitat.com UDP broadcast thread.

### 2026-05-17T19:28:07-07:00 — HPM Multi-Driver Bundle: Direct Precedent in This Repo

- SunStat `packageManifest.json` already ships a 2-driver manifest (parent + child). Schema supports N entries trivially.
- Canonical schema source: HubitatCommunity/hubitatpackagemanager README.
- Release.yml path `find drivers -type f` excludes root-level manifests; needs a small update for bundle support.

### 2026-05-17T15:50:06-07:00 — Watts Home API: No Boost Surface

**Confirmed from `homebridge-tekmar-wifi` reference (tree SHA `553ce89`, 2026-01-18):**

- The Watts Home API (`https://home.watts.com/api/`) exposes **no boost, hold-timer, or BoostUntil field** anywhere. Exhaustively verified across all 8 source/doc files in the reference implementation.
- `PATCH /Device/{id}` accepts only: `{ Settings: { Mode?, Heat?, Cool?, Fan?, Schedule? } }`. No `Boost`, `BoostMinutes`, `BoostUntil`, or `Hold` write field exists.
- `Target.Hold` appears in GET responses (always `0` in all examples) but is **never written** by the reference implementation. Its semantics are unknown. May be firmware-reserved. Not a usable boost API surface today.
- `SchedEnable` (`"On"` / `"Off"`) can be toggled off during a pseudo-boost to prevent schedule from overwriting the elevated setpoint.
- **Correct implementation path:** Driver-managed pseudo-boost — raise `Target.Heat` by a configurable delta, set `SchedEnable = "Off"`, store pre-boost setpoint in `state`, schedule `runIn` for auto-cancel, and re-check on each poll in case Hubitat restarts mid-boost.
- Rate limit for the API is **undocumented**; 2 PATCHes per boost start/cancel is well within any reasonable budget.


### 2026-05-17T16:34:52-07:00 — DP-map vs real hardware: tuya-local YAML cannot be trusted for write semantics

**Lesson learned during Touchstone DP 105/109 investigation:**

- A YAML entry claiming `type: string` with numeric-looking `dps_val` values is **not a guarantee the device will accept writes** of that type. The YAML reflects how the HA tuya-local integration *mapped* the DP, not necessarily what the hardware firmware actually does.
- Real-hardware evidence always supersedes YAML documentation. If Mads says writes have no effect, the YAML is suspect — it may reflect a different firmware version, a different sub-model, or an incorrect community contribution.
- **`setRawDP` is an invalid test for string-typed DPs with numeric values.** The driver's `coerceRawValue` converts `"5"` → `Integer 5`. Sending integer `5` when the device expects string `"5"` results in a silent reject. Always test string-DP writes via the dedicated typed command, not via setRawDP.
- The `optional: true` flag in a tuya-local YAML DP entry is a real signal: the DP may not exist on all firmware variants or sub-models of the listed device.
- **Never trust prior DP research over real-hardware evidence.** Re-verify by reading the actual YAML line-by-line and cross-checking the exact write path in the driver before declaring a DP "confirmed writable."

**Full investigation:** `.squad/decisions/inbox/cypher-touchstone-dp105-dp109-investigation.md`

---

## Team updates

- 2026-05-17: Participated in top-3 driver improvements batch — sunstat v0.1.6, touchstone v0.1.6, gemstone v0.4.9.


## 2026-05-17 (session cypher-2) — Touchstone DP 105 / DP 109 real-hardware investigation

**Requested by:** Mads (community-beta self-testing; setRawDP doesn't work for DPs 105, 109)

### Root cause (Confirmed — Hypothesis B)

coerceRawValue() corrupts string-typed DPs. When called with numeric-looking strings (e.g., "5"), the function returns integers instead of strings. DP 105 (log brightness) and DP 109 (ember brightness) both declared as 	ype: string in Tuya YAML; device rejects integer-typed values.

**Evidence:**
- DP 105: YAML 	ype: string, values "1"–"12" (quoted strings). setRawDP 105 "5" sends Integer 5, device expects String "5".
- DP 109: YAML 	ype: string, optional: true, values "L0"–"L5" (capital-L prefix). setRawDP 109 "1" sends Integer 1 + wrong value format; correct is "L1"–"L5".
- setRawDP command documentation warns: "whole numbers become integers" — using it to test string DPs is invalid.

### Resolution status

**DP 105 — Hypothesis C (read-only) unconfirmed:**
- Dedicated setLogBrightness("12") command sends correct string type per YAML
- Never tested on real hardware; may actually work
- Mads must test setLogBrightness in isolation from device page

**DP 109 — No inbound code, marked optional:**
- No dp109 attribute in driver; whether device pushes DP 109 status is unverified
- May not exist on all Sideline Elite firmware (optional: true in YAML)
- Likely test used wrong value format ("1" instead of "L1")

### v0.1.10 actions (assigned to Tank)

1. Add setRawDPString command or quoted-string input syntax to skip coercion
2. Add setEmberBrightness command with "L0"–"L5" enum for DP 109
3. Add dp109 inbound attribute for status tracking
4. If setLogBrightness also fails empirically: remove/deprecate DP 105 write

### Deliverables

- Merged investigation into .squad/decisions.md with full YAML excerpts, hypothesis verdict, sources
- Assigned v0.1.10 fixes to Tank
- Pending Mads's empirical test to confirm/refute Hypothesis C

---

## Team Update — 2026-05-18

### ⚠️ System.arraycopy is Sandbox-Blocked on Hubitat

**Alert for Tuya/protocol work:** `java.lang.System.arraycopy` is on the Hubitat driver sandbox's MethodCallExpression blocklist (same as `java.util.zip.CRC32` import block + reflection API block).

- **Example error:** `Expression [MethodCallExpression] is not allowed: java.lang.System.arraycopy(...) at line N`
- **Implication:** Perf-todo #7 (Touchstone byte-copy optimization) is permanently unachievable on Hubitat. Use primitive `int`-counter `for` loops instead.
- **Rule:** Never use `System.arraycopy` in any Hubitat Groovy driver. Add to code review checklist.
- **Fixed in:** Touchstone v0.1.30 hotfix (reverted v0.1.29 optimization)
- **Decision:** `.squad/decisions/decisions.md::tank-touchstone-v130-arraycopy-fix` (status: done)
