# Switch — QA / Testing Engineer (Archive)

Earlier work on Touchstone v0.1.0-v0.1.4, Bosch Home Connect scoping, real-device validation workflow. Summarized entries kept in main history.md.

## Key Learnings (Pre-2026-05-17T15:50)

1. **Tuya Local Device Testing Patterns:**
   - Single TCP connection hard constraint (close Smart Life app pre-test)
   - Smoke vs full validation distinction
   - Known device quirks (temp revert on power cycle, remote-only features)
   - Recovery testing critical (network drop, power loss, app collision)
   - Enum validation prevents silent failures
   - Polling observation (20–30s typical interval)
   - DP schema vs empirical mapping distinction

2. **Touchstone Real-Device Test Plan (v0.1.0):**
   - 20-test suite covering pre-flight, happy path, state sync, recovery, edge cases, stability
   - Smoke test: 30 min (tests 1–9)
   - Full validation: 3+ hours
   - Enum label confirmation responsibility (after Mads runs tests, report observed values to Link)

3. **v0.1.4 Release Awareness:**
   - Power-on defaults apply ~1.5s after on()
   - Heater never auto-toggles (intentional design)
   - Sandbox reflection errors fixed (v0.1.3 intermediate state, never released)

4. **TESTING.md Pattern for Tuya/LAN Drivers (2026-05-17T15:50):**
   - Pre-flight checklist mandatory (close app, ping device, verify key, confirm power)
   - Section order: Pre-flight → Lifecycle → Happy-path → Power-on defaults → Settings edge cases → Recovery → Discovery → Parsed-not-commanded DPs → Validation summary
   - Explicit [verify on hardware] flags for unconfirmed behavior
   - Tank coordination flags for concurrent changes
   - 33 tests across 9 areas reusable pattern

See main history.md for current session work.
