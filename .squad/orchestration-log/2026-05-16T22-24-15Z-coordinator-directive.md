# Orchestration Event — 2026-05-16T22:24:15Z

**Source:** Coordinator
**Agent/Actor:** Copilot (Coordinator role)
**Requested by:** Mads Kristensen
**Mode:** Direction capture (no agent spawn)
**Why:** User confirmed local-only scope mid-session. Coordinator captures this decision for team visibility. No new agent work triggered — existing Tank, Cypher, Trinity, Switch, Link tasks remain in scope but scoped to local-only path.

---

## Files Written
- `.squad/decisions/inbox/copilot-directive-local-only.md` — captured user scope decision

---

## Outcome
**User directive captured and flagged to team:**
- Gemstone Lights driver is **local LAN only**, no cloud Cognito/AWS implementation.
- v0.2.0 target: working local HTTP protocol via controller at 192.168.1.238.
- Next user actions: `curl -v` and port scan to probe local API endpoints.
- All agent scopes tightened accordingly (Tank skips Cognito, Cypher focuses on local probe strategy, Trinity/Switch/Link adapt test/doc scope).
