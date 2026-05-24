# Trinity — Climate Advisor Performance Audit

**Date:** 2026-05-23  
**Requested by:** Mads  
**Scope:** `apps/climate-advisor/climate-advisor-app.groovy` v0.3.4 and `drivers/climate-advisor/climate-advisor-device.groovy` v0.3.4

## Verdict

**Overall load: VERY LIGHT to LIGHT.**

Climate Advisor is event-driven. It does not poll sensors on a repeating schedule. Sensor changes are coalesced through a 1-second overwrite debounce before `evaluateAll()`, so event bursts collapse into one evaluation.

## Finding 1 — No polling loop

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

## Finding 2 — In-pass device reads are mostly light, but thermostat reads are repeated

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

## Finding 3 — Child device chatter is deduped

**Severity:** CLEAN

The app pushes many child attributes per evaluation, but all app-originated child events go through `sendEventIfChanged()`, which compares `currentValue()` and skips unchanged values. `latestMessage` is also deduped; unchanged idle/advice text is not re-emitted.

The child driver itself sends raw events during install/manual commands only (`clearMessages`, `acknowledge`, `deviceNotification`). That is user/action-driven, not background chatter.

## Finding 4 — Idle status v0.3.4 cost

**Severity:** LOW

`buildIdleStatus()` runs only when no advice messages are active. It reuses the already-read outdoor temp, rain boolean, and AQI value. It does re-read the weather condition attribute to display the condition word/emoji; `checkRain()` already read that same attribute earlier in the pass.

This is minor. If touching the snapshot work above, also pass `conditionRaw` from `checkRain()`/a weather snapshot into `buildIdleStatus()`.

## Finding 5 — Hidden costs

**Severity:** CLEAN

No `pauseExecution`, blocking HTTP, async HTTP, external libraries, or sandbox-hostile patterns found. List scans are bounded by configured zones/devices and a fixed 10 dashboard slots. Trend buffers are trimmed on event append and computed lazily during evaluation.

## Final call

No urgent fix required. The app is safe to run alongside dozens of other apps. The only optimization worth considering is per-evaluation caching/snapshotting of thermostat/weather reads; severity LOW, polish only.
