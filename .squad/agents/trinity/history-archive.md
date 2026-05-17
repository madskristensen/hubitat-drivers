# Trinity — Archived History

## Learnings (Prior Sessions)

### 2026-05-16T21:07:23-07:00: Hubitat preference length limit & long-secret pattern

**Problem:** Hubitat's preference UI silently rejects values exceeding ~1024 characters.

**Solution:** Use `setRefreshToken(String token)` command instead of preference. Command STRING parameters bypass length limits. Pattern reused in SunStat v0.1.3+.

### 2026-05-16: Repo layout, conventions, and Gemstone driver architecture

**Folder structure:**
- Top-level `drivers/` (lowercase), one `kebab-case` subfolder per driver
- Each driver subfolder: `.groovy`, `README.md`, `packageManifest.json`
- Namespace: `"mads"` (consistent across drivers)

**HPM conventions:**
- Per-driver `packageManifest.json` + repo-root `repository.json`
- `id` field = UUID v4, never changed
- `minimumHEVersion: "2.3.0"` baseline (C-7/C-8)

**Gemstone design (single driver, local-only):**
- Capabilities: Actuator, Switch, SwitchLevel, ColorControl, LightEffects, Refresh, Initialize
- Polling: `runEvery5Minutes`
- Logging: `logEnable` preference, 30-min auto-off

---

## 2026-05-16T21:45:13Z — Team update: Gemstone protocol research ongoing

Cypher confirmed cloud API is documented (sslivins). Local API discovery phase underway. Tank scaffolded driver with HubAction stubs.

---

## 2026-05-16T22:24:15Z — User directive: local-only scope

Scope tightened to local controller only (no cloud path). v0.2.0 timeline tied to Mads' packet capture success.

---

## 2026-05-16T23:04:57Z — Gemstone architecture validated; v0.2.0 blocked on pcap

**Findings from driver extraction:**
- C4 driver confirmed: TCP port 80, PKCS#7 encryption
- ELAN driver: edrvc binary format
- 70+ API probes: all 404

Architecture remains sound. Next gate: Mads' UniFi pcap reveals routing envelope.

---

## 2026-05-16T20:01:41-07:00: SunStat Connect Plus — architecture design

**New patterns:**
- Parent/child from day one (multi-device reality known at architecture time)
- `Thermostat` capability with constrained modes via `supportedThermostatModes`
- `setBoost(minutes)` custom command for timed override
- Dual-sensor split: `temperature` + custom `floorTemperature`
- Temperature unit normalization with `location.temperatureScale`

**Effort:** Medium (2 sessions)

---

## 2026-05-17T03:01:41Z — SunStat v0.1.0 shipped

Parent/child driver pair complete. Tank wired full implementation. Switch drafted test plan. Awaiting Mads' real-device verification.

---

## 2026-05-16T20:01:41-07:00: Bosch Home Connect — driver architecture design

**Architecture:**
- OAuth2 via App (only Apps get `mappings {}`)
- Parent App + child Driver per door (ContactSensor capability)
- Polling first; SSE deferred pending research

**New patterns:**
- `hubitat-cloud-oauth-app` skill extracted
- `atomicState` for CSRF state parameter
- `doorAlarm` custom attribute for "left open too long"
- Folder: `*-app.groovy` naming when entry point is App

**Risk:** Bosch requires exact-match redirect URI. Community driver research gates implementation.

---

## 2026-05-17T16:45:09Z — Bosch consumer auth investigation

**Finding:** Developer portal registration is unavoidable (single 5-min cost). No consumer-auth alternatives exist.

**Verdict:** Device Flow OAuth2 design stands. User registers own client_id + client_secret at developer portal.

---

