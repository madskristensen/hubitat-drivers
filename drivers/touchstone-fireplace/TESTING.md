# Manual Test Plan — Touchstone / Tuya Fireplace Driver

**Driver:** `touchstone-fireplace.groovy`
**Test Target:** Touchstone Sideline Elite (and other Tuya WiFi fireplaces)
**LAN Protocol:** Tuya Local v3.3 · AES-128-ECB · TCP port 6668
**Platform:** Hubitat C-7 / C-8
**Created:** 2026-05-17T15:50:06-07:00
**Status:** DRAFT — covers v0.1.5 baseline + v0.1.6 new commands (`setFlameSpeed`, `setLogBrightness`)

---

## Pre-flight Checklist

Complete every item before running any test. Skipping these is the most common source of false failures.

- [ ] **Smart Life / Tuya app is CLOSED on every phone and tablet on the network.**
  The Tuya v3.3 module allows exactly ONE TCP connection at a time. If the app is open it owns the slot, and every driver command will time out immediately. This is device firmware behavior, not a driver bug.
- [ ] **Fireplace is on its wall power switch or breaker.** The Tuya WiFi module needs power even when the fireplace is "off" via the app.
- [ ] **Device IP is reachable from the Hubitat hub:**
  - From a computer on the same LAN, run `ping 192.168.1.38` (substitute your device IP). Four consecutive replies = good.
  - If ping fails, check your router's DHCP leases and verify the fireplace got an IP. Set a DHCP reservation so the IP never changes.
- [ ] **Local key is correct (16 characters, hex):**
  - Obtained via Tuya IoT Cloud Portal + tinytuya wizard, or via Home Assistant tuya-local integration.
  - Confirm `devices.json` from tinytuya shows `"local_key": "<16-char-string>"` for your device.
  - A wrong local key causes AES decryption failures — the driver will show `online = offline` with no clear auth error.
- [ ] **Device ID matches the fireplace** (from `devices.json` or Tuya IoT portal).
- [ ] **Driver code is saved** in Hubitat web UI → Drivers Code.
- [ ] **Two browser tabs open:** device page in one, **Logs** page in the other.
- [ ] **Debug logging is ON** for the duration of testing (Preferences → Enable debug logging).

---

## Test Area: Install / Lifecycle

### Test 1: Fresh Install — Enter Credentials, Verify First Poll

**What:** Verify driver installs cleanly, accepts credentials, connects to the device, and populates attributes on first save.

**Pre-conditions:** Driver code saved. No existing device for this fireplace. Smart Life app closed.

**Steps:**

1. In Hubitat → **Devices** → **Add Device** → **Virtual** → type **Touchstone / Tuya Fireplace** → click **Create Device**.
2. Open the new device page.
3. Scroll to **Preferences** and verify these fields exist:
   - **Device IP address**
   - **Device ID**
   - **Local key (16 chars)** (shown as a password field)
   - **Device Profile** (default: Sideline Elite (tested))
   - **Preferred setpoint / temperature unit**
   - **Polling interval** (default: 60 seconds)
   - **Enable debug logging**
   - **Enable descriptionText (info) logging**
4. Enter your device IP, Device ID, and local key.
5. Leave Device Profile as **Sideline Elite (tested)**.
6. Leave polling interval at **60 seconds**.
7. Click **Save Preferences**.
8. Watch the Logs page for 10–15 seconds.

**Expected:**

- Logs show `Touchstone / Tuya Fireplace v0.1.x installed` followed by `initialize()` being called.
- Within ~5 seconds, `online` attribute changes from `unknown` to `online`.
- `temperature` populates with a real room temperature reading (e.g., 72°F or similar).
- `switch` attribute appears (`on` or `off` reflecting current physical state).
- `heatLevel` attribute appears (`off`, `low`, or `high`).
- `heatingSetpoint` populates with the device's saved setpoint.
- No stack traces, `MissingMethodException`, or `NullPointerException` in logs.
- No `paragraph() not allowed` error (this would indicate a pre-v0.1.5 code was pasted).

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 2: `updated()` After Changing Preferences — No Duplicate Timers

**What:** Verify that saving preferences re-initializes cleanly without orphaned polling schedules.

**Pre-conditions:** Device is installed and `online = online`.

**Steps:**

1. Note the current `temperature` attribute value and the time.
2. Open **Preferences** and change the **Polling interval** from 60 seconds to **30 seconds**.
3. Click **Save Preferences**.
4. Watch the Logs page.
5. Wait 90 seconds and observe how many poll cycles occur in the logs.
6. Change polling interval back to **60 seconds** and save again.

**Expected:**

- Logs show `preferences updated` and then `initialize()`.
- The old polling schedule is cancelled before the new one starts — no double-firing (i.e., polls happen at roughly 30-second intervals, not every 5–10 seconds).
- `online` attribute remains `online` throughout.
- Attributes continue updating after the preference change.
- Toggling **Enable debug logging** alone (no other changes) does NOT clear state or force a full reconnect beyond a standard `initialize()`.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 3: Remove and Re-add Device — Clean State

**What:** Verify that deleting and re-adding the device leaves no phantom state.

**Pre-conditions:** Device is working.

**Steps:**

1. From the device page, scroll to the bottom and click **Delete Device**. Confirm the deletion.
2. Watch the Logs page for `uninstalled()` message.
3. Wait 30 seconds. Confirm no more log lines appear from the deleted device.
4. Re-add the device following Test 1 steps.
5. Verify first poll succeeds again.

**Expected:**

- Logs show a clean `uninstalled()` message after deletion.
- No phantom polling log lines appear after deletion (confirms schedules were cancelled).
- Re-add succeeds identically to Test 1.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

## Test Area: Happy-Path Commands

### Test 4: `on()` / `off()` — Switch and Physical State

**What:** Verify the fireplace physically turns on and off, and the `switch` attribute reflects the correct state.

**Pre-conditions:** Device is `online`. Physical fireplace is visible.

**Steps:**

1. Note the current `switch` value.
2. If `switch = off`, click **ON** on the device page. If `switch = on`, click **OFF** first.
3. Observe: Hubitat attribute update, logs, and the physical fireplace.
4. Wait 5 seconds.
5. Click the opposite command.
6. Observe again.

**Expected:**

- `switch` attribute updates immediately (before device confirms) — this is the driver's optimistic state update.
- Physical fireplace turns on or off within 2–4 seconds.
- Logs show `[Touchstone] <device name> switch → on` / `switch → off`.
- `online` remains `online` after both commands.
- After powering on, `flameColor`, `flameBrightness`, and `logColor` attributes should reflect device state within ~8 seconds (the post-power-on refresh delay).
- After powering off, a follow-up status poll reconciles all attributes.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 5: `setFlameColor(color)` — Cycle All Six Palette Options

**What:** Verify all six flame color/effect options produce visible changes on the fireplace.

**Pre-conditions:** Fireplace is ON. `online = online`. Device Profile = Sideline Elite.

**Steps:**

For each value in `["1", "2", "3", "4", "5", "6"]`:

1. On the device page, run **`setFlameColor`** with the value.
2. Observe the `flameColor` attribute and the physical flame.
3. Wait 3 seconds between each.

Known mappings (verify on hardware):

| Value | Expected flame color |
|---|---|
| `"1"` | Orange |
| `"2"` | Blue |
| `"3"` | Yellow |
| `"4"` | Orange + Blue |
| `"5"` | Orange + Yellow |
| `"6"` | Blue + Yellow |

**Expected:**

- `flameColor` attribute updates immediately for each value.
- Physical flame visibly changes color/effect for each value. [verify on hardware]
- Logs show `flame color → <value>` for each call.
- No errors or `null` DP warnings.
- Setting `"7"` or `""` logs a warning and does NOT change the attribute. [verify empty/out-of-range behavior]

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 6: `setFlameBrightness(level)` — Cycle All Five Levels

**What:** Verify flame brightness responds to all five levels.

**Pre-conditions:** Fireplace is ON. `online = online`. Device Profile = Sideline Elite.

**Steps:**

For each value in `["1", "2", "3", "4", "5"]`:

1. Run **`setFlameBrightness`** with the value.
2. Observe the `flameBrightness` attribute and the physical flame brightness.
3. Wait 3 seconds between each.

Known mappings (verify on hardware):

| Value | Expected brightness |
|---|---|
| `"1"` | 20% (dim) |
| `"2"` | 40% |
| `"3"` | 60% |
| `"4"` | 80% |
| `"5"` | 100% (full) |

**Expected:**

- `flameBrightness` updates immediately for each value.
- Visible brightness change on the physical fireplace at each step. [verify on hardware]
- No errors for any valid value.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 7: `setLogColor(color)` — Cycle All Twelve Palette Options

**What:** Verify log/ember color responds to all twelve options.

**Pre-conditions:** Fireplace is ON. `online = online`. Device Profile = Sideline Elite.

**Steps:**

For each value in `["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"]`:

1. Run **`setLogColor`** with the value.
2. Observe the `logColor` attribute and the physical log/ember color.
3. Wait 3 seconds between each.

Known mappings (verify on hardware):

| Value | Expected log/ember color |
|---|---|
| `"1"` | Orange |
| `"2"` | Red |
| `"3"` | Blue |
| `"4"` | Yellow |
| `"5"` | Green |
| `"6"` | Purple |
| `"7"` | Teal |
| `"8"` | Pink |
| `"9"` | White |
| `"10"` | Peach Puff |
| `"11"` | Black (off?) |
| `"12"` | Grey |

**Expected:**

- `logColor` updates immediately for each value.
- Visible ember color change on the physical fireplace for most values. [verify on hardware — values 11 and 12 may be subtle]
- No errors for any valid value.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 8: `setFlameSpeed(speed)` — NEW IN v0.1.6 — Cycle All Enum Values

**What:** Verify the new `setFlameSpeed` command (Tank's v0.1.6 addition; uses DP 103) produces visible animation speed changes.

**Pre-conditions:** v0.1.6 driver is installed. Fireplace is ON. `online = online`.

**Note:** In v0.1.5 and earlier, DP 103 is surfaced only as the raw `dp103` attribute with no dedicated command. If you are testing v0.1.5, skip this test and use Test 28 (DP 103 raw attribute) instead.

**Steps:**

1. Confirm the **`setFlameSpeed`** command appears on the device page Commands tab. If it does not appear, the v0.1.6 update has not been applied yet.
2. Note the current flame animation speed visually.
3. Cycle through each enum value shown in the command's dropdown (exact values TBD in v0.1.6; likely `"1"` through `"N"`). [verify enum range in v0.1.6]
4. For each value, observe the `flameSpeed` attribute (or `dp103` if attribute name differs in v0.1.6) and the physical animation speed.
5. Wait 3 seconds between each value.

**Expected:**

- `setFlameSpeed` command is visible in the Commands tab. [verify in v0.1.6]
- The driver attribute for flame speed updates immediately for each value. [verify attribute name in v0.1.6]
- Visible change in flame animation speed on the physical fireplace. [verify on hardware]
- Logs show the flame speed DP write (DP 103) succeeding.
- No errors for any valid enum value.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 9: `setLogBrightness(level)` — NEW IN v0.1.6 — Cycle All 12 Levels

**What:** Verify the new `setLogBrightness` command (Tank's v0.1.6 addition; uses DP 105) changes the log/ember brightness visibly.

**Pre-conditions:** v0.1.6 driver is installed. Fireplace is ON. `online = online`.

**Note:** In v0.1.5 and earlier, DP 105 is surfaced only as the raw `dp105` attribute with no dedicated command. If you are testing v0.1.5, skip this test and use Test 28 (DP 105 raw attribute) instead.

**Steps:**

1. Confirm **`setLogBrightness`** appears on the device page Commands tab. If it does not appear, the v0.1.6 update has not been applied yet.
2. Note the current log ember brightness visually.
3. Cycle through all 12 levels shown in the command dropdown. [verify enum values/labels in v0.1.6]
4. For each level, observe the `logBrightness` attribute (or `dp105` if attribute name differs) and the physical log/ember brightness.
5. Wait 3 seconds between each value.

**Expected:**

- `setLogBrightness` command is visible in the Commands tab. [verify in v0.1.6]
- The driver attribute for log brightness updates immediately for each level. [verify attribute name in v0.1.6]
- Visible brightness change in the log/ember section on the physical fireplace across the full range. [verify on hardware]
- Logs show the DP 105 write succeeding.
- No errors for any valid level.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 10: `setHeatingSetpoint(temp)` — Verify Thermostat Behavior

**What:** Verify the temperature setpoint command writes the correct DP and that the heater behaves correctly.

**Pre-conditions:** Device is ON. `online = online`. Preferred unit is Fahrenheit. `heatLevel` is `low` or `high`.

**Important:** The heater is binary — it either runs at the selected heat level or it does not. The setpoint tells the device at what room temperature to stop running; it does not directly control the flame appearance.

**Steps:**

1. Note the current `heatingSetpoint` value.
2. Run **`setHeatingSetpoint(72)`**.
3. Observe the `heatingSetpoint` attribute and logs.
4. Compare the Hubitat setpoint to what the Smart Life app shows (close the app again after checking).
5. Run **`setHeatingSetpoint(75)`** and observe.
6. Try an out-of-range value: **`setHeatingSetpoint(50)`** (below the 67°F device minimum).
7. Try **`setHeatingSetpoint(95)`** (above the 88°F device maximum).

**Expected:**

- `heatingSetpoint` updates to 72, then 75 in the Hubitat attribute.
- The Tuya module accepts the value; logs show `heating setpoint → 72°F` and `heating setpoint → 75°F`.
- The physical thermostat behavior: if room temperature > setpoint, heater turns off; if room temp < setpoint, heater runs. [verify on hardware — may take several minutes to trigger]
- Out-of-range values (50, 95) are clamped by the driver to the valid range (67–88°F) or logged as warnings. [verify clamping behavior on hardware]
- No stack traces.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 11: `setHeatLevel(level)` — Off / Low / High

**What:** Verify the three heat level states work correctly.

**Pre-conditions:** Device is ON. `online = online`.

**Steps:**

1. Run **`setHeatLevel("off")`**.
2. Observe `heatLevel` attribute, logs, and physical heater (wait 15 seconds for physical confirmation).
3. Run **`setHeatLevel("low")`**.
4. Observe again.
5. Run **`setHeatLevel("high")`**.
6. Observe again.
7. Run **`setHeatLevel("INVALID")`** (uppercase invalid value).

**Expected:**

- `heatLevel` attribute updates immediately for each valid value.
- Physical heater responds to `low` and `high` (may take 10–20 seconds to feel warm air; verify the fan runs). [verify on hardware]
- `setHeatLevel("off")` stops the heater fan.
- `setHeatLevel("INVALID")` logs `setHeatLevel: invalid level 'INVALID' — use off, low, or high` and does NOT update `heatLevel`.
- The heater does NOT auto-start when `on()` is called — only explicit `setHeatLevel()` changes the heater.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 12: `refresh()` — All Attributes Reconcile to Device State

**What:** Verify `refresh()` retrieves current state from the device and updates all attributes accurately.

**Pre-conditions:** Device is ON. `online = online`.

**Steps:**

1. Use the Smart Life app to change a setting (e.g., change flame color to something different than what Hubitat shows). Close the app immediately after.
2. Wait 30 seconds without pressing Refresh (to let the app release the TCP slot).
3. On the Hubitat device page, click **Refresh**.
4. Observe all attributes and logs.
5. Compare Hubitat attribute values to the current physical device state.

**Expected:**

- Logs show a status query being sent and a response received.
- `flameColor`, `flameBrightness`, `logColor`, `heatLevel`, `heatingSetpoint`, `temperature`, `switch`, and `online` all reflect the actual current device state.
- The change made via the app is now visible in Hubitat.
- Response arrives within 5 seconds of Refresh.
- `online` updates to `online` after a successful response.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

## Test Area: Power-On Defaults Timing

### Test 13: Defaults Apply ~1.5s After `on()`

**What:** Verify that power-on default preferences (flame color, log color, flame brightness, heating setpoint) are applied approximately 1.5 seconds after the `on()` command.

**Pre-conditions:** Device is OFF. At least one default is configured in Preferences (e.g., `defaultFlameColor = "2"`).

**Steps:**

1. Configure at least one default in Preferences: set **Default flame color** to **`"2"`** (Blue).
2. Click **Save Preferences**.
3. Click **OFF** on the device page (ensure the device is off).
4. Click **ON**.
5. Watch the Logs page at high attention.

**Expected:**

- Logs show `switch → on` immediately.
- Approximately 1.5 seconds later, logs show `flame color → 2` (or whichever default was configured).
- The physical fireplace starts with its default (typically orange), then transitions to blue ~1.5 seconds after power-on. [verify on hardware — subtle timing may be hard to catch visually]
- `flameColor` attribute updates to `"2"` after the default is applied.
- The default heating setpoint (if configured) is also applied after the delay.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 14: `on()` Followed Immediately by `off()` — Defaults Are Cancelled

**What:** Verify that calling `off()` within the 1.5-second defaults window cancels the pending default writes.

**Pre-conditions:** Device is OFF. At least one power-on default is configured (same setup as Test 13).

**Steps:**

1. Click **ON** on the device page.
2. Immediately (within 1 second) click **OFF**.
3. Watch the Logs page.

**Expected:**

- Logs show `switch → on` immediately followed by `switch → off`.
- The power-on defaults are NOT sent (no `flame color → 2` or other default log lines appear).
- Physical fireplace may briefly power on then off, or may not start at all depending on command delivery timing. [verify on hardware]
- No errors in logs. The command queue processes the off() cleanly.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 15: Explicit Command Within Defaults Window Wins

**What:** Verify that an explicit `setFlameColor()` command issued within the 1.5-second window after `on()` takes priority over the scheduled default.

**Pre-conditions:** Device is OFF. `defaultFlameColor` is set to `"2"` (Blue) in Preferences.

**Steps:**

1. Click **ON** on the device page.
2. Within 1 second, run **`setFlameColor("5")`** (Orange + Yellow).
3. Watch the Logs page and the physical flame.

**Expected:**

- The logs show `switch → on`, then `flame color → 5` (the explicit command).
- Approximately 1.5 seconds after on(), the default (`flame color → 2`) fires as well, overwriting the explicit command. [verify on hardware — note: the driver's current architecture sends defaults as a separate queued write; the explicit command and the default may both execute in sequence]
- OR — if the driver is smarter about detecting the explicit command cancels the default — only `flame color → 5` fires and no default write appears.
- Determine which behavior actually occurs and document it here for the README.
- Physical flame color reflects whichever command won.

**Actual:**
(Fill in during testing)

**Status:**
(Pending — behavior needs hardware validation to determine which command wins)

---

## Test Area: Settings Validation Edge Cases

### Test 16: Empty IP Address

**What:** Verify the driver logs a clear error when Device IP is blank.

**Pre-conditions:** Device is installed and working.

**Steps:**

1. Open Preferences and clear the **Device IP address** field (leave it blank).
2. Click **Save Preferences**.
3. Watch the Logs page.
4. Click **Refresh** or wait for a poll cycle.
5. Restore the correct IP and save.

**Expected:**

- Logs show a message like `Waiting for device IP, device ID, and a 16-character local key` (the configuration-incomplete warning).
- `online` changes to `unknown`.
- No socket connection attempt is made; no TCP errors.
- After restoring the correct IP and saving, the driver reinitializes and `online` returns to `online`.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 17: Malformed IP Address (Truncated)

**What:** Verify the driver handles a syntactically invalid IP gracefully.

**Pre-conditions:** Device is installed and working.

**Steps:**

1. Open Preferences and replace Device IP with `192.168.1` (truncated — missing the last octet).
2. Click **Save Preferences**.
3. Watch the Logs page.
4. Click **Refresh**.
5. Restore the correct IP and save.

**Expected:**

- The driver attempts a TCP connection and either:
  - Logs a socket error (e.g., connection refused / unresolved host) and retries with exponential backoff (5s, 15s, 30s delays).
  - OR validates the IP format itself and logs a clear warning without attempting a connection.
- `online` changes to `offline` or `unknown`.
- No stack traces.
- Recovery after restoring the correct IP: driver reinitializes and `online` returns to `online` within ~10 seconds. [verify on hardware]

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 18: Wrong Local Key (16 Chars, Wrong Value)

**What:** Verify AES authentication failure is surfaced usefully when the local key is plausible but incorrect.

**Pre-conditions:** Device is installed and working.

**Steps:**

1. Note the current (correct) local key.
2. Open Preferences and change the local key to any other 16-character string (e.g., `aaaaaaaaaaaaaaaa`).
3. Click **Save Preferences**.
4. Click **Refresh**.
5. Watch the Logs page for 15–20 seconds.
6. Restore the correct local key and save.

**Expected:**

- The driver sends a command, receives a garbled/invalid response (AES decryption fails), and logs a warning or error.
- `online` may temporarily show `offline` or the driver may silently retry (depending on how the device responds to a bad key). [verify on hardware — Tuya v3.3 devices often just ignore badly-keyed frames]
- No crash; driver remains in a stable retry loop.
- After restoring the correct key and saving, `online` returns to `online` within ~10 seconds.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 19: Mismatched Device ID

**What:** Verify the driver fails gracefully when Device ID doesn't match the device being contacted.

**Pre-conditions:** Device is installed and working.

**Steps:**

1. Open Preferences and change **Device ID** to a string of similar length but different characters (e.g., swap the last 4 hex digits).
2. Click **Save Preferences**.
3. Click **Refresh**.
4. Watch the Logs page.
5. Restore the correct Device ID and save.

**Expected:**

- The device either ignores the command (mismatched devId in the Tuya frame) or returns an error code.
- The driver logs a warning or error; `online` may go to `offline`.
- No stack traces.
- After restoring the correct Device ID, driver recovers. [verify on hardware — behavior may vary by device firmware]

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 20: Trailing Whitespace in IP Address

**What:** Verify the driver tolerates (or cleanly rejects) an IP address entered with a trailing space.

**Pre-conditions:** Device is installed and working.

**Steps:**

1. Open Preferences and change Device IP to `192.168.1.38 ` (add one trailing space — copy-paste errors often introduce this).
2. Click **Save Preferences**.
3. Click **Refresh**.
4. Watch the Logs page.

**Expected:**

- Either: the driver trims whitespace internally and connects normally (`online = online`).
- Or: connection fails with a socket error, which is at least a clear failure rather than a silent one.
- Preferred outcome: trimming happens and connection succeeds. [verify on hardware — whether the driver calls `.trim()` on the IP]

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

## Test Area: Recovery Scenarios

### Test 21: Network Cable Pulled Mid-Command

**What:** Verify the driver detects a dropped connection, logs it clearly, retries with backoff, and recovers when connectivity returns.

**Pre-conditions:** Device is ON. `online = online`. Hub has a wired LAN connection to the same switch as the fireplace (for easy interruption test), OR the fireplace is on WiFi and you can temporarily block its traffic at the router/AP.

**Steps:**

1. Disconnect the LAN cable from the Hubitat hub (or block the fireplace's WiFi at your router) while the device is idle.
2. Immediately run **`setFlameColor("3")`** from the device page.
3. Watch the Logs page.
4. Observe the retry sequence (should be: first retry at 5s, second at 15s, third at 30s).
5. After the third retry attempt, reconnect the cable / unblock WiFi.
6. Wait up to 60 seconds (next poll cycle).
7. Observe whether the command is retried and the device recovers.

**Expected:**

- Logs show a socket error within 5 seconds of the connection being dropped.
- `online` changes to `offline`.
- Driver retries with delays of approximately 5 seconds, 15 seconds, then 30 seconds.
- After reconnection, the next poll or retry attempt succeeds.
- `online` returns to `online`.
- The `setFlameColor("3")` command either executes (if it was retried after reconnect) or is dropped with a log message. [verify on hardware — whether the command queue persists across the retry cycle]
- No infinite tight retry loop (log lines do not fire every second continuously).

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 22: Power-Cycle the Fireplace

**What:** Verify the driver recovers and reconciles state after the fireplace loses power and reboots.

**Pre-conditions:** Device is ON. `online = online`.

**Steps:**

1. Note the current `flameColor` and `heatLevel` values.
2. Turn off the wall power switch / breaker for the fireplace (hard power-cut — not via the on() command).
3. Watch the Logs page. The next poll will fail.
4. Observe `online` changing to `offline` and backoff retries.
5. After 30–60 seconds, restore wall power.
6. Wait up to 60 seconds (next poll cycle).
7. Observe the driver reconnecting and re-reading device state.

**Expected:**

- After power is cut, the next poll attempt logs a socket error and sets `online = offline`.
- Driver retries with the 5s/15s/30s backoff (does NOT retry every second; should not spam logs).
- After power is restored, the fireplace reboots its Tuya module (~20–30 seconds). [verify on hardware — boot time varies]
- The next poll or retry attempt after the fireplace reboots succeeds.
- `online` returns to `online`.
- All attributes (`switch`, `temperature`, `heatLevel`, etc.) reconcile to the fireplace's power-on state.
- Note: `heatingSetpoint` may revert to the firmware's default (67°F) on power cycle — this is device firmware behavior, not a driver bug. [verify on hardware]

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 23: Smart Life App Connects (TCP Slot Stolen)

**What:** Verify the driver detects when the Smart Life app takes the TCP connection, logs it clearly, and recovers when the app closes.

**Pre-conditions:** Device is ON and connected. `online = online`. Smart Life app is closed on all devices.

**Steps:**

1. From Hubitat, run **`setFlameColor("1")`**. Verify it succeeds.
2. Open the **Smart Life app** on a phone while Hubitat is actively trying to command or poll the fireplace. (The timing needs to be tight — if possible, initiate a command from Hubitat, then immediately open Smart Life while the command is in flight.)
3. Alternatively: just open Smart Life and keep it in the foreground, then try sending a command from Hubitat.
4. Watch the Logs page.
5. Close the Smart Life app on the phone.
6. Wait 30–60 seconds.
7. Run another command from Hubitat and observe.

**Expected:**

- When the app holds the socket, Hubitat commands fail with a socket error or timeout.
- Logs show `Another Tuya client may still own the single TCP slot` (or similar wording).
- `online` goes to `offline` or stays in retry state.
- Retries fire at the standard backoff intervals.
- After the app is closed, the Tuya module releases the slot (usually within a few seconds of the app going to background on iOS/Android).
- The driver's next retry or poll succeeds.
- `online` returns to `online`.
- No crash, no hanging, no manual recovery needed.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 24: Hub Reboot — Auto-Initialize on Startup

**What:** Verify the driver re-establishes connection and resumes polling automatically after a hub reboot.

**Pre-conditions:** Device is ON. `online = online`.

**Steps:**

1. Note the current attribute state (write it down or take a screenshot).
2. Reboot the Hubitat hub from **Settings** → **Reboot Hub**. Wait for it to come fully back online (typically 2–3 minutes).
3. Open the device page and the Logs page.
4. Watch for `initialize()` to fire automatically.
5. Run **`on()`**, **`off()`**, **`refresh()`**, **`setFlameColor("1")`**, and **`setHeatLevel("off")`** to confirm full functionality.

**Expected:**

- After hub restart, the driver fires `initialize()` automatically (via the `Initialize` capability).
- `online` transitions from `unknown` → `online` within ~10 seconds.
- Polling resumes at the configured interval without re-entering credentials.
- All commands work normally without needing to re-save preferences.
- All attributes repopulate after the first poll.
- Logs show `initialize() called` and a successful status query response.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

## Test Area: Discovery Workflow (Advanced)

### Test 25: `discoverDPs()` — Print All Current DP Values

**What:** Verify that `discoverDPs()` logs all current DP values from the device in a readable format.

**Pre-conditions:** `online = online`. Debug logging ON.

**Steps:**

1. On the device page, click **`discoverDPs`**.
2. Watch the Logs page.

**Expected:**

- Logs show a DP query being sent to the device.
- Within 5 seconds, logs show all current DP values as key-value pairs (e.g., `DP 1 = true`, `DP 2 = 22`, `DP 101 = "1"`, etc.).
- At minimum, DPs 1, 2, 3, 5, 13, 14, 15, 101, 102, 103, 104, 105, 107, 108 should appear (on Sideline Elite). [verify on hardware — DPs may vary if device is off]
- No crash; the command is safe to run any number of times.
- The output is useful for a user trying to map an unknown Touchstone model.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 26: `captureBaseline()` → Button Press → `captureDiff()` — DP Mapping Workflow

**What:** Verify the baseline/diff workflow helps identify which DPs are controlled by physical remote buttons.

**Pre-conditions:** `online = online`. Physical IR remote for the fireplace is available.

**Steps:**

1. On the device page, click **`captureBaseline`**.
2. Watch the Logs page for `baseline captured` or similar.
3. On the physical remote, press one button (e.g., the "Flame Color" cycle button).
4. Wait 2 seconds.
5. On the device page, click **`captureDiff`**.
6. Watch the Logs page.
7. Repeat with a different remote button.

**Expected:**

- `captureBaseline()` logs that a baseline snapshot was taken (stores current DP values in driver state).
- After pressing the remote button, the device firmware updates one or more DPs.
- `captureDiff()` queries the device, compares the result to the baseline, and logs only the DPs that changed (e.g., `DP 101 changed: "1" → "2"`).
- If no button was pressed between capture and diff, the diff logs `no changes detected`.
- Warning shown if `captureDiff()` is called before `captureBaseline()`.
- This workflow is useful for identifying DPs that the physical remote controls but the driver doesn't yet know about.
- Note: Physical remote buttons for log brightness, flame speed, and timer do NOT trigger Tuya DP updates on all Touchstone models — the remote may be IR-only with no state feedback to the WiFi module. [verify on hardware]

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 27: `setRawDP(dpId, value)` — Direct Write for Experimentation

**What:** Verify the raw DP write command works for manual experimentation.

**Pre-conditions:** `online = online`. Know what DP you want to test (e.g., DP 103 for flame speed).

**Steps:**

1. Run **`setRawDP(103, "2")`** to write value `"2"` to DP 103.
2. Observe the Logs page.
3. Run **`refresh()`** and check the `dp103` attribute.
4. Try **`setRawDP(0, "true")`** (invalid dpId = 0).
5. Try **`setRawDP(1, "true")`** (power DP — should turn fireplace on or off).

**Expected:**

- `setRawDP(103, "2")` sends the write and logs `[Touchstone][RawDP] Writing DP 103="2" (String)`.
- The `dp103` attribute updates after the next refresh.
- `setRawDP(0, "true")` logs `dpId must be a positive integer` and does NOT send anything.
- `setRawDP(1, "true")` logs the raw write and the fireplace responds (power state changes). [verify on hardware — use caution if heater is running]

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

## Test Area: DP 108 Child Lock (Parsed, Not Yet Commanded)

### Test 28: `dp108` Attribute — Child Lock State Read

**What:** Verify that DP 108 (child lock on Sideline Elite firmware) is read and surfaced as the `dp108` attribute, even though there is no `setChildLock()` command yet.

**Pre-conditions:** `online = online`. Child lock feature may or may not be accessible without the physical remote.

**Steps:**

1. On the device page, click **Refresh**.
2. Look for the `dp108` attribute in the Current States section.
3. If the physical remote has a child lock button, press it, wait 5 seconds, then click Refresh again.
4. Observe whether `dp108` changes.

**Expected:**

- `dp108` attribute is visible on the device page after a successful poll.
- The value is `"true"` (locked) or `"false"` (unlocked) as a string. [verify on hardware]
- If the remote button changes child lock state, `dp108` updates on the next poll.
- There is NO `setChildLock()` command visible on the device page — this is expected in v0.1.5 and v0.1.6. The attribute is read-only from Hubitat for now.

**Note for community testers:** If you need to programmatically set child lock, use `setRawDP(108, "true")` or `setRawDP(108, "false")`. [verify DP 108 boolean write works on hardware]

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 29: `dp103` and `dp105` Raw Attributes (v0.1.5 and Earlier)

**What:** Verify DP 103 (flame speed) and DP 105 (log brightness) appear as raw attributes when `setFlameSpeed` and `setLogBrightness` commands are not yet available.

**Note:** This test applies to v0.1.5 and earlier only. In v0.1.6, both DPs gain dedicated commands (`setFlameSpeed`, `setLogBrightness`). Skip this test if running v0.1.6+.

**Pre-conditions:** v0.1.5 or earlier driver installed. `online = online`.

**Steps:**

1. Click **Refresh**.
2. Look for `dp103` and `dp105` in the Current States section of the device page.
3. Note their current values.
4. Use `setRawDP(103, "2")` to change flame speed and click Refresh.
5. Use `setRawDP(105, "3")` to change log brightness and click Refresh.
6. Confirm the attribute values update.

**Expected:**

- `dp103` and `dp105` are visible in Current States after a successful poll.
- Writing via `setRawDP()` changes the values and they reflect in the attribute after the next refresh.
- Physical fireplace animation speed (`dp103`) and log brightness (`dp105`) change visibly. [verify on hardware]

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

## Test Area: Device Profile Variants

### Test 30: Device Profile = "Generic Tuya Fireplace"

**What:** Verify the Generic profile limits commands to power, heat level, and temperature only (no LED controls).

**Pre-conditions:** Device is installed. You can test with the Sideline Elite — the Generic profile intentionally maps fewer DPs.

**Steps:**

1. Open Preferences and change **Device Profile** to **Generic Tuya Fireplace**.
2. Click **Save Preferences**.
3. On the Commands tab, observe which commands are visible.
4. Try running **`setFlameColor("1")`**.
5. Try running **`on()`** and **`setHeatLevel("low")`**.
6. Restore Device Profile to **Sideline Elite (tested)** and save.

**Expected:**

- `setFlameColor`, `setFlameBrightness`, `setLogColor` commands may still appear in the UI (they are defined globally) but produce a log warning: `Flame color is not mapped for profile 'Generic Tuya Fireplace'` (or equivalent). [verify warning text on hardware]
- `on()`, `off()`, `setHeatLevel()`, and `setHeatingSetpoint()` work normally.
- `flameColor`, `flameBrightness`, `logColor` attributes may not update since the profile does not map those DPs.
- After restoring the Sideline Elite profile, all LED commands work again.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 31: Device Profile = "Custom" — DP Override

**What:** Verify that the Custom profile exposes DP override fields and uses them for commands.

**Pre-conditions:** Device is installed and working.

**Steps:**

1. Open Preferences and change **Device Profile** to **Custom**.
2. Click **Save Preferences**. Verify new DP number fields appear: Flame Color DP, Flame Brightness DP, Log Color DP, Heat Level DP, Temperature Setpoint (°F) DP, Temperature Setpoint (°C) DP, Power DP.
3. Set **Flame Color DP** to `101` (the Sideline Elite value — this should behave identically to the Sideline Elite profile for the flame color command).
4. Click **Save Preferences**.
5. Run **`setFlameColor("2")`** and verify the `flameColor` attribute updates and the physical flame changes.
6. Change **Flame Color DP** to `999` (a DP that doesn't exist on the device).
7. Save and run **`setFlameColor("3")`**.
8. Restore Device Profile to **Sideline Elite (tested)** and save.

**Expected:**

- DP override fields appear when Custom profile is selected.
- With DP 101 set, `setFlameColor()` behaves identically to Sideline Elite profile.
- With DP 999 set, the command sends the write but the device ignores it (no visible change); logs may not show an error since the Tuya protocol does not return per-DP errors. [verify on hardware]
- Driver does not crash with an unmapped DP.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

## Test Area: Polling and State Sync

### Test 32: State Change via Smart Life App Is Detected by Next Poll

**What:** Verify that changes made through the Smart Life app (a second Tuya client) are reflected in Hubitat after the next poll cycle.

**Pre-conditions:** Device is ON. `online = online`. Polling interval is 60 seconds.

**Steps:**

1. Note the current `flameColor` attribute in Hubitat.
2. Open the Smart Life app and change the flame color to something different.
3. **Close the Smart Life app immediately** (critical — must release the TCP slot for Hubitat to poll).
4. Wait for the next poll cycle (up to 60 seconds).
5. Observe `flameColor` in Hubitat.

**Expected:**

- After the poll cycle, `flameColor` updates to reflect the change made in the Smart Life app.
- The update happens without any manual Refresh.
- If the poll fails because Smart Life is still holding the socket, `online` goes `offline` and the driver retries — this is expected and correct behavior.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 33: Physical Remote Button — State Change Detection

**What:** Determine whether physical remote button presses are detectable via polling.

**Pre-conditions:** Device is ON. `online = online`. Physical IR remote available.

**Steps:**

1. Note the current `flameColor` or `heatLevel` in Hubitat.
2. Use the physical remote to change a setting (e.g., press the flame color button to cycle to the next color).
3. Wait for the next poll cycle (up to 60 seconds).
4. Observe whether the attribute updates in Hubitat.

**Expected:**

- If the remote button updates a DP (i.e., the button changes the WiFi module state), Hubitat will reflect the change after the next poll.
- If the remote is IR-only and does not update the Tuya WiFi module's DPs, Hubitat will NOT reflect the change. This is expected device behavior, not a driver bug.
- Use `discoverDPs()` before and after pressing the remote button to determine whether the button updates any DP at all. [verify on hardware — this is empirical testing]
- Document the result here for the README.

**Actual:**
(Fill in during testing — critical: note which remote buttons do / do not update DPs)

**Status:**
(Pending — empirical hardware validation required)

---

## Test Area: Persistent Socket (v0.1.18)

> **Pre-conditions for all tests in this area:** Driver v0.1.18 installed. Smart Life / Tuya app **closed** on all devices. Physical fireplace powered on.

---

### Test 34: Initial Socket Open — socketState and online on Initialize

**What:** Verify the driver opens the persistent socket on `initialize()` and surfaces the correct attributes.

**Pre-conditions:** Device is configured with valid IP, device ID, and local key.

**Steps:**

1. On the device page, click **Initialize** (or save Preferences to trigger `updated()` → `initialize()`).
2. Watch the Logs page for 5–10 seconds.
3. Observe the `socketState` and `online` attributes.

**Expected:**

- Logs show `[Touchstone] Socket opened to <ip>:6668` at info level.
- `socketState` attribute transitions to `open` within ~2 seconds.
- `online` transitions to `online` within ~5 seconds (after the post-connect refresh poll returns).
- Logs show `Heartbeat sent` (debug level) approximately 10 seconds after the socket opened.
- No `MissingMethodException`, `NullPointerException`, or `ClosedChannelException` in logs.
- `socketState` attribute is visible on the Hubitat device page (not blank).

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 35: Heartbeat Survival — Socket Stays Open After 5 Minutes

**What:** Verify the ~10 s heartbeat prevents the Tuya device from closing the idle connection.

**Pre-conditions:** `socketState = open`. Debug logging enabled.

**Steps:**

1. Enable debug logging (Preferences → Enable debug logging → Save).
2. Open the Logs page.
3. Wait 5 minutes without sending any commands.
4. Every ~10 seconds, confirm `Heartbeat sent` appears in debug logs.
5. After 5 minutes, run `refresh()` manually.

**Expected:**

- `Heartbeat sent` appears in debug logs approximately every 10 seconds throughout the 5-minute window.
- `socketState` remains `open` for the entire 5 minutes (never transitions to `reconnecting` or `error`).
- After 5 minutes, `refresh()` succeeds: `online` remains `online` and attributes update.
- Hub memory / state size does not grow noticeably (no log entries about state overflow).
- `state.seqNo` visible in **Current States** increments with each sent frame (heartbeats + commands).

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---

### Test 36: Push-Update Round Trip — Physical Remote → Hubitat Within 2 s

**What:** Verify that pressing the physical remote triggers a Tuya push frame that the driver receives and applies to attributes — without waiting for the next poll.

**Pre-conditions:** `socketState = open`. `online = online`. Physical remote available.

**Steps:**

1. Note the current `flameColor` (or any other DP-mapped attribute) in Hubitat.
2. Press a physical remote button that changes that setting (e.g., press the flame color cycle button once).
3. Watch the Hubitat Logs page and the device attribute panel simultaneously.
4. Observe whether the attribute updates without any manual command or poll.

**Expected:**

- Within ~2 seconds of the remote button press, the relevant attribute (e.g., `flameColor`) updates in Hubitat.
- Logs (debug) show a `Received Tuya cmd 8` (STATUS) or `cmd 7` frame decoded with the new DP values.
- `applyDps` processes the incoming DP and emits a `physical` event (or at minimum updates the attribute value).
- `online` remains `online`.
- No `responseTimeout` or retry log lines triggered by the unsolicited frame.
- The `switch` attribute correctly reflects `on`/`off` if the power button on the remote is pressed.

**Actual:**
(Fill in during testing — critical: confirm which remote buttons emit DP push frames)

**Status:**
(Pending — requires hardware with physical remote)

---

### Test 37: Auto-Reconnect After Network Blip / Device Reboot

**What:** Verify the driver automatically reconnects when the TCP connection is dropped, and resumes normal operation.

**Pre-conditions:** `socketState = open`. `online = online`.

**Steps — Method A (unplug and replug device):**

1. Note `socketState = open`.
2. Physically unplug the fireplace's power cord for 5 seconds, then plug it back in.
3. Watch the Logs page.
4. Wait up to 5 minutes.

**Steps — Method B (hub network drop simulation — no physical access to device):**

1. On your router, temporarily block the Hubitat hub's access to the fireplace IP (or unplug the hub's Ethernet for 10 s, then reconnect).
2. Watch the Logs page.
3. After reconnecting, wait for the driver to reconnect.

**Expected (both methods):**

- Shortly after the disconnect, logs show `socketStatus: ...` with an error/disconnect message.
- `socketState` transitions to `reconnecting`.
- `online` transitions to `offline`.
- Logs show `Socket disconnected; reconnecting in 5s (attempt 1)` (first attempt uses 5 s delay).
- After the device comes back online, logs show `[Touchstone] Socket opened to <ip>:6668`.
- `socketState` transitions back to `open`.
- `online` transitions back to `online` after the post-reconnect refresh completes.
- Heartbeat resumes (debug logs show `Heartbeat sent` within 10 s of reconnect).
- Any commands issued during the disconnect window (queued in `state.pendingRequests`) are sent after reconnect.
- No tight reconnect loop — wait times between attempts should be 5 s, then 30 s, then 60 s if the device stays down.

**Actual:**
(Fill in during testing)

**Status:**
(Pending)

---



Use this checklist to declare the driver "works" before recommending it to other community members.

**Core LAN Control**

- [ ] `on()` and `off()` physically toggle the fireplace within 4 seconds
- [ ] `switch` attribute accurately reflects power state
- [ ] `temperature` attribute shows a real room temperature reading
- [ ] `online` transitions correctly: `unknown` → `online` → `offline` under appropriate conditions
- [ ] Driver reconnects after hub reboot without re-entering credentials

**LED Commands**

- [ ] `setFlameColor()` cycles through all 6 values and changes the physical flame color
- [ ] `setFlameBrightness()` cycles through all 5 levels and visibly changes flame brightness
- [ ] `setLogColor()` cycles through all 12 values and changes the physical log/ember color
- [ ] `setFlameSpeed()` cycles through all enum values and changes animation speed [verify in v0.1.6]
- [ ] `setLogBrightness()` cycles through all 12 levels and changes log brightness [verify in v0.1.6]

**Heating**

- [ ] `setHeatLevel("off" / "low" / "high")` controls the heater fan
- [ ] `setHeatingSetpoint()` writes the correct Fahrenheit DP and updates the attribute
- [ ] The heater does NOT auto-start when `on()` is called (safety requirement)

**Settings / Lifecycle**

- [ ] Blank IP field produces a configuration-incomplete warning, not a crash
- [ ] Wrong local key causes graceful failure, not a silent hang
- [ ] Changing polling interval does not create duplicate timers
- [ ] Power-on defaults apply ~1.5s after `on()`
- [ ] Calling `off()` within 1.5s after `on()` cancels the pending defaults

**Persistent Socket (v0.1.18)**

- [ ] `socketState` attribute is visible and transitions to `open` within ~2 s of `initialize()`
- [ ] `Heartbeat sent` debug log appears every ~10 s; socket stays open after 5+ minutes idle
- [ ] Physical remote button press updates Hubitat attribute within ~2 s (push frame)
- [ ] Network blip / device reboot → driver reconnects automatically; `socketState` recovers to `open`
- [ ] No regression: `setFlameColor`, `setCharcoalColor`, `setFlameBrightness`, `setFlameSpeed`, `setHeatLevel` still work after v0.1.18 update


- [ ] Network loss causes retry with 5s/15s/30s backoff (not a tight loop)
- [ ] Smart Life app collision is detected; driver recovers when app closes
- [ ] Device power-cycle: driver reconnects and attributes reconcile

**Discovery (for users mapping unknown models)**

- [ ] `discoverDPs()` prints all current DP values in the Logs
- [ ] `captureBaseline()` + button press + `captureDiff()` shows which DP changed
- [ ] `setRawDP()` writes a value to an arbitrary DP

**DP 108 Child Lock**

- [ ] `dp108` attribute is visible after a poll
- [ ] No `setChildLock()` command is present (correctly absent in v0.1.6)

---

*Once all checkboxes above are marked, the driver is ready for community beta release.*


### Test 38: Child Lock — setChildLock on/off

**What:** Verify the child lock command (DP 108) locks and unlocks the physical buttons on the fireplace.

**Pre-conditions:** Device profile = Sideline Elite. `socketState = open`. `online = online`. `switch = on` (fireplace powered on with display active so button presses are observable).

**Steps:**

1. In the device page, press **`setChildLock`** -> select **`on`** -> press **Set**.
2. Watch Logs page.
3. Try pressing a physical button on the fireplace unit.
4. In the device page, press **`setChildLock`** -> select **`off`** -> press **Set**.
5. Try pressing a physical button on the fireplace unit.
6. Run `refresh()` and observe the `childLock` attribute value.

**Expected:**

- After step 1: Logs show `[Touchstone] Child lock: on`.
- After step 1: `childLock` attribute updates to `on` within ~3 s (optimistic emit).
- After step 3: Physical buttons on the fireplace do not respond (buttons locked).
- After step 4: Logs show `[Touchstone] Child lock: off`.
- After step 4: `childLock` attribute updates to `off`.
- After step 5: Physical buttons on the fireplace respond normally.
- After step 6: `childLock` reflects the lock state returned by the device.

**Pass criteria:** Physical buttons locked when `childLock = on`; unlocked when `childLock = off`.