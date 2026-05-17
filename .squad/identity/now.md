---
updated_at: 2026-05-16T21:45:13Z
focus_area: Gemstone Lights v0.1.0 — scaffold complete, awaiting local-API capture
active_issues: []
---

# What We're Focused On

Gemstone Lights driver scaffold (v0.1.0) is in place at `drivers/gemstone-lights/`. Cloud protocol is fully documented in `.squad/decisions.md` (Cypher's spec). The driver's `sendCommand()` / `parse()` are deliberately stubbed pending **Mads' LAN capture** of the Gemstone app talking to the controller at 192.168.1.238 — that capture is the next milestone. Once the local API is known, Tank wires real `HubAction` calls and Switch runs the manual test plan in `drivers/gemstone-lights/TESTING.md`.

Repo conventions, READMEs (top + per-driver), `LICENSE` (MIT), `.gitignore`, and `.gitattributes` are in place. Repo is **not yet `git init`'d** — that's also pending the user.
