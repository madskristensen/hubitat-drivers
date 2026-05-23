# Tank — Historical Archive (Summary)

**Archived:** 2026-05-23T15:47:33-07:00  
**Reason:** Primary history.md exceeded 15,360-byte threshold (was 15,802 bytes)

## Summary of Tank's Full Record (pre-2026-05-23)

**Role:** Driver Developer & App Implementer  
**Period Covered:** 2026-05-17 through 2026-05-23

### Major Deliverables

1. **Climate Advisor App + Driver (in-progress):**
   - Trinity completed design (v1, then v2 generic revision)
   - Tank awaiting Mads approval before implementation
   - v2 spec: parent app + child device, 8 user-configurable zones, generic preferences, SharpTools-first (no HomeKit required)

2. **Away Lights App v0.1.0 (shipped):**
   - Converted Mads's webCoRE piston to native Hubitat app
   - v0.8.1 resource cleanup: unconditional schedule cancellation on mode exit, structural mode-subscription fixes
   - Key learnings: Hubitat's mode subscription must be permanent; value-filtered subscriptions not yet supported

3. **Three Community Driver Forks Shipped:**
   - **PurpleAir AQI v0.4.0:** Fixed Groovy string-multiplication bug, latitude/longitude pole clamp, async HTTP error handling, AirQuality capability alignment
   - **Fully Kiosk v0.4.6:** MQTT support (v0.4.0), LWT offline signal, 5→4 arg `mqtt.connect()` fix, password masking in logs. Rapid iteration v0.1.0–v0.4.6
   - **Honeywell T6 Pro v0.4.0 (LIVE):** Critical fixes (txtEnable, device.currentValue nil-dereference, octal CMD_CLASS bug), Z-Wave clock sync optimization, thermostat fan state enum

### Key Architectural Decisions

- **Fork-cleanup pattern:** Preserve original copyright, add attribution block, audit + apply fixes with FIX comments, follow Daikin/SunStat templates
- **Groovy numerics:** `043` is octal; always use `0x` prefix for hex
- **Hubitat UX hygiene:** emitIfChanged + descriptionText + lastActivity (Pattern B for cloud, Pattern A for LAN ping)
- **Scope discipline:** Explicit 3-paragraph warnings achieve 100% compliance on multi-file changes
- **Mode subscription lifecycle:** Cannot unsubscribe/resubscribe to location mode for "only during X" semantics — subscription must be permanent

### Open Actions

- **Climate Advisor v2 implementation:** Pending Mads approval of generic architecture (awaiting signal)
- **Touchstone retry-logic improvements:** 3 Cypher-flagged defects (retryIndex reset on heartbeats, no retry cap, socket recycle) documented in decisions.md

### Learnings Archive

- Groovy Gotchas: string multiplication (`"60" * 5` → `"6060606060"`), octal literals, BigDecimal type coercion
- Z-Wave: fingerprint `inClusters` authoritative; CMD_CLASS_VERS author assumptions require cross-check
- Hubitat Cloud: PurpleAir refresh-on-save pattern; OpenWeatherMap custom attributes
- Driver Polling: Disable logic must never schedule `runIn(0, ...)` — coerce interval once before backoff math
- Test Coverage: Away Lights "already in window + sunset" edge case validates sunrise/sunset boundary correctly

---

*For full entry details, see Tank's primary history.md*
