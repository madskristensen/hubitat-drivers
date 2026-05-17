### 2026-05-17T01:59Z: User directive — agents do not push or open PRs without explicit per-task approval
**By:** Mads (via Copilot)
**What:** Agents may prepare files, commit locally, and document exact next-step commands, but must NOT execute `git push`, `gh pr create`, `gh repo create --push`, or any other operation that mutates a remote (origin or upstream forks) on Mads's behalf. The user owns all remote-touching operations manually after reviewing the local state.
**Why:** Mads wants visibility/control over what lands in his GitHub account, especially for cross-org actions like opening PRs against community repos (e.g., the HPM `hubitat-packagerepositories` master list). Local commits are fine — they're easy to undo with `git reset`. Pushes and PRs aren't.
**Implementation guidance:**
- Agents should run all local prep: `git init`, edits, `git add`, `git commit`, `gh repo fork --clone` (to a local clone is fine, but no upstream push afterwards)
- Agents must NOT run: `git push`, `gh pr create`, `gh repo create --push`, `gh release create --target=<remote>`
- Agents output a clearly-marked "🚀 NEXT STEPS FOR MADS" block at the end of their report listing the exact commands to run, in order, to publish what they prepared
- Carve-out exception: this directive does NOT apply to Tank-10 which was already mid-flight when the rule was set. v0.4.0 may already be on GitHub via that earlier run.
