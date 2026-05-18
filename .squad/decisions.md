# Decisions

---

## 2026-05-18: Daikin Driver Research (Cypher + Trinity)

**Status:** COMPLETE — Two companion memos assess upstream `eriktack/hubitat-daikin-wifi` and converge on fork-into-repo recommendation.

### Cypher's Assessment

Fork `eriktack/hubitat-daikin-wifi` into `drivers/daikin-wifi/` as v0.1.0 (Option C). The upstream repo is effectively abandoned — issues disabled, two open PRs unreviewed for 1–2 years, last code commit 2021. The root bug is a two-line fix: `otemp=-` (Daikin sentinel for "outdoor sensor unavailable") bypasses the truthy guard on line 464 and hits `Double.parseDouble("-")` on line 466, throwing `NumberFormatException` every poll cycle when the field is in this state. Fixing that plus applying in-house hygiene (descriptionText, lastActivity, parse-path dedup) is a focused 3–5 hour session using the same patterns as Touchstone/Gemstone/SunStat. Also file a PR upstream (Option B) in parallel as good-citizen contribution — 30 minutes, may never merge but costs little. Do not go with Option D (rewrite) — the code is structurally sound; just SmartThings-era on polish.

**Key Facts:**
- **License:** MIT — full fork rights confirmed
- **Protocol:** Local LAN HTTP (BRP069B series, `/aircon/get_*`), not cloud
- **Bug line:** `daikin-wifi-split-system-hubitat.groovy` line 466 — `Double.parseDouble(otemp)` when `otemp="-"`
- **Upstream:** Abandoned (2-star repo, issues disabled, 2 open unreviewed PRs)
- **Hygiene gap:** All 66 events lack `descriptionText`; no lastActivity; no HealthCheck
- **Sandbox:** Clean — no blocklisted patterns

### Trinity's Worth-It Verdict + Priority List

Fork and fix. The upstream driver exposes the right endpoints and has sound architecture, but is missing critical Hubitat schema (`supportedThermostatModes` completely absent — breaks Rule Machine), has no `initialize()` lifecycle (polling dies on hub restart), and over-polls energy endpoints on every refresh cycle. These are all straightforward fixes. Combined with Cypher's root bug fix (`otemp="-"` sentinel), a focused 4–5 hour session produces a reliable, working driver. Full "exemplary" rewrite (HealthCheck, event hygiene, econo/powerful mode, model detection) would add ~12–15 hours on top. Defer those to a follow-up if the driver proves itself useful.

**Recommended Scope — Ordered Priority List:**
1. **Apply Cypher's bug fix** — guard `otemp`/`htemp` against `"-"` sentinel before `Double.parseDouble()`. Prerequisite for stable operation.
2. **Add `supportedThermostatModes`** — emit `["auto","cool","heat","dry","fan","off"]` in `installed()` + `updated()`. Fixes Rule Machine thermostat mode dropdowns.
3. **Fix `supportedThermostatFanModes`** — also emit in `installed()`, not just `updated()`. 5-minute change.
4. **Add `initialize()` lifecycle** — chain from `installed()` + `updated()`; re-registers polling after hub restart.
5. **Throttle energy endpoint polling** — separate `refreshEnergy()` on a 30-minute schedule; remove `get_week_power_ex` and `get_year_power_ex` from the main `refresh()` cycle.
6. **Add `capability "HealthCheck"` + `lastActivity`** — LAN driver qualifies for Pattern A per skill `hubitat-healthcheck-vs-lastactivity`.
7. **Add econo/powerful mode support** — `get_special_mode` read + `setSpecialMode()` command.
8. **Apply event hygiene** — `descriptionText:` on all `sendEvent` calls + `emitIfChanged()` on parse paths.

**Items 1–5:** ~4–5 hrs. Delivers a trustworthy, Rule Machine-compatible driver.  
**Items 1–8:** ~16–20 hrs. Delivers repo-quality, shelf-stable driver.

**Full memos:** `.squad/files/daikin-research/daikin-driver-assessment.md`, `.squad/files/daikin-research/daikin-capability-gap-memo.md`

---

## 2026-05-18: Tank — Pending driver perf/quality todos

**Status:** PENDING — Eight fresh perf/quality audit items proposed by Tank, including one outstanding repo-backed item (SC-4 from Trinity's redundant-write audit).

### Proposed Items

1. **id**: `sunstat-sc4-cache-floor-min-temp`
   **title**: Cache floor warmth and skip redundant `setFloorMinTemp` PATCH
   **description**: In `drivers/sunstat-thermostat/sunstat-thermostat-child.groovy`, extend `parseDeviceStateInternal()` to cache `Schedule.Floor.W` alongside the existing `state.floorAway`, then compare that cached value in `setFloorMinTemp()` before sending the read-modify-write PATCH. This closes the last unshipped repo-backed audit item and avoids no-op Watts API calls when rules re-assert the same floor minimum.
   **priority**: medium
   **source**: existing audit ID `SC-4`

2. **id**: `touchstone-rxbuffer-partial-state`
   **title**: Persist `state.rxBuffer` only when a partial Tuya frame remains
   **description**: In `drivers/touchstone-fireplace/touchstone-fireplace.groovy`, `parse()` writes the full concatenated hex buffer into `state.rxBuffer` before `consumeReceiveBuffer()`, even when the chunk is fully consumed. Move the state write so only leftover partial-frame data is persisted; this reduces Hubitat state I/O on every inbound socket chunk without changing frame parsing behavior.
   **priority**: high
   **source**: new — Tank proposal

3. **id**: `touchstone-drop-lastdps-state`
   **title**: Remove dead `state.lastDps` writes from Touchstone frame processing
   **description**: `processFrame()` in `drivers/touchstone-fireplace/touchstone-fireplace.groovy` still stores each normalized DPS map into `state.lastDps`, but nothing ever reads that cache. Dropping the write trims hot-path state churn and keeps the device State Variables panel cleaner with no functional change.
   **priority**: medium
   **source**: new — Tank proposal

4. **id**: `touchstone-dedupe-parse-events`
   **title**: Dedupe unchanged Touchstone telemetry events in `applyDps()`
   **description**: In `drivers/touchstone-fireplace/touchstone-fireplace.groovy`, `applyDps()` already distinguishes changed vs unchanged values for logging, but `emitAttribute()` still sends events for both. Add change checks (or a parse-only `emitIfChanged` helper) for attributes such as `flameColor`, `flameBrightness`, `charcoalColor`, `flameSpeed`, `heatLevel`, `heatingSetpoint`, and `temperature` so periodic refreshes and push echoes stop filling event history with unchanged state.
   **priority**: high
   **source**: new — Tank proposal

5. **id**: `touchstone-byte-copy-helpers`
   **title**: Replace boxed byte-copy loops with primitive / arraycopy helpers
   **description**: `concatBytes()`, `sliceBytes()`, `startsWithBytes()`, and `protocol33HeaderBytes()` in `drivers/touchstone-fireplace/touchstone-fireplace.groovy` still use `for (Integer i = ...)` loops in hot protocol helpers. Switch to primitive `int` counters and `System.arraycopy` where possible to reduce autoboxing and per-frame copy overhead on every send/receive cycle.
   **priority**: medium
   **source**: new — Tank proposal

6. **id**: `gemstone-clonemap-copy-hygiene`
   **title**: Replace Gemstone `cloneMap()` JSON round-trips with lighter copies
   **description**: In `drivers/gemstone-lights/gemstone-lights.groovy`, `cloneMap()` serializes to JSON and parses back for every pattern/request copy, and the helper sits on many hot call sites (`rememberPattern`, request queueing, refresh handling, effect activation). Replace it with a lighter copy strategy tailored to the actual pattern/request shapes so refreshes and command bursts spend less CPU and GC time on avoidable JSON work.
   **priority**: high
   **source**: new — Tank proposal

7. **id**: `gemstone-dedupe-refresh-telemetry-events`
   **title**: Gate unchanged Gemstone refresh telemetry behind change checks
   **description**: `handleRefreshResponse()` in `drivers/gemstone-lights/gemstone-lights.groovy` still emits `switch`, `level`, `hue`, and `saturation` on every successful refresh even when the payload matches current device state. Reuse the same event-hygiene pattern SunStat adopted today so poll-driven refreshes stop creating duplicate events while user-command paths keep their digital events.
   **priority**: high
   **source**: new — Tank proposal

8. **id**: `cloud-driver-metadata-hygiene`
   **title**: Add missing `Polling` / `Actuator` markers on cloud drivers
   **description**: `drivers/gemstone-lights/gemstone-lights.groovy` and `drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy` both implement `poll()` but still omit `capability "Polling"`, and the SunStat parent accepts commands without `capability "Actuator"`. Add the missing metadata markers so Hubitat apps discover these drivers correctly and command-capable parents advertise the expected contract.
   **priority**: low
   **source**: new — Tank proposal

---

## 2026-05-18: Redundant-write audit shipping — five driver releases

**Status:** SHIPPED — 5 driver releases close 16 of 17 audit findings (SC-4 deferred).
- Touchstone v0.1.25 (b4122ee) — T-2, T-3
- Touchstone v0.1.26 (ffe2e9d) — T-4 through T-10
- Gemstone v0.4.12 (91e0d1a) — G-1
- Gemstone v0.4.13 (6ee553a) — G-2 through G-6
- SunStat v0.1.8 (f9060fb) — SP-1, SC-1, SC-2, SC-3 (SC-4 deferred)

---

### Decision Drop — Touchstone v0.1.25 (T-2, T-3)

## Context

Trinity's redundant-write audit identified two findings in the Touchstone Fireplace driver:

- **T-2 🔴** — `on()` unconditionally sends DP1=true even when the switch is already on. Tuya fireplaces emit an audible click on every DP1 write regardless of current state. Rules that re-assert `on` (common in mode-based automations) produce repeated click artifacts.
- **T-3 🟡** — `off()` unconditionally sends DP1=false even when the switch is already off. No audible artifact, but generates unnecessary wire traffic and can interfere with DP echo sequencing.

## Decision

Add an early-return guard at the top of `on()` and `off()` (after the null-profile check, before any state mutations or DP writes) that returns silently when the device is already in the requested state:

```groovy
// on()
if (device.currentValue("switch") == "on") {
    debugLog "on(): device already on — skipping DP write"
    return
}

// off()
if (device.currentValue("switch") == "off") {
    debugLog "off(): device already off — skipping DP write"
    return
}
```

## applyOnPowerOnDefaults Reschedule Decision

`on()` calls `runInMillis(POWER_ON_DEFAULTS_DELAY_MILLIS, "applyOnPowerOnDefaults")` after the DP write. The early-return bypasses this scheduling.

**Decision: skip the runIn when already on.**

Rationale:
- If the device is already on, the power-on defaults were applied during the prior `on()` invocation. Rescheduling them on a redundant `on()` call would re-fire `applyOnPowerOnDefaults` unnecessarily — the same redundant-write problem the v0.1.23/0.1.24 audit addressed.
- If a user believes values have drifted since the last power-on, the correct recovery path is toggle off → on, which forces a full transition including the defaults schedule. This is the idiomatic "re-apply defaults" gesture and is consistent with how the v0.1.23 defaults guard was designed.
- Adding a conditional re-runIn (e.g., "reschedule only if uptime > threshold") would introduce stateful complexity with no measurable benefit and contradicts the audit's skip-if-match philosophy.

## Consistency with Prior Art

Same pattern as v0.1.23 (`applyOnPowerOnDefaults` per-DP guards) and v0.1.24 (`defaultHeatingSetpoint` guard). All use `device.currentValue()` as the idempotency key and log at `debug` level on skip.

## Files Changed

- `drivers/touchstone-fireplace/touchstone-fireplace.groovy` — v0.1.25

## Findings Closed

- T-2 🔴 (audible artifact on redundant `on()`)
- T-3 🟡 (wire traffic on redundant `off()`)

---

### Decision Drop — Touchstone v0.1.26 (T-4 through T-10)

# Decision: Touchstone v0.1.26 — Batch-fix audit T-4 through T-10

**Date:** 2026-05-18  
**Author:** Tank  
**Driver:** drivers/touchstone-fireplace/touchstone-fireplace.groovy  
**Version:** 0.1.26

## Summary

Applied the v0.1.23 skip-if-match idempotency pattern to seven user-explicit command paths that were previously writing the device DP unconditionally (Trinity's 🟡 findings T-4 through T-10, plus T-10 🟢).

## Functions Guarded

| Finding | Function | Attribute | DP | Type |
|---|---|---|---|---|
| T-4 | setFlameColor(name) | "flameColor" | 101 | String (label) |
| T-5 | setFlameBrightness(level) | "flameBrightness" | 102 | String (label) |
| T-6 | setFlameSpeed(speed) | "flameSpeed" | 103 | String (label) |
| T-7 | setCharcoalColor(name) | "charcoalColor" | 104 | String (label) |
| T-8 | setHeatLevel(level) | "heatLevel" | 5 | String (normalized lowercase) |
| T-9 | setHeatingSetpoint(temp) | "heatingSetpoint" | 14 | Integer (compared as Integer) |
| T-10 | setChildLock(state) | "childLock" | 108 | String ("on"/"off") |

## Pattern Applied

Each function now reads `device.currentValue("<attribute>")` before the `sendDpWrite` call. If the current value matches the requested value AND current is non-null, the function logs a `debugLog` skip message and returns early without sending to the device.

Null-current rule: if `device.currentValue()` returns null (unknown state, fresh install), the write proceeds — guards only trigger on confirmed matches.

## Numeric Attribute Note (T-9)

`setHeatingSetpoint` compares as `Integer` (via `safeInt`) to avoid false mismatches from type coercion. The comparison uses the post-clamped value so edge-of-range requests are handled correctly.

## setHeatLevel Safety Note (T-8)

The heater is intentionally excluded from `applyOnPowerOnDefaults` for safety (no auto-start on power-on). That exclusion is **preserved and unaffected** by this change. The guard added here only applies to the explicit `setHeatLevel` user command path.

## Rationale

Tuya Local DP writes cause an audible relay click on the Touchstone Sideline Elite regardless of whether the new value equals the current one. Automation rules that re-assert the same state (e.g., scene controllers, mode rules) would cause repeated audible artifacts. The skip-if-match pattern eliminates these while preserving correct behavior: the first write always goes through (null state), and the Hubitat attribute stays in sync via the `emitAttribute` call that follows a successful write.

---

### Decision Drop — Gemstone v0.4.12 (G-1)

## Problem

`activateEffectWithPattern()` unconditionally sent `PUT /deviceControl/play/pattern` on every
`setEffect()` call. Gemstone hardware visibly restarts the animation sequence on receiving any
pattern PUT, even with identical parameters. Calling `setEffect("Pulse")` when Pulse is already
playing caused a visible animation glitch.

**Audit finding:** G-1 🔴 (Trinity's redundant-write audit)

## Fix (v0.4.12)

Added an idempotency guard in `activateEffectWithPattern()` immediately before the
`executeOrQueueRequest(buildEffectRequest(...))` call:

```groovy
String currentEffect = safeString(device.currentValue("effectName"))
if (resolvedName && currentEffect == resolvedName) {
    debugLog "setEffect: '${resolvedName}' already active — skipping pattern PUT"
    return
}
```

State events (`switch`, `level`, `effectName`, `colorMode`) are still emitted before the guard —
the Hubitat device state stays consistent whether or not the PUT is skipped.

`safeString` is an existing helper at L2221 (confirmed present in the file).

## Edge-Case Decision: cycleEffect with 1-effect catalog

`setNextEffect()` and `setPreviousEffect()` delegate through `cycleEffect()` →
`activateEffectByIndex()` → `activateEffectWithPattern()`. With a 1-effect catalog, "next" wraps
back to the same effect — the guard suppresses that cycle command (no PUT is sent).

**Decision:** Accept this edge case. A 1-effect catalog is a degenerate configuration; there is
nothing meaningful to cycle to. The guard suppression is the correct behavior (no pointless
restart). A `forceWrite: true` flag was considered to thread through `cycleEffect()` to bypass the
guard, but the wiring would touch three call sites for negligible real-world benefit. Simpler
guard wins.

This edge case is documented here; no code comment added (would be noise for normal catalogs).

## Same Pattern As

Touchstone v0.1.23+ skip-if-match guards for redundant defaults (Trinity audit T-series).

---

### Decision Drop — Gemstone v0.4.13 (G-2 through G-6)

# Decision Drop: Gemstone Lights v0.4.13 — Batch Yellow Audit Findings G-2 through G-6

**Author:** Tank (Driver Developer)
**Date:** 2026-05-18
**Driver:** `drivers/gemstone-lights/gemstone-lights.groovy`
**Version shipped:** 0.4.13

---

## Summary

Five 🟡 cloud-quota findings from Trinity's redundant-write audit were closed in a single batch. All share the same root cause: commands were unconditionally issuing cloud API calls (`PUT /deviceControl/onState` or `PUT /deviceControl/play/pattern`) even when the device was already in the requested state.

---

## Findings Closed

| # | Function | Guard condition |
|---|---|---|
| G-2 | `on()` | `device.currentValue("switch") == "on"` → return before sendCommand |
| G-3 | `off()` | `device.currentValue("switch") == "off"` → return before sendCommand |
| G-4 | `setLevel()` | captured level before sendEvent; if `currentLevel == clamped` → skip pattern PUT |
| G-5 | `setColor()` | composite: `colorMode == "RGB"` AND hue + saturation + level all match → skip pattern PUT |
| G-6 | `setColorTemperature()` | `colorMode == "CT"` AND `colorTemperature == kelvin` → skip pattern PUT |

---

## G-5 Composite-Write Rationale

`setColor()` writes three attributes atomically (hue, saturation, level). A single-attribute check would miss partial-match cases (e.g., same hue but different saturation). Three options were considered:

1. **Compare hue + saturation + level all three** — safe; misses only if all three happen to match by coincidence with a non-RGB color mode. ✅ **Chosen.**
2. **Check colorMode == "RGB" AND three components** — same as option 1 with explicit mode guard. ✅ (also included — this is effectively what option 1 became.)
3. **Skip guarding G-5 entirely** — avoided; the composite check is tractable and avoids unnecessary cloud calls.

The implemented guard requires:
- `colorMode == "RGB"` (ensures we're not in CT or EFFECTS mode)
- `curHue as Integer == hue` AND `curSat as Integer == saturation` AND `curLevel as Integer == level`

This is conservative: any mismatch on any component causes the PUT to proceed. No risk of false-dedup from mode transitions.

---

## G-6 CT Mode Guard Rationale

`setColorTemperature()` should only skip when in `colorMode == "CT"`. If the device is in RGB or EFFECTS mode and a CT command arrives with a matching kelvin value (unlikely but possible after a driver restart with stale state), we must still send the PUT to switch the hardware into CT mode. The guard therefore requires both conditions before skipping.

---

## Non-Interference with G-1 (setEffect)

G-1's guard (shipped v0.4.12) lives in `activateEffectWithPattern()` and checks `effectName`. The G-2–G-6 guards are in distinct command handlers (`on`, `off`, `setLevel`, `setColor`, `setColorTemperature`) and do not touch the effects execution path. No interaction.

---

## Sandbox Safety

All comparisons use `device.currentValue()` (standard Hubitat API) and integer arithmetic. No reflection, no `System.*`, no `Thread.*`. Passes Hubitat sandbox constraints.

---

### Decision Drop — SunStat v0.1.8 (SP-1, SC-1, SC-2, SC-3; SC-4 deferred)

# Decision Drop — SunStat v0.1.8: Redundant Cloud PATCH Batch Fix

**Date:** 2026-05-18  
**Author:** Tank  
**Drivers:** sunstat-thermostat-parent.groovy + sunstat-thermostat-child.groovy  
**Version bump:** 0.1.7 → 0.1.8

---

## Findings Addressed

### SP-1 — setAwayModeInternal (parent)
- Added skip-if-match guard reading `device.currentValue("awayMode")` before the optimistic `sendEvent` + PATCH.
- Uses `safeStr` + null-safe compare (`current != null && current == mode`).

### SC-1 — setThermostatMode (child)
- Added per-case guards inside the `switch` for "heat" and "off" branches.
- Reads `device.currentValue("thermostatMode")` before `sendEvt` + PATCH per branch.
- No guard needed for unsupported modes (they already return with a warning).

### SC-2 — setHeatingSetpoint (child)
- Added numeric guard before `sendEvent` + PATCH.
- Reads `device.currentValue("heatingSetpoint")`, casts to BigDecimal, compares to post-clamp `clamped` value.
- Guard fires on the clamped/rounded value so it matches what the driver would actually send.

### SC-3 — setScheduleEnabled (child) — dual-guard pattern
- **Existing guard:** `emitIfChanged` at L335 already deduplicates the Hubitat event. Retained unchanged.
- **New write-guard:** reads `device.currentValue("scheduleEnabled")` BEFORE `emitIfChanged` is called (so the pre-event value is captured correctly; `sendEvent` in Hubitat updates `device.currentValue()` synchronously). Checks captured value after the `wattsDeviceId` validation, before `sendDevicePatch`.
- Both guards coexist and solve independent problems:
  - `emitIfChanged` → prevents duplicate Hubitat events on the local platform
  - write-guard → prevents redundant cloud PATCH calls to the Watts API

---

## SC-4 Deferred — setFloorMinTemp

**Status:** DEFERRED — not addressed in this batch.

**Reason:** Fixing SC-4 requires caching the current `floorMinTemp` value from `parseDeviceState`. The function sends a read-modify-write payload to `/Device/{id}` with both `Floor.W` (warmth) and `Floor.A` (away) values. To skip a redundant PATCH, the driver would need to compare the incoming `temp` against the last-polled warmth value. That value is not currently stored in `state.*` — it would need to be cached as `state.floorMinTemp` (or similar) whenever `parseDeviceState` processes the schedule payload. That's a non-trivial state-handling change warranting its own focused commit.

**What would be needed:**
1. In `parseDeviceState`, when the schedule payload contains `Floor.W`, store it: `state.floorMinTemp = <parsed value>`
2. In `setFloorMinTemp`, read `safeBigDecimal(state.floorMinTemp, null)` and compare to `clamped`. If equal, skip PATCH.

---

## BY-DESIGN Exclusions (intentionally NOT guarded)

| Finding | Function | Reason |
|---------|----------|--------|
| SC-5 | cancelBoost() | State-assertion path — must always PATCH to defeat cloud drift and correctly exit boost state |
| SC-6 | setBoost() | Always a new boost with a new duration/expiry — no meaningful idempotency |
| SC-7 | boostExpired() / initialize() | Recovery paths — must always reassert state to ensure correctness after reconnect or reboot |

---

## Version History
- Parent: 0.1.7 → 0.1.8
- Child:  0.1.7 → 0.1.8

---

## 2026-05-18: Trinity Redundant-Write Audit — All Four Drivers

**Date:** 2026-05-18  
**Author:** Trinity (Lead / Architect)  
**Status:** AUDIT COMPLETE — pending Mads authorization for individual fixes  
**Triggered by:** Touchstone v0.1.23 idempotent-default pattern; user asked "do we have anything like that elsewhere?"

---

### Summary

**3 🔴 findings, 14 🟡, 1 🟢, 3 BY-DESIGN (not bugs).**

Recommended next steps:
1. **Authorize T-1 immediately** — the `defaultHeatingSetpoint` gap in `applyOnPowerOnDefaults` is the only unfixed item from the v0.1.23 work, and it's the same physical-click risk as the four color defaults that were fixed.
2. **Authorize T-2** — `on()` click-when-already-on is the most common automation pattern that triggers audible artifacts.
3. **Authorize G-1** — effect animation restart on `setEffect(sameName)` is the most visible cross-driver finding.
4. The 🟡 cloud-driver findings (Gemstone, SunStat) can be addressed in a single batch pass. No user-visible artifacts; purely API quota.

---

### Touchstone Fireplace (`drivers/touchstone-fireplace/touchstone-fireplace.groovy`)

| # | Function (lines) | DP / endpoint | Current check | Trigger | Severity |
|---|---|---|---|---|---|
| T-1 | `applyOnPowerOnDefaults()` L1227–1244 | DP14 (heatingSetpoint) | **None** — the four color/speed defaults above it ARE guarded (v0.1.23), but `defaultHeatingSetpoint` is not | Power-on lifecycle, fires ~1.5s after every `on()` | 🔴 Visible — DP14 write during post-ON transition causes relay click on Tuya firmware |
| T-2 | `on()` L395–409 | DP1=true | **None** | User or rule call | 🔴 Visible — Tuya fireplaces click on DP write even if already on; rules that re-assert `on` repeatedly produce audible artifacts |
| T-3 | `off()` L412–424 | DP1=false | **None** | User or rule call | 🟡 Wire only — device is already off; no audible artifact when off; still generates wire traffic |
| T-4 | `setFlameColor()` L596–612 | DP101 | None | Explicit user command | 🟡 Wire only — user commanded it; automation loops could repeat |
| T-5 | `setFlameBrightness()` L615–630 | DP102 | None | Explicit user command | 🟡 Wire only |
| T-6 | `setFlameSpeed()` L657–672 | DP103 | None | Explicit user command | 🟡 Wire only |
| T-7 | `setCharcoalColor()` L633–650 | DP104 | None | Explicit user command | 🟡 Wire only |
| T-8 | `setHeatLevel()` L675–689 | DP5 | None | Explicit user command | 🟡 Wire only — heater relay not safety-sensitive but generates click on repeat |
| T-9 | `setHeatingSetpoint()` L692–708 | DP14 | None | Explicit user command | 🟡 Wire only |
| T-10 | `setChildLock()` L723–731 | DP108 | None | Explicit user command | 🟢 Harmless — child lock DP unlikely to produce visible artifact on repeat |

**Recommended fix — T-1 (highest priority):**  
Same skip-if-match pattern as v0.1.23 applied to the four color/speed defaults. Add before the `sendDpWrite` at L1235:
```groovy
Integer currentSetpoint = safeInt(device.currentValue("heatingSetpoint"), null)
if (currentSetpoint != null && currentSetpoint == clampedSetpoint) {
    traceLog "applyOnDefaults: skipping defaultHeatingSetpoint — already ${clampedSetpoint}°${unit}"
} else {
    // existing sendDpWrite block
}
```

**Recommended fix — T-2:**  
Add at the top of `on()`, before `sendDpWrite`:
```groovy
if (device.currentValue("switch") == "on") {
    debugLog "on(): device already on — skipping DP write"
    return
}
```
(Remove the early-return after confirming `applyOnPowerOnDefaults` should still be rescheduled in this path if needed.)

**Caveat — T-8 (setHeatLevel):** The heater is intentionally excluded from `applyOnPowerOnDefaults` for safety. The explicit command path (`setHeatLevel()`) is fine to guard; the exclusion in the defaults path is correct and should not be changed.

---

### Gemstone Lights (`drivers/gemstone-lights/gemstone-lights.groovy`)

| # | Function (lines) | Endpoint | Current check | Trigger | Severity |
|---|---|---|---|---|---|
| G-1 | `activateEffectWithPattern()` L983–996 | `PUT /deviceControl/play/pattern` | **None** — no check against current `effectName` or `state.lastPattern.id` | `setEffect()` or `cycleEffect()` | 🔴 Visible — Gemstone hardware re-executes the animation sequence on receiving a pattern PUT; calling `setEffect("Pulse")` when Pulse is already playing visibly restarts the animation |
| G-2 | `on()` L198–203 | `PUT /deviceControl/onState {onState:true}` | None | User or rule call | 🟡 Wire/API — extra cloud quota; device likely transitions smoothly rather than flashing |
| G-3 | `off()` L205–209 | `PUT /deviceControl/onState {onState:false}` | None | User or rule call | 🟡 Wire/API |
| G-4 | `setLevel()` L216–228 | `PUT /deviceControl/play/pattern` (brightness) | None | User or rule call | 🟡 Wire/API |
| G-5 | `setColor()` L235–254 | `PUT /deviceControl/play/pattern` (colors) | None | User or rule call | 🟡 Wire/API — solid-color writes are composite (hue+sat+level); no single attribute to check atomically |
| G-6 | `setColorTemperature()` L277–301 | `PUT /deviceControl/play/pattern` | None | User or rule call | 🟡 Wire/API |

**Recommended fix — G-1:**  
In `activateEffectWithPattern()` before `executeOrQueueRequest(buildEffectRequest(...))`, add:
```groovy
String currentEffect = safeString(device.currentValue("effectName"))
if (resolvedName && currentEffect == resolvedName) {
    debugLog "setEffect: '${resolvedName}' already active — skipping pattern PUT"
    return
}
```

**Caveat — `cycleEffect()`:** `setNextEffect()` and `setPreviousEffect()` delegate through `cycleEffect()` → `activateEffectByIndex()` → `activateEffectWithPattern()`. The skip-if-match above would also suppress a user pressing "next" when the catalog has only one effect (next wraps back to the same effect). That edge case is probably acceptable but worth noting in the fix.

---

### SunStat Parent (`drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy`)

| # | Function (lines) | Endpoint | Current check | Trigger | Severity |
|---|---|---|---|---|---|
| SP-1 | `setAwayModeInternal()` L210–256 | `PATCH /Location/{id}/State {awayState:...}` | **None** — optimistic `sendEvent` fires first but the PATCH is unconditional | `setHome()`, `setAway()`, `setAwayMode()` | 🟡 Wire/API — extra API quota; no visible effect |

**Recommended fix — SP-1:**  
Add before the PATCH call:
```groovy
if (safeStr(device.currentValue("awayMode")) == mode) {
    debugLog "setAwayMode: already '${mode}' — skipping PATCH"
    return
}
```

---

### SunStat Child (`drivers/sunstat-thermostat/sunstat-thermostat-child.groovy`)

| # | Function (lines) | Endpoint | Current check | Trigger | Severity |
|---|---|---|---|---|---|
| SC-1 | `setThermostatMode()` L174–191 | `PATCH /Device/{id} {Settings:{Mode:...}}` | None | `heat()`, `off()`, `setThermostatMode()` | 🟡 Wire/API |
| SC-2 | `setHeatingSetpoint()` L204–213 | `PATCH /Device/{id} {Settings:{Heat:...}}` | None | User or rule call | 🟡 Wire/API |
| SC-3 | `setScheduleEnabled()` L328–343 | `PATCH /Device/{id} {Settings:{SchedEnable:...}}` | **Partial** — `emitIfChanged` gates the Hubitat event but `sendDevicePatch` at L342 is unconditional regardless | User command | 🟡 Wire/API — note: event deduplication ≠ write deduplication; the two guards solve different problems |
| SC-4 | `setFloorMinTemp()` L350–366 | `PATCH /Device/{id} {Settings:{Schedule:{Floor:{W:...}}}}` | None | User command | 🟡 Wire/API — fixing requires caching the current floorMinTemp from `parseDeviceState` |

**BY-DESIGN (do not fix):**

| # | Function | Reason |
|---|---|---|
| SC-5 | `cancelBoost()` L291–313 — writes `Heat: preBoostSetpoint` and optionally `SchedEnable: "On"` | State-assertion semantic. Intentionally force-writes the restore setpoint regardless of current state, to defeat cloud drift that may have occurred during the boost window. |
| SC-6 | `setBoost()` L240–285 — writes `Heat: boostTarget` | Always writes a new value (currentSetpoint + delta), never the same value the device already has. Not a redundant write. |
| SC-7 | `boostExpired()` / `initialize()` boost recovery L319–321, L128–145 | Timer callback delegates to `cancelBoost()` — same BY-DESIGN reasoning as SC-5. Hub-restart recovery re-arms the timer only; no immediate device write unless boost has overrun. |

**SC-3 fix note:** The `emitIfChanged` at L335 correctly guards the Hubitat event. The fix is to add the same condition before the `sendDevicePatch` call:
```groovy
String currentSched = safeStr(device.currentValue("scheduleEnabled"))
if (currentSched == lower) {
    debugLog "setScheduleEnabled: already '${lower}' — skipping PATCH"
    return
}
```

---

### Cross-Driver Observations

1. **The `emitIfChanged` / write-guard split** — SunStat child's `setScheduleEnabled` is the clearest example of a half-fix: Hubitat event deduplication is present but device write deduplication is absent. These are independent guards solving independent problems. Any driver that adds `emitIfChanged` should also ask "does the device write need the same guard?"

2. **Lifecycle-driven writes are 🔴; user-explicit are 🟡** — `applyOnPowerOnDefaults` fires automatically on every power-on without user awareness. User commands (setFlameColor, setHeatingSetpoint) are 🟡 because the user explicitly requested the write.

3. **State-assertion paths are BY-DESIGN** — SunStat's boost cancel/expire path intentionally writes the restore setpoint unconditionally. This is correct behavior for "defeat cloud drift" semantics. Do not apply skip-if-match to these paths.

4. **Cloud vs. local severity** — Redundant writes to Tuya local (Touchstone) can produce audible/tactile artifacts (clicks, brief flame state transitions). Redundant REST calls to Gemstone/Watts produce API quota consumption. Exception: Gemstone `PUT /deviceControl/play/pattern` also restarts the animation on hardware (🔴).

---

## 2026-05-18: Tank — Touchstone v0.1.24 — defaultHeatingSetpoint skip-if-match (closes T-1)

**Date:** 2026-05-18  
**Author:** Tank  
**Driver:** Touchstone Fireplace v0.1.24  
**Status:** Shipped  
**Scope:** Closes Trinity's T-1 finding from redundant-write audit; extends v0.1.23 pattern to heatingSetpoint default

---

### What Changed

Applied the same skip-if-match guard from v0.1.23 to the `defaultHeatingSetpoint` line in `applyOnPowerOnDefaults()`. Before writing DP14, the driver now compares the configured default against `device.currentValue("heatingSetpoint")`. Only sends the DP write if:
- Current value is **null** (state unknown), OR
- Current value **differs** from the configured default

If they match, skips the write and logs at `traceLog`.

### Pattern (v0.1.23 + v0.1.24 unified)

All power-on defaults now use the same conditional logic:
```groovy
String current = device.currentValue("attributeName")
if (current != null && current == configuredDefault) {
    traceLog "applyOnDefaults: skipping defaultAttributeName — already '${configuredDefault}'"
} else {
    debugLog "applyOnDefaults: applying defaultAttributeName = '${configuredDefault}' (was '${current}')"
    sendDpWrite(...)
}
```

Guarded defaults in v0.1.24: `defaultFlameColor`, `defaultFlameBrightness`, `defaultFlameSpeed`, `defaultCharcoalColor`, **`defaultHeatingSetpoint`** (NEW).

### Rationale

- DP14 write during post-power-on transition causes relay click on Tuya firmware (visible/audible)
- User turns fireplace off/on; firmware retains setpoint state; unconditional write is noise
- Matches the conditional pattern already in v0.1.23 for the four light/flame defaults
- No protocol behavior change; purely guard around existing write path
- Null = "needs the default" is the safe side (race between defaults window and first STATUS frame)

### Severity Closed

✅ T-1 🔴 → 🟢 (no longer visible artifact risk after this fix)

---

## 2026-05-18: Touchstone Driver Log Hygiene — trace vs debug taxonomy

**Date:** 2026-05-18  
**Author:** Tank  
**Driver:** touchstone-fireplace.groovy  
**Version introduced:** 0.1.22

Add a `traceEnable` preference (bool, default false, auto-off after 30 min) alongside the existing `logEnable`. Introduce a `traceLog()` helper gated on `traceEnable` / `log.trace`. Demote protocol-level firehose lines from `debugLog` to `traceLog`.

This matches the community pattern (e.g. kkossev Zigbee drivers) and keeps `logEnable=true` readable in production.

**Taxonomy:**
- **traceLog** (firehose, off by default): Heartbeat sent/ACK, refresh queue/send, decoded Tuya payload (raw wire dump), DP 105 echo, unchanged DP echoes
- **debugLog** (stays at debug): Non-heartbeat cmd received, user-initiated writes (power, setFlameColor), changed DP echoes, socket lifecycle, errors
- **"Log only on change" rule for DP echoes:** Before `debugLog` in `applyDps`, compare decoded label against `device.currentValue(attributeName)`. If equal → `traceLog`. If changed → `debugLog`.

Implementation: `traceOff()` mirrors `logsOff()` (30-min auto-disable). `updated()` schedules `traceOff` independently. `installed()` writes `traceEnable = false`. `traceLog()` is `private void` at file bottom. Protocol behavior (heartbeat timing, refresh cadence, DP map, command dispatch) unchanged.

---

## 2026-05-17: README Pre-Release Scrub (Tank-16)

**Date:** 2026-05-17  
**Author:** Tank (Copilot)  
**Task:** Remove pre-1.0 migration, breaking-change, and upgrade-path content from all READMEs

Removed pre-1.0 migration, breaking-change, and upgrade-path content from root README and all three driver READMEs (Gemstone, Touchstone, SunStat). Each now reads as if this is the first and only version. No mention of version history, backward compatibility, or upgrade paths. Changelog sections listing v0.4.x changes are gone. Groovy driver Changelog: blocks remain (developer-facing). Commit e4a68d5.

---

## ⚠️ USER DIRECTIVE — NO MIGRATION/BREAKING-CHANGE NOTES IN DRIVER READMEs

**By:** Mads Kristensen (via Copilot)  
**Date:** 2026-05-17  
**Status:** ACTIVE — affects all future README work

Do not include any "breaking changes," "what's new in this version," "things to be aware of when updating," migration guides, or upgrade-path notes in any driver README (root README + drivers/*/README.md). "No one has installed these drivers yet but me. we're still developing them."

Affects all driver READMEs going forward. Existing migration-style sections must be scrubbed. The driver .groovy Changelog: block at the top of each file is the changelog (developer-facing); READMEs describe what the driver DOES, not what it USED TO DO. Revisit when post-1.0.

---

## 2026-05-17: Asymmetric Health Monitoring — HealthCheck vs lastActivity (Tank-17)

**Date:** 2026-05-17  
**Author:** Tank (Driver Developer)  
**Status:** Shipped (v0.1.21, v0.4.11, v0.1.7)

**Local TCP drivers (Touchstone):** Add the full HealthCheck capability with ping(), healthStatus, and lastActivity.

**Cloud REST drivers (Gemstone, SunStat):** Add lastActivity only. No HealthCheck, no ping().

Touchstone: local LAN TCP (free, no rate limits). The persistent socket can silently die between heartbeat windows. ping() provides on-demand socket validation. healthStatus = offline after >= 2 consecutive reconnect attempts.

Gemstone + SunStat: API calls consume cloud quota. lastActivity is a passive timestamp — advances automatically on every successful 2xx response.

Implementation: Touchstone v0.1.21 has capability HealthCheck with ping(). Gemstone v0.4.11 + SunStat v0.1.7 have lastActivity attribute hooked into all successful API response paths. New reusable pattern documented in .squad/skills/hubitat-healthcheck-vs-lastactivity/SKILL.md.

---

## ⚠️ USER DIRECTIVE — NO JVM-BASED TESTING FRAMEWORKS

**By:** Mads Kristensen (via Copilot)  
**Date:** 2026-05-17  
**Status:** ACTIVE — supersedes all prior intent to add JVM testing

### What

Do not add JVM-based unit testing frameworks (Gradle, Maven, Spock + .jar dependencies) to the hubitat-drivers repo. 

**History:** Tank-14 (Spock unit test POC) was aborted mid-flight. Spock harness (~36 tests passing) was nearly complete, but Tank had already downloaded a full Gradle distribution (~150MB) plus dependencies — totaling 247MB in `tests/` — before Mads pulled the plug. Commit 8a3334a was immediately reverted via cab38d9.

### Why

The repo's value proposition is **single-file `.groovy` drivers that drop into Hubitat** — adding a parallel JVM build environment violates that simplicity. Drivers should remain lightweight and dependency-free.

### Future Testing

If testing is revisited:
- ✅ **Acceptable:** Standalone Groovy scripts that run with the system `groovy` interpreter (no Gradle wrapper, no jar deps beyond what Groovy bundles)
- ❌ **Not acceptable:** External build systems, wrapper downloads, jar dependencies
- **Recommended:** TESTING.md hardware-validation plans owned by Switch (real device testing is the primary validation strategy)

---

## Tank-14 Session — Spock Unit Test POC (ABORTED + REVERTED)

**Date:** 2026-05-17  
**Agent:** Tank (claude-sonnet-4.6, background mode)  
**Duration:** 699 seconds (before abort)  
**Status:** ABORTED + REVERTED  
**Revert commit:** cab38d9 (force-reverted entire Spock commit 8a3334a)

### What Happened

Tank was building a Spock + Gradle unit testing harness for the hubitat-drivers repo:
- ~36 unit tests passing
- Gradle wrapper + dependencies downloaded (~247MB total in `tests/`)
- Commit 8a3334a ready for merge

User (Mads) demanded immediate rollback: "too many dependencies and .jar files." Coordinator reverted the commit via cab38d9 and killed the Gradle daemon (PID 56616).

### Why It Was Aborted

The Gradle bootstrap + jar artifacts violated the repo's design principle: lightweight, single-file `.groovy` drivers with no build system dependencies. The 247MB downloads made the repository heavy for a simple driver distribution model.

### Captured Directive

This abort event triggered the USER DIRECTIVE above: no JVM-based testing frameworks in this repo.

---

# Decision Record — Touchstone v0.1.18: Persistent Socket Architecture

**By:** Tank
**Date:** 2026-05-17
**Status:** Shipped — pending Switch hardware validation

---

## Context

Prior to v0.1.18, the Touchstone driver used an open→send→wait 2s→close pattern for every Tuya command. This meant the socket was idle between polls, so spontaneous push frames emitted by the device (e.g., physical remote presses) were never received. Hubitat dashboards showed stale state between 60-second poll cycles.

---

## Decision: Replace ephemeral socket with persistent long-lived connection

### What changed

1. **Socket lifecycle:** `initialize()` calls `runIn(1, "openSocket")` which calls `interfaces.rawSocket.connect()` and never closes. Socket stays open indefinitely.
2. **Heartbeat:** `sendHeartbeat()` fires every 10 s via self-rescheduling `runIn(HEARTBEAT_INTERVAL_SECONDS, "sendHeartbeat")`. Tuya devices time out idle connections after ~30 s.
3. **Reconnect:** `scheduleReconnect()` / `reconnectSocket()` with backoff [5 s, 30 s, 60 s, 300 s]. Resets on successful open.
4. **Intentional close suppression:** `closeSocket()` stamps `state.intentionalCloseAt = now()`. `socketStatus()` checks `(now() - intentionalAt) < 3000L` before triggering reconnect. Avoids spurious reconnect loops on `updated()` and `uninstalled()`.
5. **Push frame handling:** No new code needed — existing `parse()` → `processFrame()` → `applyDps()` handles spontaneous STATUS frames (cmd 8) identically to polled responses.
6. **pumpQueue:** Checks `state.socketOpen != true` and bails early; queued writes drain after reconnect via `retryPendingRequests()`.
7. **responseTimeout:** No longer closes the socket — just requeues and retries via `scheduleRetry()`.
8. **socketState attribute:** New `enum ["open","closed","reconnecting","error"]` — surfaces at info level, visible on dashboards.

### Heartbeat frame format

Tuya cmd 9 (HEART_BEAT) must have **truly empty payload** — zero bytes, no encryption. Added special case in `encryptTuyaPayload()`:
```groovy
if (cmd == TUYA_CMD_HEARTBEAT) {
    return new byte[0]
}
```
Without this, AES-PKCS5 of empty input produces 16 bytes of padding, which is not a valid Tuya heartbeat and may cause device-side errors.

### Poll interval

Default changed from 60 s → 300 s. Push frames now carry live state; polling is a safety net only.

---

## Risks / open items for Switch

- **Single TCP slot:** Tuya v3.3 devices allow only one connection at a time. If Smart Life app is open on the same LAN, the persistent socket cannot connect. The driver retries with backoff — same behavior as before, but now more visible via `socketState = reconnecting`.
- **Push frame correlation:** The driver cannot distinguish "response to my write" from "push from remote" at the protocol level (both arrive as cmd 8). It processes both via `applyDps()`. If a push frame arrives during an in-flight write, `awaitingResponse` is cleared early. This is acceptable — the write already happened; the next refresh reconciles if needed.
- **Heartbeat format:** The reference bytes match the TinyTuya heartbeat. If the Sideline Elite firmware rejects cmd 9, Switch should look for `[Touchstone] Heartbeat failed` in logs and report back.
- **readDelay: 150:** Kept from original. May need tuning if push frames are being missed.

---

## Alternatives considered

- **runEvery1Minute for heartbeat:** Hubitat only supports 1-minute minimum for most `runEvery*` helpers. 10 s requires `runIn()` rescheduled inside the handler. Accepted approach.
- **pauseExecution(600) in updated():** Rejected in favor of `runIn(1, "openSocket")` which gives OS 1 s to release the port without blocking the hub event thread.
- **Correlated response tracking via seq numbers:** Deferred. The driver already uses seq numbers in frame headers, but doesn't verify that response seq matches request seq. Acceptable for now — push vs response ambiguity is benign.
---

# Decision: DP 105 (Log Brightness) Non-Writable on Sideline Elite — Command Removed

**Date:** 2026-05-17  
**Author:** Tank (Driver Developer)  
**Driver version:** v0.1.11

## Finding

Mads tested `setLogBrightness("12")` directly from the Hubitat device page on the real
Touchstone Sideline Elite. The fireplace logs did not respond. This was a direct call to the
named command's send path — it bypassed `setRawDP` and its known coercion bug, refuting the
earlier hypothesis that the failure was purely a type-coercion issue.

Additionally, Cypher confirmed in the decisions log that DP 105 is string-typed per the
device YAML, so a string → integer coercion issue in `setRawDP` would not have explained
the named-command failure anyway.

**Conclusion:** DP 105 is read-only or unimplemented on the Sideline Elite firmware. Writes
are silently dropped regardless of value type or send path.

## Action Taken

- Removed `setLogBrightness` command from driver (v0.1.11)
- Removed `logBrightness` attribute from driver metadata
- Removed `defaultLogBrightness` power-on default preference
- Removed `LOG_BRIGHTNESS_OPTIONS` constant
- Removed `logBrightness: 105` from `SIDELINE_PROFILE_DPS`
- DP 105 inbound status updates are now silently absorbed at debug level only (the device
  does appear to send DP 105 in status responses, but the value is not actionable)

## Pending

The actual write target for log/ember brightness control on the Sideline Elite is unknown.
DP 109 is under separate investigation by Cypher (ember brightness write target). If a
confirmed writable DP is identified, the command may be re-added with the correct DP number.

Do NOT re-add `setLogBrightness` pointed at DP 105 without hardware-confirmed write evidence.
---

# Decision: Touchstone DP 104 — Rename to "Charcoal Color" with Verified Labels

**Date:** 2026-05-17  
**Driver:** Touchstone / Tuya Fireplace  
**Version:** v0.1.17  
**Author:** Tank (Driver Developer)

## Context

The Touchstone Sideline Elite fireplace exposes DP 104 as an ember/log color picker with 12 palette slots. The driver historically called this feature "Log Color" (`setLogColor`, `logColor` attribute, `defaultLogColor` preference). This terminology was a guess.

Mads Kristensen supplied a second Tuya app screenshot showing the actual label the Tuya app uses for this control: **"Charcoal"** (or "Charcoal Color"). This confirmed the internal name was wrong.

## Decision

Rename all driver references from "Log Color" to "Charcoal Color":

| Old | New |
|-----|-----|
| `setLogColor(number)` | `setCharcoalColor("LabelName")` |
| `attribute "logColor"` | `attribute "charcoalColor"` |
| `defaultLogColor` preference | `defaultCharcoalColor` preference |
| `LOG_COLOR_OPTIONS` (numeric strings) | `CHARCOAL_COLOR_OPTIONS` + lookup maps |

## Authoritative Label Mapping (DP 104)

Verified from Tuya app palette picker in app order (left-to-right, top-to-bottom):

| DP value | Label       | Notes |
|----------|-------------|-------|
| "1"      | Orange      | Default selected in app |
| "2"      | Red         | |
| "3"      | Blue        | |
| "4"      | Yellow      | |
| "5"      | Green       | |
| "6"      | Purple      | |
| "7"      | Cyan        | |
| "8"      | Magenta     | |
| "9"      | White       | |
| "10"     | Pink        | |
| "11"     | Rainbow     | 8-segment multi-color pie chart |
| "12"     | Spotlight   | ⚠️ Best-guess — mostly-white circle with small orange wedge in app |

## Breaking Change

This is a **breaking rename**. No backward-compat alias was added. Existing Rule Machine automations using `setLogColor(N)` must be migrated to `setCharcoalColor("LabelName")`.

Existing `defaultLogColor` numeric preferences (saved from v0.1.14) will not match new label strings and are silently skipped on the next power-on. Users must re-select from the new ENUM dropdown.

## Rationale

- Naming commands to match the Tuya app reduces confusion when users cross-reference the app and the Hubitat driver.
- Converting from NUMBER to named ENUM prevents invalid inputs and provides a user-friendly dropdown in the Hubitat UI.
- "Spotlight" is acknowledged as a best-guess label; it will be updated if the real Tuya app label is supplied.
- No alias was added because the old `setLogColor(number)` signature is incompatible with the new `setCharcoalColor(string)` signature.
---

# Tank Decisions — Gemstone v0.4.10 Multi-Zone

**By:** Tank  
**Date:** 2026-05-17  
**Driver:** `drivers/gemstone-lights/gemstone-lights.groovy`  
**Version shipped:** 0.4.10

---

## Decision 1: Architecture — Option A-lite adopted as specified

Implemented Cypher's recommended Option A-lite (multi-instance with named controller preference). No parent/child, no new endpoints. One Hubitat device per Gemstone zone, each with its own `controllerName` preference.

**Rationale:** Zero architecture changes. Backward compatible. Hubitat users already manage multiple devices natively. Rule Machine can group them trivially.

---

## Decision 2: `controllerName` preference placement

Added as the **last** preference field (after `txtEnable`). All required/functional prefs come first; `controllerName` is optional and advanced, so it belongs at the bottom of the preferences block.

---

## Decision 3: Graceful degradation on no-match

When `controllerName` is set but no controller matches: `log.warn` with the available names list, then fall back to `devices[0]`. The driver continues to function (does NOT leave `state.deviceId` unbound). This is better than failing silently or throwing an error.

---

## Decision 4: Suppress multi-controller warning when `controllerName` is set

The pre-v0.4.10 warning ("Multiple Gemstone controllers were found — using first") was noise when the user intentionally has multiple controllers. v0.4.10 suppresses it when `controllerName` is non-blank and emits it only when `controllerName` is blank (user hasn't configured multi-zone yet).

---

## Decision 5: `state.availableControllers` as diagnostic state (not attribute)

Stored in `state` (not as a device attribute). This keeps it out of the primary device tile UI while still being readable from the device page's **State Variables** section. Sorted alphabetically, comma-joined for readability.

---

## Decision 6: `USER_AGENT` synced to v0.4.10

The USER_AGENT string was stale at `0.4.8` across multiple versions. Synced to `0.4.10`. Comment "keep in sync with DRIVER_VERSION" retained.

---

## Open items for Switch (hardware verification)

1. Confirm `devices.size() > 1` for Mads's account from `GET /homegroup/devices`
2. Verify `controllerName = "Eaves"` binds to the Eaves controller and that commands route there
3. Verify graceful degradation: `controllerName = "Nonexistent"` → warn fires with name list, driver continues on fallback
4. Verify two Hubitat devices with different `controllerName` values operate independently without interference
5. Check `state.availableControllers` is populated after first successful auth+discovery

See `drivers/gemstone-lights/TESTING.md` Tests 19–22 for the full test plan.
---

# Gemstone Zones / Segments — API Feasibility

**By:** Cypher  
**Date:** 2026-05-17  
**Requested by:** Mads Kristensen  
**Status:** Ready for Tank

---

## 1. Verdict

✅ **Feasible — proceed.**

The Gemstone cloud REST API fully supports independent per-zone control. The key finding: Gemstone's "zones" are **multiple physical controllers**, each with its own `deviceId`, targetable via the same API the driver already uses. No new endpoints are required. The primary driver change is relaxing the current "pick first device, ignore the rest" behavior and letting the user select which controller(s) to target.

---

## 2. Reference Implementation Found

| Repo | URL | What it covers |
|------|-----|----------------|
| `sslivins/pygemstone` | https://github.com/sslivins/pygemstone | Low-level async client; all REST endpoints, all payload models, Cognito SRP auth |
| `sslivins/hass-gemstone` | https://github.com/sslivins/hass-gemstone | HA integration layer; shows multi-device discovery and one-entity-per-controller pattern |

Both repos updated **~1 day ago** at time of research. pygemstone README explicitly states the endpoint catalogue was derived from a `mitmproxy --mode wireguard` capture of the official iOS app (`com.gemstone.lights`, v0.6.03).

---

## 3. Protocol Spec

### Auth (unchanged from current driver)

Cognito USER_PASSWORD_AUTH, same as v0.4.9. No changes for zone support.

```
POST https://cognito-idp.us-west-2.amazonaws.com/
Content-Type: application/x-amz-json-1.1
X-Amz-Target: AWSCognitoIdentityProviderService.InitiateAuth

{ "AuthFlow": "USER_PASSWORD_AUTH",
  "ClientId": "2647t144niotrl53vvru0ivno7",
  "AuthParameters": { "USERNAME": "<email>", "PASSWORD": "<password>" } }
```

Source: `pygemstone/src/pygemstone/const.py`

---

### Device Discovery

Returns all physical controllers in a homegroup. This is where "zones" live.

```
GET https://mytpybpq12.execute-api.us-west-2.amazonaws.com/prod/homegroup/devices
    ?homegroupId=<homegroupId>
Authorization: Bearer <accessToken>

Response:
{
  "data": [
    { "id": "<uuid>", "name": "Front of House", "homegroupId": "<uuid>",
      "firmware": "...", "disconnectReason": null, "lastUpdatedAt": 1716000000 },
    { "id": "<uuid>", "name": "Eaves",           "homegroupId": "<uuid>", ... },
    { "id": "<uuid>", "name": "Soffit",          "homegroupId": "<uuid>", ... }
  ]
}
```

Source: `pygemstone/src/pygemstone/client.py:devices()`, `models.py:Device.from_api()`

The current driver already calls this endpoint (`/homegroup/devices`, action `discoverDevices`), but discards all entries after `devices[0]` — line 1288-1289 of the Groovy driver. It logs a warning when multiple are found.

---

### Get Device State (per zone)

```
GET /deviceControl/currentlyPlaying?deviceOrGroupId=<deviceId>
Authorization: Bearer <accessToken>

Response:
{
  "data": {
    "id": "<deviceId>",
    "onState": true,
    "pattern": {
      "id": "<uuid>", "name": "Pulse",
      "colors": [4294902015],        // ABGR unsigned 32-bit ints
      "animation": "motionless",
      "brightness": 200,
      "speed": 128,
      "direction": 0,
      "backgroundColor": 0,
      "referencePatternId": "<uuid-or-null>"
    },
    "lastUpdatedAt": 1716000000
  }
}
```

Source: `pygemstone/src/pygemstone/client.py:device_state()`, `models.py:DeviceState.from_api()`

---

### Turn On/Off (per zone)

```
PUT /deviceControl/onState?deviceOrGroupId=<deviceId>
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "onState": true }

Response:
{ "data": { "txId": "<uuid>" } }
```

Source: `pygemstone/src/pygemstone/client.py:set_on_state()`

---

### Play Pattern (per zone)

```
PUT /deviceControl/play/pattern?deviceOrGroupId=<deviceId>
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "pattern": {
    "id": "<uuid>",          // must be real UUID from a prior GET; do NOT synthesize
    "name": "Hubitat Solid Color",
    "colors": [4278190335],  // ABGR unsigned 32-bit, alpha=0xFF always
    "animation": "motionless",
    "brightness": 200,        // 0-255 wire scale
    "speed": 128,
    "direction": 0,
    "backgroundColor": 0
    // omit referencePatternId entirely (do not send null)
  }
}

Response:
{ "data": { "txId": "<uuid>" } }
```

Source: `pygemstone/src/pygemstone/client.py:play_pattern()`, `models.py:Pattern.to_api()`

The `deviceOrGroupId` query param is the **only** thing that changes when targeting a different zone. All other payload fields are identical.

---

### Device Groups (multi-controller targeting — unconfirmed schema)

```
GET /deviceGroup/list?homegroupId=<homegroupId>
Authorization: Bearer <accessToken>

Response: { "data": [] }   // capture saw empty list; schema unknown
```

Source: `pygemstone/src/pygemstone/client.py:device_groups()` — docstring explicitly notes: *"The capture saw an empty list — the per-group schema is not yet known — so raw dicts are returned."*

The `deviceOrGroupId` param naming implies group IDs from this endpoint can be passed to control endpoints, allowing one command to fan out across multiple physical controllers. **But this is speculative from the param name only; no group response payload has been captured.**

---

## 4. Zone Model

### What a "zone" is

A Gemstone "zone" is a **physical controller** — a separate hardware unit with its own LED string and its own cloud `id`. It is a server-side entity created when the user pairs a controller with the Gemstone mobile app.

There is **no per-pixel segmentation** within a single controller exposed by the cloud API. The `Pattern.colors` array is a list of palette colors, not per-pixel addresses.

### What a "device group" is

A device group (`/deviceGroup/list`) is a user-defined grouping of multiple physical controllers. Commanding a group via `deviceOrGroupId=<groupId>` appears to fan out the command to all member controllers simultaneously. The schema is not confirmed from any non-empty capture.

### Persistence

Zones (devices) are server-side, stable, UUID-keyed, created in the Gemstone mobile app. They don't change without the user explicitly renaming or unpairing a controller. The device `id` is a UUID; stable for the lifetime of the pairing.

### Discovery flow

1. `GET /homegroup/list` → list of homegroup IDs
2. For each homegroup: `GET /homegroup/devices?homegroupId=<id>` → list of Device records with id + name
3. Store all `{id, name}` pairs

The current Hubitat driver already performs steps 1–2 but discards all but the first device.

---

## 5. Recommended Architecture

### **Recommended: Option A-lite — Multi-instance with named controller preference**

Full parent/child adds Groovy complexity (addChildDevice, parent-child state passing, child component drivers) without meaningful benefit for the typical 2–4 controller case. The simpler path achieves the same result:

**Add a `controllerName` preference to the existing driver.**  
When `controllerName` is blank → keep current behavior (bind to first controller found — preserves backward compatibility).  
When `controllerName` is a non-blank string → after discovery, bind to the controller whose `name` matches (case-insensitive). If not found, log which controllers were discovered and fall back to first-found.

**User creates one Hubitat device per Gemstone zone:**
- Device 1 `controllerName = "Front of House"` → binds to that device's UUID
- Device 2 `controllerName = "Eaves"` → binds to that device's UUID
- Device 3 `controllerName = "Soffit"` → binds to that device's UUID

Each Hubitat device independently authenticates (shared Cognito client ID, separate token cache), independently polls, independently manages its effect catalog. No coordination needed between devices — each is fully self-contained.

**Why this beats true parent/child for v1:**
- Zero architecture changes — same single-driver model
- Backward compatible (blank `controllerName` = current behavior)
- M drivers means M auth flows, but Cognito token refresh is cheap and Gemstone doesn't impose per-token rate limits in the API
- Hubitat users already manage multiple devices; Rule Machine can group them trivially

### Option B — True parent/child

Parent driver: auth only, discovery, stores full `state.deviceList`. Creates child devices via `addChildDevice()`. Children share nothing (Hubitat driver isolation means each child still needs its own token store or the parent must call a `setToken()` method on each child). Correct for large installs (>6 controllers) but over-engineered for the typical 2–4 zone case.

### Option C — Scene-only (`setZoneColor(zoneId, hue, sat)`)

Requires exposing device IDs to users, which are opaque UUIDs. Poor UX. Discard.

---

## 6. Implementation Work Breakdown (for Tank)

**Dependency order:**

1. **[S] Add `controllerName` preference** — `input name: "controllerName", type: "text", title: "Controller name"`. Empty = current first-device behavior.

2. **[S] Update `handleDevicesResponse()`** — after receiving the device list, if `controllerName` is set, find the first device whose `name` case-insensitively equals the preference. If not found, log the available names and fall back to first device (with a `log.warn`). Assign `state.deviceId` and `state.deviceName` as before.

3. **[S] Update authStatus message** — already shows `state.deviceName`, which is the selected controller's name. No change needed; users will see `"Authenticated: Eaves"` etc. already.

4. **[S] Backward-compatibility guard** — when `controllerName` is blank/null, `handleDevicesResponse()` must continue to pick `devices[0]` and the warn-multiple log must be adjusted to only fire when `controllerName` is blank (otherwise multiple devices found is expected).

5. **[M] Update README** — document the `controllerName` preference and the multi-instance pattern for zone control. Add a "Multiple zones / controllers" section.

6. **[S] (Optional) Add `availableControllers` attribute** — after discovery, set `state.availableControllers = device names joined by comma`. Useful for debugging which names are available. Not user-facing in the main UI.

**Total estimate:** S-M overall. No new API calls. No new driver architecture. The biggest risk is the case-insensitive name-match logic being fragile against leading/trailing spaces or emoji in Gemstone controller names — sanitize both sides with `.trim().toLowerCase()`.

---

## 7. Unknowns — What Switch Must Verify on Real Hardware

1. **Multiple controller accounts confirmed?** — Switch should confirm Mads's account has `devices.size() > 1` returned from `GET /homegroup/devices`. The current driver logs `"Multiple Gemstone controllers were found..."` if so.

2. **Device group schema** — Is `GET /deviceGroup/list?homegroupId=<id>` ever non-empty? If so, what fields does it return? Can a `groupId` actually be passed as `deviceOrGroupId` to control all member controllers simultaneously? If Mads has configured groups in the app, this endpoint would return data. **Capturing this response would unlock multi-zone simultaneous control** (one command, all zones together — useful for "all on/off" without N separate calls).

3. **Controller naming stability** — Are controller names guaranteed unique per homegroup? The API returns an `id` (UUID) + `name`, but there's no uniqueness constraint evident in the schema. If two controllers have the same name, the `controllerName` preference match would be ambiguous; should fall back to an index preference or UUID preference as an advanced option.

4. **Per-zone effect catalog** — Does each physical controller support the same full effect/pattern catalog? (Likely yes — patterns are account-level, not controller-level — but confirm.)

5. **Polling rate per device** — With 3 Hubitat devices each polling at 5-minute intervals, that's 3× the cloud requests. Gemstone doesn't publish rate limits. Two iOS app captures showed no rate-limit responses; 30s polling in HA was fine. 5-minute intervals per device should be well within any undocumented budget.

---

## 8. Sources

| Claim | Source |
|-------|--------|
| `deviceOrGroupId` query param used by all control endpoints | `pygemstone/src/pygemstone/client.py:set_on_state()`, `device_state()`, `play_pattern()` |
| Device schema (id, name, homegroupId, firmware) | `pygemstone/src/pygemstone/models.py:Device.from_api()` |
| Pattern schema (colors=ABGR list, no pixel-range fields) | `pygemstone/src/pygemstone/models.py:Pattern.from_api()` |
| `device_groups()` returns empty list, schema unknown | `pygemstone/src/pygemstone/client.py:device_groups()` docstring |
| hass-gemstone creates one HA entity per physical controller | `hass-gemstone/custom_components/gemstone/__init__.py:async_setup_entry()` lines enumerating all devices across all homegroups |
| Current Hubitat driver discards all but first device | `drivers/gemstone-lights/gemstone-lights.groovy:handleDevicesResponse()` lines 1284-1289 |
| `buildApiParams()` sets `query.deviceOrGroupId = state.deviceId` | `drivers/gemstone-lights/gemstone-lights.groovy` line 1619 |
| iOS app capture source for endpoints | `pygemstone/src/pygemstone/const.py` header comment |
---

## 2026-05-17T16:34:52-07:00 — Cypher — DP 105 / DP 109 real-hardware investigation

**By:** Cypher

**Status:** Root cause identified; pending Mads empirical test; Tank to ship v0.1.10 fixes

### What

Compound investigation into why DP 105 (log brightness) and DP 109 (ember brightness) don't respond to `setRawDP` writes on Mads's Touchstone Sideline Elite real hardware.

### Root cause (Confirmed — Hypothesis B)

**`setRawDP` type coercion corrupts string-typed DPs with numeric-looking values.**

The driver's `coerceRawValue()` function converts numeric-looking strings to integers:
```groovy
coerceRawValue("5")  // → Integer 5, not String "5"
```

- **DP 105 (log brightness):** YAML declares `type: string` with wire values `"1"` through `"12"` (quoted strings). `setRawDP 105 "5"` sends integer `5` but device expects string `"5"`.
- **DP 109 (ember brightness):** YAML declares `type: string`, `optional: true` with wire values `"L0"` through `"L5"` (capital-L prefix). `setRawDP 109 "1"` sends integer `1` and wrong value format; correct format is `"L1"`, `"L2"`, etc.

**The setRawDP command documentation explicitly warns:** *"whole numbers become integers"*. Using `setRawDP` to test string-typed DPs is therefore invalid.

### Resolution path

**DP 105 — Still unresolved whether truly read-only:**
- `setLogBrightness("12")` (dedicated command) sends correct string type per YAML and is **untested on real hardware** — may actually work
- Mads must test `setLogBrightness("12")` directly from device page (NOT via setRawDP)

**DP 109 — No dedicated driver command exists:**
- No driver code tracks inbound DP 109 status (no `dp109` attribute)
- `setRawDP 109 "L1"` sends correct type + value (L-prefix strings not coerced to integers) — untested
- May be optional on some firmware variants (`optional: true` in YAML)

**Tank v0.1.10 fixes (regardless of empirical test outcome):**
1. Add `setRawDPString` command to skip `coerceRawValue` for string-typed DPs (or quoted-string syntax for setRawDP)
2. Add `setEmberBrightness` command with "L0"–"L5" enum constraints for DP 109
3. Add `dp109` inbound attribute for status tracking
4. If `setLogBrightness` also fails empirically: remove/deprecate DP 105 write command and document as read-only

### Verification test (Mads to run before Tank commits v0.1.10)

1. Turn fireplace on (fire must be active)
2. Call `setLogBrightness("12")` from device page — watch for visible log brightness change
3. Call `setRawDP 109 "L3"` from device page — watch for visible ember brightness change
4. Report results to team

### Recommendation

Confirmed Type-Coercion Bug (Hypothesis B) must ship as v0.1.10 regardless of Hypothesis C outcome. Pending Mads's empirical test to determine if DP 105/DP 109 are truly read-only or just untested write paths.

---

## 2026-05-17T13:24:30-07:00 — Directive — Scribe must push after every commit

**By:** Mads (via Copilot)

### What changed

- After every successful `git commit`, Scribe must also push before ending the task.
- On `main`, run `git push origin main`.
- On another branch, push the current branch (`git push -u origin <branch>` the first time).
- If no commit was made, no push is required.

### Why

- This repo's release and delivery automation is push-driven, so local-only commits can silently block GitHub workflows and release/tag creation.
- That gap already happened here: commits existed locally on `main`, but GitHub never saw them, so downstream automation did not fire.

### Failure handling

- If push fails because of auth, non-fast-forward, or branch protection, report it immediately.
- Do not silently skip the push, auto-rebase, or force-push.

---

## 2026-05-17T12:22:15-07:00 — Tank — Touchstone v0.1.5 paragraph() fix

**Requested by:** Mads

### What changed

- Removed the `paragraph` header from the `preferences {}` block in `drivers/touchstone-fireplace/touchstone-fireplace.groovy`.
- Moved the power-on-defaults explanation into the affected `input` descriptions for `defaultFlameColor`, `defaultFlameBrightness`, `defaultLogColor`, and `defaultHeatingSetpoint`.
- Bumped the driver header/version/user-agent stamp to `v0.1.5` and added the explicit bugfix note for the Hubitat driver preference allowlist issue.

### App-only preference UI audit result

- Audited the full driver for app-only preference constructs: `paragraph`, `section`, `href`, `app`, `mode`, and `pageDefault`.
- Found one executable hit: the single `paragraph` block that labeled the optional power-on defaults group.
- Found no `section`, `href`, `app`, `mode`, or `pageDefault` constructs in the driver.

### Hubitat sandbox families (consolidated)

1. **Import allowlist** — Hubitat blocks imports like `java.util.zip.*`, `ByteArrayOutputStream`, and much of `java.io.*`, `java.nio.*`, and `java.security.*`; prefer pure-Groovy or already-verified allowed equivalents.
2. **Reflection blocked** — Hubitat blocks `.getClass()`, instance `.class`, `.metaClass`, `.getMethods()`, `.respondsTo()`, `.hasProperty()`, `Class.forName()`, and sometimes method-pointer `&`; drop reflection or restructure with explicit code paths.
3. **App-only preference UI** — Hubitat drivers should use only `input` with driver-supported types; app UI helpers like `paragraph`, `section`, `href`, `app`, `mode`, and `pageDefault` are not allowed in drivers and should be replaced by `description:` text on each `input`.

---

## 2026-05-17T12:22:15-07:00 — Link — Touchstone v0.1.5 docs bump

**Summary:** Tank shipped v0.1.5 (removed `paragraph()` from preferences block per Hubitat sandbox app-only restrictions). Documentation updated to match driver code.

### Files Modified

- `drivers/touchstone-fireplace/packageManifest.json` — version bumped 0.1.4 → 0.1.5
- `drivers/touchstone-fireplace/README.md` — version bumps + changelog + troubleshooting

### Changes

1. **packageManifest.json:** Root `version` and `drivers[0].version` both set to 0.1.5; `dateReleased` remains 2026-05-17
2. **README.md Status header:** v0.1.4 → v0.1.5
3. **README.md Latest section:** Updated to describe bugfix (removed app-only `paragraph()` from preferences block; no behavior changes; moved text to field descriptions)
4. **README.md Troubleshooting (CRC32 entry):** Version anchor updated to "v0.1.5 or later"
5. **README.md Troubleshooting (Reflection entry):** Version anchor updated to "v0.1.5 or later"
6. **README.md Troubleshooting (NEW):** Added "No signature of method: Script1.paragraph()" entry → points users to v0.1.4 v0.1.5+ update
7. **README.md Changelog:** v0.1.5 entry added at top: "BUGFIX — removed `paragraph()` from preferences block (Hubitat driver allowlist; app-only construct). No behavior changes; previous defaults UI text moved into per-field descriptions."

### Rationale

- Fast-follow patch; version anchors in troubleshooting now point to v0.1.5 so users can self-identify which version they're on
- New `paragraph()` error is the first public user symptom for this restriction family; added as standalone entry so users searching error logs can find it
- Changelog entry documents the Hubitat sandbox restriction (app-only UI) for future reference — completes the three-family sandbox pattern (imports, reflection, app-only UI)

### Verification

- README structure intact; no section reorganization
- Version bumped consistently in both manifests and refs
- Changelog and troubleshooting entries follow established patterns

**Decision:** Accept. Docs ready for v0.1.5 release.

---

## 2026-05-17T11:58:55-07:00 — Touchstone v0.1.4 — safety + sandbox fixes

**By:** Tank  
**Requested by:** Mads

**Exactly removed / changed in drivers/touchstone-fireplace/touchstone-fireplace.groovy:**
1. Removed the defaultHeatLevel preference input from the power-on defaults block.
2. Removed the defaultHeatLevel auto-apply branch from pplyOnPowerOnDefaults(), so heater DP 5 is never written from implicit power-on/default logic.
3. Added an inline SAFETY comment in pplyOnPowerOnDefaults() stating that heater state changes only happen through explicit setHeatLevel() user commands.
4. Kept defaultFlameColor, defaultFlameBrightness, defaultLogColor, and defaultHeatingSetpoint as the only auto-applied defaults.
5. Removed the sandbox-blocked reflection log from parse() (original v0.1.3 line 449: .getClass().getName()).
6. Removed the second executable .getClass() usage in dpValueType() and replaced the fallback with a generic "object" type label.

**Reflection audit scope + result:**
- Scanned for 14 reflection-related patterns in the driver:
  - .getClass()
  - instance .class
  - .metaClass
  - .getMethods()
  - .getMethod()
  - .getDeclaredMethods()
  - .getFields()
  - .getField()
  - .getDeclaredFields()
  - .invoke()
  - .respondsTo()
  - .hasProperty()
  - Class.forName()
  - method-pointer syntax (someObj.&methodName)
- Found 2 executable hits:
  1. parse() diagnostic logging (.getClass().getName()) at original v0.1.3 line 449
  2. dpValueType() fallback type-name logging (alue.getClass().getSimpleName().toLowerCase())
- Confirmed no instance .class reads, no .metaClass, no method/field introspection calls, no Class.forName(), no 
espondsTo()/hasProperty(), and no method-pointer syntax in the driver.
- Remaining getClass text is comments/changelog only; no executable reflection calls remain.

---

## 2026-05-17T11:58:55-07:00 — Touchstone v0.1.3 — optional defaults applied on power-on

**By:** Tank (requested by Mads)

**What:**
- Added optional defaultFlameColor, defaultFlameBrightness, defaultLogColor, defaultHeatLevel, and defaultHeatingSetpoint preferences to drivers/touchstone-fireplace/touchstone-fireplace.groovy.
- on() still emits the switch/power events immediately and writes DP 1 right away, but now schedules pplyOnPowerOnDefaults() to queue any configured follow-up defaults asynchronously.
- Each default is independent: blank/unset preferences do nothing, so the fireplace keeps whatever value its firmware remembered.

**Delay choice:**
- Used 
unInMillis(1500, "applyOnPowerOnDefaults").
- Rationale: Touchstone's off→on transition has a short settle window, and DP 14 / Fahrenheit setpoint was previously observed to revert briefly during that window. A 1500 ms delay is a conservative first pass that keeps the UI snappy while giving the firmware a beat before follow-up writes.
- Follow-up writes still use the existing queued retry/backoff path, so Smart Life / Tuya single-client socket contention behavior is unchanged.

**Implementation notes / tradeoffs:**
- Flame/log/brightness default inputs are gated out when Device Profile = Generic Tuya Fireplace, since those roles are not mapped in Generic mode.
- off() and subsequent on() calls cancel any queued-but-unsent power-on default writes before adding new power requests, so the latest power toggle generally wins.
- Already in-flight writes cannot be recalled; in a very rapid off/on/off sequence, one last default write may still land before the later power command finishes draining through the Tuya queue.
- After defaults are queued, the driver forces a later refresh on the power-transition cadence rather than the shorter normal write cadence, favoring safer readback after the settle window.

**Note:** This version (v0.1.3) contained the unsafe defaultHeatLevel parameter. Immediately superseded by v0.1.4 safety hardening.

---

## 2026-05-17T11:58:55-07:00 — Documentation bump — Touchstone v0.1.4

**By:** Link (requested by Mads; decision pair to Tank's v0.1.4 code + safety fix)

**What:** Updated all Touchstone driver docs from v0.1.2 to v0.1.4.

**Files updated:**

1. drivers/touchstone-fireplace/packageManifest.json
   - ersion: 0.1.2 → 0.1.4
   - drivers[0].version: 0.1.2 → 0.1.4

2. drivers/touchstone-fireplace/README.md
   - Status header: updated to v0.1.4
   - Supported Capabilities: added "Optional power-on defaults" with safety callout
   - New "Power-on Defaults" section explaining defaultFlameColor, defaultFlameBrightness, defaultLogColor, defaultHeatingSetpoint; ~1.5s apply delay; Device Profile gating
   - New "Safety" subsection: explains why heater is intentionally NOT auto-startable (radiant heat fire/burn risk); states "no defaultHeatLevel preference and there never will be"
   - Troubleshooting: updated CRC32 entry version refs; added new entry for reflection error .getClass() (v0.1.3 bug, fixed v0.1.4)
   - Changelog: added v0.1.4 entry; clarified v0.1.2 as released; v0.1.3 omitted (never publicly released; buggy intermediate state)

**Key decision: v0.1.3 not listed in public changelog**

- v0.1.3 had critical issues: (1) heater could auto-start (safety), (2) sandbox reflection error in parse() exception handler
- v0.1.3 was never published to users; only v0.1.2 and v0.1.4 are "real" releases
- Changelog reflects only shipped versions; internal buggy states are omitted to avoid user confusion
- Troubleshooting entries point to version ranges where bugs existed, so users can self-identify and find fixes

**Pattern for hardware safety:**

When documenting drivers controlling hazardous hardware (heaters, locks, etc.), be explicit about safety-driven feature omissions:
- State the decision directly: "by design", "intentionally"
- Name the risk clearly: "radiant heat element — fire/burn risk"
- Explain trade-offs: hardware safety > convenience
- Direct users to auditable alternatives (explicit Hubitat Rules, not implicit/auto paths)

This pattern is applicable to future drivers controlling power-consuming or hazardous hardware.

---

## 2026-05-17T11:58:55-07:00 — User directive — Heater must never auto-start (safety)

**By:** Mads (via Copilot)

**What:** The Touchstone driver MUST NOT allow the heater (DP 5, heat_level) to come on automatically — neither via "default on power-on" settings, nor any other implicit/auto path. The heater is a physical safety device (radiant heat element); enabling it without explicit user action is a fire/burn risk.

**Why:** Mads said: *"don't make it so that the heater can come on automatically. that's probably a safety risk."* — captured as a hard scope rule for the team.

**Required immediate changes (v0.1.4 follow-up to v0.1.3 currently in flight):**
1. REMOVE the defaultHeatLevel preference input added in v0.1.3
2. REMOVE any code that writes DP 5 (heat_level) from pplyOnPowerOnDefaults() or any other auto-applied path
3. Keep defaultHeatingSetpoint (the temperature setpoint) — it's NOT the heater enable, just the target temp; the heater itself stays off until the user explicitly cycles it via setHeatLevel/the heater button
4. Add a defensive guard in any future code: heater state changes only via the explicit setHeatLevel(level) command (or an equivalent explicit user action). No implicit power-on or scene-triggered heater activation.
5. README: add a "Safety" section noting the design choice — driver intentionally does NOT auto-start the heater.

**Future-proofing rule:** If we ever add scenes, presets, schedules, or rules-engine integrations to this driver, those must NEVER toggle DP 5 implicitly. Only direct user command writes the heater DP.

---

## 2026-05-17T11:58:55-07:00 — Bug report — Hubitat sandbox rejects .getClass() at line 449

**By:** Mads (via Copilot)

**What:** Hubitat sandbox error on driver compile/run: Expression [MethodCallExpression] is not allowed: e.getClass() at line number 449

**Confirmed location** (coordinator viewed file at line 449):
\\\groovy
447:     } catch (Exception e) {
448:         log.warn "[Touchstone] parse() failed — \"
449:         debugLog "parse() exception class=\"
450:     }
\\\

**Why this fails:** Hubitat's Groovy sandbox blocks reflection-style calls (.getClass(), .getMethods(), .getFields(), anything that inspects type metadata at runtime). It's part of the platform's strict security model — same family as the import allowlist.

**Fix (Tank v0.1.4):**
1. At line 449, drop the .getClass().getName() reflection. Replacements that work in Hubitat:
   - **Simplest:** delete the line entirely — .message on line 448 already conveys what happened
   - **Or** use the exception's class name via Throwable.class.simpleName pattern? NO — .class access on instances is also reflection. Don't.
   - **Or** catch typed exceptions separately if you really need to differentiate: catch (java.net.SocketTimeoutException e) { ... } catch (java.net.ConnectException e) { ... } catch (Exception e) { ... }. But parse() likely doesn't need this level of granularity.
2. **Audit the entire driver file** for other reflection patterns — .getClass(), .class on instance variables, .getMethods(), .invoke(), .metaClass, .respondsTo(), .hasProperty(). Hubitat blocks all of them. Replace with explicit code paths.
3. Bump skill SKILL.md 	uya-local-groovy to note: Hubitat blocks not just imports but also reflection-style method calls. Add to the "gotchas" list.

**Combined with:** the no-auto-heater directive (also captured this session). Both should land in Tank v0.1.4 once v0.1.3 finishes in flight.

---


# Decisions

## 2026-05-17T11:31:31-07:00 — Touchstone v0.1.2 CRC32 allowlist fix

**By:** Tank
**Requested by:** Mads

### What was forbidden

- Hubitat rejected the driver at install time because `import java.util.zip.CRC32` is not on the platform import allowlist.
- During the audit, `java.io.ByteArrayOutputStream` was also treated as risky for the same sandbox reason and removed proactively.

### Replacement strategy

- Replaced the `CRC32` object usage with a pure-Groovy table-driven `crc32(byte[] data)` helper using canonical CRC-32/ISO-HDLC settings:
  - reversed polynomial `0xEDB88320L`
  - init `0xFFFFFFFFL`
  - reflected byte updates
  - xor-out `0xFFFFFFFFL`
- Hoisted the 256-entry lookup table into `@Field static final long[] CRC32_TABLE` so the table is built once at driver load, not per frame.
- Replaced `ByteArrayOutputStream` frame assembly with a small `concatBytes()` helper plus manual byte-array composition so the Tuya `55AA ... CRC32 ... AA55` framing stays identical without depending on extra `java.io` classes.

### Import audit result

Remaining explicit imports in `drivers/touchstone-fireplace/touchstone-fireplace.groovy` after the fix:
- `groovy.transform.Field`
- `groovy.json.JsonOutput`
- `groovy.json.JsonSlurper`
- `javax.crypto.Cipher`
- `javax.crypto.spec.SecretKeySpec`

No `java.util.zip.*`, `java.nio.*`, or `java.io.*` imports remain in the driver file.

---

## 2026-05-17T11:24:33-07:00 — Touchstone v1.1 generalization pass

**By:** Tank
**Requested by:** Mads

### What changed

- Kept the file path stable at `drivers/touchstone-fireplace/touchstone-fireplace.groovy`, but renamed the driver metadata display name to **`Touchstone / Tuya Fireplace`** and updated the top comment header to frame the driver as **Touchstone Sideline Elite — and other Tuya WiFi fireplaces**.
- Added **Device Profile** handling with three modes:
  - `Sideline Elite (tested)` → hardcoded verified DP map
  - `Generic Tuya Fireplace` → only power, heat level, and temperature setpoint commands are wired
  - `Custom` → per-role DP number preferences for power / flame color / flame brightness / log color / heat level / temp setpoint F / temp setpoint C
- Added on-device discovery tooling: `discoverDPs()`, `captureBaseline()`, `captureDiff()`, and `setRawDP()` (while keeping `setDpRaw()` as a legacy alias).
- Moved DP resolution behind `dpFor(role)` so command paths and status parsing both honor the active profile at runtime.

### Tradeoffs

- The **Generic** profile intentionally refuses to guess flame/log/light mappings. Those commands warn and point users to **Custom** or `setRawDP()` instead of pretending the Sideline LED map is universal.
- `Custom` preferences are gated in the driver UI with `if (settings?.deviceProfile == "Custom")`, which means Hubitat only reveals those fields after the user saves/reopens the device page. The runtime helper still falls back to sane defaults so a fresh Custom selection does not explode.
- Discovery queries request a broader DP range for mapping work so non-Sideline devices can be explored from Hubitat without tinytuya/Python, while the existing socket/AES/retry/polling behavior stays intact.

### Still TODO

- **Link:** README walkthrough for the new discovery workflow and for other Touchstone / Tuya fireplace models.
- **Switch:** Expanded validation cases for `Generic Tuya Fireplace` and `Custom`, including `setRawDP()` audit logging and baseline/diff mapping flow.

---

# Touchstone Driver Documentation — Link Shipped

**Date:** 2026-05-17T18:31:31Z  
**Agent:** Link (DevRel / Documentation)  
**Status:** Complete  

## What Shipped

**Two new files:**

1. **`drivers/touchstone-fireplace/README.md`** — Per-driver user guide (18.2 KB)
2. **`drivers/touchstone-fireplace/packageManifest.json`** — HPM manifest

**Scope covered:**
- Device support matrix (Sideline Elite verified; other Touchstone lines + generic Tuya via Custom profile)
- Complete capability + command reference
- Installation (HPM stub + manual)
- Setup walkthrough: two-path Tuya local key extraction (Method A: iot.tuya.com + tinytuya durable; Method B: Home Assistant with caveat)
- Preferences reference with all driver settings
- **Key section:** "Got a Different Touchstone? Map It Yourself" — in-driver discovery walkthrough using `discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()` — no Python needed
- Known quirks: single TCP slot, temperature setpoint persistence, separate °F/°C tracking, remote buttons without Tuya equivalents
- Troubleshooting (offline, CRC32 import error, wrong DP responses, power transition window)
- Credits + changelog

## Key Decisions Documented

### Tuya Local Key Extraction Path

Per `.squad/decisions.md` (Cypher's 2026-05-17 Tuya Portal-Free audit), documented both extraction methods:

- **Method A (Primary):** iot.tuya.com portal + tinytuya Python wizard — durable, Tuya controls the outcome, no fragility from hardcoded client_ids
- **Method B (Alternative):** Home Assistant tuya-local cloud-auth — fast (~5 min) but relies on Tuya's HA-issued hardcoded client_id that Tuya can revoke unilaterally

README presents A as the "durable" standard; B as the "Home Assistant shortcut with caveat." Both link to `.squad/skills/tuya-cloud-key-extraction/SKILL.md` for deep-dives.

**Rationale:** Mads is already on Path A (iot.tuya.com); this documentation reflects that reality + arms users to make informed trade-off choices.

### Device Generalization Strategy

Per `.squad/decisions.md` (Mads's 2026-05-17 user directive):

- **Device Profile preference:** Selects Sideline Elite (default, verified), Generic Tuya Fireplace (basic control), or Custom (user-mapped)
- **Custom profile reveals DP number overrides** in preferences — users don't need to edit driver code
- **In-driver discovery commands** (`discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()`) enable users to self-discover DP maps for unknown models
- **Dedicated README section** ("Got a Different Touchstone? Map It Yourself") walks users through the discovery workflow step-by-step
- **Encourages community contribution:** "Open a GitHub Issue with your DP map so we can ship it as a preset"

**Rationale:** Eliminates the barrier for users with other Touchstone models; community can accumulate presets over time.

### HPM Manifest Format

Matched `.squad/decisions.md` DP map and existing repo patterns (Gemstone, SunStat):

- Single-driver manifest (SunStat is parent/child; Touchstone is single-file)
- UUID: 63f16ca9-2413-418f-a5d5-b798c23452ee (fresh UUID per driver)
- Version field in both root + drivers array (matches Gemstone convention)
- minimumHEVersion: "2.3.0" (matches other drivers)
- dateReleased: "2026-05-17" (per CURRENT_DATETIME directive)

## Conventions Adopted

From **gemstone-lights/README.md** + **sunstat-thermostat/README.md:**

| Pattern | Source | Adoption |
|---|---|---|
| **Header tagline + status line** | Both | ✓ Used; status line includes v0.1.1 + beta note |
| **Supported Devices table** | SunStat | ✓ Adapted for model/profile variants |
| **Capabilities + Attributes table** | Gemstone | ✓ Two-table layout (std + custom) |
| **Command Reference (table)** | Gemstone | ✓ Used; four sub-tables (Standard, Heating, Lighting, Discovery) |
| **Installation: HPM + Manual** | Both | ✓ Mirrored structure; HPM includes stub for future publish |
| **Setup with auth bootstrap walkthrough** | SunStat | ✓ Adapted for Tuya local key extraction (two methods, step-by-step) |
| **Preferences Reference table** | Gemstone | ✓ All prefs + defaults + one-line descriptions |
| **Known Quirks section** | Gemstone | ✓ Used; 4 quirks relevant to Tuya + Sideline Elite behavior |
| **Troubleshooting by symptom** | Gemstone | ✓ 4 troubleshooting paths (offline, CRC32, wrong DP, transition window) |
| **Credits + Changelog** | Gemstone | ✓ Credits: Tuya v3.3 sources, tinytuya, empirical mapping |
| **GitHub + Community links** | Gemstone | ✓ Footer sign-off |

## Open / Future

1. **HPM publish:** Not in scope; Mads will do separately
2. **Screenshots:** Optional nice-to-have (Hubitat device page preferences); not blocking
3. **Hubitat driver README skill:** `.squad/skills/hubitat-driver-readme/SKILL.md` — would standardize this pattern for future drivers; not yet created; consider as future documentation infrastructure task

## Quality Checklist

- ✅ All driver capabilities + commands documented
- ✅ All preferences with defaults + descriptions
- ✅ Installation (HPM stub + manual) clear
- ✅ Tuya local key extraction walkthrough (two methods, step-by-step)
- ✅ Device generalization via Discovery section (step-by-step; no Python needed)
- ✅ Known quirks from Switch's test plan + real-device findings
- ✅ Troubleshooting covers common user errors
- ✅ Credits acknowledge sources + contributors
- ✅ Changelog documents v0.1.1 + v0.1.0
- ✅ Conventions matched to repo style (Gemstone + SunStat)

---

**Next:** Mads reviews docs. If approved, docs are ready for HPM publish (separate step). If clarifications needed, Link can iterate in v0.1.2 README revision.

---

### 2026-05-17T11:31:31-07:00: Bug report from Mads (Touchstone driver install)

**By:** Mads (via Copilot)

**What:** Hubitat rejected the v1 Touchstone driver on install with: `Importing [java.util.zip.CRC32] is not allowed`

**Why captured:** Hubitat enforces a strict import allowlist (it's part of the platform security model — drivers run in a sandboxed Groovy environment). `java.util.zip.CRC32` is NOT on the allowlist, so any Tuya v3.3 driver using it for packet checksums will fail at compile time.

**Fix required (Tank v1.2):**
1. Remove `import java.util.zip.CRC32`
2. Implement CRC32 in pure Groovy as a private helper method. Use the standard polynomial 0xEDB88320 + 256-entry precomputed lookup table. Reference: kkossev's Tuya drivers (kkossev/Hubitat - their tuya local protocol files).
3. Verify every other `import` in the file against Hubitat's allowlist. Likely-safe imports for this driver: `hubitat.helper.HexUtils`, `groovy.transform.Field`, `javax.crypto.Cipher`, `javax.crypto.spec.SecretKeySpec`. If anything else (e.g., from `java.util.zip.*`, `java.nio.*` beyond ByteBuffer, `java.security.*` beyond MessageDigest) is in the file, swap it for an allowed equivalent or implement in-Groovy.
4. Smoke test by re-attempting install after the fix.

**Skill update:** Add "Hubitat import allowlist" to `.squad/skills/tuya-local-groovy/SKILL.md` — this gotcha applies to every Hubitat driver, not just Tuya.

---

## 2026-05-17T11:24:33-07:00: User-directed naming decision — "Option C"

**By:** Mads (via Copilot — coordinator decided per autopilot after surfacing trade-offs)

**What:** Touchstone driver positioning decision.

- **File path:** stays `drivers/touchstone-fireplace/touchstone-fireplace.groovy` (community SEO: Hubitat users will search for "Touchstone")
- **Driver display name** in `metadata { definition { name: ... } }`: change from `"Touchstone Sideline Elite"` to **`"Touchstone / Tuya Fireplace"`** — accurate framing in Hubitat's driver picker
- **README header:** "Touchstone Sideline Elite — and other Tuya WiFi fireplaces"
- **Device Profile preference** default: `Sideline Elite (tested)` with `Generic Tuya Fireplace` and `Custom` as secondary options

**Why:** Mads asked "is this more of a Tuya fireplace driver than a touchstone then?" — recognized that the driver is fundamentally Tuya v3.3 + a DP-map config, not a brand-locked driver. Option C threads the needle: community discoverability + honest scope + room for other Tuya WiFi fireplaces. Captured for Tank's v1.1 follow-up.

---

## 2026-05-17T11:10:56-07:00: User directive — Touchstone driver must be generalizable

**By:** Mads (via Copilot)

**What:** The Touchstone driver should work for other Touchstone fireplace models (Sideline Steel, Sideline Linear, Forte, Onyx, etc.), not just the Sideline Elite we just mapped. Users with other models cannot reasonably do manual DP discovery via tinytuya + Python — the driver itself must provide the discovery workflow.

**Why:** Mads asked "how can you make it work for other touchstone lines too if we can't verify the api the way we just did manually?" — captured as a scope/design directive for Tank's next pass.

**Required driver features (v1.1 or fold into v1 if Tank hasn't sealed the file):**

1. **Discovery commands on the device page:**
   - `discoverDPs()` — call `status()` and log the full DP dump (mimics `python -m tinytuya OutletDevice.status()`)
   - `captureBaseline()` — snapshot state
   - `captureDiff()` — compare to baseline, log which DPs changed; users press a remote button between the two
   - `setRawDP(dpId, value)` — write any DP directly so users can experiment with unmapped fields

2. **Preference-driven DP mapping:**
   - "Device Profile" dropdown: `Sideline Elite` (default, mapped), `Generic Tuya Fireplace` (DP 1/2/5 only), `Custom`
   - `Custom` mode reveals individual DP-number text inputs (`flameColorDp`, `logColorDp`, `flameBrightnessDp`, `heatLevelDp`, etc.)

3. **Universal safe defaults** (verified across Tuya ecosystem, not just this device):
   - DP 1 = power (bool)
   - DP 5 = mode/level (enum string "0"/"1"/"2")

4. **README guidance** (Link's task):
   - "Got a different Touchstone? Here's how to map it" section
   - Step-by-step using the driver's own discovery commands — no Python/tinytuya needed
   - Invite users to share their DP maps via GitHub Issues so presets accumulate over time

**Action items:**
- Tank: fold discovery commands + Device Profile preference into the scaffold if still in-flight, otherwise queue as immediate v1.1 follow-up
- Link: incorporate the "other models" walkthrough into the README

---

## 2026-05-17T11:07:22-07:00: Touchstone Sideline Elite — Real-Device Test Plan

**Date:** 2026-05-17T11:07:22-07:00  
**Author:** Switch (Tester / QA Engineer)  
**Status:** Ready for driver handoff (tank)  
**Target Device:** Touchstone Sideline Elite (Tuya v3.3, LAN port 6668)  
**Test Harness:** Mads (human runner; ~30 min smoke pass)  

[Test plan includes 19 tests covering: pre-flight, initialization, power control, heat levels, flame colors, log colors, brightness controls, temperature setpoint, refresh, state sync (app), state sync (remote), network recovery, device power recovery, app collision recovery, invalid enum values, out-of-range temperature, rapid command bursts, 1-hour stability, and cleanup. Full plan in Tank's test file.]

---

## 2026-05-17T11:07:22-07:00: Tank — Touchstone driver scaffold shipped

**By:** Tank

**What shipped:**
- Added `drivers/touchstone-fireplace/touchstone-fireplace.groovy` as a single-file Hubitat driver for the Touchstone Sideline Elite fireplace.
- Implemented Tuya Local v3.3 framing in Groovy: rawSocket TCP/6668, AES-128-ECB encryption/decryption, `55AA` packet framing, CRC32 validation, queued request handling, and defensive `parse()` buffering for concatenated / partial LAN frames.
- Wired the requested preferences: device IP, device ID, local key (`password` input), preferred temperature unit (default `F`), polling interval (default 60s), and `logEnable` / `txtEnable` toggles.
- Wired the requested capabilities + commands: `Switch`, `Refresh`, `Initialize`, `Polling`, `TemperatureMeasurement`, plus `setFlameColor`, `setFlameBrightness`, `setLogColor`, `setHeatLevel`, `setHeatingSetpoint`, and `setDpRaw`.
- Surfaced the requested attributes: `power`, `flameColor`, `flameBrightness`, `logColor`, `heatLevel`, `heatingSetpoint`, `temperature`, `online`, `tempUnit`, and raw discovery attributes `dp103`, `dp105`, `dp107`, `dp108`.
- Added the single-connection mitigation requested by Mads: request queue + retry backoff at 5s / 15s / 30s, with log messaging that points at the likely Smart Life / Tuya single-socket contention case.
- Added the power-transition safeguard requested by this session's discovery: writes schedule delayed refresh, and immediate DP14-derived setpoint updates are suppressed during the post-power settle window.

**Known gaps / follow-ups:**
- DP `103`, `105`, `107`, and `108` are intentionally surfaced raw only. This scaffold does **not** claim semantics for them beyond exposing their current values in Hubitat.
- The enum dictionaries for DP `101`, `102`, and `104` are still raw placeholder strings. The command inputs expose likely Tuya ranges, but Switch still needs to confirm the human-friendly labels on real hardware before Link documents them as authoritative.
- There is no live hardware validation in this scaffold commit. The Tuya framing matches tinytuya / qwerk community implementations, but Mads still needs a real Hubitat import + fireplace smoke test.
- The broader "generalize for other Touchstone models" directive is **not** fully folded into this file yet. Discovery commands like `discoverDPs()` / `captureBaseline()` / `captureDiff()` and profile-driven DP remapping remain good v1.1 follow-up work.
- `setDpRaw` is the only advanced discovery command in v0.1.0. It covers raw experimentation, but it is not yet the full self-discovery workflow Mads asked for in the generic Touchstone directive.

---

## 2026-05-17: 2026 Tuya Portal-Free Key Extraction Assessment (Cypher — CORRECTION)

**Date:** 2026-05-17T10:10:26-07:00  
**Author:** Cypher (Integration / Protocol Engineer)  
**Status:** Definitive  
**⚠️ SUPERSEDES:** Prior claim in this same decisions.md (session cypher-6, Touchstone Tuya Feasibility) that `make-all/tuya-local` cloud-auth "passes Mads' no developer app boundary" if he has HA. That was **optimistic and under-flagged three critical constraints.**

### Verdict

**Yes-but-fragile.** One genuinely portal-free path exists in 2026. However, it is **not broadly applicable** and carries **unilateral revocation risk** by Tuya.

### What Was Wrong in Cypher-6

The prior entry stated:
> "Path 2 detail — make-all/tuya-local (HA integration, no dev account needed) ... uses SmartLife credentials only, no developer portal. Recommended if user has HA."

**Constraints that were glossed over:**

1. **Requires Home Assistant to be installed and running** — not a standalone CLI tool. This was mentioned but buried as "~5 min if you have HA"; it's a prerequisite, not just a timing note.

2. **Relies on hardcoded Tuya-issued `client_id = "HA_3y9q4ak7g4ephrvke"`** (`schema = "haauthorize"`) — Tuya can revoke this ID unilaterally, breaking the entire flow for all `tuya-local` users simultaneously. No workaround. This fragility was **completely unmentioned**.

3. **Auth endpoint is `apigw.iotbing.com`, not iot.tuya.com** — This is why it's "portal-free," but this distinction was not explained. The endpoint is Tuya's consumer Smart Life API gateway. It is **not** the Tuya IoT developer portal (`iot.tuya.com`). However, Tuya controls this endpoint and can modify or revoke it.

### The Correct Assessment

| Method | Portal Required | HA Dependency | Revocation Risk | Recommended |
|---|---|---|---|---|
| `make-all/tuya-local` cloud-auth | ❌ No | ⚠️ Yes, required | 🔴 High (hardcoded client_id) | ✅ Only if HA installed; acknowledge risk |
| `tinytuya wizard` | ⚠️ Yes (free account) | ❌ No | 🟡 Low (Tuya can time-limit trial) | ✅ Most durable non-HA path |
| `localtuya` (HA) | ⚠️ Yes (required) | ⚠️ Yes | 🟡 Medium | ⚠️ Fallback |

### For Mads Specifically

**Mads does not run Home Assistant.** Therefore:
- The portal-free path is **inaccessible** to him.
- The **iot.tuya.com portal path he has already started is the correct choice** — it is more durable than relying on a Tuya-controlled consumer API and a hardcoded client_id.

---

## 2026-05-17: Bosch → Touchstone Pivot (User Directive)

**Date:** 2026-05-17T09:53:47-07:00  
**By:** Mads Kristensen (via Copilot)  
**Status:** Archived (Bosch), Active (Touchstone)

Drop the Bosch Home Connect fridge driver project. Do not pursue it further. New target: Hubitat driver for the Touchstone LED fireplace (WiFi-connected; Tuya-based; exposes flame color, log color, brightness, etc.).

**Rationale:** Developer-portal requirement was a non-starter for Bosch. Pivoting to Tuya-based device with more promising integration surface.

**Impact:** Bosch decision record remains for historical reference but is no longer active scope. Next phase: Feasibility pass for Touchstone by Cypher + Trinity.

---

## 2026-05-17: Touchstone Tuya Feasibility (Cypher)

**Date:** 2026-05-17T09:53:47-07:00  
**Author:** Cypher (Integration / Protocol Engineer)  
**Status:** Complete — ready for team review

### Verdict

**Yes-with-caveats.** Tuya Local (LAN) over `interfaces.rawSocket` + AES is the right path. The Touchstone Sideline is confirmed Tuya (product ID `qhwld7e4eqvu5fbp`) with a fully documented DP map from production HA integration. Local key extraction is one-time only and requires no developer portal if using SmartLife credentials via HA tuya-local cloud-auth path.

### Key Findings

**Control path:** Tuya Local (LAN) — unconditionally preferred over Tuya Cloud API.

**Device confirmed:** Touchstone Sideline is explicitly listed in `make-all/tuya-local` DEVICES.md. Product ID `qhwld7e4eqvu5fbp` is the Tuya catalog identifier.

**DP map — Touchstone Sideline** (from `make-all/tuya-local` reference implementation):

| DP | Type | Name | Values |
|---|---|---|---|
| 1 | boolean | Power | `true` = on, `false` = off |
| 101 | string | Flame color/effect | `"1"`=Orange, `"2"`=Blue, `"3"`=Yellow, `"4"`=Orange+Blue, `"5"`=Orange+Yellow, `"6"`=Blue+Yellow |
| 102 | string | Flame brightness | `"1"`=20%, `"2"`=40%, `"3"`=60%, `"4"`=80%, `"5"`=100% |
| 103 | string | Flame speed | `"1"`=Slow, `"2"`=Medium, `"3"`=Fast |
| 104 | string | Ember/log color | `"1"`=orange, `"2"`=red, `"3"`=blue, `"4"`=yellow, `"5"`=green, `"6"`=purple, `"7"`=teal, `"8"`=pink, `"9"`=white, `"10"`=peachpuff, `"11"`=black (off), `"12"`=grey (Mystery/Cycle effects) |
| 105 | string | Log brightness | `"1"`–`"12"` = 8%–100% (linear scale) |

**Platform feasibility:** Hubitat `interfaces.rawSocket` supports Tuya Local v3.3 protocol (AES-128-ECB) with available `javax.crypto.Cipher`. Known issue: silent drop on idle connections — mitigated by heartbeat ping every 20 seconds + reconnect handler. Standard practice for Tuya drivers.

**Local key extraction UX:** Three paths compared:
- `make-all/tuya-local` cloud-auth: ✅ Recommended. No developer account required — uses SmartLife app credentials only (~5 min).
- `tinytuya wizard`: ⚠️ Fallback. Requires free Tuya IoT developer account (~20 min one-time).
- mITM: ❌ Broken since 2023.

**Key correction:** Flame and ember colors are **NAMED PALETTE INDICES** (6 flame effects, 12 log colors), **not** free-form RGB. `ColorControl` capability expecting HSV input will map user colors to nearest palette entry with confusing rounding. **Use named custom commands (`setFlameColor(name)`, `setLogColor(name)`) instead** — more honest UX.

### Open Questions for Switch (Real-Device Validation)

1. **Model confirmation** — Is it the Sideline series? Check the device label. Run `python -m tinytuya scan` to verify.
2. **Protocol version** — Run `python -m tinytuya scan` to confirm v3.3 vs v3.4/v3.5. Determines framing complexity.
3. **Full DP map** — Run `python -m tinytuya wizard` or use HA tuya-local cloud-auth. Confirms DP assignments match assumptions.
4. **Connectivity test** — After extracting `deviceId`, `ip`, `localKey`, run `tinytuya` test to confirm local control works.
5. **Single vs dual connection** — Tuya modules often allow one TCP connection at a time. Test by closing all apps before connecting.

### Sources

- `make-all/tuya-local` DEVICES.md + `touchstone_sideline_fireplace.yaml`
- `make-all/tuya-local` README.md — cloud-assisted config flow (no dev portal path)
- `jasonacox/tinytuya` — local key wizard
- `rospogrigio/localtuya` — protocol v3.1–3.4 reference
- Tuya developer docs — `dbl` category = "Electric fireplace"

---

## 2026-05-17: Touchstone Architecture (Trinity)

**Date:** 2026-05-17T09:53:47-07:00  
**Author:** Trinity (Lead / Architect)  
**Status:** Proposal — awaiting Mads approval

### Recommended Architecture

**Single Groovy driver, no cloud dependency.** Tuya Local (LAN) over rawSocket TCP + AES. Single file at `drivers/touchstone-fireplace/touchstone-fireplace.groovy`.

### Capability Mapping

| Capability / Attribute | DP | Note |
|---|---|---|
| `Switch` | 1 | on/off |
| `SwitchLevel` | 102 | Flame brightness; map 0–100 → `"1"`–`"5"` buckets |
| `Refresh` | all | Status query |
| `Initialize` | — | Socket connect + schedule |
| **Custom command `setFlameColor(name)`** | 101 | Named palette: orange, blue, yellow, orange+blue, orange+yellow, blue+yellow |
| **Custom command `setLogColor(name)`** | 104 | Named palette (12 colors) |
| **Custom command `setLogBrightness(level)`** | 105 | 12-step brightness for log lighting |
| **Custom command `setFlameSpeed(speed)`** | 103 | Slow / Medium / Fast |
| **Custom attribute `flameColor`** | string | Current flame effect name |
| **Custom attribute `logColor`** | string | Current log/ember color name |

**⚠️ CORRECTION (Cypher finding supersedes Trinity's original proposal):**  
Trinity originally recommended `ColorControl` capability for flame color mapping. **This is incorrect and should NOT be used.** Cypher's analysis confirms that flame and ember colors are named palette indices (6 flame effects, 12 log colors), not free-form RGB/HSV. `ColorControl` with HSV input will produce confusing rounding behavior when mapping to palette entries. **Use the named custom commands above instead** — this is the correct UX for palette-based color selection.

### Effort Estimate

**Medium — 2–3 sessions.**

- Session 1: Cypher confirms DP map from tinytuya output; Trinity finalizes DP-to-capability mapping; Tank scaffolds driver with Tuya Local protocol layer.
- Session 2: Tank wires all capability commands to DPs; Switch writes test plan; Mads validates on real device.
- Session 3 (conditional): If protocol version is 3.4/3.5 or DPs differ, one additional session for fixes.

### Folder Layout

```
drivers/
  touchstone-fireplace/
    touchstone-fireplace.groovy     ← single driver file
    README.md                       ← install guide + local-key extraction steps
    packageManifest.json            ← HPM manifest (new UUID v4)
```

### Next Steps

1. **Mads:** If feasibility confirmed safe, run `tinytuya wizard` (or `python -m tinytuya scan`) against the fireplace and share output JSON.
2. **Cypher:** Analyze DP map output, confirm protocol version (3.3/3.4/3.5).
3. **Tank:** Once DP map is known, scaffold driver using Tuya Local protocol layer.
4. **Link:** README + local-key extraction steps once architecture is locked.

---

## 2026-05-17: Touchstone Sideline Elite — Local LAN Control Achieved

**Date:** 2026-05-17T10:47:09-07:00  
**Author:** Coordinator (Direct Mode)  
**Status:** Verified ✅

### Summary

End-to-end LAN control of the Touchstone Sideline Elite fireplace confirmed from Mads' machine. Completed: Tuya IoT signup → tinytuya wizard → local_key extraction → `tinytuya.OutletDevice.status()` query → live DP dump validation.

### Device Facts

- **Product:** Touchstone Sideline Elite electric LED fireplace
- **Tuya productKey:** nc1lwvgjse1ujlr
- **Tuya category:** qn (electric fireplace)
- **Device ID:** 70223053e8db84d10b53
- **IP (LAN):** 192.168.1.38
- **MAC:** e8:db:84:d1:0b:53
- **Protocol:** v3.3, AES-encrypted
- **local_key:** <stored at C:\Users\madsk\devices.json — DO NOT inline value>

### Heater DP Map (Official Tuya Schema)

| DP | Type | Name | Range |
|---|---|---|---|
| 1 | bool | switch | on/off |
| 2 | int | temp_set | 19–30°C |
| 3 | int | temp_current | 0–50°C |
| 5 | enum | level | 0/1/2 (heat level) |
| 13 | enum | temp_unit_convert | c/f |
| 14 | int | temp_set_f | 67–88°F |
| 15 | int | temp_current_f | 32–122°F |

### Vendor-Custom LED DPs (Empirical Mapping — TBD)

Not in Tuya schema. Observed values from live DP dump:

| DP | Type | Observed | Status |
|---|---|---|---|
| 101 | string-enum | "1" | TBD |
| 102 | string-enum | "5" | TBD |
| 103 | string-enum | "1" | TBD |
| 104 | string-enum | "4" | TBD |
| 105 | string-enum | "5" | TBD |
| 107 | bool | false | TBD |
| 108 | bool | false | TBD |

Next session: Validate empirical DP mapping via Tuya app interaction.

### Operational Lesson

**Tuya IoT Cloud Project API subscription gotcha:** A new Tuya IoT Cloud Project does NOT auto-subscribe to the APIs needed for `tinytuya wizard`. Must manually subscribe to:
- IoT Core
- Authorization Token Management
- Smart Home Basic Service
- Device Status Notification

All are free trials with no card on file required. This was the key blocker before Mads could run the wizard.

### Session Context

- **Topic:** touchstone-local-control-achieved
- **Mode:** Direct (Coordinator — no agent spawns)
- **Requested by:** Mads Kristensen

---

## 2026-05-17T13:21:30-07:00 — Tank — Changelog date format fix

- **When:** 2026-05-17T13:21:30-07:00
- **Requested by:** Mads
- **Scope:** `drivers/touchstone-fireplace/touchstone-fireplace.groovy`

### Decision

Normalize every parsed `Changelog:` entry in the Touchstone fireplace driver to use plain `YYYY-MM-DD` dates.

### Why

The release workflow parser in `.github/workflows/release.yml` only matches changelog lines formatted as `version — YYYY-MM-DD — description`. Full ISO 8601 timestamps with time and timezone caused the v0.1.x entries to miss the regex and fail release-note generation for v0.1.5.

### Change made

Removed the `Thh:mm:ss-07:00` portion from the v0.1.5, v0.1.4, v0.1.3, and v0.1.1 changelog dates while leaving version numbers, descriptions, and code unchanged.


---

## 2026-05-17T15:50:06Z — Cypher — Watts Home boost API research

**Status:** Adopted

**By:** Cypher (Research Agent)

### What was researched

No native boost API endpoint exists in the Watts Home thermostat API. Exhaustive reverse-engineering against homebridge-tekmar-wifi (main @ 553ce89) confirmed:
- docs/API_ENDPOINTS.md — zero boost mentions
- src/types/api.ts — no Boost, BoostActive, BoostUntil, BoostExpiration, or hold-timer field
- src/lib/api/client.ts — no setBoost or cancelBoost method
- src/platformAccessory.ts — no boost characteristic

### Recommendation

Implement pseudo-boost in driver state via temporary setpoint override:
- setBoost(minutes): Save current heat setpoint, raise to preset or +5°F, schedule expiry
- cancelBoost() / oostExpired(): Restore saved setpoint, clear state flags
- Mitigate Hubitat restart loss by checking oostUntil on each poll cycle

### Why this decision

Tank needs the contract to implement setBoost / cancelBoost on SunStat v0.1.6. No API contract exists; driver-managed boost is the only viable path.

---

## 2026-05-17T15:50:06Z — Tank — SunStat async HTTP migration pattern (v0.1.5+)

**Status:** Adopted

**By:** Tank (Driver Developer)

### What changed

Pattern: synchronous token refresh + async fan-out + 401 single-retry.

1. **Token refresh stays synchronous** (
efreshTokensSync()) — called before fan-out begins so all async calls share one valid token.
2. **Polling and patching use synchttpGet / synchttpPatch** — each passes data map with childDni and 
etry401: true.
3. **401 recovery uses 	hrottled401Refresh()** — rate-limits refreshes to one per 60 seconds, calls 
efreshTokensSync(), re-issues with 
etry401: false.
4. **429 rate-limit handling** — log warn once per 60 seconds, no retry.
5. **Discovery stays synchronous** — user-triggered, sequential, not on hot path.

### Why

Hub thread stall eliminated during polling cycles. Backward-compatible (removed 
etry401 parameter default, but children never passed it explicitly).

### Caveats

- Callback closures cannot capture live objects; use childDni string + getChildDevice() at callback time.
- httpMethod() shim retained for setAwayModeInternal.

---

## 2026-05-17T17:48:55-07:00 — Tank — Touchstone v0.1.15 flame color authoritative labels (DP 101)

**By:** Tank (Driver Developer)

**Decision:** Use the following authoritative DP 101 flame color labels from Tuya app screenshot (Mads Kristensen):

| DP value | Label         |
|----------|---------------|
| `"1"`    | Orange        |
| `"2"`    | Blue          |
| `"3"`    | White         |
| `"4"`    | Orange+Blue   |
| `"5"`    | Orange+White  |
| `"6"`    | Blue+White    |

Orange (`"1"`) is the app default.

**Background:** v0.1.13 invented labels (Red, Orange, Yellow, …) without hardware verification, causing "set flame color doesn't work" report (UI Orange → DP `"2"` = Blue). v0.1.14 safely reverted to NUMBER. v0.1.15 restores named ENUM with verified labels.

**Log color (DP 104):** 12-value palette labels unknown. `setLogColor` remains NUMBER (1–12) until hardware owner provides Tuya app screenshot. Do NOT invent log color labels.

**Lesson:** Always request Tuya app screenshot from hardware owner before assigning human-readable labels to enum DPs. Owner-verified screenshots are the only trustworthy source.

---
# HPM Multi-Driver Bundle Feasibility

**By:** Cypher  
**Date:** 2026-05-17  
**Requested by:** Mads Kristensen

---

## 1. Verdict

✅ **Feasible — proceed.**

The HPM schema natively supports multiple drivers in a single `packageManifest.json`. Our own SunStat driver already ships a 2-entry `drivers` array — the pattern is proven in-repo. The main work is: create one new bundle manifest, update `release.yml` to handle it (small but required), and decide on version coupling.

---

## 2. HPM Manifest Schema

Source: [HubitatCommunity/hubitatpackagemanager README](https://raw.githubusercontent.com/HubitatCommunity/hubitatpackagemanager/main/README.md)

The manifest is a JSON file. The `drivers` array accepts N entries. `required: false` on an entry makes it **optional** — HPM will prompt the user to opt in/out during install. `required: true` installs silently with no prompt.

Relevant fields per driver entry:

| Field | Required | Notes |
|---|---|---|
| `id` | Yes | UUID, must be unique within the manifest |
| `name` | Yes | Must match the `name:` metadata in the `.groovy` exactly — mismatch causes duplicate installs on Match-Up |
| `namespace` | Yes | Must match the `namespace:` metadata in the `.groovy` |
| `location` | Yes | Raw GitHub URL to the `.groovy` file |
| `required` | Yes | `true` = always installed; `false` = user-selectable optional |
| `version` | No | Per-driver version; omit if using top-level package versioning (don't mix) |

Full schema skeleton for a bundle:

```json
{
  "packageName": "Mads Kristensen — Hubitat Drivers",
  "author": "Mads Kristensen",
  "minimumHEVersion": "2.3.0",
  "dateReleased": "2026-05-17",
  "version": "1.0.0",
  "communityLink": "",
  "documentationLink": "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/README.md",
  "drivers": [
    {
      "id": "63f16ca9-2413-418f-a5d5-b798c23452ee",
      "name": "Touchstone / Tuya Fireplace",
      "namespace": "mads",
      "location": "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/touchstone-fireplace/touchstone-fireplace.groovy",
      "required": false
    },
    {
      "id": "257ada29-4d65-4f90-9183-da6cc75ef908",
      "name": "Gemstone Lights",
      "namespace": "mads",
      "location": "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/gemstone-lights/gemstone-lights.groovy",
      "required": false
    },
    {
      "id": "fe4da0f7-5c8f-429c-8a5d-8d5797667e1f",
      "name": "SunStat Connect Plus",
      "namespace": "mads",
      "location": "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy",
      "required": false
    },
    {
      "id": "2139d8a6-3dc4-4f7c-95b4-e18ecef215f9",
      "name": "SunStat Connect Plus Thermostat",
      "namespace": "mads",
      "location": "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/sunstat-thermostat/sunstat-thermostat-child.groovy",
      "required": false
    }
  ]
}
```

Notes:
- All four driver UUIDs are reused from the existing per-driver manifests — this is intentional and correct. HPM matches on `id` + `name` + `namespace`; the same code installed from either source is tracked as the same package component.
- SunStat ships both parent and child. In the bundle, both remain present (just as in the per-driver manifest). The SunStat child should probably be `required: false` at the bundle level too (if user skips SunStat, skip both).
- Set all three drivers to `required: false` — user picks which ones to install. This is the natural UX for a "collection" manifest.

---

## 3. Reference Packages Found

### Precedent in this repo

**`drivers/sunstat-thermostat/packageManifest.json`** (in-repo, already shipping)  
Already has a 2-driver `drivers` array (parent + child, both `required: true`). This is direct proof the format works as deployed.

### External examples

**`gilderman/utec-lock` — `repository/packageManifest.json`**  
URL: https://raw.githubusercontent.com/gilderman/utec-lock/main/repository/packageManifest.json  
Layout: `apps` + `drivers` + `libraries` arrays in one manifest. Shows that apps and libraries can coexist with drivers. Note: the manifest also has a top-level `namespace` field (not required by HPM but harmless).

**`spinrag/hubitat` — `dmsMonitor/packageManifest.json`**  
URL: https://raw.githubusercontent.com/spinrag/hubitat/main/dmsMonitor/packageManifest.json  
Single-driver with `required: true`, `description` and `tags` fields (HPM repository-filing metadata). Shows the complete field set including category/tags needed for HPM repository submission.

**Official HPM schema example** (canonical reference)  
URL: https://raw.githubusercontent.com/HubitatCommunity/hubitatpackagemanager/main/README.md  
The README shows a 2-driver example with one `required: true` and one `required: false`. This is the authoritative format reference.

### Multi-driver community packages (observed conventions)

Large community packages (Kasa Integration, ecobee Suite) use a single top-level `version` for the entire package and bump it any time any component changes. Per-component versioning exists in the schema but is rarely used in practice — see the HPM docs note: "don't mix-and-match."

---

## 4. Recommended Repo Layout

### File to create

**`packageManifest.json`** at repo root (or optionally `drivers/bundle/packageManifest.json`)

**Recommendation: repo root**, because:
- It is logically a meta-package, not a driver
- Keeps it visually distinct from the per-driver manifests
- URL is clean: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/packageManifest.json`

### Per-driver manifests: keep them

Users who installed a single driver via its per-driver URL stay on that path. The bundle manifest is additive. HPM will not conflict because the same `id`/`name`/`namespace` tuples will be recognized as already-installed during Match-Up — see **§6 Migration** below.

### Directory layout after change

```
hubitat-drivers/
├── packageManifest.json          ← NEW (bundle)
├── drivers/
│   ├── touchstone-fireplace/
│   │   ├── packageManifest.json  ← kept (per-driver)
│   │   └── touchstone-fireplace.groovy
│   ├── gemstone-lights/
│   │   ├── packageManifest.json  ← kept (per-driver)
│   │   └── gemstone-lights.groovy
│   └── sunstat-thermostat/
│       ├── packageManifest.json  ← kept (per-driver)
│       ├── sunstat-thermostat-parent.groovy
│       └── sunstat-thermostat-child.groovy
└── .github/workflows/release.yml ← needs update (see §5)
```

---

## 5. Release Workflow Changes

### Current behavior

`release.yml` triggers on:
```yaml
paths:
  - 'drivers/**/packageManifest.json'
```

The detect step runs:
```bash
mapfile -t manifests < <(find drivers -type f -name 'packageManifest.json' | sort)
```

This `find drivers ...` hard-codes the `drivers/` prefix. A root-level `packageManifest.json` will **not** be found. The push trigger also won't fire for it.

Additionally, for each manifest the script does:
```bash
driver_dir=$(dirname "$manifest")   # → "." for root manifest
slug=$(basename "$driver_dir")       # → "." ← BREAKS tag generation
```

The tag would become `.-v1.0.0` — invalid.

### Required changes

**Change 1 — add root-level path to push trigger:**
```yaml
paths:
  - 'drivers/**/packageManifest.json'
  - 'packageManifest.json'           # ← add this
```

**Change 2 — update the find command to also scan root:**
```bash
mapfile -t manifests < <(
  { find . -maxdepth 1 -name 'packageManifest.json';
    find drivers -type f -name 'packageManifest.json'; } | sort
)
```

**Change 3 — handle bundle manifest (no matching .groovy file):**

The script currently errors out if no `.groovy` file is found. For a bundle manifest there is no `.groovy`. Two options:
- **Option A (simple):** Add a `bundle-changelog.md` inside `drivers/bundle/` (or similar) and put the manifest there, not at root — then the slug becomes `bundle`, the driver_file search can look for a `*.md` changelog stub.
- **Option B (cleaner):** Add a `type: "bundle"` detection in the script that skips changelog extraction and uses the `releaseNotes` top-level field from the manifest instead. 
- **Option C (simplest):** Exclude the root manifest from the automated release workflow entirely. Bump it manually when any driver changes. The per-driver releases are already tagged individually; the bundle just needs to point at the latest `location` URLs (which are always the latest since they're `main` branch URLs).

**Recommendation: Option C for now.** The bundle manifest has no changelog to extract — it's a meta-package. Tag the bundle release manually or trigger it via `workflow_dispatch`. Add a note in the root manifest's CONTRIBUTING instructions.

If Option C, the workflow change is just the path trigger addition to fire on `packageManifest.json`, plus a conditional skip when `driver_dir == "."`:

```bash
if [ "$driver_dir" = "." ]; then
  # Bundle manifest: no groovy file, no changelog extraction.
  # Tag as "bundle-v${version}" with a simple "Bundle update" note.
  notes="Bundle version ${version}: see individual driver changelogs."
  tag="bundle-v${version}"
fi
```

---

## 6. Version Coupling Decision

### What HPM docs say

> "You can either version the entire package as a whole, or each app/driver can be versioned, but don't mix-and-match within the same package."

### Community convention

Large multi-driver packages overwhelmingly use **top-level package versioning** (one `version` field at the manifest root, no per-driver `version` fields). When any component bumps, the package version bumps. This is observed across Kasa Integration, ecobee Suite, and most well-maintained HPM packages.

### Recommendation: bundle version is independent of per-driver versions

- The per-driver manifests keep their own `version` (e.g., `"version": "0.1.18"`)
- The bundle manifest has its own `version` (e.g., `"version": "1.0.0"`)
- The bundle version bumps whenever any driver in the bundle bumps (or when a new driver is added)
- The bundle manifest has NO per-driver `version` fields (avoids mix-and-match)

Practical workflow: when you bump `drivers/touchstone-fireplace/packageManifest.json` from `0.1.18` to `0.1.19`, also bump the root `packageManifest.json` from `1.0.x` to `1.0.x+1`. HPM users who installed via the bundle will be notified of an update and will re-fetch all driver `.groovy` files from `main`.

---

## 7. User Experience

### Installing via bundle

1. User pastes the bundle URL into HPM "Install from URL"
2. HPM reads the manifest, sees 4 drivers all with `required: false`
3. HPM shows a checklist: user selects which drivers to install
4. HPM fetches and installs the selected `.groovy` files
5. HPM tracks the bundle package for future updates

**Uninstall:** All-or-nothing at the package level (HPM uninstalls everything it installed for that package). Per-driver manifests are separate packages and unaffected.

### Per-driver vs bundle update flow

If a driver bumps and both the bundle manifest AND the per-driver manifest are updated, HPM will show updates in **both** the bundle package AND the per-driver package for any user who installed via both paths. This is a minor annoyance but not a bug — applying either update fetches the same `.groovy` content.

---

## 8. Migration: Existing Per-Driver Users

**Scenario:** User already installed Touchstone Fireplace via `drivers/touchstone-fireplace/packageManifest.json`. They now install the bundle.

**What happens:** HPM's Match-Up will recognize that the driver with `id: "63f16ca9-..."` + `name: "Touchstone / Tuya Fireplace"` + `namespace: "mads"` is already installed. HPM marks it as a matched component within the new bundle package. The user is now tracked under BOTH packages.

**Risk:** When the per-driver manifest and bundle manifest both show updates, the user will see two update prompts for the same driver. This is cosmetically awkward but functionally harmless — both installs update the same `.groovy` content.

**Mitigation:** Document in README: "Install via bundle OR per-driver URL, not both." No code change needed.

---

## 9. Implementation Work Breakdown (for Tank)

1. **[S] Create root `packageManifest.json`** — new file, copy IDs/names/locations from existing manifests, all drivers `required: false`, initial `version: "1.0.0"`. ~5 min.

2. **[S] Update `release.yml` push trigger** — add `- 'packageManifest.json'` to the `paths` list.

3. **[M] Update `release.yml` detect step** — handle the root manifest case: skip changelog extraction, generate `bundle-v${version}` tag, emit a simple `notes` string. Approximately 10 lines of bash.

4. **[S] Update root README** — add "Install all drivers via one HPM URL" section with the bundle URL; clarify the per-driver vs bundle trade-off.

5. **[S] Establish version bump convention** — add a sentence to CONTRIBUTING.md (or equivalent): "When bumping any per-driver version, also bump the root `packageManifest.json` version."

Total: roughly 1–2 hours of Tank work.

---

## 10. Unknowns

- **HPM duplicate detection across packages:** I cannot empirically test whether HPM silently deduplicates or shows two update prompts when the same driver is tracked under two packages. The Match-Up behavior is described in the docs but the edge case of overlapping packages is undocumented. **Switch should test this on a real hub before shipping the bundle.**
- **`namespace` top-level field:** Some manifests (e.g., gilderman/utec-lock) include a top-level `namespace` field not shown in the official schema. It appears to be optional metadata for HPM repository filing. Including it is harmless; I've left it out of the skeleton above.
- **Bundle version policy:** Whether HPM users prefer "one URL, always current" vs "one URL, version-locked" is a user-preference question. The `main`-branch raw URLs in `location` mean users always get the latest code on update regardless of the bundle version — this is correct behavior.

---

# Tuya Autodiscovery on Hubitat — Feasibility

**By:** Cypher  
**Date:** 2026-05-17  
**Requested by:** Mads Kristensen

---

## 1. Verdict

⚠️ **Feasible-with-caveats — but the primary approach (passive UDP broadcast listening) is not supported on Hubitat.**

The native Tuya LAN discovery mechanism requires passively listening for UDP broadcasts that devices emit spontaneously on port 6666/6667. **Hubitat does not support this.** A Hubitat staff member confirmed in the official UDP broadcast thread: *"No, we do not support [receiving UDP broadcasts]. You can only send out UDP messages and receive a reply to that message."* (Source: https://community.hubitat.com/t/udp-broadcast-support/3957/11, December 2018.)

**Plan B is viable:** An explicit "Discover" button that performs an active TCP probe of the local /24 subnet on port 6668 can locate the fireplace. This is not automatic/background discovery — it's a user-triggered action — but it directly solves the "DHCP renewal silently breaks the driver" problem with acceptable UX.

Verdict summary:
- ❌ True UDP broadcast listening (passive): not feasible on Hubitat — no API
- ⚠️ Active TCP scan ("Discover" button): feasible, ~1 hour of Tank work, addresses the user problem
- ✅ DHCP reservation (user-side): always works, zero driver code, document as primary recommendation

---

## 2. Hubitat UDP Listening Capability

### What Hubitat supports

Hubitat provides two LAN comms mechanisms in drivers/apps:

**A. `sendHubCommand` with `LAN_TYPE_UDPCLIENT`**

Sends a UDP datagram to a specific `destinationAddress: "ip:port"`. Supports both unicast and broadcast (`255.255.255.255:port`). When the target device sends a response back to the hub's source IP:port, the response arrives in the driver's `parse()` callback. This is request-reply UDP only.

```groovy
def myHubAction = new hubitat.device.HubAction(
    payloadHex,
    hubitat.device.Protocol.LAN,
    [type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
     destinationAddress: "192.168.1.100:6668",
     encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
     callback: "parseUdp"])
sendHubCommand(myHubAction)
```

The `parseUdp(message)` callback fires when a response arrives:
```groovy
def parseUdp(message) {
    def resp = parseLanMessage(message)
    if (resp.type == "LAN_TYPE_UDPCLIENT") {
        // process resp.payload
    }
}
```

Source: abdinoor/Hubitat `Kasa-LAN-Switch.groovy` and dcmeglio HPM README examples.

**B. `interfaces.rawSocket`**

Opens a **TCP** socket to a specific IP:port. Used in the existing touchstone-fireplace driver (`interfaces.rawSocket.connect(ip, 6668, byteInterface: true, readDelay: 150)`). TCP-only — cannot receive UDP frames.

### What Hubitat does NOT support

- Passively listening on a UDP port (no `bind()` equivalent for UDP)
- Receiving unsolicited UDP packets from arbitrary sources
- Multicast UDP subscription
- Any `parse()` or `rawSocket` callback triggered by an inbound UDP broadcast that the hub did not initiate

**Citation:** Hubitat staff member (community.hubitat.com/t/udp-broadcast-support/3957/11):
> *"if you are asking about the hub receiving udp broadcasts from a device. No, we do not support that. You can only send out UDP messages and receive a reply to that message."*

This was confirmed in 2018. No subsequent Hubitat firmware release has added a passive UDP listener API. Community threads as recent as 2023-2024 still describe `LAN_TYPE_UDPCLIENT` as the sole UDP mechanism, with the same send-and-receive-reply constraint.

---

## 3. Tuya Broadcast Protocol

### Overview

Tuya devices (including the Touchstone Sideline, v3.3) emit UDP broadcast packets **spontaneously and continuously** on the local subnet. These are device-initiated broadcasts, not responses to any query. A listener (e.g., tinytuya, make-all/tuya-local) passively binds a socket to port 6666 or 6667 and waits.

### Ports

| Port | Protocol version | Encryption |
|------|-----------------|------------|
| 6666 | v3.1 (and fallback) | None — plaintext JSON |
| 6667 | v3.3+ | XOR-encrypted with a known public key |

### Payload shape (v3.3, after decryption)

```json
{
  "ip": "192.168.1.47",
  "gwId": "bf1234567890abcdef1234",
  "active": 2,
  "ability": 0,
  "mode": 0,
  "encrypt": true,
  "productKey": "qhwld7e4eqvu5fbp",
  "version": "3.3"
}
```

The `gwId` field equals the device's `deviceId` (same value used in TCP protocol DPS frames). The `productKey` for the Touchstone Sideline is `qhwld7e4eqvu5fbp` (confirmed in cypher-touchstone-tuya-feasibility.md from make-all/tuya-local YAML).

### Decryption for v3.3 broadcasts (port 6667)

The broadcast payload (after the 20-byte header) is XOR-encrypted with the public key `yGAdlopoPVldABfn` (16 bytes, repeated to cover payload length). This key is publicly documented in tinytuya source and make-all/tuya-local. After XOR-decoding the result is the plaintext JSON above.

Decryption steps:
1. Receive raw UDP packet on port 6667
2. Skip the first 20 bytes (Tuya header: prefix 4 bytes, sequence 4 bytes, command 4 bytes, length 4 bytes, return code 4 bytes)
3. XOR each byte of the remaining payload with the corresponding byte of the repeating key `b'yGAdlopoPVldABfn'`
4. The result is the plaintext JSON

**This decryption is NOT needed for port 6666 (v3.1 broadcasts), where the JSON is sent in plaintext.**

### Why tinytuya scanner cannot be ported directly to Hubitat

tinytuya's `scanner.py` (and `tuya-local`'s equivalent) does:
```python
sock.bind(("", 6666))  # bind to port 6666, any source IP
sock.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)
data, addr = sock.recvfrom(4096)  # blocks, waiting for any broadcast
```

This is passive UDP listening — binding to a local port and waiting for inbound packets from **any** source IP. Hubitat has no equivalent API for this.

---

## 4. Recommended Architecture (Plan B — Active TCP Probe)

Since passive UDP listening is blocked, the best feasible approach is an active TCP scan using the mechanism the driver already relies on (`interfaces.rawSocket`).

### Core idea

When the driver fails to connect to the stored IP, it can optionally (on user command) probe a range of IPs on port 6668. If a device responds with a Tuya v3.3 HELLO/ping frame that contains the stored `gwId`, the driver updates the stored IP automatically.

### Implementation flow

**Trigger points:**
1. **`initialize()`** — after the driver restarts, if the stored IP is unreachable on first connection attempt, log a warning and trigger discovery (or display a message telling the user to press "Discover").
2. **`discover()` command button** — explicit user action, callable from the Hubitat device page. Runs the scan. Logs the found IP and updates the preference.

**Discovery steps:**

```
1. Extract the /24 from the last known IP (e.g., "192.168.1.47" → "192.168.1.")
2. For i = 1..254 (or a smarter range: last-known ± 10 first, then full sweep):
   a. Try interfaces.rawSocket.connect("192.168.1.{i}", 6668, byteInterface: true, readDelay: 150)
   b. Send a Tuya v3.3 heartbeat frame (CMD 9, or STATUS_REQUEST CMD 10)
   c. Wait for socketStatus() callback: if "established", send frame and await parse()
   d. In parse(): decode the response, check if gwId matches stored deviceId
   e. If match: save discovered IP to state and updateSetting("ipAddress", discoveredIp); break
   f. If no response within ~2s: rawSocket.close(); move to next IP
```

### Caveats and constraints

- **rawSocket is one-connection-at-a-time.** Hubitat's driver sandbox runs all callbacks on a single thread. Sequential scanning is safe but slow — ~2s per IP worst case = up to ~8 minutes for a full /24. In practice, the fireplace IP typically moves only a few octets, so a ±20 range around the last known IP takes <1 minute.
- **The driver's socketStatus/parse callback model means sequential scanning requires state machine management** — `state.discoveryMode = true`, `state.discoveryNextIp = i`, etc. Not trivial but Tank-manageable in ~100 lines.
- **Hubitat sandbox blocks `java.net.*` directly** — cannot use DatagramSocket, InetAddress scanning, or ARP lookups. Must use only the `interfaces.rawSocket` and `sendHubCommand` APIs.
- **No broadcast ping shortcut.** Although `LAN_TYPE_UDPCLIENT` supports `destinationAddress: "255.255.255.255:6668"`, Tuya devices do NOT respond to UDP queries on port 6668 — they only respond to TCP connections on 6668. There is no UDP discovery query that Tuya devices are designed to answer.

### IP surfacing

On success:
1. `log.info "Tuya autodiscovery: found device at ${discoveredIp} (was ${oldIp})"`
2. `device.updateSetting("ipAddress", [type:"string", value: discoveredIp])` — updates the preference field directly
3. `sendEvent(name: "networkAddress", value: discoveredIp)` — optional attribute for visibility

### Handling multiple Tuya devices on the LAN

Filter by `gwId` match. The driver already stores the `deviceId` (gwId) in preferences or state. When probing, only accept an IP if the Tuya status response's device ID matches the stored one. Other Tuya devices on the LAN will be ignored.

---

## 5. Fallback: No Discovery Needed (Recommended First Step)

Before implementing any scanning, the recommended guidance to users should be:

> **Set a DHCP reservation in your router for the fireplace's MAC address.** This prevents IP changes entirely. Most home routers expose this in the DHCP settings page. The fireplace's MAC address is visible in your router's connected-devices list.

This costs zero driver code and solves the root problem permanently. The driver should print a clear log.error (not just log.warn) when a connection fails:

```groovy
log.error "Cannot connect to Touchstone fireplace at ${ip}. " +
          "If your device IP changed (DHCP lease renewed), update the IP in device preferences. " +
          "Tip: set a DHCP reservation in your router to prevent this."
```

The "Discover" button as Plan B gives power users an in-Hubitat recovery path without needing to log into the router.

---

## 6. Implementation Work Breakdown (for Tank)

1. **[S] Improve error message on TCP connect failure** — change silent fail to `log.error` with actionable text including current IP and DHCP reservation tip. ~10 lines. (Minimum viable improvement, ship first.)

2. **[M] Add `discover()` command** — driver metadata declaration + stub implementation. Triggers the scan described in §4. ~20 lines scaffolding.

3. **[L] Implement active TCP probe state machine** — `discoveryMode` flag, sequential rawSocket connect/probe per IP, `socketStatus()`/`parse()` handlers dispatch to discovery path vs normal path, match on gwId, update preference on success. ~80–120 lines. Requires careful integration with existing socket lifecycle — the current driver uses rawSocket for normal Tuya commands; discovery must not clobber in-flight command state.

4. **[S] Add `networkAddress` attribute** — surface discovered IP as an attribute so automations can observe IP changes. ~5 lines.

5. **[S] Document in README** — "IP Discovery" section: DHCP reservation is primary, Discover button is fallback. Expected scan time for typical home network. ~20 lines.

Total: ~3–4 hours of Tank work (dominated by the state machine).

---

## 7. Risks / Unknowns (Switch to Verify)

| Risk | Severity | Notes |
|---|---|---|
| rawSocket sequential scan throughput | Medium | 2s timeout × 254 IPs = ~8 min worst case. Switch should measure actual scan time on a real C-8 hub. |
| Hub sandbox rate-limiting rawSocket.connect() | Medium | Hubitat may throttle rapid sequential TCP connections. If connections are refused after N attempts, the scan will fail silently. Switch must observe hub logs during a test scan. |
| gwId in Tuya v3.3 status response | Medium | The plan assumes the STATUS_REQUEST response includes gwId or that the driver can authenticate the device is the right one. Switch must confirm that a v3.3 status response (CMD 10) includes gwId in the payload — or identify an alternative fingerprint. |
| Hubitat restart mid-scan | Low | If the hub restarts during a scan, state.discoveryNextIp is lost. Non-critical — user can press Discover again. |
| No Tuya device at probed IP responds on 6668 | None | Expected — the scan just skips it. Safe. |
| IP outside current /24 | Low | If DHCP issued an IP in a different subnet (unusual), the /24 scan won't find it. Fall back to manual entry. |

---

## 8. Sources

1. Hubitat staff quote on UDP broadcast: https://community.hubitat.com/t/udp-broadcast-support/3957/11 (Patrick, Hubitat staff, December 2018)
2. `LAN_TYPE_UDPCLIENT` code pattern and `parseUdp()` callback: `abdinoor/Hubitat` `Kasa-LAN-Switch.groovy` (live, verified)
3. `interfaces.rawSocket` TCP-only: confirmed in existing `drivers/touchstone-fireplace/touchstone-fireplace.groovy` (this repo), which uses `rawSocket.connect()` for TCP to Tuya port 6668
4. Tuya broadcast ports and XOR key: tinytuya source + make-all/tuya-local, documented in `.squad/decisions.md` (Touchstone Tuya feasibility section, cypher-6)
5. Touchstone Sideline `productKey: "qhwld7e4eqvu5fbp"`: make-all/tuya-local YAML, cited in `cypher-touchstone-tuya-feasibility.md`
6. Hubitat `LAN_TYPE_UDPCLIENT` example (broadcast-to-broadcast-address pattern): codahq comment at community.hubitat.com/t/udp-broadcast-support/3957/9 (December 2018)
7. UDP broadcast-to-255.255.255.255 attempt with no response: community.hubitat.com/t/udp-broadcast-support/3957/23-25 (community developer, LIFX protocol, December 2018 — confirms broadcast send works but receiving broadcast replies from devices is unreliable or unsupported)

---

# HPM Multi-Driver Bundle Manifest v1.0.0

**By:** Tank  
**Date:** 2026-05-17  
**Status:** Shipped — commit a0e695d

---

## What Was Done

Created a single Hubitat Package Manager (HPM) bundle manifest at the repo root, bundling all four drivers so users can install all of Mads's drivers from one URL.

### Files Created / Modified

- **`packageManifest.json` (repo root, NEW):** Bundle manifest v1.0.0, four drivers with `required: false`
- **`.github/workflows/release.yml` (MODIFIED):**
  - Added `- 'packageManifest.json'` to push trigger `paths:`
  - Updated `find` command to also scan repo root: `{ find . -maxdepth 1 -name 'packageManifest.json'; find drivers -type f -name 'packageManifest.json'; } | sort`
  - Added conditional skip for root manifest: when `driver_dir == "."`, set `tag="bundle-v${version}"` and `notes="Bundle version ${version}: see individual driver changelogs."`, then `continue`
- **`README.md` (root, MODIFIED):**
  - Added "Install all drivers via one URL (HPM bundle)" section with URL and install instructions
  - Added note: install via bundle OR per-driver URL, not both
  - Added version bump convention in Contributing section

### Bundle URL

```
https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/packageManifest.json
```

### UUID Mapping (reused from per-driver manifests)

| Driver | UUID |
|--------|------|
| Touchstone / Tuya Fireplace | `63f16ca9-2413-418f-a5d5-b798c23452ee` |
| Gemstone Lights | `257ada29-4d65-4f90-9183-da6cc75ef908` |
| SunStat Connect Plus | `fe4da0f7-5c8f-429c-8a5d-8d5797667e1f` |
| SunStat Connect Plus Thermostat | `2139d8a6-3dc4-4f7c-95b4-e18ecef215f9` |

### Version Coupling

- Bundle version `1.0.0` is independent of per-driver versions
- Bundle has no per-driver `version` fields (HPM: never mix top-level + per-driver versioning)
- When any per-driver version bumps, also bump root `packageManifest.json` (patch/minor)

---

## Gotchas Encountered

1. **release.yml `find` hard-codes `drivers/`** — root manifest would have been silently ignored without the workflow update.
2. **`basename(dirname("."))` returns `"."` → bad tag** — required the explicit `driver_dir == "."` branch with a custom tag format.
3. **UUID reuse is mandatory** — HPM Match-Up matches on `id + name + namespace`. Different UUIDs between bundle and per-driver manifest = two separate tracked components = duplicate update prompts.

---

## Follow-Up (Switch)

- HPM duplicate-detection edge case: if a user installed a driver via per-driver URL and then installs the bundle, does HPM show one update or two? Community HPM docs describe Match-Up behavior but the multi-package overlap case is untested. Switch should test before publicly advertising the bundle URL.

---

# Touchstone v0.1.19 — Child Lock Command (DP 108)

**By:** Tank  
**Date:** 2026-05-17  
**Status:** Shipped — commit 3a59f04

---

## What Was Done

Added `setChildLock(on|off)` command to the Touchstone / Tuya Fireplace driver (v0.1.18 → v0.1.19).

### Changes

- **Metadata:** Added `command "setChildLock", [[name: "state*", type: "ENUM", constraints: ["off", "on"]]]`
- **Metadata:** Added `attribute "childLock", "enum", ["on", "off"]`
- **New method:** `setChildLock(lockState)` — validates on/off, emits optimistic attribute, calls `sendDpWrite("108", lockState == "on", "child lock", WRITE_REFRESH_DELAY_SECONDS)`
- **applyDps() DP 108 handler:** Now also emits `childLock` attribute (on/off) in addition to the raw `dp108` string attribute. Uses `asBoolean()` to convert the Tuya boolean wire value.
- **README:** Added `setChildLock` to command reference; added `childLock` to attributes section.
- **TESTING.md:** Added Test 38.
- **packageManifest.json:** Bumped to 0.1.19.

### Wire Protocol

DP 108 is a Tuya BOOL type on the Touchstone Sideline Elite. `true` = locked (buttons disabled), `false` = unlocked. Wire values are passed directly as Groovy `Boolean` to the existing `sendDpWrite()` plumbing, which serializes them correctly in the JSON payload.

### Notes for Switch

- **Test 38** in TESTING.md covers lock-on/lock-off with observation of physical button response.
- The `childLock` attribute should update in real time via push frames (the device echoes back DP 108 state on change). If push frame does not carry DP 108 on lock, the attribute will catch up on next poll.

---

## Key Pattern (Reusable)

Two-line boolean DP dispatch:
- Write: `sendDpWrite("N", userWantsOn, "label", WRITE_REFRESH_DELAY_SECONDS)`
- Read (in applyDps): `Boolean lockBool = asBoolean(dps["N"]); String lockValue = lockBool ? "on" : "off"; emitAttribute("attrName", lockValue, ...)`

---

# Touchstone v0.1.20 — Active-TCP IP Discovery (DHCP-Renewal Recovery)

**By:** Tank  
**Date:** 2026-05-17  
**Status:** Shipped — commit ffbfd08

---

## What Was Done

Added `discover` command and active-TCP /24 subnet scan to the Touchstone fireplace driver (v0.1.19 → v0.1.20), solving the "DHCP lease renewal silently breaks the driver" problem.

### Sub-item 3A: Improved error UX

Changed `openSocket()` failure from `log.warn` to `log.error` with actionable text:
- States the IP that was tried
- Tells user to update preferences or press Discover
- Recommends DHCP reservation as the permanent fix

### Sub-item 3B: Active-TCP Discovery State Machine

**New command:** `discover` (zero-arg button on device page)  
**New attribute:** `networkAddress` (string) — surfaces discovered IP

**State machine flow:**

```
discover()
  → build state.discoveryProbeQueue (smart ±20 range first, then 1-254 sweep)
  → closeSocket + unschedule heartbeat
  → state.discoveryMode = true
  → runIn(1, "discoveryProbeNext")

discoveryProbeNext()
  → if queue empty → discoveryComplete()
  → pop next octet from queue
  → rawSocket.connect(targetIp, 6668)
  → send TUYA_CMD_DP_QUERY frame (to elicit response with devId)
  → runIn(3, "discoveryProbeTimeout")

socketStatus()  [modified]
  → if discoveryMode + error/disconnect: cancel timeout, runIn(1, "discoveryProbeNext")
  → if discoveryMode + other: debug log only

parse() → processFrame()  [modified]
  → if discoveryMode: route to discoveryHandleResponse(response) instead of applyDps()

discoveryHandleResponse()
  → if no devId in response: warn, skip, probeNext
  → if devId matches stored deviceId: update deviceIP pref, emit networkAddress, discoveryComplete()
  → if devId mismatch: debug log, probeNext

discoveryProbeTimeout()
  → if discoveryMode: probeNext

discoveryComplete()
  → state.discoveryMode = false
  → log success or failure
  → initialize()  ← restores normal socket + heartbeat
```

### Guards Added

- `openSocket()`: skip if discoveryMode
- `reconnectSocket()`: skip if discoveryMode
- `sendHeartbeat()`: skip if discoveryMode
- `parse()`: skip normal post-processing (pumpQueue, etc.) if discoveryMode

---

## Known Limitations / Switch Verification Items

| Item | Notes |
|------|-------|
| gwId in v3.3 DP_QUERY response | The plan assumes the device responds with `devId` in the JSON payload. Switch should confirm on real hardware that a CMD 10 response from the Sideline Elite includes `devId`. If not, the discovery will find no match and log a warning. |
| Hub sandbox rate-limiting | Hubitat may throttle rapid sequential rawSocket.connect() calls. If connections are refused after N attempts, the scan fails silently. Switch should observe hub logs during a test scan. |
| Scan time | 2 s/IP worst case → ~8 min full sweep. Smart ±20 range typically < 1 min for normal DHCP drift. |
| IP outside /24 | If DHCP assigns an IP in a different subnet, the scan won't find it. Document: set DHCP reservation to avoid this. |

---

## Key Design Decisions

1. **Fail-closed on devId:** only accept if `response.devId == storedDevId`. No partial matches, no "first Tuya device on port 6668 wins."
2. **pre-computed probe queue in state:** simpler than managing phase flags. 254 integers ~1 KB in Hubitat state.
3. **intentionalCloseAt reuse:** avoids adding new socket-suppression logic; each probe-close stamps the same timestamp the rest of the driver already respects.
4. **discoveryComplete() always calls initialize():** clean handoff from discovery to normal operation, regardless of success/failure.

---

## Version Bumps

- `drivers/touchstone-fireplace/packageManifest.json`: 0.1.19 → 0.1.20
- `packageManifest.json` (bundle root): 1.0.0 → 1.0.1

---

# Decision: drivers/daikin-wifi/ v0.1.0 Fork

**Author:** Tank  
**Date:** 2026-05-18  
**Commit:** a3ac5cf

---

## Summary

Forked `eriktack/hubitat-daikin-wifi` (MIT, last code commit 2021-04-30) into `drivers/daikin-wifi/` as `v0.1.0`. Upstream is effectively abandoned (issues disabled, 2 open PRs unreviewed for 1–2 years). Fork rights confirmed — MIT license.

---

## v0.1.0 Scope (Items 0–5 + EnergyMeter)

| Item | Change | Status |
|------|--------|--------|
| 0 | Sentinel guard: `.isNumber()` before `Double.parseDouble()` on `otemp`/`htemp` — fixes `NumberFormatException` every poll cycle when outdoor sensor unavailable | ✅ Shipped |
| 1 | `supportedThermostatModes` emitted in `installed()` + `updated()` — fixes Rule Machine / dashboard mode selectors | ✅ Shipped |
| 2 | `supportedThermostatFanModes` also emitted in `installed()`, deprecated `displayed: false` removed | ✅ Shipped |
| 3 | `initialize()` lifecycle added, called from both `installed()` + `updated()` — fixes post-reboot polling dead zone | ✅ Shipped |
| 4 | `refreshEnergy()` method on fixed 30-min cron schedule; `get_week_power_ex` + `get_year_power_ex` removed from `refresh()` and `updateDaikinDevice()` | ✅ Shipped |
| 5 | `capability "HealthCheck"` + `lastActivity` attribute — Pattern A (LAN HTTP variant): `ping()` returns HubAction, cleared in `parse()`, 5s `pingTimeout`, `lastActivity` throttled to ≥60s | ✅ Shipped |
| + | `capability "EnergyMeter"` + standard `energy` attribute (= today's kWh) | ✅ Shipped |

---

## Deferred to v0.1.1+

- **Econo / powerful mode** (`get_special_mode` / `set_special_mode`) — Trinity item 7
- **`get_model_info` capability detection** — prevents unsupported commands appearing on incapable units
- **Full event hygiene pass** — `descriptionText:` on all 66 `sendEvent` calls, full `emitIfChanged()` sweep — Trinity item 8
- **Humidity** (`RelativeHumidityMeasurement`) — requires `get_model_info` first

---

## Attribution

- **Original source:** `eriktack/hubitat-daikin-wifi` — `daikin-wifi-split-system-hubitat.groovy` v1.0.3 (2021-04-30)
- **Original copyright:** Copyright 2018 Ben Dews — https://bendews.com (MIT)
- **Contribution:** RBoy Apps
- **Hubitat port:** eriktack, based on tsaaek community work
- **License:** MIT — original copyright preserved verbatim in file header per MIT license terms

---

# Decision Drop: Daikin WiFi Driver — Fork → Clean-Room Course Correction

**Date:** 2026-05-18  
**Author:** Tank  
**Status:** Decided — implemented

---

## Context

A previous Tank spawn delivered `drivers/daikin-wifi/` v0.1.0 as a fork of `eriktack/hubitat-daikin-wifi` (commit `a3ac5cf`). Mads explicitly redirected: he wants a **clean-room implementation** — new driver, same protocol knowledge, independent authorship.

---

## Decision

**Do not fork. Write a clean-room implementation.**

1. Revert the fork commit via `git revert a3ac5cf` (never `git reset --hard` — shared history must not be rewritten).
2. Implement `drivers/daikin-wifi/daikin-wifi.groovy` v0.1.0 from scratch using:
   - Protocol knowledge from team research memos (Cypher + Trinity prose analysis)
   - In-repo structural patterns (Touchstone, SunStat, Gemstone) as implementation reference
   - **Not** the upstream source code or any copy thereof
3. Credit eriktack as **inspiration / prior art** in the file header and README — not as MIT licensor.
4. License: Mads Kristensen's own MIT copyright.

---

## Rationale

- **Legal cleanliness:** Clean-room work is unambiguously Mads's own original work. No obligation to preserve a third-party copyright block.
- **Quality:** Lets us build the driver to this repo's standards from the ground up, not retrofit SmartThings-circa-2017 code to Hubitat idioms.
- **Mads's explicit direction:** "don't fork. create a new one in this repo. make sure to credit the person for the inspiration."

---

## Attribution Model

| Location | What |
|---|---|
| File header | `Inspiration / prior art: eriktack/hubitat-daikin-wifi — first community driver ...` |
| README `## Acknowledgments` | Credit + link to upstream repo |
| License block | `MIT License — original work by Mads Kristensen (2026)` — no third-party copyright included |

---

## Deferred Items (v0.1.1+)

- `get_special_mode` / `set_special_mode` — econo and powerful modes
- `get_model_info` on `initialize()` — runtime capability detection (humidity sensor presence, swing support)
- `setSchedule()` full implementation — on-device timer via `get_program` / `set_program`

---

## Commits

- **Revert:** `29f8389` — `Revert "feat(daikin-wifi): fork of eriktack/hubitat-daikin-wifi as v0.1.0"`
- **Clean-room:** `b26c04f` — `feat(daikin-wifi): clean-room driver for Daikin BRP069B WiFi adapters v0.1.0`

---






---

## 2026-05-18: EZ Dashboard Not In Scope (Mads Directive)

### 2026-05-18T19:39:26Z: User directive
**By:** Mads (via Copilot)
**What:** Mads does not use Hubitat EZ Dashboard. PR #3's only useful contribution (declaring `supportedThermostatModes` / `supportedThermostatFanModes` as `JSON_OBJECT` attribute type for EZ Dashboard compatibility) is therefore dropped from the Daikin driver v0.1.2 backlog. The associated `setSupportedThermostatModes()` / `setSupportedThermostatFanModes()` runtime-override commands are also dropped — they were paired with the same EZ Dashboard scenario.
**Why:** User request — captured for team memory. Future Daikin v0.1.1+ planning sessions should not re-investigate EZ Dashboard support.


---

## 2026-05-18: Daikin WiFi v0.1.1 Hotfix (Tank)

# Decision Drop — Daikin WiFi v0.1.1 Hotfix

**Author:** Tank  
**Date:** 2026-05-18  
**Commit:** c28882f  
**Version bump:** 0.1.0 → 0.1.1 (patch)

---

## Context

Mads installed v0.1.0 on his hub immediately after ship and hit two runtime errors within minutes:

```
warn  [Daikin] sendGet failed (/aircon/get_sensor_info): Could not find matching constructor for:
      hubitat.device.HubAction(java.util.LinkedHashMap, hubitat.device.Protocol, java.util.LinkedHashMap)
warn  [Daikin] sendGet failed (/aircon/get_control_info): Could not find matching constructor for:
      hubitat.device.HubAction(java.util.LinkedHashMap, hubitat.device.Protocol, java.util.LinkedHashMap)
error groovy.lang.GroovyRuntimeException: Cannot read write-only property: schedule on line 685 (method updated)
```

Both errors fired on first `Save Preferences` after install — polling never started.

---

## Bug 1 — Write-only property: schedule (line 685)

**Root cause:** `def setSchedule(schedule)` (Thermostat capability command stub, line 312) follows Groovy's JavaBean naming convention. The method name `setSchedule` creates a write-only pseudo-property named `schedule` on the driver object (setter exists, no getter). Hubitat's sandbox then cannot distinguish between:
- Reading the `schedule` property (to invoke it as a closure) — FAILS: write-only
- Calling the platform's `schedule(String cron, String method)` built-in — should succeed but is shadowed

When `registerSchedules()` called `schedule(fastCron, "refresh")` at line 685, Groovy's dynamic dispatch resolved `schedule` as the write-only property rather than the platform method → runtime error.

**Fix:** Replaced both `schedule(cron, method)` calls in `registerSchedules()` with idiomatic `runEvery*` platform methods (switch on `refreshInterval` enum: 1/5/10/15/30 minutes). `runEvery30Minutes("refreshEnergy")` replaces the fixed 30-minute energy cron. This entirely avoids invoking `schedule` by name and sidesteps the naming conflict permanently.

**Lines changed:** 683–697 (registerSchedules method rewritten)

**Lesson:** Any driver implementing the Thermostat capability MUST NOT call `schedule(cron, method)` — use `runEvery*` methods instead. The `setSchedule` command stub is unavoidable for capability compliance; the workaround lives in the caller.

---

## Bug 2 — HubAction constructor signature (lines 413–418)

**Root cause:** The driver called `new hubitat.device.HubAction(Map params, Protocol protocol, Map options)` — a 3-argument constructor that does NOT exist in current Hubitat firmware. The `callback` key was placed in a separate third `Map options` argument.

**Valid HubAction constructors for LAN HTTP:**
| Signature | Status |
|---|---|
| `HubAction(String action)` | ✅ valid |
| `HubAction(String action, Protocol protocol)` | ✅ valid |
| `HubAction(String action, Protocol protocol, String dni)` | ✅ valid |
| `HubAction(String action, Protocol protocol, String dni, Map options)` | ✅ valid |
| `HubAction(Map params)` | ✅ valid |
| `HubAction(Map params, Protocol protocol)` | ✅ valid — **preferred for LAN GET** |
| `HubAction(Map params, Protocol protocol, Map options)` | ❌ does NOT exist |

**Fix:** Moved `callback: callbackMethod` into the `params` Map; dropped the third argument. Now uses the documented 2-arg form:
```groovy
sendHubCommand(new hubitat.device.HubAction(
    [method: "GET", path: path,
     headers: ["HOST": "${ip}:${port}", "Accept": "*/*"],
     callback: "handlerMethodName"],
    hubitat.device.Protocol.LAN
))
```

**Lines changed:** 413–418 (sendGet method, HubAction constructor)

**Note:** The v0.1.0 history entry for "HubAction callback pattern" had documented the broken 3-arg form. Superseded by this fix.

---

## Other findings (smoke check)

- No other `schedule(cron, method)` calls elsewhere in the file — only the two in `registerSchedules()` (now fixed).
- No other HubAction calls beyond `sendGet()` — only one call site.
- Sandbox compliance unchanged: no `System.arraycopy`, `java.util.zip`, reflection, or app-only prefs.
- packageManifest.json valid JSON; both `version` fields bumped to `0.1.1`.

---

## Lessons Learned

1. **`schedule` is a write-only property** — NEVER call `schedule(cron, method)` in a driver that also defines `setSchedule()`. Use `runEvery*` methods.
2. **HubAction 3-arg Map variant does not exist** — `HubAction(Map, Protocol, Map)` is not a valid overload. Callback belongs inside the params Map when using the 2-arg `HubAction(Map, Protocol)` form.
3. **Test on first install before declaring shipped** — both bugs were immediately visible on first `Save Preferences`. A quick hub install smoke test before tagging would catch these.

