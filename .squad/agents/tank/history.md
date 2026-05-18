# Tank — Driver Developer

**⚠️ SUMMARIZED 2026-05-18T13:19:11Z — Detailed history moved to `history-archive-2026-05-18.md` (file was 29,359 bytes).**

---

## Latest Work (2026-05-18)

### 2026-05-18T17:45:00Z — Cloud driver metadata hygiene shipped (all 8 perf/quality todos closed)
- **Gemstone v0.4.16**: added `capability "Polling"` so Hubitat apps discover `poll()`; bumped driver + `drivers/gemstone-lights/packageManifest.json`.
- **SunStat v0.1.11**: parent now declares `capability "Polling"` + `capability "Actuator"`; synced child + `drivers/sunstat-thermostat/packageManifest.json` to v0.1.11 for release lockstep.
- **Status:** SHIPPED — all 8 perf/quality todos from the 2026-05-18 board are now shipped.

### 2026-05-18T17:11:04Z — SunStat v0.1.10 shipped (SC-4 closes audit)
- **SunStat v0.1.10**: cached `Schedule.Floor.W` into `state.floorWarmth` alongside `state.floorAway`, then added a skip-if-match guard in `setFloorMinTemp()` so redundant floor-min writes no longer issue a no-op PATCH.
- Bumped parent + child + `drivers/sunstat-thermostat/packageManifest.json` to v0.1.10 for release sync; parent change is version-sync only.
- **Status:** SHIPPED — SunStat redundant-write audit board is now empty (all repo-backed audit items closed).

### 2026-05-18T15:30:00Z — Audit shipping spree: 5 driver releases closed 16/17 findings
- **Touchstone v0.1.25** (b4122ee): T-2 + T-3 (switch idempotency)
- **Touchstone v0.1.26** (ffe2e9d): T-4 through T-10 (7× wire-only yellows batch)
- **Gemstone v0.4.12** (91e0d1a): G-1 (effect animation idempotency)
- **Gemstone v0.4.13** (6ee553a): G-2 through G-6 (5× cloud quota yellows batch)
- **SunStat v0.1.8** (f9060fb): SP-1, SC-1–SC-3 (4× API quota yellows batch; SC-4 deferred for state.floorMinTemp caching refactor)

All findings applied skip-if-match idempotency pattern (current attribute check before DP/API write). Pattern prevents audible relay clicks (T-2, G-1), reduces API quota (G-2–G-6, SP-1, SC-1–SC-3), and maintains wire-traffic hygiene (T-3–T-10). By-design exclusions: SC-5/SC-6/SC-7 state-assertion and recovery paths untouched.

**Status:** SHIPPED (16/17 findings closed; SC-4 deferred); awaiting Mads real-device validation.

---

### Touchstone v0.1.22 — Log Hygiene (trace/debug split)
- **Shipped:** 2026-05-18 (Commit f53312c)
- **Status:** Delivered
- **Changes:**
  - Added `traceEnable` preference (bool, default off, 30-min auto-disable)
  - Created `traceLog()` helper for protocol firehose (heartbeat ACK, refresh queue/send, raw dumps, unchanged DP echoes)
  - Demoted heartbeat/refresh/echo noise from `debugLog` to `traceLog`
  - Matches kkossev Zigbee driver pattern (community standard)
  - Protocol behavior unchanged; purely additive logging layer
- **Skill:** tuya-local-groovy/SKILL.md updated with "Log Hygiene" section

---

## Summary of Shipped Drivers (Prior Sessions)

**Touchstone Fireplace (Latest: v0.1.22)**
- v0.1.1–0.1.5: Core scaffold, safety hardening, reflection fixes
- v0.1.18: Persistent socket + heartbeat + push subscriptions
- v0.1.19: Child lock (DP 108)
- v0.1.20: Active TCP discovery + DHCP recovery
- v0.1.22: Log hygiene (trace/debug split)

**Gemstone Smart Heater (Latest: v0.4.10)**
- Multi-controller zone support with named controller binding
- lastActivity attribute for passive monitoring

**SunStat Solar Control (Latest: v0.1.7)**
- lastActivity attribute integration

**HPM Bundle (v1.0.0)**
- Root packageManifest.json with all 4 drivers
- release.yml bundle tag generation

---

## Key Learnings Archive

See `history-archive-2026-05-18.md` for detailed learnings on:
- Persistent socket lifecycle and heartbeat patterns
- Tuya protocol nuances (empty heartbeat payload, frame handling)
- Multi-controller binding with blank-preference idiom
- Active TCP discovery state machines
- HPM bundle assembly and versioning
- DP 108 child lock boolean wiring
- And more...

## Learnings

### Skip-if-match pattern for power-on defaults (v0.1.23)

When a driver applies user-configured defaults at a lifecycle event (power-on, scene activation, etc.), first check `device.currentValue(attributeName)` against the configured default label before writing the DP:

```groovy
String current = device.currentValue("flameColor")
if (current != null && current == configuredDefault) {
    traceLog "applyOnDefaults: skipping defaultFlameColor — already '${configuredDefault}'"
} else {
    debugLog "applyOnDefaults: applying defaultFlameColor = '${configuredDefault}' (was '${current}')"
    // proceed with DP write
}
```

Key rules:
- **null current value = proceed with write.** The device state may not be known yet immediately after power-on. Skipping when state is unknown would silently fail to apply the user's preference.
- **Each default is independent.** Evaluate each attribute separately — don't gate all four on a single all-or-nothing check.
- **Log hygiene:** skipped lines go to `traceLog`, applied lines go to `debugLog`. Consistent with v0.1.22 trace/debug taxonomy.
- **DPs not mapped for the profile** (the existing `log.warn` branches) are unaffected — skip-if-match only applies inside the "DP is mapped" branch.

**Applicable to similar drivers:**
- **Gemstone Smart Heater** — if it applies configured zone defaults at zone-activate time, apply the same pattern per zone attribute.
- **SunStat Solar Control** — if defaults are applied at schedule trigger, apply the same pattern for setpoint/mode attributes.
- Generally: any driver with a `defaultFoo` preference that writes a DP at a lifecycle event should use this pattern to avoid spurious wire traffic and device flicker.


---

## 2026-05-17T19:29:40Z — Touchstone v0.1.5 paragraph() fix (App-only UI audit)

**Requested by:** Mads

### Completed

- Removed `paragraph` header from `preferences {}` block
- Moved power-on defaults explanation into per-field `description:` text
- Audited for app-only constructs (`section`, `href`, `app`, `mode`, `pageDefault`) — clean
- Bumped driver version to v0.1.5
- Consolidated Hubitat sandbox families into `.squad/skills/tuya-local-groovy/SKILL.md`

### Key Learning

Hubitat driver preferences are not the same as app preferences. Drivers should use only `input` fields; app UI helpers like `paragraph()`, `section()`, `href()`, `app()`, `mode()`, and `pageDefault()` will fail at install time in drivers and should be replaced with `description:` text on each field.

---

## 2026-05-17T19:29:40Z — Touchstone v0.1.4 shipped (Cross-Agent Batch Awareness)

**Collaborators:** Tank (2 runs), Link, Switch (test surface awareness)

### v0.1.3 + v0.1.4 are bundled in a single commit

v0.1.3 shipped optional power-on defaults (flame color, log color, flame brightness, temp setpoint, heat level). Link updated docs. Then immediately hardened v0.1.4: removed heater auto-apply per Mads's safety directive, fixed Hubitat sandbox reflection bugs. v0.1.3 was never released; users only see v0.1.4.

### Cross-Team Coverage

1. **Tank v0.1.3:** Added power-on defaults (runInMillis 1500ms delay for firmware settle window)
2. **Tank v0.1.4:** Removed defaultHeatLevel (fire/burn safety); removed 2 executable reflection calls (parse() exception logging, dpValueType() fallback)
3. **Link v0.1.4:** Updated README with Power-on Defaults + Safety sections; bumped packageManifest to v0.1.4; changelog omits v0.1.3
4. **Switch (test surface):** Aware that defaults apply ~1.5s after on(); heater never auto-toggles; v0.1.4 should install without sandbox reflection errors

### Key Decisions Captured in decisions.md

- User directive: heater must never auto-start (safety)
- Hubitat bug: sandbox rejects e.getClass() at line 449
- Documentation pattern: hardware safety > convenience; be explicit about intentional omissions

---

See history-archive.md for detailed earlier sessions (Gemstone, SunStat, Bosch feasibility).

## Learnings

- 2026-05-17T12:22:15-07:00 — Hubitat driver preferences are not the same as app preferences: drivers should use only `input` fields, and app-only UI helpers like `paragraph`, `section`, `href`, `app`, `mode`, and `pageDefault` will fail in drivers. Put explanatory copy into each input's `description:` instead.
- 2026-05-17T13:21:30-07:00 — The repo release workflow parses driver `Changelog:` entries with the regex in `.github/workflows/release.yml` line ~106, so each changelog line must use a plain `YYYY-MM-DD` date; ISO 8601 timestamps with time/offset will break release-note generation.
- 2026-05-17T15:41:32-07:00 — Cross-driver audit (Gemstone, SunStat, Touchstone) surfaced these anti-patterns to avoid in future drivers:
  1. **Synchronous HTTP on hot paths** — SunStat parent uses `httpGet/Post/Patch` (blocking) throughout polling and token refresh. With N children this stalls the hub thread for N×timeout. Always prefer `asynchttpGet/Post` for polling drivers; only use synchronous HTTP when the response is needed inline and there is no alternative (e.g. token bootstrap).
  2. **Nested blocking HTTP** — SunStat parent calls `refreshTokensSync()` (synchronous `httpPost`) inside an `httpGet` callback closure (line 487). This double-nests hub thread blocking. Token refresh triggered by 401 should be async or at minimum handled outside the callback.
  3. **`state.rxBuffer` persisted on every `parse()` call** — Touchstone writes the hex receive buffer to `state` on every incoming TCP chunk (line 479). Hubitat state writes are relatively expensive I/O; only persist the buffer when a partial frame remains after processing. Clear on next write rather than on every call.
  4. **Dead state writes** — Gemstone stores `state.idToken` (line 1121) on every Cognito auth but never reads it for any API call. `state.lastDps` in Touchstone (line 1109) is written but never read. Audit `state.*` for write-only fields; remove or make them explicitly "diagnostic only".
  5. **O(n) reverse-index scans** — `effectNameForPatternId` and `effectIndexForPatternId` in Gemstone do `.find {}` linear scans over the catalog on every effect activation. Build reverse lookup maps (patternId→name, patternId→index) at catalog finalization time to make lookups O(1).
  6. **`cloneMap` JSON round-trip overhead** — Gemstone's `cloneMap()` does `JsonSlurper().parseText(JsonOutput.toJson(source))` for every map copy (~14 call sites, hot paths). For shallow maps, `new LinkedHashMap(source)` is much faster. Reserve JSON round-trip deep-copy only for maps with nested mutable structures.
  7. **Boxed Integer in byte-copy inner loops** — Touchstone uses `for (Integer i = ...)` in `concatBytes`, `sliceBytes`, `startsWithBytes`, `protocol33HeaderBytes`. Use `int` (primitive) to avoid autoboxing. `System.arraycopy` (java.lang, sandbox-safe) is even better for bulk copies.
  8. **Guard block copy-paste** — Gemstone duplicates the same credential+catalog guard verbatim in 5 command handlers (`setEffect×2`, `setNextEffect`, `setPreviousEffect`, `refreshEffectCatalog`). Extract to a private helper to reduce maintenance surface.
  9. **`infoLog` double-negative guard** — Touchstone checks `settings.txtEnable != false` (line 1643). Prefer `settings.txtEnable == true` for clarity; a missing/null setting reads as enabled with the double-negative.
  10. **Missing `capability "Actuator"` on command-accepting parent** — SunStat parent accepts commands (setHome, setAway, setAwayMode, setRefreshToken, discoverDevices) but doesn't declare `capability "Actuator"`. Convention: any driver that accepts commands should declare Actuator.
  11. **`USER_AGENT` literal not linked to `DRIVER_VERSION`** — All three drivers hard-code the version in both `DRIVER_VERSION` and `USER_AGENT`. The sandbox prevents cross-@Field refs but a comment "keep in sync with DRIVER_VERSION" should appear on both lines (not just USER_AGENT).

- 2026-05-17T15:50:06-07:00 — Init-time stale-flag-reset is a common pattern for stateful async drivers: any driver that guards operations behind `state.*InFlight` boolean flags must reset those flags to `false` at the top of `initialize()`, because a hub reboot or crash mid-operation leaves them `true` and causes all subsequent operations to silently no-op. If a third driver adopts this pattern, extract a shared `clearInFlightFlags()` private helper rather than duplicating the reset block.

- 2026-05-17T15:50:06-07:00 — SunStat async migration pattern (v0.1.5): keep token refresh synchronous (`refreshTokensSync`) so the caller always has a valid token before fan-out; dispatch per-device polls and location-state fetches via `asynchttpGet` with a data map carrying `[childDni, deviceId, retry401: true]`; on 401 in a callback, call `throttled401Refresh()` (rate-limited to once per 60s) then re-issue as a fresh `asynchttpGet` with `retry401: false` — never nest a sync HTTP call inside an async callback. For PATCH, use `asynchttpPatch` with the same 401 single-retry pattern.

- 2026-05-17T15:50:06-07:00 — Pseudo-boost pattern for cloud thermostats (SunStat v0.1.6): when no native boost API exists, implement boost as a driver-managed state machine: (1) save preBoostSetpoint; (2) PATCH the real setpoint + optionally suppress schedule; (3) set `state.boostActive = true`, `state.boostUntil = now() + window`; (4) arm `runIn(seconds, "boostExpired")` — always `unschedule` first to prevent duplicates; (5) on `boostExpired()`/`cancelBoost()`, restore setpoint + schedule, clear state, unschedule. Hub-restart recovery has two paths: `initialize()` re-arms the timer if `boostUntil` is in the future, else immediately calls `boostExpired()`; `parseDeviceState` (poll callback) checks the same condition so a boost that overran while the hub was offline is caught on the next poll. This pattern is reusable for any "timed override" feature on cloud thermostats (vacation presets, setback overrides, etc.).

- 2026-05-17T20:04:44-07:00 — HealthCheck capability pattern (Touchstone v0.1.21): `ping()` calls `sendHeartbeat()` immediately (reusing existing frame builder), sets `state.pingPending = true` and `state.pingRequestedAt = now()`, then arms `runIn(5, "pingTimeout")`. In `parse()`, on every successful inbound frame, stamp `lastActivity`, clear `pingPending`, and emit `healthStatus = online`. `pingTimeout()` checks if `state.lastSocketEventTs < state.pingRequestedAt`; if so, emits `healthStatus = offline`. For socketState, `scheduleReconnect()` emits `healthStatus = offline` only after ≥2 consecutive reconnect attempts (checked via `state.reconnectAttempts >= 2`) to suppress flicker on the first miss. `initialize()` and `updated()` both reset `state.pingPending = false` to clear orphan state from prior sessions.

- 2026-05-17T20:04:44-07:00 — lastActivity-only pattern for cost-sensitive cloud drivers (Gemstone v0.4.11, SunStat v0.1.7): declare `attribute "lastActivity", "string"` in metadata; add a `touchActivity()` private helper that emits `sendEvent(name: "lastActivity", value: new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX"), ...)`. Call it exclusively on 2xx success paths — never on error, 401, or timeout branches. For parent/child drivers (SunStat), the parent's `touchActivity()` also cascades to all children via `child.setLastActivity(ts)`; the child exposes a `void setLastActivity(String timestamp)` method that controls its own event emission (cleaner than parent calling `child.sendEvent()` directly).

- 2026-05-18 — Log hygiene pattern for Tuya protocol drivers (Touchstone v0.1.22): the two-tier `debugLog` / `traceLog` split keeps `logEnable=true` readable in production. **Trace taxonomy (off by default):** heartbeat send + ACK, periodic refresh queue/send/receive/decoded-payload, read-only DP echoes (e.g. DP 105), per-DP echoes when decoded value equals current device attribute. **Debug taxonomy (on when logEnable is enabled):** user-initiated writes (on/off, setFlameColor, etc.), per-DP echoes where the value *changed*, socket lifecycle events, protocol mode switches. **"Log only on change" rule:** before `debugLog` in `applyDps`, compare decoded label against `device.currentValue(attr)` — route to `traceLog` if unchanged. **Heartbeat split pattern:** check `cmd == TUYA_CMD_HEARTBEAT` before the generic received-frame log and route to `traceLog`; non-heartbeat cmds fall through to `debugLog`. **Refresh chatter suppression:** gate "Queued" and "Sent" log lines on `reason == "refresh"` → `traceLog`, else `debugLog`. The `traceEnable` preference mirrors `logEnable` with its own `traceOff()` auto-disable at 30 minutes. Protocol behavior (timing, DP map, command dispatch) must never be changed during a logging-only cleanup.

- 2026-05-17T20:04:44-07:00 — When to add full HealthCheck vs just lastActivity: local TCP drivers (e.g. Touchstone) with persistent sockets should get the full `capability "HealthCheck"` + `ping()` — the socket can silently die between heartbeats and `ping()` forces an immediate probe at zero cost. Cloud REST drivers (Gemstone, SunStat) should use `lastActivity`-only — adding `ping()` would burn API quota on every hub health-check cycle. The lightweight timestamp lets Rule Machine and dashboards detect stale cloud connectivity (e.g., "no activity in 10 min → alert") without per-ping API hits.

- 2026-05-17T20:04:44-07:00 — Hook points discovered for each driver's response-handling code: Touchstone — `parse()` after `consumeReceiveBuffer()` returns processed > 0 (covers heartbeat ack, push frames, command responses); Gemstone — `apiResponseCallback()` just before `updateAuthenticatedStatus()` at end (covers refresh, command, effectCatalogPage) + `handleDevicesResponse()` before its own `updateAuthenticatedStatus()` call (covers the discoverDevices early-return path); SunStat — `pollChildDeviceCallback()` at the status-200 success block before `child.parseDeviceState()`, `fetchLocationStateCallback()` when `loc` is found before `parseLocationState()`, and `refreshTokensSync()` inside the `httpPost` success block after `scheduleProactiveRefresh()`.

---

Participated in 4-way driver improvement scan with Trinity, Cypher, Switch. Findings consolidated by Squad. Orchestration log: .squad/orchestration-log/2026-05-17T15-41-32-tank.md.

---

## 2026-05-17T15:50:06Z — Touchstone v0.1.6 — flame speed, log brightness, drop power attribute

### DP 103 — Flame Speed label↔value mapping (community-derived; Switch to verify on hardware)

| Label | DP value sent |
|-------|--------------|
| `"Slow"` | `"1"` |
| `"Medium"` | `"2"` |
| `"Fast"` | `"3"` |

`FLAME_SPEED_OPTIONS = ["Slow", "Medium", "Fast"]`. These labels are inferred — the Sideline Elite YAML/device reported DP 103 as a 3-value enum but label names were not directly observed. Switch should run `setFlameSpeed("Slow")` / `"Medium"` / `"Fast"` and watch for visible flame animation differences.

### DP 105 — Log Brightness

`LOG_BRIGHTNESS_OPTIONS = ["1".."12"]` (raw numeric strings, 12 levels). Mirrors the `logColor` raw-string pattern. Sent as-is to the device; the device interprets them as integer enum steps. No label translation needed (user sees 1–12 which is self-describing).

### `power` attribute removal

The `power` attribute (`attribute "power", "enum", ["on", "off"]`) was a duplicate of `switch`. It was emitted alongside every `switch` event, causing doubled events for the same state change. Removed in v0.1.6. `switch` is the canonical on/off attribute. The internal DP role key `"power"` (maps to DP 1) is unchanged — it's a role string in `SIDELINE_PROFILE_DPS`, not the removed attribute.


## Team updates

- 2026-05-17: Participated in top-3 driver improvements batch — sunstat v0.1.6, touchstone v0.1.6, gemstone v0.4.9.

## Learnings (continued)

- 2026-05-17T16:15:00-07:00 — When inserting a new function near an existing one, ALWAYS verify the surrounding `def` lines are intact in a post-edit view — a single misaligned old_str can swallow a signature line and produce orphan-body parse errors that only surface at hub load time.

- 2026-05-17T16:19:32-07:00 — defaultLogBrightness is the symmetric gap — v0.1.6 added setLogBrightness without a corresponding default preference. Mads asked only for flame speed in v0.1.8; flag this as a likely follow-up if he reports it.

- 2026-05-17T23:22:29-07:00 — Touchstone v0.1.6 power-on defaults now have full v0.1.6-command symmetry — every named command has a `default*` preference counterpart that auto-applies during the defaults window. The pattern is firmly established: input declaration + application block + DP-write.

## Learnings (continued v0.1.10)

- 2026-05-17 — Hubitat enum attribute display: when the OPTIONS list labels are themselves numeric strings ("1","2","3"...), the inbound parse path must NOT use the DP value as an array index — that produces an off-by-one (and blanks the UI on the max value). Use direct passthrough or a labeled lookup table. Additionally, the defensive bounds-check must be present before any `emitAttribute` call on enum DPs: emit only if the incoming DP value is in the known options list, else log.warn and bail. Without this guard, out-of-range device values (or driver bugs) silently set the attribute to a value that is not in the enum list, causing the Hubitat dropdown to blank.

## 2026-05-17 (session tank-4) — Touchstone v0.1.10 enum bounds-check hardening

**Requested by:** Mads (real-hardware bug: display blanking on out-of-range device echoes)

### What shipped

Added in OPTIONS bounds-checks + log.warn + early bail in pplyDps() for enum DPs (101, 102, 103, 104, 105) before writing to UI attributes.

**Why:** Device sometimes pushes out-of-range DP values during echo (DP write response). Without bounds-check, driver applies invalid value to enum attribute, causing Hubitat UI to blank (value not in declared OPTIONS list).

**Verification:** Mads reported "one higher" display anomaly (showed flame effect 4 when device had 3). Investigation: NOT driver off-by-one, likely device firmware noise. Fix prevents invalid values from reaching UI.

**Commit:** 3fe727c — bounds-checks for all enum DPs.

**v0.1.10 status:** Awaiting Cypher's empirical test result (DP 105/109 read-only confirmation) before final changelog.

- 2026-05-17 — When fixing display bugs in Hubitat drivers, examine BOTH the WRITE-side emit AND the INBOUND parse paths. v0.1.10 fixed the parse-side (added bounds checks for echoed-back DP values) but missed the actual write-side off-by-one that emitted OPTIONS[dpValue] after computing dpValue = idx + 1. The lesson: when a user reports 'always shows +1', the bug is almost certainly in the WRITE path where the same off-by-one math is reused for both wire output AND attribute emit.

- 2026-05-17 — Hubitat Commands-tab dropdown bug: when a command parameter declares `type: 'ENUM'` with numeric-string constraints (e.g., ['1','2','3','4','5']), the dropdown widget advances one position after the user presses Set — purely cosmetic platform UI quirk, value sent and attribute emitted are both correct. Workaround: declare such parameters as `type: 'NUMBER'` with `range: 'N..M'` instead — Hubitat renders an input field, no dropdown to advance. Label enums (non-numeric strings) keep ENUM since they're not affected as severely and changing them harms UX.

- 2026-05-17T17:39:11-07:00 — Touchstone color palettes (DP 101 flame, DP 104 log) stay NUMBER input until someone with hardware reports the actual visible color for each palette index. Inventing labels without verification creates worse UX than honest numeric input. setFlameBrightness named-ENUM is appropriate because Dimmest/Dim/Medium/Brighter/Brightest are hardware-independent labels — the mapping is intuitive regardless of device.

- 2026-05-17 — Touchstone flame color palette (DP 101) verified labels from Tuya app screenshot: 1=Orange (default), 2=Blue, 3=White, 4=Orange+Blue, 5=Orange+White, 6=Blue+White. Future verifications for other Tuya devices should also request app screenshots before inventing labels.


- Touchstone DP 104 = 'Charcoal Color' in the Tuya app (not 'Log Color' — the driver historically used the wrong term). 12 palette values verified: 1=Orange (default), 2=Red, 3=Blue, 4=Yellow, 5=Green, 6=Purple, 7=Cyan, 8=Magenta, 9=White, 10=Pink, 11=Rainbow (8-segment), 12=Spotlight (best-guess; mostly-white circle with orange wedge in the app). Rename completed in v0.1.17 — breaking change, no alias.

## Learnings (v0.1.18 — Persistent Socket)

- 2026-05-17 — **Persistent socket lifecycle pattern:** use `interfaces.rawSocket.connect()` on `initialize()` and keep it open forever. Schedule a 10 s heartbeat via `runIn(HEARTBEAT_INTERVAL_SECONDS, "sendHeartbeat")` inside the `sendHeartbeat` handler itself — this is the Hubitat-safe alternative to sub-minute cron (`schedule()` can be unreliable for sub-60 s intervals on some hub versions). On success, `sendHeartbeat` reschedules itself; on send failure, it calls `scheduleReconnect()` and returns without rescheduling.

- 2026-05-17 — **Tuya heartbeat (cmd 9) requires truly empty payload.** `buildTuyaFrame(TUYA_CMD_HEARTBEAT, "")` would AES-encrypt the empty string, producing 16 bytes of PKCS5 padding — that's NOT a valid Tuya heartbeat. Fix: add a special case in `encryptTuyaPayload()` that returns `new byte[0]` when `cmd == TUYA_CMD_HEARTBEAT`. The frame then has length=8 (crc+suffix only), matching the `b'\x00\x00\x55\xaa...\x00\x09\x00\x00\x00\x0c...'` reference bytes from TinyTuya.

- 2026-05-17 — **intentionalCloseAt timestamp pattern for socketStatus suppression:** rather than a boolean `intentionalClose` flag (which has race conditions between `closeSocket()` → `initialize()` clearing it → `socketStatus()` firing), use a timestamp: `state.intentionalCloseAt = now()` in `closeSocket()`, and in `socketStatus()` check `(now() - intentionalAt) < 3000L`. Stale timestamps from previous sessions are safe — after a hub reboot, `now()` will be days/hours later, so `now() - oldTs >> 3000` and the guard never fires.

- 2026-05-17 — **Push frame handling is free:** existing `parse()` → `consumeReceiveBuffer()` → `processFrame()` → `applyDps()` pipeline already handles any inbound Tuya STATUS frame (cmd 8) regardless of whether it was solicited. Push frames from the physical remote arrive on the same socket and get processed identically. No separate push handler needed — just don't close the socket after a response.

- 2026-05-17 — **pumpQueue without ensureSocketConnected:** in the persistent socket model, `pumpQueue()` should check `state.socketOpen != true` and return early rather than trying to connect on-demand. The reconnect path (`scheduleReconnect` → `reconnectSocket` → `openSocket`) will pump the queue after it succeeds. This avoids a race where pumpQueue opens a second connection while reconnect is already in progress.

- 2026-05-17 — **Do NOT close socket on responseTimeout in persistent model.** The old pattern (responseTimeout → closeSocket → scheduleRetry) was necessary when each poll opened a fresh connection. With a persistent socket, closing on timeout would tear down the connection unnecessarily. Instead: requeueInFlight + scheduleRetry only. If the socket is genuinely broken, `socketStatus()` will fire and trigger reconnect independently.

- 2026-05-17 — **Poll interval reduction with persistent socket:** 5 minutes is the right default for the safety-net refresh poll when push updates are live. The old 60 s default was compensating for missed physical-remote events that are now handled by push frames. Reducing the default avoids unnecessary load on both the Hubitat hub and the Tuya device.

- 2026-05-17 — **Multi-controller binding via blank-defaults preference (Gemstone v0.4.10):** The cleanest way to support "N physical devices, each bound to a different cloud entity" is a single `controllerName` text preference on the existing driver. Blank = first-found (100% backward compat). Non-blank = case-insensitive trim match against discovered device names. Graceful degradation: no-match logs available names and falls back to first device. Set `state.availableControllers` (sorted, comma-joined) after discovery so users can copy-paste exact spelling. Suppress the "multiple controllers" warning when `controllerName` is set — multiple devices is expected and the warn would be noise. This pattern is reusable for any cloud driver where the same API account can return multiple independently addressable devices.

- 2026-05-17 — **Name-match sanitization idiom:** always do `settings.controllerName?.trim()?.toLowerCase()` and `safeString(it?.name).trim().toLowerCase()` before comparing. Guards against leading/trailing spaces in user preferences AND in cloud-returned names. Gemstone controller names come from a mobile app where users may accidentally add spaces.

- 2026-05-17 — **Backward-compat-via-blank-preference idiom:** when adding a new binding preference to an existing driver, make the blank/null case reproduce the exact old behavior with zero code divergence. Use `?: ""` to normalize null/blank to empty string, then `if (wanted)` to branch. Users upgrading see no change unless they explicitly set the new preference.


## Learnings (v0.1.19 + v0.1.20 + HPM Bundle — 2026-05-17)

### DP 108 Child Lock

- 2026-05-17 — Boolean DP wiring pattern: DP 108 is a Tuya BOOL type. Write: sendDpWrite("108", normalized == "on", ...). Read in applyDps(): asBoolean() + map to on/off string. Key gotcha: do not name the method parameter "state" — shadows Hubitat's state map. Use lockState or similar.
- 2026-05-17 — Attribute type for boolean DPs: declare as attribute "childLock", "enum", ["on", "off"]. Constraint list should match command ENUM constraints exactly.
- 2026-05-17 — Optimistic attribute emit on write: emit childLock before device echo. Device echo via applyDps() DP 108 overwrites with confirmed state ~2 s later. Pattern: emit on write side, also handle in applyDps().

### HPM Bundle Assembly

- 2026-05-17 — Bundle UUID reuse required: id UUID in bundle packageManifest.json MUST match per-driver manifest UUID. HPM Match-Up uses id+name+namespace. Diverged UUIDs create duplicate installs.
- 2026-05-17 — release.yml gotcha for root manifest: find drivers hard-codes the drivers/ prefix; root manifest not found. basename(dirname(root manifest)) returns "." breaking tag generation. Fix: update find to scan root; add conditional for driver_dir == "." to set tag=bundle-vX.Y.Z and skip changelog extraction.
- 2026-05-17 — No per-driver version fields in bundle: use only top-level version. Mixing top-level + per-driver versioning causes HPM update-check issues.
- 2026-05-17 — Bundle version bump convention: when any per-driver bumps, also bump root packageManifest.json. Document in repo README Contributing section.

### Active TCP Discovery State Machine

- 2026-05-17 — Smart-range scan via pre-computed queue: build probe order in discover() as state.discoveryProbeQueue (List of ints). Smart phase: +-20 from known IP first. Full sweep: remaining 1-254. discoveryProbeNext() pops from front. 254 integers in state ~1 KB — fine for Hubitat state storage.
- 2026-05-17 — Socket state during discovery: stamp state.intentionalCloseAt = now() before each close in discoveryProbeNext(). Reuses v0.1.18 suppression mechanism to prevent each probe-close from triggering normal disconnect->reconnect handler.
- 2026-05-17 — Guards against cross-contamination: add discoveryMode guards in openSocket(), reconnectSocket(), sendHeartbeat(). Without these, a scheduled reconnect from before discovery could fire and clobber state.socketOpen during the scan.
- 2026-05-17 — Fail-closed devId match: only accept match if response.devId == storedDevId. If no devId in response (heartbeat echo, wrong-key garbage), log warn and skip. Prevents wrong Tuya device being accepted.
- 2026-05-17 — parse() post-processing guard: during discovery, consumeReceiveBuffer() routes to discoveryHandleResponse() via processFrame(). Add discoveryMode guard after consumeReceiveBuffer() in parse() to skip pumpQueue() and other normal-mode operations.
- 2026-05-17 — Timeout-based fallback for unreachable IPs: Hubitat rawSocket does not guarantee synchronous failure. Always schedule runIn(3, "discoveryProbeTimeout") after each connect. Cancel with unschedule("discoveryProbeTimeout") when discoveryHandleResponse fires. Timeout moves to next IP.

### 2026-05-18 — Pending perf/quality todo sweep

- Repo-backed audit status: Trinity's redundant-write audit is effectively closed except **SC-4** (`setFloorMinTemp` still lacks a cached-current-value guard). The later perf audit is only partially recoverable from commit history: fixes **#1** (lastActivity throttling), **#3** (Touchstone heartbeat 10s→20s), and **#5** (SunStat child telemetry dedupe) are documented in shipped commits, but the underlying specs for **#2** and **#4** were not preserved in current `.squad/decisions*` files.
- Highest-value still-open Touchstone perf items are all in the hot parse path: stop persisting `state.rxBuffer` on every chunk when no partial frame remains, remove dead `state.lastDps` writes, and replace byte-copy helper loops with primitive/System.arraycopy-style copies. These are pure driver-internal optimizations in `drivers/touchstone-fireplace/touchstone-fireplace.groovy`.
- The next Gemstone wins are cache-shape and event-hygiene work in `drivers/gemstone-lights/gemstone-lights.groovy`: add reverse lookup maps for `patternId -> name/index`, replace `cloneMap()` JSON round-trips with a lighter copy strategy, and gate unchanged refresh telemetry (`switch`/`level`/`hue`/`saturation`) behind change checks.
- SunStat's remaining repo-backed audit item is still `setFloorMinTemp()` in `drivers/sunstat-thermostat/sunstat-thermostat-child.groovy`: cache the parsed `Schedule.Floor.W` value alongside `state.floorAway`, then skip the read-modify-write PATCH when the requested warmth value already matches.

## Learnings (v0.1.28 — 2026-05-18)

- 2026-05-18 — Touchstone parse-path buffer hygiene: build the concatenated socket hex in a local variable, feed it through `consumeReceiveBuffer(buffer)`, and persist `state.rxBuffer` only when a partial Tuya frame remains; fully-consumed chunks should remove the state key instead of rewriting it.
- 2026-05-18 — Parse/poll event dedupe in Hubitat should be path-aware: compare inbound telemetry against `device.currentValue(...)` (numeric attrs via `BigDecimal`) and skip unchanged `sendEvent`s on the parse path, while leaving user-command handlers free to emit immediate digital events after real outbound writes.
- 2026-05-18 — Hot-path clone hygiene for Hubitat Groovy: if the real data shapes are `Map`/`List` trees of scalars (Gemstone patterns, queued request payloads), replace `JsonOutput.toJson(...)` + `JsonSlurper.parseText(...)` deep copies with a small recursive container clone. You keep state/request isolation without paying JSON serialization cost on every refresh, queue, and effect activation.

## 2026-05-18 — Touchstone v0.1.29 (perf todos #6/#7)

### What changed

- Removed the dead `state.lastDps` write from `processFrame()` after re-grepping the driver and confirming there were no `state.lastDps` readers to migrate.
- Added one-time `state.remove("lastDps")` cleanup in `initialize()` so upgraded devices shed the stale state key without putting the write back on the parse path.
- Reworked `concatBytes()`, `sliceBytes()`, `startsWithBytes()`, and `protocol33HeaderBytes()` to use primitive `int` counters, with `System.arraycopy(...)` for contiguous copies in concat/slice/header assembly.
- Bumped the driver and `drivers/touchstone-fireplace/packageManifest.json` to `0.1.29` and added release-note-safe changelog entries dated `2026-05-18`.

### Why

- These were the last two medium-priority Touchstone perf items still sitting directly on the Tuya send/receive path. Removing dead state churn and boxed byte-copy loops trims Hubitat overhead without touching AES framing, CRC32, or any reflection-sensitive code.

## Learnings (v0.1.29 — 2026-05-18)

- 2026-05-18 — When retiring a hot-path `state.*` cache in a Hubitat driver, remove the write from the hot path and clear the legacy key once during `initialize()`; otherwise the stale state variable survives upgrades even though nothing reads it anymore.
- 2026-05-18 — Tuya framing helpers on Hubitat should stay on plain `byte[]` plus primitive `int` math: use `System.arraycopy(...)` for contiguous `concatBytes`/`sliceBytes`/header copies, and reserve manual loops only for comparison scans like `startsWithBytes()`.
