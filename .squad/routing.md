# Work Routing

How to decide who handles what.

## Routing Table

| Work Type | Route To | Examples |
|-----------|----------|----------|
| Architecture, folder layout, capability selection | Trinity | "How should the repo be structured?", "Should this be a parent/child driver?" |
| Groovy driver implementation | Tank | Write/edit `.groovy` driver files, command handlers, `parse()`, preferences |
| Protocol / API research / payload specs | Cypher | "How does the Gemstone API work?", read `has-gemstone`, document endpoints |
| Test plans, manual validation, edge cases | Switch | Write test plans, validate on real device, regression checks |
| README, install docs, packaging, HPM manifests | Link | Top-level README, per-driver README, install steps, changelog |
| Code review | Trinity | Review PRs, check Hubitat idioms, suggest improvements |
| Scope & priorities | Trinity | What to build next, trade-offs, decisions |
| Session logging | Scribe | Automatic — never needs routing |
| Work monitoring / backlog | Ralph | "Ralph, go", "status", "what's on the board?" |

## Issue Routing

| Label | Action | Who |
|-------|--------|-----|
| `squad` | Triage: analyze issue, assign `squad:{member}` label | Lead |
| `squad:{name}` | Pick up issue and complete the work | Named member |

### How Issue Assignment Works

1. When a GitHub issue gets the `squad` label, the **Lead** triages it — analyzing content, assigning the right `squad:{member}` label, and commenting with triage notes.
2. When a `squad:{member}` label is applied, that member picks up the issue in their next session.
3. Members can reassign by removing their label and adding another member's label.
4. The `squad` label is the "inbox" — untriaged issues waiting for Lead review.

## Rules

1. **Eager by default** — spawn all agents who could usefully start work, including anticipatory downstream work.
2. **Scribe always runs** after substantial work, always as `mode: "background"`. Never blocks.
3. **Quick facts → coordinator answers directly.** Don't spawn an agent for "what port does the server run on?"
4. **When two agents could handle it**, pick the one whose domain is the primary concern.
5. **"Team, ..." → fan-out.** Spawn all relevant agents in parallel as `mode: "background"`.
6. **Anticipate downstream work.** If a feature is being built, spawn the tester to write test cases from requirements simultaneously.
7. **Issue-labeled work** — when a `squad:{member}` label is applied to an issue, route to that member. The Lead handles all `squad` (base label) triage.
