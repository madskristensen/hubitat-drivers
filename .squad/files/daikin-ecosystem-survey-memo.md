# Hubitat Thermostat Ecosystem Survey
**Date:** 2026-05-18  
**Prepared by:** Trinity (Lead/Architect)  
**Scope:** Patterns in well-regarded Hubitat thermostat drivers vs. our drivers (Daikin WiFi v0.1.5, SunStat Connect Plus v0.1.11)

---

## 1. Drivers Surveyed

| Driver | Brand | Notes |
|---|---|---|
| **Venstar ColorTouch Local API** | Venstar | Community driver; local LAN control; supports multi-stage HVAC; strong developer reputation; MIT license. Primary reference for multi-zone/multi-stage patterns. |
| **Ecobee Suite Manager** | Ecobee | Community app + driver; cloud-backed; rich sensor + scheduling support; occupancy detection; voice assistant integration. Reference for schedule control + external-sensor child-device patterns. |
| **Honeywell Total Connect** | Honeywell | Community drivers vary; some cloud, some local (if available). Reference for away/vacation mode patterns. |
| **Emerson Sensi** | Sensi | Community drivers available; simpler protocol; 7-day programmable scheduling. Reference for basic schedule patterns. |
| **Built-in Hubitat Generic Z-Wave/Zigbee Thermostat** | Multiple | Core Hubitat drivers for Zigbee/Z-Wave thermostats. Reference for platform capability conventions. |

---

## 2. Common Patterns We Don't Have

| Pattern | Drivers that have it | Our Status | Verdict | Effort |
|---|---|---|---|---|
| **Display string attributes** (e.g., `setpointDisplay = "Heat: 72°F"`) | Venstar, Ecobee, community refs | 🔴 Missing from both | 🟡 Worth adding | 0.5 hrs |
| **Away mode tracking** (discrete `awayMode` attribute) | Venstar, Honeywell, Ecobee, Sensi | 🟢 SunStat has it; 🔴 Daikin doesn't | 🟡 Add to Daikin if API exposes it | 1 hr |
| **Vacation mode / hold state** | Ecobee (vacation), Honeywell (hold), Venstar (schedule hold) | 🟢 SunStat has `thermostatHold`; 🔴 Daikin doesn't | 🔴 Skip — Daikin API doesn't expose | N/A |
| **Schedule enable/disable toggle** | Venstar, Ecobee, Sensi, Honeywell | 🟢 SunStat has `setScheduleEnabled`; 🔴 Daikin doesn't | 🟡 Daikin v0.1.0 memo notes `set_program` exists but rarely useful with Hubitat | 1 hr if pursued |
| **Multi-stage HVAC display** (aux heat, 2nd stage cool) | Venstar (auxHeat), Honeywell | 🔴 Neither driver | 🔴 Skip — not applicable to Daikin (single-stage heat pump) or SunStat (electric floor heating) | N/A |
| **Filter runtime / maintenance reminders** | Some Honeywell, custom community builds | 🔴 Neither | 🔴 Skip — Daikin doesn't expose; SunStat doesn't track; community pattern is Rule Machine automation, not driver-native | N/A |
| **External/remote sensor child devices** | Ecobee (SmartSensors), Venstar (aux sensors) | 🟢 SunStat exposes `outdoorTemperature`; 🔴 Daikin exposes `outsideTemp` as attribute only, not child | 🟡 SunStat already good; Daikin good enough | 0 hrs |
| **Occupancy / presence integration** | Ecobee (roomSensors), some Honeywell | 🔴 Neither | 🔴 Skip — platform concern (Rule Machine or app), not driver concern | N/A |
| **Indoor Air Quality (IAQ) reporting** | Ecobee (vocIndex, CO₂ estimates) | 🔴 Neither | 🔴 Skip — not in Daikin or SunStat device APIs | N/A |
| **Humidification/Dehumidification control** | Venstar (some models), Honeywell | 🟢 Daikin has `RelativeHumidityMeasurement` (read-only); 🔴 No control | 🔴 Skip — Daikin doesn't expose humidity control in BRP069B API | N/A |

---

## 3. Trinity's Recommendations

### Top 3 Priorities for v0.1.6+ (ranked by user value × ease)

**🥇 Priority 1: Add `setpointDisplay` attribute (both drivers)** — **0.5 hrs**
- **User value:** High. Dashboard users see cleaner thermostat tiles without having to check separate heat/cool setpoints.
- **Ease:** Trivial. Add one computed string attribute; emit whenever thermostat mode or setpoint changes.
- **Pattern:** `"Heat: 72°F"` or `"Auto: 70°F / 75°F"` or `"Off"`.
- **Rationale:** Low-hanging fruit; improves UX on dashboards with near-zero complexity.

---

**🥈 Priority 2: Daikin — Emit `awayMode` attribute if BRP069B API exposes it** — **1 hr**
- **User value:** Medium. Allows users to see at a glance whether Daikin is in eco/away state; some automations key on this.
- **Ease:** Low. Check if `get_control_info` or `get_special_mode` includes an away/eco flag; if yes, parse and emit.
- **Current state:** Daikin v0.1.5 emits `specialMode` (off/econo/powerful), which partially fills this role but is domain-specific.
- **Rationale:** SunStat already does this well; Daikin should follow for consistency. **ACTION: Cypher to verify if BRP069B exposes this during next protocol audit.**

---

**🥉 Priority 3: SunStat — No priority additions; driver is mature.** — **0 hrs**
- **Status:** SunStat already has schedule control, away mode, hold state, floor sensors, outdoor temp, and boost. Feature-complete for its device.

---

### Items to Skip (and why)

- **Multi-stage HVAC:** Not applicable. Daikin is a single-stage inverter heat pump; SunStat is electric floor heating. Future drivers for traditional 2-stage furnace/AC might benefit, but not our current scope.
- **Filter reminders:** Daikin doesn't expose filter runtime; SunStat doesn't either. This belongs in Rule Machine / app layer, not driver.
- **Vacation mode:** Daikin's `set_program` is rarely useful when Hubitat rules are more flexible. Skip for now.
- **Schedule control for Daikin:** Redundant with Hubitat Rule Machine. SunStat's `setScheduleEnabled` is a good middle ground (toggle on/off cloud schedule), but Daikin's endpoint is less valuable.
- **IAQ / Occupancy:** Outside driver scope; platform concern.

---

## 4. Cross-Driver Consistency Notes

Both drivers should adopt:
1. **Consistent attribute naming:** `setpointDisplay`, `awayMode` (if applicable), `lastActivity` (both already have this ✅).
2. **Consistent event hygiene:** Both drivers already follow `emitIfChanged` + `descriptionText` patterns ✅.
3. **Consistent mode validation:** Both emit `supportedThermostatModes` on `installed()` ✅.

---

## 5. Ecosystem Insight

The Hubitat thermostat community consensus is:
- **Capabilities matter more than commands.** Most dashboard/Rule Machine integrations key off standard capabilities (Thermostat, TemperatureMeasurement, EnergyMeter) and well-known attributes. Custom commands are power-user territory.
- **Display strings are under-adopted.** Many drivers expose raw numeric attributes; `setpointDisplay` is simple but not universal.
- **Schedule control is split:** Cloud-backed systems (Ecobee, Honeywell) expose schedule ON/OFF toggles; LAN systems (Venstar, our Daikin) often skip this because Hubitat rules are the primary scheduler.
- **External sensors → child devices.** Ecobee's pattern is cleanest: SmartSensors are child devices with their own TemperatureMeasurement capability. SunStat's pattern (separate attributes) is simpler for single-room scenarios.

---

## 6. Archive References

**For future ecosystem audits:**
- Venstar Local API: https://developer.venstar.com/rest-api/ (multi-stage reference)
- Ecobee patterns: Look at "Ecobee Suite Manager" in Hubitat Community (child device + external sensor reference)
- Community driver hub: Hubitat Package Manager (HPM) browsing reveals adoption trends

