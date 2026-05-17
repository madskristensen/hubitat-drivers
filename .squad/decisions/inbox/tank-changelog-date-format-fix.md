# Tank decision — changelog date format fix

- **When:** 2026-05-17T13:21:30-07:00
- **Requested by:** Mads
- **Scope:** `drivers/touchstone-fireplace/touchstone-fireplace.groovy`

## Decision

Normalize every parsed `Changelog:` entry in the Touchstone fireplace driver to use plain `YYYY-MM-DD` dates.

## Why

The release workflow parser in `.github/workflows/release.yml` only matches changelog lines formatted as `version — YYYY-MM-DD — description`. Full ISO 8601 timestamps with time and timezone caused the v0.1.x entries to miss the regex and fail release-note generation for v0.1.5.

## Change made

Removed the `Thh:mm:ss-07:00` portion from the v0.1.5, v0.1.4, v0.1.3, and v0.1.1 changelog dates while leaving version numbers, descriptions, and code unchanged.
