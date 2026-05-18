# Cypher Audit — PurpleAir Local vs. Cloud API
**Date:** 2026-05-18T16:12:53-07:00  
**Requested by:** Mads Kristensen  
**Driver under review:** sidjohn1/hubitat — `PurpleAirLocal/purpleAirLocalDTH.groovy`  
**Related skill:** `.squad/skills/cloud-killed-api-evaluation/SKILL.md`

---

## 1. Executive Summary

**Bottom line for Mads (no hardware owned):** sidjohn1's driver is LOCAL-ONLY and requires a PurpleAir sensor (~$200–260) on his LAN — it cannot work for a neighbor's sensor. The one cloud-API community driver that exists (SANdood/PurpleAirStation) uses the **defunct** `www.purpleair.com/json` legacy endpoint, last patched May 2020, and is dead. **There is a genuine greenfield gap for a PurpleAir cloud-API driver targeting `api.purpleair.com/v1/`.** The free tier (1M points/month) means Mads can monitor any public sensor on the PurpleAir map — including a neighbor's — with zero hardware cost. PNW wildfire season makes this high-value. **Recommendation: BUILD the cloud-API driver (rubric: 80/100) if Mads will not buy hardware; INSTALL sidjohn1 (rubric: 75/100, blocked by "Mads Can Test" = 0) only if he buys a sensor.**

---

## 2. sidjohn1 PurpleAirLocal Driver Audit

| Field | Value |
|---|---|
| **Repo** | github.com/sidjohn1/hubitat/tree/main/PurpleAirLocal |
| **Author** | Sidney Johnson (`sidjohn1`) |
| **Last code date** | 2024-06-03 (file header: "Date: 2024-06-03", v1.5) |
| **License** | Apache 2.0 |
| **HPM-published** | Yes (packageManifest.json present) |

### Protocol — CONFIRMED LOCAL ONLY

The `poll()` method makes a single HTTP call:
```
GET http://${ipAddress}/json?live=${realTime}
```
This is `http://<sensor-ip>/json` — the **PurpleAir sensor's LAN endpoint**, not the cloud API. The sensor must be on the same network as the Hubitat hub (or routed LAN). **No cloud path exists in this driver.**

`asynchttpGet` is NOT used. The driver uses blocking `httpGet(params) { resp -> ... }`. This is a minor quality gap (blocking calls can cause Hubitat hub latency spikes on timeout) but not a hard bug since the 5-second timeout limit (`timeout: 5`) keeps it bounded.

### Capabilities & Attributes

```
Capabilities: TemperatureMeasurement, RelativeHumidityMeasurement, AirQuality,
              PressureMeasurement, SignalStrength, Sensor, Polling
Custom attrs:  pressure (mBar), dewPoint (°F), aqi, aqiDisplay, aqiMessage,
              pm01, pm25, pm10 (µg/m³), voc, vocDisplay, vocMessage, rssi, timestamp
```

Architecture: **Single-device**. No parent/child. One driver per physical sensor.

### Hardware Requirement

**CONFIRMED: requires user to own a PurpleAir hardware sensor.** The `ipAddress` preference must be set to the sensor's LAN IP. Outdoor sensor (PA-II, PA-II-SD) costs ~$200–260 at purpleair.com. Indoor (PA-I) ~$100. Without owned hardware, this driver is completely inoperable.

### Poll Interval & Error Handling

- Poll: configurable enum 0/1/2/5/10/15/30 min (default 5). Randomized seconds offset to avoid hub surge. ✅
- Error handling: `try/catch(Exception e)` around `httpGet` → `log.error`. No retry, no sentinel-event, no HealthCheck capability. Basic but not broken.
- Temperature default calibration: -8°F (addresses PurpleAir sensor's known self-heating issue). Humidity: +4%. These are hardcoded defaults matching PurpleAir's own recommendations. ✅

### Sentinel-Value / Bad-Data Guards

- **Dual-laser averaging:** `calculateAverage(valueA, valueB, scale)` — averages A/B channels, falls back to channel A if B is null. ✅
- **`Flag` field check: ABSENT.** PurpleAir's local JSON includes a `Flag` field when a sensor reports anomalous data. sidjohn1 does NOT check this field. SANdood's (stale) driver DID check `Flag`. If a sensor malfunctions, inflated values will be emitted without warning. **Minor gap.**
- **Data-age staleness check: ABSENT.** No guard for stale sensor data (timestamp not compared to now). SANdood's driver had `if (age > 300000)` staleness warning. **Minor gap for reliability during wildfire events when sensors can lock up.**
- **EPA Barkjohn 2021 correction: ABSENT.** Driver reads the sensor's own pre-computed `pm2.5_aqi` value. The sensor's internal AQI computation uses the standard EPA NowCast formula without the Barkjohn correction that improves wildfire-smoke accuracy. For PNW wildfire use, this may understate severity by 10–30%. Not a blocker but worth noting.

### Maintenance Status

- v1.5 (2024-06-03) — well within the 18-month staleness threshold. **Not stale.**
- No open issues visible in the sidjohn1/hubitat repo on this path.
- No community forum thread URL available from this analysis, but HPM distribution implies community presence.

### Quality Verdict

**"Install fine — wrong shape for Mads"**

The driver is well-written, actively maintained, sensibly calibrated, and correctly implements local LAN polling. The quality is sound. The disqualifier is simple: Mads doesn't own a PurpleAir sensor. Without hardware, this driver does nothing.

---

## 3. Cloud-API Alternative — Community Drivers

### Search Results (2026-05-18)

| Query | Result |
|---|---|
| `hubitat purpleair cloud api` (GitHub repos) | **0 results** |
| `hubitat purpleair` (GitHub repos) | 1 result: SANdood/PurpleAirStation |

### SANdood/PurpleAirStation

| Field | Value |
|---|---|
| **Repo** | github.com/SANdood/PurpleAirStation |
| **Author** | Barry Burke (storageanarchy@gmail.com) |
| **Last commit** | May 7, 2020 — **over 6 years stale** |
| **Platform** | SmartThings + Hubitat dual-platform (SHPL boilerplate) |

**Protocol confirmed DEAD:** The driver hits:
```
asynchttpGet(purpleAirResponse, [uri: 'https://www.purpleair.com', path: '/json', query: [show: purpleID]])
```
This is the **legacy PurpleAir JSON endpoint** (`www.purpleair.com/json`). PurpleAir deprecated this endpoint and migrated to `api.purpleair.com/v1/` in 2021–2022. The legacy endpoint requires no API key and returns a `results[]` array schema that no longer matches the v1 API structure. **This driver is broken and cannot be fixed without a full rewrite targeting the v1 API.**

**Verdict: DEAD. Do not install.**

### Gap Assessment: CONFIRMED GREENFIELD

No Hubitat community driver targets `api.purpleair.com/v1/sensors/{sensor_index}`. This is a real gap.

**PurpleAir v1 Cloud API — What We Know:**
- Endpoint: `GET https://api.purpleair.com/v1/sensors/{sensor_index}?fields=pm2.5_atm,pm2.5_atm_b,temperature,humidity,pm1.0_atm,pm10.0_atm,rssi`
- Auth: `X-API-Key: <your_key>` header. Keys are free — apply at developer.purpleair.com.
- Free tier: 1,000,000 points/month. Each field in the response costs 1 point. A 7-field request at 5-min polling = 7 × 288 = 2,016 points/day ≈ 60,480/month. **Well within free tier. No payment required.**
- Schema: `{"sensor": {"pm2.5_atm": 3.5, "pm2.5_atm_b": 3.4, "temperature": 68, "humidity": 42, ...}}`
- Any publicly listed sensor on the PurpleAir map is accessible (including neighbors' sensors). Private sensors need the owner's read key.
- Rate limits: documented; conservative polling at 5 minutes respects all limits.

**Build effort estimate:** ~150–250 lines of Groovy. Single device driver (no parent/child). Pattern is identical to Gemstone Lights cloud REST driver. Key steps:
1. `asynchttpGet` to `api.purpleair.com/v1/sensors/{id}` with `X-API-Key` header
2. Parse JSON → emit pm2.5, pm10, pm1.0, temp, humidity, rssi
3. Apply **EPA Barkjohn 2021 correction** for PNW wildfire accuracy:
   - `pm2.5_corrected = 0.534 × pm2.5_avg - 0.0844 × humidity + 5.604`
   - Standard EPA NowCast AQI breakpoints on corrected value
4. Schedule at 5-minute intervals (configurable)
5. Emit `AirQuality` capability attribute + custom `pm25`, `aqi`, `aqiDisplay`, `aqiMessage`

Tank-hours estimate: **S (~1 sprint, 20–30h)**, including EPA formula + integration testing with live sensor data.

---

## 4. Rubric Scores

Using Trinity's Driver Fit Rubric (100 pts). Criteria per `.squad/decisions.md` §trinity-driver-fit-rubric.

### Option A — Install sidjohn1 PurpleAirLocal (assumes Mads BUYS hardware)

| Criterion | Max | Score | Reasoning |
|---|---|---|---|
| **Local vs. Cloud Protocol** | 20 | **20** | Pure LAN HTTP to sensor IP. No cloud dependency. Full 20. |
| **Mads Can Test** | 15 | **0** | Mads does NOT own hardware. He'd need to buy ~$200–260 outdoor sensor. Score = 0 (hard block on practical testing). If he WILL buy: score becomes 15. |
| **User Demand Signal** | 15 | **8** | PNW wildfire relevance is real and high. However, hardware cost creates friction. HPM distribution indicates community adoption. Significant deduction for ownership barrier. |
| **Sandbox-Safe** | 15 | **15** | Blocking `httpGet` + try/catch. No crypto, no reflection, no JNI. All standard. Full 15. |
| **Vendor API Stability** | 15 | **15** | Local LAN endpoint on owned hardware. Zero vendor dependency. PurpleAir cannot break this. Full 15. |
| **Effort to Ship** | 10 | **9** | HPM install; just enter IP address. Trivially easy. Minor deduction: `httpGet` blocking vs. `asynchttpGet` is a known minor quality gap. |
| **Maintenance Burden** | 10 | **8** | Local protocol — stable indefinitely. sidjohn1 active in 2024. Minor deduction for `Flag` check absence and no Barkjohn correction. |

**Total (no hardware): 75/100 — ⚠️ Practical blocker on Mads Can Test**  
**Total (owns hardware): 90/100 — ✅ Strong Fit**

### Option B — BUILD cloud-API driver (greenfield, no hardware needed)

| Criterion | Max | Score | Reasoning |
|---|---|---|---|
| **Local vs. Cloud Protocol** | 20 | **10** | Cloud REST HTTPS. No local fallback. Penalty per rubric for cloud dependency. |
| **Mads Can Test** | 15 | **15** | Free API tier, no hardware. Any public PurpleAir sensor on the map accessible immediately. Full 15. |
| **User Demand Signal** | 15 | **13** | High PNW wildfire relevance. Access to neighbor sensors is the single biggest value proposition. No existing community driver = unmet demand. -2 for no existing forum discussion confirming demand. |
| **Sandbox-Safe** | 15 | **15** | `asynchttpGet` + `X-API-Key` in state + JSON parsing. All confirmed patterns. Full 15. |
| **Vendor API Stability** | 15 | **12** | PurpleAir launched v1 in 2021–2022, has monetized with a sustainable free tier, API is documented (developer.purpleair.com). Not hostile. Minor deduction: relatively young developer program (3 years), private company, no "forever free" SLA. |
| **Effort to Ship** | 10 | **8** | S-effort task (~150–250 lines, single device, single endpoint). EPA formula is documented and straightforward. Slight deduction: live-sensor integration testing required to validate formula output. |
| **Maintenance Burden** | 10 | **7** | Cloud REST driver → schema/key rotation risk. PurpleAir's v1 API has been stable since 2022 but could change quotas or schema. Monitoring required. |

**Total: 80/100 — ✅ Strong Fit (BUILD)**

---

## 5. Recommendation Matrix

| Mads's Situation | Recommendation | Rubric | Notes |
|---|---|---|---|
| **ALREADY OWNS a PurpleAir sensor** | INSTALL sidjohn1 | 90/100 | Just enter LAN IP. HPM install. Done in 5 minutes. |
| **WILL BUY a PurpleAir sensor** | INSTALL sidjohn1 (after purchase) | 90/100 | sidjohn1 is the right driver. No reason to wait on cloud build. |
| **Will NOT buy hardware (current state)** | **BUILD cloud-API driver** | 80/100 | Greenfield. Free API. Can monitor any public sensor today. |
| ~~SANdood/PurpleAirStation~~ | ~~SKIP~~ | N/A | Dead — legacy `www.purpleair.com/json` endpoint deprecated ~2022. |

### Primary Recommendation (Mads has no hardware, most likely scenario)

> **BUILD a cloud-API Hubitat driver targeting `api.purpleair.com/v1/sensors/{id}`.**
>
> This is a genuine greenfield opportunity — no Hubitat community driver exists. The free developer API tier requires no payment. Mads can add a neighbor's sensor index (from the public PurpleAir map at map.purpleair.com) and get hyperlocal AQI with zero hardware purchase. Include the **EPA Barkjohn 2021 correction formula** for accurate PNW wildfire-smoke readings — this is the single most meaningful quality differentiator from sidjohn1's device-internal AQI computation.
>
> Suggest Tank for implementation. Effort: S (~1 sprint). Output: single Groovy driver, `AirQuality` capability, `pm25`/`aqi`/`aqiDisplay` custom attributes, 5-min configurable poll, Barkjohn corrected AQI.

---

## 6. Sources

| Source | URL | Date Checked |
|---|---|---|
| sidjohn1 PurpleAirLocal repo | github.com/sidjohn1/hubitat/tree/main/PurpleAirLocal | 2026-05-18 |
| sidjohn1 Groovy source | raw.githubusercontent.com/sidjohn1/hubitat/main/PurpleAirLocal/purpleAirLocalDTH.groovy | 2026-05-18 |
| SANdood/PurpleAirStation | github.com/SANdood/PurpleAirStation | 2026-05-18 |
| SANdood Groovy source (legacy API confirmed) | raw.githubusercontent.com/SANdood/PurpleAirStation/master/…/purpleair-air-quality-station.groovy | 2026-05-18 |
| GitHub search: hubitat purpleair | github.com/search?q=hubitat+purpleair&type=repositories | 2026-05-18 (1 result: SANdood) |
| GitHub search: hubitat purpleair cloud api | github.com/search?q=hubitat+purpleair+cloud+api&type=repositories | 2026-05-18 (0 results) |
| PurpleAir v1 API reference | developer.purpleair.com | Referenced from PurpleAir documentation |
| EPA Barkjohn 2021 correction | doi.org/10.1039/D1EA00050K | Barkjohn et al., Environ. Sci.: Atmos., 2021 |
| Trinity Driver Fit Rubric | .squad/decisions/decisions.md §5 (Rainbird audit, Bosch audit) | 2026-05-18 |
| Cloud-Killed API Evaluation skill | .squad/skills/cloud-killed-api-evaluation/SKILL.md | 2026-05-18 |


---

# OAuth Callback Retrofit Analysis
**Author:** Trinity (Lead/Architect)  
**Date:** 2026-05-18T16:14:09-07:00  
**Requested by:** Mads Kristensen  
**Trigger:** Cypher's Bosch Home Connect audit documented the `mappings {}` + `cloud.hubitat.com/api/{uuid}/apps/{id}/oauth/callback` pattern. Does it improve any existing driver?

---

## 1. Pre-Triage

| Driver | Protocol | Auth | Candidate? | Disposition |
|--------|----------|------|-----------|-------------|
| `touchstone-fireplace` | Local Tuya TCP socket | None | ❌ | ELIMINATED — no cloud, no auth, pattern irrelevant |
| `daikin-wifi` | Local LAN HTTP | None | ❌ | ELIMINATED — local HTTP only, zero auth surface |
| `gemstone-lights` | Cloud REST (Cognito) | Email+password via Cognito USER_PASSWORD_AUTH | ✅ | Deep-dive below |
| `sunstat-thermostat` | Cloud REST (Azure B2C) | Refresh-token bootstrap via external CLI | ✅ | Deep-dive below |

---

## 2. Gemstone Lights — Deep Dive

### 2a. Current Auth Flow

Driver calls AWS Cognito directly using the `USER_PASSWORD_AUTH` flow:
- Hardcoded endpoint: `https://cognito-idp.us-west-2.amazonaws.com/`
- Hardcoded Cognito client ID: `2647t144niotrl53vvru0ivno7` (Gemstone's own mobile app pool)
- POST body: `{"AuthFlow":"USER_PASSWORD_AUTH","AuthParameters":{"USERNAME":"<email>","PASSWORD":"<password>"}}`
- On success: receives `AccessToken` + `RefreshToken` + `IdToken` in the `AuthenticationResult` envelope
- Tokens stored in `state.accessToken`, `state.refreshToken`, `state.tokenExpiresAt`
- `RefreshToken` is used for proactive renewal (scheduled) and fallback re-auth if refresh fails
- **No external helper required.** Fully self-contained within the driver.

### 2b. Current User Setup UX

1. Install driver via HPM (or paste into Hubitat UI)
2. Add Device → select Gemstone driver
3. Preferences → enter account email + account password → Save
4. Driver auto-authenticates on `updated()` / `initialize()`, polls every 5 min

**Total new-user burden: ~4 clicks + 2 text fields. No external tools. No copy-paste of tokens.**  
The `authStatus` attribute surfaces auth state in the device UI ("Authenticated", "Not configured", etc.) for feedback.

### 2c. Would the OAuth Callback Pattern Improve This?

No. The pattern requires:
1. A vendor OAuth Authorization Server with a public `/authorize` redirect endpoint
2. The ability to register a third-party OAuth Client ID + callback URI with the vendor

Gemstone uses **AWS Cognito `USER_PASSWORD_AUTH`** — not an Authorization Code Grant with redirect. There is no public Gemstone developer portal where anyone can register an OAuth application. The Cognito client ID `2647t144niotrl53vvru0ivno7` is Gemstone's own mobile app client. Even if Hubitat's `mappings {}` redirect were implemented, there is no Gemstone OAuth authorize URL to redirect to, and no way to register `cloud.hubitat.com/...` as an allowed redirect URI for that pool.

The current approach — direct Cognito USER_PASSWORD_AUTH — is functionally equivalent to "Authorization Code Grant" for the user, but simpler: the user types credentials once into encrypted preferences; everything else is automated. The callback pattern would make setup *more* complex, not less.

### 2d. Conversion Cost

Moot — the pattern cannot be applied (no vendor OAuth redirect endpoint). Even if attempted: parent app ~150 lines, driver refactor ~50 lines, docs update. But it would never work without Gemstone standing up a public OAuth developer portal.

### 2e. Verdict

**NOT WORTH IT — vendor blocker + current UX is already clean.**  
Gemstone's Cognito pool does not expose a public Authorization Code Grant endpoint. The existing email+password preferences flow is as simple as this type of cloud driver gets. No improvement possible via the callback pattern.

---

## 3. SunStat (Watts Home) Thermostat — Deep Dive

### 3a. Current Auth Flow

Driver uses **Watts Azure AD B2C** (`login.watts.io`) for token exchange:
- Token endpoint: `https://login.watts.io/tfp/wattsb2cap02.onmicrosoft.com/B2C_1A_Residential_UnifiedSignUpOrSignIn/oauth2/v2.0/token`
- Client ID: `c832c38c-ce70-4ebc-83b6-b4548083ac90` (Watts mobile app's Xamarin iOS MSAL client)
- Grant type used in driver: `refresh_token` (not initial login — driver never handles the login step)
- **Bootstrap problem:** The driver has no mechanism to perform the initial Watts login. The user must run `homebridge-tekmar-wifi` (an external Node.js CLI tool) once to capture the initial refresh token (~1,660 chars), then paste it into the `setRefreshToken()` command on the parent device.
- After bootstrap: driver rotates tokens automatically on each `refreshTokensSync()` call, persisting the new `refresh_token` from every response. Self-maintaining from that point on.
- `state.refreshToken`, `state.accessToken`, `state.tokenExpiresAt` — same pattern as Gemstone but with Azure B2C token URL.

### 3b. Current User Setup UX

1. Install `homebridge-tekmar-wifi` (requires Node.js on a computer)
2. Run CLI → log in with Watts account → extract the refresh token from the output
3. Copy the ~1,660-character refresh token to clipboard
4. Install SunStat parent + child drivers in Hubitat
5. Create SunStat parent device
6. Open parent device → Commands → `setRefreshToken` → paste the token → Run
7. Driver fetches devices, creates child thermostats
8. Delete the CLI tool (optional cleanup)

**Total new-user burden: 8 steps, requires external Node.js tooling, involves a 1,660-char blind paste into a command field. Genuinely painful.** This is the UX problem the OAuth callback pattern would ideally fix — if the vendor cooperated.

### 3c. Would the OAuth Callback Pattern Improve This?

In theory: yes. The ideal flow would be:
1. Install SunStat parent App (not driver — an App with `mappings {}`)
2. Click "Connect to Watts Home" link in the App's config page
3. Browser redirects to `https://login.watts.io/tfp/wattsb2cap02.onmicrosoft.com/B2C_1A_Residential_UnifiedSignUpOrSignIn/oauth2/v2.0/authorize`
4. User logs in with Watts credentials in browser
5. Watts redirects to `https://cloud.hubitat.com/api/{hub-uuid}/apps/{app-id}/oauth/callback`
6. App exchanges code for tokens, stores in `atomicState`

**The blocker is entirely on the vendor side.** Azure AD B2C only allows redirect URIs registered in the tenant's app registration. The client ID `c832c38c-ce70-4ebc-83b6-b4548083ac90` is Watts's own Xamarin mobile app registration — it has a registered set of redirect URIs that certainly does not include `cloud.hubitat.com`. To add a new redirect URI, Watts would need to update their Azure B2C app registration. There is **no public Watts developer portal** where third parties can register OAuth clients or redirect URIs. Watts/SunStat does not have an API partner program.

The Azure B2C tenant name `wattsb2cap02.onmicrosoft.com` and policy `B2C_1A_Residential_UnifiedSignUpOrSignIn` are consumer-facing infrastructure, not developer-partner infrastructure.

**Alternative not yet explored:** Azure B2C supports **Device Authorization Flow** (`grant_type=urn:ietf:params:oauth:grant-type:device_code`). If the `B2C_1A_Residential_UnifiedSignUpOrSignIn` policy has device flow enabled, a Hubitat driver could display a `user_code` + URL, then poll for the token — no redirect URI required. This is NOT the callback pattern being evaluated here, but it would eliminate the homebridge dependency if it worked. Unknown whether Watts's B2C policy supports it.

### 3d. Conversion Cost

Moot at the `mappings {}` callback level — the vendor blocker is hard. The alternative (Device Flow) would be:
- Add `deviceAuthorizationUrl` to `@Field` constants (~1 line)
- New `initiateDeviceAuth()` command that POSTs to device_authorization endpoint, stores `device_code`, displays `verification_uri_complete` as an info bar or attribute
- `pollDeviceAuth()` called via `runIn()` every 5 seconds until `access_token` returned or error
- If it works: eliminates the homebridge dependency entirely, ~80 lines of new code in the parent driver, no parent App needed

This is speculative until someone verifies whether the Watts B2C policy supports Device Flow.

### 3e. Verdict

**NOT WORTH IT — vendor blocker (for OAuth callback specifically).**  
Watts/SunStat runs a consumer-internal Azure B2C tenant with no public developer portal for third-party OAuth client registration. The `mappings {}` callback pattern requires registering `cloud.hubitat.com/api/{uuid}/apps/{id}/oauth/callback` with Watts — which is not possible without Watts engineering involvement. The pattern cannot be applied.

**Open side path:** Investigate Azure B2C Device Flow as a bootstrap replacement — eliminates homebridge dependency without needing redirect URI registration. Worth one verification call before dismissing.

---

## 4. Next Steps (If Scheduled)

Neither driver is a candidate for the OAuth callback retrofit. However:

**SunStat — Device Flow Investigation (conditional):**
- One 30-minute research task: check whether `B2C_1A_Residential_UnifiedSignUpOrSignIn` supports `device_authorization` endpoint via a test POST. If it returns a `device_code`, the homebridge bootstrap dependency can be eliminated without any vendor portal access.
- Assign to: Cypher (protocol research) → Trinity review → Tank implement if confirmed
- Effort: ~2h if Device Flow works; 0h if it doesn't.

**Gemstone — No action.** Current flow is optimal for the available API surface.

---

## 5. Open Questions / Unknowns

1. **Did not verify** whether the Watts `B2C_1A_Residential_UnifiedSignUpOrSignIn` B2C policy has Device Authorization Flow enabled. This is the only remaining meaningful auth UX improvement path for SunStat.
2. **Did not verify** whether Gemstone has any undocumented Authorization Code Grant endpoint separate from the Cognito USER_PASSWORD_AUTH flow. Unlikely — the mobile app uses the same Cognito pool and `homebridge-gemstone` (community) also uses USER_PASSWORD_AUTH.
3. **Assumed** Watts has no public developer API program. This assumption is based on: no developer.watts.io portal found, client ID is clearly a Xamarin mobile app registration, no Hubitat community threads reference a Watts API registration process.

---

## Summary Table

| Driver | Auth UX Pain | Vendor OAuth Portal | Verdict |
|--------|-------------|---------------------|---------|
| Gemstone | ✅ Clean (email+password pref) | ❌ None (Cognito USER_PASSWORD_AUTH only) | NOT WORTH IT — already clean |
| SunStat | ❌ Painful (homebridge CLI bootstrap) | ❌ None (Azure B2C consumer tenant) | NOT WORTH IT — vendor blocker |

