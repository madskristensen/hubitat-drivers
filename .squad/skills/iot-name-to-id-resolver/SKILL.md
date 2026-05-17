---
name: "iot-name-to-id-resolver"
description: "Resolve user-facing IoT scene/effect names to opaque IDs with a cached catalog and safe fallback behavior."
domain: "iot"
confidence: "medium"
source: "earned"
---

## Context
Use this when an IoT driver or integration exposes effects, scenes, presets, or animations through opaque IDs but the automation surface should accept human-readable names.

## Rules
1. Cache a human-readable lookup map (`displayName -> id`) and preserve the original display name for logging/UI.
2. Normalize user input with trim + case-insensitive matching before resolving names.
3. Refresh the catalog automatically when empty or stale; a 1-hour TTL is a reasonable default unless the vendor spec says otherwise.
4. If activation needs more than the ID, cache a second compact map (`id -> activation payload`) alongside the display-name map.
5. Fetch every page from every relevant source (for example: built-in/vendor-managed plus user-saved content) before finalizing the catalog.
6. On duplicate visible names, prefer the user-owned/custom item over the vendor/built-in item and log that rule once.
7. On unknown names, log the sorted available names so users can self-discover valid inputs.
8. Reuse the existing auth/token-retry pipeline for catalog fetches instead of inventing a second network path.
9. If the vendor marks favorites/starred items, keep a separate ordered favorites map and build user-facing pickers favorites-first; decorate only the display labels so raw-name lookup still works without the prefix.

## Examples
- `drivers/gemstone-lights/gemstone-lights.groovy`
  - `refreshEffectCatalog()`
  - `setEffect(String name)`
  - `finalizeEffectCatalogRefresh()`

## Anti-Patterns
- Case-sensitive exact-match lookups only
- Caching IDs without the activation payload when the command endpoint needs the full object
- Re-fetching the catalog on every command even when a fresh cache exists
- Silently preferring a built-in effect over a same-name user preset
- Returning "unknown effect" without telling the user which names are valid
