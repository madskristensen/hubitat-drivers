# Cypher — History Archive

**Archived 2026-05-18 per summarization protocol.**

---

## Bosch Home Connect Protocol + craigde Driver Audit (2026-05-18)

**Full memo:** `.squad/decisions-archive.md`

**Protocol shape:**
- Transport: HTTPS to `api.home-connect.com`. No local protocol. No LAN fallback.
- Auth: OAuth2 Authorization Code Grant (Bosch also supports Device Flow). Access token 86400s (24h). Refresh token long-lived, may rotate.
- Discovery: `GET /api/homeappliances` → array of `{haId, name, type, connected}`.
- SSE: `GET /api/homeappliances/{haId}/events` — text/event-stream, viable via EventStream but requires watchdog.
- Rate limits: 1,000 requests/day, 50/minute, 10 SSE channels, 100 token refreshes/day.

**Verdict:** INSTALL craigde/hubitat-homeconnect-v3 v3.1.7 (2026-03-13). Active, HPM-published, 13 appliance types. Rubric 67/100 (Conditional Fit).

---

## Rainbird LNK WiFi Protocol (2026-05-18)

**Protocol shape:**
- Transport: HTTP POST to `http://{ip}/stick` port 80 (or HTTPS 443).
- Envelope: JSON-RPC 2.0, `method: "tunnelSip"`, `params.data` = SIP command as hex string.
- Encryption: AES-256-CBC with SHA-256 hash frame (no HMAC).

**Verdict:** IMPROVE-EXISTING MHedish/Hubitat RainBird-LNK driver (v1.0.0.0, 2026-05-07). Rubric 92/100 (Strong Fit).

---

## Driver Opportunity Shortlist (2026-05-18)

**Top 5 ranked:**
1. Enphase Envoy — Local HTTP REST, M effort
2. Tesla Wall Connector Gen 3 — GET /api/1/vitals, S effort
3. Tibber energy price — GraphQL + Bearer, very low cloud-kill risk, S effort
4. Reolink camera/doorbell — Local HTTP CGI (officially sanctioned), M effort
5. Mitsubishi mini-split via ESPHome CN105 — ESPHome REST, S/M effort

**Anti-list:** Ring, Wyze, Nest, Arlo, Flo by Moen, Velux KLF 200, Pentair ScreenLogic, Flexit Nordic, MelCloud.

---

## MyQ / ratgdo Feasibility (2026-05-18)

**Key facts:**
- MyQ cloud API dead for third parties (Chamberlain Oct 2023).
- ratgdo ESPHome firmware replacement (~$45, maintained April 2026).
- ratgdo ESPHome REST API on port 80 (garage door, light, obstruction, motion).
- No Hubitat ratgdo driver exists — genuine gap.
- Recommend: ratgdo ESPHome REST driver, asynchttpGet polling, 5s interval.

---

## Daikin BRP069B Endpoint Catalog (2026-05-18)

**28 endpoints:** 7 implemented (control, power, special mode, model, swing). 10 skip (cloud, scheduling, dangerous). 4 maybe-later (demand control, filter alerts, hourly energy, legacy power).

**v0.1.5 quality:** Production-ready. Three maintenance items: NPE in setpoint setters (30 min), missing log interpolation (5 min), energy poll when off (30 min).

---

## Tank Support + Daikin v0.1.x Lessons (2026-05-18)

- **HPM Bundle v1.0.0:** Manifest schema validated, UUID reuse confirmed, release workflow documented.
- **Touchstone v0.1.20:** Active TCP confirmed as only viable Hubitat Tuya autodiscovery path.
- **HubAction gotcha:** Map-based constructors unreliable across firmware. Retire for asynchttpGet.
- **Daikin v0.1.4 roadmap closed:** Econo/Powerful mode, model cache, event hygiene audit all shipped.

---

## Archive Scope

Sessions 2026-05-16 to 2026-05-17: Bosch, Rainbird, MyQ/ratgdo, driver opportunities survey, Daikin API audit.

**Current focus (2026-05-18):**
- PurpleAir local vs. cloud API audit → greenfield BUILD verdict (80/100)
- OAuth callback retrofit triage → NOT WORTH IT for Gemstone + SunStat

See `.squad/decisions-archive.md` and `.squad/decisions.md` for full audit narratives.
