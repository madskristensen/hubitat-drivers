# Decision: Spock Unit Test POC — Touchstone Pure Helpers
**Date:** 2026-05-17  
**Author:** Tank  
**Status:** POC complete, pending Mads review

---

## Summary

Mads asked whether Hubitat driver `.groovy` files can be unit tested. Answer: **pure functions yes, sandbox lifecycle code no.** This decision documents the chosen approach, trade-offs, and recommended expansion path.

---

## Context

Hubitat drivers run inside a sandboxed Groovy interpreter that:
- Blocks reflection (`getClass`, `metaClass`, `Class.forName`)
- Blocks `java.util.zip.CRC32` and other JDK I/O classes
- Injects globals (`log`, `device`, `state`, `settings`, `sendEvent`, `runIn`, `schedule`, `interfaces.rawSocket`)
- Exposes a custom `metadata { definition { } preferences { } }` DSL

None of those globals exist in a standard JVM test harness, which makes testing lifecycle methods (`installed`, `parse`, `socketStatus`) effectively impossible without a full sandbox emulator.

However, the *pure functions* — CRC32 computation, Tuya frame building/parsing, AES encrypt/decrypt, label↔DP maps, temperature conversions — have no dependencies on sandbox globals. These are testable.

---

## Chosen Strategy: Strategy A (Extracted Helpers)

**What we did:** Copied the pure helper functions from `touchstone-fireplace.groovy` into `tests/src/test/groovy/touchstone/TouchstoneHelpers.groovy`. Added a source citation header. Tests reference the copied class.

**Result:** 36 tests, all passing, covering:
- CRC32 table + computation (validated against `java.util.zip.CRC32`)
- Tuya frame builder — outgoing frame structure, prefix/suffix/cmd/CRC correctness
- Heartbeat frame — empty payload, correct 24-byte size
- Full build→parse→decrypt round-trip for CONTROL frames
- All FLAME_COLOR, CHARCOAL_COLOR, FLAME_BRIGHTNESS, FLAME_SPEED label↔DP maps

**What Strategy A proves:** The pure logic in the driver is correct and testable. The pattern is reusable.

**What Strategy A does NOT prove:** That the actual driver file on disk matches this logic (it's a copy). If `touchstone-fireplace.groovy` is changed without updating the helpers file, tests will pass on stale code.

---

## Trade-offs Mads Should Know

| | Strategy A (chosen) | Strategy B (future) |
|---|---|---|
| Setup effort | Low — 1 helper class | Medium — GroovyShell + Binding setup |
| Tests against | Copied helpers | Actual driver file |
| Fragility | Manual sync required | Tightly coupled to driver |
| Metadata/DSL stripping | Not needed | Must strip or mock `metadata {}` |
| `@Field static final` handling | N/A | Needs shell script pre-processing |
| Value for CI | High for logic verification | Higher for regression detection |

**Recommendation:** Accept Strategy A for now. Ship it as a safety net for the label maps and frame logic. Schedule Strategy B as a follow-up once the team agrees on the scope of the test suite.

---

## Frame Format Note (Important for Strategy B)

The Tuya protocol uses two slightly different frame layouts:
- **Outgoing** (built by `buildTuyaFrame`): `prefix + seqNo + cmd + length + payload + CRC + suffix` — 24 bytes minimum
- **Incoming** (received from device, parsed by `processFrame`): `prefix + seqNo + cmd + length + retcode + payload + CRC + suffix` — 28 bytes minimum

Strategy A's `parseTuyaFrameRaw` only handles outgoing frames. Testing incoming frame parsing requires either a real device response capture or a mock that includes the retcode field.

---

## Recommended Expansion Path

### Phase 2: Strategy B scaffold (1–2 days)
- Build a `DriverTestShell` utility: reads the `.groovy` file, strips `metadata { }` with a regex, injects mock globals (`log`, `device`, `state`, `sendEvent`) via `GroovyShell(Binding)`
- Port the Strategy A tests to run against the actual `touchstone-fireplace.groovy`
- Add regression tests for `applyDps` (requires mock `device.currentValue`)

### Phase 3: Gemstone
- Extract ABGR color math + controllerName matching
- Test effect catalog reverse lookups (patternId → name, patternId → index)

### Phase 4: SunStat
- Extract boost expiry math with injectable `now()` parameter
- Test clamp + temperature conversion helpers
- Token expiry timestamp comparisons

---

## Files Created

- `tests/build.gradle` — Groovy + Spock + JUnit5
- `tests/settings.gradle`
- `tests/gradle/wrapper/` + `tests/gradlew` + `tests/gradlew.bat` — self-contained wrapper
- `tests/.gitignore`
- `tests/README.md`
- `tests/src/test/groovy/touchstone/TouchstoneHelpers.groovy`
- `tests/src/test/groovy/touchstone/TouchstoneHelpersSpec.groovy`
