# Hubitat Driver Unit Tests — Spock POC

> **Status:** Proof-of-concept — pure helpers only. Lifecycle code (sandbox) is not tested here.

## Quick Start

```
cd tests/
./gradlew test          # macOS / Linux
gradlew.bat test        # Windows
```

**Requirements:** JDK or JRE 8+ on PATH (or set `JAVA_HOME`).  
The Gradle wrapper auto-downloads Gradle 7.6.4 on first run. No Gradle install needed.

---

## What's Covered

| Test group | # tests | What it proves |
|---|---|---|
| CRC32 table + computation | 3 | Lookup table is correct; crc32() matches `java.util.zip.CRC32` |
| `intToBytes` / `readUInt32` round-trip | 5 | Byte-serialisation of Tuya frame header fields |
| `buildTuyaFrame` structure | 2 | Prefix, suffix, cmd, CRC are all correct in built frames |
| Build → parse round-trip + AES decrypt | 1 | Full frame build + decrypt restores original JSON payload |
| `FLAME_COLOR` label↔DP maps | 2 | All 6 colours round-trip; unknown labels return null |
| `CHARCOAL_COLOR` label↔DP maps | 14 | All 12 colours individually verified + full round-trip |
| `FLAME_BRIGHTNESS` label↔DP maps | 1 | All 5 brightness levels round-trip |
| `FLAME_SPEED` label↔DP maps | 1 | Slow/Medium/Fast round-trip |
| Temperature conversion | 8 | `celsiusToFahrenheit` / `fahrenheitToCelsius` spot checks |

**Total: 36 tests, all passing.**

---

## What Is NOT Covered (By Design)

These require the Hubitat sandbox and cannot run in a standard JVM:

- `installed()`, `updated()`, `initialize()` — lifecycle methods that call `device.updateSetting`, `runIn`, etc.
- `parse()`, `socketStatus()` — raw socket callbacks calling `hubitat.helper.HexUtils` + `interfaces.rawSocket`
- `sendEvent()`, `log`, `device`, `state`, `settings` — all Hubitat-provided globals
- Network framing of *incoming* device responses (has a `retcode` field not present in outgoing frames — see note in `TouchstoneHelpers.parseTuyaFrameRaw()`)

These are covered by Switch's real-hardware tests on live Touchstone hardware.

---

## Strategy A vs B

**Strategy A (chosen):** Extract copies of pure helpers into `TouchstoneHelpers.groovy`. Tests reference the copy.

- **Pro:** Zero mocking gymnastics. Tests run fast, results are unambiguous.
- **Con:** Code duplication. If the driver changes the CRC32, frame builder, or label maps, `TouchstoneHelpers.groovy` must be updated in sync. The header comment at the top of the helper file makes this explicit.

**Strategy B (future):** Load the actual `.groovy` driver file into a `GroovyShell` with mocked globals (`log`, `sendEvent`, `state`, etc.) injected via `Binding`.

- **Pro:** Tests run against the real driver code — no duplication.
- **Con:** Harder to set up. `@Field static final` declarations need special handling. The `metadata { }` DSL block must be stripped or mocked before evaluation.
- **Recommended next step** once the POC is accepted. Start with the frame builder + CRC paths, then progressively add `applyDps` and command handlers.

---

## Extending to Other Drivers

### Gemstone (LED controller)

Pure helpers to extract and test:
- `abgrToHex(r, g, b)` / `hexToAbgr(hex)` — ABGR byte-order conversions
- `controllerName` matching logic (pattern matching)
- Effect catalog lookup (patternId → name, patternId → index reverse maps)

Strategy B would be more attractive here because the ABGR math is tightly coupled to effect-activation commands.

### SunStat (cloud thermostat)

Pure helpers to test:
- Boost expiry math: `boostUntil` timestamp arithmetic
- `clampSetpoint`, temperature unit conversions
- Token expiry checking (timestamp comparison)
- `asynchttpGet` callback routing (partial — can mock the response object)

Boost expiry math is especially valuable to test because it involves `now()` which can be injected as a parameter in a Strategy A extract.

---

## Directory Layout

```
tests/
├── build.gradle                         — Groovy + Spock + JUnit5 platform
├── settings.gradle
├── gradle/wrapper/
│   ├── gradle-wrapper.jar               — Gradle bootstrapper (binary)
│   └── gradle-wrapper.properties        — Points to Gradle 7.6.4 download
├── gradlew                              — Unix wrapper script
├── gradlew.bat                          — Windows wrapper script
├── .gitignore                           — Excludes build/, .gradle/, .gradle-dist/
├── README.md                            — This file
└── src/test/groovy/touchstone/
    ├── TouchstoneHelpers.groovy         — Pure helpers (Strategy A copy)
    └── TouchstoneHelpersSpec.groovy     — Spock specification (36 tests)
```
