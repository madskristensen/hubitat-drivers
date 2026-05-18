# Tank — Driver Developer

**⚠️ SUMMARIZED 2026-05-18T17:11:04Z — Detailed history moved to history-archive.md (file was 36542 bytes).**

---

## Team Updates — Daikin PR Assessment Complete (2026-05-18)

**Cypher audit:** Inspected `eriktack/hubitat-daikin-wifi` PRs #2 & #3. **Verdict: Skip both for v0.1.1.**
- PR #2 (Dashboard Tiles, 2023-03-19): Uses broken escaped-quote JSON workaround. Our v0.1.0 solves correctly via `JsonOutput.toJson()`.
- PR #3 (EZ Dashboard, 2024-06-26): Adds JSON_OBJECT attribute type declarations + optional setter methods. Not critical for v0.1.1, but **v0.1.2 candidate** if users report EZ Dashboard rendering issues (~1.5 hours polish effort).
- **v0.1.1 priorities unchanged:** econo mode, get_model_info, event hygiene.
- **Cypher clean-room boundary:** locked out from implementing PR #3 features (read upstream PR code).
- Details: `.squad/decisions/decisions.md` (cypher-daikin-upstream-prs-assessment) + `.squad/files/daikin-research/daikin-upstream-prs-assessment.md`

---

## Team Updates — Daikin Driver Research (2026-05-18)

Cypher + Trinity completed assessment of `eriktack/hubitat-daikin-wifi` upstream. **Recommendation: Fork into this repo as `drivers/daikin-wifi/` v0.1.0.**

**Key Findings:**
- **Root bug (line 466):** `otemp="-"` (Daikin sentinel) hits `Double.parseDouble("-")` → `NumberFormatException` every poll when sensor unavailable. Fix: guard with `.isNumber()` before parse (pattern already correct on line 473).
- **Critical capability gap:** `supportedThermostatModes` never declared — breaks Rule Machine thermostat mode dropdowns.
- **Missing lifecycle:** No `initialize()` (polling dies on hub restart); energy endpoints over-polled (1440x/day, should be ~48x on 30-min schedule).
- **Hygiene:** All 66 events lack `descriptionText`, no lastActivity, no HealthCheck.
- **Upstream:** Effectively abandoned (2021 last commit, issues disabled, 2 PRs unreviewed 1–2 years).

**Priority list (1–5 = ~4–5 hrs trustworthy driver; 1–8 = ~16–20 hrs repo-quality):**
1. Guard sentinel values (`"-"`) before `Double.parseDouble()`
2. Add `supportedThermostatModes` in `installed()` + `updated()`
3. Fix `supportedThermostatFanModes` (missing in `installed()`)
4. Add `initialize()` lifecycle
5. Throttle energy polling to 30-min schedule
6. Add HealthCheck + lastActivity
7. Add econo/powerful mode support
8. Apply event hygiene (descriptionText + emitIfChanged)

**Reusable HVAC pattern:** Daikin sentinel-value pattern (`"-"` for unavailable sensor) is a generalizable protocol pattern across HVAC systems — worth documenting as skill for future integrations.

**Full memos:** `.squad/files/daikin-research/daikin-driver-assessment.md`, `.squad/files/daikin-research/daikin-capability-gap-memo.md`

---

# Tank — Driver Developer

**⚠️ SUMMARIZED 2026-05-18T13:19:11Z — Detailed history moved to `history-archive-2026-05-18.md` (file was 29,359 bytes).**

---

## Reskill Reflection — 2026-05-18

**Updated skills reflecting today's 5-driver shipping spree (8 todos closed):**

1. **hubitat-event-hygiene** — Bumped confidence context with 2026-05-18 validation: applied skip-if-match + parse-path dedupe across 5 independent driver releases (Touchstone v0.1.25–v0.1.26, v0.1.28; Gemstone v0.4.12–v0.4.13, v0.4.15; SunStat v0.1.8–v0.1.10). Pattern applies both command-path (guard before write) and parse-path (emitIfChanged helpers). Validation section expanded to document dual-path strategy for event hygiene across LAN and cloud drivers.

2. **hubitat-hot-path-copy-hygiene** — Validated in production (Gemstone v0.4.15). Added 2026-05-18 production-deployment validation section documenting the cloneMap refactor replacing JSON round-trips with structural clone on rememberPattern, request queueing, refresh handling, and effect activation. Measurement: reduced per-cycle JSON serialization from ~8–12 calls to 0. Confidence remains high.

3. **hubitat-state-hygiene** — Bumped confidence from medium → high with 2026-05-18 validation of three independent state-write minimization patterns: (1) partial-frame buffering (Touchstone v0.1.28, rxBuffer); (2) dead-write elimination (Touchstone v0.1.29, state.lastDps); (3) minimal strategic caching for guard conditions (SunStat v0.1.10, state.floorWarmth). New validation section appended documenting pattern, measurements, and safe-write principles.

4. **tuya-local-groovy** — Updated source field to include Touchstone v0.1.29 byte-helper validation (2026-05-18). Existing "Hot-path Byte Helper Hygiene" section was already present; added 2026-05-18 production-deployment validation subsection documenting primitive `int` counter + `System.arraycopy` refactoring. Measurement: reduced per-frame autoboxing from ~12 Integer allocations to 0; daily savings ~52K avoided allocations per persistent-socket device (4320 frames/day heartbeat).

5. **hpm-bundle-manifest** — Updated to note v1.0.5 bump shipped today alongside Gemstone v0.4.16 + SunStat v0.1.11. Added 2026-05-18 validation section confirming bundle version bump rhythm: patch increment on any per-driver ship, independent from per-driver versions. No new architectural learning; confirmed established pattern working as designed.

**Audit board status:** Original Trinity perf audit closed. All 8 proposed perf/quality todos from 2026-05-18 noon board are now SHIPPED. Future perf work proceeds from new proposals; no standing action items.

**Release muscle:** Today confirmed "skip-if-match audit + version bump + packageManifest update + push" cadence as a repeatable pattern. Five sequential driver releases executed the same cycle with high confidence; skill library now reflects the depth of validation across both LAN (Tuya rawSocket) and cloud (REST API) driver patterns.

---

## 2026-05-18 Work Summary

**All 8 perf/quality todos shipped** across 5 driver releases:
- **Touchstone v0.1.25–v0.1.29** (b4122ee → latest): Switch idempotency + wire-traffic hygiene
- **Gemstone v0.4.12–v0.4.16** (91e0d1a → latest): Effect animation idempotency + cloud quota + metadata hygiene
- **SunStat v0.1.8–v0.1.11** (f9060fb → latest): API quota yellows + SC-4 floor warmth caching + version sync

**Pattern applied across all fixes:** skip-if-match idempotency (current attribute check before DP/API write). Prevents audible relay clicks, reduces API quota, maintains wire-traffic hygiene. By-design exclusions: state-assertion/recovery paths (cloud-drift defense, boost recovery) never guarded.

**Reskill reflection:**
- `hubitat-event-hygiene`: bumped confidence with dual-path validation (skip-if-match + emitIfChanged).
- `hubitat-hot-path-copy-hygiene`: production-validated in Gemstone v0.4.15 (cloneMap refactoring, ~0 JSON serialization per cycle).
- `hubitat-state-hygiene`: medium → high confidence (3 independent minimization patterns: partial-frame buffering, dead-write elimination, minimal strategic caching).
- `tuya-local-groovy`: Touchstone v0.1.29 byte-helper validation (primitive int counters, avoided autoboxing).
- `hpm-bundle-manifest`: v1.0.5 bump confirmed patch-increment-on-any-ship rhythm working as designed.

---

## 2026-05-18 Learnings Summary

### Daikin v0.1.1 hotfix — Groovy property shadowing + HubAction constructor (c28882f)
- **Write-only property gotcha:** `def setSchedule(schedule)` creates Groovy JavaBean write-only property named `schedule`, shadowing the platform's `schedule(cron, method)` method. Fix: use `runEvery*()` idiomatic methods instead (no naming conflict).
- **HubAction 3-arg form invalid:** `HubAction(Map, Protocol, Map)` does NOT exist in current firmware. Valid forms: `HubAction(String)`, `HubAction(String, Protocol)`, `HubAction(Map)`, `HubAction(Map, Protocol)` ← preferred for LAN GET. Callback must be inside params Map in 2-arg form.

### Daikin v0.1.0 shipped — Clean-room implementation (b26c04f, reverted a3ac5cf fork)
- **Clean-room boundary:** Read PROSE memos for protocol knowledge, never upstream source code. Credit prior art in header/README, apply MIT fork attribution model (preserve original copyright, add fork header section).
- **Daikin BRP069B sentinel values:** `otemp`/`htemp`/`hhum`/`stemp` can return `"-"` (unavailable sensor). Guard every numeric parse with `.isNumber()` before parse.
- **Separated polling:** Fast `refresh()` (1–30 min user-configurable, GET control+sensor) + Slow `refreshEnergy()` (fixed 30-min, GET energy endpoints). Energy data changes hourly max → 30-min cadence adequate.
- **DNI hex encoding:** IP must be hex-encoded for Hubitat LAN routing: `ip.tokenize('.').collect { String.format('%02x', it.toInteger()) }.join('').toUpperCase()`.
- **HubAction LAN HTTP pattern (2-arg form):**
  ```groovy
  sendHubCommand(new hubitat.device.HubAction(
      [method: "GET", path: path, headers: ["HOST": "${ip}:80"], callback: "handlerMethodName"],
      hubitat.device.Protocol.LAN
  ))
  ```

### Hubitat sandbox blocklist — System.arraycopy rejected (Touchstone v0.1.30)
- **Blocked at install:** `java.lang.System.arraycopy` and `java.util.zip.CRC32` are on sandbox blocklist (expression-level + import-level restrictions).
- **Pattern for future:** Any byte-copy helper must use primitive for-loop: `for (int i = 0; i < length; i++) { dest[destOff + i] = src[srcOff + i] }`. Avoid `Arrays.copyOf`, `ByteArrayOutputStream`, `java.nio` bulk-copy APIs.



### Daikin v0.1.2 hotfix — HubAction(Map, Protocol) also fails; switch to asynchttpGet

- **HubAction 2-arg Map form also invalid:** v0.1.1 tried HubAction(Map, Protocol). Also fails on Mads's firmware with 'Could not find matching constructor'. Both 3-arg and 2-arg Map-based HubAction constructors are broken for HTTP use on current Hubitat firmware. Stop guessing at HubAction overloads.
- **asynchttpGet is the correct HTTP-over-LAN API:** Adopted in v0.1.2 (sendGet lines 409-422, drivers/daikin-wifi/daikin-wifi.groovy). Works for any HTTP URL (LAN or cloud), firmware 2.2+. Pattern: asynchttpGet(callbackMethod, [uri: "http://ip/path", timeout: 10, contentType: "text/plain"], [path: path])
- **Callback signature:** def handler(hubitat.scheduling.AsyncResponse response, Map data) — body via response.getData(), error check via response.hasError(). No parseLanMessage, no DNI required.
- **Correction to team memo:** Trinity's v0.1.0 memo claimed asynchttpGet was cloud-only. Incorrect — works for LAN HTTP too. Corrected in .squad/decisions/inbox/tank-daikin-wifi-v012-asynchttp.md.
- **Skill:** .squad/skills/hubitat-asynchttpget-pattern/SKILL.md (new); .squad/skills/hubitat-hubaction-constructors/SKILL.md (updated to mark all Map-based forms unreliable, confidence medium).

### Daikin v0.1.3 — setSwingMode command + swingMode attribute (665e968)

- **Daikin BRP069B f_dir mapping:** f_dir=0→"off" (fixed position), f_dir=1→"vertical" (up/down swing), f_dir=2→"horizontal" (left/right swing), f_dir=3→"3d" (combined both axes). "3d" is Daikin's term for the combined mode.
- **Trinity's fanDirection memo was stale:** The v0.1.2 driver did NOT parse f_dir at all and had no fanDirection attribute. f_dir parsing (→ swingMode attribute) was added as part of v0.1.3 — it was a genuine gap, not just a write-side omission.
- **set_control_info requires all 6 params:** pow, mode, stemp, f_rate, f_dir, shum must all be present on every set_control_info call. The existing sendControlWrite(Map overrides) helper covers this pattern — reads current device attribute values as defaults and the caller supplies only the fields to change. Used for setSwingMode exactly as for setFanRate and setThermostatMode. No new helper needed.
- **sendControlWrite f_dir default:** Changed from hardcoded "0" to SWING_TO_DAIKIN_F_DIR[device.currentValue("swingMode")] ?: "0" — preserves current swing setting across all other control writes (setThermostatMode, setFanRate, setpoint changes, etc.).
- **Extension note (v0.1.4 econo/powerful):** Econo and powerful modes use a separate `set_special_mode` endpoint (not set_control_info), so a dedicated sendSpecialModeWrite helper will be needed. The set_control_info 6-param pattern does not apply there.


---

## 2026-05-18 — v0.1.4 Learnings

### Daikin get_special_mode / set_special_mode endpoint (adv field bitmap)
- **get_special_mode response:** et=OK,adv=2-fff10000,... — dv field carries special mode flags.
- **Community-documented BRP069B4x values:** dv="" or dv=0 = neither mode active; dv=2 = econo (energy-saving); dv=12 = powerful (boost).
- **Compound adv strings:** Some firmware returns compound strings like "2-fff10000". Safest parse: split on - and take the leading numeric token.
- **set_special_mode params:** ?set_spmode=1&spmode_kind=2 (enable econo), ?set_spmode=1&spmode_kind=12 (enable powerful), ?set_spmode=0&spmode_kind=<kind> (disable). The spmode_kind value must match what was enabled.
- **⚠️ UNVERIFIED on Mads's hardware** — exact adv values and compound-string format need real-hardware confirmation in v0.1.5 if behaviour differs.

### get_model_info field names + state.modelInfo caching pattern
- **URL:** GET /aircon/get_model_info — call once in initialize() (fire-and-forget).
- **Community-documented BRP069B4x fields:** model (or n_model), ev (or n_ver), n_hum (1=humidity sensor present), swing_l (1=horizontal swing), swing_v (1=vertical swing).
- **Caching pattern:** Store parsed fields in state.modelInfo Map: {name, firmware, hasHumiditySensor, supportsSwing, supportsSwingH, supportsSwingV}.
- **Error handling:** If get_model_info returns error or non-OK ret, log.warn and continue — driver functions without the cache.
- **No functional gating in v0.1.4** — cached for diagnostics + future use only.
- **⚠️ UNVERIFIED** — exact field names on Mads's firmware revision need hardware confirmation.

### Event hygiene audit checklist
- Check: all sendEvent calls in parse handlers should go through mitIfChanged() — not raw sendEvent.
- Check: mitIfChanged signature includes descriptionText on every call.
- Check: lastActivity emitted through mitLastActivity() with throttle guard (not raw sendEvent in hot path).
- Check: no displayed: false (SmartThings-era noise, Hubitat ignores it).
- Check: no isStateChange: true unless intentionally re-emitting unchanged values (which breaks hygiene).
- v0.1.4 audit result: driver was already clean on all five checks. No fixes needed.

### v0.1.0+ roadmap from Trinity's memo — NOW FULLY SHIPPED
- Trinity's original capability gap list (econo/powerful mode, get_model_info, event hygiene) is fully implemented as of v0.1.4.
- v0.1.3 shipped swing mode (setSwingMode + swingMode attribute).
- v0.1.4 shipped the remaining three: setSpecialMode, get_model_info cache, hygiene audit.
- Future work: on-device timer (deferred — use Hubitat rules), parent/child multi-unit (deferred — Mads has one unit), EZ Dashboard JSON_OBJECT attributes (deferred, was v0.1.2 candidate per Cypher).
