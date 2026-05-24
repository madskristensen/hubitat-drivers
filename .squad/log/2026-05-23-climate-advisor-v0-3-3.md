# Session Log: Climate Advisor v0.3.3

**Timestamp:** 2026-05-23  
**Version:** 0.3.3

## Summary

New evaluator `evaluateFreeCooling` addresses coverage gap in free-cooling scenario (outdoor cooler than indoor at setpoint).

**Scenario:** Outdoor 65°F, indoor 75°F (setpoint), windows closed, all conditions favorable for ventilation. No notification fired before v0.3.3.

**Fix:** New evaluator fires INFO when outdoor < indoor and indoor at/near setpoint, preventing AC from running unnecessarily.

**Non-overlapping:** Mutually exclusive with existing evaluators (opposite direction/conditions).

**Status:** Shipped v0.3.3. File changes documented in decisions.md.

---

*Recorded 2026-05-23T17:50:00-07:00*
