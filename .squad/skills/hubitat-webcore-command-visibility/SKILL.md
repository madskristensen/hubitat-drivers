# Skill: Hubitat WebCoRE Command Visibility

**Confidence:** Medium (confirmed by Mads' production WebCoRE install, 2026-05-16)

---

## Problem

You have a Hubitat driver that implements a standard capability (e.g. `LightEffects`) **and** wants to expose a custom overload of that capability's method with a different parameter type (e.g. a String name instead of a Number index). The custom overload is declared in `definition{}` as a `command`. Users report the command is not visible in WebCoRE's action picker.

---

## Root Cause

WebCoRE builds its action picker from Hubitat's **capability metadata registry**, not from the driver's raw Groovy method table. When you declare `capability "LightEffects"`, Hubitat registers `setEffect(NUMBER)` as the canonical signature for that capability. Any `command "setEffect"` entry in the same `definition{}` block is a Groovy *overload* of the same method name — but WebCoRE only exposes the capability-registered signature. The custom overload is **shadowed and invisible** to WebCoRE regardless of how it is declared in VSCT / `definition{}`.

This is not a WebCoRE bug; it is a consequence of how Hubitat separates capability metadata from driver source.

---

## Fix: Use a Distinct Command Name

Give the WebCoRE-facing command a **different name** so there is no overload collision with the capability method:

```groovy
// In definition{}:
command "playEffectByName", [[name: "name*", type: "STRING",
    description: "Play a Gemstone effect by name. Use this from WebCoRE — the standard setEffect command only accepts a number."]]

// As a method (one-liner delegate — no duplicated validation logic):
def playEffectByName(String name) {
    setEffect(name)
}
```

WebCoRE now sees `playEffectByName` as a distinct command with a STRING parameter and renders a text input field in its action picker.

---

## When to Apply

Apply this pattern any time ALL of the following are true:

1. The driver declares a standard Hubitat capability that registers a method (e.g. `LightEffects → setEffect`, `ColorControl → setColor`, `ThermostatSetpoint → setThermostatSetpoint`).
2. You want to expose an overloaded version of that method (different param type or count) to WebCoRE users.
3. The capability-defined signature does not match the param type the user needs (e.g. number vs. string).

---

## Anti-patterns to Avoid

- **Don't** try to make WebCoRE see both overloads by tweaking `command` declarations — it won't work; the capability descriptor always wins.
- **Don't** duplicate validation logic in the new command — delegate to the existing implementation.
- **Don't** name the new command `setEffectByName` if a `setEffect` capability overload already exists — choose a clearly distinct verb/noun (e.g. `playEffectByName`, `activateScene`, `triggerEffectNamed`).

---

## Reference

- Driver: `drivers/gemstone-lights/gemstone-lights.groovy` — `playEffectByName` added in v0.4.1
- Decision context: `.squad/decisions.md` (2026-05-16 WebCoRE overload-shadowing entry)
- Hubitat docs: capability definitions at https://docs2.hubitat.com/en/developer/driver/capability-list
