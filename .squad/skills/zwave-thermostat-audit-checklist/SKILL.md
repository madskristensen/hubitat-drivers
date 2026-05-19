# Skill: Z-Wave Thermostat Driver Audit Checklist

**Confidence:** medium  
**First validated:** 2026-05-18 — Honeywell T6 Pro TH6320ZW2003 Z-Wave survey  
**Author:** Cypher

---

## Summary

A repeatable checklist for auditing a Z-Wave thermostat Hubitat driver against the device's full CC surface. Covers CC gap analysis, configuration parameter completeness, and specific features that are commonly missing in community-originated thermostat drivers.

---

## Step 1: Confirm Model Identity

1. Find the driver's `fingerprint` block: extract `mfr`, `prod`, `deviceId` (hex).
2. Cross-reference against Z-Wave Alliance DB:
   - `https://products.z-wavealliance.org/products/<ID>/` *(often 404; use mirrors)*
   - OpenZWave XML: search `github.com/OpenZWave/open-zwave` or `github.com/domoticz/domoticz Config/`
   - OpenHAB ZWave binding DB: `github.com/openhab/org.openhab.binding.zwave doc/`
3. Note if there are multiple SKUs (e.g., TH6320ZW2003 vs TH6320ZW2007 — newer often adds SmartStart/S2, Indicator CC, extra params).

---

## Step 2: Build the CC List

**Primary source:** fingerprint `inClusters` string in the driver. This is authoritative (extracted from device at inclusion time).

**Cross-reference:** `CMD_CLASS_VERS` map in the driver. Note that this map can contain CCs the driver author assumed are present — they may NOT be in the fingerprint. Flag discrepancies.

**For each CC in inClusters, check:**

| Check | Question |
|-------|----------|
| Version registered? | Is the CC hex in `CMD_CLASS_VERS` with the correct version? |
| Handler present? | Does `zwaveEvent()` have a handler for the Report frame? |
| Commands called? | Does `refresh()` / `pollDeviceData()` / commands issue the correct Get? |
| Attribute emitted? | Does the handler call `sendEvent()` / `eventProcess()` with the parsed value? |

**⚠️ Groovy octal trap:** In Groovy, `043` is **octal 43 = decimal 35 = 0x23**, NOT hexadecimal 0x43. Always use `0x` prefix in `CMD_CLASS_VERS` map. Example known bug in djdizzyd T6 Pro driver: `043:2` intended to register 0x43 (Thermostat Setpoint v2) but actually registers 0x23 (Scene Controller Config v2).

---

## Step 3: Standard Z-Wave Thermostat CC Checklist

For Z-Wave thermostat devices, these CCs are almost universally present. Check each:

| CC | Hex | Common Gap |
|----|-----|-----------|
| Thermostat Mode | 0x40 | Mode v2 not registered (misses AutoChangeOver mode) |
| Thermostat Operating State | 0x42 | Missing `descriptionText` on event; wrong arg `device.currentValue` (no attribute name) |
| **Thermostat Setpoint** | 0x43 | **Octal bug**: driver writes `043` not `0x43` in CMD_CLASS_VERS |
| **Thermostat Fan Mode** | 0x44 | v3 not registered — misses "circulate" mode reporting |
| **Thermostat Fan State** | 0x45 | **Parsed but not emitted** — "running high" = stage 2 active, valuable for 2-stage systems |
| Sensor Multilevel | 0x31 | Only type 1 (air temp) and 5 (humidity) polled; extra sensor types silently ignored |
| Notification/Alarm | 0x71 | Only power-management (type 8) handled; types 9 (System), 11 (Clock) fall through |
| Battery | 0x80 | Event 0xFF → 1% handled; events 10/11 ("replace battery soon/now") silently break |
| Clock | 0x81 | syncClock scheduler — check for zombie accumulation (unschedule before runEvery) |
| Configuration | 0x70 | Param list completeness; newer SKU may add params not in older doc |
| Association | 0x85 | Group 1 set to hub node in processAssociations(); verify on first run |
| Supervision | 0x6C | SupervisionGet handler → SupervisionReport(status: 0xFF) must be present for Z-Wave Plus |

---

## Step 4: Configuration Parameter Audit

1. Find the official param count for the device (usually in OZW XML or OpenHAB ZWave DB doc).
2. Compare with `configParams` map in driver — check for:
   - **Missing params** (newer SKU may have added params)
   - **Wrong range** (e.g., Balance Point param lists only subset of valid 5°F steps)
   - **Wrong type** (byte vs enum for params that have discrete valid values)
   - **Signed params** (params with negative valid values like Temperature Offset −3 to +3 need sign handling)
3. Check the sign-handling code: `if (scaledValue > 127) scaledValue = scaledValue - 256` — should be present for 1-byte signed params.

---

## Step 5: Feature Checklist for Common Thermostat Omissions

| Feature | What to look for |
|---------|-----------------|
| **Fan State attribute** | `THERMOSTAT_FAN_STATE` map defined? Handler emits it? Values include "running high" (stage 2)? |
| **Battery-low notification** | Notification type 8 events 10/11 handled (not silent break)? |
| **Schedule via Z-Wave** | Is SCHEDULE CC (0x53) in fingerprint? If not, `setSchedule()` should warn "not supported." |
| **Keypad lock** | Is Indicator CC (0x87) in fingerprint? If not, keypad lock is physical-menu only. |
| **Outdoor temp** | Does device report outdoor temp over Z-Wave? Check SensorMultilevel handler for sensor types beyond 1 and 5. Many thermostats show outdoor temp on-screen but do NOT report it to controller. |
| **Hold state** | Is there a `thermostatHold` attribute? If so, is it device-reported (reliable) or driver-inferred (optimistic, may drift)? |
| **Temperature reporting resolution** | Is there a param for deadband/threshold before temp is reported? (Low API chattiness lever.) |
| **Humidity reporting resolution** | Same for humidity. |
| **PowerSource event completeness** | Notification type 8 events 2/3 → powerSource "battery"/"mains". Events 10/11 → battery warning. |
| **Clock sync** | syncClock scheduler — fired every 3 hours? Zombie guard in configure()? |
| **descriptionText on all sendEvent calls** | Especially temperature, humidity, mode, operating state. |

---

## Step 6: Hubitat Sandbox & Quality Gates

| Check | Rule |
|-------|------|
| No `httpGet` / `httpPost` | Z-Wave thermostat drivers must be local-only. No HTTP calls. |
| No `@CompileStatic` on `@Field static Map` with mixed types | Groovy static compilation interacts poorly with dynamic dispatch. |
| `eventProcess()` (dedup) | All sendEvents should go through a dedup wrapper to prevent redundant state-change events. |
| `logEnable` AND `txtEnable` | Both preferences must be declared; `txtEnable` silences informational log.info; `logEnable` silences debug. A driver with only `logEnable` will permanently suppress info logs. |
| `runEvery` + `unschedule` | Every `runEvery*("methodName")` call in `configure()` or `updated()` must be preceded by `unschedule("methodName")` to prevent zombie schedulers. |

---

## Known Device-Specific Notes (T6 Pro TH6320ZW2003/ZW2007)

- **Outdoor sensor (param 3):** Enables a wired outdoor sensor display on thermostat screen. **NOT reported to Z-Wave controller.** Community-confirmed; not official spec.
- **CMD_CLASS_VERS octal bug:** Original djdizzyd driver has `043:2` (Groovy octal). Fix to `0x43:2`.
- **TH6320ZW2007 extra params (43–45):** Humidity Offset, Temp Reporting Resolution, Humidity Reporting Resolution. Absent from ZW2003 documentation; may not work on ZW2003 firmware.
- **Association group 1 only:** 1 group, 1 node max. Multi Channel Association not needed (single endpoint device).
- **Thermostat Setpoint precision:** OZW revision 4 notes "requires 2 bytes for precision" for Celsius setpoints. Hubitat's `scaledValue` field handles this automatically.

---

## Sources / References

- OpenZWave XML repo (domoticz/domoticz mirror) — authoritative param source for most Z-Wave thermostats
- OpenHAB ZWave binding DB (`github.com/openhab/org.openhab.binding.zwave doc/`) — full CC list + params in markdown
- Driver fingerprint `inClusters` — always the **primary** CC source; never assume from CMD_CLASS_VERS alone
- Skill: `hubitat-thermostat-capability-enums` — valid enum values for thermostatMode, thermostatOperatingState, thermostatFanMode
- Skill: `hubitat-event-hygiene` — descriptionText, type (digital/physical), dedup wrappers
