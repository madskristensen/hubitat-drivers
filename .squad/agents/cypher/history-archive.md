# Cypher Agent History Archive

**Archived:** 2026-05-16T23:04:57Z
**Reason:** history.md exceeded 15,360 bytes; older research entries moved to archive
**Retained in main history:** Framework fingerprinting, SDDP breakthrough, Control4 research, recent team updates

---

## Early Research Entries (Archived)

### 2026-05-16T14:08:16-07:00 — Gemstone protocol research

**Reference repos used:**
- `sslivins/hass-gemstone` @ `a4abbf029ba8631caa445789e598e51da8bb7721` — HA custom integration (cloud_polling, iot_class)
- `sslivins/pygemstone` @ `263ee41a8e8195c9384e277266db317f94dba641` — underlying Python client; the real API source

**Key protocol facts:**
- The Gemstone cloud is AWS Amplify in `us-west-2`: Cognito User Pool `us-west-2_rr5lY7Etr`, client ID `2647t144niotrl53vvru0ivno7`, REST base `https://mytpybpq12.execute-api.us-west-2.amazonaws.com/prod`
- Auth is Cognito USER_SRP_AUTH → Bearer access token. SRP math is non-trivial in Groovy; test `USER_PASSWORD_AUTH` first against the real pool.
- The two control endpoints that matter for Hubitat: `PUT /deviceControl/onState` (on/off) and `PUT /deviceControl/play/pattern` (brightness + color + animation)
- Changing brightness requires a full read-modify-write cycle on the pattern object — there is no partial-update API
- Colors are ARGB-packed 32-bit ints in a `colors: []` array
- State polling: 30 s, cloud lags 30–60 s after commands → use optimistic state updates
- AppSync GraphQL endpoint exists but was never used by the iOS app in live captures — REST-only confirmed
- 21 REST endpoints documented total; 5 are needed for a Hubitat driver (auth, homegroups, devices, currentlyPlaying, onState, play/pattern)

**Critical unresolved question:**
- **Local API unknown.** The "Allow local commands" feature in the Gemstone app likely enables a local HTTP server on the controller. The `hass-gemstone` reference is 100% cloud-based and has no local protocol info. To discover the local API, Mads needs to sniff LAN traffic from the app to `192.168.1.238` with mitmproxy or Wireshark. If a local API exists, it would be the preferred path for Hubitat (no cloud dependency, no SRP auth complexity, sub-second response).

### 2026-05-16T15:13:32-07:00 — Local API discovery capture playbook

**Objective:** Provide Mads with a concrete, Windows-friendly capture plan to reverse-engineer the unknown local API.

**Deliverable:** 6-technique playbook:
1. **Port scan** (PowerShell Test-NetConnection or nmap) — 5 min, HIGH yield. Finds listening TCP/UDP.
2. **curl probe** (common REST paths) — 5 min, MEDIUM yield. Tests if HTTP is wide open.
3. **mDNS discovery** (Bonjour Browser GUI) — 10 min, LOW–MED yield. Locates advertised services.
4. **Wireshark passive capture** — 30 min, LOW yield without port mirroring. Shows unicast only on same router.
5. **mitmproxy phone-to-PC proxy** — 45 min, HIGH yield. Best signal for HTTP/HTTPS payloads.
6. **Router tcpdump** (OpenWrt/UniFi SSH) — 1+ hour, HIGH yield. Overkill for most users; advanced fallback.

**Recommended sequence for Mads:** #1 (port scan) → #2 (curl) → #5 (mitmproxy) if #2 fails.

### 2026-05-16T15:13:32-07:00 — Re-scan: pygemstone local-mode check

**Question:** Does pygemstone contain any LOCAL-mode (LAN) code that wasn't captured in the initial cloud spec?

**Answer:** **CONFIRMED CLOUD-ONLY.** No local-mode support found.

**Evidence:**
- **Hosts:** const.py lists only `us-west-2.amazonaws.com` endpoints (REST API, AppSync HTTP + WebSocket). ZERO 192.168.x.x references, mDNS, UDP, or local discovery.
- **Auth:** Pure AWS Cognito SRP via pycognito. No local auth scheme.
- **Parameters:** GemstoneClient accepts only `email`, `password`, `session`, `timeout`, `api_base`. No `host`, `controller_ip`, `local_only`, `prefer_local`, etc.
- **README:** Zero mention of "local", "LAN", or "allow local commands".

**Conclusion:** pygemstone is a cloud-only AWS Amplify client. Tank must proceed with local API discovery via packet capture before implementing LAN control in the Hubitat driver.

---

## Summary

The early research phase documented the cloud API, established capture playbooks, and confirmed that the local protocol is not publicly available anywhere. All findings were consolidated into Team Updates and merged into .squad/decisions.md. Subsequent research focused on SDDP discovery, Control4 driver analysis, and mitmproxy planning, which led to the final recommendation of UniFi packet capture as the agreed discovery path.

**Current status:** Awaiting Mads' packet capture; research phase closed.
