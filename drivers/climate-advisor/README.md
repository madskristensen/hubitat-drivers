# Climate Advisor Device

Virtual child driver for the [Climate Advisor](../apps/climate-advisor/README.md) app. Used for both the aggregate house child and per-zone children.

## Attributes

| Attribute | Type | Notes |
|---|---|---|
| `severity` | NUMBER | 0=clear, 1=warning, 2=alert |
| `severityText` | ENUM | clear / info / warning / alert |
| `latestMessage` | STRING | Human-readable top alert |
| `messages` | STRING | JSON array of all active messages |
| `houseStatus` | STRING | Short summary; back-compat for webCoRE |
| `outdoorTrend` | ENUM | rising / falling / steady / unknown |
| `tempTrend` | ENUM | Legacy alias for outdoorTrend (aggregate only) |
| `outdoorTempSlope10min` | NUMBER | °F change per 10 min |
| `activeAlertCount` | NUMBER | Count of severity ≥ 2 messages |
| `zoneCount` | NUMBER | Number of configured zones (aggregate) |
| `zoneName` | STRING | Zone display name (zone children) |
| `indoorTemp` | NUMBER | Average indoor temp for zone |
| `indoorTrend` | ENUM | rising / falling / steady / unknown |
| `indoorTempSlope10min` | NUMBER | °F change per 10 min |
| `openContactCount` | NUMBER | Number of open contacts in zone |
| `openContacts` | STRING | Comma-separated names of open contacts |
| `aqi` | NUMBER | Current AQI value |

## Commands

- `refresh()` — triggers re-evaluation on the parent app
- `deviceNotification(text)` — passive sink; updates `latestMessage` (Rule Machine integration)

## Notes

- Do not add this driver to a Hubitat device manually; it is managed by the Climate Advisor app.
- The `advisorRole` data value (`aggregate` or `zone`) distinguishes child types; unused attributes stay at their last value.
