# Manual Test Plan — SunStat Connect Plus Driver

**Drivers:** `sunstat-thermostat-parent.groovy` + `sunstat-thermostat-child.groovy` (v0.1.3 — setRefreshToken command replaces password preference)  
**Test Target:** SunStat Connect Plus electric floor heating thermostat  
**Cloud Control:** Watts iOS app  
**Platform:** Hubitat C-7 / C-8  
**Created:** 2026-05-16T20:01:41-07:00  
**Status:** DRAFT — v0.1.0 core tests finalized; v0.1.1 home/away tests added 2026-05-16; v0.1.2 energy/schedule/hold/outdoor/precision/bounds tests added 2026-05-16; v0.1.3 setRefreshToken command tests added 2026-05-16T21:07:23-07:00; v0.1.4 API envelope unwrap + URL encoding tests added 2026-05-16T21:24:48-07:00

---

## Prerequisites

Before running any test:

- **Hubitat hub** has internet connectivity (cloud driver requires outbound HTTPS)
- **Watts app** on iOS is already working with the same SunStat account you plan to configure in Hubitat
- **Driver code** — both `sunstat-thermostat-parent.groovy` AND `sunstat-thermostat-child.groovy` are saved in the Hubitat hub web UI → Drivers Code (install the child driver FIRST so the parent can create child devices)
- **Virtual device** (or Hubitat-managed child device) exists and uses type **SunStat Thermostat**
- **Logs** page is open in a second browser tab for all tests
- **Device page** is open in the main browser tab
- **SunStat thermostat** is powered on and physically connected to WiFi and the heating system

**Important cloud behavior:** Cloud response latency is typical 2–10 seconds. The driver may use optimistic Hubitat events for some commands (e.g., setpoint change), then poll to reconcile cloud state. Verify both the Hubitat tile and the Watts app reflect the change.

---

## Test Area: Lifecycle & Authentication

### Test 1: Install and Initial Configuration

**What:** Verify driver installation, device creation, and credentials setup.

**Steps:**

1. Create a new virtual device in Hubitat and select type **SunStat Thermostat** [needs Trinity profile].
2. Open the device page and scroll to **Preferences**.
3. Verify these preferences exist [needs Trinity profile]:
   - **SunStat account email** (or username)
   - **SunStat account password**
   - **Device selection** (if the account has multiple thermostats)
   - **Polling interval** (suggested: 5 minutes)
   - **HTTPS request timeout** (suggested: 30 seconds)
   - **Enable debug logging** (checkbox)
4. Enter the same email and password that work in the official Watts app.
5. If the account has multiple SunStat devices, select the correct thermostat by name or serial number.
6. Leave other settings at defaults.
7. Click **Save Preferences**.
8. Watch both the device page and the logs for 15–30 seconds.

**Expected:**

- `authStatus` attribute changes from **Authenticating** to **Authenticated: <thermostat name>** (or equivalent success indicator) [needs Trinity profile]
- No stack traces, `MissingMethodException`, `NullPointerException`, or `ClassCastException` in logs
- The driver logs do not expose the email, password, or any authentication tokens
- Clicking **Refresh** after auth succeeds populates `thermostatMode`, `heatingSetpoint`, `currentTemperature`, `thermostatOperatingState`, and other core attributes [needs Trinity profile]
- `currentTemperature` shows a numeric room temperature (in °F or °C depending on configuration) [needs Cypher spec for units]
- `floorTemperature` (if exposed) shows a different numeric value (sensor under the tile) [needs Trinity profile]

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 2: Authentication Failure Handling

**What:** Verify bad credentials fail cleanly and show a helpful error.

**Steps:**

1. In Preferences, replace the password with an incorrect one.
2. Click **Save Preferences**.
3. Watch `authStatus` and the logs for 10 seconds.
4. Restore the correct password and click **Save Preferences** again.
5. Verify the device recovers.

**Expected:**

- `authStatus` changes to a clear error message (e.g., **Auth failed — check email/password**) [needs Trinity profile]
- A helpful log entry appears with the HTTP status code and error reason (without exposing credentials)
- The device does not crash or hang; it remains in a degraded but stable state
- After restoring the correct password, `authStatus` returns to **Authenticated: <thermostat name>**

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 3: Updated / Preferences Change

**What:** Verify the driver reinitializes cleanly when preferences are modified.

**Steps:**

1. Ensure the device is authenticated and working.
2. Change the **polling interval** (e.g., from 5 minutes to 1 minute).
3. Click **Save Preferences**.
4. Watch the logs for re-initialization messages.
5. Verify the device continues to poll at the new interval.

**Expected:**

- The logs show a clear message indicating preferences were saved and polling was re-registered (or equivalent lifecycle event) [needs Trinity profile]
- No stack traces or orphaned schedules
- `refresh()` or polling still works at the new interval
- Core attributes remain populated

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 4: Device Uninstall / Cleanup

**What:** Verify the driver deregisters all schedules and cleans up on removal.

**Steps:**

1. Ensure the device is working.
2. From the Hubitat device page, click the **Delete** button at the bottom.
3. Confirm the deletion.
4. Watch the logs and verify the device no longer appears in the device list.
5. Optional: Restart the Hubitat hub and confirm the device does not reappear.

**Expected:**

- The logs show a clean `uninstalled()` message (or equivalent cleanup log) [needs Trinity profile]
- No orphaned schedules or background tasks remain (verified by hub stability and no phantom CPU usage)
- No error entries related to the deleted device in subsequent polls or commands

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Read State / Refresh

### Test 5: Refresh Current State

**What:** Verify `refresh()` pulls current setpoint, temperatures, and mode from the cloud.

**Steps:**

1. Ensure the device is authenticated.
2. On the device page, click **Refresh**.
3. Watch the Hubitat attributes and logs.
4. Compare the displayed values to the Watts app on your phone.

**Expected:**

- `thermostatMode` displays the current mode (e.g., **Off**, **Heat**, **Auto**) [needs Cypher spec for exact mode names]
- `heatingSetpoint` shows the target temperature (e.g., **72°F**) [needs Trinity profile]
- `currentTemperature` shows room air temperature (e.g., **68°F**)
- `floorTemperature` (if exposed) shows the under-tile floor temperature (e.g., **70°F**) [needs Trinity profile]
- `thermostatOperatingState` reflects whether the system is actively heating (**Heating**) or idle (**Idle**) [needs Cypher spec]
- All values match the Watts app display (within typical cloud lag of 2–10 seconds)
- No HTTP errors, parse errors, or stack traces appear in logs

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 6: Polling Cycle

**What:** Verify polling automatically keeps state in sync.

**Steps:**

1. Set the polling interval to **1 minute** via Preferences.
2. Note the current `thermostatMode` and `heatingSetpoint` in Hubitat.
3. Open the Watts app and change the setpoint (e.g., from 72°F to 75°F) and/or the mode.
4. Return to the Hubitat device page and wait for the next poll cycle (up to 1 minute).
5. Verify Hubitat updates without manual **Refresh**.

**Expected:**

- After 1 minute, `heatingSetpoint` and `thermostatMode` automatically update to reflect the Watts app change
- The device logs show poll/refresh activity at regular intervals (without spam)
- No user action required to see the change in Hubitat

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Setpoint Control

### Test 7: Set Heating Setpoint

**What:** Verify `setHeatingSetpoint()` command works end-to-end.

**Steps:**

1. Ensure the device is authenticated and `thermostatMode` is **Heat** or **Auto**.
2. Note the current `heatingSetpoint` (e.g., **72°F**).
3. On the device page, run the command **`setHeatingSetpoint(75)`** [needs Trinity profile for exact command name/signature].
4. Watch the Hubitat attribute, logs, and the Watts app.
5. Wait 2–10 seconds for cloud confirmation.
6. Try setting an out-of-range setpoint (e.g., **35°F** or **100°F**) and observe the response [needs Cypher spec for min/max].

**Expected:**

- `heatingSetpoint` updates immediately to **75°F** in Hubitat (optimistic update)
- The logs show a successful API call or HTTP `200` status [needs Cypher spec for API response shape]
- Within 2–10 seconds, the Watts app reflects the new setpoint
- The physical thermostat display (if visible) shows the new setpoint
- Out-of-range values are rejected with a clear log error (e.g., **Setpoint out of valid range: 50–95°F**) [needs Cypher spec for valid range]
- No stack traces or auth failures appear

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 8: Setpoint Edge Cases

**What:** Verify setpoint validation and boundary behavior.

**Steps:**

1. Try `setHeatingSetpoint(-10)` (well below min).
2. Try `setHeatingSetpoint(120)` (well above max).
3. Try `setHeatingSetpoint(72.5)` (decimal/precision test).
4. Try `setHeatingSetpoint(0)` (zero).

**Expected:**

- Out-of-range attempts log a clear validation error and do not send to the cloud [needs Cypher spec for valid range]
- Decimal values are either accepted and rounded to the nearest integer, or rejected with a clear message [needs Cypher spec for precision]
- Zero or negative values are rejected with a helpful message
- The current setpoint does not change after an invalid command
- No HTTP errors or stack traces appear

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Mode Control

### Test 9: Set Thermostat Mode

**What:** Verify mode transitions (Off, Heat, Auto) work and display correctly.

**Steps:**

1. Ensure the device is authenticated.
2. Set mode to **Heat** via `setThermostatMode("heat")` [needs Trinity profile for exact command name].
3. Watch `thermostatMode` and `thermostatOperatingState` in Hubitat and logs.
4. Set mode to **Off**.
5. Set mode to **Auto** (if supported) [needs Cypher spec for available modes].
6. Verify the Watts app reflects each change.

**Expected:**

- `thermostatMode` updates to the requested mode immediately (optimistic)
- `thermostatOperatingState` remains **Heating** if the floor temp is below setpoint and mode is **Heat**, or transitions to **Idle** if mode is **Off**
- The logs show successful API calls or HTTP `200` responses [needs Cypher spec]
- The physical thermostat display reflects the new mode
- After 1–2 poll cycles, Hubitat reconciles with cloud state and confirms the mode is stable
- No auth failures, HTTP errors, or stack traces appear

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 10: Mode Transition Correctness

**What:** Verify operating state updates correctly as floor temperature changes.

**Steps:**

1. Set mode to **Heat** and setpoint to **72°F**.
2. Wait for the system to begin heating (if floor temp is below 72°F).
3. Observe `thermostatOperatingState` — should be **Heating**.
4. Continue to monitor the logs and Hubitat for 10–20 minutes as the floor warms.
5. When floor temperature reaches or exceeds the setpoint, observe `thermostatOperatingState` transition to **Idle**.

**Expected:**

- `thermostatOperatingState` accurately reflects whether the system is heating (**Heating**) or idle (**Idle**)
- State transitions are clean and appear in logs without jitter (no rapid flipping between states)
- The physical thermostat element (if accessible) confirms heating activity matches the operating state
- No spurious error messages or retries appear during state transitions

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Schedule (If Exposed)

### Test 11: Schedule Readback

**What:** Verify the driver can read and display the programmed schedule [needs Trinity profile to confirm if schedule is exposed].

**Steps:**

1. Ensure the device is authenticated.
2. In the Watts app, confirm a programmed schedule exists (e.g., weekday 6 AM: 72°F, 9 PM: 68°F).
3. On the Hubitat device page, look for a **`schedule`** attribute or a **`getSchedule()`** command [needs Trinity profile].
4. If exposed, run the command or inspect the attribute.

**Expected:**

- The driver displays the schedule in a human-readable format (e.g., JSON or descriptive text) [needs Trinity profile for format]
- All program entries (day, time, setpoint) are correctly read from the cloud
- The schedule matches the Watts app configuration
- No parse errors or stack traces appear

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 12: Schedule Modification (If Supported)

**What:** Verify the driver can modify schedules [needs Trinity profile to confirm if write-capable].

**Steps:**

1. Ensure the device is authenticated.
2. If the driver exposes a `setSchedule()` command, attempt to update a single program entry (e.g., change Monday 6 AM from 72°F to 70°F) [needs Trinity profile].
3. Wait 5 seconds for the cloud to confirm.
4. Verify the Watts app reflects the change.

**Expected:**

- The command succeeds and logs a success message or HTTP `200` response [needs Cypher spec]
- The Watts app reflects the updated schedule within 5–10 seconds
- A subsequent `getSchedule()` or `refresh()` shows the new value
- No stack traces or validation errors appear

**Note:** [needs Trinity profile] — This test may be deferred if schedule modification is not supported in v1.0.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Boost / Hold (If Supported)

### Test 13: Boost Mode

**What:** Verify custom boost/hold command if exposed [needs Trinity profile].

**Steps:**

1. Ensure the device is authenticated and in **Heat** mode at setpoint 72°F.
2. Run a custom command like `setBoost(60)` to boost the setpoint +60 minutes (or equivalent) [needs Trinity profile for exact signature].
3. Observe `heatingSetpoint`, `thermostatMode`, and any **`boostActive`** or **`boostTimeRemaining`** attributes [needs Trinity profile].
4. Wait approximately 60 minutes (or manually trigger a refresh before then to verify the timer is decrementing).
5. Verify boost expires and the thermostat returns to the original setpoint and mode.

**Expected:**

- `heatingSetpoint` increases (e.g., setpoint + 3–5°F boost, or to a preset value) [needs Cypher spec for boost behavior]
- A **`boostActive`** attribute becomes `true` or a timer attribute shows remaining time [needs Trinity profile]
- The Watts app reflects the boost and timer
- After the timer expires, the thermostat automatically returns to the previous setpoint and mode
- No stack traces or auth failures appear

**Note:** [needs Trinity profile] — This test may be deferred if boost is not supported in v1.0.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 14: Hold Mode

**What:** Verify hold/indefinite mode if exposed [needs Trinity profile].

**Steps:**

1. Ensure the device is authenticated with a programmed schedule active.
2. Run a custom command like `setHold()` to hold the current setpoint indefinitely (suspending schedule) [needs Trinity profile].
3. Observe `thermostatMode` or a **`holdMode`** attribute [needs Trinity profile].
4. Verify the schedule does not override the hold.
5. Run `releaseHold()` to resume the schedule.

**Expected:**

- `thermostatMode` changes to **Hold** (or a similar indicator) [needs Cypher spec for mode names]
- The programmed schedule is suspended; setpoint does not change even if the next scheduled time arrives
- A **`holdActive`** attribute becomes `true` [needs Trinity profile]
- The Watts app reflects the hold state
- After `releaseHold()`, the schedule resumes and the next program entry takes effect
- No stack traces or parsing errors appear

**Note:** [needs Trinity profile] — This test may be deferred if hold is not supported in v1.0.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Home/Away (Location-Level)

**Overview:** v0.1.1 adds location-level home/away support. The parent device exposes `awayMode` (enum: home / away / unsupported / unknown) and `locationSupportsAway` (enum: true / false), along with commands `setHome()`, `setAway()`, and `setAwayMode(string)`. Child devices mirror the `awayMode` attribute. The driver polls the SunStat API's `GET /api/Location` endpoint each cycle to keep `awayMode` current and sends `PATCH /api/Location/{locationId}/State` with `{"awayState": 0|1}` to update.

---

### Test 15: Discover Captures Away Support [Tier 1]

**What:** Verify parent device discovers location away support during initial setup.

**Steps:**

1. Open the parent device page.
2. Click the **Discover Devices** or **Refresh** button (exact name depends on Trinity profile).
3. Wait 5–10 seconds for the driver to poll `GET /api/Location`.
4. Observe the parent device attributes in the Hubitat UI.

**Expected:**

- `locationSupportsAway` attribute appears and shows `"true"` (if Mads' location supports away) or `"false"` (if not)
- `awayMode` attribute appears and shows `"home"` or `"away"` matching the current state in the Watts app
- No auth failures or HTTP errors in logs

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 16: Set Away from Hubitat [Tier 1]

**What:** Verify parent can set the location to away mode.

**Steps:**

1. Ensure `awayMode = "home"` on the parent.
2. Call the `setAway()` command on the parent.
3. Observe the Hubitat logs and the parent device attributes immediately (within 1 second).
4. Open the Watts iOS app and verify the away state reflects the change within 5–10 seconds.
5. Wait one full poll cycle (≤ pollInterval) and verify child devices update their `awayMode` attribute.

**Expected:**

- Immediately after `setAway()`: parent emits an event `awayMode = "away"` (optimistic update)
- Logs show a successful HTTP `PATCH` call or equivalent success message
- Watts app reflects the away state within 5–10 seconds
- All child devices show `awayMode = "away"` on the next poll cycle
- No auth failures or HTTP errors appear

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 17: Set Home from Hubitat [Tier 1]

**What:** Verify parent can set the location back to home mode.

**Steps:**

1. Ensure `awayMode = "away"` on the parent (use Test 16 result or call `setAway()` first).
2. Call the `setHome()` command on the parent.
3. Observe the Hubitat logs and parent attributes immediately.
4. Open the Watts app and verify the home state reflects within 5–10 seconds.
5. Wait one poll cycle and verify child devices update.

**Expected:**

- Immediately after `setHome()`: parent emits an event `awayMode = "home"` (optimistic update)
- Logs show successful HTTP call
- Watts app reflects home state within 5–10 seconds
- All child devices show `awayMode = "home"` on next poll
- No errors

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 18: setAwayMode with Valid Argument [Tier 2]

**What:** Verify parent accepts `setAwayMode("home")` and `setAwayMode("away")` as alternatives to shortcut commands.

**Steps:**

1. Ensure `awayMode = "home"` on parent.
2. Call `setAwayMode("away")` on the parent.
3. Wait 2–3 seconds and verify `awayMode = "away"`.
4. Call `setAwayMode("home")` on the parent.
5. Wait 2–3 seconds and verify `awayMode = "home"`.

**Expected:**

- Both calls produce the same API behavior as `setAway()` and `setHome()`
- Logs show successful HTTP `PATCH` calls
- Attribute updates optimistically and reconciles after poll
- No errors or stack traces

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 19: setAwayMode with Invalid Argument [Tier 2]

**What:** Verify invalid `setAwayMode()` arguments are rejected gracefully.

**Steps:**

1. Call `setAwayMode("vacation")` on the parent.
2. Observe the logs and attribute state.
3. Verify no API call is made and attribute is unchanged.

**Expected:**

- Logs show a clear warning message (e.g., "Invalid awayMode value: vacation. Allowed: home, away")
- No HTTP `PATCH` call is sent to the SunStat API
- `awayMode` attribute does not change
- No stack trace or silent failure

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 20: Away Set from Watts App [Tier 3]

**What:** Verify parent detects away changes made in the Watts iOS app via polling.

**Steps:**

1. Ensure the driver is polling (verify pollInterval is active in logs).
2. Open the Watts iOS app on a phone or simulator.
3. Toggle the away state in the app (e.g., from Home to Away).
4. Wait for the next poll cycle (≤ pollInterval, typically 5 minutes).
5. Observe the parent's `awayMode` attribute in Hubitat.

**Expected:**

- Within one poll cycle, Hubitat detects the change and emits `awayMode = "away"` (or "home" if toggled back)
- No user action was required in Hubitat; the driver automatically reconciles via polling
- Logs show successful `GET /api/Location` call
- Child devices also reflect the new away state on their next poll

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 21: Unsupported Location [Tier 3]

**What:** Verify graceful handling when location does not support away mode.

**Steps:**

1. **Option A (if available):** Mads has access to another SunStat location with `supportsAway = false`. Add it as a second parent device in Hubitat.
2. **Option B (if not available):** Mark this test `[skip if not applicable]`.
3. If testing, call `setAway()` or `setHome()` on a parent for an unsupported location.
4. Observe the logs and attribute state.

**Expected:**

- `locationSupportsAway` shows `"false"`
- `awayMode` shows `"unsupported"` (or similar indicator)
- Calling `setAway()` / `setHome()` logs a clear warning (e.g., "Location does not support away mode")
- No HTTP `PATCH` call is sent to the API
- No stack trace or crash

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

**Note:** [skip if not applicable] — Mads may not have a location with `supportsAway = false`. This test can be deferred or skipped if only one location is available.

---

### Test 22: Child Mirror Lag [Tier 2]

**What:** Verify child devices update their `awayMode` on the next poll cycle, not instantly (within acceptable lag ≤ pollInterval).

**Steps:**

1. Ensure parent and child devices are paired and polling normally.
2. Note the current time and `awayMode` on a child device.
3. Call `setAway()` on the parent.
4. Check the child device immediately (within 1–2 seconds) — it should still show the old state.
5. Wait for the next poll cycle (typically 5 minutes; can be shortened by manually editing the pollInterval preference).
6. Observe the child's `awayMode` update to the new value.

**Expected:**

- Child does **not** update instantly; lag is present and ≤ pollInterval
- After the child's next poll, `awayMode` matches the parent
- Hubitat dashboard tile for child shows `awayMode = "away"` (or current state) correctly after update
- No errors or stale attribute display

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 23: Rule Machine Integration [Tier 3]

**What:** Verify Rule Machine can trigger based on `awayMode` changes and execute away-related commands.

**Steps:**

1. Open Hubitat Rule Machine and create a new rule.
2. Create a trigger: "If SunStat parent's `awayMode` changes to 'away', then execute an action."
3. Example action: send a notification or toggle a virtual switch.
4. Use the Watts app to toggle away mode (from Test 20) or call `setAway()` in Hubitat.
5. Verify the rule fires and the action executes.
6. Create a second rule: "If location mode changes to Away, then call `setAway()` on SunStat parent."
7. Toggle Hubitat's location mode from Home to Away and verify the parent receives `setAway()`.

**Expected:**

- Rule Machine recognizes the parent device and lists `awayMode` as a triggerable attribute
- Rules fire correctly without errors
- Notifications and downstream actions execute
- No stack traces in Hubitat logs related to Rule Machine or the SunStat device

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 24: Discovery on Unsupported Location [Tier 3]

**What:** Verify discovery correctly flags locations that do not support away mode.

**Steps:**

1. **Option A:** If Mads has a location with `supportsAway = false`, add it as a second device (see Test 21).
2. **Option B:** Simulate by directly editing the device state (advanced; may require IDE console). Set `state.locationSupportsAway = false` manually.
3. Click **Discover Devices** or **Refresh** on the parent.
4. Observe attributes.

**Expected:**

- `locationSupportsAway` attribute shows `"false"`
- `awayMode` attribute shows `"unsupported"` (or a similar indicator, not "home" or "away")
- Logs confirm the discovery parsed `supportsAway: false`

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

**Note:** [skip if not applicable] — Deferred if only one location is available.

---

### Test 25: Polling Picks Up Watts-App Changes [Tier 1]

**What:** Verify polling reconciliation detects away state changes made in the Watts app without any user action in Hubitat (regression test for Test 20).

**Steps:**

1. Ensure polling is active (logs show periodic `GET /api/Location` calls).
2. Ensure Hubitat's `awayMode` is currently `"home"` or known value.
3. Open Watts app and toggle away to a different state.
4. **Do not** interact with Hubitat; let polling run.
5. Wait exactly one poll interval (e.g., 5 minutes; logs will show the next poll call).
6. Observe Hubitat's `awayMode` attribute.

**Expected:**

- After the next poll, Hubitat's `awayMode` matches the Watts app's current state
- No command was required; polling auto-reconciles
- Logs show successful `GET /api/Location` call and state parsing
- Attribute updates correctly without errors

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: v0.1.2 Features (Energy, Schedule, Hold, Outdoor, Precision, Bounds)

**Overview:** v0.1.2 adds energy reporting, schedule enable/disable control, hold mode tracking, outdoor temperature sensing, setpoint precision rounding, and floor temperature bounds clamping. This section covers the six new feature groups with Tier classifications.

### Energy Reporting Tests [Tier 2 — Feature]

### Test 26: Energy Attribute Populated

**What:** Verify `energy` attribute populates after install and first refresh.

**Steps:**

1. Create a new virtual device and select **SunStat Thermostat**.
2. Enter credentials and save.
3. Click **Refresh** on the device page.
4. Watch the device attributes for the `energy` attribute.

**Expected:**

- After refresh, `energy` attribute displays a numeric value with unit = `"kWh"`
- Value is ≥ 0.0 (may be 0.0 if device has been idle)
- Attribute appears on the device page alongside other core attributes
- No parse errors or stack traces in logs

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 27: Daily Energy Array

**What:** Verify `energyYesterday` reflects the previous day's consumption from the API response.

**Steps:**

1. Ensure device is authenticated and polling normally.
2. Click **Refresh** and watch the logs for successful API response parsing.
3. Compare `energyYesterday` attribute value to the Watts app's daily breakdown (if available).
4. Perform another **Refresh** 24 hours later (or note the value for future comparison).

**Expected:**

- `energyYesterday` attribute shows a numeric value ≥ 0 (in kWh)
- Value represents yesterday's total consumption
- Value remains stable across polls (unless a new day starts)
- Matches Watts app's daily history view (within rounding tolerance)

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 28: Monthly Energy

**What:** Verify `energyMonth` reflects month-to-date kWh consumption.

**Steps:**

1. Ensure device is authenticated and polling normally.
2. Note the current month and the device's `energyMonth` value.
3. In the Watts app, navigate to the monthly view and note the month-to-date total.
4. Compare the two values.

**Expected:**

- `energyMonth` displays a numeric value ≥ 0.0 (in kWh)
- Value represents energy consumed from the start of the current month to now
- Matches Watts app's month-to-date display (within 0.5 kWh tolerance for rounding)
- Value increases as heating occurs and decreases are not expected mid-month

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 29: Last Month Energy

**What:** Verify `energyLastMonth` populates with previous month's total consumption.

**Steps:**

1. Ensure device is authenticated and polling normally.
2. Click **Refresh**.
3. Check the `energyLastMonth` attribute.

**Expected:**

- `energyLastMonth` displays a numeric value ≥ 0.0 (in kWh)
- Value represents the complete previous month's consumption
- Value remains constant (does not change) while current month progresses
- Value is consistent across multiple polls

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 30: Energy Missing from Response

**What:** Verify graceful handling if SunStat firmware doesn't return energy data.

**Steps:**

1. Enable debug logging.
2. Perform a **Refresh** command.
3. Watch logs for any energy-parsing attempts.
4. Check device page to confirm no errors are displayed.

**Expected:**

- If `data.Energy` field is missing from API response, driver logs debug message (e.g., "Energy data not available")
- Device does not crash, throw stack trace, or enter error state
- Energy attributes remain unchanged or show "N/A" (graceful degradation)
- Next poll continues normally

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

**Note:** [hard to reproduce without firmware swap] — This test may require a mock API or coordination with SunStat firmware team to simulate a missing Energy field.

---

### Test 31: Energy Decimal Rounding

**What:** Verify energy values are rounded to 2 decimal places.

**Steps:**

1. Ensure device is authenticated and polling normally.
2. Take a screenshot of the device page showing `energy`, `energyYesterday`, `energyMonth`, `energyLastMonth` attributes.
3. Check the Hubitat event history (device page → Event History) for any energy-related state changes.
4. Verify decimal precision in both the attribute display and the event log.

**Expected:**

- All energy values display exactly 2 decimal places (e.g., 12.34 kWh, not 12.3456 or 12)
- Event history entries also show 2 decimal precision
- No floating-point display errors (e.g., 12.340000000001)

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Schedule Control Tests [Tier 1 — Core]

### Test 32: setScheduleEnabled("off")

**What:** Verify calling `setScheduleEnabled("off")` disables the schedule.

**Steps:**

1. Ensure device is authenticated and `scheduleEnabled` is currently `"on"`.
2. Click **Refresh** to sync state.
3. On device page, run command **`setScheduleEnabled("off")`** [needs Trinity profile for exact command].
4. Watch the logs, device attributes, and Watts app for 10–15 seconds.
5. Note the `scheduleEnabled` attribute on the Hubitat device page.

**Expected:**

- Hubitat shows optimistic event: `scheduleEnabled = "off"` within 1–2 seconds
- Logs show successful API `PATCH` call (or similar)
- Within 2–10 seconds, Watts app shows schedule disabled
- After next polling cycle (≤ 5 min), `scheduleEnabled` attribute matches the Watts app state
- No stack traces or HTTP errors

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 33: setScheduleEnabled("on")

**What:** Verify calling `setScheduleEnabled("on")` re-enables the schedule (reverse of Test 32).

**Steps:**

1. Ensure device is authenticated and `scheduleEnabled` is currently `"off"` (from Test 32 or manual disable in Watts app).
2. On device page, run command **`setScheduleEnabled("on")`**.
3. Watch the logs, device attributes, and Watts app for 10–15 seconds.

**Expected:**

- Hubitat shows optimistic event: `scheduleEnabled = "on"` within 1–2 seconds
- Logs show successful API `PATCH` call
- Within 2–10 seconds, Watts app shows schedule enabled
- After next polling cycle, `scheduleEnabled` attribute matches the Watts app state
- No stack traces or HTTP errors

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 34: Invalid setScheduleEnabled Argument

**What:** Verify invalid arguments are rejected gracefully.

**Steps:**

1. Ensure device is authenticated.
2. On device page, attempt to run command with invalid argument: **`setScheduleEnabled("maybe")`** [or similar invalid value].
3. Watch the logs.
4. Observe that device state does not change.

**Expected:**

- Logs show a clear warning (e.g., "Invalid scheduleEnabled value: 'maybe'. Valid values are 'on' or 'off'.")
- No API call is sent to the cloud
- `scheduleEnabled` attribute does not change
- No stack trace or crash
- Device remains stable

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 35: scheduleEnabled Reflects External Change

**What:** Verify toggling schedule in Watts app updates Hubitat's `scheduleEnabled` attribute.

**Steps:**

1. Ensure device is authenticated and polling normally (interval ≤ 5 minutes).
2. Note the current `scheduleEnabled` value in Hubitat.
3. Open Watts app and toggle the schedule enable/disable setting.
4. Do not interact with Hubitat; let polling run.
5. Wait for the next poll cycle (≤ 5 minutes; check logs to confirm the poll executed).
6. Observe Hubitat's `scheduleEnabled` attribute.

**Expected:**

- After the next poll, Hubitat's `scheduleEnabled` matches the Watts app's current state
- No command was required; polling auto-reconciles
- Logs show successful `GET` call and schedule state parsing
- Attribute updates correctly without errors

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Hold Mode Tests [Tier 2 — Feature]

### Test 36: Hold Reflects Schedule Following

**What:** Verify `thermostatHold` attribute shows `"following"` when not held.

**Steps:**

1. Ensure device is authenticated.
2. Verify `scheduleEnabled` is `"on"` (enable via Test 33 if needed).
3. Ensure no manual setpoint override has been applied.
4. Click **Refresh**.
5. Check the `thermostatHold` attribute on the device page.

**Expected:**

- `thermostatHold` attribute displays `"following"` (or similar indicator that schedule is active)
- After next polling cycle, value remains `"following"` while following the daily schedule
- Logs do not show hold-mode errors

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 37: Hold Reflects Manual Override

**What:** Verify `thermostatHold` changes to `"holding"` when setpoint is manually overridden.

**Steps:**

1. Ensure device is authenticated and `thermostatHold` is currently `"following"`.
2. On device page, run command **`setHeatingSetpoint(72)`** (or any value different from the schedule).
3. Watch `thermostatHold` and logs for 2–10 seconds.
4. Wait for the next polling cycle to confirm state.

**Expected:**

- Within 1–2 seconds (optimistic update), `thermostatHold` shows `"holding"`
- Logs show the override was registered
- After next polling cycle (≤ 5 min), `thermostatHold` still shows `"holding"`
- Setpoint matches the manually set value

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 38: Hold Clears When Schedule Resumes

**What:** Verify `thermostatHold` returns to `"following"` when hold is cleared.

**Steps:**

1. Ensure device is currently in hold state (from Test 37 or manual override in Watts app).
2. In Hubitat, call **`setScheduleEnabled("on")`** or in the Watts app, clear the hold/override.
3. Click **Refresh** in Hubitat.
4. Watch `thermostatHold` for 2–10 seconds and through the next polling cycle.

**Expected:**

- After clearing the hold (via command or Watts app), `thermostatHold` reverts to `"following"`
- Logs show schedule re-activation
- Setpoint adjusts back to the scheduled value for the current time
- No errors or stale "holding" state persists

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Outdoor Temperature Tests [Tier 3 — Optional Hardware]

### Test 39: Outdoor Sensor Unavailable

**What:** Verify graceful handling when outdoor sensor is not installed.

**Steps:**

1. Ensure device is authenticated.
2. Click **Refresh**.
3. Check the device page for `outdoorTemperature` and `outdoorSensorStatus` attributes.

**Expected:**

- If no outdoor probe is installed, `outdoorSensorStatus` shows `"unavailable"` (or `"not_installed"`)
- `outdoorTemperature` attribute is not populated or displays "N/A"
- No errors or stack traces in logs
- Device operates normally without outdoor data

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

**Note:** [default expectation if no outdoor probe] — This is the expected behavior for installations without an outdoor temperature sensor.

---

### Test 40: Outdoor Sensor Okay

**What:** Verify outdoor temperature displays when sensor is available and functional.

**Steps:**

1. **Skip this test if no outdoor probe is installed** (see Test 39).
2. Ensure device is authenticated.
3. Click **Refresh**.
4. Check `outdoorTemperature` and `outdoorSensorStatus` attributes on device page.
5. Cross-reference the displayed temperature to the expected outdoor temperature for your location and time of day.

**Expected:**

- `outdoorSensorStatus` shows `"okay"` (or `"good"`/`"healthy"`)
- `outdoorTemperature` displays a reasonable numeric value (e.g., 55–85°F for typical climate)
- Temperature is in the correct unit for your hub's configuration (°F or °C)
- Value is consistent across multiple polls (or changes gradually over time, not erratically)
- Matches expected weather conditions for your location

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

**Note:** [skip if no outdoor probe] — Only run this test if your SunStat installation includes an outdoor temperature sensor.

---

### Test 41: Outdoor Unit Conversion

**What:** Verify outdoor temperature uses the hub's unit, not the device's unit.

**Steps:**

1. **Skip if no outdoor probe or hub is Imperial (°F)**.
2. Note the hub's `location.temperatureScale` (check Hub Preferences or IDE console for "Temperature Scale").
3. Ensure device is authenticated and has an outdoor sensor.
4. Click **Refresh**.
5. Verify the displayed `outdoorTemperature` matches the hub's configured unit (not the device's native unit).

**Expected:**

- If hub is set to Celsius, outdoor temperature displays in °C (e.g., 18.3°C)
- If hub is set to Fahrenheit, outdoor temperature displays in °F (e.g., 65°F)
- Conversion is applied correctly (±0.5° tolerance for rounding)
- No mixed units (e.g., not 65°F when hub is set to Celsius)

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Setpoint Precision Tests [Tier 3 — UX Polish]

### Test 42: setpointStep Populated

**What:** Verify `setpointStep` attribute exists and contains the correct step value.

**Steps:**

1. Ensure device is authenticated.
2. Click **Refresh**.
3. Check the device page for `setpointStep` attribute.

**Expected:**

- `setpointStep` attribute is present and displays a positive numeric value
- For °F-based devices, value is 1.0
- For °C-based devices, value is 0.5
- Value matches the device's reported granularity in the API response
- No parse errors or missing-attribute errors in logs

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 43: setHeatingSetpoint Rounds to Step

**What:** Verify setpoint commands round to the configured step value.

**Steps:**

1. Ensure device is authenticated and uses °F units (1.0°F step).
2. On device page, run command **`setHeatingSetpoint(72.3)`** (a value not aligned to the 1°F step).
3. Watch the logs for the API `PATCH` body.
4. Observe the `heatingSetpoint` attribute.

**Expected:**

- The actual value sent to the API is 72.0 (rounded to nearest 1.0°F), not 72.3
- Logs show the rounded value in the PATCH request body (if debug logging is enabled)
- `heatingSetpoint` attribute displays 72.0 (the rounded value)
- No error or rejection; command succeeds with rounding applied

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 44: setHeatingSetpoint Half-Step on °C

**What:** Verify setpoint rounding to nearest 0.5°C on Celsius devices.

**Steps:**

1. **Skip if hub is Imperial (°F); only run on Celsius devices**.
2. Ensure device is authenticated and uses °C units (0.5°C step).
3. On device page, run command **`setHeatingSetpoint(20.7)`** (a value between the 0.5°C steps: 20.5 and 21.0).
4. Watch the logs and `heatingSetpoint` attribute.

**Expected:**

- The value is rounded to either 20.5°C or 21.0°C (nearest 0.5°C step)
- Logs show the rounded value in the PATCH request
- `heatingSetpoint` attribute displays the rounded value
- Command succeeds without errors

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

**Note:** [skip if Imperial] — Only applies to metric (°C) devices.

---

### Floor Temperature Bounds Tests [Tier 2 — Feature]

### Test 45: setFloorMinTemp Within Bounds

**What:** Verify `setFloorMinTemp` command works when value is within valid range.

**Steps:**

1. Ensure device is authenticated.
2. Click **Refresh** to populate current floor bounds.
3. Check device state or logs for `state.floorMin` and `state.floorMax` (e.g., 40°F min, 85°F max).
4. On device page, run command **`setFloorMinTemp(70)`** (a value between the bounds).
5. Watch logs and device attributes.

**Expected:**

- Command succeeds cleanly without clamping warnings
- Logs show a successful API `PATCH` call
- After next polling cycle, device state reflects the new floor min temperature
- No errors or stack traces

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 46: setFloorMinTemp Above Max

**What:** Verify `setFloorMinTemp` clamps to `floorMax` when value exceeds upper bound.

**Steps:**

1. Ensure device is authenticated and bounds are known (e.g., max = 85°F).
2. On device page, run command **`setFloorMinTemp(95)`** (well above the max).
3. Watch logs for clamping messages.
4. Check the device state or next poll to verify what value was actually sent to the API.

**Expected:**

- Logs show a warning message like: **"Floor min 95 exceeds max 85; clamped to 85"**
- The API `PATCH` body contains 85 (the clamped value), not 95
- Device state updates to reflect the clamped value
- Command does not fail or crash

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 47: setFloorMinTemp Below Min

**What:** Verify `setFloorMinTemp` clamps to `floorMin` when value is below lower bound.

**Steps:**

1. Ensure device is authenticated and bounds are known (e.g., min = 40°F).
2. On device page, run command **`setFloorMinTemp(30)`** (well below the min).
3. Watch logs for clamping messages.
4. Check the device state or next poll to verify what value was actually sent to the API.

**Expected:**

- Logs show a warning message like: **"Floor min 30 is below minimum 40; clamped to 40"**
- The API `PATCH` body contains 40 (the clamped value), not 30
- Device state updates to reflect the clamped value
- Command does not fail or crash

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 48: Floor Min/Max Populated in State

**What:** Verify floor temperature bounds are available in device state for inspection.

**Steps:**

1. Ensure device is authenticated and has completed at least one polling cycle.
2. Open the Hubitat IDE **State** view for the device (or check logs for state output).
3. Look for `state.floorMin` and `state.floorMax` entries.

**Expected:**

- `state.floorMin` and `state.floorMax` are populated with numeric values (typically 40 and 85 for °F devices)
- Values are consistent with the API response and match the bounds used in Tests 45–47
- No null, undefined, or missing values
- Values persist across polling cycles

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

**Note:** [devs-only inspection — check via Hubitat IDE state view] — This test requires access to the IDE State view or debug logging to inspect internal state.

---

## Test Area: Edge Cases & Network Resilience

### Test 49: Network Timeout

**What:** Verify the driver handles cloud connectivity loss gracefully.

**Steps:**

1. Ensure the device is authenticated and working.
2. Temporarily block internet access from the Hubitat hub (e.g., firewall rule, WiFi disconnect, or unplug ethernet).
3. On the device page, click **Refresh** or `setHeatingSetpoint(75)`.
4. Watch the logs and device state for 30 seconds.
5. Restore internet access.
6. Click **Refresh** again.

**Expected:**

- The failed refresh or command logs a clear error message, e.g.:
  - **Cloud request timed out after 30 seconds...**
  - **Unable to reach SunStat API...**
  - or another helpful network error [needs Trinity profile]
- The error suggests checking internet connectivity or the timeout preference
- `authStatus` does not crash; it may temporarily show **Offline** or **Connecting** [needs Trinity profile]
- No stack traces or infinite retry loops appear
- After internet is restored, **Refresh** succeeds cleanly without needing to recreate the device or re-enter credentials
- Subsequent polls resume at the configured interval

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 50: Malformed Cloud Response

**What:** Verify the driver recovers from unexpected API response shape [needs Cypher spec for expected API structure].

**Steps:**

1. This test may require mock HTTP interception or is deferred until Cypher finalizes the API contract.
2. If simulated, inject a malformed JSON response or missing field.

**Expected:**

- The driver logs a clear JSON parse error or missing-field warning [needs Trinity profile]
- The device does not crash or hang
- Attributes retain their previous values (no null/undefined shown to the user)
- The next poll or command automatically retries

**Note:** [needs Cypher spec] — This test is deferred until the exact API response shape is known. Once Cypher completes the API spec, this test will be executable via curl mocking or pcap replay.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 51: Device Offline / Physically Powered Off

**What:** Verify the driver handles the thermostat being powered off.

**Steps:**

1. Ensure the device is authenticated and working.
2. Physically power off the SunStat thermostat (or disable it from the circuit breaker / switch).
3. Wait 1–2 poll cycles.
4. Observe Hubitat attributes and logs.
5. Power the device back on.
6. Wait 1–2 poll cycles.

**Expected:**

- After power-off, the next poll fails with a clear offline/unreachable message [needs Trinity profile]
- `authStatus` may change to **Device Offline** or similar [needs Trinity profile]
- No stack traces; the driver remains stable
- After power-on and WiFi reconnection, the next poll succeeds and state is restored
- Attributes do not show stale data indefinitely; they update once the device is responsive again

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 52: Rapid / Concurrent Commands

**What:** Verify the driver handles burst commands without race conditions or dropped updates.

**Steps:**

1. Ensure the device is authenticated and working.
2. Rapidly toggle mode: `setThermostatMode("heat")`, then `setThermostatMode("off")`, then `setThermostatMode("heat")` in quick succession (within 1 second).
3. Immediately issue a setpoint change: `setHeatingSetpoint(75)`.
4. Watch the logs and Hubitat attributes.
5. Wait 5 seconds and observe final state.

**Expected:**

- All commands are queued and processed (not dropped)
- The logs show each command attempt, even if some fail or are rate-limited [needs Cypher spec for rate limits]
- Final state is correct after all commands settle
- No stack traces, deadlocks, or orphaned HTTP requests appear
- Hubitat tile updates reflect the final state

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 53: Hub Reboot / Token Reuse

**What:** Verify the driver recovers after a Hubitat hub restart.

**Steps:**

1. Ensure the device is authenticated, working, and has issued at least one command (so credentials are validated in memory).
2. Reboot the Hubitat hub from **Settings → Reboot Hub**.
3. Wait for the hub to come back online (typically 2–5 minutes).
4. Open the device page and watch `authStatus` and logs for 30 seconds.
5. Test commands: **Refresh**, `setHeatingSetpoint(72)`, mode change.

**Expected:**

- After reboot, the driver reinitializes cleanly (no credentials re-entry required)
- `authStatus` returns to **Authenticated: <thermostat name>** after startup [needs Trinity profile]
- Polling resumes at the configured interval
- Refresh, setpoint, and mode commands still work without errors
- No stack traces or missing-schedule issues appear
- Device page loads and responds normally

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Capability Conformance

### Test 54: Hubitat Dashboard Display

**What:** Verify the thermostat tile displays correctly on a Hubitat dashboard.

**Steps:**

1. Create a new Hubitat dashboard or use an existing one.
2. Add the SunStat device to the dashboard using the **Thermostat** tile template [needs Trinity profile for exact tile name].
3. Observe the tile layout and displayed attributes.

**Expected:**

- The tile shows `thermostatMode`, `heatingSetpoint`, `currentTemperature`, and `thermostatOperatingState` [needs Trinity profile for exact layout]
- All values are readable and update in real-time
- Mode and setpoint buttons/controls are functional
- No red error indicators or missing data
- Colors and icons render correctly

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 55: Rule Machine Integration

**Steps:**

1. Open Hubitat Rule Machine and create a simple rule that triggers on the SunStat device.
2. Example rule: "If `thermostatOperatingState` becomes Heating, send a notification" [needs Trinity profile].
3. Execute the rule by triggering the condition (e.g., set mode to Heat and ensure floor temp is below setpoint).
4. Verify the rule fires and actions execute.
5. Create a second rule: "Set SunStat to 75°F when motion is detected" [needs Trinity profile].

**Expected:**

- Rule Machine recognizes the SunStat device and lists its attributes and commands
- Rules trigger correctly and actions execute without errors
- Notifications or other downstream actions fire
- No stack traces in Hubitat logs related to Rule Machine or the SunStat device

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 56: Multi-Device Scenario

**What:** Verify multiple SunStat thermostats operate independently.

**Steps:**

1. If Mads has multiple SunStat thermostats on the same Watts account, add both to Hubitat.
2. Assign each to a different device instance.
3. Set different setpoints for each (e.g., Device 1 to 72°F, Device 2 to 68°F).
4. Observe both devices for 30 seconds.

**Expected:**

- Both devices authenticate independently
- Setpoint and mode changes to Device 1 do not affect Device 2
- Each device has its own `thermostatMode`, `heatingSetpoint`, `currentTemperature`, and `thermostatOperatingState`
- Polling for both devices occurs without interference
- No auth failures or state cross-contamination

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Logging & Debug Output

### Test 57: Debug Logging Auto-Disable

**What:** Verify debug logging automatically disables after a timeout to prevent log spam.

**Steps:**

1. Enable **Debug logging** in Preferences.
2. Click **Save Preferences**.
3. Note the time.
4. Watch the logs — debug entries should appear.
5. Wait 30–35 minutes (or check logs periodically).
6. Verify debug logging stops and does not resume unless manually re-enabled.

**Expected:**

- Debug logging produces helpful diagnostic info (e.g., HTTP request/response, JSON parse details) [needs Trinity profile]
- After 30 minutes, a log entry appears: **Debug logging disabled** [needs Trinity profile]
- Debug logs stop appearing after that time
- Info/warning/error logs continue normally
- Manual re-enable via Preferences restarts the 30-minute timer

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 58: Sensitive Data Redaction in Logs

**What:** Verify credentials, tokens, and sensitive data are never logged.

**Steps:**

1. During all tests, search the Hubitat IDE Logs for:
   - Email or password
   - Any auth token or JWT substring
   - API key or management key (if applicable)
2. Perform a `grep` or text search in the logs for each credential.

**Expected:**

- No email, password, or credentials appear in any logs
- Auth tokens or sensitive headers are either omitted or redacted (e.g., `Authorization: [REDACTED]`)
- Log entries clearly identify what failed (e.g., "Auth failed with status 401") without exposing the secret
- Error messages are helpful but safe (e.g., "Invalid credentials. Check your email and password in Preferences.")

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: v0.1.3 — `setRefreshToken` Command

> **Context:** v0.1.3 replaces the `refreshToken` password preference with a `setRefreshToken(String token)` command on the device Commands tab. This bypasses Hubitat's ~1024-char preference save limit. Watts Home refresh tokens are ~1660 chars (JWE format, 4 dots). All tests in this section target the parent device page.

---

### Test 59: Happy Path — Fresh Install with `setRefreshToken`

**Tier:** 1 (Core — must pass before beta)  
**v0.1.3 feature**

**What:** Verify a brand-new install (no prior token in state) succeeds end-to-end via `setRefreshToken`.

**Setup:**

- Fresh virtual device of type **SunStat Thermostat** — no prior `state.refreshToken`
- v0.1.3 parent driver code saved in Hubitat
- Logs tab open

**Steps:**

1. Create a new virtual device; assign parent driver type **SunStat Thermostat**.
2. Open device page → **Preferences** tab. Confirm there is **no** `refreshToken` password field visible.
3. Enter `pollInterval`, `logLevel`, and any other non-token preferences. Click **Save Preferences**.
4. Navigate to the **Commands** tab. Locate the **setRefreshToken** command with its single text input.
5. Paste the full ~1660-char Watts Home refresh token (from `homebridge-tekmar-wifi tokens.json`) into the input field.
6. Click **Run**.
7. Watch the Logs window for 30 seconds.
8. After the first poll cycle completes, verify child device(s) appear under the parent.

**Expected:**

- Log line: `[SunStat] Refresh token stored (1660 chars)` (exact char count will vary by token)
- No `NullPointerException`, `ClassCastException`, or stack trace
- `state.refreshToken` is populated (verify via device data view or debug log)
- `state.accessToken` and `state.tokenExpiresAt` are cleared (driver forces re-auth with new token)
- `initialize()` runs; polling schedule is registered
- Within one poll cycle, child device(s) are discovered and their thermostat attributes (`currentTemperature`, `heatingSetpoint`, `thermostatMode`) are populated
- Token value does NOT appear in any log line (only length is logged)

**Pass Criteria:** Log line with char count appears; child devices discovered; no errors.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 60: Migration — Existing User with Token Already in State (v0.1.2 → v0.1.3)

**Tier:** 1 (Core — regression; must not break existing users)  
**v0.1.3 migration**

**What:** Verify that a user who completed the v0.1.2 install (token successfully migrated to `state.refreshToken`) upgrades to v0.1.3 without any manual re-entry of the token.

**Setup:**

- Device is running v0.1.2; `state.refreshToken` is populated; driver is polling normally
- v0.1.3 driver code is saved in Hubitat (code update only — no new device)

**Steps:**

1. Update parent driver code to v0.1.3 via Drivers Code editor. Save.
2. On the parent device page, click **Save Preferences** (triggers `updated()` → `initialize()`).
3. Watch Logs for 30 seconds.
4. Wait for the next poll cycle to complete.
5. Verify child devices still report correct temperature and mode.

**Expected:**

- No log warning about a missing token
- No prompt to run `setRefreshToken`
- Polling resumes immediately on `initialize()`
- Child thermostat attributes continue updating as before
- No stack traces or errors

**Pass Criteria:** Driver continues operating without token re-entry; no regressions in child device state.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 61: Migration — Token Stuck in Preferences (v0.1.2 Broken Install → v0.1.3)

**Tier:** 2 (Feature — important migration path for users who were previously broken)  
**v0.1.3 migration**

**What:** Verify the recovery path for a user whose v0.1.2 token save always failed (preference truncation bug) and who has no `state.refreshToken`.

**Setup:**

- Device is at v0.1.2; `state.refreshToken` is absent or empty; driver was unable to poll
- Update parent driver code to v0.1.3

**Steps:**

1. Update parent driver code to v0.1.3. Save.
2. Click **Save Preferences** on the device page.
3. Watch Logs immediately.
4. Navigate to the **Commands** tab. Locate **setRefreshToken**.
5. Paste the full ~1660-char refresh token. Click **Run**.
6. Watch Logs for 30 seconds.
7. Verify polling starts and children appear.

**Expected after Save Preferences (step 3):**

- Log line: `[SunStat] is waiting for a Watts Home refresh token — run the setRefreshToken command`
- No crash; driver stays alive in "waiting" state
- Polling does NOT start until token is provided (no auth attempts with empty token)

**Expected after setRefreshToken (step 6):**

- Same as Test 59 expected outcomes: `Refresh token stored (NNNN chars)`, child discovery succeeds

**Pass Criteria:** Warning logged; driver waits safely; succeeds after `setRefreshToken` is run.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 62: Empty Input to `setRefreshToken`

**Tier:** 2 (Edge case — invalid input must be rejected gracefully)  
**v0.1.3 feature**

**What:** Verify that calling `setRefreshToken("")` logs a warning and does not alter state.

**Setup:**

- Parent device with v0.1.3 driver. Either fresh or with existing token in state (test both sub-cases).

**Steps:**

1. On the Commands tab, find **setRefreshToken**.
2. Leave the input field blank (empty string) and click **Run**.
3. Watch Logs.
4. If a prior token was in `state.refreshToken`, verify it is still there (use device data or debug log).

**Expected:**

- Log warning: `[SunStat] setRefreshToken: token too short (0 chars) — paste the full token from homebridge-tekmar-wifi`
- `state.refreshToken` is unchanged (not cleared, not overwritten)
- `initialize()` is NOT called
- No stack trace

**Pass Criteria:** Warning logged with "0 chars"; state not modified.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 63: Null Input to `setRefreshToken`

**Tier:** 2 (Edge case — Hubitat may pass null for empty command fields)  
**v0.1.3 feature**

**What:** Verify that `setRefreshToken(null)` does not throw a NullPointerException and is handled as gracefully as an empty string.

**Setup:**

- Parent device with v0.1.3 driver. Logs tab open.

**Steps:**

1. Invoke `setRefreshToken(null)` programmatically via Hubitat Rule Machine or the hub's local API (`POST /apps/api/.../devices/{id}/setRefreshToken`).
   - If the Commands tab UI enforces non-null input, use Rule Machine's "Run Custom Action" with a blank parameter.
2. Watch Logs.

**Expected:**

- No `NullPointerException` — the `token?.trim() ?: ""` null-safe guard in the driver handles null cleanly
- Log warning: `[SunStat] setRefreshToken: token too short (0 chars) — ...`
- `state.refreshToken` unchanged
- `initialize()` NOT called

**Pass Criteria:** No NPE; warning logged; state unchanged.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 64: Whitespace-Only Input to `setRefreshToken`

**Tier:** 2 (Edge case — common paste artifact)  
**v0.1.3 feature**

**What:** Verify that a string of spaces, tabs, and newlines is treated as effectively empty after trim.

**Setup:**

- Parent device with v0.1.3 driver. Logs tab open.

**Steps:**

1. On the Commands tab, find **setRefreshToken**.
2. Enter `"   \n\t  "` (or paste several spaces and a newline) into the input. Click **Run**.
3. Watch Logs.

**Expected:**

- After `trim()`, the string is `""` (length 0)
- Log warning: `[SunStat] setRefreshToken: token too short (0 chars) — ...`
- `state.refreshToken` unchanged
- `initialize()` NOT called
- No stack trace

**Pass Criteria:** Whitespace-only input treated as empty; warning logged.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 65: Short String Input (Likely a Paste Error)

**Tier:** 2 (Edge case — user pastes only part of token by accident)  
**v0.1.3 feature**

**What:** Verify that a plausible-looking but obviously-too-short string (e.g., the beginning of a JWT) is rejected with a helpful message.

**Setup:**

- Parent device with v0.1.3 driver. Logs tab open.

**Steps:**

1. On the Commands tab, find **setRefreshToken**.
2. Enter `"eyJ123"` (6 chars — a typical JWT prefix with no body) into the input. Click **Run**.
3. Also try `"eyJ" + "x".repeat(96)` (99 chars — just under the 100-char threshold).
4. Watch Logs for both attempts.

**Expected (6-char input):**

- Log warning: `[SunStat] setRefreshToken: token too short (6 chars) — paste the full refresh token from homebridge-tekmar-wifi`
- `state.refreshToken` unchanged
- `initialize()` NOT called

**Expected (99-char input):**

- Same warning: `token too short (99 chars) — ...`
- State unchanged

**Pass Criteria:** Both short strings rejected with char count in the log; state unchanged.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 66: Token with Surrounding Whitespace (Copy-Paste Artifact)

**Tier:** 2 (UX — common terminal/browser copy-paste behavior adds whitespace)  
**v0.1.3 feature**

**What:** Verify that a valid token with leading/trailing whitespace or a trailing newline is trimmed before storage.

**Setup:**

- Parent device with v0.1.3 driver. Logs tab open.
- Prepare a ~1660-char token. Manually add 3 spaces before and a newline + 2 spaces after.

**Steps:**

1. On the Commands tab, find **setRefreshToken**.
2. Paste the whitespace-padded token (total length: ~1665 chars with padding) into the input. Click **Run**.
3. Watch Logs.
4. Inspect `state.refreshToken` (via device data view or a debug-mode poll).

**Expected:**

- Log line: `[SunStat] Refresh token stored (1660 chars)` — reflects the trimmed length, not the padded length
- `state.refreshToken` starts with `eyJ` and ends with the last base64url character, no leading/trailing spaces or newlines
- Polling starts normally; no auth error due to whitespace-corrupted token

**Pass Criteria:** Stored token is trimmed; length in log matches trimmed token; driver initializes normally.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 67: Replace an Existing Token via `setRefreshToken`

**Tier:** 2 (Feature — token rotation; also covers the case where the user re-runs the CLI tool)  
**v0.1.3 feature**

**What:** Verify that running `setRefreshToken` when a valid token is already in state cleanly replaces it and forces re-auth.

**Setup:**

- Parent device with v0.1.3 driver; `state.refreshToken` is set and polling is running
- Have a second valid token value ready (can be the same token if only one account is available; the important thing is that the command runs against existing state)

**Steps:**

1. Note the first three characters of the current `state.refreshToken` value (from debug log or device data).
2. On the Commands tab, run **setRefreshToken** with the new (or same) ~1660-char token.
3. Watch Logs.
4. Verify `state.accessToken` and `state.tokenExpiresAt` are cleared (look for debug log showing they are removed or missing).
5. Wait for the next poll cycle to confirm re-auth succeeds with the new token.

**Expected:**

- Log line: `[SunStat] Refresh token stored (NNNN chars)` for the new token
- `state.accessToken` is removed/cleared (driver must re-exchange refresh token for a new access token)
- `state.tokenExpiresAt` is removed/cleared
- `initialize()` is called; polling schedule re-registers
- Within one poll cycle, the driver authenticates with the new refresh token and child attributes update

**Pass Criteria:** Old access token cleared; new token stored; driver re-initializes and resumes polling.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 68: Concurrent `setRefreshToken` Calls

**Tier:** 3 (Edge case — unlikely in practice but tests for state corruption)  
**v0.1.3 feature**

**What:** Verify that if the user accidentally clicks **Run** twice in rapid succession, the driver does not produce errors, duplicate child discovery, or log spam.

**Setup:**

- Parent device with v0.1.3 driver. Logs tab open.
- Prepare a ~1660-char token.

**Steps:**

1. On the Commands tab, find **setRefreshToken** with the token ready in the input field.
2. Click **Run** twice as fast as possible (double-click or two rapid single-clicks).
3. Watch Logs for the next 60 seconds.
4. Verify child device count does not double; verify no error spam.

**Expected:**

- Two `Refresh token stored` log lines may appear (one per call) — this is acceptable
- `state.refreshToken` contains the token from the second call (last-write wins)
- No duplicate child devices are created
- No exception or error log lines
- Polling schedule is registered once (Hubitat `runEvery*` calls are idempotent)

**Pass Criteria:** No errors; no duplicate children; final state is consistent.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 69: Token Survives Hub Reboot (Regression Check)

**Tier:** 1 (Core regression — state persistence across reboots is mandatory)  
**v0.1.3 regression**

**What:** Verify that after a successful `setRefreshToken`, rebooting the Hubitat hub does not lose the token and the driver resumes polling automatically on startup.

**Setup:**

- Parent device with v0.1.3 driver; `setRefreshToken` was run successfully; polling is active.

**Steps:**

1. Confirm `state.refreshToken` is set and the driver is polling (child devices updating).
2. Reboot the Hubitat hub (Settings → Reboot Hub). Wait for it to come back online (~2 minutes).
3. After hub restart, open the parent device page and the Logs tab.
4. Wait up to 2× the configured `pollInterval` for the first poll to occur.

**Expected:**

- `state.refreshToken` is still present after reboot (Hubitat `state.*` is persisted across reboots)
- `initialize()` is called automatically by Hubitat on hub startup
- Polling schedule re-registers without user intervention
- Within one poll cycle, child attributes update normally
- No "missing token" warning logged; no errors

**Pass Criteria:** Token present after reboot; driver resumes polling without any user action.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 70: `tokenBootstrapReady()` Logic — Code Review Check

**Tier:** 2 (Code review / static verification — no runtime hardware required)  
**v0.1.3 code correctness**

**What:** Verify that `tokenBootstrapReady()` returns the correct boolean based solely on `state.refreshToken`, and no longer references `settings.refreshToken`.

**Setup:**

- Access to `sunstat-thermostat-parent.groovy` (v0.1.3 source)

**Steps:**

1. Open `sunstat-thermostat-parent.groovy` in the Hubitat Drivers Code editor (or locally).
2. Locate the `tokenBootstrapReady()` method.
3. Verify the method body matches the v0.1.3 spec exactly:
   ```groovy
   private boolean tokenBootstrapReady() {
       return safeStr(state.refreshToken).size() > 0
   }
   ```
4. Confirm the method contains **no** reference to `settings.refreshToken`.
5. Mentally trace both branches:
   - When `state.refreshToken` is null or `""`: `safeStr(...)` returns `""`, `.size()` returns 0, method returns `false` ✓
   - When `state.refreshToken` is a 1660-char token: `safeStr(...)` returns the token, `.size()` returns 1660, method returns `true` ✓

**Expected:**

- Method body matches spec (only `state.refreshToken`, no `settings.refreshToken`)
- Both branches produce the correct boolean
- No legacy fallback to `settings.refreshToken` present

**Pass Criteria:** Method body is exactly as specified; both logical branches verified correct.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 71: No Live References to `settings.refreshToken` — Code Review Check

**Tier:** 1 (Code review / static verification — ensures complete preference removal)  
**v0.1.3 code correctness**

**What:** Verify that the v0.1.3 parent driver contains no live (non-comment) references to `settings.refreshToken`, confirming the preference was fully removed and not just hidden.

**Setup:**

- Access to `sunstat-thermostat-parent.groovy` (v0.1.3 source) and a text search tool

**Steps:**

1. Run a text search (grep or Drivers Code editor search) for `settings.refreshToken` in `sunstat-thermostat-parent.groovy`:
   ```powershell
   Select-String -Path "drivers\sunstat-thermostat\sunstat-thermostat-parent.groovy" -Pattern "settings\.refreshToken"
   ```
2. Review any matches. Distinguish between:
   - **Live code references** (assignments, conditionals, method calls) — these are failures
   - **Comment-only references** (lines starting with `//` or inside `/* */`) — acceptable if they explain the migration
3. Confirm the `preferences {}` block in `metadata {}` does NOT contain an `input` for `refreshToken`.
4. Confirm `initialize()` does NOT contain the v0.1.2 migration block (`if (settings.refreshToken && !state.refreshToken) { ... }`).
5. Confirm `refreshTokensSync()` does NOT fall back to `settings.refreshToken`.

**Expected:**

- Zero live code references to `settings.refreshToken`
- `preferences {}` block has no `input name: "refreshToken"` declaration
- `initialize()` migration block is absent
- `refreshTokensSync()` uses only `state.refreshToken`

**Pass Criteria:** `grep` finds zero live references; preference declaration and migration block are absent.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Logging Watch List

During all tests, watch for these regressions:

1. **Auth problems**
   - `authStatus` should change clearly on success/failure
   - No passwords, tokens, hashes, or API keys should ever appear in logs

2. **Unexpected HTTP statuses**
   - Look for `Unexpected HTTP 4xx/5xx ...` in logs [needs Cypher spec for API error codes]
   - A single HTTP `401` may be followed by a token refresh + replay; confirm this behavior is documented [needs Trinity profile]

3. **State regressions**
   - `thermostatMode` should track the current mode (Off, Heat, Auto, Hold, Boost, etc.)
   - `heatingSetpoint` should always be a valid number, never null or undefined
   - `currentTemperature` and `floorTemperature` should be numeric and non-zero (unless sensor malfunction)
   - `thermostatOperatingState` should be Heating or Idle, never null

4. **Driver stability**
   - No stack traces, especially `MissingMethodException`, `NullPointerException`, `ClassCastException`, or JSON parse errors
   - No new forbidden Hubitat sandbox calls (`System.*`, `Thread.*`, `Runtime.*`, reflection, file I/O)
   - No memory leaks or orphaned schedules (verify by hub stability over time)

---

## Test Execution Matrix

**Priority Tier 1 (Core Functionality)**
- Test 1: Install and Initial Configuration
- Test 5: Refresh Current State
- Test 7: Set Heating Setpoint
- Test 9: Set Thermostat Mode
- Test 32: setScheduleEnabled("off") [v0.1.2 — schedule control]
- Test 33: setScheduleEnabled("on") [v0.1.2 — schedule control]
- Test 59: Happy Path — Fresh Install with setRefreshToken [v0.1.3]
- Test 60: Migration — Token Already in State (v0.1.2 → v0.1.3) [v0.1.3]
- Test 69: Token Survives Hub Reboot [v0.1.3]
- Test 71: No Live References to settings.refreshToken — Code Review [v0.1.3]

**Priority Tier 2 (State & Integration)**
- Test 2: Authentication Failure Handling
- Test 6: Polling Cycle
- Test 10: Mode Transition Correctness
- Test 26–31: Energy Reporting Tests [v0.1.2 — feature]
- Test 35: scheduleEnabled Reflects External Change [v0.1.2 — schedule sync]
- Test 36–38: Hold Mode Tests [v0.1.2 — feature]
- Test 43: setHeatingSetpoint Rounds to Step [v0.1.2 — precision]
- Test 45–48: Floor Bounds Clamping Tests [v0.1.2 — feature]
- Test 54: Hubitat Dashboard Display
- Test 55: Rule Machine Integration
- Test 61: Migration — Token Stuck in Preferences → setRefreshToken [v0.1.3]
- Test 62: Empty Input to setRefreshToken [v0.1.3]
- Test 63: Null Input to setRefreshToken [v0.1.3]
- Test 64: Whitespace-Only Input to setRefreshToken [v0.1.3]
- Test 65: Short String Input (Paste Error) [v0.1.3]
- Test 66: Token with Surrounding Whitespace [v0.1.3]
- Test 67: Replace Existing Token via setRefreshToken [v0.1.3]
- Test 70: tokenBootstrapReady() Logic — Code Review [v0.1.3]

**Priority Tier 3 (Edge Cases & Advanced)**
- Test 8: Setpoint Edge Cases
- Test 34: Invalid setScheduleEnabled Argument [v0.1.2 — error handling]
- Test 39–41: Outdoor Temperature Tests [v0.1.2 — optional hardware]
- Test 42: setpointStep Populated [v0.1.2 — precision]
- Test 44: setHeatingSetpoint Half-Step on °C [v0.1.2 — precision, Celsius-only]
- Test 49: Network Timeout
- Test 51: Device Offline / Physically Powered Off
- Test 52: Rapid / Concurrent Commands
- Test 53: Hub Reboot / Token Reuse
- Test 56: Multi-Device Scenario
- Test 68: Concurrent setRefreshToken Calls [v0.1.3]

**Priority Tier 4 (Optional Features & Defer)**
- Test 11: Schedule Readback [needs Trinity profile]
- Test 12: Schedule Modification [needs Trinity profile]
- Test 13: Boost Mode [needs Trinity profile]
- Test 14: Hold Mode [needs Trinity profile]
- Test 30: Energy Missing from Response [v0.1.2 — hard to reproduce without firmware swap]
- Test 50: Malformed Cloud Response [needs Cypher spec]
- Test 57: Debug Logging Auto-Disable
- Test 58: Sensitive Data Redaction in Logs

---

## Dependency Notes

### [needs Cypher spec]

The following tests cannot execute until Cypher completes API reverse-engineering:

- **Mode names:** Exact string values for Off, Heat, Auto, Boost, Hold, etc.
- **Setpoint range:** Minimum and maximum allowed setpoint values (e.g., 50–95°F)
- **Operating state:** Exact values for Heating, Idle, and any other states
- **API error codes and responses:** Expected HTTP status codes, JSON structure for success/failure
- **Units:** Celsius vs. Fahrenheit configuration (or auto-detection)
- **Boost/Hold behavior:** Exact semantics (setpoint delta, duration, auto-expiry)
- **Rate limits:** Any throttling or request-per-minute limits
- **Schedule structure:** JSON schema or format if schedule is exposed

### [needs Trinity profile]

The following tests cannot execute until Trinity finalizes the capability list and command signatures:

- **Exposed capabilities:** Thermostat, TemperatureMeasurement, Refresh, etc.
- **Command signatures:** `setHeatingSetpoint(value)`, `setThermostatMode(mode)`, custom commands like `setBoost(duration)`
- **Attributes:** Which temperature sensors are exposed (floorTemperature, currentTemperature, etc.)
- **Optional commands:** Whether schedule read/write, boost, hold, and other advanced features are included
- **Tile behavior:** Exactly which attributes appear on the default Hubitat thermostat tile
- **Debug logging:** Specific log format and categories

---

## When to Update This Plan

Once v0.1.2 is released and tested, incorporate feedback:
1. Update Tests 26–48 with actual results from real-world testing
2. Mark any Tier 3 tests as "Skipped if no hardware" with justification (e.g., outdoor probe unavailable)
3. Log any new edge cases discovered and add as Tests 72+

Once Cypher completes the API spec (Test 50 requirements, mode names, setpoint range, etc.), update this plan:
1. Replace `[needs Cypher spec]` markers with concrete API details
2. Add any missing test cases discovered during reverse-engineering (e.g., firmware update behavior, sensor calibration)

Once Trinity finalizes the capability profile:
1. Replace `[needs Trinity profile]` markers with exact command signatures and attributes
2. Finalize Test 11–14 (Schedule, Boost, Hold) based on what is actually exposed
3. Confirm dashboard tile behavior for Test 54

---

**Once all items in Tier 1 and Tier 2 are checked and passing, the driver is ready for beta testing with Mads.**

---

## Test Area: v0.1.4 — API Envelope Unwrap + URL Encoding

**Added:** 2026-05-16T21:24:48-07:00  
**Driver version:** 0.1.4  
**Background:** Every Watts API response is wrapped in `{errorNumber, errorMessage, body: <payload>}`. Prior to v0.1.4, `parseResponseBody()` and all direct `resp.data` usages returned the raw envelope, causing `discoverDevices` to fail silently. Additionally, locationIds that contain spaces (e.g., Mads' `"Misty Gray"`) broke HTTP requests because they were interpolated into URL paths without encoding. v0.1.4 fixes both issues.

---

### Test 72: `parseResponseBody` Unwraps `GET /User` Envelope — Unit / Code Review

**What:** Verify that `parseResponseBody(resp)` correctly strips the outer `{errorNumber, errorMessage, body}` envelope and returns the inner `body` Map when `resp.data` is the full API envelope for a User response.

**Setup:**

- Open `sunstat-thermostat-parent.groovy` in the Hubitat driver editor (or review locally).
- Identify the `parseResponseBody()` private method.

**Steps:**

1. Review `parseResponseBody()` source. Confirm it contains logic equivalent to:
   ```groovy
   if (m.containsKey("body") && m.body instanceof Map) {
       return m.body as Map
   }
   ```
2. Simulate or mentally trace: `resp.data` = `{errorNumber:0, errorMessage:null, body:{userId:"u-1", defaultLocationId:"abc-123", measurementScale:"I"}}`.
3. Confirm that the returned Map has key `userId` at the top level (NOT `errorNumber`).
4. Confirm the returned Map does NOT contain the key `errorNumber`.

**Expected:**

- `parseResponseBody(resp)` returns `[userId:"u-1", defaultLocationId:"abc-123", measurementScale:"I"]` (the inner body, not the envelope).
- `body?.defaultLocationId` evaluates to `"abc-123"`, not `null`.

**Pass Criteria:** The unwrap branch is present in source; the returned Map contains `userId` at top level.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 73: `parseResponseList` Unwraps `GET /Location` Envelope — Unit / Code Review

**What:** Verify that `parseResponseList(resp)` correctly strips the envelope and returns a List of 2 items when `resp.data` is a Location array response.

**Setup:**

- Identify the `parseResponseList()` private method in `sunstat-thermostat-parent.groovy`.

**Steps:**

1. Review `parseResponseList()` source. Confirm it contains logic equivalent to:
   ```groovy
   if (m.containsKey("body") && m.body instanceof List) {
       return m.body as List
   }
   ```
2. Simulate: `resp.data` = `{errorNumber:0, errorMessage:null, body:[{locationId:"X", name:"Home"}, {locationId:"Y", name:"Office"}]}`.
3. Confirm the returned List has exactly 2 elements.
4. Confirm `list[0]?.locationId` == `"X"`.

**Expected:**

- `parseResponseList(resp)` returns a List of size 2 (not an empty list, not the envelope Map).
- First element has `locationId == "X"`.

**Pass Criteria:** Method is present in source; returns List with correct size and contents.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 74: `parseResponseBody` Graceful Degradation — Missing `body` Key

**What:** Verify that if `resp.data` is a Map but does NOT contain a `body` key (e.g., old non-enveloped or unexpected response shape), `parseResponseBody` returns the Map as-is without crashing.

**Setup:**

- Review `parseResponseBody()` source for the fallback path.

**Steps:**

1. Simulate: `resp.data` = `{userId:"u-1", defaultLocationId:"abc-123"}` (no `errorNumber`, no `body` key — bare old shape).
2. Confirm the method returns the map as-is: `[userId:"u-1", defaultLocationId:"abc-123"]`.
3. Confirm no exception is thrown.

**Expected:**

- Returns `[userId:"u-1", defaultLocationId:"abc-123"]`.
- No NPE, no ClassCastException, no crash.
- Graceful degradation: callers can still read `defaultLocationId` from the returned Map.

**Pass Criteria:** The fallback `return m` path is present in source; no crash on this input shape.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 75: `parseResponseList` With API-Level Error Response — No Crash

**What:** Verify that `parseResponseList(resp)` returns an empty List (not an exception) when the API returns an error envelope where `body` is `null` and `errorNumber` is non-zero.

**Setup:**

- Review `parseResponseList()` source.

**Steps:**

1. Simulate: `resp.data` = `{errorNumber:1, errorMessage:"Unauthorized", body:null}`.
2. Confirm `parseResponseList(resp)` returns `[]`.
3. Confirm no NPE or ClassCastException.
4. In live testing: confirm the driver logs a warning or error at the call site (e.g., `fetchFirstLocationId` logs `"GET /Location returned empty list"` or equivalent).

**Expected:**

- Returns `[]`.
- No crash.
- Error is surfaced in logs by the calling method (not silently swallowed).

**Pass Criteria:** Method returns empty List; no exception thrown for `body:null` input.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 76: `resp.data` Is a Raw JSON String — Both Helpers Parse and Unwrap

**What:** Verify that both `parseResponseBody()` and `parseResponseList()` correctly parse a raw JSON String (not yet parsed by Hubitat's HTTP client) and then apply envelope unwrapping.

**Setup:**

- Review both helper methods for the `if (data instanceof String)` branch.

**Steps:**

1. For `parseResponseBody`: simulate `resp.data` = the String `'{"errorNumber":0,"errorMessage":null,"body":{"userId":"u-1","defaultLocationId":"abc-123"}}'`.
   - Confirm method parses the String via `JsonSlurper`, detects the `body` key, returns `[userId:"u-1", defaultLocationId:"abc-123"]`.
2. For `parseResponseList`: simulate `resp.data` = `'{"errorNumber":0,"errorMessage":null,"body":[{"locationId":"X"},{"locationId":"Y"}]}'`.
   - Confirm method returns a List of 2 elements.

**Expected:**

- Both helpers handle String input by parsing first, then unwrapping.
- Returned values are correctly typed (Map vs. List).

**Pass Criteria:** String path is present in both methods' source; traced return values are correct.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 77: `resp.data` Is Null — Both Helpers Return Empty Without NPE

**What:** Verify that both `parseResponseBody()` and `parseResponseList()` return empty collections (not null, no NPE) when `resp.data` is `null`.

**Setup:**

- Review both helper methods for null-safety.

**Steps:**

1. For `parseResponseBody`: simulate `resp.data` = `null`.
   - Confirm returns `[:]` (empty Map).
2. For `parseResponseList`: simulate `resp.data` = `null`.
   - Confirm returns `[]` (empty List).
3. Confirm neither method throws an NPE.

**Expected:**

- `parseResponseBody` returns `[:]`.
- `parseResponseList` returns `[]`.
- No NullPointerException.

**Pass Criteria:** Null path returns empty collections; no exception.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 78: URL Encoding — `locationId = "Misty Gray"` (Space) — Live Test

**What:** Verify that `discoverDevicesAtLocation()` (and any other call using locationId in the URL path) encodes spaces as `%20`, producing a valid HTTP request.

**Setup:**

- v0.1.4 driver installed on Hubitat.
- In parent device Preferences: set `Watts Home location ID` = `Misty Gray`.
- Enable debug logging.

**Steps:**

1. Click **discoverDevices** on the parent device page.
2. Watch the Hubitat live log.
3. Confirm the log (or underlying HTTP request) constructs the URL `/Location/Misty%20Gray/Devices` (not `/Location/Misty Gray/Devices`).
4. Confirm the HTTP request succeeds (HTTP 200) — no `IllegalArgumentException` or `URI syntax` error in logs.

**Expected:**

- URL fragment `Misty%20Gray` visible in debug logs.
- No URI syntax exception.
- If the locationId is valid on the Watts server, device list is returned.

**Pass Criteria:** No URI error in logs; `%20` encoding visible; request completes without exception.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 79: URL Encoding — UUID `locationId` Is Passed Verbatim — Code Review

**What:** Verify that a standard UUID locationId (e.g., `"12345678-1234-1234-1234-123456789abc"`) passes through `encodePathSegment()` unchanged, since hyphens and alphanumeric characters are safe in URL paths.

**Setup:**

- Review `encodePathSegment()` source in `sunstat-thermostat-parent.groovy`.

**Steps:**

1. Trace: `encodePathSegment("12345678-1234-1234-1234-123456789abc")`.
2. Confirm output equals `"12345678-1234-1234-1234-123456789abc"` (no percent-encoding applied).

**Expected:**

- UUID is returned verbatim.
- URL becomes `/Location/12345678-1234-1234-1234-123456789abc/Devices`.

**Pass Criteria:** Encoding is idempotent for RFC 3986 unreserved characters; UUID passes through unchanged.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 80: URL Encoding — Non-ASCII `locationId = "café"` — Code Review

**What:** Verify that non-ASCII characters in a locationId are percent-encoded using UTF-8, e.g., `é` → `%C3%A9`.

**Setup:**

- Review `encodePathSegment()` source.

**Steps:**

1. Trace: `encodePathSegment("café")`.
2. Confirm output = `"caf%C3%A9"`.
3. Confirm the URL fragment would be `/Location/caf%C3%A9/Devices`.

**Expected:**

- `é` (U+00E9) encoded as UTF-8 bytes `0xC3 0xA9` → `%C3%A9`.
- Full output: `"caf%C3%A9"`.

**Pass Criteria:** UTF-8 percent-encoding applied correctly; encoding matches RFC 3986 §2.1.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 81: URL Encoding — `locationId` With Literal Slashes — Code Review

**What:** Verify that a locationId containing literal forward slashes (pathological input) has those slashes encoded as `%2F`, preventing the URL path from being split incorrectly.

**Setup:**

- Review `encodePathSegment()` source.

**Steps:**

1. Trace: `encodePathSegment("name/with/slashes")`.
2. Confirm output = `"name%2Fwith%2Fslashes"`.
3. Confirm the URL fragment is `/Location/name%2Fwith%2Fslashes/Devices` — three path segments, not five.

**Expected:**

- `/` encoded as `%2F`.
- URL path has the correct structure (no accidental path splitting).

**Pass Criteria:** Slashes are encoded; URL structure is preserved.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 82: URL Encoding — Empty `locationId` — Behavior Verification

**What:** Verify that an empty `locationId` string does not cause an unhandled exception. The expected URL would be `/Location//Devices`; the driver should either guard against this before the call or fail loudly (not silently).

**Setup:**

- v0.1.4 driver installed on Hubitat.
- In parent device Preferences: set `Watts Home location ID` = `` (empty).
- Clear `state.locationId` if set.
- Enable debug logging.

**Steps:**

1. Click **discoverDevices**.
2. Watch logs.
3. Confirm the driver either: (a) logs a clear error before making the request (preferred), OR (b) makes the request to `/Location//Devices` and logs the resulting HTTP error.
4. Confirm no stack trace or unhandled exception.

**Expected:**

- `encodePathSegment("")` returns `""`.
- Driver logs a visible error or warning about missing locationId.
- No NPE, no unhandled exception.

**Pass Criteria:** No crash; empty locationId produces a log message, not a silent failure.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 83: `encodePathSegment` Used in All Affected Methods — Code Review

**What:** Verify that `discoverDevicesAtLocation()`, `fetchAndParseLocationState()`, and the `setAwayMode` PATCH call all use `encodePathSegment()` on the locationId before constructing the URL path. No raw string interpolation of locationId into URL paths.

**Setup:**

- Open `sunstat-thermostat-parent.groovy` in editor.

**Steps:**

1. Search for all occurrences of `locationId` interpolated into a URL string (e.g., `/Location/${locationId}`).
2. For each occurrence, confirm the surrounding code applies `encodePathSegment(locationId)` first (e.g., `/Location/${encodePathSegment(locId)}`).
3. Grep for raw interpolation: no instances of `/Location/${locId}` or `/Location/${locationId}` without `encodePathSegment` wrapping.
4. Confirm `setAwayMode` PATCH URL is also encoded (search for `/Location/` in the PATCH path-building code).

**Expected:**

- Zero raw `locationId` interpolations into URL paths.
- All three callers use `encodePathSegment`.

**Pass Criteria:** Grep finds no unencoded locationId in URL strings; all three call sites wrap with `encodePathSegment`.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 84: End-to-End Happy Path — Full Discovery Flow With v0.1.4 — Live Test

**What:** Regression / integration test. Verify the complete `discoverDevices` flow works end-to-end with v0.1.4 installed, no locationId pre-configured: GET /User unwrapped → `defaultLocationId` extracted → GET /Location unwrapped → first location selected → GET /Location/{encoded}/Devices unwrapped → child device created.

**Setup:**

- v0.1.4 driver installed on Hubitat.
- Parent device has a valid refresh token set via `setRefreshToken`.
- `Watts Home location ID` preference is BLANK (test auto-discovery).
- Enable debug logging.

**Steps:**

1. Click **discoverDevices**.
2. Watch live logs for the following breadcrumbs (all newly added in v0.1.4):
   - `[SunStat] GET /User → userId=..., defaultLocationId=..., scale=...`
   - `[SunStat] GET /Location returned N location(s)` (where N ≥ 1)
   - `[SunStat] Auto-selected locationId: <value>`
3. Confirm a child device appears under the parent in Hubitat (or existing child is found).
4. Confirm no `"Could not resolve a Watts location ID"` error in logs.

**Expected:**

- All three breadcrumb log lines appear.
- `defaultLocationId` extracted correctly from GET /User response.
- GET /Location returns at least 1 location.
- Child thermostat device appears in Hubitat.

**Pass Criteria:** Child device created; no "Could not resolve" error; all breadcrumb lines visible.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 85: Regression — `locationId = "Misty Gray"` in Preferences (Mads' Scenario) — Live Test

**What:** Verify that a user who manually set `locationId = "Misty Gray"` in preferences (the Mads scenario, pre-v0.1.4) does NOT need to reconfigure after upgrading to v0.1.4. The preference value is read, URL-encoded, and the request succeeds.

**Setup:**

- v0.1.4 driver installed on Hubitat.
- In parent device Preferences: `Watts Home location ID` = `Misty Gray` (the space-containing display name).
- Valid refresh token in state.
- Enable debug logging.

**Steps:**

1. Click **discoverDevices**.
2. Watch logs for the HTTP call to GET /Location/Misty%20Gray/Devices.
3. Confirm no `URI syntax` or `IllegalArgumentException` error.
4. Confirm the request completes (HTTP 200 if `"Misty Gray"` is a valid locationId on the server, OR a 404/error logged cleanly if not).
5. Confirm driver does NOT crash regardless of HTTP status.

**Expected:**

- URL constructed as `/Location/Misty%20Gray/Devices` (space encoded).
- No URI encoding exception thrown.
- Any server error (e.g., 404 if name isn't the actual locationId) is logged cleanly without a crash.

**Pass Criteria:** No URI exception; `%20` encoding confirmed in debug log; no unhandled crash.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 86: Negative — API Returns `errorNumber: 401` for `/Location` — Error Logging + No Silent Empty List

**What:** Verify that when the Watts API returns a non-zero `errorNumber` (e.g., `{errorNumber:401, errorMessage:"Unauthorized", body:null}`) for the GET /Location call, the driver logs the error number and message, does NOT silently treat the response as an empty location list, and (if implemented) triggers a token refresh.

**Setup:**

- This is difficult to reproduce live without a token expiry. Test via code review for the error-check path, or simulate by revoking the token in the Watts app.

**Steps (code review):**

1. Review `fetchFirstLocationId()` source.
2. Confirm the method (or `parseResponseList`) checks for `errorNumber != 0` and logs a warning or error (e.g., `log.warn "[SunStat] GET /Location API error ${errorNumber}: ${errorMessage}"`).
3. Confirm it returns `[]` (not `null`) and does not proceed as if the location list were valid.

**Steps (live — optional, requires expired token):**

1. Let the access token expire (wait, or manually corrupt `state.accessToken`).
2. Click **discoverDevices**.
3. Confirm logs show the 401 error number/message.
4. Confirm the driver attempts a token refresh (or logs a clear message that the token must be refreshed).
5. Confirm no NPE or crash.

**Expected:**

- Error number and message are logged (not silently swallowed).
- Driver does not proceed with empty location list as if the call "succeeded".
- No NPE or ClassCastException.

**Pass Criteria:** Error is logged; behavior is at least non-crashing; driver does not create spurious state from an error response.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 87: Diagnostic Breadcrumb Log — `GET /User` Info Line Visible Without Debug Toggle

**What:** Verify that the info-level breadcrumb `[SunStat] GET /User → userId=..., defaultLocationId=..., scale=...` is logged even when `logEnable = false` (debug logging OFF). This line is NOT behind the debug gate and must always appear.

**Setup:**

- v0.1.4 driver installed on Hubitat.
- In Preferences: `Enable debug logging` = OFF (unchecked).
- Valid refresh token in state; `Watts Home location ID` is blank.

**Steps:**

1. Click **discoverDevices**.
2. In the Hubitat Logs page, filter to INFO level only (hide debug messages).
3. Confirm the log line `[SunStat] GET /User → userId=..., defaultLocationId=..., scale=...` appears.
4. Confirm `userId`, `defaultLocationId`, and `scale` fields are present (not null placeholders).

**Expected:**

- Info log line appears at INFO level.
- Appears even with debug logging OFF.
- Contains meaningful values extracted from the GET /User response.

**Pass Criteria:** Log line visible at INFO level with debug OFF; all three fields populated.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 88: Diagnostic Breadcrumb Log — `GET /Location returned N location(s)` Visible Without Debug Toggle

**What:** Verify that `fetchFirstLocationId()` logs `[SunStat] GET /Location returned N location(s)` at INFO level (not behind `logEnable`) so that the location count is always visible in production.

**Setup:**

- v0.1.4 driver installed on Hubitat.
- In Preferences: `Enable debug logging` = OFF.
- Valid refresh token in state; `Watts Home location ID` is blank (so `fetchFirstLocationId` is called).

**Steps:**

1. Click **discoverDevices**.
2. In Hubitat Logs, filter to INFO level only.
3. Confirm the log line `[SunStat] GET /Location returned N location(s)` appears (where N is the actual count).
4. If N ≥ 1, also confirm `[SunStat] Auto-selected locationId: <value>` appears.

**Expected:**

- `GET /Location returned N location(s)` appears at INFO level.
- Visible without debug toggle.
- If locations found: `Auto-selected locationId:` line also appears.

**Pass Criteria:** Both INFO log lines visible with debug OFF; location count is accurate.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Execution Matrix (v0.1.4 additions)

**Priority Tier 1 (Core — must pass before v0.1.4 release):**
- Test 84: End-to-End Happy Path — Full Discovery Flow [v0.1.4]
- Test 85: Regression — `locationId = "Misty Gray"` in Preferences [v0.1.4]
- Test 78: URL Encoding — `locationId = "Misty Gray"` (Space) [v0.1.4]

**Priority Tier 2 (Feature correctness):**
- Test 72: `parseResponseBody` Unwraps Envelope [v0.1.4]
- Test 73: `parseResponseList` Unwraps Envelope [v0.1.4]
- Test 74: `parseResponseBody` Graceful Degradation [v0.1.4]
- Test 75: `parseResponseList` With API-Level Error [v0.1.4]
- Test 83: `encodePathSegment` Used in All Affected Methods — Code Review [v0.1.4]
- Test 87: Diagnostic Breadcrumb Log — GET /User [v0.1.4]
- Test 88: Diagnostic Breadcrumb Log — GET /Location [v0.1.4]

**Priority Tier 3 (Edge cases):**
- Test 76: `resp.data` Is Raw JSON String [v0.1.4]
- Test 77: `resp.data` Is Null [v0.1.4]
- Test 79: URL Encoding — UUID Verbatim [v0.1.4]
- Test 80: URL Encoding — Non-ASCII [v0.1.4]
- Test 81: URL Encoding — Literal Slashes [v0.1.4]
- Test 82: URL Encoding — Empty locationId [v0.1.4]
- Test 86: Negative — API Returns `errorNumber: 401` [v0.1.4]
