# Session Log — Touchstone Log Hygiene Fix (2026-05-18T13:19:11Z)

## Summary

Tank delivered Touchstone v0.1.22 with a clean trace/debug split to address user-reported log spam (6+ lines/minute at debug level). The fix is purely additive — adds `traceEnable` preference to gate protocol firehose; no protocol behavior changes.

## User Complaint

Touchstone driver at `logEnable=true` was spamming logs:
- Heartbeat ACK every 10s
- Periodic refresh dumps every 60s
- Per-DP echoes for each property read

**Screenshot evidence:** Six or more log lines per minute, making production logs unreadable.

## Tank's Solution

Introduced `traceEnable` preference (default off, auto-disable after 30 min):
- **traceLog()** helper for protocol firehose (heartbeat, refreshes, raw dumps, unchanged DP echoes)
- **debugLog()** remains for meaningful activity (user commands, state changes, socket lifecycle)
- Matches kkossev Zigbee driver pattern — community-standard approach

**Version:** v0.1.22  
**Commit:** f53312c

## Ambiguous Follow-Up

User: "Actually, debug was on."

**Interpretation:** Unclear whether user meant (a) they had `logEnable=true` the whole time, or (b) they're questioning why debug was so chatty.

**Coordinator's judgment:** Keep v0.1.22 in place. The trace/debug split is opt-in, follows Hubitat community convention, and makes debug logs readable at production volume.

## Related Decisions

New decision record: `.squad/decisions.md` — "2026-05-18: Touchstone Driver Log Hygiene — trace vs debug taxonomy"

## Artifacts

- Driver: `drivers/touchstone-fireplace/touchstone-fireplace.groovy` (f53312c)
- Tank history: `.squad/agents/tank/history.md` (uncommitted)
- Skill update: `.squad/skills/tuya-local-groovy/SKILL.md` (uncomm. — Log Hygiene section)
- Decision drop: `.squad/decisions/inbox/tank-touchstone-log-hygiene.md` (to merge)
