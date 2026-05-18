# Session Log — Touchstone Child Lock, HPM Bundle, Active TCP Discovery

**Date:** 2026-05-18T02:42:00Z (UTC)  
**Session:** tank-14 (aborted), cypher-4, tank-15  
**Requested by:** Mads Kristensen  
**Status:** Complete (with one abort)

---

## Executive Summary

Complex multi-agent session with three workstreams and one abort:

1. **Tank-14 (ABORTED + REVERTED)** — Spock unit testing POC attempted, halted at user request due to excessive JVM dependencies (247MB Gradle downloads). Reverted via cab38d9. Triggered USER DIRECTIVE: no JVM-based testing frameworks.

2. **Cypher-4 (COMPLETE)** — Research on HPM multi-driver bundle feasibility and Tuya autodiscovery:
   - ✅ HPM bundle feasible; documented full implementation strategy
   - ⚠️ Tuya autodiscovery: passive UDP impossible on Hubitat, but active TCP probe viable

3. **Tank-15 (COMPLETE)** — Three shipped workstreams:
   - **Touchstone v0.1.19:** Child lock command (DP 108 boolean, setChildLock on/off)
   - **HPM Bundle v1.0.0:** Root packageManifest.json + release.yml updates
   - **Touchstone v0.1.20:** Active-TCP discovery state machine for DHCP recovery

---

## Detailed Workstreams

### Tank-14 — Spock Unit Test POC (ABORTED)

**Scope:** Build Gradle + Spock unit testing harness for hubitat-drivers repo  
**Duration:** 699 seconds  
**Status:** 🔴 ABORTED + REVERTED

**What Happened:**
- Tank-14 was building ~36 passing unit tests with Gradle + Spock framework
- Gradle wrapper + dependencies downloaded = ~247MB in `tests/` directory
- Mads immediately demanded rollback: "too many dependencies and .jar files"

**Why Stopped:**
The repo's value proposition is single-file `.groovy` drivers with **zero build-system dependencies**. Adding a parallel Gradle environment violates that principle. Users expect to clone, pick a `.groovy` file, and drop it into Hubitat — no build steps.

**Revert Details:**
- Revert commit: cab38d9 (force-reverted entire Spock commit 8a3334a)
- Gradle daemon killed (PID 56616)
- `tests/` directory deleted

**Captured Directive:**
This abort triggered a **USER DIRECTIVE** that now appears prominently in decisions.md:
- ❌ No JVM-based testing frameworks (Gradle, Maven, Spock)
- ✅ **Acceptable alternative:** Standalone Groovy scripts (system `groovy` interpreter, no wrapper, no jars)
- ✅ **Primary strategy:** Hardware validation via Switch (real Hubitat devices)

---

### Cypher-4 — Research (HPM Bundle + Tuya Autodiscovery)

**Duration:** 558 seconds  
**Status:** ✅ COMPLETE — Two decision docs delivered

#### Research Item 1: HPM Multi-Driver Bundle Feasibility

**Verdict:** ✅ **Feasible — proceed.**

**Key Findings:**
- HPM schema supports N drivers in one manifest (proven by in-repo SunStat 2-driver precedent)
- Bundle manifest goes at repo root (clean URL: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/packageManifest.json`)
- Per-driver manifests remain unchanged (additive, not replacements)
- Version coupling: bundle version independent of per-driver versions

**Release Workflow Changes Needed:**
- Add root manifest to push trigger paths
- Handle special case: `driver_dir == "."` → generate `bundle-v${version}` tag, skip changelog
- Recommendation: Option C (manual bundle tag release, not automated changelog parsing)

**User Experience:**
- Install from bundle URL → HPM shows checklist of 4 drivers
- User selects which ones to install
- HPM Match-Up deduplicates using id+name+namespace tuples

**Unknown:** HPM behavior when user installs same driver via both bundle and per-driver URLs — Switch to test before public release.

#### Research Item 2: Tuya Autodiscovery on Hubitat

**Verdict:** ⚠️ **Feasible-with-caveats — primary approach blocked, Plan B viable.**

**Key Finding:**
❌ **Passive UDP broadcast listening is NOT supported by Hubitat.**  
Hubitat staff confirmed (2018, unchanged): "You can only send out UDP messages and receive a reply to that message."  
Tuya autodiscovery normally requires listening on port 6666/6667 — not feasible.

**Plan B — Active TCP Probe (Feasible):**
✅ Explicit "Discover" button that scans /24 subnet on port 6668 (TCP)  
✅ Solves "DHCP renewal silently breaks driver" problem  
✅ ~1 hour Tank work to implement state machine

**Implementation Strategy:**
- Smart ±20 octet range first (typical DHCP drift), then full sweep if needed
- Sequential rawSocket connects (Hubitat single-thread sandbox)
- DP_QUERY frames sent to each IP; match on gwId
- ~2s timeout per IP worst case; typical scan <1 min
- Heartbeat suspended during scan to free socket

**Fallback (Recommended First Step):**
- Document DHCP reservation guidance in README
- Users set static reservation in router — prevents IP changes entirely
- Zero driver code required

**Tuya Protocol Details Captured:**
- Broadcast ports: 6666 (v3.1, plaintext) vs 6667 (v3.3, XOR-encrypted)
- v3.3 XOR key: `yGAdlopoPVldABfn` (16 bytes, repeated for payload)
- Payload schema: gwId, ip, productKey, version

**Risks for Switch to Verify:**
- gwId in v3.3 DP_QUERY response confirmation
- Hub rate-limiting on rapid TCP connects
- Throughput: worst case 8 min (2s × 254 IPs)
- Edge case: IP outside /24 subnet

---

### Tank-15 — Three Ships (Implementation)

**Duration:** 1122 seconds  
**Status:** ✅ COMPLETE — All three committed and ready for Switch validation

#### Ship 1: Touchstone v0.1.19 — Child Lock (DP 108)

**Commit:** 3a59f04  
**Deliverables:**
- `setChildLock(on|off)` command (enable/disable physical button lock)
- `childLock` attribute (enum: on/off)
- Test 38 added to TESTING.md

**Wire Protocol:**
- DP 108: Tuya BOOL type
- `true` = locked, `false` = unlocked
- Device echoes state via push frame; fallback on next poll

**Rationale:**
- Simple boolean write
- Reusable pattern for future drivers (two-line DP boolean dispatch)

**Versions:**
- Driver: 0.1.18 → 0.1.19
- HPM bundle: 1.0.0 → 1.0.1 (kept in sync)

---

#### Ship 2: HPM Multi-Driver Bundle v1.0.0

**Commit:** a0e695d  
**Deliverables:**
- `packageManifest.json` (repo root, NEW)
- `.github/workflows/release.yml` (updated)
- `README.md` (updated)
- `.squad/skills/hpm-bundle-manifest/SKILL.md` (new skill)

**What Changed:**

**Bundle Manifest:**
```json
{
  "packageName": "Mads Kristensen — Hubitat Drivers",
  "version": "1.0.0",
  "drivers": [
    // 4 drivers, all required: false
  ]
}
```

**Release Workflow:**
- Added root manifest path trigger
- Updated find command to scan both root + drivers/
- Special case for `driver_dir == "."`: generates `bundle-v${version}` tag

**README:**
- "Install all drivers via one HPM URL" section
- Version bump convention documented

**UUID Mapping (reused from per-driver manifests):**
- Touchstone: `63f16ca9-2413-418f-a5d5-b798c23452ee`
- Gemstone: `257ada29-4d65-4f90-9183-da6cc75ef908`
- SunStat Parent: `fe4da0f7-5c8f-429c-8a5d-8d5797667e1f`
- SunStat Child: `2139d8a6-3dc4-4f7c-95b4-e18ecef215f9`

**Gotchas:**
- `basename(dirname("."))` = `"."` → breaks tag generation (required special handling)
- UUID reuse is mandatory; different UUIDs = duplicate update prompts
- `release.yml` `find drivers` hard-codes prefix; root manifest would have been silently ignored

---

#### Ship 3: Touchstone v0.1.20 — Active-TCP IP Discovery

**Commit:** ffbfd08  
**Deliverables:**
- `discover` command (zero-arg button on device page)
- `networkAddress` attribute (string) — surfaces discovered IP
- Discovery state machine (191 lines)
- Test 39 added to TESTING.md
- `.squad/skills/hubitat-active-tcp-discovery/SKILL.md` (new skill)

**What Changed:**

**3A: Improved Error UX**
- Changed `openSocket()` failure from `log.warn` to `log.error` with actionable text
- Recommends DHCP reservation + press Discover button
- States IP that was tried

**3B: Discovery State Machine**
- `discover()` → builds probe queue (smart ±20 first, then 1-254 sweep)
- `discoveryProbeNext()` → sequential rawSocket.connect + DP_QUERY
- `socketStatus()` [modified] → routes errors to next probe
- `parse()` [modified] → if discoveryMode, route to discovery handler
- `discoveryHandleResponse()` → match devId, update pref, emit networkAddress
- `discoveryComplete()` → restore normal socket + heartbeat

**Guards:**
- Skip openSocket if discoveryMode
- Skip reconnectSocket if discoveryMode
- Skip sendHeartbeat if discoveryMode
- Skip normal parse post-processing if discoveryMode

**Design Decisions:**
- Fail-closed on devId (only accept exact match)
- Pre-computed probe queue (simpler than phase flags)
- Reuse `intentionalCloseAt` (no new socket logic)
- `discoveryComplete()` always calls `initialize()` (clean state handoff)

**Known Limitations (Switch to Verify):**
- gwId in v3.3 DP_QUERY response confirmation
- Hub sandbox rate-limiting on rapid TCP connects
- Scan time: 2s/IP worst case → ~8 min full sweep; smart ±20 typically <1 min
- IP outside /24 requires manual entry

**Versions:**
- Driver: 0.1.19 → 0.1.20
- HPM bundle: 1.0.1 → 1.0.2 (kept in sync)

---

## Dependency Graph

```
Tank-14 (aborted)
  ↓ [abort halts; revert via cab38d9]
  ↓
Cypher-4 (research) ──→ HPM bundle feasibility + Tuya autodiscovery analysis
  ↓ [feeds Tank-15]
  ↓
Tank-15 (implementation)
  ├─ Ship 1: Child lock (v0.1.19) [standalone]
  ├─ Ship 2: HPM bundle v1.0.0 [depends on Cypher research]
  └─ Ship 3: Discovery (v0.1.20) [depends on Cypher autodiscovery research]
```

**All shipped sequentially; no blockers.**

---

## Files Staged for Commit

**Squad documentation:**
- `.squad/decisions.md` (merged all 6 inbox files + added Tank-14 abort + USER DIRECTIVE)
- `.squad/orchestration-log/2026-05-18T02-42-00Z-tank-14.md`
- `.squad/orchestration-log/2026-05-18T02-42-00Z-cypher-4.md`
- `.squad/orchestration-log/2026-05-18T02-42-00Z-tank-15.md`
- `.squad/log/2026-05-18T02-42-00Z-touchstone-childlock-hpm-bundle-discovery.md`
- `.squad/agents/switch/history.md` (appended test areas)
- `.squad/agents/tank/history.md` (summarized, >15360 bytes)
- `.squad/agents/cypher/history.md` (summarized, >15360 bytes)

**Skills:**
- `.squad/skills/hpm-bundle-manifest/SKILL.md` (new)
- `.squad/skills/hubitat-active-tcp-discovery/SKILL.md` (new)
- `.squad/skills/multi-controller-binding/SKILL.md` (untracked from prior session)
- `.squad/skills/tuya-local-groovy/SKILL.md` (modified)

---

## QA Checklist

- ✅ Tank-14 abort captured with revert commit reference
- ✅ USER DIRECTIVE prominently documented
- ✅ Cypher research decisions merged into decisions.md
- ✅ Tank-15 three ships documented with commit SHAs
- ✅ Orchestration logs created (one per agent)
- ✅ Session log created
- ✅ Switch history appended with new test areas
- ✅ Tank + Cypher histories summarized (both >15360 bytes)
- ✅ All .squad files staged for commit
- ✅ All skills staged for commit

---

## Next Steps (After Commit/Push)

1. **Switch:** Hardware validation for Touchstone Tests 38–39 + HPM bundle install test
2. **Mads:** Review USER DIRECTIVE; confirm it supersedes any prior testing intent
3. **Team:** Reference SKILL.md files for HPM bundle + active TCP discovery patterns in future drivers
