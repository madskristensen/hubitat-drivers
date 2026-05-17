# Decisions

## 2026-05-17T11:31:31-07:00 — Touchstone v0.1.2 CRC32 allowlist fix

**By:** Tank
**Requested by:** Mads

### What was forbidden

- Hubitat rejected the driver at install time because `import java.util.zip.CRC32` is not on the platform import allowlist.
- During the audit, `java.io.ByteArrayOutputStream` was also treated as risky for the same sandbox reason and removed proactively.

### Replacement strategy

- Replaced the `CRC32` object usage with a pure-Groovy table-driven `crc32(byte[] data)` helper using canonical CRC-32/ISO-HDLC settings:
  - reversed polynomial `0xEDB88320L`
  - init `0xFFFFFFFFL`
  - reflected byte updates
  - xor-out `0xFFFFFFFFL`
- Hoisted the 256-entry lookup table into `@Field static final long[] CRC32_TABLE` so the table is built once at driver load, not per frame.
- Replaced `ByteArrayOutputStream` frame assembly with a small `concatBytes()` helper plus manual byte-array composition so the Tuya `55AA ... CRC32 ... AA55` framing stays identical without depending on extra `java.io` classes.

### Import audit result

Remaining explicit imports in `drivers/touchstone-fireplace/touchstone-fireplace.groovy` after the fix:
- `groovy.transform.Field`
- `groovy.json.JsonOutput`
- `groovy.json.JsonSlurper`
- `javax.crypto.Cipher`
- `javax.crypto.spec.SecretKeySpec`

No `java.util.zip.*`, `java.nio.*`, or `java.io.*` imports remain in the driver file.

---

## 2026-05-17T11:24:33-07:00 — Touchstone v1.1 generalization pass

**By:** Tank
**Requested by:** Mads

### What changed

- Kept the file path stable at `drivers/touchstone-fireplace/touchstone-fireplace.groovy`, but renamed the driver metadata display name to **`Touchstone / Tuya Fireplace`** and updated the top comment header to frame the driver as **Touchstone Sideline Elite — and other Tuya WiFi fireplaces**.
- Added **Device Profile** handling with three modes:
  - `Sideline Elite (tested)` → hardcoded verified DP map
  - `Generic Tuya Fireplace` → only power, heat level, and temperature setpoint commands are wired
  - `Custom` → per-role DP number preferences for power / flame color / flame brightness / log color / heat level / temp setpoint F / temp setpoint C
- Added on-device discovery tooling: `discoverDPs()`, `captureBaseline()`, `captureDiff()`, and `setRawDP()` (while keeping `setDpRaw()` as a legacy alias).
- Moved DP resolution behind `dpFor(role)` so command paths and status parsing both honor the active profile at runtime.

### Tradeoffs

- The **Generic** profile intentionally refuses to guess flame/log/light mappings. Those commands warn and point users to **Custom** or `setRawDP()` instead of pretending the Sideline LED map is universal.
- `Custom` preferences are gated in the driver UI with `if (settings?.deviceProfile == "Custom")`, which means Hubitat only reveals those fields after the user saves/reopens the device page. The runtime helper still falls back to sane defaults so a fresh Custom selection does not explode.
- Discovery queries request a broader DP range for mapping work so non-Sideline devices can be explored from Hubitat without tinytuya/Python, while the existing socket/AES/retry/polling behavior stays intact.

### Still TODO

- **Link:** README walkthrough for the new discovery workflow and for other Touchstone / Tuya fireplace models.
- **Switch:** Expanded validation cases for `Generic Tuya Fireplace` and `Custom`, including `setRawDP()` audit logging and baseline/diff mapping flow.

---

# Touchstone Driver Documentation — Link Shipped

**Date:** 2026-05-17T18:31:31Z  
**Agent:** Link (DevRel / Documentation)  
**Status:** Complete  

## What Shipped

**Two new files:**

1. **`drivers/touchstone-fireplace/README.md`** — Per-driver user guide (18.2 KB)
2. **`drivers/touchstone-fireplace/packageManifest.json`** — HPM manifest

**Scope covered:**
- Device support matrix (Sideline Elite verified; other Touchstone lines + generic Tuya via Custom profile)
- Complete capability + command reference
- Installation (HPM stub + manual)
- Setup walkthrough: two-path Tuya local key extraction (Method A: iot.tuya.com + tinytuya durable; Method B: Home Assistant with caveat)
- Preferences reference with all driver settings
- **Key section:** "Got a Different Touchstone? Map It Yourself" — in-driver discovery walkthrough using `discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()` — no Python needed
- Known quirks: single TCP slot, temperature setpoint persistence, separate °F/°C tracking, remote buttons without Tuya equivalents
- Troubleshooting (offline, CRC32 import error, wrong DP responses, power transition window)
- Credits + changelog

## Key Decisions Documented

### Tuya Local Key Extraction Path

Per `.squad/decisions.md` (Cypher's 2026-05-17 Tuya Portal-Free audit), documented both extraction methods:

- **Method A (Primary):** iot.tuya.com portal + tinytuya Python wizard — durable, Tuya controls the outcome, no fragility from hardcoded client_ids
- **Method B (Alternative):** Home Assistant tuya-local cloud-auth — fast (~5 min) but relies on Tuya's HA-issued hardcoded client_id that Tuya can revoke unilaterally

README presents A as the "durable" standard; B as the "Home Assistant shortcut with caveat." Both link to `.squad/skills/tuya-cloud-key-extraction/SKILL.md` for deep-dives.

**Rationale:** Mads is already on Path A (iot.tuya.com); this documentation reflects that reality + arms users to make informed trade-off choices.

### Device Generalization Strategy

Per `.squad/decisions.md` (Mads's 2026-05-17 user directive):

- **Device Profile preference:** Selects Sideline Elite (default, verified), Generic Tuya Fireplace (basic control), or Custom (user-mapped)
- **Custom profile reveals DP number overrides** in preferences — users don't need to edit driver code
- **In-driver discovery commands** (`discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()`) enable users to self-discover DP maps for unknown models
- **Dedicated README section** ("Got a Different Touchstone? Map It Yourself") walks users through the discovery workflow step-by-step
- **Encourages community contribution:** "Open a GitHub Issue with your DP map so we can ship it as a preset"

**Rationale:** Eliminates the barrier for users with other Touchstone models; community can accumulate presets over time.

### HPM Manifest Format

Matched `.squad/decisions.md` DP map and existing repo patterns (Gemstone, SunStat):

- Single-driver manifest (SunStat is parent/child; Touchstone is single-file)
- UUID: 63f16ca9-2413-418f-a5d5-b798c23452ee (fresh UUID per driver)
- Version field in both root + drivers array (matches Gemstone convention)
- minimumHEVersion: "2.3.0" (matches other drivers)
- dateReleased: "2026-05-17" (per CURRENT_DATETIME directive)

## Conventions Adopted

From **gemstone-lights/README.md** + **sunstat-thermostat/README.md:**

| Pattern | Source | Adoption |
|---|---|---|
| **Header tagline + status line** | Both | ✓ Used; status line includes v0.1.1 + beta note |
| **Supported Devices table** | SunStat | ✓ Adapted for model/profile variants |
| **Capabilities + Attributes table** | Gemstone | ✓ Two-table layout (std + custom) |
| **Command Reference (table)** | Gemstone | ✓ Used; four sub-tables (Standard, Heating, Lighting, Discovery) |
| **Installation: HPM + Manual** | Both | ✓ Mirrored structure; HPM includes stub for future publish |
| **Setup with auth bootstrap walkthrough** | SunStat | ✓ Adapted for Tuya local key extraction (two methods, step-by-step) |
| **Preferences Reference table** | Gemstone | ✓ All prefs + defaults + one-line descriptions |
| **Known Quirks section** | Gemstone | ✓ Used; 4 quirks relevant to Tuya + Sideline Elite behavior |
| **Troubleshooting by symptom** | Gemstone | ✓ 4 troubleshooting paths (offline, CRC32, wrong DP, transition window) |
| **Credits + Changelog** | Gemstone | ✓ Credits: Tuya v3.3 sources, tinytuya, empirical mapping |
| **GitHub + Community links** | Gemstone | ✓ Footer sign-off |

## Open / Future

1. **HPM publish:** Not in scope; Mads will do separately
2. **Screenshots:** Optional nice-to-have (Hubitat device page preferences); not blocking
3. **Hubitat driver README skill:** `.squad/skills/hubitat-driver-readme/SKILL.md` — would standardize this pattern for future drivers; not yet created; consider as future documentation infrastructure task

## Quality Checklist

- ✅ All driver capabilities + commands documented
- ✅ All preferences with defaults + descriptions
- ✅ Installation (HPM stub + manual) clear
- ✅ Tuya local key extraction walkthrough (two methods, step-by-step)
- ✅ Device generalization via Discovery section (step-by-step; no Python needed)
- ✅ Known quirks from Switch's test plan + real-device findings
- ✅ Troubleshooting covers common user errors
- ✅ Credits acknowledge sources + contributors
- ✅ Changelog documents v0.1.1 + v0.1.0
- ✅ Conventions matched to repo style (Gemstone + SunStat)

---

**Next:** Mads reviews docs. If approved, docs are ready for HPM publish (separate step). If clarifications needed, Link can iterate in v0.1.2 README revision.

---

### 2026-05-17T11:31:31-07:00: Bug report from Mads (Touchstone driver install)

**By:** Mads (via Copilot)

**What:** Hubitat rejected the v1 Touchstone driver on install with: `Importing [java.util.zip.CRC32] is not allowed`

**Why captured:** Hubitat enforces a strict import allowlist (it's part of the platform security model — drivers run in a sandboxed Groovy environment). `java.util.zip.CRC32` is NOT on the allowlist, so any Tuya v3.3 driver using it for packet checksums will fail at compile time.

**Fix required (Tank v1.2):**
1. Remove `import java.util.zip.CRC32`
2. Implement CRC32 in pure Groovy as a private helper method. Use the standard polynomial 0xEDB88320 + 256-entry precomputed lookup table. Reference: kkossev's Tuya drivers (kkossev/Hubitat - their tuya local protocol files).
3. Verify every other `import` in the file against Hubitat's allowlist. Likely-safe imports for this driver: `hubitat.helper.HexUtils`, `groovy.transform.Field`, `javax.crypto.Cipher`, `javax.crypto.spec.SecretKeySpec`. If anything else (e.g., from `java.util.zip.*`, `java.nio.*` beyond ByteBuffer, `java.security.*` beyond MessageDigest) is in the file, swap it for an allowed equivalent or implement in-Groovy.
4. Smoke test by re-attempting install after the fix.

**Skill update:** Add "Hubitat import allowlist" to `.squad/skills/tuya-local-groovy/SKILL.md` — this gotcha applies to every Hubitat driver, not just Tuya.

---

## 2026-05-17T11:24:33-07:00: User-directed naming decision — "Option C"

**By:** Mads (via Copilot — coordinator decided per autopilot after surfacing trade-offs)

**What:** Touchstone driver positioning decision.

- **File path:** stays `drivers/touchstone-fireplace/touchstone-fireplace.groovy` (community SEO: Hubitat users will search for "Touchstone")
- **Driver display name** in `metadata { definition { name: ... } }`: change from `"Touchstone Sideline Elite"` to **`"Touchstone / Tuya Fireplace"`** — accurate framing in Hubitat's driver picker
- **README header:** "Touchstone Sideline Elite — and other Tuya WiFi fireplaces"
- **Device Profile preference** default: `Sideline Elite (tested)` with `Generic Tuya Fireplace` and `Custom` as secondary options

**Why:** Mads asked "is this more of a Tuya fireplace driver than a touchstone then?" — recognized that the driver is fundamentally Tuya v3.3 + a DP-map config, not a brand-locked driver. Option C threads the needle: community discoverability + honest scope + room for other Tuya WiFi fireplaces. Captured for Tank's v1.1 follow-up.

---

## 2026-05-17T11:10:56-07:00: User directive — Touchstone driver must be generalizable

**By:** Mads (via Copilot)

**What:** The Touchstone driver should work for other Touchstone fireplace models (Sideline Steel, Sideline Linear, Forte, Onyx, etc.), not just the Sideline Elite we just mapped. Users with other models cannot reasonably do manual DP discovery via tinytuya + Python — the driver itself must provide the discovery workflow.

**Why:** Mads asked "how can you make it work for other touchstone lines too if we can't verify the api the way we just did manually?" — captured as a scope/design directive for Tank's next pass.

**Required driver features (v1.1 or fold into v1 if Tank hasn't sealed the file):**

1. **Discovery commands on the device page:**
   - `discoverDPs()` — call `status()` and log the full DP dump (mimics `python -m tinytuya OutletDevice.status()`)
   - `captureBaseline()` — snapshot state
   - `captureDiff()` — compare to baseline, log which DPs changed; users press a remote button between the two
   - `setRawDP(dpId, value)` — write any DP directly so users can experiment with unmapped fields

2. **Preference-driven DP mapping:**
   - "Device Profile" dropdown: `Sideline Elite` (default, mapped), `Generic Tuya Fireplace` (DP 1/2/5 only), `Custom`
   - `Custom` mode reveals individual DP-number text inputs (`flameColorDp`, `logColorDp`, `flameBrightnessDp`, `heatLevelDp`, etc.)

3. **Universal safe defaults** (verified across Tuya ecosystem, not just this device):
   - DP 1 = power (bool)
   - DP 5 = mode/level (enum string "0"/"1"/"2")

4. **README guidance** (Link's task):
   - "Got a different Touchstone? Here's how to map it" section
   - Step-by-step using the driver's own discovery commands — no Python/tinytuya needed
   - Invite users to share their DP maps via GitHub Issues so presets accumulate over time

**Action items:**
- Tank: fold discovery commands + Device Profile preference into the scaffold if still in-flight, otherwise queue as immediate v1.1 follow-up
- Link: incorporate the "other models" walkthrough into the README

---

## 2026-05-17T11:07:22-07:00: Touchstone Sideline Elite — Real-Device Test Plan

**Date:** 2026-05-17T11:07:22-07:00  
**Author:** Switch (Tester / QA Engineer)  
**Status:** Ready for driver handoff (tank)  
**Target Device:** Touchstone Sideline Elite (Tuya v3.3, LAN port 6668)  
**Test Harness:** Mads (human runner; ~30 min smoke pass)  

[Test plan includes 19 tests covering: pre-flight, initialization, power control, heat levels, flame colors, log colors, brightness controls, temperature setpoint, refresh, state sync (app), state sync (remote), network recovery, device power recovery, app collision recovery, invalid enum values, out-of-range temperature, rapid command bursts, 1-hour stability, and cleanup. Full plan in Tank's test file.]

---

## 2026-05-17T11:07:22-07:00: Tank — Touchstone driver scaffold shipped

**By:** Tank

**What shipped:**
- Added `drivers/touchstone-fireplace/touchstone-fireplace.groovy` as a single-file Hubitat driver for the Touchstone Sideline Elite fireplace.
- Implemented Tuya Local v3.3 framing in Groovy: rawSocket TCP/6668, AES-128-ECB encryption/decryption, `55AA` packet framing, CRC32 validation, queued request handling, and defensive `parse()` buffering for concatenated / partial LAN frames.
- Wired the requested preferences: device IP, device ID, local key (`password` input), preferred temperature unit (default `F`), polling interval (default 60s), and `logEnable` / `txtEnable` toggles.
- Wired the requested capabilities + commands: `Switch`, `Refresh`, `Initialize`, `Polling`, `TemperatureMeasurement`, plus `setFlameColor`, `setFlameBrightness`, `setLogColor`, `setHeatLevel`, `setHeatingSetpoint`, and `setDpRaw`.
- Surfaced the requested attributes: `power`, `flameColor`, `flameBrightness`, `logColor`, `heatLevel`, `heatingSetpoint`, `temperature`, `online`, `tempUnit`, and raw discovery attributes `dp103`, `dp105`, `dp107`, `dp108`.
- Added the single-connection mitigation requested by Mads: request queue + retry backoff at 5s / 15s / 30s, with log messaging that points at the likely Smart Life / Tuya single-socket contention case.
- Added the power-transition safeguard requested by this session's discovery: writes schedule delayed refresh, and immediate DP14-derived setpoint updates are suppressed during the post-power settle window.

**Known gaps / follow-ups:**
- DP `103`, `105`, `107`, and `108` are intentionally surfaced raw only. This scaffold does **not** claim semantics for them beyond exposing their current values in Hubitat.
- The enum dictionaries for DP `101`, `102`, and `104` are still raw placeholder strings. The command inputs expose likely Tuya ranges, but Switch still needs to confirm the human-friendly labels on real hardware before Link documents them as authoritative.
- There is no live hardware validation in this scaffold commit. The Tuya framing matches tinytuya / qwerk community implementations, but Mads still needs a real Hubitat import + fireplace smoke test.
- The broader "generalize for other Touchstone models" directive is **not** fully folded into this file yet. Discovery commands like `discoverDPs()` / `captureBaseline()` / `captureDiff()` and profile-driven DP remapping remain good v1.1 follow-up work.
- `setDpRaw` is the only advanced discovery command in v0.1.0. It covers raw experimentation, but it is not yet the full self-discovery workflow Mads asked for in the generic Touchstone directive.

---

## 2026-05-17: 2026 Tuya Portal-Free Key Extraction Assessment (Cypher — CORRECTION)

**Date:** 2026-05-17T10:10:26-07:00  
**Author:** Cypher (Integration / Protocol Engineer)  
**Status:** Definitive  
**⚠️ SUPERSEDES:** Prior claim in this same decisions.md (session cypher-6, Touchstone Tuya Feasibility) that `make-all/tuya-local` cloud-auth "passes Mads' no developer app boundary" if he has HA. That was **optimistic and under-flagged three critical constraints.**

### Verdict

**Yes-but-fragile.** One genuinely portal-free path exists in 2026. However, it is **not broadly applicable** and carries **unilateral revocation risk** by Tuya.

### What Was Wrong in Cypher-6

The prior entry stated:
> "Path 2 detail — make-all/tuya-local (HA integration, no dev account needed) ... uses SmartLife credentials only, no developer portal. Recommended if user has HA."

**Constraints that were glossed over:**

1. **Requires Home Assistant to be installed and running** — not a standalone CLI tool. This was mentioned but buried as "~5 min if you have HA"; it's a prerequisite, not just a timing note.

2. **Relies on hardcoded Tuya-issued `client_id = "HA_3y9q4ak7g4ephrvke"`** (`schema = "haauthorize"`) — Tuya can revoke this ID unilaterally, breaking the entire flow for all `tuya-local` users simultaneously. No workaround. This fragility was **completely unmentioned**.

3. **Auth endpoint is `apigw.iotbing.com`, not iot.tuya.com** — This is why it's "portal-free," but this distinction was not explained. The endpoint is Tuya's consumer Smart Life API gateway. It is **not** the Tuya IoT developer portal (`iot.tuya.com`). However, Tuya controls this endpoint and can modify or revoke it.

### The Correct Assessment

| Method | Portal Required | HA Dependency | Revocation Risk | Recommended |
|---|---|---|---|---|
| `make-all/tuya-local` cloud-auth | ❌ No | ⚠️ Yes, required | 🔴 High (hardcoded client_id) | ✅ Only if HA installed; acknowledge risk |
| `tinytuya wizard` | ⚠️ Yes (free account) | ❌ No | 🟡 Low (Tuya can time-limit trial) | ✅ Most durable non-HA path |
| `localtuya` (HA) | ⚠️ Yes (required) | ⚠️ Yes | 🟡 Medium | ⚠️ Fallback |

### For Mads Specifically

**Mads does not run Home Assistant.** Therefore:
- The portal-free path is **inaccessible** to him.
- The **iot.tuya.com portal path he has already started is the correct choice** — it is more durable than relying on a Tuya-controlled consumer API and a hardcoded client_id.

---

## 2026-05-17: Bosch → Touchstone Pivot (User Directive)

**Date:** 2026-05-17T09:53:47-07:00  
**By:** Mads Kristensen (via Copilot)  
**Status:** Archived (Bosch), Active (Touchstone)

Drop the Bosch Home Connect fridge driver project. Do not pursue it further. New target: Hubitat driver for the Touchstone LED fireplace (WiFi-connected; Tuya-based; exposes flame color, log color, brightness, etc.).

**Rationale:** Developer-portal requirement was a non-starter for Bosch. Pivoting to Tuya-based device with more promising integration surface.

**Impact:** Bosch decision record remains for historical reference but is no longer active scope. Next phase: Feasibility pass for Touchstone by Cypher + Trinity.

---

## 2026-05-17: Touchstone Tuya Feasibility (Cypher)

**Date:** 2026-05-17T09:53:47-07:00  
**Author:** Cypher (Integration / Protocol Engineer)  
**Status:** Complete — ready for team review

### Verdict

**Yes-with-caveats.** Tuya Local (LAN) over `interfaces.rawSocket` + AES is the right path. The Touchstone Sideline is confirmed Tuya (product ID `qhwld7e4eqvu5fbp`) with a fully documented DP map from production HA integration. Local key extraction is one-time only and requires no developer portal if using SmartLife credentials via HA tuya-local cloud-auth path.

### Key Findings

**Control path:** Tuya Local (LAN) — unconditionally preferred over Tuya Cloud API.

**Device confirmed:** Touchstone Sideline is explicitly listed in `make-all/tuya-local` DEVICES.md. Product ID `qhwld7e4eqvu5fbp` is the Tuya catalog identifier.

**DP map — Touchstone Sideline** (from `make-all/tuya-local` reference implementation):

| DP | Type | Name | Values |
|---|---|---|---|
| 1 | boolean | Power | `true` = on, `false` = off |
| 101 | string | Flame color/effect | `"1"`=Orange, `"2"`=Blue, `"3"`=Yellow, `"4"`=Orange+Blue, `"5"`=Orange+Yellow, `"6"`=Blue+Yellow |
| 102 | string | Flame brightness | `"1"`=20%, `"2"`=40%, `"3"`=60%, `"4"`=80%, `"5"`=100% |
| 103 | string | Flame speed | `"1"`=Slow, `"2"`=Medium, `"3"`=Fast |
| 104 | string | Ember/log color | `"1"`=orange, `"2"`=red, `"3"`=blue, `"4"`=yellow, `"5"`=green, `"6"`=purple, `"7"`=teal, `"8"`=pink, `"9"`=white, `"10"`=peachpuff, `"11"`=black (off), `"12"`=grey (Mystery/Cycle effects) |
| 105 | string | Log brightness | `"1"`–`"12"` = 8%–100% (linear scale) |

**Platform feasibility:** Hubitat `interfaces.rawSocket` supports Tuya Local v3.3 protocol (AES-128-ECB) with available `javax.crypto.Cipher`. Known issue: silent drop on idle connections — mitigated by heartbeat ping every 20 seconds + reconnect handler. Standard practice for Tuya drivers.

**Local key extraction UX:** Three paths compared:
- `make-all/tuya-local` cloud-auth: ✅ Recommended. No developer account required — uses SmartLife app credentials only (~5 min).
- `tinytuya wizard`: ⚠️ Fallback. Requires free Tuya IoT developer account (~20 min one-time).
- mITM: ❌ Broken since 2023.

**Key correction:** Flame and ember colors are **NAMED PALETTE INDICES** (6 flame effects, 12 log colors), **not** free-form RGB. `ColorControl` capability expecting HSV input will map user colors to nearest palette entry with confusing rounding. **Use named custom commands (`setFlameColor(name)`, `setLogColor(name)`) instead** — more honest UX.

### Open Questions for Switch (Real-Device Validation)

1. **Model confirmation** — Is it the Sideline series? Check the device label. Run `python -m tinytuya scan` to verify.
2. **Protocol version** — Run `python -m tinytuya scan` to confirm v3.3 vs v3.4/v3.5. Determines framing complexity.
3. **Full DP map** — Run `python -m tinytuya wizard` or use HA tuya-local cloud-auth. Confirms DP assignments match assumptions.
4. **Connectivity test** — After extracting `deviceId`, `ip`, `localKey`, run `tinytuya` test to confirm local control works.
5. **Single vs dual connection** — Tuya modules often allow one TCP connection at a time. Test by closing all apps before connecting.

### Sources

- `make-all/tuya-local` DEVICES.md + `touchstone_sideline_fireplace.yaml`
- `make-all/tuya-local` README.md — cloud-assisted config flow (no dev portal path)
- `jasonacox/tinytuya` — local key wizard
- `rospogrigio/localtuya` — protocol v3.1–3.4 reference
- Tuya developer docs — `dbl` category = "Electric fireplace"

---

## 2026-05-17: Touchstone Architecture (Trinity)

**Date:** 2026-05-17T09:53:47-07:00  
**Author:** Trinity (Lead / Architect)  
**Status:** Proposal — awaiting Mads approval

### Recommended Architecture

**Single Groovy driver, no cloud dependency.** Tuya Local (LAN) over rawSocket TCP + AES. Single file at `drivers/touchstone-fireplace/touchstone-fireplace.groovy`.

### Capability Mapping

| Capability / Attribute | DP | Note |
|---|---|---|
| `Switch` | 1 | on/off |
| `SwitchLevel` | 102 | Flame brightness; map 0–100 → `"1"`–`"5"` buckets |
| `Refresh` | all | Status query |
| `Initialize` | — | Socket connect + schedule |
| **Custom command `setFlameColor(name)`** | 101 | Named palette: orange, blue, yellow, orange+blue, orange+yellow, blue+yellow |
| **Custom command `setLogColor(name)`** | 104 | Named palette (12 colors) |
| **Custom command `setLogBrightness(level)`** | 105 | 12-step brightness for log lighting |
| **Custom command `setFlameSpeed(speed)`** | 103 | Slow / Medium / Fast |
| **Custom attribute `flameColor`** | string | Current flame effect name |
| **Custom attribute `logColor`** | string | Current log/ember color name |

**⚠️ CORRECTION (Cypher finding supersedes Trinity's original proposal):**  
Trinity originally recommended `ColorControl` capability for flame color mapping. **This is incorrect and should NOT be used.** Cypher's analysis confirms that flame and ember colors are named palette indices (6 flame effects, 12 log colors), not free-form RGB/HSV. `ColorControl` with HSV input will produce confusing rounding behavior when mapping to palette entries. **Use the named custom commands above instead** — this is the correct UX for palette-based color selection.

### Effort Estimate

**Medium — 2–3 sessions.**

- Session 1: Cypher confirms DP map from tinytuya output; Trinity finalizes DP-to-capability mapping; Tank scaffolds driver with Tuya Local protocol layer.
- Session 2: Tank wires all capability commands to DPs; Switch writes test plan; Mads validates on real device.
- Session 3 (conditional): If protocol version is 3.4/3.5 or DPs differ, one additional session for fixes.

### Folder Layout

```
drivers/
  touchstone-fireplace/
    touchstone-fireplace.groovy     ← single driver file
    README.md                       ← install guide + local-key extraction steps
    packageManifest.json            ← HPM manifest (new UUID v4)
```

### Next Steps

1. **Mads:** If feasibility confirmed safe, run `tinytuya wizard` (or `python -m tinytuya scan`) against the fireplace and share output JSON.
2. **Cypher:** Analyze DP map output, confirm protocol version (3.3/3.4/3.5).
3. **Tank:** Once DP map is known, scaffold driver using Tuya Local protocol layer.
4. **Link:** README + local-key extraction steps once architecture is locked.

---

## 2026-05-17: Touchstone Sideline Elite — Local LAN Control Achieved

**Date:** 2026-05-17T10:47:09-07:00  
**Author:** Coordinator (Direct Mode)  
**Status:** Verified ✅

### Summary

End-to-end LAN control of the Touchstone Sideline Elite fireplace confirmed from Mads' machine. Completed: Tuya IoT signup → tinytuya wizard → local_key extraction → `tinytuya.OutletDevice.status()` query → live DP dump validation.

### Device Facts

- **Product:** Touchstone Sideline Elite electric LED fireplace
- **Tuya productKey:** nc1lwvgjse1ujlr
- **Tuya category:** qn (electric fireplace)
- **Device ID:** 70223053e8db84d10b53
- **IP (LAN):** 192.168.1.38
- **MAC:** e8:db:84:d1:0b:53
- **Protocol:** v3.3, AES-encrypted
- **local_key:** <stored at C:\Users\madsk\devices.json — DO NOT inline value>

### Heater DP Map (Official Tuya Schema)

| DP | Type | Name | Range |
|---|---|---|---|
| 1 | bool | switch | on/off |
| 2 | int | temp_set | 19–30°C |
| 3 | int | temp_current | 0–50°C |
| 5 | enum | level | 0/1/2 (heat level) |
| 13 | enum | temp_unit_convert | c/f |
| 14 | int | temp_set_f | 67–88°F |
| 15 | int | temp_current_f | 32–122°F |

### Vendor-Custom LED DPs (Empirical Mapping — TBD)

Not in Tuya schema. Observed values from live DP dump:

| DP | Type | Observed | Status |
|---|---|---|---|
| 101 | string-enum | "1" | TBD |
| 102 | string-enum | "5" | TBD |
| 103 | string-enum | "1" | TBD |
| 104 | string-enum | "4" | TBD |
| 105 | string-enum | "5" | TBD |
| 107 | bool | false | TBD |
| 108 | bool | false | TBD |

Next session: Validate empirical DP mapping via Tuya app interaction.

### Operational Lesson

**Tuya IoT Cloud Project API subscription gotcha:** A new Tuya IoT Cloud Project does NOT auto-subscribe to the APIs needed for `tinytuya wizard`. Must manually subscribe to:
- IoT Core
- Authorization Token Management
- Smart Home Basic Service
- Device Status Notification

All are free trials with no card on file required. This was the key blocker before Mads could run the wizard.

### Session Context

- **Topic:** touchstone-local-control-achieved
- **Mode:** Direct (Coordinator — no agent spawns)
- **Requested by:** Mads Kristensen
