# Session Log — Touchstone v0.1.4 Safety & Sandbox Fix

**Timestamp:** 2026-05-17T18:58:55Z  
**Session:** touchstone-v014-safety-fix  
**Agents:** Tank (2 runs), Link  

## Brief

Power-on optional defaults shipped (v0.1.3 intermediate), then immediately safety-hardened (v0.1.4: removed heater auto-apply per Mads's directive; fixed Hubitat reflection sandbox bugs blocking install).

## Decision Pairs Captured

1. **Tank v0.1.3:** Added optional power-on defaults for fireplace (flame color, log color, flame brightness, heating setpoint, heat level). Gated by Device Profile.

2. **Tank v0.1.4 Safety Directive:** User (Mads) explicitly prohibited automatic heater activation—fire/burn risk from radiant heat element. Removed `defaultHeatLevel` completely; added SAFETY comment.

3. **Tank v0.1.4 Sandbox Audit:** Hubitat blocks reflection-style `.getClass()` calls at runtime. Removed two executable instances (parse() exception logging, dpValueType() fallback type lookup). Full audit: 14 reflection patterns scanned, 2 hits removed, zero remaining.

4. **Link v0.1.4 Documentation:** Updated README with Power-on Defaults section + new Safety subsection (explains intentional omission of heater auto-start); updated packageManifest to 0.1.4; changelog omits v0.1.3 (never publicly released).

## Bundled Commit

Single commit reflects both the intent (v0.1.3 features) and the safety fix (v0.1.4). v0.1.3 was an intermediate state never released to users; only v0.1.4 is visible.

## Build Status

Ready for integration after Mads's real-device smoke test (part of Switch's test plan; ~30 min).
