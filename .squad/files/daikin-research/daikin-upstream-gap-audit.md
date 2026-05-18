# Daikin WiFi — Upstream Feature-Gap Audit
**Author:** Cypher  
**Date:** 2026-05-18  
**Scope:** eriktack/hubitat-daikin-wifi v1.0.3 vs madskristensen v0.1.5 (+ Tank-7 hotfix)  
**Clean-room boundary:** No longer applies for analysis; ideas only, no code copied.

---

## 1. Side-by-Side Comparison

### Commands

| Upstream command | Notes | Status vs Ours |
|---|---|---|
| `fan` | Delegate to mode | ✅ Covered via `setThermostatMode("fan")` |
| `dry` | Delegate to mode | ✅ Covered via `setThermostatMode("dry")` |
| `auto` / `cool` / `heat` | Standard Thermostat cap | ✅ |
| `on` / `off` | Standard Switch | ✅ |
| `setThermostatMode` | Standard | ✅ |
| `setThermostatFanMode` | Standard | ✅ |
| `setHeatingSetpoint` / `setCoolingSetpoint` | Standard | ✅ |
| `setFanRate(number)` | Accepts numeric strings | ✅ Better — ours uses a typed ENUM |
| `fanRateAuto` | Single-purpose convenience | ✅ Covered by `setFanRate("A")` |
| `fanRateSilent` | Single-purpose convenience | ✅ Covered by `setFanRate("B")` |
| `fanOn` / `fanAuto` / `fanCirculate` | Standard | ✅ |
| `fanDirectionVertical` | Toggle helper | 🚫 Missing — but see note ¹ |
| `fanDirectionHorizontal` | Toggle helper | 🚫 Missing — but see note ¹ |
| `tempUp` | +0.5° step from current setpoint | 🚫 Missing |
| `tempDown` | −0.5° step from current setpoint | 🚫 Missing |
| `setTemperature(number)` | Unified setpoint setter | ➖ We use standard setHeating/CoolingSetpoint |
| *(none)* | `setSwingMode` ENUM | ➖ We're ahead |
| *(none)* | `setSpecialMode` ENUM | ➖ We're ahead |
| *(none)* | `refreshEnergy` | ➖ We're ahead |
| *(none)* | `emergencyHeat` (stubs to heat) | ➖ We're ahead |

¹ Upstream's toggle commands (`fanDirectionVertical` / `fanDirectionHorizontal`) implement a 3-state toggle: Off→on, on→3D, 3D→off. Our `setSwingMode(ENUM)` is a direct setter — cleaner for RM, less convenient as a dashboard button tile.

---

### Attributes

| Upstream attribute | Type | Status vs Ours |
|---|---|---|
| `outsideTemp` | number | ✅ |
| `targetTemp` | number (unified setpoint) | ✅ Covered by standard `thermostatSetpoint` |
| `currMode` | string (device mode regardless of power) | 🚫 Missing — redundant with `thermostatMode` |
| `fanAPISupport` | string ("true"/"false") | 🚫 Missing — we handle silently via .isNumber() guards |
| `fanRate` | string | ✅ (ours is typed ENUM — better) |
| `fanDirection` | string ("Off"/"Vertical"/"Horizontal"/"3D") | ✅ We have `swingMode` ENUM — functionally equivalent, better typed |
| `statusText` | string ("Heating to 72°", "Fan Mode", etc.) | 🚫 Missing — see note ² |
| `connection` | string | ✅ We have `healthStatus` which actually works (upstream declared but never set) |
| `energyToday` | number (kWh) | ✅ Covered by standard `energy` attribute |
| `energyYesterday` | number (kWh) | 🚫 Missing |
| `energyThisYear` | number (kWh, calendar year) | 🚫 Missing (dead computation in our `handleYearPower`) |
| `energyLastYear` | number (kWh, previous calendar year) | 🚫 Missing |
| `energy12Months` | number (kWh, rolling 12 months) | 🚫 Missing |
| *(none)* | `specialMode` | ➖ We're ahead |
| *(none)* | `healthStatus` | ➖ We're ahead |
| *(none)* | `lastActivity` | ➖ We're ahead |

² `statusText` is a human-readable dashboard string like "Heating to 72°" or "Fan Mode". Trinity already recommended an equivalent as `setpointDisplay` in the ecosystem survey memo — same idea, different name.

---

### Capabilities

| Upstream | Status vs Ours |
|---|---|
| `Thermostat` | ✅ |
| `Temperature Measurement` | ✅ |
| `Actuator` | ✅ |
| `Switch` | ✅ |
| `Sensor` | ✅ |
| `Refresh` | ✅ |
| `Polling` | ✅ |
| *(none)* | `RelativeHumidityMeasurement` — we're ahead |
| *(none)* | `EnergyMeter` — we're ahead |
| *(none)* | `HealthCheck` — we're ahead |

---

### Preferences

| Upstream preference | Status vs Ours |
|---|---|
| `ipAddress` (text) | ✅ We have `ip` |
| `ipPort` (string, default 80) | 🚫 We hardcode port 80 |
| `refreshInterval` (enum 1/5/10/15/30) | ✅ |
| `displayFahrenheit` (boolean) | ➖ We use `location.temperatureScale` automatically — superior |
| `debugLogging` (bool) | ✅ We have `logEnable` + `traceEnable` (two levels) — ahead |
| *(none)* | `defaultMode` — we're ahead |
| *(none)* | `defaultSetpoint` — we're ahead |
| *(none)* | `defaultFanRate` — we're ahead |

---

## 2. Behavioral / Quirk Findings

### 2a. Operating-state mapping
Upstream emits `"fan only"` for **both** fan mode and dry mode. We emit `"fan only"` for fan but `"drying"` for dry. The Hubitat Thermostat capability spec defines `"fan only"` as a valid `thermostatOperatingState` value but does **not** list `"drying"`. Upstream is technically more spec-compliant here; our `"drying"` value is non-standard and may confuse dashboards or apps that only know the documented enum values.

### 2b. Energy field names — potential protocol bug in our driver
Upstream reads `week_heat` + `week_cool` from `get_week_power_ex` and sums them for today/yesterday. We use `s_dayw`. Both approaches should yield the same combined total — `s_dayw` appears to be the already-summed field in the `_ex` response. This is fine for today's energy.

**More significant:** for yearly energy, upstream reads `curr_year_heat` + `curr_year_cool` (and `prev_year_heat` + `prev_year_cool`). Our driver reads `this_year`. If `this_year` is not a real field in the `get_year_power_ex` response (upstream never uses it), then our `handleYearPower` dead computation is dead for *two* reasons: (1) we never emit `yearTotal`, AND (2) the field name might be wrong. **This needs real-device verification.** If `this_year` returns null, `yearTotal` will be 0 whether we emit it or not.

### 2c. No setpoint clamping in upstream
Upstream sends whatever temperature value the caller provides, with no bounds checking. We clamp to 10–32°C / 50–90°F. Our approach is safer.

### 2d. tempUp / tempDown — dashboard convenience
Upstream exposes `tempUp()` and `tempDown()` as ±0.5° step commands. These are extremely useful as dashboard button-tile actions (a user can tap a + or − button on a tile to adjust setpoint without launching a full slider UI). We have no equivalent.

### 2e. statusText human-readable string
Upstream's `statusText` attribute holds strings like `"Heating to 72°"`, `"Cooling to 68°"`, `"Fan Mode"`, `"System is off"`. This is exactly what Trinity recommended as `setpointDisplay` for v0.1.6. The implementation pattern is clear: compute it whenever mode or setpoint changes.

### 2f. Swing direction toggle vs ENUM
Upstream's toggle logic for fan direction is clever for dashboard use (a single button toggles the axis on/off). However, it introduces state-tracking complexity and can produce unexpected behavior if state gets out of sync. Our direct ENUM setter is more predictable for Rule Machine. For dashboard users wanting a one-tap toggle, the toggle pattern is better UX. This is a product tradeoff, not a bug.

### 2g. HTTP transport
Upstream uses the Map-based `HubAction` constructor — the exact pattern that failed in our v0.1.0 and v0.1.1. Upstream's driver will crash on Hubitat firmware that doesn't support this constructor. Our `asynchttpGet`-based approach is a significant reliability improvement.

### 2h. `connection` attribute — dead code upstream
Upstream declares `attribute "connection", "string"` but never emits it anywhere. It is never set. We replaced this pattern with a working `healthStatus` + `lastActivity` HealthCheck implementation.

---

## 3. Worth-It List

### 🟢 Adopt

| Item | Rationale | Target |
|---|---|---|
| **`energyYesterday` attribute** | Data is already in `s_dayw[1]` — one extra `emitIfChanged` call in `handleWeekPower`. Zero new HTTP calls. Clear user value: "how much did I use yesterday?" | v0.1.6 |
| **`energyThisYear` attribute** | Already computing `yearTotal` in `handleYearPower` (dead code per prior audit). Just needs an emit. **But:** first verify the `this_year` field name is correct on real hardware (upstream uses `curr_year_heat` + `curr_year_cool`). | v0.1.6 (verify field name first) |
| **`energyLastYear` attribute** | Requires reading `prev_year_heat` + `prev_year_cool` (or whatever the `_ex` prev-year field is). Completes the energy story. | v0.1.6 (with field name verification) |
| **`tempUp` / `tempDown` commands** | ±0.5° step buttons — real-world convenience for dashboard tile users. ~10 lines of code. Not covered by any existing memo. | v0.1.6 |
| **Fix `"drying"` → `"fan only"` in `operatingStateForMode`** | Upstream's `"fan only"` for dry mode is spec-compliant; our `"drying"` is non-standard and may break integrations. Low-risk one-liner fix. | v0.1.6 hotfix-tier |

*(Note: `statusText` / `setpointDisplay` is already queued from Trinity's ecosystem survey memo — not re-recommending here.)*

### 🟡 Defer

| Item | Rationale |
|---|---|
| **`energy12Months` rolling 12-month attribute** | Interesting, but requires month-boundary arithmetic across two year arrays. Moderate complexity, low urgency. |
| **`ipPort` preference** | Almost nobody uses non-80 port on BRP069B. Add only when a user reports needing it. |

### 🔴 Skip

| Item | Rationale |
|---|---|
| **`fanDirectionVertical` / `fanDirectionHorizontal` toggle commands** | Our ENUM-based `setSwingMode` is cleaner and more RM-friendly. The toggle pattern is confusing; upstream's own code shows it can produce surprising state interactions. |
| **`fanRateAuto` / `fanRateSilent` as separate commands** | Fully covered by `setFanRate("A")` and `setFanRate("B")`. Adding these would be dead weight. |
| **`setTemperature(number)` unified setter** | Our separate `setHeatingSetpoint` / `setCoolingSetpoint` is more standard-conformant. |
| **`currMode` attribute** | Redundant with `thermostatMode`. Upstream needed it to track device mode when powered off — we handle this differently and more cleanly. |
| **`fanAPISupport` attribute** | We handle missing fan API silently without polluting state. No user value in surfacing a runtime detection flag. |
| **`displayFahrenheit` preference** | Our automatic `location.temperatureScale` detection is strictly better. |
| **`connection` attribute** | Dead in upstream. We replaced it with a working `healthStatus`. |

---

## 4. Cypher's Recommendation

We are ahead of the upstream on almost every dimension: reliability (asynchttpGet vs broken HubAction), capability breadth (HealthCheck, EnergyMeter, RelativeHumidityMeasurement), typing discipline (ENUM attributes vs plain strings), and correctness (setpoint clamping, `.isNumber()` guards, emitIfChanged hygiene). The upstream driver's real contribution was proving the concept and documenting which API endpoints produce usable data. There are exactly **four concrete items worth pulling forward for v0.1.6:** `energyYesterday`, `energyThisYear`, `energyLastYear` (subject to field-name verification on real hardware), and `tempUp`/`tempDown` step commands. There is also a one-liner correctness fix: `"drying"` → `"fan only"` in `operatingStateForMode` for dry mode. None of these require architectural changes — they are all leaf-node additions to existing handlers. Everything else in upstream is either already covered, already skip-listed in prior memos, or is pattern we deliberately improved upon.

---

*Cross-references: Trinity ecosystem survey memo (setpointDisplay queued for v0.1.6). Cypher API+perf audit memo (energyThisYear dead computation already flagged, field-name concern newly raised here). No overlap with prior endpoint skip lists.*
