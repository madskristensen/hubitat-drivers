# Generic Appliance

Turn "dumb" appliances — washers, dryers, dishwashers — into **status-reporting devices** on your Hubitat dashboard, in Rule Machine, and in webCoRE.

Each appliance you configure gets its own child **Generic Appliance Device** whose `applianceStatus` follows a simple, predictable lifecycle:

```
Ready  →  Running  →  Finished  →  (door opens)  →  Ready
```

No cloud, no special hardware on the appliance itself — just a power meter or a vibration sensor you already have.

## How it works

The app watches one **run-detection source** per appliance and flips the status accordingly:

| Source | Best for | "Running" when |
| ------ | -------- | -------------- |
| **Power meter** | Washer, dryer | Reported watts ≥ your threshold |
| **Acceleration / vibration sensor** | Dishwasher | Sensor reports `active` |

When the source goes quiet, the app waits a configurable **debounce** ("quiet minutes") before declaring the cycle `Finished`. This stops a mid-cycle pause (a dishwasher between phases, a washer mid-spin lull) from flipping the status prematurely.

An optional **door contact sensor** is mirrored onto the child device. Opening the door **after** a cycle is treated as "I unloaded it" and resets the appliance back to `Ready`.

## Status lifecycle

| Status | Meaning | Entered when |
| ------ | ------- | ------------ |
| `Ready` | Idle and empty, ready for a new load | App start (idle source), or door opened after `Finished` |
| `Running` | A cycle is in progress | Power ≥ threshold, or acceleration `active` |
| `Finished` | Cycle done, waiting to be unloaded | Source quiet for the full debounce window |
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
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/apps/generic-appliance/packageManifest.json`
3. Follow the prompts.

**Manual:**

1. Add **`generic-appliance-device.groovy`** under **Drivers Code**.
2. Add **`generic-appliance-app.groovy`** under **Apps Code**.
3. **Apps → Add User App → Generic Appliance**, then configure your appliances.

## Configuration

1. Choose **how many appliances** you want to track.
2. For each appliance, set:
   - **Name** (becomes the child device label)
   - **Run detection source** — Power meter or Acceleration sensor
   - The **source device** (and a **watts threshold** for power meters)
   - **Quiet minutes before Finished** (default `3`)
   - An optional **door contact sensor**

The child devices appear both in your **Devices** list and nested under the app in **Apps → Generic Appliance**, and are cleaned up automatically if you remove an appliance or uninstall the app.

## Tips

- **Threshold tuning:** Set the watts threshold above the appliance's standby draw but below its lowest active draw. Many smart plugs idle at 0–2 W; a washer/dryer pulls tens to hundreds of watts while running.
- **Debounce tuning:** Increase "quiet minutes" for machines with long pauses between phases (some dishwashers and front-loaders) to avoid a premature `Finished`.
- **No door sensor?** The appliance still cycles `Ready → Running → Finished`; just use `reset()` (or `markReady()`) from an automation, or open/close behavior isn't required.
