### 2026-05-17T02:01Z: User directive — REVERSAL — `.squad/` should be committed, not gitignored
**By:** Mads (via Copilot)
**What:** Supersedes the earlier directive `copilot-directive-squad-gitignored-2026-05-16.md`. The `.squad/` folder is now **part of the public repository content** — committed and pushed alongside the driver. Do NOT add `.squad/` to `.gitignore`. If Tank-10 already added it to `.gitignore`, Tank-11 must remove that line and force-add the existing `.squad/` files in a follow-up commit.
**Why:** Mads's call — the AI-team coordination state (charters, decisions, history, orchestration logs, casting) is interesting/valuable for readers of the repo to see; demonstrates how a Squad-style multi-agent team operates. Effectively turns the repo into both a driver release AND a coordination-pattern reference.
**Implementation guidance:**
- `.gitignore` should NOT contain `.squad/`. Keep everything else from the earlier gitignore (`.copilot/`, `*.pcap`, OS junk, etc.) — those are still excluded.
- `.copilot/` stays excluded (local CLI session memory).
- `*.pcap` stays excluded (large research artifacts, not part of the driver story).
- For follow-up commits Scribe makes to `.squad/decisions.md` / agent histories / logs: those commits DO go to the public repo now. Scribe should continue its commit-and-skip-push pattern; Mads pushes when ready.
- Re-evaluate the no-secrets directive (`copilot-directive-no-secrets-2026-05-16.md`) — it's still in force, and now MORE important since `.squad/` is public. The earlier sweep already redacted creds from `.squad/`; future Scribe writes must continue redaction discipline.
