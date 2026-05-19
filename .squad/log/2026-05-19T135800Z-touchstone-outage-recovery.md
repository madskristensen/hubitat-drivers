# Session Log: Touchstone Fireplace Outage Recovery

**Date:** 2026-05-19  
**Timestamp:** 2026-05-19T135800Z  
**Outcome:** SUCCESS — Recovery playbook delivered, 3 driver improvements flagged for Tank

## Summary

Mads reported unresponsive Touchstone fireplace. Cypher analyzed retry loop logs; root cause identified as likely localKey rotation. Recovery sequence (power cycle → ping → re-fetch key) restored operation. Flagged 3 retry-logic defects in driver for future hardening:

1. **retryIndex reset on heartbeat ACKs** (line 869): Resets backoff on every frame, including Tuya heartbeats with no AES payload. Causes infinite oscillation 5s→15s→0→5s...
2. **No retry cap**: Retry loop continues every 30s indefinitely after permanent failures.
3. **No socket recycle**: TCP half-open state never cleared after prolonged timeouts.

## Decision Record

Entry filed in `.squad/decisions.md` with implementation suggestions for Tank.

## Next

Tank reviews retry-cap decision and schedules fix (estimated 2–3 improvements).
