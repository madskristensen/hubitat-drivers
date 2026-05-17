### 2026-05-17T13:21:30-07:00: Directive — Scribe must push after commit

**By:** Mads (via Copilot — coordinator captured a process gap)

**What:** When Scribe commits to `main` (or any branch where releases auto-trigger), Scribe MUST also `git push` the commits to origin. Local commits that never reach GitHub block the release workflow from firing.

**Why:** Discovered when reviewing release readiness for the Touchstone driver. The repo's `.github/workflows/release.yml` triggers on push to main when any `drivers/**/packageManifest.json` changes — it auto-creates `{slug}-v{version}` tags + GitHub Releases by parsing the driver's `Changelog:` block. We had 9 Touchstone commits sitting locally that GitHub never saw, so no Touchstone releases were ever cut.

**Required Scribe charter update:**
- Add to the standard task list, AFTER `GIT COMMIT`:
  > **8. GIT PUSH:** After a successful commit on `main`, run `git push origin main`. If push fails (auth, conflict, etc.), surface a HEALTH REPORT line — never silently skip. If running on a feature branch, push the current branch (`git push -u origin <branch>` on first push).
- The "Skip if nothing staged" semantics on commit also apply here — if no commit was made, no push needed.

**Why this matters specifically for this repo:**
- The release pipeline is push-driven. Local-only commits create silent release gaps.
- Affects every driver: Gemstone, SunStat, Touchstone all rely on the same release.yml.

**Edge cases Scribe should handle:**
- Auth failure → report to user, don't loop
- Diverged remote (`! [rejected] non-fast-forward`) → report to user; do NOT auto-rebase or force-push
- Push to a protected branch where CI rules block direct push → report; the user may need a PR flow
