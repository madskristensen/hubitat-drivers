# Session Log: C4 + ELAN Driver Extraction and Protocol Discovery

**Date:** 2026-05-16
**Session Start:** 2026-05-16T16:04:57-07:00 (UTC 2026-05-16T23:04:57Z)
**Agents:** Cypher (research), Coordinator (self-probe inline)
**Status:** RESEARCH COMPLETE — All speculative paths exhausted

---

## Summary

The Gemstone Lights C4 driver (737 KB) and ELAN OS driver (3.3 MB) were successfully downloaded and extracted from DriverCentral. Both drivers and their companion documentation (130-page PDF) were analyzed.

**Key Deliverable:** Extracted JSON property shape from PDF screenshots:
- **Custom patterns:** `{ "animation": "motionless", "brightness": 0-255, "colors": [...] }`
- **Preset patterns:** `{ "patternId": "<uuid>", "brightness": 0-255, "speed": 0-255 }`

**Challenge:** Both drivers are encrypted (C4 driver.lua is PKCS#7 enveloped; ELAN edrvc is proprietary binary). No plaintext source code available for inspection.

---

## C4 Driver Analysis

- **File:** gemstone_lights.c4z (737 KB)
- **Contents:**
  - `driver.xml` — metadata, confirms TCP port 80, "Send Custom Pattern JSON" command, "Get Version Info" handshake
  - `driver.lua.encrypted` — PKCS#7 enveloped data (encryption level 2), uncrackable without controller private key
  - `assets/` — miscellaneous resources

**Blocking Issue:** driver.lua is encrypted. Wayback Machine search for older unencrypted versions yielded no results.

---

## ELAN OS Driver Analysis

- **File:** Gemstone_Lighting_ELAN_OS-20260501.zip (3.3 MB)
- **Contents:**
  - `gemstone_lighting.edrvc` — Cindev proprietary binary format (magic: `6E 00 00 00 11 00 00 00 "Gemstone"`), encrypted/obfuscated
  - Miscellaneous resources

**Blocking Issue:** edrvc is a proprietary binary; no decompilation tools publicly available.

---

## PDF Documentation Analysis

- **File:** gemstone_integration_note.pdf (130 pages, ~3.3 MB)
- **Extraction:** pypdf text (153 KB) + 267 embedded images
- **Key Content:**
  - Pages 1-130: Pattern catalog (PatternId UUIDs, Brightness 0-255, Speed 0-255)
  - Pages 3-4: Screenshots of Custom Pattern UI and Event Mapper

**User-Facing JSON Confirmed:**
- Animation values: "motionless" (and others from catalog)
- Brightness range: 0-255
- Speed range: 0-255
- Color arrays: supported
- PatternId: UUID format

---

## Protocol Probing Results

**Testing Matrix:** 70+ HTTP request combinations tested against 192.168.1.238:80

**Keys tested (all Gemstone-specific):**
- `animation`, `patternId`, `brightness`, `colors`, `speed`
- Paths: `/`, `/api/v1/`, `/lights/`, `/control/`, `/device/`
- Methods: POST, PUT, PATCH, GET with JSON body
- Headers: Content-Type: application/json

**Result:** Every single probe returned `404 "Invalid route."`

**Analysis:** The JSON property shape is what users paste into an ELAN property; the encrypted driver wraps it in a routing envelope before HTTP transmission. The wrapper shape remains unknown without packet capture.

---

## Critical Finding: Routing Envelope Missing

The controller at 192.168.1.238:80 rejects all direct API calls with a generic 404. This indicates:

1. **Routing key is required** — likely provisioned during pairing handshake
2. **HTTP wrapper is proprietary** — specific header or body wrapping mechanism expected
3. **Cannot be reverse-engineered** via direct API probing alone

**Conclusion:** Packet capture is the only remaining path to discover the routing envelope.

---

## Final Recommendation: Packet Capture

**Status:** Plain HTTP on port 80 (no TLS, confirmed). mitmproxy + phone cert installation NOT required.

**Agreed Path:** UniFi packet capture (Mads has UniFi built-in capture capability)
1. Start UniFi packet capture on controller IP (192.168.1.238)
2. Launch Gemstone app on phone and send on/off and pattern commands
3. Stop capture and export .pcap file
4. Drop at `.squad/research/gemstone-capture.pcap`
5. Dissect with tshark/scapy to extract HTTP routing envelope

**Timeline:** ~15 minutes (Mads' execution)

**mitmproxy path:** Abandoned per user directive (refused phone cert installation, UniFi capture is cleaner for plaintext HTTP anyway)

---

## Next Steps

1. **Mads:** Run UniFi packet capture, export .pcap
2. **Tank:** Once pcap available, dissect HTTP and reverse-engineer wrapper shape
3. **Tank:** Implement v0.2.0 wire-up with confirmed keys (animation/patternId/brightness/speed/colors, 0-255 ranges)
4. **Squad:** Finalize v0.2.0 release with full local API support

---

## Archive References

- **C4 driver artifacts:** `.squad/research/c4-driver/`
- **ELAN driver artifacts:** `.squad/research/elan-driver/`
- **Decisions merged:** `.squad/decisions.md` (new sections appended)
- **Orchestration logs:**
  - `2026-05-16T23-04-57Z-cypher.md` (SDDP + C4 research)
  - `2026-05-16T23-04-57Z-coordinator-driver-extraction.md` (driver extraction + probing)

---

**Compiled by:** Scribe
**Session Status:** Complete. Awaiting Mads' packet capture action.
