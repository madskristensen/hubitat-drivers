# Decision: Climate Advisor v0.3.2 — isComponent: true

**Date:** 2026-05-23  
**Author:** Tank  
**Version:** 0.3.2

## Change

In `reconcileChildren()` (`climate-advisor-app.groovy` line ~200), changed:

```groovy
addChildDevice(CHILD_NS, CHILD_DRIVER, dni, [name: label, label: label, isComponent: false])
```

to:

```groovy
addChildDevice(CHILD_NS, CHILD_DRIVER, dni, [name: label, label: label, isComponent: true])
```

## Rationale

With `isComponent: false`, the Climate Advisor Device appeared in the main **Devices** list alongside every other device on the hub — cluttering the list with a device users never interact with directly. It's a virtual aggregate surface for SharpTools/dashboard consumption, not a physical device.

Setting `isComponent: true` nests the child device under the parent app in the **Apps** section, exactly the pattern Hubitat uses for its own first-party apps (Groups and Scenes, Room Lighting, etc.). Users who want to interact with the device can still reach it by opening the app; dashboard tiles continue to work normally because component devices are still fully addressable.

## Back-compat impact

Existing installations that have already created the child device (`isComponent: false`) will not automatically migrate. On next **Save** of the app (which calls `reconcileChildren()`), the old device is deleted and recreated as a component device. This is acceptable — the device DNI is stable, so any SharpTools tiles or Rule Machine rules referencing the DNI remain valid after the hub picks up the new device. Users re-authorizing the device in SharpTools is the only expected friction.

No rollback shim is provided; the old behavior was a bug-of-omission, not a deliberate feature.

## Files changed

- `apps/climate-advisor/climate-advisor-app.groovy` — isComponent flip, version bump, changelog, paragraph clarification
- `drivers/climate-advisor/climate-advisor-device.groovy` — version bump, changelog
- `apps/climate-advisor/packageManifest.json` — version 0.3.2
- `packageManifest.json` (root) — Climate Advisor app + driver entries 0.3.2
- `apps/climate-advisor/README.md` — architecture section clarified
