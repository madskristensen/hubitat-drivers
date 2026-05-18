# Session Log — Driver Opportunity Survey + Fit Rubric

**Date:** 2026-05-18  
**Coordinator:** Scribe  
**Requested by:** Mads Kristensen  
**Session topic:** Driver opportunity survey + fit rubric (HA-vs-Hubitat gap analysis)

## Summary

Cypher and Trinity completed background research. **Cypher** surveyed Home Assistant integrations to identify driver gaps in Hubitat, producing a shortlist of 12 device + 8 cloud-service candidates ranked by feasibility. **Trinity** established a 100-point weighted rubric with hard disqualifiers as standing team policy for future candidate evaluation.

### Top 5 Picks (Cypher)

1. **Enphase Envoy** — solar gateway, local HTTP, 9/9 score, high PNW demand, fw7 JWT auth caveat
2. **Tesla Wall Connector Gen 3** — unauthenticated LAN, simplest protocol, 9/9 score
3. **Tibber Energy** — GraphQL pricing, Danish fit, free token, 9/9 score
4. **Reolink Camera/Doorbell** — official API, local doorbell-as-trigger, 8/9 score
5. **Mitsubishi ESPHome CN105** — mini-split bridge, PNW dominant, ESPHome pattern proven, 8/9 score

### Fit Rubric (Trinity)

- **7 weighted criteria:** protocol, testability, demand, sandbox-safety, vendor stability, effort, maintenance
- **100-point scale:** 80+ strong fit, 65–79 conditional, 50–64 weak, <50 no fit
- **10 hard disqualifiers:** killed APIs, reflection/JNI, >$500 hardware, OAuth2 redirect, persistent MQTT, binary protocol, no audit logging, >1KB secrets, multi-protocol, sandbox-banned Groovy
- **4 cloud-service patterns:** documented with use cases, code reuse, testing requirements

Both artifacts now in `.squad/decisions/decisions.md` as standing references.

### Outcome

Awaiting Mads's hardware-availability decision before any driver build starts. Cypher's shortlist is ranked; Trinity's rubric can now score incoming candidates.

---

**Timestamp:** 2026-05-18T15:28:26-07:00
