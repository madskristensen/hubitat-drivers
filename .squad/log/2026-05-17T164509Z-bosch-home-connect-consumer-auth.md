# Session Log: Bosch Home Connect Consumer Auth

**Timestamp:** 2026-05-17T16:45:09Z  
**Topic:** bosch-home-connect-consumer-auth  
**Requested by:** Mads Kristensen  

## Summary

User directive requested elimination of developer portal registration step for Bosch Home Connect fridge driver. Cypher researched consumer-auth alternatives (hcpy, SingleKey ID, openHAB, Homebridge).

**Verdict:** No viable path. CAPTCHA + local WebSocket protocol + lack of consumer REST API are permanent Hubitat sandbox constraints. Developer portal path (Option 1, Device Flow) remains the only feasible solution — 5-minute one-time onboarding.

**Next:** Tank to implement Device Flow auth + state polling on developer API.
