# Cypher — Integration / Protocol Engineer

**⚠️ SUMMARIZED 2026-05-18T11:55:00Z — Main history archived to `history-archive.md` (updated file size check).**

---

## Current Active Work (2026-05-18)

### HPM Multi-Driver Bundle Feasibility Research (Session cypher-4)
- **Shipped:** 2026-05-18
- **Verdict:** ✅ Feasible — HPM natively supports multiple drivers in one manifest
- **Key Findings:**
  - In-repo precedent: SunStat already ships 2-driver manifest (parent + child)
  - Bundle manifest goes at repo root; per-driver manifests remain unchanged (additive)
  - UUID reuse mandatory for HPM Match-Up deduplication
  - Version coupling: bundle version independent of per-driver versions
  - Release workflow needs update: add root manifest path trigger, handle `driver_dir == "."` case

### Tuya Autodiscovery on Hubitat Feasibility Research (Session cypher-4)
- **Shipped:** 2026-05-18
- **Verdict:** ⚠️ Feasible-with-caveats — passive UDP blocked, but active TCP Plan B viable
- **Key Findings:**
  - ❌ Passive UDP broadcast listening: NOT supported by Hubitat (staff confirmed 2018, unchanged)
  - ✅ Active TCP probe: feasible via sequential rawSocket.connect() on /24 subnet port 6668
  - Tuya v3.3 discovery via active TCP: DP_QUERY frame + gwId match (fail-closed)
  - Scan time: 2s/IP worst case → ~8 min full sweep; smart ±20 range typically <1 min
  - Fallback (primary recommendation): DHCP reservation in router (zero driver code)

### Gemstone Zones / Segments — API Feasibility Research (Session cypher-3)
- **Shipped:** 2026-05-17 
- **Verdict:** ✅ Feasible via multi-instance + controllerName preference (Option A-lite)

## Key Findings From Tank-15 Support

Cypher-4 research directly enabled two Tank-15 ships:
1. **HPM Bundle v1.0.0:** Research validated manifest schema, UUID reuse requirement, release workflow changes
2. **Touchstone v0.1.20 Discovery:** Research confirmed active TCP approach as only viable Hubitat path for Tuya autodiscovery

---

## Team updates

- 2026-05-17: Participated in top-3 driver improvements batch — sunstat v0.1.6, touchstone v0.1.6, gemstone v0.4.9.
- 2026-05-18: Daikin WiFi research memo (`daikin-driver-assessment.md`) was used as direct input for Tank-2's clean-room driver implementation (commit b26c04f). Clean-room approach proved the feasibility of independent authorship using only research prose, no upstream source code.

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
