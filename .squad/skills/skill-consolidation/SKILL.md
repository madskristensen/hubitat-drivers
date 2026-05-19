---
name: "skill-consolidation"
description: "When two agents produce overlapping skills in one session, consolidate by merging unique content into the highest-confidence existing skill."
domain: "team-process"
confidence: "low"
source: "earned"
---

## Rule

When multiple agents independently produce skills on the same topic in a single session, consolidate rather than keeping duplicates:

1. Identify the UNIQUE content in each skill that is NOT already in the highest-confidence existing skill on that topic.
2. Merge unique bits into the highest-confidence skill, respecting its frontmatter style and section structure.
3. Add cross-reference links instead of duplicating content already covered by complementary skills.
4. Delete duplicate skills via `git rm`.
5. Do NOT bump confidence on consolidation — confidence only increases on independent re-observation in future sessions.

## Why

Consolidation reduces skill fragmentation, keeps the skill graph navigable, and preserves the confidence lifecycle (low → medium/high only after repeated independent confirmation).
