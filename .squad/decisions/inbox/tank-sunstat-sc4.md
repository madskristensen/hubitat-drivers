## 2026-05-18 — Tank — SunStat v0.1.10 (SC-4)

### What changed
- In `drivers/sunstat-thermostat/sunstat-thermostat-child.groovy`, `parseDeviceStateInternal()` now caches `data.Schedule.Floor.W` into `state.floorWarmth` alongside the existing `state.floorAway` cache.
- `setFloorMinTemp(temp)` now compares the clamped request against that cached warmth value and returns early with the standard debug skip log when the thermostat already matches, before issuing the read-modify-write PATCH.
- Bumped the synced SunStat parent/child/package-manifest versions to `0.1.10`; the parent change is version-sync only.

### Why
- `setFloorMinTemp()` has to PATCH both `Schedule.Floor.W` and `.A` together. Without a cached warmth value, repeated floor-min assertions still performed a no-op cloud write even when the thermostat already matched.
- This closes SC-4, the last unshipped repo-backed item from Trinity's redundant-write audit, so the SunStat audit board is now empty.

### Guardrails kept
- Null or unknown cached warmth still falls through to the PATCH, so fresh installs and first-poll devices keep working.
- Existing `state.floorAway` read-modify-write behavior is unchanged.
- No user-command digital events were removed; only confirmed no-op writes are skipped.
