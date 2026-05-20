# Occupancy Mode Manager

A Hubitat app that decides whether someone is home by combining selected:

- Presence sensors
- Motion sensors
- Contact sensors

When rules indicate occupancy or vacancy, it sets your hub Location Mode to your configured **Home** or **Away** mode.

## Install

### Hubitat Package Manager (HPM)

1. Open HPM → **Install** → **Search by URL**
2. Paste:

   ```text
   https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/apps/presence-confidence-mode-manager/packageManifest.json
   ```

3. Follow the prompts.

### Manual

1. Open **Apps Code** in Hubitat → **New App**
2. Paste [`presence-confidence-mode-manager.groovy`](presence-confidence-mode-manager.groovy)
3. Save, then install the app from **Apps**

## Configuration

1. Select any combination of presence, motion, and contact sensors.
2. Choose the target **Home** and **Away** modes.
3. Optionally choose locks to secure when switching to Away and locks to unlock when switching to Home.
4. Set the Home inactivity threshold (0 means instant Home on current activity).
5. Set the Away inactivity threshold (default 20 minutes).
6. Optionally pause in specific modes (for example Night), and restrict Home/Away paths to specific current modes.
7. Use **Evaluate occupancy now** to run an immediate decision pass.

## Notes

- Hub mode selectors use your hub's actual configured location modes.
- The app shows a "Why this mode" summary plus recent decision history.
- A mode-change cooldown prevents rapid Home/Away flapping.
- Presence, motion, and contact changes all roll into the same inactivity timer as the away/home thresholds.
- Contact open and close events both count as activity.
- Presence not-present time is tracked separately so Away waits for the full window.
- Lock unlock events (from the "activity locks" picker) count as occupancy activity.
- Evaluation is event-driven and debounced instead of polling on a fixed timer.
- Lock and unlock actions are tied to mode changes, using separate lock lists for Away and Home.
- Optional Away prerequisite locks: Away mode is only evaluated when all designated locks are locked.
- Evaluation is event-driven and debounced instead of polling on a fixed timer.

## Changelog

| Version | Date       | Notes |
|---------|------------|-------|
| 0.10.0  | 2026-05-20 | Added optional Away prerequisite locks — Away is only evaluated when all designated locks are locked; locking them triggers an immediate re-evaluation. |
| 0.9.0   | 2026-05-20 | Lock unlock events from a new "activity locks" picker count as occupancy activity. |
| 0.8.0   | 2026-05-20 | Split presence inactivity from general activity so Not Present has its own timer and Away waits for the full window. |
| 0.7.0   | 2026-05-20 | Switched to event-driven, debounced evaluation and scheduled the next check only when needed to reduce wakeups. |
| 0.6.0   | 2026-05-20 | Removed secondary Away confirmation. Contact open/close events now both count as activity for the inactivity timer. |
| 0.5.0   | 2026-05-20 | Added lock/unlock actions for Away/Home with separate device lists. |
| 0.4.0   | 2026-05-20 | Removed the strategy preset and recency knobs; Home and Away now use simple inactivity thresholds only, with motion/contact folded into the same timer. |
| 0.3.0   | 2026-05-20 | Added explicit arrival-vs-departure asymmetry controls and optional Away secondary confirmation using selected exit contacts (closed recently). |
| 0.2.0   | 2026-05-20 | Renamed to Occupancy Mode Manager. Replaced confidence percentages with preset strategies + timing controls. Added mode-based path restrictions and pause modes (Night-friendly), plus decision explainability/history. |
| 0.1.1   | 2026-05-20 | Added required `iconUrl` and `iconX2Url` in app metadata to satisfy Hubitat app definition validation. |
| 0.1.0   | 2026-05-20 | Initial release: confidence-based Home/Away mode control from selected presence, motion, and contact sensors. |
