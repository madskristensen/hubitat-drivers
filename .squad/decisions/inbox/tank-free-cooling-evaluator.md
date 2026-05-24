# Decision: Climate Advisor v0.3.3 — evaluateFreeCooling

**Date:** 2026-05-23  
**Author:** Tank  
**Version:** 0.3.3

## The Gap (diagnosed in behavioral trace)

Mads reported a real-world scenario that produced zero notifications despite an actionable situation:

- Outdoor: 65°F, Indoor: 75°F (= cooling setpoint), windows CLOSED, trend unknown

**Why nothing fired:**

| Evaluator | Exit reason |
|---|---|
| `evaluateCoolingPreAlert` | `windowGatePasses = false` (contacts configured, all closed) — exits line 1 |
| `evaluateHeatingPreAlert` | Same gate — exits line 1 |
| `evaluateCoolBreach` | `outdoorTemp > indoorTemp` required (65 > 75 = false) — wrong direction |
| `evaluateComfortOpen` | `outdoorTemp >= lowerBound` required (65 >= 70 = false) — outdoor below comfort band |

The root cause: `evaluateCoolBreach` fires when outdoor is **hotter** than indoor (close-windows-now). `evaluateComfortOpen` fires when outdoor is in the mid-comfort band. Neither covers outdoor **cooler** than indoor when indoor is at the setpoint — which is the ideal free-cooling condition.

## The Fix

New evaluator `evaluateFreeCooling` added after `evaluateComfortOpen` in `climate-advisor-app.groovy`. Fires INFO (severity 1) when ALL:

1. `outdoorTemp < indoorTemp` — outdoor is cooler (right direction for ventilation)
2. `indoorTemp >= (coolSP - coolingPreAlertOffset)` — indoor at/near setpoint (reuses existing offset, default 3°F); AC would otherwise run
3. Thermostat in `cool` or `auto` mode
4. Contacts configured AND all closed (something to open; not already open)
5. Not raining
6. AQI below warn threshold
7. Outdoor trend NOT `rising` (don't suggest opening if it'll overshoot)

Message format: `"Zone 1 outdoor 65°F is 10.0°F cooler than indoor 75°F — consider opening windows for free cooling (cool setpoint 75°F)"`

## Non-overlap proof

- **vs `evaluateCoolBreach`**: that requires `outdoorTemp > indoorTemp` — mutually exclusive with condition 1 above
- **vs `evaluateComfortOpen`**: that requires outdoor inside `[heatSP+2, coolSP-2]`; free cooling fires when outdoor is *below* that band
- **vs `evaluateCoolingPreAlert`**: that requires `windowGatePasses = true` (contacts open or absent) AND outdoor rising AND outdoor hotter — three conditions opposite to this scenario

## Behavioural change for Mads's scenario

Before v0.3.3: no notification. After v0.3.3: INFO message "Zone 1 outdoor 65°F is 10.0°F cooler than indoor 75°F — consider opening windows for free cooling (cool setpoint 75°F)". Severity = 1; `contact` attribute stays `closed` (INFO < 1 threshold for open); `severityText` = "info".

## Files changed

- `apps/climate-advisor/climate-advisor-app.groovy` — new `evaluateFreeCooling` method, wire-up in `evaluateZone`, version 0.3.3, changelog
- `drivers/climate-advisor/climate-advisor-device.groovy` — version 0.3.3, changelog
- `apps/climate-advisor/packageManifest.json` — version 0.3.3
- `packageManifest.json` (root) — Climate Advisor app + driver entries 0.3.3
- `apps/climate-advisor/README.md` — free cooling row added to alert table
