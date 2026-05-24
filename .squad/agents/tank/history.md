## 2026-05-23 — Climate Advisor v0.3.3 + isComponent clarification

**Deliverables:**
- Climate Advisor v0.3.3: New `evaluateFreeCooling` evaluator for ideal ventilation scenario (outdoor cooler than indoor at setpoint)
- isComponent behavior clarification: `isComponent: true` prevents accidental deletion/driver-change in Devices UI; does NOT hide child from Devices list (hard platform limitation)

**Learnings:**

### Free-cooling coverage gap (v0.3.3)

When outdoor is cooler than indoor at the cooling setpoint with closed windows, no existing evaluator fires:
- `evaluateCoolBreach` requires `outdoor > indoor` (opposite direction)
- `evaluateComfortOpen` requires outdoor inside mid-band (not below)
- `evaluateCoolingPreAlert` requires windows open or contacts absent (gates on `windowGatePasses = true`)

**Fix:** New evaluator `evaluateFreeCooling` fires INFO when all conditions met:
1. Outdoor cooler than indoor (optimal for ventilation)
2. Indoor at/near cooling setpoint (would otherwise run AC)
3. Thermostat in cool/auto mode
4. Contacts configured AND closed (something to open)
5. Not raining
6. AQI below warn
7. Trend not rising (avoid overshoot)

Non-overlapping via opposite logical conditions (proved in decision entry). Mads's test case (65°F outdoor, 75°F indoor) now fires INFO instead of silence.

### isComponent behavior and Hubitat platform limitations

Mads questioned whether v0.3.2's `isComponent: true` worked as described. Investigation of live Hubitat docs + 2018 staff posts confirmed:

**What `isComponent: true` does:**
- Device still appears in main Devices list
- Prevents user deletion via Devices UI ✅
- Prevents user driver change via Devices UI ✅
- Child shows "Parent app" in Device Info tab

**What it does NOT do:**
- Hide from Devices list (NO Hubitat SDK mechanism available for third-party apps to hide app-created children)
- Only device-parented children get visual indentation; app-parented children never do
- Groups and Scenes app confirms same behavior — no third-party app can hide its children from Devices list

**Changelog/README corrected** to accurate wording. Feature is genuinely useful (prevents accidental deletion) — just the *marketing* description was wrong. No code change needed; wording fix only.

**Decision recorded:** Mads accepted Option 1 (accept platform behavior; no architecture change). `isComponent: true` stays.

---

## 2026-05-23 — Climate Advisor v2 IMPLEMENTED

**Deliverables:**
- `apps/climate-advisor/climate-advisor-app.groovy` v0.1.0
- `drivers/climate-advisor/climate-advisor-device.groovy` v0.1.0
- `apps/climate-advisor/packageManifest.json` (app + driver both registered)
- `apps/climate-advisor/README.md`, `drivers/climate-advisor/README.md`

## Learnings

### Tricky implementation details

1. **`state.indoorSamples` must be `Map` not a plain Groovy map across serialization.** Hubitat serializes `state` as JSON; a plain `[:]` round-trips fine, but always re-cast with `(state.indoorSamples ?: [:]) as Map` on every read. Similarly for lists: `(state.outdoorSamples ?: []) as List`.

2. **`@Field static final` cross-reference trap.** Declared `CHILD_DRIVER` and `CHILD_NS` as separate literals so the `definition(...)` block can reference them safely; never assigned one `@Field` using another `@Field` value.

3. **Octal literal trap is real in Groovy.** Avoided leading-zero numerics (`defaultValue: 30`, never `030`). Particularly easy to hit with range defaults.

4. **`subscribeAll()` called after `reconcileChildren()`.** Order matters: children must exist before `evaluateAll()` (called via `runIn(5, evaluateAll)`) pushes events to them.

5. **House-level rain check vs zone-level.** Trinity left this open; implemented as app-level: one `house-rain-windows-open` message fires if ANY zone has an open contact. This keeps the message ID stable across zone changes.

6. **Contact open-window gate.** When no contact sensors are configured for a zone, the pre-alert fires anyway with a "(no window sensors configured)" suffix. This is Tank's documented call per spec.

7. **`extractZoneId` regex.** Used Groovy `=~` pattern match against `zone-zone${n}-` prefix. Sandbox-safe — no `Class.forName`, no reflection.

8. **Notification deduplication.** `state.lastNotificationAt` keyed by stable message ID. On each `evaluateAll` run, messages whose predicates no longer hold simply don't appear in the output list, so their throttle entry ages out naturally without explicit clearing.

### Performance optimizations applied (Mads mid-flight directive — 2026-05-23)

9. **Event-coalescing debounce.** All event handlers converted to `runIn(1, "evaluateAll", [overwrite: true])`. Only `outdoorTempHandler` does real work (O(1) sample append + trim) before scheduling; all others are pure debounce stubs. A burst of correlated events (temp + setpoint + contact) collapses to one evaluation pass.

10. **`state.childDniMap` lookup cache.** Built once in `buildChildDniMap()` (called from `initialize()` and after `reconcileChildren()`). `evaluateAll()` calls `lookupChild(key)` which reads from the Map rather than calling `getChildDevice()` on every pass — avoids N hub device-table lookups per evaluation.

11. **`state.activeMessages` for stable message timestamps.** Candidate messages from evaluate* methods carry no `ts`. `resolveMessages()` reconciles against `prevActive`: if the message text is unchanged, the original `ts` is reused. This makes the JSON blob byte-for-byte identical across evaluations when nothing changes, which causes `sendEventIfChanged` on `messages` to skip the sendEvent entirely — the biggest single win for the JSON attribute.

12. **`sendEventIfChanged` helper — 26 call sites.** Every `child.sendEvent(...)` replaced with `sendEventIfChanged(child, ...)`. Cheapest sendEvent is the one not made.

13. **Notification throttle upgraded.** `state.lastNotificationAt[msgId]` now stores `[ts: epochMs, sev: severity]` (not a plain Long). Dispatch is skipped if BOTH `(now - lastTs) < throttleMs` AND `sev <= lastSev`. This means severity escalation always breaks through the throttle window — intentional; a pre-alert that upgrades to a hard breach notifies immediately.

14. **`runIn(5, "evaluateAll")` string form.** Changed from `runIn(5, evaluateAll)` (closure/method reference) to `runIn(5, "evaluateAll")` string form — the Hubitat-correct schedulable method name pattern.

15. **`averageTemps` uses `inject` instead of `vals.sum()`.** `List.sum()` can produce Integer or BigDecimal inconsistently depending on initial element type. `inject(0.0G) { acc, v -> acc + v }` pins the accumulator to BigDecimal from the start.

### Deviations from Trinity's spec (with rationale)

- **`packageManifest.json` placed in `apps/climate-advisor/`** (not a duplicate in `drivers/climate-advisor/`) — both the app and driver entries live in one manifest. This matches the HPM single-package pattern where one entry bundles both. Trinity's spec said "Link wires into root" — leaving that for Link.
- **`indoorTrendEnabled` defaults `true`** but skip path uses `settings.indoorTrendEnabled != false` (not `== true`) to handle the unset/null case safely.
- **Severity 0 messages** (info-level contact-open) skipped from notification dispatch (`if (sev < 1) { return }`). Contact open is informational state captured in `openContactCount`/`openContacts` attributes rather than a standalone message. This avoids notification noise for a normal open window on a pleasant day.

---

## 2026-05-23 — Climate Advisor Architecture Revised: v2 Generic & Shareable (Trinity) — IMPLEMENTATION READY

**Context:** Mads provided explicit feedback on Trinity's v1 design:
1. Make it generic — all devices selectable via app preferences for HPM distribution (not hardcoded zone names / device IDs)
2. Drop HomeKit requirement — remove `ContactSensor` capability; use main page + per-zone href sub-pages (not single-page)

**Trinity completed v2 revision.** Key changes from v1:
- **All hardcoding removed:** Zones (up to 8), thermostats, contacts, weather, AQI, speakers all user-configurable via capabilities-typed inputs
- **HomeKit dropped:** Removed `ContactSensor` capability (no HomeKit requirement in v2). Rich data only via custom attributes (SharpTools-first)
- **UX pattern:** Main preferences page (global devices + thresholds) + `href` links to per-zone sub-pages (each zone: name, thermostat, contacts, temp sensors, speakers)
- **Custom attributes preserved:** severity, severityText, latestMessage, messages, houseStatus, tempTrend, activeAlertCount
- **Parent app + child virtual device architecture unchanged**

**Directives captured:** Climate Advisor must be generic/shareable; use main+sub-page UX pattern.

**Decision locked in `.squad/decisions/decisions.md` under "Architecture Proposal: Climate Advisor — v2 (Generic, SharpTools-first) — SUPERSEDES v1"**

**Read before implementing:**
- Sections 1–7 for architecture, preferences structure, logic pseudocode, subscription model, HPM registration
- Section 8 for open questions (placeholder text vs starter template; per-zone vs house-wide rain check)
- See v1 decision entry for historical context and superseded spec

**Ready for implementation. Proceed when Mads approves v2.**

---

## 2026-05-20 — Away Lights v0.8.1 Resource Cleanup (revised — aggressive)

**Task:** Implement resource cleanup enhancements; Mads clarified backcompat is not a priority pre-v1.0.0 — make breaking changes if needed.

**Changes made to `apps/away-lights/away-lights.groovy`:**
- **Enhancement 1 (unconditional):** `unschedule("offTimeHandler")` now fires on ANY Away-mode exit, not just when `turnOffOnHome=true`
- **Enhancement 2 (structural fix):** Changed `else if (turnOffOnHome)` → `else` in `modeHandler`. All cleanup runs unconditionally on Away exit.
- **Dropped no-op:** mode subscription stays permanent (cannot unsubscribe "only during X" — it's circular)

**Architecture note:** The mode subscription must remain permanent to detect Away re-entry. Future value-filtered subscription support in Hubitat would enable conditional subscription, but today the permanent subscription is correct.

---

# Historical Archive

For full record of Tank's prior work (2026-05-17 through 2026-05-19), see:
- `history-archive-2026-05-23.md` — Summary archive created when primary file exceeded 15,360-byte threshold

**Deliverables summary:** 3 community driver forks (PurpleAir v0.4.0, Fully Kiosk v0.4.6, T6 Pro v0.4.0 LIVE), Away Lights v0.1.0 app, Climate Advisor design (v1 → v2 revision awaiting implementation).

---

*See archive file for pre-2026-05-23 details*

---

## PERMANENT ARCHITECTURAL BOUNDARY (2026-05-23)

**Piston Coexistence — v0.1.0 and beyond:**

Implementation confirmed: Climate Advisor v0.1.0 is **advisor-only** with no HVAC control. Existing webCoRE pistons ("Thermostat management" and "Sunroom climate") retain permanent ownership of HVAC actions. Climate Advisor owns notifications, severity-based alerts, and predictive close-window warnings.

This boundary is **permanent and not subject to v0.2.0+ revisiting.** Reasons:
1. Pistons have been production-stable for 6 months
2. "Mode preset rules" restoration is cleaner than reimplementation
3. No overlapping writes — clean failure isolation
4. Complementary timing: predict → warn, then piston → act if needed

The earlier consideration to add per-zone `controlHvac` toggles for v0.2+ is **definitively withdrawn.** Consolidating config is not worth porting working code.

**Updated:** 2026-05-23 (Climate Advisor v0.1.0 shipped with generic zones, per-zone children, concrete trends, predictive alerts, 10 performance optimizations)
