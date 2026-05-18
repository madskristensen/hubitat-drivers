# Tank — Driver Developer

**⚠️ SUMMARIZED 2026-05-18T01:41:11Z — Main history moved to `history-archive.md` (file was 20,381 bytes).**

---

## Current Active Work (2026-05-18)

### Touchstone v0.1.18 — Persistent Socket + Tuya Push Subscriptions
- **Shipped:** 2026-05-17 (Commit 67f905b)
- **Status:** Pending Switch hardware validation

### Gemstone v0.4.10 — Multi-Controller Zones / Named Controller Binding
- **Shipped:** 2026-05-17 (Commit e35b666)
- **Status:** Pending Switch hardware validation

### Touchstone v0.1.11 — DP 105 Removal (Awaiting Mads Test)
- **Investigation:** Mads tested setLogBrightness on real hardware — no response
- **Conclusion:** DP 105 is read-only on Sideline Elite
- **Action:** Removed command, attribute, and default preference

## Active Milestones Summary

### Touchstone Fireplace Driver (Current)

v0.1.4 shipped with optional power-on defaults + safety hardening (heater never auto-starts) + Hubitat sandbox reflection fixes. See .squad/orchestration-log/ for v0.1.3 + v0.1.4 batch details.

**Key learnings:**
- Power-on defaults: use runInMillis() for async delay (1500ms) to allow firmware settle post-power-on
- Heater safety: never auto-toggle hazardous hardware; keep behind explicit user commands
- Hubitat sandbox: blocks reflection (.getClass(), .metaClass, etc.) at runtime, not just imports
- Documentation for safety-critical features: be explicit, clear, and direct about intentional omissions

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

---

Participated in 4-way driver improvement scan with Trinity, Cypher, Switch. Findings consolidated by Squad. Orchestration log: .squad/orchestration-log/2026-05-17T15-41-32-tank.md.

- 2026-05-17T19:24:48-07:00 — **Spock unit test POC (Touchstone pure helpers):** Strategy A (extracted helpers) is the right starting point. Key learnings:
  1. **Strategy A vs B in practice:** Strategy A (copy pure functions to a testable helper class) has zero mocking friction and runs in ~1s. The main hidden cost is the dual Tuya frame format: `buildTuyaFrame` produces *outgoing* frames (no retcode, 24-byte minimum); `processFrame` parses *incoming* frames (with retcode at offset 16, 28-byte minimum). Strategy A exposed this immediately — Strategy B would have surfaced the same issue but with more debugging noise.
  2. **CRC32 cross-validation pattern:** Test the driver's pure-Groovy CRC32 against `java.util.zip.CRC32` directly in the spec. This confirms the lookup table is identical to the JDK implementation, which confirms the Tuya framing is correct at the wire level.
  3. **Gradle + Java 8 JRE (not JDK):** Gradle 7.6.4 runs fine against a JRE (not a full JDK) for Groovy-only builds because the Groovy compiler (`groovyc`) ships with the Groovy JAR — `javac` is not required. This is a non-obvious but important detail for CI on minimal images.
  4. **Extending to Gemstone:** The ABGR color math and `controllerName` match are the best Strategy A extraction targets. Effect catalog lookups need Strategy B to avoid copy-pasting the full catalog definition.
  5. **Extending to SunStat:** Boost expiry math should accept `now()` as an injectable `long` parameter rather than calling `now()` internally — makes tests deterministic without mocking. Same pattern for token expiry checks.
  6. **`@Field static final` on extracted helpers:** In Strategy A, use a plain `static final` in a `class` (not a script-level `@Field`). This avoids the script-vs-class compilation ambiguity that Strategy B would need to handle.

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

