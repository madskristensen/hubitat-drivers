# Session Log — Climate Advisor v0.3.2

**Timestamp:** 2026-05-23T17:30:14Z  
**Version:** 0.3.2  
**Agent:** Tank  
**Status:** Committed (not pushed)

## Summary

`isComponent: true` flip — child device now nests under app instead of cluttering device list.

**Files changed:** 4  
**Commits:** 1  
**Changelog:** Added

## Details

- `addChildDevice(..., isComponent: true)` in reconcileChildren()
- Version bumps: app, driver, manifests
- Back-compat: Device recreated on next app Save; DNI stable
