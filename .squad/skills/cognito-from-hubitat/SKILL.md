---
name: "cognito-from-hubitat"
description: "Call AWS Cognito InitiateAuth correctly from a Hubitat driver and log failures without leaking secrets."
domain: "auth"
confidence: "low"
source: "earned"
---

## Context
Use this when a Hubitat Groovy driver talks directly to AWS Cognito User Pools over `asynchttpPost`, especially for `USER_PASSWORD_AUTH` login and `REFRESH_TOKEN_AUTH` token renewal.

## Rules
1. Send `POST https://cognito-idp.<region>.amazonaws.com/` with `Content-Type: application/x-amz-json-1.1`.
2. Use `X-Amz-Target: AWSCognitoIdentityProviderService.InitiateAuth` for direct `InitiateAuth` calls.
3. Keep the JSON body bare and AWS-shaped: `AuthFlow`, `ClientId`, and `AuthParameters` in PascalCase.
4. Use uppercase auth-parameter names exactly as Cognito expects: `USERNAME`, `PASSWORD`, and `REFRESH_TOKEN`.
5. Give Cognito calls at least 30 seconds of timeout headroom from Hubitat so handshake/DNS latency is not misdiagnosed as a payload bug.
6. On any auth failure, log request method/url, `Content-Type`, `X-Amz-Target`, body shape, `resp.hasError`, `resp.status`, `resp.errorMessage`, `resp.headers`, `resp.data`, and `resp.errorJson`.
7. Never log the password, refresh/access/id tokens, or the full ClientId. Log only the body shape and a truncated ClientId prefix.
8. If Hubitat reports `status=408`, inspect `resp.hasError()` and `resp.errorMessage` before assuming Cognito returned a true HTTP 408 response.

## Examples
- `drivers/gemstone-lights/gemstone-lights.groovy`
  - `buildCognitoParams`
  - `buildCognitoCallbackData`
  - `cognitoAuthCallback`
  - `logCognitoAuthFailure`

## Anti-Patterns
- `X-Amz-Target: AmazonCognitoIdentityProvider.InitiateAuth`
- `Content-Type: application/json`
- Wrapping the Cognito payload inside an extra envelope instead of posting the bare JSON body
- Lowercase `clientId`, `authParameters`, `username`, or `password` keys
- Logging credentials or tokens while debugging auth failures
- Treating every `408` as proof of a server-side timeout without checking Hubitat transport-error flags
