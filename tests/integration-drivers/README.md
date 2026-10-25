# Integration-driver offline regressions

From the repository root, with Java 8+ and Groovy 2.4.21 available:

```powershell
java -cp "$env:TEMP\hubitat-driver-tests\groovy-all-2.4.21.jar" groovy.ui.GroovyMain tests\integration-drivers\IntegrationDriversTest.groovy
```

Adjust the JAR location if needed. The argument-free `*Test.groovy` entry also supports recursive test-runner discovery. No dependencies are downloaded by the test.

The self-contained fixture compiles and loads all four actual Gemstone, Touchstone and SunStat production scripts. Hubitat events, scheduling, synchronous/asynchronous HTTP and raw sockets are replaced with recording stubs; callbacks and timers are driven deterministically. Touchstone frames use production encryption/CRC helpers. Assertions check commands, payloads, saved state, response correlation and recovery, not source-text patterns. Any failed assertion exits nonzero.

This is offline Groovy behavioral coverage, not Hubitat sandbox certification or a physical-device integration test. Metadata closures are skipped; scheduler timing, live cloud envelopes and firmware sequence echo still require device validation. No device/network calls are made.