# Session Log — Climate Advisor Design

**Date:** 2026-05-23  
**Topic:** climate-advisor-design  
**Participants:** Trinity, Cypher, Scribe  

## Scope

Design and specification phase for a new "House Status / Climate Advisor" virtual device on Hubitat that surfaces climate/environmental alerts to SharpTools dashboards and HomeKit.

## Deliverables

1. **Trinity** — Architecture Proposal (v2): Parent App + Child Virtual Device, capability selection, multi-zone data model, evaluation logic, device mappings
2. **Cypher** — Capability Research: HomeKit bridge landscape, capability evaluation, SharpTools rendering patterns, device integration
3. **Scribe** — Decision merge and orchestration logging

## Decisions Finalized

- **Architecture**: Parent App (`apps/climate-advisor/climate-advisor-app.groovy`) + Child Virtual Device (`drivers/climate-advisor/climate-advisor-device.groovy`)
- **HomeKit**: `ContactSensor` (contact: open=alert, closed=clear) via homebridge-hubitat-tonesto7
- **Rich Data**: `Sensor` + custom attributes (severity, latestMessage, messages, houseStatus, tempTrend)
- **Zones**: Hardcoded 3-zone model (Upstairs, Downstairs, Sunroom) with device lists in preferences
- **Outdoor Data**: Backyard sensor (temperature/trend), Weather device (rain), Air quality sensor (AQI)
- **Announcements**: Direct `capability.speechSynthesis` to Sonos Advanced speakers
- **Migration**: Parallel webCoRE piston with new app/device, transition when validated

## Next Steps

- Mads approval required before implementation
- Tank to implement app logic, device driver, and test across real device inventory

---

*Session archived by Scribe. Awaiting Mads sign-off.*
