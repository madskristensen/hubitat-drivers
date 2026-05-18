# Skill: Idempotent Release Workflow — Graceful Tag Re-creation

**Created:** 2026-05-17  
**Applied in:** release.yml commit ea2a74f  
**Confidence:** low (newly captured, applied once)

---

## Problem Signature

Path-gated GitHub Actions release workflows (triggered on `packageManifest.json` changes) create git tags and GitHub releases for each driver version. When a multi-manifest workflow processes release candidates, some drivers may not have version bumps between consecutive runs — their `packageManifest.json` remains unchanged.

If the workflow attempts to re-create a tag that already exists, GitHub's API returns:
```
Reference already exists (HTTP 422)
```

This crashes the workflow run, blocking all other drivers' releases. In a bundle + per-driver manifest scenario, this is common: the bundle manifest stays at `1.0.0` across five driver releases, triggering the 422 error on re-run.

---

## Pattern

Before calling `gh api` to create the ref, probe for tag existence and gracefully skip:

```bash
if gh api "repos/${GITHUB_REPOSITORY}/git/refs/tags/${tag}" >/dev/null 2>&1; then
  echo "::notice::Skipping ${tag}: tag already exists"
  continue
fi
```

### Key Rules

1. **Probe the tag endpoint** — `gh api repos/<owner>/<repo>/git/refs/tags/<tag>` returns HTTP 200 if found, HTTP 404 if not. Redirect both stdout and stderr to `/dev/null`; only the exit code matters.
2. **Use `::notice::` for logging** — helps workflow readers understand which tags were skipped and why, without triggering a warning-level alarm.
3. **`continue` skips only the "already exists" path** — keep `set -e` semantics intact. Every other error (4xx, 5xx, timeout, permission denied) still crashes the workflow.
4. **Place the guard early** — before any work that assumes the tag is new (writing release notes, building the tag object, etc.). Wasted I/O on skipped candidates is cheap.

### Location in Workflow

In the release creation loop, **immediately after reading the candidate fields and before creating the tag object**:

```bash
while IFS= read -r candidate; do
  tag=$(jq -r '.tag' <<< "$candidate")
  # ... extract name, version, notes ...
  
  # Idempotent guard ← goes here
  if gh api "repos/${GITHUB_REPOSITORY}/git/refs/tags/${tag}" >/dev/null 2>&1; then
    echo "::notice::Skipping ${tag}: tag already exists"
    continue
  fi
  
  # ... build tag object, create release, etc. ...
done < <(jq -c '.[]' release-candidates.json)
```

---

## When to Use

- Any workflow that **iterates a candidate list** and **creates tags/releases**, where candidates can repeat versions across runs.
- **Bundle manifests + per-driver manifests** are the classic case — bundle version stays locked for many consecutive driver releases.
- **Per-driver patch releases** where multiple CI runs touch the same manifest without version bump (rare but possible if the retry loop resumes).

---

## Gotchas

- **Do NOT probe inside the tag creation step itself.** The `gh api /git/tags` (create tag object) endpoint does not fail on duplicate — it's the ref creation (`gh api /git/refs`) that throws 422.
- **Idempotency stops at tag creation.** If the tag exists but the GitHub Release is missing (rare: someone deleted the release but kept the tag), this pattern will skip the release creation. Document this edge case if it becomes a problem.
- **Leave early detection to the manifest-scanning phase** — some workflows pre-check `git rev-parse refs/tags/${tag}` to skip candidates before queuing them. This idempotent pattern is for the **release creation phase**, assuming all candidates reached the loop.

---

## Reference

- **Commit:** ea2a74f (2026-05-17)
- **File:** `.github/workflows/release.yml` lines 204–207
- **Dependency:** `hpm-release-workflow` skill (full workflow context)

---

## See Also

- **hpm-release-workflow** — Covers the full release workflow automation, candidate detection, and GitHub release body population. This skill focuses narrowly on the idempotent tag handling guard.
- **hpm-bundle-manifest** — Explains why bundle + per-driver manifests can trigger this pattern (version coupling).
