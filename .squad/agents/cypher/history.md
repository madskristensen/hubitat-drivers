# Cypher — Integration / Protocol Engineer

**⚠️ SUMMARIZED 2026-05-18T11:55:00Z — Main history archived to `history-archive.md` (updated file size check).**

---

## Learnings

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
