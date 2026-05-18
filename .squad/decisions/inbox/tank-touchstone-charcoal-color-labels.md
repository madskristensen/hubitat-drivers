# Decision: Touchstone DP 104 — Rename to "Charcoal Color" with Verified Labels

**Date:** 2026-05-17  
**Driver:** Touchstone / Tuya Fireplace  
**Version:** v0.1.17  
**Author:** Tank (Driver Developer)

## Context

The Touchstone Sideline Elite fireplace exposes DP 104 as an ember/log color picker with 12 palette slots. The driver historically called this feature "Log Color" (`setLogColor`, `logColor` attribute, `defaultLogColor` preference). This terminology was a guess.

Mads Kristensen supplied a second Tuya app screenshot showing the actual label the Tuya app uses for this control: **"Charcoal"** (or "Charcoal Color"). This confirmed the internal name was wrong.

## Decision

Rename all driver references from "Log Color" to "Charcoal Color":

| Old | New |
|-----|-----|
| `setLogColor(number)` | `setCharcoalColor("LabelName")` |
| `attribute "logColor"` | `attribute "charcoalColor"` |
| `defaultLogColor` preference | `defaultCharcoalColor` preference |
| `LOG_COLOR_OPTIONS` (numeric strings) | `CHARCOAL_COLOR_OPTIONS` + lookup maps |

## Authoritative Label Mapping (DP 104)

Verified from Tuya app palette picker in app order (left-to-right, top-to-bottom):

| DP value | Label       | Notes |
|----------|-------------|-------|
| "1"      | Orange      | Default selected in app |
| "2"      | Red         | |
| "3"      | Blue        | |
| "4"      | Yellow      | |
| "5"      | Green       | |
| "6"      | Purple      | |
| "7"      | Cyan        | |
| "8"      | Magenta     | |
| "9"      | White       | |
| "10"     | Pink        | |
| "11"     | Rainbow     | 8-segment multi-color pie chart |
| "12"     | Spotlight   | ⚠️ Best-guess — mostly-white circle with small orange wedge in app |

## Breaking Change

This is a **breaking rename**. No backward-compat alias was added. Existing Rule Machine automations using `setLogColor(N)` must be migrated to `setCharcoalColor("LabelName")`.

Existing `defaultLogColor` numeric preferences (saved from v0.1.14) will not match new label strings and are silently skipped on the next power-on. Users must re-select from the new ENUM dropdown.

## Rationale

- Naming commands to match the Tuya app reduces confusion when users cross-reference the app and the Hubitat driver.
- Converting from NUMBER to named ENUM prevents invalid inputs and provides a user-friendly dropdown in the Hubitat UI.
- "Spotlight" is acknowledged as a best-guess label; it will be updated if the real Tuya app label is supplied.
- No alias was added because the old `setLogColor(number)` signature is incompatible with the new `setCharcoalColor(string)` signature.
