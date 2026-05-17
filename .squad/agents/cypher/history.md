# Cypher — Integration / Protocol Engineer

Recent contributions documented in history-archive.md. Current session: SunStat v0.1.4 API diagnosis.

- 2026-05-17T04:24:00Z: SunStat v0.1.4 — diagnosed API envelope unwrap bug, shipped bootstrap script
- 2026-05-16: SunStat Connect Plus API research complete (Azure AD B2C, token refresh documented)

## 2026-05-17 (session cypher-3) — Gemstone setColor + setColorTemperature Both Broken

**Prior framing was wrong.** cypher-2 assumed setColorTemperature was a working baseline. Mads confirmed: neither setColor nor setColorTemperature works. This entry supersedes that framing.

### CT Silent-Fail Mode (Confirmed)

The silent-fail site is `executeOrQueueRequest` lines 712-716:
```groovy
if (!hasUsableAccessToken() || (request?.requiresDevice && !state.deviceId)) {
    queueRequest(request)   // NO LOGS — completely silent
    continueSessionSetup()
    return
}
```
CT hits this path when called before device discovery completes (`state.deviceId` null) or during the 5-minute token leeway window. The request enters `state.pendingRequests` silently. `continueSessionSetup()` no-ops if `state.authInFlight = true`. No error, no info, no debug emitted.

**This is NOT an effectCatalogStale/effectCatalogMissing gate** — those only affect `setEffect()` paths. CT bypasses them entirely.

### setColor 400 Root Cause (High Confidence)

Three candidate causes, all in `buildColorRequest` and `buildColorTemperatureRequest`:

1. **`pattern.id = generatePatternId()` produces non-UUID** — `"hubitat-{timestamp}-{n}"`. Gemstone API uses UUID-format pattern IDs (read-modify-write protocol). `buildLevelRequest` does NOT override `id` and presumably works. Removing the id override from color/CT builders is the primary fix.

2. **Colors use alpha=0 (`0x00RRGGBB`)** — `hubitatHueSatToArgb` and `kelvinToArgb` both omit the alpha byte. Gemstone API appears to use `0xFFRRGGBB` (alpha=255=opaque). Fix: OR with `(0xFF << 24)` in both functions.

3. **`referencePatternId = null` explicit null** — May need to be absent rather than null. Fix: `pattern.remove("referencePatternId")`.

### has-gemstone / pygemstone Reference Status

Neither `has-gemstone` (HA integration) nor pygemstone (sslivins) is available locally. The `.squad/research/` directory contains only encrypted ELAN/Control4 binaries. Pattern payload requirements inferred from code analysis and prior research ("read-modify-write required" note in history-summary-2026-05-16).

**Capture-and-respond path**: Ship Fix 1 (surface `response.getErrorData()` in 400 handler) in v0.4.2 first. The actual API error message will confirm which of the three candidates is the primary cause.

### Deliverable

Full report at: `.squad/decisions/inbox/cypher-gemstone-color-ct-both-broken.md`

## Learnings

### 2026-05-16T22:27:55-07:00 — Gemstone setColor vs setColorTemperature payload diff (HTTP 400 investigation)

**Finding:** `buildColorRequest` and `buildColorTemperatureRequest` produce **structurally identical JSON payloads** for `/deviceControl/play/pattern`. Both paths use `currentOrDefaultPattern()` as seed and explicitly set the same 6 fields (`id`, `name`, `animation`, `colors`, `brightness`, `referencePatternId`). The remaining 3 fields (`speed`, `direction`, `backgroundColor`) come from `currentOrDefaultPattern()` defaults. No code-level defect found that would cause one to 400 while the other succeeds.

**Key numbers for `setColor(hue:68, sat:69, level:43)`:**
- `hubitatHueSatToArgb(68, 69)` = **6,115,327** (0x005D4FFF, valid positive Integer, hue=244.8°)
- `levelToWireBrightness(43)` = **110** (valid 0–255 range)

**Root cause — UNCONFIRMED:** The 400 response body is not captured by Hubitat's async callback (`response.getData()` returns null/empty for 4xx). All structural hypotheses remain speculative without the actual error message.

**Most likely diagnostic path:** Tank to add `debugLog "setColor pattern body: ${groovy.json.JsonOutput.toJson(pattern)}"` before the PUT + improve 400 body capture in `apiResponseCallback`. See `.squad/decisions/inbox/cypher-gemstone-setcolor-400-fix.md` for full spec.

**pygemstone wire format confirmed:** The 9 canonical fields for PUT /deviceControl/play/pattern are: `id`, `name`, `colors` (list of ARGB ints), `animation`, `brightness`, `speed`, `direction`, `backgroundColor`, `referencePatternId`. Source: `sslivins/pygemstone/src/pygemstone/models.py` `Pattern.to_api()`.

**Potential stale-field risk:** `currentOrDefaultPattern()` does NOT strip unknown fields when `state.lastPattern` was set by `activateEffectByPattern` (cloud effect). Extra fields like `ownerId`/`folderId`/`createdAt` may survive into the PUT body. Fix: whitelist to the 9 canonical fields before overriding in both `buildColorRequest` and `buildColorTemperatureRequest`.
