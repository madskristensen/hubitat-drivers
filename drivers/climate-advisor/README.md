# Climate Advisor Device

Virtual child driver for the [Climate Advisor](../apps/climate-advisor/README.md) app. One device per Climate Advisor installation — house-wide aggregate only.

## Attributes

### Status
| Attribute | Type | Notes |
|---|---|---|
| `severity` | NUMBER | 0=clear, 1=info, 2=warning, 3=danger |
| `severityText` | ENUM | clear / info / warning / danger |
| `latestMessage` | STRING | Human-readable top alert |
| `messages` | STRING | JSON array of all active messages |
| `houseStatus` | STRING | Compact summary ("House — all clear" or "N active alerts") |
| `contact` | ENUM | open (alerts active) / closed (all clear) — HomeKit-friendly |
| `acknowledged` | ENUM | false / true — user-controlled dismissal flag |

### Outdoor trend
| Attribute | Type | Notes |
|---|---|---|
| `outdoorTrend` | ENUM | heating up / cooling down / steady / unknown |
| `outdoorTempSlope10min` | NUMBER | °F change per 10 min |

### Counters
| Attribute | Type | Notes |
|---|---|---|
| `activeAlertCount` | NUMBER | Count of severity ≥ 1 messages |
| `zoneCount` | NUMBER | Number of configured zones |
| `openContactCount` | NUMBER | Total open contacts across all zones |

### Per-zone data
| Attribute | Type | Notes |
|---|---|---|
| `zoneStatuses` | STRING | JSON map: zone name → `{severity, severityText, latestMessage, indoorTemp, openContactCount, aqi}` |
| `zone1Name` … `zone10Name` | STRING | Zone display name (empty if slot unused) |
| `zone1Severity` … `zone10Severity` | NUMBER | Zone severity 0–3 (0 if slot unused) |
| `zone1Message` … `zone10Message` | STRING | Zone latest message (empty if slot unused) |

## Commands

- `refresh()` — triggers re-evaluation on the parent app
- `deviceNotification(text)` — passive sink; updates `latestMessage` (Rule Machine integration)
- `clearMessages()` — resets severity to 0 / clears message list / clears parent activeMessages state
- `acknowledge()` — sets `acknowledged = true`; auto-resets to `false` when new or escalating alerts arrive
- `pushMessage(key, severity, text)` — push an external message into the Climate Advisor pipeline. `key` is a unique identifier (e.g., piston name); reusing the same key replaces the previous message. `severity` is 1=info, 2=warning, 3=danger. Pass empty/null `text` to clear.
- `clearMessage(key)` — remove the external message previously pushed under `key`.

### External messages from Rule Machine / webCoRE

Any automation can publish a message to Climate Advisor's aggregate `latestMessage` / `messages` / `severity` pipeline by calling `pushMessage` on this device. Example webCoRE piston: `pushMessage("sump-pump", 3, "Sump pump high water")` to raise a danger alert, then later `pushMessage("sump-pump", 3, "")` (or `clearMessage("sump-pump")`) to clear it. External messages persist across evaluations until explicitly cleared.

## Notes

- Do not add this driver to a Hubitat device manually; it is managed by the Climate Advisor app.
- The `contact` attribute maps severity ≥ 1 → `open`, all-clear → `closed`. Use it with the tonesto7 HomeKit bridge to surface Climate Advisor as a contact sensor in HomeKit.
