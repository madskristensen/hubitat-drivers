# Skill: hubitat-upstream-feature-gap-pattern

**Confidence:** low  
**Validated:** 2026-05-18 — eriktack/hubitat-daikin-wifi v1.0.3 vs madskristensen v0.1.5 comparison by Cypher  
**Author:** Cypher

---

## Summary

When building a **clean-room driver** that reimplements or improves on an upstream open-source driver, a disciplined feature-gap audit prevents both:
- **Feature parity misses** — we ship without a feature the upstream has
- **Scope creep** — we add ten features when three would suffice

**Pattern:** Read upstream code for **IDEAS**, not code. Classify findings, rank by value × ease, then prioritize.

---

## The Audit Workflow

### Step 1: Inventory Commands, Attributes, Capabilities

Create a side-by-side table: upstream column, downstream column, status column.

**Upstream example (eriktack/hubitat-daikin-wifi):**

```
| Upstream command | Status vs Ours |
|---|---|
| fan | Delegate to mode → ✅ Covered via setThermostatMode("fan") |
| dry | Delegate to mode → ✅ Covered via setThermostatMode("dry") |
| setFanRate(number) | ✅ We have ENUM (better typing) |
| fanDirectionVertical | Toggle helper → 🚫 We use direct setter (better for RM) |
| tempUp | +0.5° step → 🚫 Missing (nice-to-have) |
```

### Step 2: Classify Findings

For each gap, label as one of:

| Label | Meaning | Example |
|---|---|---|
| ✅ Covered | We have equivalent via different name/mechanism | `targetTemp` (upstream) vs our `thermostatSetpoint` (standard Hubitat) |
| ➖ We're ahead | We have feature upstream lacks | `energyMeter` capability (upstream didn't implement energy polling) |
| 🚫 Missing | Upstream has, we don't; would be useful | `tempUp` / `tempDown` commands |
| 🟡 Design trade-off | Upstream uses different approach; document why ours is better/worse | Direct setters vs toggle commands for swing mode |
| 🔴 Won't-do | Upstream feature that doesn't apply or conflicts with design | Upstream's single unified `targetTemp` setpoint vs our standard `heatingSetpoint` / `coolingSetpoint` |

### Step 3: Rank by Value × Ease

For each `🚫 Missing` item, estimate:

| Factor | Estimate | Examples |
|---|---|---|
| **Value** | High / Medium / Low | High = solves a user pain; Medium = nice UX; Low = rarely used |
| **Ease** | Easy (<1h) / Medium (1–2h) / Hard (>2h) | Easy = reuse existing pattern; Medium = new logic; Hard = needs research/testing |

Then prioritize: **High value + Easy = do first**. **High value + Hard = defer to next release**. **Low value = skip**.

---

## Daikin WiFi Example

From `.squad/files/daikin-research/daikin-upstream-gap-audit.md`:

### Commands

```
| Upstream | Status | Rank |
|---|---|---|
| tempUp / tempDown | 🚫 Missing | High value + Easy → v0.1.7 candidate |
| fanDirectionVertical / Horizontal | 🟡 Design trade-off | We use direct setSwingMode ENUM → cleaner |
```

### Attributes

```
| Upstream | Status | Rank |
|---|---|---|
| energyYesterday, energyThisYear, energyLastYear | 🚫 Missing | Medium value + Medium ease → v0.1.7 candidates |
| statusText | 🚫 Missing (equivalent: setpointDisplay) | Medium value + Medium ease → downstream of Trinity's citizen checklist |
| currMode | 🚫 Missing (redundant: use thermostatMode) | Low value → skip |
```

### Verdict

**Top 3 v0.1.7 additions:**
1. `tempUp(n)` / `tempDown(n)` commands (easy, high UX value)
2. `energyYesterday` attribute (medium effort, useful dashboard metric)
3. `setpointDisplay` attribute (Trinity's name for upstream's `statusText`, helps dashboards)

**Reason we skip:**
- `currMode` — redundant with `thermostatMode`; Hubitat's standard name is `thermostatMode`
- `fanAPISupport` — we handle silently via `.isNumber()` guards; no need to expose

---

## Integration with Clean-Room Development

This pattern **only applies when:**
- You're implementing a clean-room driver (no upstream code reuse)
- You have access to the upstream source (public GitHub repo)
- You want to ensure feature parity OR document intentional gaps

**It does NOT apply when:**
- Porting/refactoring existing code (different concern)
- No upstream exists (original implementation)
- Legal restrictions prevent reading upstream (rare)

---

## Output Format

Document findings in a decision memo (saved in `.squad/decisions/inbox/`):

```markdown
# Daikin WiFi — Upstream Feature-Gap Audit

**Upstream:** eriktack/hubitat-daikin-wifi v1.0.3
**Downstream:** madskristensen/hubitat-drivers daikin-wifi v0.1.5
**Clean-room:** Yes — no code reuse, ideas only

## Commands

| Upstream | Status | Notes |
| ... | ... | ... |

## Top N Recommendations

1. [description] — [value×ease] — [version]
```

Save for review by the team.

---

## References

- Daikin audit: `.squad/files/daikin-research/daikin-upstream-gap-audit.md`
- Daikin clean-room driver: `drivers/daikin-wifi/daikin-wifi.groovy` (v0.1.5+)
- Upstream: [eriktack/hubitat-daikin-wifi](https://github.com/eriktack/hubitat-daikin-wifi)
