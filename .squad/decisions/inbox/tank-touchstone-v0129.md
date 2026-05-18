## 2026-05-18 — Tank — Touchstone v0.1.29 (perf todos #6/#7)

### What changed
- Removed the hot-path `state.lastDps = dps` write from `processFrame()` after re-grepping the driver and confirming there are no `state.lastDps` readers to migrate.
- Added one-time `state.remove("lastDps")` cleanup in `initialize()` so upgraded devices drop the stale state key without reintroducing parse-path state churn.
- Reworked `concatBytes()`, `sliceBytes()`, `startsWithBytes()`, and `protocol33HeaderBytes()` to use primitive `int` counters, plus `System.arraycopy(...)` for contiguous copies in concat/slice/header assembly.
- Bumped the Touchstone driver metadata/changelog to `0.1.29`, updated `drivers/touchstone-fireplace/packageManifest.json` to `0.1.29`, and captured the byte-helper optimization pattern in `.squad/skills/tuya-local-groovy/SKILL.md`.

### Why
- Both requested fixes live on the Tuya v3.3 send/receive hot path. Removing dead state writes and boxed byte-copy loops reduces Hubitat overhead without touching AES framing, the pure-Groovy CRC32 implementation, or any reflection-sensitive sandbox areas.

### Guardrails kept
- No reflection APIs introduced.
- Pure-Groovy CRC32 path left unchanged.
- Helper surface stays on plain `byte[]`; only the counter/copy mechanics changed.
