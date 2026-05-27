# Calendar Todo Switch

Subscribe a Hubitat virtual device to any web calendar (iCal / webcal feed) and turn flagged events into a **switch** that turns on at the event's start time. The event title (minus its prefix) is published as the `todo` attribute, so dashboards like **SharpTools** can render the current chore on a tile and tap to dismiss it.

Use it as a lightweight todo system: drop an event in your calendar prefixed with ✅ (or any keyword you pick), and Hubitat will surface it at the right moment. Recurring events work — each weekly chore fires fresh every week.

## How it works

1. The driver polls your iCal feed on a schedule.
2. Events whose **title starts with the configured prefix** (default ✅) are considered todos.
3. Recurring events (`RRULE`) are expanded into individual occurrences across the lookback/lookahead window.
4. When such an occurrence's start time arrives, the device's `switch` flips to **on** and the cleaned title is published to the `todo` attribute.
5. The switch stays **on** until either:
	- You manually turn it off (call the standard `off` command — perfect for a "Done" tile on a dashboard), or
	- The next matching event starts (latest event wins).

## Capabilities & attributes

Capabilities: `Sensor`, `Switch`, `Polling`, `Initialize`, `Refresh`.

| Attribute        | Details                                                                  |
| ---------------- | ------------------------------------------------------------------------ |
| `switch`         | `on` while a todo is active, `off` otherwise                             |
| `todo`           | Current todo title with the prefix stripped (no emoji)                   |
| `todoIcon`       | Emoji chosen for the current todo (from emoji rules + fallback)          |
| `todoStart`      | ISO timestamp of the active todo's start                                 |
| `nextTodo`       | Title of the next upcoming matching event (preview, no emoji)            |
| `nextTodoIcon`   | Emoji for the next upcoming todo                                         |
| `nextTodoStart`  | ISO timestamp of the next upcoming matching event                        |
| `matchingEvents` | Count of matching events inside the lookback/lookahead window            |
| `lastChecked`    | ISO timestamp of the last successful feed fetch                          |

Commands: `on` (manual), `off` (clears the active todo), `refresh`, `poll`, `clearTodo`.

## Setup

1. **Get your calendar's iCal URL.** In Google Calendar: *Settings → Settings for your calendars → Integrate calendar → Secret address in iCal format*. In Outlook / iCloud: *Publish calendar* and copy the ICS URL. `webcal://` URLs are accepted and treated as `https://`.
2. In Hubitat: **Devices → Add Device → Virtual** and pick **Calendar Todo Switch**.
3. In **Preferences**, paste the iCal URL and (optionally) change the prefix.
4. **Save Preferences**. The driver fetches immediately and then polls on your selected interval.

### Via Hubitat Package Manager (HPM)

1. In HPM choose **Install → From URL**
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/calendar-todo/packageManifest.json`

## Preferences

| Setting          | Notes                                                                                   |
| ---------------- | --------------------------------------------------------------------------------------- |
| iCal feed URL    | Required. `https://` or `webcal://`.                                                    |
| Title prefix     | Default ✅. Any string. Use `Match mode` to control how it's compared.                   |
| Match mode       | *Starts with* (default) or *Contains anywhere*.                                         |
| Emoji rules      | Optional. One rule per line: `EMOJI = keyword1, keyword2, ...`. The first rule whose keyword appears (whole word, case-insensitive) in the cleaned title sets `todoIcon`. Lines starting with `#` are ignored. |
| Fallback emoji   | Used as `todoIcon` / `nextTodoIcon` when an active event matches no rule. Default 🔔. Leave blank for none. |
| Idle icon        | Used as `todoIcon` when there is no active todo. Default ✔️.                            |
| Idle text        | Used as `todo` / `nextTodo` when there is no active or upcoming chore. Default `Clear`. Hubitat hides attributes whose value is empty, so something visible works best. |
| Poll interval    | 1 / 5 / 10 / 15 / 30 / 60 minutes. The driver also schedules an exact-time trigger for the next known matching event so it fires promptly even between polls. |
| Lookback hours   | How far back to consider events that may still be "active". Default 24.                 |
| Lookahead days   | How far ahead to scan for upcoming events. Default 14.                                  |
| Debug logging    | Auto-disables after 30 minutes.                                                         |

### Emoji rules example

```
🗑️ = trash, garbage, waste, recycling
🧺 = laundry, washing
🐕 = dog, walk
💊 = pills, meds, medication
🌱 = plants, water
```

A calendar event titled `✅ Waste night` sets `todo` = `Waste night` and `todoIcon` = `🗑️`. An event titled `✅ Dentist` that matches no rule sets `todoIcon` = `🔔` (the fallback). On a SharpTools dashboard, bind one hero tile to `todoIcon` for a big category icon and another to `todo` for the title.

## SharpTools tile recipe

- Add a **Switch** tile bound to this device — it lights up when a chore is active and tapping it (turning off) marks the chore done.
- Add a **Hero Attribute** tile bound to `todoIcon` for a big category icon, and another bound to `todo` for the title text.
- Optional tiles bound to `nextTodoIcon` / `nextTodo` so the next chore is always previewed.

## Limitations

- **Recurring events:** `FREQ=DAILY`, `WEEKLY` (with optional `BYDAY=MO,TU,...`), `MONTHLY`, and `YEARLY` are supported, along with `INTERVAL`, `COUNT`, `UNTIL`, and `EXDATE`. More exotic patterns (`BYMONTHDAY`, `BYSETPOS`, `RDATE`, etc.) are not expanded.
- **Time zones:** `DTSTART;TZID=…` and UTC (`…Z`) values are honored. Floating local times use the Hubitat hub's time zone.
- All-day events (`VALUE=DATE`) trigger at local midnight on their start date.

## License

MIT — see repository [LICENSE](../../LICENSE).
