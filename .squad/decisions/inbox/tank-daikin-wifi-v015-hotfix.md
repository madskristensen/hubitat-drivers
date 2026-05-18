# Decision: daikin-wifi v0.1.5 hotfix — unclosed GString + empty log interpolation

**Date:** 2026-05-18  
**Author:** Tank (Driver Developer)  
**Driver:** `drivers/daikin-wifi/daikin-wifi.groovy`  
**Commit:** 6e90625

---

## Bug 1 — Unclosed triple-quote string literal (line 705)

**Symptom:** Hubitat rejected the driver at load time with a Groovy parse error.

**Root cause:** `String advRaw = kv.adv ?: """` — three quotes opened a multiline GString that was never closed. Tank-6 intended `""` (empty-string fallback) but accidentally typed `"""`.

**Fix:** Changed `?: """` → `?: ""`

---

## Bug 2 — Empty log interpolation (line 701)

**Symptom:** The warning log `[Daikin] get_special_mode: ret=` emitted no actual return value, making it useless for debugging.

**Root cause:** `log.warn "[Daikin] get_special_mode: ret="` — the `kv.ret` value was never interpolated.

**Fix:** Changed to `log.warn "[Daikin] get_special_mode: ret=${kv.ret}"`

---

## Broader lesson — always grep for `"""` after editing GString fallback expressions

A triple-quote in Groovy is not a syntax error at the point it appears — it opens a valid multiline GString. The parse error surfaces only when the file ends without a closing `"""`. This makes it easy to miss in review. 

**Pre-commit checklist addition:** Run `grep '"""'` on any Groovy driver file after touching GString fallback expressions (`?: ""` patterns). Zero matches expected in executable code.
