# Orchestration Log: Coordinator (Self-Probe) — C4 + ELAN Driver Extraction & Testing

**Timestamp:** 2026-05-16T23:04:57Z
**Execution:** Inline per Mads' "run probes directly" directive
**Status:** EXHAUSTED — All 70+ follow-up probes with Gemstone-specific keys returned 404

---

## C4 Driver Download & Extraction

**File:** gemstone_lights.c4z (737 KB) from DriverCentral
**Extraction:** Renamed to .zip, extracted successfully

**Findings:**
- `driver.xml` confirms TCP port 80 + "Send Custom Pattern JSON" command + "Get Version Info" handshake
- `driver.lua.encrypted` is PKCS#7 enveloped data (encryption level 2) — uncrackable without C4 controller private key
- No plaintext Lua source available in download

**Blocking Issue:** Cannot read driver.lua without controller's private key.

---

## ELAN OS Driver Download & Extraction

**File:** Gemstone_Lighting_ELAN_OS-20260501.zip (3.3 MB) from DriverCentral
**Extraction:** Successful

**Findings:**
- `gemstone_lighting.edrvc` is Cindev proprietary binary format (magic bytes `6E 00 00 00 11 00 00 00 "Gemstone"`)
- File is encrypted/obfuscated; only header strings readable
- No plaintext driver source available

**Blocking Issue:** Cannot read edrvc file — proprietary binary format.

---

## PDF Documentation Extraction & Analysis

**File:** gemstone_integration_note.pdf (130 pages, ~3.3 MB)
**Extraction:** Used pypdf to extract text (153 KB) + 267 embedded PNG/JPG images

**Findings:**
- Pages 1-130: Pattern catalog entries with PatternId UUIDs, Brightness 0-255, Speed 0-255
- Pages 3-4: Custom Pattern Page + Event Mapper screenshots (3 largest images)

**User-Facing JSON Property Shape (from screenshots):**
```json
{
  "animation": "motionless",
  "brightness": 0-255,
  "colors": [...],
  "patternId": "<uuid>",
  "speed": 0-255
}
```

**For preset patterns:**
```json
{
  "patternId": "<uuid>",
  "brightness": 0-255,
  "speed": 0-255
}
```

---

## Follow-Up API Probes with Gemstone-Specific Keys

**Test Matrix:** 70+ probe combinations against controller at 192.168.1.238:80

**Keys tested:**
- animation, patternId, brightness, colors, speed (all individual and combined)
- Path variations: `/`, `/api/v1/`, `/lights/`, `/control/`, `/device/`
- HTTP methods: POST, PUT, PATCH, GET with body
- Content-Type: application/json

**Result:** ALL 70+ probes returned **`404 "Invalid route."`**

**Conclusion:** The user-facing JSON property shape is what users paste into an ELAN property; the encrypted driver wraps it in some routing envelope before HTTP. The wrapper shape stays unknown.

---

## Blocking Issue Identified

**Missing Piece:** HTTP routing envelope used by C4/ELAN driver to wrap the JSON payload before sending to port 80.

The 404 errors are consistent with:
1. Invalid routing key missing from wrapper
2. Routing key provisioned during pairing handshake
3. Controller expects specific header or body wrapping mechanism

---

## Final Recommendation

**Local API discovery via speculative probing: DEFINITIVELY EXHAUSTED**

Only remaining path: **Packet capture**

Since controller speaks plaintext HTTP on port 80 (no TLS, confirmed earlier), mitmproxy + cert install NOT needed — plain Wireshark/tcpdump on bytes is sufficient.

**Agreed path:** UniFi packet capture during Gemstone app launch + on/off tap. Drop .pcap at `.squad/research/gemstone-capture.pcap`, then dissect with tshark/scapy to extract HTTP wrapper shape from port 80 traffic.

mitmproxy path officially abandoned (user refused phone cert install, and UniFi capture is cleaner for plaintext HTTP anyway).

---

## Research Artifacts

**Archived at:** `.squad/research/c4-driver/` and `.squad/research/elan-driver/`

- C4 driver: driver.xml (TCP 80 confirmed), driver.lua.encrypted (PKCS#7)
- ELAN driver: gemstone_lighting.edrvc (Cindev binary, magic bytes confirmed)
- PDF text: 153 KB extracted, 267 images, 3 largest screenshots containing JSON property shapes

---

**Compiled by:** Scribe
**Status for next turn:** Awaiting Mads to run UniFi packet capture. All research artifacts preserved.
