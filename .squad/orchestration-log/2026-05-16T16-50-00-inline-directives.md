# Orchestration Log — Inline Directive Captures
**Date:** 2026-05-16
**Time:** T16:50:00-07:00
**Context:** Copilot CLI captures during Gemstone v0.2.0 development session
**Status:** Recorded

## Summary

Three inline directive captures from Mads during the Gemstone investigation session, each representing a significant scope or policy decision incorporated into team decisions.md:

1. **Architectural Breakthrough** — Packet capture across gateway + three UniFi APs confirmed controller speaks AWS IoT MQTT exclusively; local LAN API is not in use by the mobile app. This settled the v0.2.0 path definitively to cloud REST.

2. **Security Directive** — Permanent policy: never commit rotatable secrets (credentials, tokens, hashes, mgmt-keys) to on-disk files. All prior mentions scrubbed; going forward, sensitive values redacted to `[REDACTED]` with field description only.

3. **Scope Lock** — Confirmed the v0.2.0 scope amendment: cloud REST (Cognito + REST control) is the only feasible path. Pure-local remains future work pending Gemstone's public API disclosure.

## Files / Decisions Captured

### copilot-mqtt-architecture-2026-05-16.md

**Finding:** Direct evidence from gateway conntrack and AP tcpdump that the controller at 192.168.1.238 maintains a **persistent MQTT-over-TLS connection** (44.241.31.78:8883 AWS us-west-2) and never accepts local HTTP commands during phone taps.

**Impact:** Eliminated local-API-discovery as a blocker for v0.2.0. Confirmed cloud is the only available path.

**Evidence Preserved:** Packet capture output, AP neighbor table stale state, zero local traffic during active phone commands.

### copilot-directive-no-secrets-2026-05-16.md

**Policy:** Going forward, no passwords, API tokens, x_authkeys, mgmt-keys, or other rotatable secrets are to be written to any `.squad/` file. Sensitive values must be redacted to `[REDACTED]` with field name description.

**Enforcement:** Applies to decisions.md, agent history.md files, session logs, orchestration logs, and any on-disk session state.

**Rationale:** Security hygiene — git history is immutable and files may sync to public repositories.

### copilot-scope-amendment-cloud-v0.2.0-2026-05-16.md

**Amendment:** The prior "local-only, no cloud implementation" directive (issued mid-session 2026-05-16) is superseded by: **v0.2.0 uses cloud REST API (Cognito SRP + REST endpoints).**

**Reason:** MQTT architecture finding (see #1 above) proves local protocol is unfeasible without vendor disclosure. Cloud is the only path with confirmed traffic patterns.

**Future:** v0.3.0 can revisit pure-local if Gemstone shares the spec or if a parallel request unlocks disclosure.

## Broader Context

These directives and findings shaped the final v0.2.0 driver shape (Tank's decision, appended to decisions.md). The cloud-auth path, security scrubbing, and scope lock are now locked-in constraints for Tank's implementation and QA.

## Next Checkpoints

- Tank executes v0.2.0 cloud driver wiring (Cognito + REST endpoints)
- All agent histories and squad files comply with the no-secrets directive
- If local API discovery becomes available (vendor disclosure, APK decompile, SDDP analysis), v0.3.0 planning begins
