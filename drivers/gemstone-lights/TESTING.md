# Manual Test Plan — Gemstone Lights Driver

**Driver:** `gemstone-lights.groovy`  
**Test Target:** Gemstone cloud REST API  
**Platform:** Hubitat C-7 / C-8  
**Created:** 2026-05-16  
**Last Updated:** 2026-05-16  

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
