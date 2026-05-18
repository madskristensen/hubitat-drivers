# Session Log — 2026-05-17 Parallel Tracks: Touchstone Socket + Gemstone Zones

**Session:** 2026-05-18T01:41:11Z (spawned 2026-05-17)  
**Requested by:** Mads Kristensen  
**Scribe:** Copilot (Scribe agent)

---

## Overview

Two parallel agent tracks launched to extend two flagship drivers with major feature sets:
1. **Touchstone v0.1.18** — Persistent socket + Tuya push subscriptions (Tank-12)
2. **Gemstone v0.4.10** — Multi-controller zones / named controller binding (Tank-13)

Upstream research (Cypher-3) validated Gemstone API feasibility and recommended Option A-lite architecture.

---

## Track 1: Cypher → Tank-12 — Touchstone Persistent Socket (v0.1.18)

### What was needed
- Prior design: open→send→wait→close per command → socket idle between polls → missed push frames → stale dashboard state (60 s poll intervals)
- New design: persistent long-lived connection → incoming push frames processed immediately → live state in dashboards

### What was delivered
- `initialize()` opens socket once and never closes (`interfaces.rawSocket.connect()`)
- Heartbeat every 10 s (Tuya devices timeout ~30 s idle) — Tuya cmd 9 with **zero-byte payload** (special case to skip AES padding)
- Reconnect backoff: 5 s → 30 s → 60 s → 300 s; suppressed within 3 s of intentional close (to avoid loop on `updated()`)
- Push frames (cmd 8) routed through existing `applyDps()` — same state handling as polled responses
- `socketState` enum attribute: [open, closed, reconnecting, error] — visible on dashboards
- Poll interval: 60 s → 300 s (push frames now carry live data; polling is safety net)
- Tests 34–37 added (socket persistence, heartbeat, reconnect backoff, push ingestion)

### Risks / unknowns for Switch
- ⚠️ Tuya v3.3 enforces **single TCP slot** — SmartLife app on same LAN will cause reconnect loop (driver retries with backoff; now more visible via `socketState`)
- ⚠️ Push/response ambiguity: both arrive as cmd 8; cannot distinguish at protocol level → both route through `applyDps()` → acceptable (write already happened; next refresh reconciles)
- ⚠️ Heartbeat format must be truly empty bytes — if Sideline Elite firmware rejects cmd 9, look for `[Touchstone] Heartbeat failed` in logs

---

## Track 2: Cypher → Tank-13 — Gemstone Multi-Controller Zones (v0.4.10)

### What was needed
- Gemstone zones = multiple physical controllers in homegroup (e.g., "Front of House", "Eaves", "Soffit")
- Prior driver: always picked first controller, logged warning if more existed, discarded rest
- New design: let user specify which controller to bind to via a `controllerName` preference
- No new API endpoints needed; cloud API already supports per-zone control via `deviceOrGroupId` query parameter

### What was delivered
- `controllerName` preference: optional text field (blank = backward-compat first-device behavior)
- `handleDevicesResponse()`: case-insensitive name-match with graceful fallback (if no match: warn + use first device)
- `state.availableControllers`: diagnostic state variable (comma-joined list of available controller names, sorted)
- `USER_AGENT` synced to v0.4.10 (was stale at 0.4.8)
- Multi-instance pattern documented: one Hubitat device per Gemstone zone, each with own `controllerName`; no parent/child architecture
- Tests 19–22 added (multiple controllers discovered, controllerName binding, graceful degradation, independent operation)
- Reusable skill `.squad/skills/multi-controller-binding/SKILL.md` created for future drivers

### Why Option A-lite (not parent/child)
- Typical homegroup has 2–4 zones; parent/child adds Groovy complexity without benefit
- Hubitat users already manage multiple devices natively; Rule Machine can group them
- Each device independently authenticates (shared Cognito client ID, separate token cache) and polls
- No inter-device coordination needed

### Risks / unknowns for Switch
- ⚠️ Controller naming stability: are names unique per homegroup? If two controllers have same name, `controllerName` preference would be ambiguous → fallback needed (index preference or UUID advanced option)
- ⚠️ Device group schema unconfirmed (may unlock simultaneous multi-zone control but not yet captured from real account)
- ⚠️ Per-zone effect catalog: do all physical controllers support the same full pattern/effect catalog? (Likely yes but not confirmed)

---

## Decisions Merged into decisions.md

5 inbox files merged (newest first):
1. `tank-touchstone-v018-persistent-socket.md` — Architecture, heartbeat format, push handling
2. `tank-touchstone-dp105-removal.md` — DP 105 (log brightness) non-writable; command removed
3. `tank-touchstone-charcoal-color-labels.md` — DP 104 renamed from "Log Color" to "Charcoal Color" (Tuya app verified)
4. `tank-gemstone-v0410-zones.md` — Architecture decisions, Option A-lite adoption, implementation notes
5. `cypher-gemstone-zones-feasibility.md` — API spec, zone model, reference implementations (sslivins/pygemstone, sslivins/hass-gemstone)

---

## Cross-Team Work Readiness

### For Switch (Hardware Validator)

**Touchstone TESTING.md Tests 34–37:**
- Socket persistence: confirm `socketState = "open"` on dashboard after 5+ minutes idle
- Heartbeat: confirm no "Heartbeat failed" errors in logs; Tuya device remains connected
- Reconnect: simulate network loss; verify backoff progression (5 s → 30 s → 60 s → 300 s)
- Push frames: press physical remote; verify dashboard updates within 1 s (not waiting for 5-minute poll)

**Gemstone TESTING.md Tests 19–22:**
- Multiple controllers: confirm `devices.size() > 1` from real account
- Binding: create two Hubitat devices with different `controllerName` values (e.g., "Front of House" and "Eaves")
- Graceful degradation: set `controllerName = "Nonexistent"` and verify warning logs but driver continues on fallback
- Independent operation: toggle lights on two separate Hubitat devices; confirm each controls the correct physical zone

---

## Session Summary

**Agents spawned:** 3 (Cypher-3, Tank-12, Tank-13)  
**Mode:** All background (no blocking dependencies)  
**Commits:** 2 (67f905b Touchstone v0.1.18, e35b666 Gemstone v0.4.10)  
**Files modified:** 8 (2 driver files, 4 manifest/doc files, 2 test files)  
**Tests added:** 8 (Tests 34–37 Touchstone; Tests 19–22 Gemstone)  
**New skill:** 1 (`.squad/skills/multi-controller-binding/SKILL.md`)  
**Decisions added:** 5 (inbox → decisions.md)  
**Total duration:** ~1300 s (~22 min)

---

## Next Steps

1. **Switch hardware validation** — Run Tests 34–37 (Touchstone) and Tests 19–22 (Gemstone) on real hardware; report findings
2. **Pending Mads review** — Touchstone DP 105 finding (non-writable); charcoal color rename (breaking change, ENUMs instead of numbers)
3. **Future:** Device group schema capture (if Mads has groups configured in Gemstone app) → unlock simultaneous multi-zone control

---

**Scribe:** Authored by Copilot (Scribe agent)  
**Date:** 2026-05-18T01:41:11Z  
**Status:** Complete — Decisions archived, inbox cleared, logs written, ready for commit.
