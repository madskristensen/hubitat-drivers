## Archive: Tank-17, Tank-16, Tank-15 Detailed Spawns (2026-05-18)

### Tank-17: v0.4.5 mqtt.connect() signature fix

**Issue:** Mads reported live MQTT connection failure: `No signature of method: hubitat.helper.interfaces.Mqtt.connect() is applicable for argument types: (java.lang.String, GStringImpl, null, null, java.util.LinkedHashMap)`

**Root cause:** v0.4.0 called 5-arg form `interfaces.mqtt.connect(brokerUrl, clientId, username, password, optionsMap)` with Map for LWT; Hubitat does not support this.

**Fix:** Switched to 8-arg documented form: `interfaces.mqtt.connect(brokerUrl, clientId, username, password, lwtTopic, lwtQos, lwtRetained, lwtPayload)` with explicit LWT parameters. Behavior fully preserved.

**Scope:** fully-kiosk v0.4.5 (all 3 files: driver, manifest, README)

### Tank-16: v0.4.4 defensive leading-slash handling

**Issue:** FKB MQTT topics may have leading slash (FKB docs show `/fully/event/...` and `/fully/deviceInfo/...`); MQTT treats `/fully/` and `fully/` as separate address spaces.

**Fix:** Added second `interfaces.mqtt.subscribe("/${prefix}/#", 0)` subscribe call + leading-slash strip in parser: `if (topic.startsWith("/")) { topic = topic.substring(1) }`. Result: robust to either FKB convention.

**Scope:** fully-kiosk v0.4.4 (all 3 files)

### Tank-15: v0.4.3 MQTT event-name mismatches

**Issue:** Cypher's reality check found 4 event-name mismatches in `handleFkEvent()`:
- driver listening for `"motionDetected"` → FKB publishes `"onMotion"`
- driver listening for `"unpluggedAC"` → FKB publishes `"unplugged"`
- driver listening for `"batteryLevel"` → FKB publishes `"onBatteryLevelChanged"`
- driver listening for `"foregroundApp"` → FKB does not publish as event (attribute from deviceInfo only)

**Fix:** Renamed case statements + removed dead `foregroundApp` case. Cypher confirmed `"pluggedAC"` is correct; no rename needed.

**Scope:** fully-kiosk v0.4.3 (all 3 files)



---

## Learnings

- MQTT attempt failed across 4 iterations — Hubitat MQTT client API signatures are fragile, broker compatibility is variable. The polling architecture is the reliable path.
- Removing MQTT was the right call once it became clear the broker wasn't accepting the handshake.
- `parseJson()` must never run on blank preference text or empty async HTTP bodies in Hubitat drivers; guard `?.trim()` first, wrap JSON parsing in `try/catch`, and early-return with `log.warn` instead of crashing `refresh()`.
- Hubitat custom numeric attributes accept the UTF-8 unit string `µg/m³` cleanly; PurpleAir raw PM2.5 can emit as `pm2_5` with that exact unit.
- PurpleAir v0.3.0 can add `TemperatureMeasurement` + `RelativeHumidityMeasurement` without conflicting with the existing custom `aqi`/`category` attributes.

- Gemstone v0.4.17 stale-token fix: add `ensureSession()` before cached-state dedup so commands only early-return when the Cognito session is healthy; edits landed in `drivers/gemstone-lights/gemstone-lights.groovy` lines 206–355 (switch/level/color handlers), 802–826 (`ensureSession()` + queue gate), and 1044–1063 (shared effect activation dedup path).
- Dedup/auth lesson: in Hubitat cloud drivers, a cache-hit return must never sit above the auth/queue path, or an expired token turns user commands into silent no-ops until a non-deduped path like `refresh()` repairs the session.

## Session Arc 2026-05-19: PST02 Performance Audit (Tank #23)

**Task:** Performance audit of `drivers/philio-pst02/philio-pst02.groovy` v1.1.0 → v1.2.0.

**Changes shipped:**
- **Implicit globals eliminated:** `resync`, `refresh`, and `value` in `deviceSync()` were bare assignments (no `def`/type), creating script-level Binding entries that persist across method calls. Declared `boolean resync`, `boolean refresh`, `Integer value`, `List cmds`.
- **Implicit globals in SensorMultilevelReport:** `value` in switch cases 5 and 3 was also undeclared — declared `BigDecimal sensorValue`, `int precision`, `String unit` before the switch.
- **Typed return types:** `def setBit` → `Integer`, `def isPst02BVariant` → `boolean`, `def resolveConfigParam*` → `Integer`. Groovy can skip dynamic-dispatch overhead when return type is declared.
- **`isPst02BVariant()` called twice per resolve fn:** Cached in local `boolean isB` at top of each resolve function — avoids redundant settings/`getDataValue` reads.
- **Redundant Z-Wave roundtrip removed:** The resync block sent `configurationGet(12)` unconditionally, but the diff-check block above it already sends `configurationGet(12)` when resync=true (because `resync ||` is true). Removed the duplicate — saves one battery-expensive Z-Wave roundtrip per full resync.
- **Map allocations reduced:** `BatteryReport` and `clearTamper` use inline `sendEvent(name:..., value:..., ...)` — eliminates one `HashMap` allocation per event.
- **`Map map = [:]` typing:** All event handlers type the map variable explicitly.
- **Redundant `.toString()` in log.trace:** All `${cmd.toString()}` → `${cmd}` (Groovy calls `toString()` implicitly in GString).
- **`WakeUpIntervalReport` local:** Compute `int minutes` locally instead of `cmd.seconds / 60` inline in `state` write — avoids reading state back for the log line and fixes the "wakup" typo.

## Learnings

- In Hubitat Groovy drivers, any variable assigned without `def` or a type in a `def` method body becomes a script-level `Binding` entry, not a stack-local. This is not sandbox-blocked but wastes a map-lookup on every read/write and can hold stale values across calls.
- `isPst02BVariant()` is called from 3 resolve functions each of which may run on every wakeup. Cache it in a local `boolean isB` at the top of each function — one read instead of two.
- On battery-powered Z-Wave devices, every `configurationGet` costs battery life; never duplicate one in a "resync info reads" block if the diff-check block above it already emits the same get unconditionally when `resync == true`.
- Groovy typed return types (`Integer`, `boolean`, `List`) on frequently-called helper functions reduce dynamic dispatch overhead in the Hubitat sandbox JVM.


**Tank #20:** Fully Kiosk v0.5.0 shipped with MQTT-to-REST wording clarification. Changelog flattened to single-line format (release.yml regex requirement). Commit: 61644e4.

**Tank #21:** PurpleAir v0.3.0 shipped with parseJson guard for blank search_coords, async response guards, and new attributes (pm2_5, temperature, humidity, confidence). Commit: fd212cc.

**Tank #22:** PurpleAir v0.4.0 shipped with all 5 Trinity production bugs fixed (String-math retry, disabled-poll guard, distance2degrees pole clamp, zero-distance protection, divide-by-zero guards), plus polish: AirQuality capability, airQualityIndex attribute, runEvery* scheduling, hub temp-scale conversion, canonical async error handling, refresh-on-save, cleaner sites output, stable AQI units, 60-second lastActivity throttling. Changelog flattened. Commit: 2d62b05.

**Key Learning:** Changelog single-line format is now enforced across all drivers (required by release.yml line 136 regex).

**Deliverables:** Orchestration logs created (.squad/orchestration-log/2026-05-19-043500Z-tank-*.md)

---

## 2026-05-20 — Away Lights v0.2.0

**Task:** Add randomized timing (jitter) and sunset-relative window start to `apps/away-lights/away-lights.groovy`.

**Shipped:** `away-lights.groovy`, `apps/away-lights/packageManifest.json`, `apps/away-lights/README.md`, root `packageManifest.json` — all bumped to v0.2.0.

**Feature 1 — Randomized jitter:** `onTimeHandler()` and `offTimeHandler()` each compute a random 0–N second delay (`Math.random() * randomizeMinutes * 60`) and delegate to `doLightsOn()` / `doLightsOff()` via `runIn()`. Both delegates re-check `location.mode == awayMode` as a guard in case mode changes during the delay. `checkAndTurnOn()` left immediate (has its own debounce).

**Feature 2 — Sunset-relative window start:** `initialize()` conditionally schedules `"0 0 12 * * ?"` → `scheduleSunsetOn()` instead of the fixed `schedule(onTime, ...)`. `scheduleSunsetOn()` calls `getSunriseAndSunset(sunsetOffset: N)` and calls `schedule(targetDate, "onTimeHandler")` with the resulting `Date`. `isInWindow()` updated to use the same sunset lookup when `useSunset` is true.

**Key decisions:**
- Noon re-schedule (`"0 0 12 * * ?"`) ensures the sunset target drifts correctly day to day — no need for a midnight handler.
- `offTime` stays fixed; sunset applies only to the window START, not the end.
- `checkAndTurnOn()` uses `isInWindow()` which already accounts for sunset, so no special case needed there.

## Learnings

- `getSunriseAndSunset(sunsetOffset: N)` accepts both positive and negative `N` (minutes) and returns a `Date` — usable directly in `schedule(Date, "handler")`. This is cleaner than computing epoch arithmetic manually.
- `schedule(Date, "handler")` in Hubitat is a one-shot schedule to a specific moment; calling it again the next day (via the noon cron) replaces the previous one-shot correctly.
- For timed-delay patterns with mode guards, always re-check mode in the delegated handler (`doLightsOn`, `doLightsOff`) — the hub mode can change during the jitter delay window.
