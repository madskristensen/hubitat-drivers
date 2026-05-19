---
name: "community-driver-audit-before-build"
description: "How to audit an existing community Hubitat driver before deciding to build a competing or replacement driver."
domain: "process"
confidence: "high"
source: "earned — Rainbird LNK feasibility research, 2026-05-18"
---

# Skill: Community Driver Audit Before Build

Use this skill when Mads asks "should we build a driver for X?" and a community driver may already exist.

---

## The Core Question

> "Is the existing driver good enough, or does a gap justify building our own?"

A gap justifies building ONLY if one or more of these is true:
1. The existing driver is **stale** (last commit >12 months, no HPM listing, no active forum thread)
2. The existing driver has a **structural flaw** we can fix better (wrong architecture, blocking vs. async, no parent/child when needed)
3. The existing driver is missing a **capability Mads specifically needs** (e.g., HealthCheck, specific command, dashboard attribute)
4. The existing driver uses **sandbox-unsafe patterns** that will fail on current Hubitat firmware

Otherwise: IMPROVE-EXISTING. File issues first. Fork only if maintainer is unresponsive.

---

## Audit Checklist

Run in order. Document each item.

### 1. Find all drivers (5 min)

Search GitHub:
- `site:github.com hubitat {device-name}`
- `site:github.com {device-name} groovy driver`
- `{device-name} hubitat` directly on github.com search

Search Hubitat community forum (community.hubitat.com):
- `{device-name}` keyword
- Check if any result links to a GitHub driver

Check HPM (Hubitat Package Manager) catalog:
- `https://github.com/HubitatCommunity/hubitatpackages` — search by keyword
- If a driver is HPM-listed, it has at minimum some community adoption

### 2. For each driver found, evaluate:

| Field | How to find it | Red flag |
|---|---|---|
| Last commit date | GitHub repo insights | >12 months = stale |
| Version number | File header or releases | v0.x still = early-stage |
| Architecture | Read capability declarations | Single parent without children when multi-zone needed |
| Protocol approach | Look for `httpPost`, `asynchttpPost`, `HubAction`, `rawSocket` | Blocking `httpPost` is suboptimal; `HubAction` Map-based is known broken |
| Encryption | Look for `javax.crypto.Cipher` imports | If needed and absent = gap |
| HPM listing | Search HubitatCommunity/hubitatpackages | Not listed = no install base |
| Active issues/PRs | GitHub issues tab | Open unanswered issues = maintenance risk |
| Forum thread | community.hubitat.com link in README | Dead thread = abandoned |

### 3. Gap analysis against our repo standards

Compare against our repo's non-negotiables:
- [ ] `asynchttpPost` / `asynchttpGet` (not blocking `httpPost`)
- [ ] Per-zone child devices for multi-zone devices
- [ ] `HealthCheck` capability with `ping()` for local-LAN devices
- [ ] `emitIfChanged` pattern (no duplicate event spam)
- [ ] No `HubAction` Map-based constructors
- [ ] Correct encryption if required (full byte-range IV, not ASCII-restricted)
- [ ] `SecureRandom` for IV generation (not `new Random()`)

### 4. Maintenance status classification

| Status | Definition | Recommendation |
|---|---|---|
| ✅ Active | Commit within 3 months, open issues get responses | IMPROVE-EXISTING |
| ⚠️ Drifting | Last commit 3–12 months, some open issues unanswered | Evaluate gaps; file issues; watch for 30 days |
| ❌ Stale | Last commit >12 months OR no response to recent issues | BUILD or fork |
| 🔴 Abandoned | README says abandoned, no HPM, no forum thread | BUILD from scratch |

### 5. Decision matrix

```
Existing driver: Active + No gaps → USE IT
Existing driver: Active + Gaps exist → File issues; wait 30 days → fork if unresponsive
Existing driver: Drifting + Minor gaps → IMPROVE-EXISTING (PR or fork)
Existing driver: Stale + Your use case works → Evaluate carefully; maybe still use it
Existing driver: Stale + Gaps → BUILD (cite which gaps justify it)
No driver exists → BUILD (normal process)
```

---

## Documenting Your Verdict

In the feasibility memo, include per-driver:
- Repo URL + file path
- Last commit date
- Capabilities exposed
- Architecture (flat/parent-child)
- Protocol approach
- Encryption handling (if any)
- Known issues
- **Maintenance status** (from table above)
- **Quality verdict** (one sentence)

---

## Reference Case: Rainbird LNK (2026-05-18)

**Found:** 2 drivers
- MHedish v1.0.0.0 (2026-05-07) — Active, HPM, parent/child, correct encryption → **Use it**
- craigde/jbilodea v0.92 (2020-08-27) — Stale, no children → **Superseded**

**Verdict: IMPROVE-EXISTING.** MHedish's driver covers all functionality. Identified improvement opportunities:
1. Switch from blocking `httpPost` to `asynchttpPost` (our standard)
2. Add `HealthCheck` capability (not in MHedish)
3. Use `SecureRandom` instead of `new Random()` for IV generation (minor crypto hygiene)

These are PR-worthy improvements, not reasons to build a competing driver from scratch.

**Full memo:** `.squad/decisions/inbox/cypher-rainbird-lnk-feasibility.md`
