# Cypher — Integration / Protocol Engineer

**⚠️ SUMMARIZED 2026-05-18T11:55:00Z — Main history archived to `history-archive.md` (updated file size check).**

---

## Learnings

### Bosch Home Connect Protocol + craigde Driver Audit (2026-05-18)

**Full memo:** `.squad/decisions/inbox/cypher-bosch-home-connect-audit.md`

**Protocol shape:**
- Transport: HTTPS to `api.home-connect.com`. No local protocol. No LAN fallback.
- Auth: OAuth2 Authorization Code Grant (Bosch also supports Device Flow — see skill). Access token 86400s (24h). Refresh token long-lived, may rotate.
- Discovery: `GET /api/homeappliances` → array of `{haId, name, type, connected}`.
- Status: `GET /api/homeappliances/{haId}/status` → array of key/value items.
- Door state key: `Refrigeration.Common.Status.Door.Refrigerator` → `BSH.Common.EnumType.DoorState.Open/Closed`. Per-compartment variants: `.Freezer`, `.FlexZone`, `.ChillerLeft`, `.ChillerRight`, `.FlexCompartment`.
- SSE: `GET /api/homeappliances/{haId}/events` — text/event-stream. **IS usable in Hubitat via EventStream interface** (contrary to earlier skill note) but fragile — requires watchdog + reconnect infrastructure.
- Rate limits: 1,000 requests/day, 50/minute, 10 SSE channels, 100 token refreshes/day.

**Sandbox safety verdict: ✅ CONFIRMED.** Auth Code Grant via App `mappings {}`, `httpPost` token exchange, `asynchttpGet` REST, EventStream SSE — all safe. No crypto, no reflection.

**OAuth2-in-Hubitat pattern:** Auth Code Grant requires a Hubitat **App** (not a Driver) to use `mappings {}`. Per-hub callback URI (`https://cloud.hubitat.com/api/{hub-uuid}/apps/{app-id}/oauth/callback`) must be pre-registered in the cloud provider's portal. This is one-time-per-user friction, not a blocker. `atomicState` survives hub reboots and is the correct token store. Proactive request-gated refresh (check expiry < 60s on every call) is the pattern craigde uses; works without a cron scheduler.

**Existing driver audit verdict:** `craigde/hubitat-homeconnect-v3` (Craig Dewar) v3.1.7 (2026-03-13) is ACTIVE, HPM-published, covers 13 appliance types including a complete FridgeFreezer driver with per-compartment ContactSensor, temperature monitoring, all modes, debug commands. **INSTALL verdict** — do not build from scratch. Rubric score 67/100 (Conditional Fit) but strategic recommendation is INSTALL because the driver is comprehensive and active.

**SSE-via-EventStream correction:** The `home-connect-oauth-device-flow/SKILL.md` says "SSE not viable on Hubitat." This is WRONG for App/Driver code that uses Hubitat's EventStream interface. SSE IS viable but requires substantial reconnect/watchdog infrastructure (craigde needed 22+ patches in 65 days to stabilize it). Updated skill with caveat.

---

## Learnings

### Rainbird LNK WiFi Protocol (2026-05-18)

**Full memo:** `.squad/decisions/inbox/cypher-rainbird-lnk-feasibility.md`

**Protocol shape:**
- Transport: HTTP POST to `http://{ip}/stick` port 80 (or HTTPS 443). Standard HTTP, not raw TCP.
- Envelope: JSON-RPC 2.0, `method: "tunnelSip"`, `params.data` = SIP command as hex string, `params.length` = byte count.
- Encryption: AES-256-CBC. Key = SHA-256(password) as 32-byte key. IV = 16 random bytes. Padding: custom (append `\x00\x10`, fill with `\x10`). Frame: [SHA-256(plaintext) 32B][IV 16B][ciphertext].
- NO HMAC — first 32 bytes are `SHA-256(plaintext)` hash only, not keyed.
- Auth: none beyond shared password. No session token. Every request standalone-encrypted.
- Push: none. Pure poll. HA polls every 60 seconds.

**Key SIP opcodes:** `02` model/version, `05` serial, `3F` active zones (bitmask), `39` manual run zone (minutes!), `40` stop all, `3E` rain sensor, `36` get rain delay, `37` set rain delay, `4C` combined state snapshot.

**Sandbox safety verdict: ✅ CONFIRMED.** `javax.crypto.Cipher / AES/CBC/NoPadding / SunJCE` is confirmed sandbox-safe — used in two existing Hubitat Rainbird drivers (craigde/jbilodea and MHedish) AND in our own Touchstone driver (AES-ECB variant). `ByteArrayOutputStream`, `MessageDigest`, `SecretKeySpec`, `IvParameterSpec` all safe. `System.arraycopy` is still blocked — use loops or `ByteArrayOutputStream.write()`.

**Existing driver audit verdict:** MHedish/Hubitat `RainBird-LNK-Wi-Fi-Module.groovy` v1.0.0.0 (last commit 2026-05-07) is active, HPM-published, parent/child architecture, proper encryption, multi-firmware support. DO NOT reinvent. **IMPROVE-EXISTING.** craigde/jbilodea v0.92 (2020) is stale and superseded.

**Rubric score:** 92/100. Strong Fit. IMPROVE-EXISTING verdict (not BUILD) because MHedish is active and covers all functionality.

**Community driver audit pattern:** New skill written at `.squad/skills/community-driver-audit-before-build/SKILL.md`.

---

### Driver Opportunity Shortlist (2026-05-18)

**Full report:** `.squad/decisions/inbox/cypher-driver-opportunities-2026.md`

**Top 5 picks (ranked):**

1. **Enphase Envoy / IQ Gateway (Pool A)** — Local HTTP REST (`/api/v1/production`). Fw7+ adds JWT (12-month cache, seeded once from Enlighten cloud). Largest PNW market gap. No Hubitat driver exists. Effort: M.
2. **Tesla Wall Connector Gen 3 (Pool A)** — `GET /api/1/vitals`, no auth, unauthenticated LAN. Simplest protocol in the list. Returns charging state + session energy + power. Effort: S.
3. **Tibber energy price (Pool B)** — GraphQL POST to `api.tibber.com/v1-beta/gql` with Bearer token (free, permanent). Returns `current { total level }`. Hourly poll. Cloud-kill risk: very low (it's their growth funnel). Effort: S.
4. **Reolink camera/doorbell (Pool A)** — Local HTTP CGI API (`/api.cgi`). Officially Reolink-sanctioned (per HA integration page). Doorbell-as-trigger value. Effort: M (token refresh + multiple entities).
5. **Mitsubishi mini-split via ESPHome CN105 (Pool A)** — ESPHome REST (climate domain), same pattern as ratgdo. Requires user to own CN105-to-ESP32 bridge hardware. PNW heat pump dominant brand. Effort: S (driver) / M (user setup).

**Cloud-kill risk summaries applied:**
- Enphase: Low (vendor wants monitoring data shared)
- Tesla WC: None (local, no cloud dependency)
- Tibber: Very low (developer API is their growth funnel)
- Reolink: Low (officially authorized by Reolink)
- Flo by Moen: HIGH — unofficial API, no developer program → Anti-list
- Ring: Anti-list (hostile, cat-and-mouse)
- Wyze: Anti-list (hostile, repeated breaks)

**Anti-list confirmed:** Ring, Wyze, Nest, Arlo, Flo by Moen, Velux KLF 200 (binary TCP), Pentair ScreenLogic (binary TCP), Flexit Nordic (BACnet UDP), MelCloud.

**Flagged for deeper feasibility:** Enphase fw7 JWT token handling; Mitsubishi CN105 ESPHome entity shape mapping.

---

### MyQ / ratgdo Feasibility Research (2026-05-18)

**Full memo:** `.squad/decisions/inbox/cypher-myq-feasibility.md`

**Key facts for future tasks:**

1. **MyQ cloud API is permanently dead for third parties.** Chamberlain blocked it October 2023. Home Assistant removed the integration December 2023. `homebridge-myq` officially retired. No developer program for individuals. `chamberlaingroup.com/developer` returns 404.

2. **ratgdo ESPHome firmware is the replacement ecosystem.** Hardware: ratgdo (ratcloud.llc, ~$45) and Konnected GDO blaQ (konnected.io, $89). Both run ESPHome firmware and expose identical REST API on port 80. Firmware actively maintained — latest release April 25, 2026 (commit aeeb338).

3. **ESPHome REST API for garage door (cover entity):**
   - GET `/cover/Garage Door` → `{"state":"OPEN","value":1.0,"current_operation":"IDLE"}`
   - POST `/cover/Garage Door/open` → opens
   - POST `/cover/Garage Door/close` → closes
   - POST `/cover/Garage Door/stop` → stops
   - POST `/cover/Garage Door/set?position=0.5` → partial position
   - GET `/binary_sensor/Obstruction` → `{"state":"ON","value":true}`
   - GET `/binary_sensor/Motion` → motion (Security+ 2.0 only)
   - GET/POST `/light/Light` → light control
   - SSE at `/events` provides push but is **not usable from Hubitat** (streaming HTTP blocked by sandbox)

4. **MQTT firmware topics (legacy, v2.5 era):**
   - Status: `<prefix>/<device>/status/door` → "opening"/"open"/"closing"/"closed"
   - Command: `<prefix>/<device>/command/door` → "open"/"close"/"stop"
   - Light: status and command similarly. **Cannot use from Hubitat** (no MQTT client in sandbox).

5. **Konnected blaQ explicitly markets Hubitat support** with a demo GIF on product page as of 2026-05-18. Protocol-identical to ratgdo ESPHome firmware.

6. **No Hubitat ratgdo driver found on GitHub** (search returned 0 results 2026-05-18). Real gap exists.

7. **Recommended driver shape:** ratgdo ESPHome REST HTTP driver using `asynchttpGet` polling at 5s interval. Capabilities: `GarageDoorControl`, `ContactSensor`, `Switch` (light), `HealthCheck` (full Pattern A — local LAN). Entity names should be user-configurable.

8. **Cloud-killed API evaluation pattern documented** as new skill: `.squad/skills/cloud-killed-api-evaluation/SKILL.md`.

---

### Daikin BRP069B Complete Endpoint Catalog (Session cypher-5, 2026-05-18)

Full reference for any future Daikin driver work. Sources: ael-code/daikin-control README, Apollon77/daikin-controller src/DaikinACRequest.ts + DaikinAC.ts v2.2.1.

**Implemented in our driver (v0.1.5):**
- `/aircon/get_control_info` — mode, pow, setpoint, fan rate, swing
- `/aircon/set_control_info` — all control writes (requires pow, mode, stemp, shum, f_rate, f_dir)
- `/aircon/get_sensor_info` — indoor/outdoor temp, humidity
- `/aircon/get_model_info` — model name, fw rev, humidity/swing capability flags
- `/aircon/get_special_mode` + `/aircon/set_special_mode` — econo/powerful mode (adv field)
- `/aircon/get_week_power_ex` — weekly kWh (s_dayw slash-delimited)
- `/aircon/get_year_power_ex` — monthly kWh (this_year slash-delimited)

**Not called — worth adopting:**
- `/common/basic_info` — MAC, firmware, device name, `lpwFlag`. If `lpwFlag=1` (some BRP069A units), all set calls need `lpw=` param appended. Apollon77 reads this at init and auto-configures lpw globally.
- `/aircon/get_demand_control` + `/aircon/set_demand_control` — max-power cap for demand-response. Confirmed in Apollon77 v2.2.1 (2025-05-24). Device-side validation needed on BRP069B41.

**Not called — maybe later:**
- `/common/get_notify` — filter maintenance alert (community-reported; schema undocumented)
- `/aircon/get_day_power_ex` — hourly kWh for today (Apollon77 TODO list, typo'd `get_day_paower_ex`)
- `/aircon/get_week_power` + `/aircon/get_year_power` — older non-`_ex` variants for BRP069A firmware fallback

**Not called — skip:**
- `/common/get_remote_method` + `set_remote_method` — cloud polling negotiation; irrelevant for LAN
- `/aircon/get_program` + `set_program` — on-device schedule; deferred per Trinity memo
- `/aircon/get_scdltimer` + `set_scdltimer` — on/off weekly timer; same rationale
- `/aircon/get_timer`, `/aircon/get_target`, `/aircon/get_price` — unknown/undocumented purpose
- `/common/set_led` — non-functional on tested hardware (ael-code note)
- `/common/reboot` — dangerous; 30s disconnect
- `/common/set_regioncode`, `/common/get_datetime`, `/aircon/get_wifi_setting` — cloud-facing or credential-risk

### Top Perf/Quality Findings from v0.1.4/v0.1.5 (Session cypher-5, 2026-05-18)

Driver is production-quality overall. Remaining items are maintenance-tier:

1. **🔴 Null crash** — `setHeatingSetpoint(null)` / `setCoolingSetpoint(null)` throws NPE at `temp.toString()` (line 340, 350). Fix: null guard before BigDecimal construction.
2. **🟡 Missing log interpolation** — `"ret="` without `${kv.ret}` in `parseModelInfo` (line 492) and `handleSetSpecialMode` (line 690).
3. **🟡 Energy poll when off** — `refreshEnergy()` fires 2 HTTP calls even when `switch == "off"`.
4. **🟡 Dead computation** — `handleYearPower` computes `yearTotal` (line 667) but never emits it. Debug-log only.
5. **🟢 All else clean** — asynchttpGet, unschedule/schedule ordering, emitIfChanged coverage, .isNumber() guards, sandbox compliance, °F↔°C conversion — all verified correct.

---

## Key Findings From Tank-15 Support

Cypher-4 research directly enabled two Tank-15 ships:
1. **HPM Bundle v1.0.0:** Research validated manifest schema, UUID reuse requirement, release workflow changes
2. **Touchstone v0.1.20 Discovery:** Research confirmed active TCP approach as only viable Hubitat path for Tuya autodiscovery

---

## Archive

**Older sessions (2026-05-16 to 2026-05-17):** SunStat/Watts research, Bosch feasibility, Gemstone color investigation, Touchstone DP analysis, Tuya key extraction audit, Hubitat sandbox learnings, system.arraycopy blocker, and cross-driver patterns saved to `history-archive.md`.

## Team Updates

**2026-05-18 Team Update (Scribe):** Bosch Home Connect audit complete — 67/100 rubric, but INSTALL verdict wins. craigde/hubitat-homeconnect-v3 is comprehensive (13 appliances), HPM-published, actively maintained. Discovered OAuth Authorization Code Grant pattern via Hubitat App cloud callbacks—reusable for future cloud-OAuth drivers. Verdict = install, not build.

### Hubitat Write-Only Property Gotcha + HubAction Constructor Table (Tank-3, 2026-05-18)

**Key Lessons from Daikin v0.1.1 hotfix:**

1. **Groovy JavaBean Naming + Scheduler Method Shadowing**  
   Custom command setX(x) creates a write-only property x on the driver object. If the code also calls the platform's x() scheduler method (e.g., schedule(cron, method)), Groovy's dynamic dispatch resolves the name as the write-only property instead of the method → runtime error ("Cannot read write-only property"). Workaround: use unEvery* idiomatic methods instead of calling schedule by name. Affected drivers: any Thermostat capability driver that calls schedule(cron, method) in addition to providing the setSchedule() stub.

2. **HubAction Constructor Overloads**  
   Valid forms for LAN HTTP: HubAction(String), HubAction(String, Protocol), HubAction(String, Protocol, String dni), HubAction(String, Protocol, String dni, Map options), HubAction(Map), HubAction(Map, Protocol) ← **preferred for GET**. Invalid form: HubAction(Map, Protocol, Map) does NOT exist. Callback must be inside the params Map when using 2-arg form.

3. **Test on First Install Before Shipping**  
   Both bugs were immediately visible on first Save Preferences after install. Smoke-test drivers on hub before tagging v1.0 releases.

### Daikin v0.1.2 Lesson: HubAction Map-Based Constructor Instability (Tank-4, 2026-05-18)

**HubAction Map-based LAN constructor audit conclusion:**

Two independent firmware failures observed in the Daikin WiFi driver:
1. **v0.1.0:** HubAction(Map, Protocol, Map) 3-arg — does not exist on Mads's firmware
2. **v0.1.1:** HubAction(Map, Protocol) 2-arg — also does not exist on Mads's firmware

**Decision: Retire Map-based HubAction entirely for LAN HTTP.** Tank-4 completed a full rewrite of Daikin v0.1.2 (commit e45967e) replacing all Map-based HubAction forms with asynchttpGet (Hubitat's documented modern HTTP-over-LAN API).

**Skill updated:** hubitat-hubaction-constructors/SKILL.md bumped to confidence **medium**; documents that **ALL Map-based HubAction forms are unreliable across firmware versions.**

**Pattern for future LAN HTTP drivers:** Use asynchttpGet (send-helper + AsyncResponse callback pattern) documented in new skill hubitat-asynchttpget-pattern/SKILL.md (confidence **medium**).

### Daikin v0.1.4 Roadmap Complete (Tank-6, 2026-05-18)

**v0.1.0+ roadmap CLOSED.** Tank-6 shipped the final three capability items in v0.1.4 (commit 1dd21fe):
1. **Econo/Powerful mode** — setSpecialMode command + specialMode ENUM, polled every fast-refresh via /aircon/get_special_mode
2. **get_model_info runtime cache** — Called in initialize(); caches name, firmware, humidity/swing sensor flags for diagnostics
3. **Event hygiene audit** — All five checks passed (emitIfChanged, descriptionText, 60s throttle, no displayed:false, no isStateChange:true anti-patterns)

Your v0.1.0 capability gap research directly enabled this final ship. Hardware verification pending on Mads's BRP069B unit.

---

## Team Updates — Daikin API Audit Complete (2026-05-18 — 21:29:42Z)

**API completeness audit shipped:** Catalogued all 28 Daikin BRP069B endpoints. Our driver implements the 7 that matter (control, power, special mode, model info, swing direction).

**Skip list (10 endpoints):** Cloud negotiation, scheduling, timers, undocumented, dangerous, credential-risk endpoints. Rationale documented in .squad/decisions.md.

**Maybe later (4 endpoints, v0.1.7+ candidates):** Demand control (needs real-device validation), filter alerts (schema unknown), hourly energy (Apollon77 TODO), legacy power variants (low priority).

**Driver quality findings — v0.1.5 production-ready:**
- 🔴 **Critical:** NPE in setpoint setters when temp is null (RM "clear" cmd). Guard needed at lines 340/350. (30 min fix)
- 🟡 **Minor:** Missing log interpolation (lines 492, 690); energy poll when off (line 385); dead yearTotal (line 667). (5+5+30 min fixes)
- 🟡 **Optional:** BRP069A backward compat reading lpwFlag. (1–2h, needs hardware testing)

**Overall verdict:** No structural issues. All remaining items are maintenance-tier. v0.1.6 scope awaiting Mads's decision. Full audit memo: .squad/files/daikin-research/daikin-api-perf-audit-memo.md
