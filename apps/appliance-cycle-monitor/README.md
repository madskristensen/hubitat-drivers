# Appliance Cycle Monitor

Turn "dumb" appliances — washers, dryers, dishwashers, and more — into **status-reporting devices** on your Hubitat dashboard, in Rule Machine, and in webCoRE.

Each appliance you configure gets its own child **Generic Appliance Device** whose `applianceStatus` follows a simple, predictable lifecycle:

```
Ready  →  Running  →  Finished  →  (door opens)  →  Ready
```

No cloud, no special hardware on the appliance itself — just a power meter or a vibration sensor you already have.

## How it works

The app watches one **run-detection source** per appliance and flips the status accordingly:

| Source | "Running" when | "Finished" when |
| ------ | -------------- | --------------- |
| **Power meter** | Watts rise to ≥ the Running threshold | Watts fall to ≤ the Finished threshold, then stay quiet for the debounce |
| **Acceleration / vibration sensor** | Sensor stays `active` for the configured sustained minutes | Sensor goes `inactive`, then stays quiet for the debounce |

In every case a cycle can only **enter** `Running` while the **door is closed**. Opening the door mid-cycle never stops a run that is already in progress; it only prevents a new cycle from starting (and, after a cycle, drives the unload reset described below).

### Power thresholds (hysteresis)

Power-metered appliances use **two** thresholds so the status doesn't flutter around a single boundary:

- **Running when watts rise to ≥** — the high mark that starts a cycle.
- **Finished when watts fall to ≤** — the low mark (e.g. a dishwasher's ~2 W standby draw) that begins the finish countdown.

Readings **between** the two values hold the current state. This deadband absorbs the noisy mid-range a meter passes through as a machine ramps up and down.

When the source drops into the "finished" range, the app waits a configurable **debounce** ("quiet minutes") before declaring the cycle `Finished`. This stops a mid-cycle pause (a dishwasher between phases, a washer mid-spin lull) from flipping the status prematurely.

### Sustained vibration (acceleration source)

Vibration sensors can twitch from a door bump or a passing footstep. To avoid false starts, an acceleration-based appliance only becomes `Running` once the sensor has stayed `active` for a configurable number of **sustained minutes**. If the vibration stops before that window elapses, the pending start is cancelled. Set it to `0` to treat any `active` reading as an immediate start.

An optional **door contact sensor** is mirrored onto the child device. Opening the door **after** a cycle and **holding it open for 15 seconds** is treated as "I unloaded it" and resets the appliance back to `Ready`. A quick peek that re-closes the door before the 15 seconds elapse leaves the status at `Finished`.

## Status lifecycle

| Status | Meaning | Entered when |
| ------ | ------- | ------------ |
| `Ready` | Idle and empty, ready for a new load | App start (idle source), or door held open 15s after `Finished` |
| `Running` | A cycle is in progress | Door closed **and** watts ≥ Running threshold, or vibration sustained for the configured minutes |
| `Finished` | Cycle done, waiting to be unloaded | Source in the finished range for the full debounce window |
| `Unknown` | No reading yet / no source configured | Before the first source event |

## Child device attributes

| Attribute | Type | Notes |
| --------- | ---- | ----- |
| `applianceStatus` | ENUM | `Ready` / `Running` / `Finished` / `Unknown` — the primary value |
| `statusText` | STRING | Friendly dashboard line, e.g. `Finished — ready to unload` |
| `contact` | ENUM | Mirror of the door sensor (`open` / `closed`) |
| `runStartedAt` | STRING | ISO timestamp of the last cycle start |
| `runEndedAt` | STRING | ISO timestamp of the last cycle end |
| `runDurationMin` | NUMBER | Length of the last completed cycle, in minutes |

## Child device commands

For manual control or Rule Machine / webCoRE automations:

- `setStatus(status)` — force any lifecycle value
- `markRunning()`, `markFinished()`, `markReady()`
- `reset()` — alias for `markReady()`

## Installation

**Via Hubitat Package Manager (recommended):**

1. **Apps → Hubitat Package Manager → Install → "From a URL"**
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/apps/appliance-cycle-monitor/packageManifest.json`
3. Follow the prompts.

**Manual:**

1. Add **`generic-appliance-device.groovy`** under **Drivers Code**.
2. Add **`appliance-cycle-monitor-app.groovy`** under **Apps Code**.
3. **Apps → Add User App → Appliance Cycle Monitor**, then configure your appliances.

## Configuration

1. Choose **how many appliances** you want to track.
2. For each appliance, set:
   - **Name** (becomes the child device label)
   - **Run detection source** — Power meter or Acceleration / vibration sensor
   - The **source device**
   - For power meters: the **Running when watts rise to ≥** and **Finished when watts fall to ≤** thresholds
   - For vibration sensors: the **Sustained vibration minutes before Running** (default `2`)
   - **Quiet minutes before Finished** (default `3`)
   - An optional **door contact sensor**

The child devices appear both in your **Devices** list and nested under the app in **Apps → Appliance Cycle Monitor**, and are cleaned up automatically if you remove an appliance or uninstall the app.

## Tips

- **Threshold tuning:** Set the *Running* threshold above the appliance's standby draw but below its lowest active draw, and the *Finished* threshold at or just above standby. Many smart plugs idle at 0–2 W; a washer or dryer pulls tens to hundreds of watts while running.
- **Debounce tuning:** Increase "quiet minutes" for machines with long pauses between phases (some dishwashers and front-loaders) to avoid a premature `Finished`.
- **Sustained vibration tuning:** Increase the sustained minutes for sensors that twitch easily, or lower it (even to `0`) for sensors with a built-in inactivity timeout that already report `active` steadily during a cycle.
- **No door sensor?** The appliance still cycles `Ready → Running → Finished`; just use `reset()` (or `markReady()`) from an automation when you unload it. Note that without a door sensor the door is always treated as closed, so the door-closed start gate never blocks a cycle.
