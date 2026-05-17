# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — a collection of Hubitat Elevation drivers. First driver: Gemstone Lights. Repo will host more drivers over time, so the README and folder structure must scale.
- **Stack:** Markdown, optional Hubitat Package Manager (HPM) manifest JSON
- **Created:** 2026-05-16

## Current Status

Link is the DevRel and Documentation specialist. See history-archive.md for detailed learnings from the initial project phase (community conventions survey, HPM release infrastructure, parent/child documentation patterns, README community-conformance audit).

## Team Milestone Summary

**Previous sessions (see history-archive.md for details):**
- SunStat v0.1.0–v0.1.4 documentation (parent/child OAuth, token bootstrap, envelope unwrap)
- Gemstone Lights v0.4.0–v0.4.1 documentation (effect naming, WebCoRE visibility, HPM release)
- Community README conformance audit (6 targeted edits across 3 drivers)

---

## 2026-05-17T16:31:55Z — Bosch Home Connect Scoping (Cypher + Trinity)

**Topic:** bosch-home-connect-feasibility

Scoping discussion completed. Implementation will follow.

**ACTION FOR LINK:** When spawned to write README:
- Device Flow OAuth2 is the auth pattern (no external redirect URI needed)
- User must register app at developer.home-connect.com (free, self-service)
- Walkthrough: user enters client_id + client_secret in app preferences, taps Authorize, opens verification URL on phone, enters user code, grants access
- Polling cadence: 90-120s (rate limit constraint)
- Single appliance (fridge) or multi-appliance support

See .squad/decisions/decisions.md section 9 (Auth Flow — Device Flow, Step by Step) for complete OAuth flow reference.

---

## 2026-05-17T16:53:47Z — Touchstone LED Fireplace Tuya Feasibility (Cypher + Trinity)

**Topic:** touchstone-fireplace-feasibility

Feasibility pass completed. Documentation phase incoming.

**Device:** Touchstone Sideline LED fireplace (Tuya-based; WiFi)  
**Control:** Tuya Local (LAN) over rawSocket + AES-128-ECB  
**Driver Shape:** Single file (drivers/touchstone-fireplace/touchstone-fireplace.groovy)

**ACTION FOR LINK:**

Prepare README documentation for once architecture is locked. Key sections:

1. **Install / Setup Flow**
    - Device setup prerequisites (WiFi, Smart Life app pairing)
    - Local IP assignment (recommend DHCP reservation)
    - **One-time local key extraction step** (critical; two methods provided):
      - Method A (preferred): Via Home Assistant + make-all/tuya-local cloud-auth (SmartLife credentials only; ~5 min)
      - Method B (fallback): Via 	inytuya wizard (free Tuya IoT dev account; ~20 min one-time)
    - Preference configuration: deviceIP, localKey, deviceId
    - Discover/pair flow

2. **Capabilities & Commands**
    - Standard: Switch (on/off), SwitchLevel (flame brightness), Refresh, Initialize
    - Custom commands: setFlameColor(name), setLogColor(name), setLogBrightness(level), setFlameSpeed(speed)
    - Palette values: 6 flame effects, 12 log colors (list all with descriptions)
    - **Important:** NOT ColorControl — this is palette-based, not RGB

3. **Troubleshooting**
    - Local key extraction errors (cloud-auth vs dev-account paths)
    - Connection drops (rawSocket idle disconnect; keepalive pattern)
    - Device accepts only one TCP connection; close mobile app before driver connects

4. **Compatibility**
    - Hubitat hub gen (confirm which gens support rawSocket; likely all modern gens)
    - Tuya protocol versions supported (v3.3 confirmed; v3.4/v3.5 added in Session 3 if needed)

See .squad/orchestration-log/2026-05-17T165347Z-trinity.md for architecture details and .squad/orchestration-log/2026-05-17T165347Z-cypher.md for Tuya protocol + local-key extraction deep dive (sources included).

**Key learning:** Local key extraction UX is one-time only and has a no-account path (SmartLife credentials via HA). Document both methods clearly so users understand the trade-offs. README should separate "prefer Method A" vs. "fallback to Method B" flow.

---

## 2026-05-17T17:14:00Z — Touchstone Tuya Portal-Free Key Extraction Audit (Cypher — Conclusion)

**Topic:** tuya-portal-free-2026

Cypher completed definitive audit of all 2026 Tuya local-key extraction methods. **Conclusion affects Touchstone README documentation.**

### KEY FINDING

**Portal-free path exists but is not applicable to Mads** (no Home Assistant). The iot.tuya.com portal signup (which Mads is already pursuing) is the **durable and correct choice** for the Touchstone driver.

### Summary for Link

Two documented local-key extraction methods exist:

1. **Preferred path (requires Home Assistant):** make-all/tuya-local cloud-auth
    - Uses SmartLife app credentials directly (QR scan)
    - ~5 minutes, no developer account needed
    - **Fragility note:** Relies on Tuya-issued hardcoded client_id in HA integration; Tuya can revoke unilaterally
    - Recommended for users with HA already installed

2. **Fallback path (no HA required):** 	inytuya wizard via iot.tuya.com
    - Requires free Tuya IoT developer account (one-time signup)
    - Full 	inytuya Python tool setup and wizard flow
    - ~20 minutes on first try
    - **Durable:** Once key is extracted, it persists until device re-pairs (dev account status doesn't matter afterward)
    - Recommended for users without HA

### Action for Link

When documenting Touchstone README (Section: "Extracting Your Local Key"):
- Present **Method A (HA path) as the quick option, Method B (tinytuya path) as the standard alternative**
- Include short callout explaining the Tuya client_id fragility in Method A
- Link both to .squad/decisions.md section "2026 Tuya Portal-Free Key Extraction Assessment" for full technical details
- Note that Mads is using Method B (iot.tuya.com platform signup) and can provide firsthand walkthrough notes

See .squad/decisions.md for full audit details and SUPERSEDES note correcting prior session cypher-6 claims about portal-free being "clean and recommended."

---

## 2026-05-17T10:47:09Z — Touchstone Sideline Elite — Local LAN Control Confirmed (Coordinator Direct Mode)

**Topic:** touchstone-local-control-achieved

Coordinator walked Mads through end-to-end Tuya IoT setup and local device verification. All heater + LED DPs now mapped and responding. Driver architecture is validated; README documentation phase incoming.

### Touchstone README Sections (Action for Link)

**1. Device Setup Prerequisites**
- Smart Life app pairing on device
- WiFi on same LAN as Hubitat hub
- Local IP assignment (recommend DHCP reservation on 192.168.1.38 or user's IP)
- Hubitat driver installed via HPM

**2. Extracting Your Local Key (CRITICAL)**

Provide two methods:

**Method A (Preferred if you have Home Assistant):**
- Use make-all/tuya-local cloud-auth path (SmartLife QR scan)
- ~5 minutes, no developer account needed
- **Important caveat:** Relies on Tuya-issued hardcoded client_id; Tuya can revoke unilaterally (unlikely but documented)
- Link to HA integration docs + Tuya IoT setup walkthrough

**Method B (Standard fallback, no HA required):**
- Use 	inytuya wizard via iot.tuya.com
- Requires free Tuya IoT developer account (one-time signup, ~5 min)
- Full Python tinytuya setup + wizard flow (~20 min)
- **Key operational note:** API subscription is MANUAL. New Tuya IoT Cloud Project does NOT auto-subscribe. Must manually enable:
  - IoT Core
  - Authorization Token Management
  - Smart Home Basic Service
  - Device Status Notification
  - (All free trials; no card required)
- Then SmartLife account linking via QR, then wizard
- Result: devices.json with device credentials
- **Durable:** Once extracted, local_key persists forever (independent of cloud account status)

**3. Preference Configuration**
- deviceIP: 192.168.1.38 (or auto-discover via app)
- localKey: <paste from devices.json>
- deviceId: 70223053e8db84d10b53

**4. Capabilities & Commands**

Standard:
- Switch — on/off (DP 1)
- SwitchLevel — flame brightness (DP 102; map 0–100 to string enum "1"–"5")
- Refresh — query all DPs
- Initialize — socket connect + heartbeat

Custom:
- setFlameColor(name) — palette: orange, blue, yellow, orange+blue, orange+yellow, blue+yellow (DP 101)
- setLogColor(name) — palette: 12 named colors (DP 104)
- setLogBrightness(level) — 12-step (DP 105)
- setFlameSpeed(speed) — Slow / Medium / Fast (DP 103)

**5. Troubleshooting**
- Local key extraction not working (cloud-auth vs dev-account path diagnostics)
- "Connection dropped" (rawSocket idle disconnect; driver has keepalive heartbeat every 20s)
- "Device accepts only one connection" (close mobile app before driver connects; will auto-reconnect if app is restarted)

### Key Learning for Link

**Tuya local key extraction UX has two durable paths:**
1. Home Assistant + SmartLife (fast, no developer account) — fragile on client_id revocation
2. Tuya IoT developer account + tinytuya (standard, manual API subscription step) — durable, used by Mads

**Document the manual API subscription step explicitly in Method B.** This is a gotcha that blocks users. It's not auto-enabled, and the wizard fails silently if any API is missing.

Next README draft will be ready once Tank scaffolds driver and Switch validates empirical LED DP mapping.
