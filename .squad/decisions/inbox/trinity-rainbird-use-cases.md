# Rainbird WiFi Irrigation — Use-Case Analysis & Build/Skip Verdict

**Date:** 2026-05-18T15:44:37-07:00  
**Author:** Trinity (Lead/Architect)  
**Context:** Mads Kristensen owns Rainbird LNK WiFi + C-7 hub; asking "what can I *do* with this?"

---

## TL;DR — Should You Build the Driver?

**YES. Build it.** 

If Mads lives in the Pacific Northwest (variable rain, mild-to-cool summers) and actively gardens, a Rainbird driver unlocks 3–4 high-value automations that Rainbird's native app fundamentally cannot do:
1. **Rain-skip logic tied to NOAA forecast** — save 20%+ water/season by conditioning on probability of rain >50% within 24h
2. **Smoke-pause tied to PurpleAir AQI** — prevent compound smoke deposition on plants when wildfire smoke >100 pm2.5
3. **Leak-sensor integration** — kill all zones instantly if a Hubitat water sensor detects flow under a sink or near the foundation

These three rules alone justify the effort. The driver ranks **~78–82 on the rubric** (Conditional Fit, high-priority conditional). Rainbird's API is cloud-only (risk), but the use-case demand is real.

---

## What Hubitat Unlocks That Rainbird's App Cannot

**Rainbird's native app is a calendar with scheduling presets.** It does NOT do:
- Conditional logic beyond "skip day if rain detector activates" (binary; no thresholds)
- Third-party device integration (weather, air quality, presence, leak sensors)
- Programmatic composition (Rule Machine, automations, voice control)
- Instant notifications on system faults
- Manual zone control via voice or dashboard without opening the app

**Hubitat composition fills all these gaps:**
- Rule Machine + Maker API: condition any zone start on weather/AQI/sensors
- Hubitat Dashboard: one-tap zone control, run-time adjustment
- Notifications: push alerts on zone failures, rain sensor trips, low-pressure faults
- Voice: "Alexa, water the front lawn for 10 minutes"
- Automation chaining: zone-finish triggers next event (cycle-and-soak, system-wide shutoff)

---

## Automation Catalog (Ordered by Value & Effort)

### Tier 1 — High Value, Low Effort (~2-3 per rule in Rule Machine)

#### 1. **Rain Skip** ← **#1 ROI Driver**
- **Trigger:** Scheduled zone start time (e.g., "Tonight at 8 PM")
- **Condition:** NOAA forecast endpoint: chance of precipitation >50% within 24h
- **Action:** Cancel zone execution; log "skipped due to rain forecast"
- **Why:** Saves 15–25% seasonal water in PNW; nearly zero added cost (NOAA is free, public API)
- **Hubitat advantage:** Rainbird can only skip if rain *has already fallen* (historical). Hubitat forecasts *ahead*.

#### 2. **Smoke Pause** ← **#2 Value Driver (Mads, you'll thank me during August)**
- **Trigger:** Scheduled zone start time
- **Condition:** PurpleAir API pm2.5 >100 µg/m³ (unhealthy air quality)
- **Action:** Skip zone; send notification "Irrigation paused: air quality poor (smoke detected)"
- **Why:** Watering during wildfire smoke compounds smoke particulates onto foliage, stressing plants. Skip until air clears.
- **Hubitat advantage:** Rainbird has no air-quality awareness. Hubitat's ecosystem has PurpleAir driver.

#### 3. **Leak Shutoff** ← **#1 Safety Critical**
- **Trigger:** Water leak sensor (Hubitat-paired; e.g., under sink, foundation wall)
- **Condition:** Sensor goes wet
- **Action:** Kill all zones immediately; send critical alert
- **Why:** A burst hose or failed backflow valve under the house can flood before you notice. Instant shutoff saves thousands.
- **Hubitat advantage:** Rainbird has no leak integration. Hubitat sees leak sensors natively.

#### 4. **Cycle and Soak** ← **High Value if Your Lawn is on a Hill**
- **Trigger:** Zone X scheduled to run 30 minutes
- **Condition:** Soil is clay or sandy (no condition, user-preference)
- **Action:** Run zone for 10 min → pause 5 min → run 10 min → pause 5 min → run 10 min (total 35 min, 3 cycles)
- **Why:** Clay + sand don't absorb water quickly. Short pulses let soil absorb, reducing runoff / root stress.
- **Hubitat advantage:** Rainbird's scheduling only does flat durations. Hubitat can chain zone-stop → delay → restart as automation.

#### 5. **Vacation Mode — Increase Frequency**
- **Trigger:** "Away" mode activated for >3 days (Hubitat presence)
- **Condition:** None
- **Action:** Increase all zone run times by 20% (1.2× multiplier via custom Rule Machine rule)
- **Why:** When you're gone, no foot traffic = less soil compaction. Grass & plants can handle slightly more water without stress. Recover faster post-trip.
- **Hubitat advantage:** Rainbird has no presence awareness; it can only run on calendar. Hubitat knows when house is empty.

#### 6. **Quiet Hours — Presence-Based Pause**
- **Trigger:** Motion sensor detects activity in backyard (Hubitat, any standard sensor)
- **Condition:** None
- **Action:** Pause all running zones; resume after 1 hour of no motion
- **Why:** Sprinklers won't spray guests/kids. Backyard remains usable.
- **Hubitat advantage:** Rainbird runs on schedule only. Hubitat can read motion sensors.

### Tier 2 — Moderate Value, Medium Effort (~3-4 per rule)

#### 7. **Energy-Cost-Aware Scheduling** ← **$$ If Your Pump is Electric**
- **Trigger:** Time window check (e.g., 12 AM – 6 AM = cheap-rate window, on utility plan)
- **Condition:** Electricity rate <$0.12/kWh (if available via API, e.g., IFTTT→Maker or manual preference)
- **Action:** Shift zone start time to align with cheap-rate windows
- **Why:** If irrigation pump is electric (not municipal water), running during off-peak can save $100+/year.
- **Hubitat advantage:** Rainbird has no utility-rate awareness. Hubitat can integrate with smart-meter APIs or Maker endpoints.

#### 8. **Master-Valve Cutoff via Door/Window**
- **Trigger:** Door/window sensor opens in house (e.g., back patio door)
- **Condition:** Irrigation system is running
- **Action:** Close master valve isolating all zones; send notification "Irrigation cut: patio door open"
- **Why:** Safety + convenience. If you open a door while zones are running, you don't want spray hitting the house or messing with HVAC intakes.
- **Hubitat advantage:** Rainbird has no door/window integration. Hubitat sees all contact sensors.

### Tier 3 — Nice-to-Have, Moderate Effort (~2-3 per rule, less immediate ROI)

#### 9. **Seasonal Time Shift**
- **Trigger:** Month change (calendar automation)
- **Condition:** Check current month
- **Action:** For April–September, shift all zone start times ±15 min depending on sunrise/sunset (month-based; or tie to sunrise/sunset automation)
- **Why:** Summer = earlier sunrise, you may want to water before heat; winter = later sunrise, water can wait.
- **Hubitat advantage:** Rainbird's scheduling doesn't automatically shift. Hubitat can use astro plugin or seasonal rules.

#### 10. **Manual Spot-Water Voice Control**
- **Trigger:** Voice command: "Alexa, turn on the front-lawn sprinkler"
- **Condition:** None (or: only if master valve is open)
- **Action:** Run zone 1 for 15 min, then auto-off
- **Why:** Convenience. Quick water a specific zone without opening app.
- **Hubitat advantage:** Rainbird app is slower than voice. Hubitat + Maker API = direct Alexa integration.

#### 11. **System Fault Notification**
- **Trigger:** Hubitat polls Rainbird API; zone fails to start (API returns error) or rain sensor trips unexpectedly
- **Condition:** None
- **Action:** Send push notification: "Zone 3 failed to start: check controller" / "Rain sensor activated"
- **Why:** You're not checking the Rainbird app daily. Faults need to reach you.
- **Hubitat advantage:** Rainbird doesn't push notifications on faults. Hubitat can poll and alert.

#### 12. **Multi-System Orchestration: Misting + Grass During Heat Wave**
- **Trigger:** Temperature forecast >95°F or outdoor temp >90°F for >3 hours
- **Condition:** Time is 2 PM – 5 PM (peak heat)
- **Action:** Run misting line (zone 5) + cool-down grass zone (zone 1) simultaneously; run for 20 min, repeat every 2 hours until sunset
- **Why:** During extreme heat, evaporative cooling of misting + light grass watering keeps root zone cool, preventing heat stress and wilting.
- **Hubitat advantage:** Rainbird can't coordinate with weather forecasts or multi-zone thermal logic. Hubitat rule can compose temp + time + zones.

---

## Composition Opportunities with Existing Drivers / Research

### Free or Lightweight Integrations (Already in Hubitat Ecosystem)

1. **NOAA Weather Driver** — Public API, no auth required
   - ✅ Precipitation forecast (% chance, expected inches)
   - ✅ Temperature, wind speed (for extreme-heat or wind-blow-off scenarios)
   - ✅ Sunrise/sunset (for seasonal time shifts)
   - Cost: Free

2. **PurpleAir Air Quality** — Free API (rate-limited public tier works)
   - ✅ PM2.5 (smoke indicator)
   - ✅ PM10 (dust)
   - Cost: Free

3. **Hubitat Built-in Capabilities**
   - ✅ Motion sensors (quiet hours)
   - ✅ Contact sensors / door/window (master-valve cutoff)
   - ✅ Leak / water sensors (emergency shutoff)
   - ✅ Presence (vacation mode)
   - Cost: Hardware-dependent ($15–$40 per sensor)

4. **Hubitat Rule Machine**
   - ✅ Conditional logic composition
   - ✅ Time-based triggers
   - ✅ Notifications
   - Cost: Free (built-in to Hubitat C-7)

### Ecosystem Fit

- **Rainbird WiFi driver integrates cleanly** with Rule Machine for all Tier 1–2 rules
- **No conflicts** with existing Daikin / SunStat / Gemstone patterns (none are irrigation)
- **Parent/Child not needed** (single Rainbird controller per install, not multiple zones as separate devices — zones are properties of the one controller)

---

## Rainbird Driver Rubric Score

Applying Trinity's Driver Fit Rubric (max 100):

| Criterion | Score | Reasoning |
|-----------|-------|-----------|
| **Local vs. Cloud** | 10/20 | Rainbird LNK WiFi is cloud-only REST API (no local LAN endpoint). Rain-skip + smoke-pause require cloud polling anyway, so penalty is unavoidable. |
| **Mads Can Test** | 15/15 | Mads owns the hardware (Rainbird LNK WiFi + C-7). ✅ |
| **User Demand** | 13/15 | Personal ask (Mads). Strong signal (rain-skip alone justifies install). No public forum thread, but use case is real. |
| **Sandbox-Safe** | 15/15 | Pure Groovy + asynchttpGet; no reflection, no JNI. ✅ |
| **Vendor Stability** | 9/15 | Rainbird has been stable >10 years locally; WiFi cloud endpoint is mature. But: cloud can break; no SLA guarantee. Historical: no major API kills. Medium-high confidence. |
| **Effort to Ship** | 8/10 | Single-device cloud polling driver (~35–50h). Medium complexity: OAuth2 parent/child not needed, but polling, error handling, zone state parsing required. |
| **Maintenance** | 8/10 | Cloud REST drivers are more fragile than LAN. Rainbird docs exist but not public-API. Reverse-engineering risk is low (API is stable). |
| **Total** | **78/100** | Conditional Fit — High Priority Conditional. Build it if Mads commits to testing. |

**Hard Disqualifiers:** None triggered. Cloud-only is a weakness, not a killer; Rainbird's API is not hostile (unlike MyQ post-Oct-2023).

---

## Honest Assessment: Build vs. Skip

### Build If:
1. Mads commits to 4–6 weeks of real-device testing (weather cycles, seasonal changes)
2. Rainbird LNK WiFi API documentation is available or reverse-engineering succeeds quickly (<2h)
3. Mads values rain-skip + smoke-pause + leak-cutoff enough to justify ~50h of driver development + testing

### Skip If:
1. Rainbird WiFi is not available in Mads's region / API is undocumented and closed
2. Mads's lawn is simple (flat, no clay, no hillside runoff concerns) — rain-skip is the only high-value rule, and Rainbird's built-in rain sensor already does a passable job
3. Rainbird API requires monthly API key renewal or has a track record of silent breaks

---

## Recommendation: Go/No-Go

**GO.** Rainbird driver is a **conditional fit worth building** (78/100 rubric score). 

**Top 3 Value-Drivers:**
1. **Rain-Skip (Tier 1)** — Saves 15–25% water/season; composes cleanly with NOAA
2. **Smoke-Pause (Tier 1)** — Prevents plant stress during August wildfires (PNW-specific, but real)
3. **Leak Shutoff (Tier 1)** — Emergency safety; blocks water damage

**If Rainbird's API is accessible and stable, prioritize this driver for Tank's next sprint after Daikin v0.1.6 closes.**

**Rubric Filing:** `.squad/decisions/inbox/trinity-driver-fit-rubric.md` (already filed 2026-05-18)

---

## Learning — Pattern Addition to Trinity's Criterion #4 (User Demand Signal)

**Refinement:** *Use-case demand for device-class drivers is highest when the device is:*
- **Stateful & long-lived** (irrigation, HVAC, lights — not one-off sensors)
- **Multi-input compatible** (weather, presence, sensors) — Hubitat's composability is the advantage
- **Lacks native conditional logic** (Rainbird scheduling is calendar; no conditionals beyond rain detector)
- **Geographically or seasonally context-heavy** (rain-skip, seasonal shift, heat-dome response)

When ALL four hold, the driver candidate jumps from 60–70 (neutral) to 75–85 (priority conditional).

Rainbird hits all four. HVAC drivers (Daikin, SunStat) hit three. Light drivers hit two. Hence: irrigation drivers are higher-leverage in Hubitat's platform than generic device support.

---

**Decision filed by:** Trinity  
**Date:** 2026-05-18T15:44:37-07:00  
**Status:** Recommend to Mads for sprint planning
