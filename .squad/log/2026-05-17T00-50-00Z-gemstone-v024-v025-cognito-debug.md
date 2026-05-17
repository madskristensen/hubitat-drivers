# Session Log — Gemstone Lights Cognito v0.2.4–v0.2.5 Debug Journey

**Date:** 2026-05-16
**UTC Timestamp:** 2026-05-17T00:50:00Z
**Agent:** Tank
**Repository:** hubitat-drivers
**Issue:** Gemstone authentication failed (HTTP 408)

---

## Story Arc

### Act I: The HTTP 408 Wall

Driver v0.2.3 shipped with working Hubitat sandbox workarounds (no `System.currentTimeMillis()`, no `UUID.randomUUID()`), but authentication against AWS Cognito was failing with a generic HTTP 408 status. The error message `Gemstone authentication failed (HTTP 408).` offered no insight into whether the problem was:
- A local Hubitat network/transport failure
- A malformed Cognito request
- An actual AWS timeout
- A TLS/DNS issue

This opacity made debugging impossible without live Hubitat logs.

### Act II: Hypothesis Generation

Reviewed the Cognito request builder:
- **JSON body**: Correctly shaped with PascalCase field names (`AuthFlow`, `ClientId`, `AuthParameters`).
- **Auth params**: Correct casing (`USERNAME`, `PASSWORD` for login; `REFRESH_TOKEN` for refresh).
- **Content-Type**: Already set to `application/x-amz-json-1.1` (AWS Cognito requirement).
- **X-Amz-Target**: Set to `AmazonCognitoIdentityProvider.InitiateAuth`.

Hypothesis: The `X-Amz-Target` header value might be wrong for direct Cognito `InitiateAuth` calls.

AWS documentation confirmed: Direct Cognito `InitiateAuth` should use `AWSCognitoIdentityProviderService.InitiateAuth`, not `AmazonCognitoIdentityProvider.InitiateAuth`.

### Act III: v0.2.4 — Diagnostic Logging

Shipped v0.2.4 with:
1. Corrected `X-Amz-Target` header to `AWSCognitoIdentityProviderService.InitiateAuth`.
2. Comprehensive secret-safe diagnostic logging:
   - Request metadata (method, URL, headers names)
   - Response status, error message, headers, body (raw JSON)
   - Timeout detection logic

The diagnostic layer would now expose the real failure mode when live Hubitat executed the auth.

### Act IV: The Real Cause Emerges

During v0.2.4 validation, re-reviewed Hubitat's `asynchttpPost` documentation and test cases. Discovered: **Hubitat has no encoder for `application/x-amz-json-1.1`**.

When the params map specifies `contentType: application/x-amz-json-1.1`, Hubitat fails locally:
```
No encoder found for request content type application/x-amz-json-1.1
```

This manifests as a synthesized `status=408` / `hasError=true` in the response object — not a real AWS response.

**Conclusion:** The 408 error is not coming from AWS. Hubitat's HTTP client is rejecting the request locally because it doesn't know how to encode the body for that content type.

### Act V: v0.2.5 — The Fix

The fix is clean:
1. Keep the **wire header** `Content-Type: application/x-amz-json-1.1` in the `headers` map (required by AWS).
2. Switch the **encoder hint** `contentType` parameter to `application/json` (tells Hubitat to use the built-in JSON encoder).
3. Pre-serialize the body with `JsonOutput.toJson(...)` to ensure it's already JSON before Hubitat processes it.

Both Cognito paths (`USER_PASSWORD_AUTH` and `REFRESH_TOKEN_AUTH`) were already using a shared builder, so the fix applies to both in one change.

Applied the same pattern to Gemstone API calls for consistency.

### Act VI: Resolution

v0.2.5 is now ready for live testing:
- Cognito will see the correct `X-Amz-Target` header (fixed in v0.2.4).
- Hubitat will not reject the request locally (fixed in v0.2.5).
- If auth still fails, the v0.2.4 diagnostic logs will reveal the real cause.

---

## Mechanics Summary

| Version | Change | Why | Risk | Status |
|---------|--------|-----|------|--------|
| v0.2.3 | Sandbox audit (System/UUID/Date cleanup) | Runtime security | Low | ✓ Stable |
| v0.2.4 | X-Amz-Target header + diagnostics | Cognito spec + visibility | Low | ⏳ Awaiting live confirmation |
| v0.2.5 | Encoder hint + pre-serialization | Hubitat HTTP quirk fix | Low | 🚀 Ready for testing |

---

## Lessons for Future Sessions

1. **Encoder Quirk**: Hubitat's `asynchttpPost` method requires the `contentType` parameter to match a known encoder. Wire headers in `headers` map are independent; use both to get the full picture.
2. **Pre-serialization Pattern**: Always pre-serialize JSON strings before passing to Hubitat's HTTP client. Hubitat is more reliable when it receives a String `body` with an explicit content-type hint.
3. **Diagnostic Logging**: When auth fails with generic codes (408, 401, etc.), add multi-layer logging (request shape, response metadata, parsed error body) so future sessions can trace the exact failure point.
4. **Shared Builders**: Request builders (Cognito, API, etc.) are high-ROI refactoring targets. One fix applied to the builder covers all call sites.

---

## Files Modified
- `drivers/gemstone-lights/gemstone-lights.groovy` (v0.2.3 → v0.2.4 → v0.2.5)
- `.squad/skills/hubitat-sandbox-pitfalls/SKILL.md` (extended with HTTP encoder quirk section)

## Files Created
- `.squad/decisions/inbox/tank-cognito-408-debug-v024.md` (merged to decisions.md)
- `.squad/decisions/inbox/tank-hubitat-encoder-quirk-v025.md` (merged to decisions.md)
- `.squad/orchestration-log/2026-05-17T00-50-00Z-tank.md` (this session's orchestration log)
- `.squad/log/2026-05-17T00-50-00Z-gemstone-v024-v025-cognito-debug.md` (this file)

## Next Immediate Action
Deploy v0.2.5 to a live Hubitat hub and confirm successful Cognito authentication with one of:
1. Device login with fresh username/password.
2. Device refresh-token flow.

Live success = ready for general release. Live failure = review v0.2.4 diagnostic logs + reopen investigation.
