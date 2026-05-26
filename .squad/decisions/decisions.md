# Decisions Log

**Last updated:** 2026-05-23

---

## Decision: isComponent behavior clarification — v0.3.2 wording corrected

**Date:** 2026-05-23  
**Author:** Tank  
**Status:** Resolved — no code change, wording fix only

### What happened

v0.3.2 set `isComponent: true` on the Climate Advisor child device and shipped with changelog text claiming it produced "cleaner Devices list" and that the device "nests under app" (implying hidden from Devices). Mads then reported the device appeared in the Devices list after recreating it, and questioned whether the change worked as described.

### Research findings (primary sources)

**Official Hubitat App Object documentation (`docs2.hubitat.com/en/developer/app/app-object`) — fetched live 2026-05-23:**

> `boolean isComponent` — true or false, **if true, device will still show up in device list but will not be able to be deleted or edited in the UI.** If false, device can be modified/deleted on the UI.

This wording has been identical since the feature was introduced in 2018 (confirmed from original Hubitat staff post by `chuck.schwer` in `community.hubitat.com/t/composite-devices-parent-child-devices/1925`).

**Official Parent-Child Drivers documentation (`docs2.hubitat.com/en/developer/driver/parent-child-drivers`) — fetched live:**

> Child devices of an app "will have a 'Parent app' listed in the 'Device Details' table on the 'Device Info' tab of their device detail page."

App-parented children appear in the Devices list (not indented, not hidden). Only device-parented children get visual indentation.

**Groups and Scenes confirmation:** G&S group/scene devices also appear in the main Devices list and are fully accessible in dashboards, Rule Machine, SharpTools, etc. The "nesting" visible in the Apps section is the app's own management UI — not a Devices-list-level feature. G&S does not hide its children from the Devices list; no third-party app can.

### What isComponent: true actually provides

| Behavior | isComponent: false | isComponent: true |
|---|---|---|
| Appears in main Devices list | yes | yes (unchanged) |
| Appears in App Details (Apps section) | yes | yes (unchanged) |
| User can delete via Devices UI | yes | blocked ✅ |
| User can change driver via Devices UI | yes | blocked ✅ |
| "Parent app" shown in Device Info tab | yes | yes (unchanged) |
| Auto-removed on app uninstall | yes | yes (unchanged) |

**The v0.3.2 change is real and useful** — it prevents accidental deletion/driver-change. The changelog claim "cleaner Devices list" was simply wrong.

### Decision

- `isComponent: true` kept (prevents accidental deletion — genuinely useful)
- Mads accepted Option 1: accept platform behavior, no architecture change
- There is no Hubitat SDK mechanism available to third-party apps to hide an app-created child device from the main Devices list — this is a hard platform limitation
- Changelog entries in app and driver headers corrected to accurate wording
- README Architecture section corrected to accurate wording
- No version bump; wording fix folded into a separate clarification commit

---

## Decision: Climate Advisor v0.3.3 — evaluateFreeCooling

**Date:** 2026-05-23  
**Author:** Tank  
**Version:** 0.3.3

### The Gap (diagnosed in behavioral trace)

Mads reported a real-world scenario that produced zero notifications despite an actionable situation:

- Outdoor: 65°F, Indoor: 75°F (= cooling setpoint), windows CLOSED, trend unknown

**Why nothing fired:**

| Evaluator | Exit reason |
|---|---|
| `evaluateCoolingPreAlert` | `windowGatePasses = false` (contacts configured, all closed) — exits line 1 |
| `evaluateHeatingPreAlert` | Same gate — exits line 1 |
| `evaluateCoolBreach` | `outdoorTemp > indoorTemp` required (65 > 75 = false) — wrong direction |
| `evaluateComfortOpen` | `outdoorTemp >= lowerBound` required (65 >= 70 = false) — outdoor below comfort band |

The root cause: `evaluateCoolBreach` fires when outdoor is **hotter** than indoor (close-windows-now). `evaluateComfortOpen` fires when outdoor is in the mid-comfort band. Neither covers outdoor **cooler** than indoor when indoor is at the setpoint — which is the ideal free-cooling condition.

### The Fix

New evaluator `evaluateFreeCooling` added after `evaluateComfortOpen` in `climate-advisor-app.groovy`. Fires INFO (severity 1) when ALL:

1. `outdoorTemp < indoorTemp` — outdoor is cooler (right direction for ventilation)
2. `indoorTemp >= (coolSP - coolingPreAlertOffset)` — indoor at/near setpoint (reuses existing offset, default 3°F); AC would otherwise run
3. Thermostat in `cool` or `auto` mode
4. Contacts configured AND all closed (something to open; not already open)
5. Not raining
6. AQI below warn threshold
7. Outdoor trend NOT `rising` (don't suggest opening if it'll overshoot)

Message format: `"Zone 1 outdoor 65°F is 10.0°F cooler than indoor 75°F — consider opening windows for free cooling (cool setpoint 75°F)"`

### Non-overlap proof

- **vs `evaluateCoolBreach`**: that requires `outdoorTemp > indoorTemp` — mutually exclusive with condition 1 above
- **vs `evaluateComfortOpen`**: that requires outdoor inside `[heatSP+2, coolSP-2]`; free cooling fires when outdoor is *below* that band
- **vs `evaluateCoolingPreAlert`**: that requires `windowGatePasses = true` (contacts open or absent) AND outdoor rising AND outdoor hotter — three conditions opposite to this scenario

### Behavioural change for Mads's scenario

Before v0.3.3: no notification. After v0.3.3: INFO message "Zone 1 outdoor 65°F is 10.0°F cooler than indoor 75°F — consider opening windows for free cooling (cool setpoint 75°F)". Severity = 1; `contact` attribute stays `closed` (INFO < 1 threshold for open); `severityText` = "info".

### Files changed

- `apps/climate-advisor/climate-advisor-app.groovy` — new `evaluateFreeCooling` method, wire-up in `evaluateZone`, version 0.3.3, changelog
- `drivers/climate-advisor/climate-advisor-device.groovy` — version 0.3.3, changelog
- `apps/climate-advisor/packageManifest.json` — version 0.3.3
- `packageManifest.json` (root) — Climate Advisor app + driver entries 0.3.3
- `apps/climate-advisor/README.md` — free cooling row added to alert table

---

## Decision: Climate Advisor v0.3.4 — contextual idle status for dashboard

**Date:** 2026-05-23  
**Author:** Tank  
**Version:** 0.3.4

### Problem

When no advisory messages were active, the `latestMessage` attribute on the child device showed:

```
All clear — no climate issues detected
```

And `houseStatus` showed `"House — all clear"`. These are informationally empty on a SharpTools kitchen tablet tile where the device is always visible — there's no value in staring at "all clear" when you could see actual ambient conditions.

### Solution: `buildIdleStatus()`

New private method in `climate-advisor-app.groovy`, called from `evaluateAll()` only when `allMessages` is empty (no active advisory candidates). Produces a single rich line for the `latestMessage` attribute.

**Format:** `{emoji} {condition} · {temp}°F · AQI {n} ({category}) · House comfortable`

**Examples:**
- `☀️ Sunny · 72°F · AQI 38 (good) · House comfortable`
- `🌧️ Rain · 58°F · AQI 42 · House comfortable`  
- `☁️ Cloudy · 65°F · AQI 51 (moderate) · House comfortable`
- `🌙 Clear · 48°F · AQI 30 · House comfortable`
- `House comfortable` (all sensors missing — absolute fallback)

### Design decisions

**Separator:** ` · ` (U+00B7 MIDDLE DOT with spaces). Clean on both SharpTools and Hubitat Dashboard without visual noise of pipes or dashes.

**Weather emoji mapping:** keyed on `contains()` matches of the weather attribute string (case-insensitive). Priority order: rain override (from `rainDetected`) → snow → storm → fog → partly cloudy → cloudy → clear/sunny → unknown (raw string + generic emoji). Rain is handled via the existing `rainDetected` boolean rather than re-reading the weather attribute, keeping it consistent with the evaluator logic.

**Day/night detection:** `isNighttime()` reads `location.sunrise` and `location.sunset` (Hubitat `Date` objects). "Clear/sunny" conditions emit 🌙 after sunset, ☀️ during day. Fails gracefully to `false` (daytime) if location data is unavailable.

**Temperature display:** Integer (`.setScale(0, BigDecimal.ROUND_HALF_UP).toInteger()`). Fractional degrees add noise on a dashboard tile.

**AQI categories (EPA standard):**
- 0–50: good
- 51–100: moderate  
- 101–150: sensitive groups
- 151–200: unhealthy
- 201–300: very unhealthy
- 301+: hazardous

**`houseStatus` attribute:** Updated idle case from `"House — all clear"` → `"House comfortable"` for consistency with the new idle line suffix.

**No new preference:** Feature ships on by default. Mads can request a toggle later if needed.

**Null safety:** Every segment is independently guarded. `buildIdleStatus(null, false, null)` returns `"House comfortable"` without exceptions — verified by reading the logic path.

### Files changed

- `apps/climate-advisor/climate-advisor-app.groovy` — `buildIdleStatus`, `isNighttime`, `aqiCategory` methods; `evaluateAll` idle branches updated; version 0.3.4
- `drivers/climate-advisor/climate-advisor-device.groovy` — version 0.3.4, changelog
- `apps/climate-advisor/packageManifest.json` — version 0.3.4
- `packageManifest.json` (root) — Climate Advisor app + driver entries 0.3.4
- `apps/climate-advisor/README.md` — "Idle dashboard line" section added

---

## Audit: Climate Advisor v0.3.4 — Trinity performance review

**Date:** 2026-05-23  
**Auditor:** Trinity  
**Scope:** `apps/climate-advisor/climate-advisor-app.groovy` v0.3.4 and `drivers/climate-advisor/climate-advisor-device.groovy` v0.3.4

### Verdict

**Overall load: VERY LIGHT to LIGHT.**

Climate Advisor is event-driven. It does not poll sensors on a repeating schedule. Sensor changes are coalesced through a 1-second overwrite debounce before `evaluateAll()`, so event bursts collapse into one evaluation.

### Finding 1 — No polling loop

**Severity:** CLEAN

The app subscribes to exact attributes only:

- outdoor temperature: `subscribe(outdoorTempDevice, "temperature", outdoorTempHandler)`
- weather condition attribute: `subscribe(weatherDevice, weatherAttribute, debounceHandler)`
- AQI: `subscribe(aqiDevice, "airQualityIndex", debounceHandler)`
- per-zone indoor temps: `subscribe(sensor, "temperature", indoorTempHandler)`
- per-zone thermostat mode/setpoints: `subscribe(t, "thermostatMode"|"coolingSetpoint"|"heatingSetpoint", debounceHandler)`
- per-zone contacts: `subscribe(c, "contact", debounceHandler)`

Scheduled jobs are lifecycle/debounce only:

- `runIn(1800, "logsOff")` when debug logging is enabled.
- `runIn(5, "evaluateAll")` after initialize, one-time seed evaluation.
- `runIn(1, "evaluateAll", [overwrite: true])` from event handlers and clear-all; coalesces bursts.

No `runEvery*`, cron `schedule`, HTTP calls, or platform polling loops found.

### Finding 2 — In-pass device reads are mostly light, but thermostat reads are repeated

**Severity:** LOW

Per evaluation pass:

- outdoor temperature: read once.
- rain/weather: read once in `checkRain()`.
- AQI: read once in `currentHouseAqi()` and passed down to every zone.
- each zone indoor temp sensor: read once via `averageTemps()`.
- each zone contact sensor: read once for zone open-count; read again only if rain is detected for the house rain alert.

The one real inefficiency: each thermostat can be read repeatedly by separate evaluators in the same pass. Worst case per thermostat is roughly:

- `thermostatMode`: up to 6 reads
- `coolingSetpoint`: up to 4 reads
- `heatingSetpoint`: up to 3 reads

This is not alarming on a C-8 with normal zone counts, especially behind the debounce, but it is real avoidable work.

**Recommendation:** If Mads wants polish, have Tank build a per-pass zone snapshot (`mode`, `coolSP`, `heatSP`, `indoorTemp`, `openContacts`) and pass it to evaluators. Do not change architecture.

### Finding 3 — Child device chatter is deduped

**Severity:** CLEAN

The app pushes many child attributes per evaluation, but all app-originated child events go through `sendEventIfChanged()`, which compares `currentValue()` and skips unchanged values. `latestMessage` is also deduped; unchanged idle/advice text is not re-emitted.

The child driver itself sends raw events during install/manual commands only (`clearMessages`, `acknowledge`, `deviceNotification`). That is user/action-driven, not background chatter.

### Finding 4 — Idle status v0.3.4 cost

**Severity:** LOW

`buildIdleStatus()` runs only when no advice messages are active. It reuses the already-read outdoor temp, rain boolean, and AQI value. It does re-read the weather condition attribute to display the condition word/emoji; `checkRain()` already read that same attribute earlier in the pass.

This is minor. If touching the snapshot work above, also pass `conditionRaw` from `checkRain()`/a weather snapshot into `buildIdleStatus()`.

### Finding 5 — Hidden costs

**Severity:** CLEAN

No `pauseExecution`, blocking HTTP, async HTTP, external libraries, or sandbox-hostile patterns found. List scans are bounded by configured zones/devices and a fixed 10 dashboard slots. Trend buffers are trimmed on event append and computed lazily during evaluation.

### Final call

No urgent fix required. The app is safe to run alongside dozens of other apps. The only optimization worth considering is per-evaluation caching/snapshotting of thermostat/weather reads; severity LOW, polish only.
