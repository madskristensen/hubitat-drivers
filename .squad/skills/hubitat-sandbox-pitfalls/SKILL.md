---
name: "hubitat-sandbox-pitfalls"
description: "Avoid Hubitat sandbox and HTTP client failures caused by cross-@Field initializers, blocked JDK APIs, and non-standard content types."
domain: "groovy"
confidence: "high"
source: "earned"
---

## Context
This skill applies when editing Hubitat drivers that use top-level `@Field static final` constants, JDK utility APIs, or Hubitat HTTP helpers that must talk to APIs with non-standard wire `Content-Type` requirements.

## Rules
1. **Parse-time restriction:** `@Field static final` initializers in Hubitat drivers MUST NOT reference any other `@Field` constant — not via GString `${X}`, not via concatenation `+ X`, not at all. Inline literals or compute the derived value inside a method body.
2. **Runtime restriction:** Hubitat blocks or discourages several JDK/Groovy APIs at runtime, including `System.*`, `Thread.*`, `Runtime.*`, reflection helpers, and file I/O. Prefer Hubitat builtins such as `now()`, `pauseExecution(ms)`, `runIn`, `runEvery*`, `state`, `atomicState`, `http*`, and `log.*`.
3. **HTTP encoder restriction:** Hubitat's HTTP helpers only know how to encode request bodies for `application/json`, `application/x-www-form-urlencoded`, and `text/xml`. If the target API requires another wire content type, pre-serialize the body to a String and set the real wire `Content-Type` in `headers`, while leaving `contentType` / `requestContentType` as `application/json`.

## HTTP Client Content-Type Encoder Quirk
Hubitat's `asynchttpPost` / `asynchttpGet` / `httpPost` only have body encoders for `application/json`, `application/x-www-form-urlencoded`, and `text/xml`. If your target API requires an unusual Content-Type (AWS services use `application/x-amz-json-1.1` and similar), you MUST pre-serialize the body to a String yourself and pass it via `body: jsonString`. Then set the wire Content-Type via the `headers` map. Use `contentType: "application/json"` in the params solely to keep Hubitat's encoder check happy — it won't actually be used. Set `requestContentType: "application/json"` too for compatibility across Hubitat versions.

## Safe Replacement Table
| Avoid | Use instead |
|---|---|
| `System.currentTimeMillis()` | `now()` |
| `new Date()` | `new Date(now())` |
| `Thread.sleep(ms)` | `pauseExecution(ms)` |
| `Thread.*`, executors | `runIn`, `runEvery1Minute`, `runEvery5Minutes`, etc. |
| `System.out.*`, `System.err.*` | `log.info`, `log.warn`, `log.error`, `log.debug` |
| reflection / `Class.forName` / `.getClass()` tricks | direct type references or explicit branching |
| `File`, `Files`, `java.nio.*` writes | `state`, `atomicState`, HTTP, or preferences |
| `UUID.randomUUID()` when sandbox behavior is uncertain | a `now()`-based id helper |
| non-standard wire `Content-Type` (for example AWS `application/x-amz-json-1.1`) | pre-serialize `body` to a JSON String, put the real header in `headers`, keep `contentType` / `requestContentType` at `application/json` |

## Audit Checklist
- Scan the entire driver for `System.`, `Thread.`, `Runtime.`, `Class.`, `.getClass(`, `Date.parse`, `new Date()`, `File(`, `Files.`, `Eval.`, `GroovyShell`, `GroovyClassLoader`, `MetaClass`, and `.eval(`.
- Re-grep after edits; do not assume the first failure is the only sandbox violation.
- If a JDK helper is not clearly safe in Hubitat, replace it with a Hubitat-native pattern.

## Examples
- `drivers/gemstone-lights/gemstone-lights.groovy`
  - Safe static literal: `@Field static final String USER_AGENT = "Hubitat Gemstone Lights/0.2.5"`
  - Safe runtime clock helper: `private Long currentEpochSeconds() { Math.round(now() / 1000.0d) as Long }`
  - Safe fallback id helper: `return "hubitat-${now()}-${nextSequence}"`
  - Safe AWS Cognito body pattern: `body: JsonOutput.toJson(payload)` with wire `Content-Type` set in `headers` and Hubitat `contentType` kept at `application/json`

## Anti-Patterns
- `@Field static final String USER_AGENT = "Hubitat Gemstone Lights/${DRIVER_VERSION}"`
- `@Field static final String USER_AGENT = "Hubitat Gemstone Lights/" + DRIVER_VERSION`
- `System.currentTimeMillis()` in auth/session helpers
- `UUID.randomUUID()` in driver code when a `now()`-based id is sufficient
- Any reflection, thread management, or file-system access inside a Hubitat driver
