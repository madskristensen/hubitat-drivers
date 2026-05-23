# Squad Decisions Log

Generated: 2026-05-23T15:47:33-07:00
Merged from inbox/

---

# Architecture Proposal: Climate Advisor — v2 (Generic, SharpTools-first) — SUPERSEDES v1

**Date:** 2026-05-23  
**Author:** Trinity (Lead / Architect)  
**Status:** PROPOSAL v2 — revised per Mads feedback  
**Supersedes:** Architecture Proposal: Climate Advisor — v1 (below) — see "What Changed and Why"

---

## What Changed and Why

Two explicit pushbacks from Mads drove this revision:

1. **Make it generic.** The v1 proposal hard-coded three zone names (Upstairs/Downstairs/Sunroom), specific device names from Mads's inventory, and specific AQI attribute names. Any user installing via HPM would need to fork and re-hard-code everything. That's not a community app — it's a personal script. This proposal replaces all fixed references with user-configurable preferences.

2. **Drop HomeKit.** Cypher confirmed that custom string attributes do not cross any HomeKit bridge (neither the built-in Hubitat integration nor homebridge-hubitat-tonesto7). The `ContactSensor` capability was chosen purely as a HomeKit proxy — a binary open/closed signal piggybacking on a wrong semantic. Since HomeKit is not a requirement, `ContactSensor` adds complexity and misleads the device's purpose with no payoff. Removed.

Mads's device inventory (T6 Pro Upstairs/Downstairs, Daikin Sunroom, Backyard sensor, PurpleAir, OpenWeatherMap) remains **reference architecture** — it informs the design and example configurations, but nothing is assumed or hard-coded.

---

## 1. Architecture: Still Parent App + Child Virtual Device

No change here. This remains the right pattern and the reasoning from v1 stands:

- Drivers can't subscribe to multiple external devices; apps can.
- The child device is the **platform-visible face** — SharpTools, RM, dashboards see it.
- The app is the **brain** — subscribes to all zone sensors, runs logic, writes to the child.
- Community precedent: Presence Plus, Combined Presence, HomeKit Integration Mode.

**Folder layout (unchanged from v1):**
```
apps/climate-advisor/climate-advisor-app.groovy
drivers/climate-advisor/climate-advisor-device.groovy
```

Both files registered in root `README.md` and root `packageManifest.json` for HPM discovery.

**Release metadata:**
```
0.1.0 — 2026-05-23 — Initial release
```

---

## 2. Capability Surface: Drop ContactSensor, Keep the Useful Ones

### Child device capabilities (v2)

```groovy
capability "Sensor"             // marker — no attributes, harmless, expected for virtual sensor devices
capability "Refresh"            // user-visible "recalculate now" button in device UI
capability "Notification"       // optional: deviceNotification(text) command for push consumers
capability "SpeechSynthesis"    // optional: speak(text) command if child is wired as a speech target
```

`ContactSensor` is **removed**. There is no remaining justification for it.

> **Future note:** If a user wants HomeKit visibility as a fork, they can add `capability "ContactSensor"` themselves and map `severity > 0` to `contact: "open"`. That is explicitly out of scope for v1.

### Custom attributes

```groovy
attribute "severity"         , "NUMBER"                                     // 0=clear 1=info 2=warning 3=danger
attribute "severityText"     , "ENUM"   , ["clear","info","warning","danger"] // human label
attribute "latestMessage"    , "STRING"                                     // single-line summary (SharpTools Hero tile)
attribute "messages"         , "STRING"                                     // JSON array — see schema below
attribute "houseStatus"      , "STRING"                                     // back-compat mirror of latestMessage
attribute "tempTrend"        , "ENUM"   , ["rising","falling","stable","unknown"]
attribute "activeAlertCount" , "NUMBER"                                     // count of active messages (severity >= 1)
```

Note: `severityText` uses `"danger"` (not `"critical"`) to match plain English. SharpTools tile templates can display any of these directly. No RM intermediate switch needed — SharpTools Rules can read `severityText` or `severity` directly and apply color profiles or triggers.

### messages JSON schema (unchanged from v1)

```json
{
  "id"      : "zone-upstairs-hot-1716485814",
  "ts"      : 1716485814000,
  "severity": 2,
  "source"  : "Upstairs",
  "text"    : "Getting warm — 83°F outside, setpoint 75°F. Close windows."
}
```

Array is ordered: highest severity first, then newest-first. Max 20 entries; prune oldest of same source when adding.

### Child device commands

```groovy
command "refresh"         // re-evaluate all zones immediately
command "clearMessages"   // reset to all-clear (user action)
command "acknowledge"     // clear info-only messages (severity=1); leave warnings/danger
```

`addMessage(text, severity, source)` and `clearMessage(id)` remain **private app-side methods**, never exposed as device commands. The app owns the write path.

---

## 3. Generic App Preferences — Dynamic Zone Configuration

This is the core change. Replace hard-coded zone blocks with a user-configurable zone count using Hubitat's **dynamic pages** pattern.

### Design principle

Hubitat's preference system does not support true runtime-dynamic input names (you can't do `input "zone${i}Thermostat"`). The standard community pattern for N-zone configurations is:

1. Define a **fixed maximum** number of zones (recommend 8 — enough for any household, small enough not to clutter the UI).
2. Let the user configure how many zones they want (e.g., `input "zoneCount", "number", range: "1..8"`).
3. Show zone pages only up to `zoneCount` using dynamic page `nextPage` chaining or a single scrolling page with all 8 zones and guidance to leave unused ones blank.

**Recommended implementation:** Single scrolling "Zones" page with 8 zone blocks. Each block has a zone-name input first. Zones where the user leaves the name blank (or leaves all devices blank) are skipped at runtime. This is simpler than dynamic page chaining and works reliably on C-7/C-8.

### Page 1 — Global: Outdoor Conditions

```groovy
input "outdoorTempSensor",
    "capability.temperatureMeasurement",
    title: "Outdoor Temperature Sensor",
    required: true

// Rain source — no standard capability.rain exists on Hubitat.
// Accept any device and let user specify the attribute name + keyword.
input "rainDevice",
    "capability.sensor",
    title: "Rain / Weather Device (optional)",
    required: false
input "rainAttribute",
    "string",
    title: "Attribute name on rain device that contains weather condition",
    defaultValue: "weather",
    required: false
input "rainKeyword",
    "string",
    title: "Keyword in that attribute indicating rain (e.g. 'rain', 'drizzle')",
    defaultValue: "rain",
    required: false

// AQI — accept capability.airQuality (standard Hubitat, attribute: airQualityIndex)
// or any sensor with a custom attribute. Expose the attribute name as a preference.
input "airQualityDevice",
    "capability.airQuality",
    title: "Air Quality / AQI Device (optional)",
    required: false
input "aqiAttributeName",
    "string",
    title: "AQI attribute name on that device",
    defaultValue: "airQualityIndex",   // Hubitat standard; user changes to "aqi" for PurpleAir
    required: false
```

> **AQI attribute note:** Hubitat's standard `capability.airQuality` attribute is `airQualityIndex`. The PurpleAir community driver (pfmiller0 / Mads's fork) uses the non-standard `aqi` instead. The `aqiAttributeName` preference lets any user configure this. Default is the standard name; PurpleAir users set it to `aqi`.

### Page 2 — Zones (repeating blocks, max 8)

Each zone block is identical in structure. Tank should implement this as a helper method `zoneSection(int n)` called in the zones page:

```groovy
// Zone N block — repeated for N = 1..8
section("Zone ${n}") {
    input "zone${n}Name",
        "string",
        title: "Zone name (leave blank to disable this zone)",
        required: false

    input "zone${n}Thermostat",
        "capability.thermostat",
        title: "Thermostat (optional — omit if zone has no HVAC)",
        required: false

    input "zone${n}Contacts",
        "capability.contactSensor",
        title: "Window / Door contacts (multi-select)",
        multiple: true,
        required: false

    input "zone${n}TempSensors",
        "capability.temperatureMeasurement",
        title: "Additional temperature sensors in this zone (optional)",
        multiple: true,
        required: false

    input "zone${n}Speakers",
        "capability.speechSynthesis",
        title: "Speakers for announcements in this zone (optional)",
        multiple: true,
        required: false
}
```

At runtime, build the zone list dynamically:

```groovy
List zones = (1..8).collect { n ->
    def name = settings["zone${n}Name"]
    if (!name?.trim()) return null
    [
        name       : name.trim(),
        thermostat : settings["zone${n}Thermostat"],
        contacts   : settings["zone${n}Contacts"]   ?: [],
        tempSensors: settings["zone${n}TempSensors"] ?: [],
        speakers   : settings["zone${n}Speakers"]    ?: [],
    ]
}.findAll { it != null }
```

App validates at least one zone has a name + at least one contact before enabling.

### Page 3 — Notifications & Announcements

```groovy
input "notificationDevices",
    "capability.notification",
    title: "Push notification devices",
    multiple: true,
    required: false

input "announceSpeakers",
    "capability.speechSynthesis",
    title: "Global announcement speakers (in addition to per-zone speakers)",
    multiple: true,
    required: false

input "announceSeverityThreshold",
    "number",
    title: "Minimum severity to trigger announcements (1=info, 2=warning, 3=danger)",
    defaultValue: 2,
    range: "1..3"

input "throttleMinutes",
    "number",
    title: "Minimum minutes between repeated notifications for the same zone",
    defaultValue: 60
```

### Page 4 — Thresholds & Advanced

```groovy
input "comfortBuffer",
    "number",
    title: "Comfort buffer beyond setpoint (°F) before alerting",
    defaultValue: 3

input "trendWindowMinutes",
    "number",
    title: "Outdoor temperature trend window (minutes)",
    defaultValue: 30

input "trendSamples",
    "number",
    title: "Trend ring buffer size (samples)",
    defaultValue: 12

input "aqiWarnThreshold",
    "number",
    title: "AQI threshold for warning (EPA Moderate = 51)",
    defaultValue: 51

input "aqiDangerThreshold",
    "number",
    title: "AQI threshold for danger (EPA Unhealthy for Sensitive Groups = 101)",
    defaultValue: 101

input "logEnable",
    "bool",
    title: "Enable debug logging",
    defaultValue: false

input "txtEnable",
    "bool",
    title: "Enable description text logging",
    defaultValue: true
```

---

## 4. Core Logic (Unchanged from v1, Adapted for Generic Zones)

### Temperature trend (ring buffer in app state)

Unchanged from v1. App subscribes to `outdoorTempSensor` temperature events. On each event:
1. Append `[ts: now(), temp: event.value as BigDecimal]` to ring buffer.
2. Drop entries older than `trendWindowMinutes * 60 * 1000` ms.
3. If fewer than 3 entries → `tempTrend = "unknown"`.
4. Otherwise: compute slope (°F/min). `> +0.15` → rising, `< -0.15` → falling, else stable.

State size: 12 entries × ~45 bytes ≈ 540 bytes. Well within Hubitat's per-app state limit.

### Mode-aware thermostat logic (from v1)

| thermostatMode | Setpoint evaluation |
|---|---|
| `"off"` | Skip hot/cold setpoint alerts. Rain/AQI still apply if contacts open. |
| `"heat"` | Only compare outdoor vs `heatingSetpoint`. |
| `"cool"` | Only compare outdoor vs `coolingSetpoint`. |
| `"auto"` | Compare both setpoints. |
| `"fan"` | Same as `"off"` — no heating/cooling active. |
| thermostat absent | Treat as "off" — no setpoint alerts, still check rain/AQI. |

### BigDecimal coercion

All setpoint reads must be coerced: `zone.thermostat.currentValue("coolingSetpoint") as BigDecimal`. Daikin returns decimals (64.4, 75.2); T6 Pro returns integers (69, 75). BigDecimal handles both. Do NOT use integer comparison or string comparison.

### Per-zone evaluation pseudologic

```
for each zone in zones:
    skip zone if zone.contacts is empty
    
    thermoMode   = zone.thermostat?.currentValue("thermostatMode") ?: "off"
    coolSetpoint = zone.thermostat?.currentValue("coolingSetpoint") as BigDecimal
    heatSetpoint = zone.thermostat?.currentValue("heatingSetpoint") as BigDecimal
    openContacts = zone.contacts.findAll { it.currentValue("contact") == "open" }
    outdoorTemp  = outdoorTempSensor.currentValue("temperature") as BigDecimal
    isRaining    = rainDevice ? rainDevice.currentValue(rainAttribute)?.toLowerCase()?.contains(rainKeyword) : false
    aqi          = airQualityDevice ? airQualityDevice.currentValue(aqiAttributeName) as Integer : 0

    if openContacts not empty:
        if thermoMode in ["heat","auto"] and outdoorTemp < heatSetpoint - comfortBuffer:
            addMessage(zone.name, "Getting cold — ${outdoorTemp}°F outside, setpoint ${heatSetpoint}°F. Close ${zone.name} openings.", severity=2)
        if thermoMode in ["cool","auto"] and outdoorTemp > coolSetpoint + comfortBuffer:
            addMessage(zone.name, "Getting warm — ${outdoorTemp}°F outside, setpoint ${coolSetpoint}°F. Close ${zone.name} openings.", severity=2)
        if isRaining:
            addMessage(zone.name, "Rain — close ${zone.name} openings.", severity=3)
        if aqi >= aqiDangerThreshold:
            addMessage(zone.name, "Poor air quality (AQI ${aqi}) — close ${zone.name} openings now.", severity=3)
        elif aqi >= aqiWarnThreshold:
            addMessage(zone.name, "Moderate air quality (AQI ${aqi}) — consider closing ${zone.name} openings.", severity=2)

    if openContacts.isEmpty() and thermoMode != "off":
        // Suggest opening windows if outdoor temp is comfortable
        if outdoorTemp > heatSetpoint + comfortBuffer and outdoorTemp < coolSetpoint - comfortBuffer:
            if !isRaining and aqi < aqiWarnThreshold and tempTrend != "rising":
                addMessage(zone.name, "Comfortable outside (${outdoorTemp}°F) — consider opening ${zone.name} openings.", severity=1)
```

### Severity aggregation and all-clear

After all zones evaluated:
- `severity` = max severity across all messages (0 if no messages).
- `severityText` = `["clear","info","warning","danger"][severity]`.
- `activeAlertCount` = messages.count { it.severity >= 1 }.
- `latestMessage` = first message in ordered array, or `"All clear — no climate issues detected"`.
- `houseStatus` = mirror of `latestMessage` (kept permanently for SharpTools back-compat).

---

## 5. Subscription Model

The app subscribes to:
- `outdoorTempSensor`, `"temperature"` → `outdoorTempHandler` (updates ring buffer, triggers evaluation)
- All zone contacts (collected from all zones), `"contact"` → `contactHandler` → `evaluateAll()`
- All zone thermostats (deduplicated), `"thermostatMode"`, `"coolingSetpoint"`, `"heatingSetpoint"` → `thermostatHandler` → `evaluateAll()`
- `rainDevice` (if configured), `rainAttribute` → `rainHandler` → `evaluateAll()`
- `airQualityDevice` (if configured), `aqiAttributeName` → `aqiHandler` → `evaluateAll()`

`evaluateAll()` is the single evaluation entry point. All event handlers call it (with debounce: if called within 2s of last run, skip). The child device's `refresh` command also calls `evaluateAll()` via the parent.

Subscriptions must be rebuilt in `updated()` (unsubscribe all, re-subscribe from current settings).

---

## 6. HPM Registration

Both files must be registered in:
- `README.md` — under "Apps" section with one-line description
- `packageManifest.json` — each file as a separate entry with `type: "app"` or `type: "driver"`

HPM discovery requires both `id` (stable UUID) and `name` fields per entry. Tank generates the UUIDs at implementation time and stamps them permanently — they must not change after first HPM listing.

---

## 7. SharpTools Rendering (Unchanged from v1)

Three patterns, still valid:
- **Hero Attribute tile:** point at `latestMessage` — zero config, works today.
- **Severity color:** SharpTools Rules read `severityText` directly; no intermediate RM virtual switch needed.
- **Multi-message Custom Tile:** reads `messages` JSON attribute, renders colored severity badges.

---

## 8. Open Questions for Mads to Confirm

### Q1 — Zone starter template vs free-form naming

The generic zone preferences let users name zones freely (type anything into "Zone 1 Name"). The UX is clean but blank-slate — new users may not know where to start.

**Suggestion:** Show placeholder text in the zone name input: `title: "Zone name (e.g. Upstairs, Sunroom, Workshop)"`. No forced template — just hints. This is simpler than a starter template flow and avoids a pre-fill that the user might leave as-is without reading.

**Alternative:** Provide a "Load starter template" button that pre-fills 3 zone names ("Upstairs", "Downstairs", "Outbuilding"). Requires a custom command or a separate page with a trigger input — more complexity, marginal UX gain.

**Trinity recommends:** Placeholder text approach. Less code, no unexpected pre-fills.

### Q2 — Rain check: per-zone or house-wide?

The current design has one `rainDevice` globally. All zones share the same rain status.

**Arguments for house-wide (v1 recommendation):** Rain affects the whole house. A single Z-Wave/cloud weather device is typically whole-property anyway. Simpler. Fewer preferences. No zone-level rain sensitivity makes practical sense since rain falls everywhere.

**Arguments for per-zone:** A greenhouse or outbuilding might have different drainage or be sheltered — user might want to suppress rain alerts for certain zones.

**Trinity recommends:** House-wide rain check for v1. Add per-zone rain override as a future enhancement if users request it. Consistent with how the weather device physically works.

---

## 9. Summary Decision Table (v2)

| Question | Decision |
|---|---|
| Architecture | Parent App + Child Virtual Device |
| HomeKit capability | **Dropped.** No `ContactSensor`. No HomeKit requirement. |
| Rich data | `Sensor` + custom attributes (severity, severityText, latestMessage, messages, houseStatus, tempTrend, activeAlertCount) |
| Zone configuration | User-configurable, up to 8 zones, each named by user. Blank name = zone disabled. |
| Zone preferences | Per zone: name, thermostat (optional), contacts (multi), tempSensors (multi, optional), speakers (multi, optional) |
| Outdoor temp source | User picks via `capability.temperatureMeasurement` preference |
| Rain source | User picks any device; user configures attribute name + keyword |
| AQI source | User picks `capability.airQuality` device; user configures attribute name (default: `airQualityIndex`) |
| Thresholds | All user-configurable (comfortBuffer, AQI warn/danger, trend window, throttle) |
| App location | `apps/climate-advisor/climate-advisor-app.groovy` |
| Driver location | `drivers/climate-advisor/climate-advisor-device.groovy` |
| Temp trend | Ring buffer in app state, slope over configurable window (default 30 min) |
| All-clear message | "All clear — no climate issues detected" when no active messages |
| houseStatus attribute | Kept permanently (SharpTools back-compat) |
| HPM discovery | Root README + packageManifest.json both updated |
| Release line | `0.1.0 — 2026-05-23 — Initial release` |

---

*No Groovy code in this document — Tank implements after Mads approves.*

---

## NOTE: Architecture Proposal v1 (SUPERSEDED BY v2 ABOVE)

**This entry has been superseded by the v2 proposal directly above.** The v1 proposal contained hardcoded zone names and device assumptions that do not support generic distribution via HPM. The v2 proposal removes all hardcoding and makes all device and zone configuration user-selectable. Kept below for historical reference.

# Architecture Proposal: Climate Advisor — Parent App + Child Virtual Device (v1 — SUPERSEDED)

**Date:** 2026-05-23  
**Author:** Trinity (Lead / Architect)  
**Status:** SUPERSEDED by v2 — see "Architecture Proposal: Climate Advisor — v2 (Generic, SharpTools-first)" above  

---

## 1. App vs Driver: Recommendation — Parent App + Child Virtual Device

**Verdict: Parent App + Child Virtual Device.** This is not a close call.

Hubitat's architectural contract is explicit:

> **Drivers** own state for one physical (or virtual) device and surface capabilities to the platform.  
> **Apps** subscribe to events across multiple devices, run logic, and orchestrate state changes.

This feature subscribes to 10+ devices (N window contacts, 3 thermostats, outdoor temp sensor, weather device, air quality monitor). Multi-device event subscriptions are only available to **Apps**; a driver can only subscribe to its own device's events. A standalone driver with preferences could reference external devices via `getDevice()`, but it cannot subscribe to their events — it would need scheduled polling instead, which is fragile and imprecise.

The community does exactly this split for "advisor" and "status" devices:
- **Presence Plus** (popular community app) — app subscribes to GPS + WiFi + contact events, writes to a virtual presence sensor child.
- **HomeKit Integration Mode** — Hubitat app writes mode events to a virtual switch child for HomeKit visibility.
- **Combined Presence** (built-in) — app synthesizes presence from multiple sensors, exposes one virtual presence device.

The pattern is: **App = brain, child device = platform-visible face**. SharpTools and HomeKit only see the child device. The app is invisible to them — which is exactly what we want.

**Concrete benefits over a standalone driver:**
1. `subscribe(device, "contact", handler)` fires instantly on window open/close — no polling delay.
2. App preferences support `multiple: true` device lists — easy to add all 6 window contacts.
3. App logic can be updated without touching the child device's capability surface.
4. App is not constrained to one "device"'s state — it can maintain zone-level ring buffers, throttle clocks, per-zone last-notification timestamps all in `state`.

**Folder:** `apps/climate-advisor/climate-advisor-app.groovy` + `drivers/climate-advisor/climate-advisor-device.groovy`

---

## 2. Capability Selection — The Hard Question

### Candidates evaluated

| Capability | HomeKit via homebridge-hubitat-tonesto7 | Semantic fit | Verdict |
|---|---|---|---|
| `Notification` | ❌ Not mapped (write-only command) | ❌ No attribute, no state | Drop |
| `PresenceSensor` | ✅ Mapped as Occupancy Sensor | ⚠️ Boolean only, no message | Weak |
| `ContactSensor` | ✅ Mapped as Contact Sensor | ✅ open=alert, closed=clear | **Primary** |
| `SmokeDetector` | ✅ Mapped as Smoke Sensor | ❌ Semantic abuse, alarming | Drop |
| `CarbonMonoxideDetector` | ✅ Mapped as CO Sensor | ❌ Semantic abuse | Drop |
| `MotionSensor` | ✅ Mapped as Motion Sensor | ⚠️ Passable but weird | Secondary option |
| `TamperAlert` | ⚠️ Surfaced as tamper sub-attribute only | ⚠️ No standalone HK type | Drop |
| `Sensor` + custom attributes | ❌ No HK mapping | ✅ Maximum flexibility | Rich data layer |

### Recommendation: ContactSensor as HomeKit proxy + Sensor + custom attributes

**Primary capability: `ContactSensor`**  
- `contact: "closed"` → severity 0 (all clear). HomeKit shows green.  
- `contact: "open"` → severity ≥ 1 (any alert). HomeKit shows alert.  
- homebridge-hubitat-tonesto7 maps ContactSensor to HomeKit `Contact Sensor` accessory. Mads can configure HomeKit automations: "If [Climate Advisor] opens → send notification."  
- The semantic is defensible: the device is literally watching whether a contact (a conceptual "circuit") is open or closed. "Open" = something needs attention. Not perfect, but honest enough.  
- HomeKit users will see it as "House Climate Sensor" in the Home app. A custom name masks the odd physical metaphor.  

**Secondary (if Mads prefers a gentler HomeKit signal): `MotionSensor`**  
- `motion: "active"` = alert; `motion: "inactive"` = all clear.  
- Slightly weirder semantically (the house isn't moving) but HomeKit handles it identically.  
- If Mads later wants to suppress HK notifications for info-only messages, the motion inactive/active threshold can be raised to severity ≥ 2 (warning).

**Rich data layer: `Sensor` + custom attributes**  
`capability "Sensor"` is a Hubitat marker that carries no attributes but signals "this device produces readings." Add custom attributes for SharpTools and Rule Machine:

```
attribute "severity"      , "NUMBER"    -- 0=clear, 1=info, 2=warning, 3=critical
attribute "severityText"  , "ENUM"      -- "clear" | "info" | "warning" | "critical"
attribute "latestMessage" , "STRING"    -- single-line status (SharpTools Hero Attribute)
attribute "messages"      , "STRING"    -- JSON array for Custom Tile / Rule parsing
attribute "houseStatus"   , "STRING"    -- backward-compat mirror of latestMessage
attribute "tempTrend"     , "ENUM"      -- "rising" | "falling" | "stable"
```

**How homebridge-hubitat-tonesto7 works here:**  
The plugin discovers devices by declared capability. Declaring `ContactSensor` ensures the device appears in HomeKit automatically. Custom attributes (`severity`, `latestMessage`) do NOT flow to HomeKit — they are Hubitat/SharpTools-only. This is the intended layering: HomeKit gets a clean binary signal; SharpTools gets the rich structured data.

**Official Hubitat HomeKit integration** (if Mads uses it instead of homebridge): Also maps `ContactSensor` to HomeKit Contact Sensor. Same result.

---

## 3. Data Model

### Child Device Attributes

```
capability "Sensor"
capability "ContactSensor"    // HomeKit proxy: contact = open/closed
capability "Refresh"          // manual re-evaluate button

attribute "contact"        , "enum"   , ["open", "closed"]
attribute "severity"       , "number"                        // 0–3
attribute "severityText"   , "enum"   , ["clear","info","warning","critical"]
attribute "latestMessage"  , "string"                        // plain-text summary
attribute "messages"       , "string"                        // JSON-encoded array (see schema below)
attribute "houseStatus"    , "string"                        // backward-compat with existing SharpTools dashboard
attribute "tempTrend"      , "enum"   , ["rising","falling","stable","unknown"]
```

### messages JSON Schema

Each element in the `messages` array:
```json
{
  "id"      : "upstairs-zone-hot-1716485814",
  "ts"      : 1716485814000,
  "severity": 2,
  "source"  : "Upstairs Zone",
  "text"    : "Getting warm upstairs (83°F out, 78°F setpoint) — close windows"
}
```

The array is ordered: most severe first, then by timestamp descending. Max entries: 20 (prune oldest of same source before adding a new one).

### Child Device Commands

```
command "refresh"                               // trigger re-evaluation from app
command "clearMessages"                         // reset to all-clear
command "acknowledge"                           // clear severity-0 info messages only (user-facing)
```

The app calls lower-level internal write methods — `clearMessages()`, update attributes directly — not via the command API. The `refresh` command is the user-visible "recalculate now" button.

**Commands the app does NOT expose as device commands:**  
`addMessage(text, severity, source)` and `clearMessage(id)` should be **private app-side methods**, not device commands. Exposing them as device commands would let WebCoRE and RM call them directly, bypassing throttle and dedup logic. The app owns the write path.

---

## 4. App Preferences (Page Layout)

The app uses a multi-page preferences layout to keep the zone grouping legible.

**Page 1 — Outdoor Conditions**
```
input "outdoorTempSensor"  , "capability.temperatureMeasurement" , title: "Outdoor Temperature Sensor"
input "weatherDevice"      , "capability.weatherCondition"       , title: "Weather Device (rain/condition)"
input "airQualityDevice"   , "capability.airQuality"             , title: "Air Quality Monitor (AQI)"
input "rainAttribute"      , "string"  , title: "Rain attribute name on Weather Device (default: weather)"
input "heavyRainKeyword"   , "string"  , title: "Keyword in weather attribute that means rain (default: rain)"
```

Note: Hubitat does not have a standard `capability.rain`; the weather device likely has a `weather` (STRING) attribute. Make the keyword configurable.

**Page 2 — Zones**  
Three zone blocks, each with the same three inputs. Use dynamic pages if a 4th zone is ever needed.

```
section("Upstairs Zone") {
    input "upstairsThermostat" , "capability.thermostat"    , title: "Thermostat"
    input "upstairsContacts"   , "capability.contactSensor" , title: "Window/Door contacts" , multiple: true
}
section("Downstairs Zone") {
    input "downstairsThermostat" , "capability.thermostat"
    input "downstairsContacts"   , "capability.contactSensor" , multiple: true
}
section("Sunroom Zone") {
    input "sunroomThermostat" , "capability.thermostat"
    input "sunroomContacts"   , "capability.contactSensor" , multiple: true
}
```

**Page 3 — Notifications**
```
input "notificationDevices" , "capability.notification" , multiple: true , title: "Notify devices"
input "throttleMinutes"     , "number"  , title: "Min minutes between repeat notifications" , defaultValue: 60
input "speakerPistonId"     , "string"  , title: "webCoRE piston ID for speaker announcement (optional)"
```

**Page 4 — Advanced**
```
input "trendWindowMinutes"  , "number"  , title: "Temperature trend window (minutes)"       , defaultValue: 30
input "trendSamples"        , "number"  , title: "Ring buffer size (samples)"               , defaultValue: 12
input "comfortBuffer"       , "number"  , title: "Comfort buffer beyond setpoint (°F)"      , defaultValue: 3
input "logEnable"           , "bool"    , title: "Enable debug logging"                     , defaultValue: false
input "txtEnable"           , "bool"    , title: "Enable description text logging"          , defaultValue: true
```

---

## 5. Rising/Falling Temperature Trend

Store a ring buffer in app `state`:

```groovy
state.outdoorTempHistory = [
    [ts: 1716485100000, temp: 72.1],
    [ts: 1716485400000, temp: 72.8],
    // ... up to trendSamples entries
]
```

The app subscribes to `outdoorTempSensor` temperature events. On each event:
1. Append `[ts: now(), temp: event.value as BigDecimal]` to the ring buffer.
2. Drop entries older than `trendWindowMinutes * 60 * 1000` ms.
3. If fewer than 3 entries → trend = "unknown".
4. Otherwise: compute slope = (lastTemp - firstTemp) / (lastTs - firstTs). Convert to °F per minute.
   - slope > +0.15°F/min → "rising"
   - slope < -0.15°F/min → "falling"
   - else → "stable"

**State size:** 12 entries × ~45 bytes each ≈ 540 bytes. Negligible against Hubitat's ~100KB per-app state limit.

**Why this matters for logic:** If outdoor temp is rising toward the cooling setpoint, windows should be closed sooner. If falling away from the heating setpoint, the "close windows" urgency drops. The trend attribute is exposed on the child device so Rule Machine or SharpTools can use it independently.

---

## 6. Per-Zone Logic

Each zone is evaluated independently. Define an internal zone list at runtime:

```groovy
List zones = [
    [name: "Upstairs"  , thermostat: settings.upstairsThermostat  , contacts: settings.upstairsContacts],
    [name: "Downstairs", thermostat: settings.downstairsThermostat , contacts: settings.downstairsContacts],
    [name: "Sunroom"   , thermostat: settings.sunroomThermostat    , contacts: settings.sunroomContacts],
]
```

Filter out zones where thermostat or contacts list is null (user left a zone blank).

**Per-zone evaluation (pseudologic — Tank will implement):**

```
for each zone:
    coolSetpoint = zone.thermostat.currentValue("coolingSetpoint")
    heatSetpoint = zone.thermostat.currentValue("heatingSetpoint")
    openContacts = zone.contacts.findAll { it.currentValue("contact") == "open" }
    allClosed    = openContacts.isEmpty()
    outdoorTemp  = outdoorTempSensor.currentValue("temperature")
    
    if openContacts not empty:
        if outdoorTemp > coolSetpoint + comfortBuffer:
            addMessage(zone, "Getting warm — ${zone.name} temp ${outdoorTemp}°F, setpoint ${coolSetpoint}°F. Close windows.", severity=2)
        elif outdoorTemp < heatSetpoint - comfortBuffer:
            addMessage(zone, "Getting cold — ${zone.name} temp ${outdoorTemp}°F, setpoint ${heatSetpoint}°F. Close windows.", severity=2)
        if isRaining and heavyRain:
            addMessage(zone, "Rain detected — close ${zone.name} windows.", severity=3)
        if airQualityIndex > AQI_MODERATE_THRESHOLD:
            addMessage(zone, "Poor air quality (AQI ${aqi}) — keep ${zone.name} windows closed.", severity=2)
    
    if allClosed:
        if outdoorTemp in comfortRange AND !isRaining AND aqi < AQI_GOOD_THRESHOLD:
            if tempTrend != "rising" (toward cooling) AND tempTrend != "falling" (toward heating):
                addMessage(zone, "${zone.name}: outdoor temp comfortable (${outdoorTemp}°F) — consider opening windows.", severity=1)
```

**Zone severity aggregation:** After evaluating all zones, collect all generated messages. The child device's `severity` = max severity across all messages. `contact = "open"` if severity ≥ 1 (any message at all — including info suggestions). Alternatively, Mads may prefer `contact = "open"` only for severity ≥ 2 (suppress HomeKit alert for info-only suggestions) — this is a configurable threshold.

**All-clear state:** If zero messages are generated, emit:
- `latestMessage = "All clear — no climate issues detected"`
- `houseStatus = "All clear"`
- `severity = 0`, `severityText = "clear"`, `contact = "closed"`

This directly satisfies Mads's requirement #4.

---

## 7. SharpTools Rendering

Three display patterns, from simple to rich:

**a) Hero Attribute Tile (zero config)**  
Point any SharpTools tile's "Hero Attribute" at `latestMessage`. Displays the plain-text summary. This already works with the existing SharpTools dashboard if the tile is reconfigured to read the attribute instead of the trigger URL.

**b) Severity color via Rule Machine (no SharpTools custom tile needed)**  
Create an RM rule: `When [Climate Advisor] severity changes → if severity >= 2, turn on [virtual switch "alert"]; else turn off.` Use the virtual switch to drive SharpTools tile color rules. Alternatively, SharpTools Rules can read `severityText` directly and apply color profiles.

**c) Multi-message Custom Tile (richest)**  
SharpTools Custom Tile (HTML/JS) reads the `messages` attribute, parses the JSON array, renders a formatted list with colored severity badges. This is pure front-end work — the driver just provides the JSON string; no Hubitat changes needed for the tile to evolve.

---

## 8. Migration Path from webCoRE Piston

The migration is designed to be zero-downtime:

1. **Deploy the app and child device** — it starts emitting `houseStatus` attribute immediately, mirroring `latestMessage`.
2. **Reconfigure SharpTools Hero Attribute tile** — change from the trigger URL webhook to the `houseStatus` attribute on the new child device. SharpTools natively polls device attributes; no webhook URL needed.
3. **Throttle notifications** — the new app handles the 1-hour throttle internally, same as the piston. The piston's notification logic can be disabled first, then removed after a week of parallel running.
4. **Retire the piston** — once the SharpTools tile is reading the device attribute and all zones + features are validated, the webCoRE piston can be paused and eventually deleted.

The `houseStatus` attribute stays in the data model permanently as a first-class attribute (not just a migration shim) because SharpTools dashboard tiles may also reference it by name in tile configurations that are time-consuming to rebuild.

---

## Summary Decisions

| Question | Decision |
|---|---|
| Architecture | Parent App + Child Virtual Device |
| HomeKit capability | `ContactSensor` (contact: open/closed as alert proxy) |
| Rich data capability | `Sensor` + custom attributes (severity, latestMessage, messages JSON, houseStatus) |
| App location | `apps/climate-advisor/climate-advisor-app.groovy` |
| Driver location | `drivers/climate-advisor/climate-advisor-device.groovy` |
| Zone grouping | Hardcoded 3 zones (Upstairs/Downstairs/Sunroom) — preferences page per zone |
| Temp trend | Ring buffer in app state, slope over configurable window (default 30 min) |
| All-clear message | Emit "All clear — no climate issues detected" explicitly |
| houseStatus attribute | Keep permanently (backward compat + SharpTools label) |
| contact threshold | Configurable: open at severity ≥ 1 (default) or severity ≥ 2 (suppress info alerts to HomeKit) |

---

*No Groovy code in this document — Tank implements after Mads approves.*

---

## Revision 1 — Concrete Device Inventory (2026-05-23)

This section supersedes/supplements the abstract preferences and zone-logic sections above with real device names, driver types, and edge cases discovered from the actual 139-device inventory.

---

### R1. Confirmed: homebridge-hubitat-tonesto7 bridge (not built-in integration)

All sampled devices show `homekit: false`, which means Mads is NOT using the official Hubitat HomeKit integration. He is using the **homebridge-hubitat-tonesto7** community bridge. This is the most popular community option and it maps:

- `ContactSensor` → HomeKit `Contact Sensor` accessory (contact: open → alert, closed → clear) ✅  
- `MotionSensor` → HomeKit `Motion Sensor` accessory ✅  
- Custom attributes → NOT mapped to HomeKit (SharpTools/RM only) ✅

**`ContactSensor` as the HomeKit proxy is confirmed.** No change to capability recommendation. The homebridge plugin specifically maps this capability; the child device will appear in the Apple Home app and can trigger iPhone notifications on state change.

---

### R2. Concrete Zone → Device Mapping

**Zone 1: Upstairs**
- Thermostat: `Upstairs thermostat` — Honeywell T6 Pro (`drivers/honeywell-t6-pro/`) — heatingSetpoint=69, coolingSetpoint=75, mode=off
- Contacts (all windows — climate-pure): `Bedroom window left`, `Bedroom window right`, `Claire's window`, `Lincoln's window (east)`, `Lincoln's window (north)`, `Wesley's window (north)`, `Wesley's window (west)`, `Office window`
- Zone note: All 8 contacts are windows. No door ambiguity. Cleanest zone for climate analysis.

**Zone 2: Downstairs**
- Thermostat: `Downstairs thermostat` — Honeywell T6 Pro (`drivers/honeywell-t6-pro/`) — heatingSetpoint=69, coolingSetpoint=75, mode=auto
- Windows (include): `Living room window left`, `Living room window right`
- Exterior doors (include — climate-relevant): `Front door`, `Garage door`
- Ambiguous doors (recommend EXCLUDE by default):
  - `Garage/sunroom door` — this is a pass-through between the Garage and the Sunroom zone, not a direct outdoor opening. Its HVAC impact crosses zones. Recommend excluding from both Downstairs and Sunroom contact lists unless Mads wants to track it.
  - `Bathroom shower door` — interior door; irrelevant to climate analysis. Exclude by default.
- Zone note: Mixed windows + exterior doors. Since the preferences use `multiple: true` device lists, Mads simply doesn't add the shower and garage/sunroom door to this zone's input list when configuring the app.

**Zone 3: Sunroom**
- Thermostat: `Sunroom mini-split` — Daikin WiFi (`drivers/daikin-wifi/`) — heatingSetpoint=64.4, coolingSetpoint=75.2, mode=off
- Contacts (all exterior doors): `Sunroom left door`, `Sunroom right door`, `Sunroom side door`
- Zone note: All contacts are exterior doors, which is semantically equivalent to windows for HVAC analysis. The Daikin driver stores setpoints as `BigDecimal` (decimal values like 64.4, 75.2) — the evaluation logic must use BigDecimal comparison, not integer/string. The existing Daikin driver (`daikin-wifi.groovy`) exposes `coolingSetpoint` and `heatingSetpoint` as standard `Thermostat` capability attributes — no driver changes needed.

**Devices to exclude entirely from all zones:**
- `Garage window` — no thermostat zone for the garage
- `Dryer door`, `Washer door`, `Dishwasher door` — appliance state doors, not HVAC-relevant
- `Deborah door` — context unclear; conservative default is exclude

---

### R3. Outdoor Data Sources — Two Devices, Two Roles

The inventory provides two outdoor data sources. Use both, for different purposes:

| Role | Device | Attribute | Why |
|---|---|---|---|
| Outdoor temperature + trend | `Backyard sensor` (Philio PAT02-B, Z-Wave) | `temperature` (54.98°F) | Local hardware sensor, physically outside, Z-Wave event-driven (no poll latency). Primary source for temp/trend ring buffer. |
| Rain / weather conditions | `Weather` (OpenWeatherMap app device) | `weather` (string) | Cloud-polled; has the `weather` attribute the existing piston uses. The only source for rain detection in the home. Also has `temperature=53` — close to Backyard but slightly lower; do NOT use for temp (less precise, cloud-polled, slight lag). |
| Air quality | `Air quality sensor` (PurpleAir AQI Virtual Sensor, `drivers/purpleair-aqi/`) | `aqi` (18), `pm2_5` (4.3) | Note: this driver uses custom `aqi` attribute, NOT Hubitat's standard `AirQuality` capability `airQualityIndex` attribute. The app preferences input should be `capability.sensor` with a note, or simply `capability.airQuality` and fall back to reading the `aqi` attribute directly. |

**AQI thresholds (EPA breakpoints, calibrated to current reading AQI=18 = Good):**
- `≤ 50` — Good (current: AQI=18) → no AQI-based message; window suggestions not suppressed
- `51–100` — Moderate → severity 1 info: "Air quality is moderate (AQI X)"
- `101–150` — Unhealthy for Sensitive Groups → severity 2 warning: "Keep openings closed (AQI X)"
- `> 150` — Unhealthy/Very Unhealthy → severity 3 critical: "Close openings now — poor air quality (AQI X)"

These thresholds apply only when contacts are open. Suggestions to open windows are suppressed at AQI > 50.

---

### R4. Thermostat Mode Awareness — Critical Logic Nuance

Both T6 Pro thermostats and the Daikin mini-split have `thermostatMode`. The evaluation logic must be mode-aware:

| thermostatMode | Setpoint logic |
|---|---|
| `"off"` | HVAC not running. Skip hot/cold setpoint alerts. Still check rain and AQI if contacts open. |
| `"heat"` | Only compare outdoor vs `heatingSetpoint`. Skip cooling concern. |
| `"cool"` | Only compare outdoor vs `coolingSetpoint`. Skip heating concern. |
| `"auto"` | Compare both setpoints (Downstairs thermostat is currently in auto mode). |
| `"fan"` (Daikin) | Same as `"off"` for setpoint logic — no heating/cooling active. |

This is new to the proposal and must be in Tank's logic. Currently Upstairs is mode=off and Sunroom is mode=off — the existing piston doesn't handle this gracefully. The new app must not spam setpoint alerts about a thermostat that isn't running.

---

### R5. Updated Zone-Logic Pseudologic (Mode-Aware)

Replace the pseudologic in §6 with this version:

```
for each zone (skip if thermostat or contacts list is null):
    mode         = zone.thermostat.currentValue("thermostatMode")
    coolSetpoint = zone.thermostat.currentValue("coolingSetpoint") as BigDecimal
    heatSetpoint = zone.thermostat.currentValue("heatingSetpoint") as BigDecimal
    openContacts = zone.contacts.findAll { it.currentValue("contact") == "open" }
    allClosed    = openContacts.isEmpty()
    outdoorTemp  = backyardSensor.currentValue("temperature") as BigDecimal

    if openContacts not empty:
        // Rain check — always applies regardless of HVAC mode
        if isRaining:
            addMessage(zone, "Rain detected — close ${zone.name} openings.", severity=3)

        // AQI check — always applies regardless of HVAC mode
        if aqi > 150:
            addMessage(zone, "Poor air quality (AQI ${aqi}) — close ${zone.name} openings now.", severity=3)
        elif aqi > 100:
            addMessage(zone, "Unhealthy air (AQI ${aqi}) — close ${zone.name} openings.", severity=2)
        elif aqi > 50:
            addMessage(zone, "Air quality moderate (AQI ${aqi}) — consider closing ${zone.name} openings.", severity=1)

        // Setpoint checks — only when HVAC is actively conditioning
        if mode in ["cool", "auto"]:
            if outdoorTemp > coolSetpoint + comfortBuffer:
                addMessage(zone, "Outdoor ${outdoorTemp}°F exceeds ${zone.name} cooling setpoint. Close openings.", severity=2)
        if mode in ["heat", "auto"]:
            if outdoorTemp < heatSetpoint - comfortBuffer:
                addMessage(zone, "Outdoor ${outdoorTemp}°F below ${zone.name} heating setpoint. Close openings.", severity=2)

        // Trend early-warning — approaching setpoint (severity 1, only when HVAC active)
        if mode in ["cool", "auto"] and tempTrend == "rising" and outdoorTemp > (coolSetpoint - comfortBuffer):
            addMessage(zone, "Outdoor temp rising toward ${zone.name} cooling setpoint — close openings soon.", severity=1)
        if mode in ["heat", "auto"] and tempTrend == "falling" and outdoorTemp < (heatSetpoint + comfortBuffer):
            addMessage(zone, "Outdoor temp falling toward ${zone.name} heating setpoint — close openings soon.", severity=1)

    if allClosed and mode not in ["off", "fan"]:
        // Suggest opening only when HVAC active and outdoor temp is within the comfort band
        if outdoorTemp >= heatSetpoint AND outdoorTemp <= coolSetpoint:
            if !isRaining AND aqi <= 50:
                if tempTrend == "stable"
                   OR (tempTrend == "rising"  AND outdoorTemp < coolSetpoint - comfortBuffer)
                   OR (tempTrend == "falling" AND outdoorTemp > heatSetpoint + comfortBuffer):
                    addMessage(zone, "${zone.name}: outdoor ${outdoorTemp}°F comfortable — good time to open.", severity=1)
```

Key changes vs v1 pseudologic:
1. **Mode gate** on setpoint checks — no hot/cold alerts when HVAC is off
2. **Trend-aware early warning** (severity 1) before the setpoint is breached, not just after
3. **Trend-aware suppression** of open-window suggestions (don't suggest opening if temp is rising toward the cooling setpoint)
4. **BigDecimal coercion** on setpoint reads (critical for Daikin's 64.4/75.2 values)
5. **Rain and AQI always apply** regardless of HVAC mode

---

### R6. Concrete Preferences — Real Device Names as Defaults/Examples

Tank should pre-populate `defaultValue` / inline comments with these real device names:

**Outdoor Conditions section:**
```groovy
input "backyardSensor"   , "capability.temperatureMeasurement"
    // → "Backyard sensor" (Philio PAT02-B Z-Wave)
input "weatherDevice"    , "capability.sensor"
    // → "Weather" (OpenWeatherMap)
input "rainAttribute"    , "string" , defaultValue: "weather"
input "rainKeyword"      , "string" , defaultValue: "rain"     // case-insensitive contains check
input "airQualityDevice" , "capability.sensor"
    // → "Air quality sensor" (PurpleAir AQI Virtual Sensor)
input "aqiAttribute"     , "string" , defaultValue: "aqi"
    // PurpleAir uses custom "aqi" attribute, not standard "airQualityIndex"
```

**Zone sections:**
```groovy
section("Upstairs Zone") {
    input "upstairsThermostat", "capability.thermostat"
        // → "Upstairs thermostat" (Honeywell T6 Pro)
    input "upstairsContacts", "capability.contactSensor", multiple: true
        // Add: Bedroom window left/right, Claire's window,
        //      Lincoln's window east/north, Wesley's window north/west, Office window
}
section("Downstairs Zone") {
    input "downstairsThermostat", "capability.thermostat"
        // → "Downstairs thermostat" (Honeywell T6 Pro)
    input "downstairsContacts", "capability.contactSensor", multiple: true
        // Add: Living room window left/right, Front door, Garage door
        // Do NOT add: Bathroom shower door, Garage/sunroom door
}
section("Sunroom Zone") {
    input "sunroomThermostat", "capability.thermostat"
        // → "Sunroom mini-split" (Daikin WiFi)
    input "sunroomContacts", "capability.contactSensor", multiple: true
        // Add: Sunroom left door, Sunroom right door, Sunroom side door
}
```

---

### R7. Zone-Specific Sonos Announcements (Replace webCoRE Speaker Piston)

The inventory includes per-zone Sonos Advanced speakers. Rather than forwarding to a "Send notifications" webCoRE piston, the app speaks directly via `capability.speechSynthesis` (Hubitat's TTS interface, which Sonos Advanced supports).

**Revised notifications preferences:**
```groovy
section("Notifications") {
    input "notificationDevices" , "capability.notification"    , multiple: true
        // push notification devices (phones)
    input "announcementDevices" , "capability.speechSynthesis" , multiple: true
        // → "Sonos Advanced - Upstairs master", "Downstairs Master", "Sunroom Speaker"
        //   (or "Sonos Group: Downstairs and outside" for broader reach)
    input "announcementSeverityThreshold", "number", defaultValue: 2
        // Only speak announcements for severity >= this value
        // severity=1 (info suggestions) → SharpTools display only, no audio
    input "throttleMinutes"     , "number"                     , defaultValue: 60
    input "speakerPistonId"     , "string"
        // optional — keep for backward compat during migration, remove after validation
}
```

The `speakerPistonId` preference stays during migration. Once Mads validates direct Sonos TTS, the preference can be hidden/removed in a future version.

---

### R8. Revised Summary Decisions Table (v2)

| Question | Decision |
|---|---|
| Architecture | Parent App + Child Virtual Device |
| HomeKit bridge | homebridge-hubitat-tonesto7 (confirmed by `homekit: false` on sampled devices) |
| HomeKit capability | `ContactSensor` — contact: open/closed — confirmed compatible with the bridge |
| Rich data | `Sensor` + custom attrs: severity, severityText, latestMessage, messages, houseStatus, tempTrend |
| Outdoor temp source | `Backyard sensor` (Philio PAT02-B) — primary; `Weather` (OpenWeatherMap) for rain attribute only |
| AQI source | `Air quality sensor` via `aqi` custom attribute (not standard `airQualityIndex`) |
| Thermostat mode gate | Setpoint checks only when mode is heat/cool/auto; rain+AQI always apply |
| Setpoint type | BigDecimal throughout — Daikin reports 64.4/75.2; T6 Pro reports 69/75 |
| Upstairs contacts | 8 windows: Bedroom L/R, Claire's, Lincoln's E/N, Wesley's N/W, Office |
| Downstairs contacts | Living room L/R windows + Front door + Garage door (exclude Bathroom shower + Garage/sunroom) |
| Sunroom contacts | Sunroom left/right/side doors |
| Excluded devices | Garage window, Dryer/Washer/Dishwasher/Deborah doors |
| Announcements | Direct `capability.speechSynthesis` → Sonos Advanced; severity threshold default=2 |
| webCoRE speaker piston | Keep `speakerPistonId` preference for migration; remove after validation |
| All-clear message | "All clear — no climate issues detected"; contact=closed, severity=0 |
| houseStatus attribute | Permanent — backward compat + stable SharpTools dashboard anchor |
| App location | `apps/climate-advisor/climate-advisor-app.groovy` |
| Driver location | `drivers/climate-advisor/climate-advisor-device.groovy` |

*Ready for Tank to implement once Mads approves.*


---

# Cypher Research: House Status / Climate Advisor — Capability & Protocol Spec

**By:** Cypher  
**Date:** 2026-05-23T14:56:54-07:00  
**Requested by:** Mads  
**Status:** Research findings — ready for team decision

---

## Summary

Mads wants a "house status / climate advisor" virtual device on Hubitat that surfaces a **SEVERITY** level, a **latest MESSAGE** string, and an **ARRAY of recent messages**, rendering on:
- (a) SharpTools tablet dashboards (Fully Kiosk tablets)
- (b) Apple HomeKit via the Hubitat→HomeKit bridge

Key conclusion up front: **HomeKit cannot display arbitrary string attributes** — it only gets the binary status signal. SharpTools gets everything. The primary architectural split is therefore:

> **HomeKit** = "alert / clear" binary  
> **SharpTools** = full rich text (message, severity, history)

---

## 1. HomeKit Bridge Landscape for Hubitat (2026)

### 1a. Hubitat Built-in HomeKit Bridge (first-party)

The dominant bridge in 2026 is Hubitat's **own first-party HomeKit Bridge**, now built into the hub and available on all supported models (C-5, C-7, C-8, C-8 Pro). No Homebridge server, no middleware. Users scan a QR code and select devices to expose.

- **Source:** https://docs2.hubitat.com/apps/homekit-integration
- **Source:** https://hubitat.com/home-automation/homekit-integration

Limitations:
- One-way only (Hubitat → HomeKit). HomeKit cannot push commands back in the same session.
- Only exposes supported device capability classes (see table below). Custom string attributes do NOT cross.
- Pairing shows "Not Certified" warning — tap "Add Anyway." This is normal.
- No Rule Machine, no dashboards, no automation logic crosses to HomeKit.

### 1b. homebridge-hubitat-tonesto7 (community, still maintained)

Still actively maintained as of mid-2025 (last major commit 2025). A complete JavaScript rewrite. Requires a Homebridge server (Raspberry Pi / NAS). Connects via MakerAPI for near-real-time push updates.

- **Source:** https://github.com/tonesto7/homebridge-hubitat-tonesto7
- **Community thread:** https://community.hubitat.com/t/release-homebridge-hubitat-v2-0/54056

Use cases where this is preferred over the built-in bridge:
- Advanced device types not supported by native bridge
- Hubitat HSM (security monitor) integration
- Better HomeKit button support
- Custom driver capability mapping

**For this project:** The built-in bridge is sufficient. The tonesto7 plugin is a fallback if the built-in bridge does not expose the chosen capability.

### 1c. Capability → HomeKit Accessory Type Mapping

Both bridges follow the same capability-to-HomeKit mapping pattern. Below is the relevant subset:

| Hubitat Capability       | HomeKit Accessory Type          | HK Push Notification?   | HK Automation Trigger? |
|-------------------------|---------------------------------|-------------------------|------------------------|
| `ContactSensor`          | Contact Sensor                  | Yes (standard)          | Yes                    |
| `MotionSensor`           | Motion Sensor                   | Yes (standard)          | Yes                    |
| `SmokeDetector`          | Smoke Sensor                    | Yes (**Critical Alert**) | Limited               |
| `CarbonMonoxideDetector` | Carbon Monoxide Sensor          | Yes (**Critical Alert**) | Limited               |
| `WaterSensor`            | Leak Sensor                     | Yes (standard)          | Yes                    |
| `Switch`                 | Switch                          | No (state only)         | Yes                    |
| `PresenceSensor`         | Occupancy Sensor (limited)      | Yes (standard)          | Yes                    |
| `TamperAlert`            | ❌ Not mapped natively           | —                       | —                      |
| `Notification`           | ❌ Not a HomeKit concept         | —                       | —                      |
| `AudioNotification`      | ❌ Not a HomeKit concept         | —                       | —                      |

---

## 2. Capability Candidates for Alert/Status Semantics

### `ContactSensor`
- **HomeKit type:** Contact Sensor
- **Values:** `open` / `closed`
- **Semantics for our use:** `open` = alert active, `closed` = all clear
- **HomeKit notifications:** Standard priority. Respects Do Not Disturb. Will be silenced if phone is muted.
- **HomeKit automations:** Full support — user can build automations on open/closed.
- **Verdict:** ✅ Semantically honest ("something is open/unresolved"), reliable, no controversy.

### `MotionSensor`
- **HomeKit type:** Motion Sensor
- **Values:** `active` / `inactive`
- **HomeKit notifications:** Standard priority.
- **Verdict:** ⚠️ Slightly misleading semantics for a house status device. Skip unless no other option.

### `SmokeDetector`
- **HomeKit type:** Smoke Sensor
- **Values:** `detected` / `clear` (Hubitat); maps to `SmokeDetected` characteristic in HomeKit
- **HomeKit notifications:** **Critical Alert** — bypasses Do Not Disturb AND silent mode (if Critical Alerts enabled in iOS Settings → Notifications → Home). Makes audible sound even on silenced phone.
- **HomeKit automations:** Limited automation triggers from smoke sensors (HomeKit restricts this intentionally).
- **Honesty concern:** Using a smoke detector for non-fire alerts is semantically dishonest. HomeKit Critical Alerts are intended for life-safety emergencies. Community precedent: Hubitat forums document this pattern (e.g., mapping dumb smoke alarm relays through virtual smoke detectors), and some users do use SmokeDetector for important alerts. However, using Critical Alerts for "house climate info" would be abusive — imagine getting a loud critical alert at 2am because your indoor humidity crossed 55%.
- **Verdict:** ❌ Do NOT use `SmokeDetector` unless severity = `critical` truly means "fire/CO/emergency". Using it for routine house status/climate is alert-abuse and will cause alert fatigue. **Reserve this pattern only if Mads specifically wants Critical Alert break-through for genuine emergencies** — and even then it's a judgment call.

### `CarbonMonoxideDetector`
- **Same analysis as SmokeDetector** (Critical Alert priority). Same verdict: ❌ for routine use.

### `WaterSensor`
- **HomeKit type:** Leak Sensor
- **Values:** `wet` / `dry`
- **HomeKit notifications:** Standard priority (not critical).
- **Verdict:** ⚠️ Semantics stretch too far. Using "wet/dry" for "alert/clear" is confusing. Skip.

### `Switch`
- **HomeKit type:** Switch
- **Values:** `on` / `off`
- **HomeKit notifications:** None built-in (Switch doesn't generate push notifications by default; automations can, but the user has to create them manually).
- **Verdict:** ⚠️ Works for HomeKit automations but no default push notification. Lower signal value.

### `PresenceSensor`
- **HomeKit type:** Occupancy Sensor (limited)
- **Verdict:** ❌ Semantically wrong, confusing.

### `TamperAlert`, `Notification`, `AudioNotification`
- **Verdict:** ❌ None of these cross the HomeKit bridge in any meaningful way. `TamperAlert` has no HomeKit native mapping.

---

## 3. String/Message Attributes Over the HomeKit Bridge

**Short answer: They don't cross. At all.**

HomeKit's accessory model is typed — temperature (number), lock state (enum), contact state (binary), etc. There is no HomeKit characteristic for arbitrary strings or dynamic text. The HomeKit protocol (HAP — HomeKit Accessory Protocol) does not have a "message" or "label" characteristic that apps surface to users.

- The Hubitat built-in HomeKit Bridge and homebridge-hubitat-tonesto7 both only map **standard capability attributes** (contact state, switch level, temperature, etc.) to HomeKit.
- Custom attributes (`latestMessage`, `severity`, `messages`, `houseStatus`) will **never appear in the Apple Home app** regardless of bridge choice.
- There is no HAP characteristic for arbitrary display text. Some third-party HomeKit apps (like Eve for HomeKit) can expose custom characteristics, but Apple's own Home app ignores them entirely.

**Architectural conclusion:**

| Channel     | Gets what                                           |
|-------------|-----------------------------------------------------|
| HomeKit     | Binary status signal only (alert/clear, one bit)    |
| SharpTools  | Full rich data: severity, latestMessage, messages[] |
| Hubitat RM  | All attributes available for automations            |

---

## 4. SharpTools Dashboard Rendering for Custom Attributes

SharpTools has three primary mechanisms for displaying custom Hubitat attributes on a tablet dashboard:

### 4a. Hero Attribute Tile (built-in Thing Tile)
- Add the device as a Thing tile. In tile settings, set **Hero Attribute** to `latestMessage` (or `severity`).
- The chosen attribute displays as the large, dominant value in the tile center.
- Works for any `string` or `number` attribute declared in the Hubitat driver.
- **Source:** https://help.sharptools.io/portal/en/kb/articles/thing-tiles-hero-attribute-and-secondary-attribute

### 4b. Super Tile (no-code visual editor, Premium)
- Drag-and-drop layout editor. Can combine multiple attributes from the same or different devices in one tile.
- Use for: `severity` (styled with color/icon based on value), `latestMessage` (as text), maybe a mini alert count.
- State mappings let you assign tile background color or icon based on severity values (`info` → green, `warning` → yellow, `critical` → red).
- **Source:** https://help.sharptools.io/article/92-super-tiles

### 4c. Custom Tile (HTML type, developer)
- Full HTML + CSS + JavaScript. Access device attributes via the `stio` library.
- Can display `messages` (JSON array) as a formatted scrollable list. Best option for message history.
- Example pattern: `stio.ready(function(data) { data.settings.myDevice.attributes['messages'].onValue(...) })`
- **Source:** https://docs.sharptools.io/developer-tools/custom-tiles/html.html  
- **stio library:** https://docs.sharptools.io/developer-tools/custom-tiles/stio-lib.html

### 4d. Rule-Driven Tile Color
- Use SharpTools Rules to change tile color or icon based on severity thresholds.
- Pairs well with any of the above tile types.

**Recommendation for Mads's tablets:**
- **Primary tile:** Super Tile showing `severity` (color-coded) + `latestMessage` (Hero text)
- **Secondary tile (optional):** Custom HTML tile rendering `messages` as a scrollable recent-history list

---

## 5. Existing Community Drivers for Similar Alert Aggregation

Searched Hubitat Package Manager (HPM) and community.hubitat.com for: "alert aggregator", "house status", "notification hub", "status dashboard driver", "virtual omni sensor".

**Findings:**
- No purpose-built "house status aggregator" driver with HomeKit support was found in HPM as a packaged community driver.
- The dominant pattern on the community forums is: **virtual sensor + Rule Machine logic**. Users pick an existing virtual device type (most commonly Virtual Contact Sensor or Virtual Motion Sensor) and drive it from Rule Machine when an aggregate condition is met.
- The Hubitat built-in **Virtual Omnisensor** (`Virtual Omni Sensor` device) supports multiple capabilities simultaneously (contact, motion, temperature, etc.) but does NOT include custom string attributes.
- Some users have built "combined status" virtual devices using virtual contact sensors driven by Rule Machine expressions — these cross the HomeKit bridge correctly as ContactSensor.
- **Community thread on virtual smoke detectors:** https://community.hubitat.com/t/virtual-smoke-and-virtual-garage-sensor/18389 — users explicitly requesting virtual smoke detector for HomeKit bridging. Developers responded positively. Confirms the pattern is known and legitimate for mapping dumb/relay smoke alarms.
- **Community thread on HomeKit + virtual contact:** https://community.hubitat.com/t/homekit-support-for-virtual-contact-sensor-with-switch-custom-driver/149239 — discusses export issues with multi-capability virtual sensors in HomeKit (minimize extra capabilities to avoid misclassification).

**Bottom line:** No existing community driver matches this exact use case (severity + message + HomeKit bridge). Tank is building something new.

---

## 6. Concrete Recommendation

### Primary HomeKit-Visible Capability: `ContactSensor`

**Pick: `ContactSensor`**

**Justification:**
- `open` = "alert active" (something in the house needs attention), `closed` = "all clear" — semantically honest and unsurprising.
- Works with Hubitat's built-in HomeKit Bridge out of the box. No Homebridge required.
- Generates standard-priority push notifications in HomeKit (HomeKit will ask "Front Door opened" style — Mads can rename the device to something meaningful like "House Status").
- Full HomeKit automation support — Mads can build automations like "if House Status opens between 9pm–7am, turn on a light."
- No ethical issues. No alert fatigue risk from critical alert abuse.
- If Mads later decides that **only truly critical** conditions (e.g., CO detected, fire alarm, flood) should break through DND, a SEPARATE device using `SmokeDetector` or `CarbonMonoxideDetector` is appropriate — but that's a different device for a different purpose.

**Do NOT use SmokeDetector** for general house status/climate. Using Critical Alerts for "indoor CO2 is high" or "you left a window open" is a textbook cry-wolf pattern that will cause the user to disable Critical Alerts entirely, defeating the purpose.

### Custom Attributes (SharpTools, NOT HomeKit visible)

```groovy
attribute "severity",      "ENUM", ["info", "warning", "critical"]
attribute "latestMessage", "string"
attribute "messages",      "JSON_OBJECT"   // JSON array, 10 most recent messages
attribute "houseStatus",   "string"        // human-readable state, e.g. "All clear" / "Warning: high humidity"
```

- `severity` drives tile color in SharpTools Super Tile state mappings.
- `latestMessage` is the Hero Attribute on the primary tile.
- `messages` (JSON array) is consumed by a Custom HTML tile for scrollable history.
- `houseStatus` is a convenience string for Rule Machine string comparisons and WebCoRE pistons (some users prefer matching strings over ENUMs).

### Contact attribute for HomeKit

```groovy
attribute "contact", "ENUM", ["open", "closed"]  // required by ContactSensor capability
```

Maps: `severity == "info"` → `contact = "closed"` (all clear), `severity == "warning" || "critical"` → `contact = "open"` (alert).

### Capability Declaration (for Tank)

```groovy
capability "ContactSensor"          // crosses HomeKit bridge
capability "Sensor"                 // boilerplate, pairs with ContactSensor
capability "Refresh"                // optional, allows manual poll/refresh
```

---

---

## Addendum — 2026-05-23T14:56:54-07:00: Device-Specific Findings

*Added after Mads provided the actual device inventory.*

---

### A1. Bridge Identification: tonesto7, NOT Built-in

The `homekit: false` flag Mads sees on sampled devices is the per-device inclusion toggle from the **homebridge-hubitat-tonesto7 Hubitat app** (`homebridge-v2.groovy`). The built-in Hubitat HomeKit Bridge does not expose a `homekit` attribute — it uses a separate app UI with no per-device attribute injection.

**Confirmed: Mads uses homebridge-hubitat-tonesto7.** All bridge behavior in this document applies to that plugin.

Per-device inclusion in tonesto7 is controlled by device selection within the Hubitat-side app (`homebridge-v2.groovy`). To include the new virtual advisor device in HomeKit, Tank must instruct Mads to:
1. Open the Homebridge Hubitat app on the hub
2. Add the new device to the selected devices list

The `homekit: false` attribute on a device means it was NOT selected for bridge inclusion at last sync.

**Source:** https://github.com/tonesto7/homebridge-hubitat-tonesto7

---

### A2. tonesto7 + SmokeDetector → HomeKit Critical Alert: Confirmed

tonesto7 maps Hubitat `SmokeDetector` → HomeKit `SmokeSensor` service, and `CarbonMonoxideDetector` → `CarbonMonoxideSensor`. These trigger **HomeKit Critical Alerts** — the highest iOS notification priority, bypassing Do Not Disturb and Silent mode.

**This is confirmed through the bridge regardless of whether Mads uses the built-in bridge or tonesto7.** Both bridges make the same HAP mapping.

Operational consequence for this project: if Mads WANTS critical-priority alerts to break through DND for severity=`critical` events (e.g., CO alarm triggered, flood sensor wet), a separate dedicated virtual `SmokeDetector` or `CarbonMonoxideDetector` device could be added alongside the main `ContactSensor`-based advisor. But this is a separate device with a dedicated, honest purpose — **not the general-status device**.

**Recommendation stands:** Use `ContactSensor` for the main advisor. Reserve a separate virtual `SmokeDetector` ONLY if Mads adds a genuine fire/CO/flood escalation path.

---

### A3. PurpleAir Driver: `AirQuality` Capability — Confirmed

Inspected `drivers/purpleair-aqi/purpleair-aqi.groovy` directly.

**Line 49:**
```groovy
capability "AirQuality"
```

**Line 439:**
```groovy
emitIfChanged("airQualityIndex", aqi2_5Value, "${device.displayName} air quality index is ${aqi2_5Value}", "AQI")
```

**Confirmed:** The driver implements the standard Hubitat `AirQuality` capability AND emits `airQualityIndex` (the standard attribute defined by that capability). It also emits the custom `aqi` attribute (line 438) for backward compatibility, but `airQualityIndex` is the canonical attribute.

**Implication for Tank:** The advisor driver can declare:
```groovy
input "airQualitySensor", "capability.airQuality", title: "Air Quality Sensor", required: false
```
and read:
```groovy
def aqiVal = airQualitySensor?.currentValue("airQualityIndex")
```

This input accepts ANY Hubitat device implementing `AirQuality` — the PurpleAir driver works today, and future AQ devices (Aqara, etc.) will work automatically if they also implement the capability. **No special-casing for PurpleAir is needed.**

Note: PurpleAir also exposes `pm2_5`, `confidence`, `category`, and `conversion` as custom attributes. The advisor can optionally read `pm2_5` for a richer message (e.g., "AQI 87 (PM2.5: 23.4 µg/m³)") if it detects those attributes are present on the connected device via `device.hasAttribute("pm2_5")`.

---

### A4. Device Inventory and Capability Map

| Device | Driver | Key Capabilities | Advisor Reads |
|--------|--------|-----------------|---------------|
| Upstairs thermostat (Honeywell T6 Pro) | Hubitat built-in | `Thermostat`, `TemperatureMeasurement`, `RelativeHumidityMeasurement` | `temperature`, `heatingSetpoint`, `coolingSetpoint`, `thermostatMode` |
| Downstairs thermostat (Honeywell T6 Pro) | Hubitat built-in | `Thermostat`, `TemperatureMeasurement`, `RelativeHumidityMeasurement` | `temperature`, `heatingSetpoint`, `coolingSetpoint`, `thermostatMode` |
| Sunroom mini-split (Daikin WiFi) | Mads custom Daikin driver | `Thermostat`, `TemperatureMeasurement` | `temperature`, `thermostatMode`, `heatingSetpoint` |
| PurpleAir AQI Virtual Sensor | Mads custom PurpleAir driver | `AirQuality`, `TemperatureMeasurement`, `RelativeHumidityMeasurement` | `airQualityIndex`, optionally `pm2_5`, `category` |
| OpenWeatherMap | Community OWM driver | `TemperatureMeasurement`, `RelativeHumidityMeasurement`, various | `weather` (string), `temperature`, `humidity` |
| Backyard sensor (Philio PAT02-B) | Hubitat built-in | `TemperatureMeasurement`, `RelativeHumidityMeasurement` | `temperature`, `humidity` |
| Sonos speakers (×4) | Hubitat built-in Sonos | `AudioNotification`, `MusicPlayer`, `SpeechSynthesis` | Not read; COMMANDED for announcements |

#### Thermostat capability note
All three thermostats implement `capability "Thermostat"` which includes `temperature`, `thermostatMode`, `heatingSetpoint`, `coolingSetpoint`. Tank can use a single `input "thermostats", "capability.thermostat", multiple: true` to capture all three in preferences — user selects which three.

#### OpenWeatherMap `weather` attribute
The `weather` attribute (string, e.g. `"Rain"`, `"Clouds"`, `"Clear"`) is a custom attribute on the OWM driver, NOT part of any standard Hubitat capability. The advisor cannot use a capability-typed input for it. Use `input "weatherDevice", "device.OpenWeatherMap"` (device type) or a generic `capability.sensor` + document the expected attribute name. Recommend: generic `capability.sensor` input with a `note` in preferences indicating the `weather` attribute is expected. Driver duck-typing: `weatherDevice?.currentValue("weather")` — returns null gracefully if attribute is absent.

---

### A5. Sonos Integration for Severity Announcements

The four Sonos speakers (Upstairs Master, Downstairs Master, Sunroom Speaker, Backyard Speaker) support `SpeechSynthesis` and `AudioNotification` capabilities. The advisor can announce severity changes.

**Recommended pattern:**
```groovy
input "announceSpeakers", "capability.speechSynthesis", title: "Announcement speakers", multiple: true, required: false
input "announceOnSeverity", "enum", title: "Announce when severity reaches", options: ["warning", "critical"], defaultValue: "critical"
```

Driver command:
```groovy
announceSpeakers?.each { it.speak("House status: ${latestMessage}") }
```

**Do NOT** use `AudioNotification` (plays audio file by URL) — use `SpeechSynthesis.speak()` for dynamic text. Hubitat's Sonos integration implements both, but `speak()` is what generates a TTS announcement.

---

### A6. Updated Capability Declaration for Tank

Given the full device inventory, the advisor driver needs these preference inputs:

```groovy
input "thermostats",      "capability.thermostat",                title: "Thermostat(s)",          multiple: true,  required: false
input "airQualitySensor", "capability.airQuality",                title: "Air Quality Sensor",     multiple: false, required: false
input "weatherDevice",    "capability.sensor",                    title: "Weather Device",         multiple: false, required: false, description: "Expects 'weather' string attribute (e.g. OpenWeatherMap)"
input "outdoorSensor",    "capability.temperatureMeasurement",    title: "Outdoor Temp/Humidity",  multiple: false, required: false
input "announceSpeakers", "capability.speechSynthesis",           title: "Announcement Speakers",  multiple: true,  required: false
input "announceOnSeverity","enum", options:["warning","critical"],title: "Announce at severity",   defaultValue: "critical"
```

All capability-typed inputs are standard Hubitat capabilities — they will accept any present or future device implementing that capability, without driver-specific coupling.

---

## Sources Cited

| URL | What it covers |
|-----|----------------|
| https://docs2.hubitat.com/apps/homekit-integration | Official Hubitat HomeKit Bridge docs |
| https://hubitat.com/home-automation/homekit-integration | Hubitat product page |
| https://github.com/tonesto7/homebridge-hubitat-tonesto7 | homebridge-hubitat-tonesto7 source |
| https://community.hubitat.com/t/release-homebridge-hubitat-v2-0/54056 | tonesto7 community release thread |
| https://community.hubitat.com/t/virtual-smoke-and-virtual-garage-sensor/18389 | Virtual smoke detector community request |
| https://community.hubitat.com/t/homekit-support-for-virtual-contact-sensor-with-switch-custom-driver/149239 | Virtual contact sensor + HomeKit export notes |
| https://help.sharptools.io/portal/en/kb/articles/thing-tiles-hero-attribute-and-secondary-attribute | SharpTools Hero Attribute tile docs |
| https://help.sharptools.io/article/92-super-tiles | SharpTools Super Tiles docs |
| https://docs.sharptools.io/developer-tools/custom-tiles/html.html | SharpTools Custom HTML Tile docs |
| https://docs.sharptools.io/developer-tools/custom-tiles/stio-lib.html | stio library reference |
| https://www.dumbswitches.com/hubitat-homekit-integration/ | 2026 practical guide to Hubitat HomeKit setup |


---

# Decision: Away Lights — Mode Subscription Lifecycle & Unconditional Cleanup (v0.8.1)

**Date:** 2026-05-20  
**Author:** Tank  
**File:** `apps/away-lights/away-lights.groovy`

## Context

v0.8.1 resource cleanup task asked for:
1. `unschedule("offTimeHandler")` on mode exit
2. A "subscription manager" that subscribes to mode events only during Away mode and unsubscribes on exit

Mads clarified: backcompat is not a priority pre-v1.0.0 — make breaking changes if needed.

## Decision

### Enhancement 1 — `unschedule("offTimeHandler")` is UNCONDITIONAL

The unschedule fires on ANY Away exit (`else` block), not just inside `turnOffOnHome=true`. When `turnOffOnHome=false`, the old code left `offTimeHandler` scheduled to fire at end-time and then no-op (mode guard). Pure waste. Since `offTimeHandler` only turns off lights when `location.mode == awayMode`, cancelling it early has no visible behavioral difference and eliminates the wasted invocation.

### Enhancement 2 — `else if (turnOffOnHome)` → `else` (structural)

All resource cleanup (`unschedule` × 3, state reset) is now unconditional on Away exit. Only `lightsOff()` stays inside `if (turnOffOnHome)`. Previously, `turnOffOnHome=false` did ZERO cleanup on Away exit — `checkAndTurnOn` and `doLightsOn` ran and no-oped at their scheduled times.

### What was NOT done — mode subscription cannot be made conditional

`subscribe(location, "mode", modeHandler)` in `initialize()` remains permanent. You cannot subscribe "only during Away mode" for the subscription that detects Away entry — it's circular. A `unsubscribe("modeHandler")` + `subscribe(...)` pair in the exit block is a net no-op and was dropped.

If Hubitat ever adds value-filtered subscriptions (`subscribe(location, "mode.Away", handler)`), the circular dependency can be broken. Until then, the permanent subscription is the correct approach.

## Impact

**Breaking:** When `turnOffOnHome=false`, scheduled tasks now cancel on Away exit (previously lingered). No user-visible difference in light behavior — the break is resource-use only.


---

## Directive: Climate Advisor — Generic & Shareable, No HomeKit Required

**Date:** 2026-05-23T15:44:28-07:00  
**By:** Mads (via Copilot)  
**Action:** Coordinator captured
**Directive ID:** copilot-directive-20260523T154428

**What:** Climate Advisor app must be GENERIC — all devices (thermostats, contact sensors, weather, AQI, speakers) selectable via app preferences so other Hubitat users can install it. No hardcoded device IDs from Mads's home. HomeKit support is NOT a requirement — drop the ContactSensor capability if it adds no value; expose rich data via custom attributes for SharpTools.

**Why:** Shareable/HPM-distributable app. User explicitly questioned the HomeKit framing.

**Implication:** Trinity revised the architecture to v2 with all user-configurable preferences.

---

## Directive: Climate Advisor — UX Pattern (Main Page + Sub-Pages)

**Date:** 2026-05-23T15:48:08-07:00  
**By:** Mads (via Copilot, autopilot decision)  
**Action:** Coordinator captured
**Directive ID:** copilot-directive-zone-ux-20260523T154808

**What:** Use main page (zone count + global devices + thresholds) with href-linked per-zone sub-pages for device configuration. NOT single-page dynamic sections.

**Why:** Scales cleanly past 3 zones; cleaner SharpTools UX; matches the Combined Presence / HomeKit Integration Mode app patterns. Single-page would require ~20 inputs in one scroll for Mads's 3-zone case.

**Implementation hint for Tank:**
- mainPage: `input "zoneCount", "number", submitOnChange: true` + global outdoor/AQI/rain device pickers + threshold inputs + repeated `href "zonePage", params: [zoneIdx: i], title: settings."zone\Name" ?: "Zone \"`
- zonePage(params): dynamic `page(name:"zonePage")` reads `params.zoneIdx`, renders that zone's inputs (name, thermostat, contacts, temp sensors, speakers)
- Settings stored as `zone1Name`, `zone1Thermostat`, `zone1Contacts` etc. — installed/updated handler iterates 1..zoneCount

---

*End of decisions*
