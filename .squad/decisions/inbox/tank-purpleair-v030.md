## 2026-05-18 — PurpleAir v0.3.0 shipped

**Author:** Tank
**Requested by:** Mads Kristensen
**Status:** completed

### Scope shipped
- Added a `parseJson` guard for blank `search_coords` so geolocation refresh exits early with a warning instead of throwing.
- Added async response JSON/body guards so empty PurpleAir bodies log and bail instead of crashing the callback.
- Updated API-key onboarding text/docs to point at [develop.purpleair.com](https://develop.purpleair.com/).
- Added `pm2_5`, `temperature`, `humidity`, and `confidence` attributes; bumped header, README, and manifest to v0.3.0.

### Architectural choices
- **Blank geolocation input does not fall back to single-sensor mode.** Falling back would silently change the user's data source; v0.3.0 warns and leaves the driver state unchanged.
- **Averaged `confidence` emits the lowest contributing sensor score.** This is the most conservative, user-visible summary of aggregate quality after the hardcoded `>= 90` filter.
- **Temperature/humidity averages skip missing sensor values.** AQI averaging still requires valid PM2.5, but the new attributes do not let null temperature/humidity fields poison the aggregate.
