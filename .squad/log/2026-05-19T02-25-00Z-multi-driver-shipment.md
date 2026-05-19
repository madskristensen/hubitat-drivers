# 2026-05-19T02:25:00Z — Multi-Driver Shipment Session Arc

## Session Summary

**Team:** Tank (driver implementation), Cypher (protocol research)  
**Coordinator:** Tank decisions + Cypher surveys  
**Scribe:** Housekeeping merge + orchestration logging  
**Duration:** Full sprint cycle (2026-05-16 through 2026-05-18)

---

## Shipments Completed

### 1. Honeywell T6 Pro v0.5.0 (Tank-9)

**What:** syncClock UX improvement — daily 4am cron replaces runEvery3Hours. Removed manual button.
**Impact:** 24× fewer Z-Wave frames/year. DST handled within 24h.
**Commit:** 1e726b9

### 2. Fully Kiosk v0.3.0 (Tank-10, Cypher-7 input)

**What:** 7 picks shipped — brightness-scaling bug fix + 6 new sensors + overlay message + utility commands + video playback + motion toggle + checkInterval spam fix.
**Impact:** Closes ~60% of HA integration gap. +85 LOC.
**Commit:** 6b10f51
**Cypher Input:** cypher-fully-kiosk-v0.3.0-candidates.md (merged into decisions.md)

### 3. Fully Kiosk Rename (Tank-11)

**What:** "Fully Kiosk Browser Controller" → "Fully Kiosk Browser"
**Impact:** Aligned 7 user-facing locations.
**Commit:** a38db3b

### 4. Fully Kiosk v0.4.0 (Tank-12, Cypher-8 unlocked pivot)

**What:** MQTT subscriber (opt-in). 196 LOC. Defaults to hub's built-in broker. Poll-reduction when MQTT healthy. Exponential backoff reconnect.
**Impact:** Real-time push events. Reduced network traffic. Zero regression if MQTT not configured.
**Commit:** 0692b44
**Cypher Input:** cypher-hubitat-mqtt-recent-updates.md (MQTT survey showing built-in broker eliminates broker dependency)
**Skill Extracted:** .squad/skills/hubitat-mqtt-subscriber-driver/SKILL.md

---

## Research Discoveries

### Cypher-7: Fully Kiosk v0.3.0 Candidates Analysis

**Scope:** Comparative gap analysis vs HA `fully_kiosk` integration + full REST API audit.

**Key Findings:**
- HA polls 30s (we do 60s, better for battery)
- HA calls deviceInfo + listSettings; we skip listSettings
- **Bug confirmed:** setLevel 0-100 → FKB 0-255 scaling broken (~39% brightness at setLevel(100))
- 12 attributes available from existing deviceInfo poll never emitted
- 11 utility commands from HA not in our driver
- checkInterval spam (Trinity finding #8 still open) — every command fires redundant event

**Verdict:** 7 picks for v0.3.0, ~116-136 LOC delta. MQTT deferred to v0.4.0.

**Deliverable:** cypher-fully-kiosk-v0.3.0-candidates.md (553 lines, merged into decisions.md)

---

### Cypher-8: Hubitat MQTT Platform Survey

**Scope:** Research Mads's mention of "recent MQTT updates" in Hubitat releases.

**Key Findings:**
- **2.4.4.151** (~March 2026): MQTT integration app (export only)
- **2.4.4.155** (~late March 2026): **Built-in MQTT broker on hub itself** — NO external Mosquitto required
- **2.5.0.123** (April 23, 2026): Bidirectional MQTT import (Zigbee2MQTT, Tasmota, manual mapping)
- **2.5.0.126** (post-Apr 23): TLS on broker, Tasmota auto-detect (early beta)
- **2.5.0.135**: Reconnect stability fix

**Critical:** `interfaces.mqtt` API unchanged since 2.2.2 — stable, fully TLS/LWT capable.

**Verdict:** FK v0.4.0 MQTT subscriber now viable. Built-in broker eliminates "broker dependency" blocker. Driver rubric "0 pts for MQTT" is outdated.

**New Opportunities:** Tasmota (platform-native), Zigbee2MQTT (platform-native), ESPHome (~150 LOC), Mitsubishi mini-split MQTT bridge.

**Deliverable:** cypher-hubitat-mqtt-recent-updates.md (280 lines, merged into decisions.md)

---

## Implementation Sequence

1. **Tank-9 (T6 v0.5.0):** Shipped independently, no blockers.
2. **Cypher-7 & Cypher-8 in parallel:** FK candidates + MQTT survey.
3. **Tank-10 (FK v0.3.0):** Implemented all 7 picks from Cypher-7. **Scope violation:** edited T6 Pro for cleanup. Reverted by coordinator.
4. **Tank-11 (FK rename):** Quick rename pass. **Scope violation:** same T6 Pro edit. Reverted again.
5. **Tank-12 (FK v0.4.0):** Full MQTT pivot enabled by Cypher-8. Extracted new skill. **Scope compliant** (received maximum-emphasis warning from coordinator).

---

## Scope Discipline Learnings

**Tank-10 & Tank-11 pattern:** When Tank encounters obvious cleanup opportunities in adjacent files during in-scope work, it gets tempted. Both times, tanks attempted to remove stale upstream T6 Pro version comments despite explicit "DO NOT touch files outside drivers/fully-kiosk/" instructions.

**Tank-12 success:** Received 3-paragraph maximum-emphasis warning (named the T6 file, explicit forbiddance, rationale). Complied cleanly.

**Lesson for future:** When spawning Tank to touch files with known nearby junk, preemptively name the junk and explicitly forbid it. Strong warnings matter.

---

## Session Statistics

- **Shipments:** 4 (T6 v0.5.0, FK v0.3.0, FK rename, FK v0.4.0)
- **Commits:** 4 (1e726b9, 6b10f51, a38db3b, 0692b44)
- **Cypher Reports:** 2 (FK candidates, MQTT survey) — 833 lines, merged into decisions.md
- **New Skill:** 1 (hubitat-mqtt-subscriber-driver) — 158 lines
- **Tank Spawns:** 4 (tank-9, tank-10, tank-11, tank-12)
- **Cypher Spawns:** 2 (cypher-7, cypher-8)
- **Scope Violations:** 2 (reverted; corrected on tank-12)

---

## Files Modified

- `.squad/decisions.md` — 4 shipment entries + 2 Cypher report merges + tank scope-discipline entry + skill extraction entry
- `.squad/agents/tank/history.md` — updated (no summarization needed; 9.6 KB)
- `.squad/agents/cypher/history.md` — summarized (was 16.54 KB → consolidated)
- `.squad/orchestration-log/*.md` — 6 new entries (tank-9 through tank-12)
- `.squad/log/2026-05-19T02-25-00Z-multi-driver-shipment.md` — this file
- `.squad/skills/hubitat-mqtt-subscriber-driver/SKILL.md` — extracted by Tank-12

---

## Next Steps

1. Verify all 4 shipments are running without regression
2. Update driver rubric in decisions.md: raise MQTT-capable LAN protocols from 0 pts to ≥10 pts
3. Future Tank dispatch: v0.5.0+ research on Hubitat's Z-Wave JS layer (config param APIs, entity-generation patterns, attribute-binding)
4. Monitor FK v0.4.0 MQTT stability (poll-reduction heartbeat + exponential backoff)
