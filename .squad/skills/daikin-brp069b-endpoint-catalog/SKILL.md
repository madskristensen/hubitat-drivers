# Skill: daikin-brp069b-endpoint-catalog

**Confidence:** medium  
**Validated:** 2026-05-18 — Cypher API audit; sources: ael-code/daikin-control + Apollon77/daikin-controller v2.2.1  
**Author:** Cypher

---

## Summary

Complete reference for the **Daikin BRP069B WiFi adapter local HTTP API**. All 28 documented endpoints, their purposes, and recommendations for Hubitat driver implementations.

This is a **project-specific but reusable** reference: use when building any Daikin driver on Hubitat.

---

## Endpoints by Category

### Core Control (7 endpoints implemented in v0.1.5+)

| Endpoint | Method | Purpose | Hubitat Status |
|---|---|---|---|
| `/aircon/get_control_info` | GET | Mode, power, setpoint, fan rate, swing | ✅ Poll every cycle (~5 min) |
| `/aircon/set_control_info` | POST | Write all control parameters (requires: pow, mode, stemp, shum, f_rate, f_dir) | ✅ On command |
| `/aircon/get_sensor_info` | GET | Indoor/outdoor temperature, humidity (may return "-" for unavailable sensors) | ✅ Poll every cycle |
| `/aircon/get_model_info` | GET | Model name, firmware, humidity/swing sensor capabilities | ✅ Called in initialize(); cached in state |
| `/aircon/get_special_mode` | GET | Econo/Powerful mode status (adv field; may be compound like "2-fff10000") | ✅ Poll every cycle |
| `/aircon/set_special_mode` | POST | Write Econo/Powerful mode | ✅ On command |
| `/aircon/get_week_power_ex` | GET | Weekly kWh (7 days, slash-delimited s_dayw field) | ✅ Poll 30-min cadence |
| `/aircon/get_year_power_ex` | GET | Monthly kWh totals (12 months, calendar year) | ✅ Poll 30-min cadence |

### Worth Adopting (2 endpoints, v0.1.6+ candidates)

| Endpoint | Method | Purpose | Recommendation |
|---|---|---|---|
| `/common/basic_info` | GET | MAC address, firmware version, device name, `lpwFlag` | Call in initialize(). **Critical:** If `lpwFlag=1` (some BRP069A units), append `lpw=` param to all subsequent `/aircon/set_*` calls. Provides device label for Hubitat state. |
| `/aircon/get_demand_control` | GET | Max-power cap / demand-response limit (percentage) | Useful for solar self-consumption / utility demand-response. Needs real-device validation on BRP069B41 hardware. |
| `/aircon/set_demand_control` | POST | Write power cap | Companion to above; defer to v0.1.7+ after read validation |

### Defer to Later (4 endpoints, v0.1.7+ if demand)

| Endpoint | Method | Purpose | Notes |
|---|---|---|---|
| `/aircon/get_day_power_ex` | GET | Hourly kWh breakdown for today | Listed in Apollon77 TODO as `get_day_paower_ex` (typo?). Would enable hourly dashboards. Community demand: low. |
| `/common/get_notify` | GET | Filter maintenance alert, error codes | Listed in ael-code docs; poorly documented. Community reports filter-cleaning alert lives here. Polling cost: +1 call/cycle. |
| `/aircon/get_week_power` | GET | Weekly runtime (older non-`_ex` format) | Fallback for old BRP069A firmware that doesn't support `_ex` endpoints. BRP069B focus means low priority. |
| `/aircon/get_year_power` | GET | Yearly runtime (older non-`_ex` format) | Same as above — legacy fallback only. |

### Skip (10 endpoints, no Hubitat value)

| Endpoint | Method | Category | Reason |
|---|---|---|---|
| `/common/get_remote_method` | GET | Cloud config | Negotiates cloud polling interval; irrelevant for LAN-only driver. |
| `/common/set_remote_method` | POST | Cloud config | Cloud-facing; no value for Hubitat. |
| `/aircon/get_program` | GET | On-device schedule | Trinity memo: Hubitat rules cover scheduling. Poorly documented; community implementations fragile. |
| `/aircon/set_program` | POST | On-device schedule | Same rationale. |
| `/aircon/get_scdltimer` | GET | On/off timer | Same rationale. |
| `/aircon/set_scdltimer` | POST | On/off timer | Same rationale. |
| `/aircon/get_scdltimer_info` | GET | Timer metadata | Apollon77 TODO; barely documented. |
| `/aircon/get_timer` | GET | One-shot timer | Unknown purpose; community hasn't reverse-engineered it. |
| `/aircon/get_target` | GET | Unknown | ael-code lists with "?"; purpose unclear. |
| `/aircon/get_price` | GET | Unknown | ael-code "?"; no known use. |
| `/common/set_led` | POST | LED control | ael-code: doesn't actually work on tested hardware. |
| `/common/reboot` | POST | Device management | Dangerous: ~30s disconnect. No legitimate use. |
| `/common/set_regioncode` | POST | Cloud config | Cloud-facing; credentials risk per ael-code. |
| `/common/get_datetime` | GET | Device time | Diagnostics only; no user value in Hubitat driver. |
| `/aircon/get_wifi_setting` | GET | WiFi config | Diagnostics; exposes potential credentials. Do not expose. |

---

## Key Implementation Notes

### Sentinel Values

All three sensor-info fields may return the literal string `"-"`:
- `htemp` (indoor temp) — rare, but possible
- `otemp` (outdoor temp) — **very common**; many units lack outdoor sensor
- `hhum` (humidity) — **very common**; most units no humidity sensor

**Always guard:** `if (value?.isNumber()) { parse }` before `Double.parseDouble()`. See skill `hubitat-sentinel-value-guards`.

### `lpwFlag` Compatibility

Some **BRP069A-series** units require an `lpw=` parameter appended to all `/aircon/set_*` calls:

1. Call `/common/basic_info` in `initialize()`
2. Check `lpwFlag` in response
3. If `lpwFlag=1`, cache globally and append to all subsequent set requests:
   ```
   POST /aircon/set_control_info?pow=1&mode=3&stemp=22&shum=0&f_rate=A&f_dir=0&lpw=
   ```

**BRP069B4x (Mads's device) appears NOT to need this**, but it's a forward-compatibility guard for older adapters.

### Special-Mode Syntax Variation

The `adv` field in `/aircon/get_special_mode` responses may appear as:
- Simple: `"2"` (econo mode)
- Simple: `"12"` (powerful mode)
- Compound: `"2-fff10000"` (firmware variant; need to extract leading token)

**Safe parsing:** `advCode = advRaw.split("-")[0].trim()`, then use the first token as the mode code.

### Graceful 404 Fallback

Some BRP069B firmware versions do not expose `/aircon/get_special_mode`. The driver must gracefully handle a 404 response without crashing:

```groovy
if (response.getStatus() == 404) {
    debugLog "Special mode not supported on this firmware"
    return
}
```

---

## Polling Cadence Recommendation

Balanced approach for most Daikin installations:

- **Core endpoints** (`/get_control_info`, `/get_sensor_info`, `/get_special_mode`): **5-min cadence** (via `poll()`)
- **Energy endpoints** (`/get_week_power_ex`, `/get_year_power_ex`): **30-min cadence** (separate scheduled task)
- **Model info** (`/get_model_info`): **initialize() only** (cached in state)

**Total HTTP load:** ~13 requests per hour (3 per 5-min core cycle + 2 per 30-min energy cycle). Negligible on modern LANs.

---

## References

- **ael-code/daikin-control** (GitHub) — BRP069A41–B41 firmware 1.4.3–3.3.1 endpoint reference
- **Apollon77/daikin-controller** (GitHub) — Node.js library v2.2.1 (2025-05-24); most comprehensive community source
- **Audit memo:** `.squad/files/daikin-research/daikin-api-perf-audit-memo.md`
- **Driver implementation:** `drivers/daikin-wifi/daikin-wifi.groovy` (v0.1.5+)
