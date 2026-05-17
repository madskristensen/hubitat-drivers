# Session Log — gemstone-v041-playeffectbyname-webcore

**Date:** 2026-05-17T04:44:00Z  
**Topic:** WebCoRE playEffectByName visibility  
**Requestor:** Mads Kristensen  

## Issue

WebCoRE exposes only numeric `setEffect(NUMBER)` from LightEffects capability. The existing `setEffect(String)` custom overload is invisible to WebCoRE's action picker — no "Execute custom command" path available for string-based effect invocation.

## Solution

Add `playEffectByName(String)` as a distinct, non-overloaded command visible to WebCoRE's action picker.

## Outcome

- **Tank**: Implementation complete — command declaration, method delegate, version bump
- **Link**: Documentation complete — README, WebCoRE usage section
- **Switch**: Testing complete — 8 test cases covering visibility, functionality, error handling

## Deliverables

- v0.4.1 release (Gemstone Lights)
- Skill extracted: `.squad/skills/hubitat-webcore-command-visibility/SKILL.md`
- All changes staged and ready for commit
