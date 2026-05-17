# Manual Test Plan — Gemstone Lights Driver

**Driver:** `gemstone-lights.groovy`  
**Test Target:** Gemstone cloud REST API  
**Platform:** Hubitat C-7 / C-8  
**Created:** 2026-05-16  
**Last Updated:** 2026-05-16T21:44:01-07:00  

---

## Prerequisites

Before running any test:

- **Hubitat hub** has internet connectivity
- **Gemstone mobile app** is already working with the same account you plan to enter in Hubitat
- **Driver code** `gemstone-lights.groovy` is saved in the Hubitat hub web UI → Drivers Code
- **Virtual device** exists and uses type **Gemstone Lights**
- **Logs** page is open in a second browser tab
- **Device page** is open in the main browser tab

**Important cloud behavior:** Gemstone's `currentlyPlaying` endpoint can lag 30–60 seconds behind a command. The driver uses optimistic Hubitat events immediately, then the next poll/refresh reconciles state.

---

## Test 1: First-Time Auth Setup

**What:** Verify Cognito login, initial device discovery, and polling setup.

**Steps:**

1. Open the device page and scroll to **Preferences**.
2. Verify these preferences exist:
   - **Gemstone account email**
   - **Gemstone account password**
   - **Polling interval**
   - **HTTPS request timeout**
   - **Enable debug logging**
3. Enter the same email/password that works in the official Gemstone app.
4. Leave **Polling interval** at `5 minutes`.
5. Leave **HTTPS request timeout** at `30`.
6. Click **Save Preferences**.
7. Watch both the device page and the logs for 15–30 seconds.

**Expected:**

- `authStatus` changes from **Authenticating** to **Authenticated: <device name>**
- No stack traces or `MissingMethodException` / `NullPointerException`
- The logs do not show `No encoder found for request content type application/x-amz-json-1.1`
- The logs may show which Gemstone cloud device the driver selected
- Clicking **Refresh** after auth succeeds populates `switch`, `level`, `hue`, `saturation`, and `effectName`

---

## Test 2: Auth Failure Visibility

**What:** Verify bad credentials fail cleanly and visibly.

**Steps:**

1. In Preferences, replace the password with an incorrect one.
2. Click **Save Preferences**.
3. Watch `authStatus` and the logs.
4. Restore the correct password.
5. Click **Save Preferences** again.

**Expected:**

- `authStatus` changes to **Auth failed — check email/password** (or another clear auth-failure message)
- A Cognito diagnostic log block shows request header/body shape plus `resp.hasError`, `resp.status`, `resp.errorMessage`, `resp.headers`, `resp.data`, and `resp.errorJson`
- The logs do not print the password, tokens, or the full Cognito client id
- After restoring the correct password, `authStatus` returns to **Authenticated: <device name>**

---

## Test 3: Switch On / Off

**What:** Verify on/off commands drive the physical lights.

**Steps:**

1. On the device page, click **ON**.
2. Observe the Hubitat tile, logs, and physical lights.
3. Wait 5–10 seconds.
4. Click **OFF**.
5. Observe again.

**Expected:**

- Hubitat updates `switch` immediately (optimistic state)
- Physical Gemstone lights turn on/off within a few seconds
- No error logs appear during normal operation
- `authStatus` remains authenticated throughout the test
- `colorMode` does **not** change just because you toggled power

---

## Test 4: Dimming / Brightness Mapping

**What:** Verify Hubitat's 0–100 level maps correctly to Gemstone's 0–255 brightness.

**Steps:**

1. Ensure the lights are ON.
2. Set level to **50**.
3. Set level to **1**.
4. Set level to **100**.
5. Set level to **0**.

**Expected:**

- Hubitat shows the requested `level` value immediately
- Physical brightness visibly changes at each step
- `setLevel(0)` turns the switch OFF
- Returning to `100` after turning back on restores full brightness
- No stack traces or repeated retries appear in logs

---

## Test 5: RGB Color Control

**What:** Verify Hubitat hue/saturation commands produce solid RGB colors and set `colorMode` to `RGB`.

**Steps:**

1. Turn the lights ON.
2. Use Hubitat's color picker (or command UI) to set:
   - **Red**
   - **Green**
   - **Blue**
   - **White** (`saturation: 0`)
3. Wait a few seconds after each selection.

**Expected:**

- `hue`, `saturation`, and `level` update immediately in Hubitat
- `effectName` becomes **Hubitat Solid Color**
- `colorMode` becomes **RGB**
- Physical lights change to the requested color
- No auth or HTTP errors appear in normal operation

---

## Test 6: Refresh / Reconciliation

**What:** Verify `refresh()` maps cleanly to Gemstone's status endpoint and infers `colorMode` from controller state.

**Steps:**

1. Change the lights from the official Gemstone app (for example: change color, play an effect, or turn them off).
2. Wait at least 60 seconds so the cloud state has time to catch up.
3. Click **Refresh** on the Hubitat device page.
4. Compare Hubitat attributes to the actual lights.

**Expected:**

- `switch`, `level`, `hue`, `saturation`, and `effectName` reconcile to the real Gemstone state
- `colorMode` becomes **EFFECTS** for active effects, **CT** for the driver's CT fallback pattern, or **RGB** for solid colors
- No crashes or malformed JSON errors appear in logs
- If you refresh immediately after a command, stale state is possible for about a minute; that is expected cloud lag, not a driver bug

---

## Test 7: Favorites-First LightEffects + `setEffect()` overloads

**What:** Verify the effect catalog loads with favorites first, dashboards see starred favorites, and both numeric + string overloads work.

**Steps:**

1. Confirm the driver is authenticated and the lights currently respond to **ON/OFF**.
2. On the device page, run **`refreshEffectCatalog()`**.
3. Watch the logs for two info lines:
   - `[Gemstone] Loaded <count> effects. Favorites (<count>): ...`
   - `[Gemstone] Other patterns (<count>): ...`
4. Verify the **`lightEffects`** attribute now starts with one or more `⭐` entries and that **`favoriteEffects`** contains the same starred names.
5. Run **`setEffect(0)`**. This should play the first favorite shown in `lightEffects`.
6. If `⭐ Pulse` is present, run **`setEffect("⭐ Pulse")`**. Otherwise, use the first starred name shown in `lightEffects`.
7. Run the same effect again **without** the prefix (for example **`setEffect("Pulse")`**).
8. Run **`setNextEffect()`**.
9. Run **`setPreviousEffect()`**.
10. Optional discoverability check: run **`setEffect("not a real effect")`** once and confirm the driver logs the full favorites-first list.

**Expected:**

- `refreshEffectCatalog()` completes asynchronously without blocking the device page
- Favorites appear first everywhere user-facing: logs, `favoriteEffects`, and `lightEffects`
- `setEffect(0)` plays the first favorite, not an `Off` placeholder
- `setEffect("⭐ Pulse")` and `setEffect("Pulse")` both resolve to the same favorite when that effect exists
- `setNextEffect()` and `setPreviousEffect()` cycle through the favorites-first index order
- `effectName` updates immediately and `colorMode` becomes **EFFECTS**
- The physical lights change to the selected Gemstone effect within a few seconds

---

## Test 8: ColorTemperature fallback + `colorMode`

**What:** Verify Kelvin control works through the RGB fallback and that mode transitions are correct.

**Steps:**

1. Run **`setColorTemperature(3000)`**.
2. Wait a few seconds and inspect Hubitat attributes.
3. Run **`setColorTemperature(2700, 80)`**.
4. Inspect the physical lights and Hubitat attributes again.
5. Run **`setColor(hue: 70, saturation: 100, level: 100)`** from the command UI or color picker.
6. Optionally click **Refresh** after a minute to confirm the mode still reconciles cleanly.

**Expected:**

- The logs warn once that Gemstone is using an RGB white-spectrum fallback because no native CCT endpoint is exposed in the current reverse-engineered API docs
- `colorTemperature` updates to the requested Kelvin value
- `colorName` maps correctly (`White` around 3000K, `Warm White` around 2700K)
- `colorMode` becomes **CT** after `setColorTemperature()`
- Physical lights change to warmer/cooler whites even though the driver is using RGB under the hood
- After `setColor(...)`, `colorMode` flips back to **RGB**

---

## Test 9: Network Timeout Handling

**What:** Verify the driver logs helpful timeout errors.

**Steps:**

1. Confirm the driver is currently authenticated and working.
2. Temporarily remove internet access from the Hubitat hub (or otherwise block outbound HTTPS).
3. Click **Refresh** or **ON**.
4. Watch the logs.
5. Restore internet access.
6. Click **Refresh** again.

**Expected:**

- The failed call logs a `log.error` message similar to:
  - `Gemstone refresh device state timed out after 30 seconds...`
  - or another clear network failure message
- The message suggests checking internet connectivity or the timeout preference
- After connectivity is restored, Refresh succeeds again without needing to recreate the device

---

## Test 10: Hub Reboot / Token Reuse

**What:** Verify the driver recovers after a hub restart.

**Steps:**

1. Reboot the Hubitat hub from **Settings**.
2. Wait for the hub to come back online.
3. Open the Gemstone device page.
4. Watch `authStatus` and logs.
5. Test **ON**, **OFF**, **Refresh**, **`setEffect(0)`**, and **`setColorTemperature(3000)`** once more.

**Expected:**

- The driver reinitializes cleanly after reboot
- `authStatus` returns to **Authenticated: <device name>** after startup
- Commands still work without re-entering credentials
- Polling resumes at the configured interval
- `refreshEffectCatalog()` / `setEffect()` / `setColorTemperature()` still work after reboot

---

## Logging Watch List

During all tests, watch for these regressions:

1. **Auth problems**
   - `authStatus` should change clearly on success/failure
   - No passwords, tokens, hashes, or management keys should ever appear in logs

2. **Unexpected HTTP statuses**
   - Look for `Unexpected HTTP 4xx/5xx ...`
   - A single HTTP `401` may be followed by a token refresh + replay; that is expected

3. **Catalog regressions**
   - Favorites should stay first
   - `lightEffects` should use stringified numeric keys (`"0"`, `"1"`, ...)
   - `favoriteEffects` should contain the starred subset only

4. **Mode regressions**
   - `setColor()` should set `colorMode` to **RGB**
   - `setColorTemperature()` should set `colorMode` to **CT**
   - `setEffect(...)` should set `colorMode` to **EFFECTS**
   - `on()` / `off()` should not change the existing `colorMode`

5. **Driver stability**
   - No stack traces, especially `MissingMethodException`, `NullPointerException`, `ClassCastException`, or JSON parse errors
   - No new forbidden Hubitat sandbox calls (`System.*`, `Thread.*`, `Runtime.*`, reflection, file I/O)

---

**Once all items are checked, v0.4.0 is ready for beta testing.**

---

## Tests 11–18: `playEffectByName` command (v0.4.1)

These tests cover the new `playEffectByName(String name)` command added in v0.4.1.
Purpose: WebCoRE's action picker does not expose overloaded capability methods with a string
input, so `playEffectByName` is added as a discrete command that delegates to `setEffect(String)`.

---

## Test 11: WebCoRE action picker visibility

**What:** Verify that `playEffectByName` appears in WebCoRE's action picker with a single STRING input, and that both commands remain visible.

**Steps:**

1. In WebCoRE (accessible from Apps on the Hubitat hub), create a new piston.
2. Click **+ New Piston → Create a blank piston**.
3. Add an action block. When prompted to select a device, choose the Gemstone Lights device.
4. In the action list, note all commands shown for the device.
5. Locate `playEffectByName` in the list and select it. Observe the input field type presented.
6. Also locate `setEffect` in the list and observe its input field type.

**Expected:**

- `playEffectByName` appears as a distinct action with a single **string** (text) input field.
- `setEffect` (the standard capability command) still appears separately; its numeric overload presents a **number** input.
- Both commands are visible in the same picker — user chooses based on whether they prefer name-based or index-based invocation.
- Neither command is missing or duplicated.

**Pass/Fail:** PASS if both commands are listed and `playEffectByName` shows a string input. FAIL if `playEffectByName` is absent or shows a numeric input.

---

## Test 12: Happy path — play effect by name

**What:** Verify that `playEffectByName("Pulse")` produces identical results to `setEffect("Pulse")`.

**Setup:** Driver is authenticated. Effect catalog has been loaded (run `refreshEffectCatalog()` if needed). "Pulse" (or substitute another known effect name) exists in `lightEffects`. Lights are ON.

**Steps:**

1. On the Hubitat device page **Commands** tab, find `playEffectByName`.
2. Enter `Pulse` in the string input field (without quotes).
3. Click the **playEffectByName** button.
4. Watch the device attributes, the physical lights, and the Logs tab.

**Expected:**

- Physical lights begin playing the Pulse effect within a few seconds.
- `effectName` attribute updates to `Pulse` (or `⭐ Pulse` if it is a favorite).
- `colorMode` becomes **EFFECTS**.
- A log line confirms the effect was activated (same message pattern as `setEffect`).
- No stack traces or error messages appear.

**Pass/Fail:** PASS if lights respond and attributes update as above. FAIL if nothing happens or an error is logged.

---

## Test 13: Happy path — starred favorite name

**What:** Verify that `playEffectByName("⭐ Pulse")` (the starred prefix form) resolves to the same effect as `playEffectByName("Pulse")`.

**Setup:** Same as Test 12. `⭐ Pulse` must exist in `favoriteEffects`; if not, substitute with any starred favorite shown in `lightEffects`.

**Steps:**

1. On the device page **Commands** tab, enter `⭐ Pulse` in the `playEffectByName` input.
2. Click **playEffectByName**.
3. Observe the physical lights, `effectName`, and logs.

**Expected:**

- Driver strips the `⭐ ` prefix and resolves to the same pattern ID as plain `Pulse`.
- Physical lights, `effectName`, and `colorMode` behave identically to Test 12.
- Log confirms the same effect was activated — no "not found" warning.

**Pass/Fail:** PASS if behavior matches Test 12. FAIL if the driver logs a "not found" error for the starred form.

---

## Test 14: Empty / null / whitespace input

**What:** Verify that invalid inputs to `playEffectByName` are rejected with the same validation behavior as `setEffect(String)`.

**Steps:**

1. On the device page **Commands** tab, enter an **empty string** (leave the field blank) and click **playEffectByName**.
2. Check logs.
3. Enter a string of spaces only (e.g., three spaces) and click **playEffectByName**.
4. Check logs.
5. If the Hubitat UI allows it, attempt to invoke `playEffectByName(null)` via Rule Machine custom action or direct device command call.

**Expected:**

- For each invalid input, the driver logs a message indicating the name must be non-empty (same message wording as `setEffect(String)` validation).
- No state change occurs: `effectName`, `colorMode`, and the physical lights remain unchanged from before each call.
- No stack traces (`NullPointerException`, `MissingMethodException`, etc.).

**Pass/Fail:** PASS if the driver rejects all three inputs cleanly. FAIL if any input causes a state change or unhandled exception.

---

## Test 15: Unknown effect name

**What:** Verify that an unrecognized name produces the same "not found" behavior as `setEffect("not a real effect")`.

**Steps:**

1. On the device page **Commands** tab, enter `not a real effect` in the `playEffectByName` input.
2. Click **playEffectByName**.
3. Inspect the Logs tab immediately after.

**Expected:**

- The driver logs a "not found" (or equivalent) message for the given name.
- The log line includes the full favorites-first effect list (identical to the diagnostic output from `setEffect("not a real effect")`) so the user can see what names are available.
- No state change: `effectName` and `colorMode` are unchanged, physical lights are unaffected.

**Pass/Fail:** PASS if the not-found log appears with the effect list. FAIL if the driver crashes, silently does nothing, or changes state.

---

## Test 16: Catalog refresh trigger

**What:** Verify that `playEffectByName` triggers a catalog refresh and replays when the catalog is stale or missing — same as `setEffect(String)`.

**Setup:** Either (a) wait more than 1 hour after the last `refreshEffectCatalog()` call so the catalog is considered stale, or (b) force staleness by clearing state (hub reboot then skip the initial catalog load).

**Steps:**

1. Confirm the effect catalog is stale or absent (check logs for the last catalog load timestamp, or confirm `lightEffects` is empty).
2. On the device page, run `playEffectByName("Pulse")` (or any known effect name).
3. Watch the Logs tab.

**Expected:**

- The driver recognizes the stale/missing catalog and queues the effect name for replay.
- A catalog refresh request is issued immediately.
- After the catalog loads, the queued `playEffectByName("Pulse")` executes automatically.
- `effectName`, `colorMode`, and physical lights update as in Test 12.
- Log sequence mirrors the behavior of `setEffect(String)` under the same stale-catalog condition.

**Pass/Fail:** PASS if the effect plays after the automatic refresh with no manual retry needed. FAIL if the driver throws an error or never activates the effect.

---

## Test 17: Both commands work in the same WebCoRE piston (regression)

**What:** Verify that `setEffect(0)` and `playEffectByName("Pulse")` can coexist in the same piston without interfering with each other.

**Setup:** WebCoRE is installed and working. Both effects are available in the catalog.

**Steps:**

1. In WebCoRE, create a new piston with two sequential actions on the Gemstone device:
   - **Action 1:** `setEffect(0)` — plays the first favorite by index.
   - **Action 2:** Wait 5 seconds.
   - **Action 3:** `playEffectByName("Pulse")` — plays Pulse by name.
2. Run the piston manually (or trigger it).
3. Watch the physical lights, `effectName`, and logs.

**Expected:**

- Action 1 activates the first favorite effect; `effectName` and `colorMode` update.
- After the wait, Action 3 activates Pulse; `effectName` and `colorMode` update again.
- Each invocation produces its own clean log line with no errors.
- No interference or state corruption between the two commands.

**Pass/Fail:** PASS if both execute correctly in sequence. FAIL if either command errors out or state is corrupted.

---

## Test 18: From Rule Machine (regression, non-WebCoRE)

**What:** Verify that `playEffectByName` is accessible and functional from Hubitat Rule Machine.

**Steps:**

1. In Hubitat, open **Apps → Rule Machine → Create New Rule**.
2. Give the rule a name (e.g., "Test playEffectByName").
3. Add a **Trigger** of your choice (e.g., a virtual switch turning on).
4. Under **Actions**, choose **Custom action**.
5. Select the Gemstone Lights device.
6. In the command list, select `playEffectByName`.
7. Enter `Pulse` as the parameter value.
8. Save the rule and trigger it.
9. Observe the physical lights, `effectName`, and logs.

**Expected:**

- `playEffectByName` appears in Rule Machine's custom-action command list.
- After the trigger fires, lights play the Pulse effect.
- `effectName` updates to `Pulse` (or `⭐ Pulse`) and `colorMode` becomes **EFFECTS**.
- Log confirms execution — identical to direct invocation from the device page.
- No errors or uncaught exceptions in the Hubitat log.

**Pass/Fail:** PASS if the command is visible in RM and executes correctly. FAIL if the command is absent from RM or produces an error.

---

**Once all items are checked, v0.4.1 is ready for beta testing.**
