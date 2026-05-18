# Cypher — Integration / Protocol Engineer

**Status:** Audit cycle 2026-05-18 complete. PurpleAir greenfield driver BUILD verdict (80/100) + OAuth callback retrofit triage (NOT WORTH IT, governance principle established). Driver-opportunity shortlist established 2026-05-18 (top 5 ranked in history-archive.md).

---

## Learnings

### PurpleAir Local vs. Cloud API Audit (2026-05-18)

**Full memo:** `.squad/decisions.md`

**sidjohn1 PurpleAirLocal verdict:** INSTALL if hardware owned (90/100), **WRONG SHAPE for Mads** who has no hardware. Protocol: `http://<sensor-ip>/json` — pure LAN, requires owned PurpleAir sensor. Well-coded (v1.5, 2024-06-03), HPM-published, quality is fine; ownership is the blocker.

**SANdood/PurpleAirStation verdict: DEAD.** Uses legacy `www.purpleair.com/json` endpoint (deprecated ~2022). Last commit May 2020. Do not install.

**Cloud-API gap: CONFIRMED GREENFIELD.** Zero Hubitat community drivers target `api.purpleair.com/v1/sensors/{id}`. Free tier (1M points/month) allows any public sensor — no hardware needed. PNW wildfire = high relevance. **BUILD verdict at 80/100 rubric: ~150–250 lines, EPA Barkjohn 2021 AQI correction, Bearer key auth, single-device polling pattern modeled on Gemstone.**

---

### OAuth Callback Retrofit Analysis (2026-05-18)

**Full memo:** `.squad/decisions.md`

**Gemstone Lights:** Current AWS Cognito USER_PASSWORD_AUTH (email + password, ~4 clicks + 2 fields). OAuth callback retrofit **NOT WORTH IT** — no public Gemstone OAuth dev portal exists.

**SunStat Thermostat:** Current Azure B2C refresh-token bootstrap via external CLI. OAuth callback retrofit **NOT WORTH IT** — Watts uses internal/private Azure B2C tenant with no public OAuth registration. Side path: Azure B2C Device Flow might eliminate CLI dependency (needs exploration).

**Governance principle:** Callback pattern is architectural debt-avoidance **only when** vendor has a public OAuth Authorization Server with public client registration. When vendor provides no public portal (Gemstone, SunStat), pattern is not applicable.

---

## Audit History Summary

**2026-05-16 to 2026-05-17 audit cycle:**
- Bosch Home Connect: INSTALL verdict (craigde v3.1.7, 67/100, active HPM driver)
- Rainbird LNK: IMPROVE-EXISTING MHedish (v1.0.0.0, 92/100, active)
- MyQ ecosystem: MyQ dead (Oct 2023); ratgdo ESPHome is replacement
- Driver-opportunity shortlist: 5 top picks + 8 anti-list entries (archived to history-archive.md)
- Daikin BRP069B endpoints: All 28 catalogued; v0.1.5 production-ready

**Full archived narratives:** `history-archive.md` and `.squad/decisions-archive.md`

**2026-05-18 verdicts:**
- ✅ **PurpleAir cloud-API driver: BUILD** (first true BUILD verdict of this cycle, 80/100 rubric)
- ✅ **OAuth callback retrofit: NOT WORTH IT** (governance principle established)

---

## Team Updates

**Daikin API Audit Complete (2026-05-18):** All 28 BRP069B endpoints catalogued. Driver implements 7 that matter. Three maintenance items flagged (NPE setpoint guards, log interpolation, energy poll when off). Full audit memo: `.squad/decisions-archive.md`.

**Bosch Home Connect Audit Complete (2026-05-18):** INSTALL verdict wins. craigde/hubitat-homeconnect-v3 is comprehensive, HPM-published, actively maintained. OAuth Authorization Code Grant pattern reusable for future cloud-OAuth drivers.

**PurpleAir CLOUD-API driver is greenfield (2026-05-18):** No Hubitat community driver targets `api.purpleair.com/v1/sensors/{id}`. If Mads green-lights, this is your next build: ~150–250 lines, EPA Barkjohn 2021 AQI conversion, Bearer key auth, single-device polling pattern. No hardware needed for testing — use any public sensor ID from map.purpleair.com.
