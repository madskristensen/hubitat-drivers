# SKILL: Spock Unit Testing for Hubitat Groovy Drivers
**Created:** 2026-05-17  
**Author:** Tank

---

## When to Use This Skill

Use this skill when you need to unit test Hubitat driver `.groovy` files outside the sandbox. Applicable to any driver that has pure-function helpers (frame builders, CRC computations, label↔DP maps, math helpers, temperature conversions, etc.).

---

## Sandbox Reality Check (Read This First)

Hubitat drivers run inside a restricted Groovy interpreter. This sandbox:
- **Blocks** reflection (`getClass`, `metaClass`, `Class.forName`)
- **Blocks** `java.util.zip.CRC32` and many JDK I/O classes
- **Injects** platform globals: `log`, `device`, `state`, `settings`, `sendEvent`, `runIn`, `schedule`, `interfaces.rawSocket`, `httpGet`, `parent`
- **Exposes** a `metadata { definition { } preferences { } }` DSL not available in standard Groovy

**Consequence:** Tests run in a standard JVM **do not verify sandbox-equivalence**. They verify logical correctness of pure functions only. Sandbox-equivalence is verified by Switch's real-hardware tests.

**What IS testable:** Any function whose body has no calls to sandbox globals. Typically:
- CRC/checksum computations
- Frame builders and parsers
- AES encrypt/decrypt wrappers
- Label↔DP maps and round-trips
- Temperature conversions, value clamping
- Type coercion helpers (`safeInt`, `safeLong`, `safeStr`, `asBoolean`, `coerceRawValue`)
- Any pure math / string manipulation

**What is NOT testable:** `installed()`, `updated()`, `initialize()`, `parse()`, `socketStatus()`, any method calling `sendEvent`, `runIn`, `schedule`, `interfaces.*`, `log.*`, `device.*`, `state.*`, `settings.*`.

---

## Directory Layout

```
tests/                                   ← confined here; no Gradle at repo root
├── build.gradle
├── settings.gradle
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── .gitignore                           ← excludes build/, .gradle/, .gradle-dist/
├── README.md
└── src/test/groovy/<driver>/
    ├── <Driver>Helpers.groovy           ← Strategy A: extracted pure helpers
    └── <Driver>HelpersSpec.groovy       ← Spock specification
```

---

## Gradle Config (build.gradle)

```groovy
plugins {
    id 'groovy'
}
repositories {
    mavenCentral()
}
dependencies {
    implementation 'org.codehaus.groovy:groovy:3.0.21'
    testImplementation 'org.spockframework:spock-core:2.3-groovy-3.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.2'
}
test {
    useJUnitPlatform()
}
```

**Version choices:**
- Groovy 3.0.x — stable on Java 8 / 11 / 17
- Spock 2.3 — requires JUnit Platform; use `spock-core:2.3-groovy-3.0`
- Gradle 7.6.4 — last 7.x, supports Java 8+ runtime

**Generate wrapper:** `gradle wrapper --gradle-version 7.6.4` (requires system Gradle once, then wrapper is self-contained)

---

## Strategy A: Extracted Helpers (Recommended for POC)

1. Create `tests/src/test/groovy/<driver>/<Driver>Helpers.groovy`
2. Copy (do NOT import) the pure functions from the driver
3. Add header comment:
   ```
   // source: drivers/<driver>/<driver>.groovy@vX.Y.Z
   // If the driver changes these helpers, update this file in sync.
   // TODO: factor out properly once the POC is accepted.
   ```
4. Write Spock spec in `<Driver>HelpersSpec.groovy`

**Pros:** Simple. No mocking. Tests run in ~1s.  
**Cons:** Manual sync required when driver changes.

---

## Strategy B: GroovyShell with Mocked Globals (Next Step)

```groovy
class DriverTestShell {
    static Script loadDriver(String driverPath) {
        String source = new File(driverPath).text
        // Strip metadata block (Hubitat DSL, not valid in standard Groovy)
        source = source.replaceAll(/(?s)metadata\s*\{.*?\n\}/, '')
        Binding binding = new Binding()
        binding.setVariable('log',       new MockLog())
        binding.setVariable('device',    new MockDevice())
        binding.setVariable('state',     [:])
        binding.setVariable('settings',  [:])
        binding.setVariable('sendEvent', { Map e -> /* capture */ })
        // Add runIn, schedule, etc. as no-ops
        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(source)
    }
}
```

**Known issues:**
- `@Field static final` declarations compile at the script class level — access via `script.class.getDeclaredField(...)` or reflection after loading
- The `metadata { }` regex strip must handle nested braces correctly
- If the driver imports sandbox-blocked classes, the import must be removed before parsing

**When to use:** Once Strategy A is accepted and you want regression tests that run against the actual driver file on disk.

---

## Tuya Frame Format (for Frame Tests)

**Outgoing** (built by driver, sent to device):
```
prefix(4) + seqNo(4) + cmd(4) + length(4) + encPayload(N) + CRC(4) + suffix(4)
```
Minimum = 24 bytes. `length` field = `N + 8` (covers CRC + suffix).

**Incoming** (received from device, parsed by `processFrame`):
```
prefix(4) + seqNo(4) + cmd(4) + length(4) + retcode(4) + payload(N) + CRC(4) + suffix(4)
```
Minimum = 28 bytes. `processFrame` uses `frame.length - 28` for payload length.

**CRC** covers everything except the last 8 bytes (CRC field + suffix).

**Version header** (prepended for CONTROL/STATUS frames, not DP_QUERY/HEARTBEAT):
- `"3.3"` (3 bytes ASCII) + 12 zero bytes = 15 bytes total

---

## Running the Tests

```bash
# From the tests/ directory:
./gradlew test              # first run downloads Gradle 7.6.4 (~117 MB)
./gradlew test --rerun-tasks   # force rerun even if up-to-date
```

HTML report: `tests/build/reports/tests/test/index.html`

---

## Extending to Other Drivers

| Driver | Pure helpers to test | Notes |
|---|---|---|
| Gemstone | ABGR color math, effect catalog lookups, controllerName match | ABGR byte order is hardware-verified — test both directions |
| SunStat | Boost expiry math, clampSetpoint, token expiry checks | Inject `now()` as parameter to avoid `System.currentTimeMillis()` coupling |
| Any future | CRC, frame framing, label maps, conversions | Follow Strategy A first, migrate to B if duplication becomes painful |
