---
name: "watts-home-auth"
description: "Azure AD B2C auth flow for the Watts® Home API (home.watts.com) used by SunStat Connect Plus and Tekmar WiFi thermostats."
domain: "protocol"
confidence: "high"
source: "reverse-engineered via seanami/homebridge-tekmar-wifi"
---

## Context

The Watts® Home API (`https://home.watts.com/api`) is the cloud backend for Watts Water Technologies North America products including SunStat Connect Plus and Tekmar WiFi thermostats. Authentication is **Azure AD B2C with OAuth 2.0 PKCE** — not AWS Cognito, and not the EU Watts Vision API.

## Auth Constants

```
LOGIN_BASE  = https://login.watts.io
TENANT      = wattsb2cap02.onmicrosoft.com
POLICY      = B2C_1A_Residential_UnifiedSignUpOrSignIn
CLIENT_ID   = c832c38c-ce70-4ebc-83b6-b4548083ac90
REDIRECT_URI= msalc832c38c-ce70-4ebc-83b6-b4548083ac90://auth
SCOPE       = https://wattsb2cap02.onmicrosoft.com/wattsapiresi/manage offline_access openid profile
TOKEN_URL   = https://login.watts.io/tfp/wattsb2cap02.onmicrosoft.com/B2C_1A_Residential_UnifiedSignUpOrSignIn/oauth2/v2.0/token
```

## Token Lifetimes

- **Access token:** 15 minutes (900 seconds)
- **Refresh token:** 90 days (7,776,000 seconds)
- **Refresh tokens rotate** — each refresh issues a NEW refresh token; the old one is invalidated immediately. Always persist the new refresh token.

## Token Refresh (simple — implement in Hubitat)

```http
POST https://login.watts.io/tfp/wattsb2cap02.onmicrosoft.com/B2C_1A_Residential_UnifiedSignUpOrSignIn/oauth2/v2.0/token
Content-Type: application/x-www-form-urlencoded

client_id=c832c38c-ce70-4ebc-83b6-b4548083ac90
&grant_type=refresh_token
&refresh_token={STORED_REFRESH_TOKEN}
&client_info=1
&scope=https://wattsb2cap02.onmicrosoft.com/wattsapiresi/manage%20offline_access%20openid%20profile
```

Response:
```json
{
  "access_token": "eyJhbGci...",
  "refresh_token": "eyJraWQ...",
  "expires_in":    900,
  "expires_on":    1768718583,
  "refresh_token_expires_in": 7776000
}
```

## Initial Login (complex — do outside Hubitat)

Initial login requires OAuth2 PKCE with multi-step HTML form scraping (extract CSRF token from HTML → POST credentials → follow redirect → exchange code for tokens). Not feasible in Groovy.

**Recommended Hubitat bootstrap approach:**
1. User runs `node dist/cli/index.js login` from the `seanami/homebridge-tekmar-wifi` CLI
2. Copies `access_token`, `refresh_token`, and `expires_on` (Unix epoch int) from `tokens.json` into driver preferences
3. Driver handles all subsequent refresh internally

**Alternative (unconfirmed):** Check if ROPC policy exists:
```
POST https://login.watts.io/tfp/wattsb2cap02.onmicrosoft.com/B2C_1A_ResourceOwnerPasswordCredentials/oauth2/v2.0/token
grant_type=password&client_id={CLIENT_ID}&scope={SCOPE}&username={email}&password={pw}
```
If this returns 200 with tokens, initial auth can be done in Hubitat as a simple POST.

## Hubitat Driver Pattern

```groovy
// State keys
// state.accessToken  (String)
// state.refreshToken (String)
// state.tokenExpiresAt (Long, Unix epoch seconds)

private String getValidToken() {
    Long nowSecs = Math.round(now() / 1000.0d) as Long
    if (state.tokenExpiresAt && nowSecs > (state.tokenExpiresAt - 300)) {
        refreshTokens()
    }
    return state.accessToken
}

private void refreshTokens() {
    def params = [
        uri: "https://login.watts.io",
        path: "/tfp/wattsb2cap02.onmicrosoft.com/B2C_1A_Residential_UnifiedSignUpOrSignIn/oauth2/v2.0/token",
        contentType: "application/json",
        requestContentType: "application/x-www-form-urlencoded",
        body: "client_id=c832c38c-ce70-4ebc-83b6-b4548083ac90" +
              "&grant_type=refresh_token" +
              "&refresh_token=${state.refreshToken}" +
              "&client_info=1" +
              "&scope=https%3A%2F%2Fwattsb2cap02.onmicrosoft.com%2Fwattsapiresi%2Fmanage%20offline_access%20openid%20profile"
    ]
    httpPost(params) { resp ->
        if (resp.status == 200) {
            state.accessToken    = resp.data.access_token
            state.refreshToken   = resp.data.refresh_token   // always update!
            state.tokenExpiresAt = resp.data.expires_on as Long
        } else {
            log.error "Token refresh failed: ${resp.status}"
        }
    }
}
```

## API Request Headers

All calls to `https://home.watts.com/api/*` require:
```
Authorization: Bearer {access_token}
Api-Version:   2.0
Content-Type:  application/json
```

## Important Warnings

- **Refresh token rotation is critical.** If you call refresh but don't persist the new `refresh_token`, the next refresh will fail with 401. Always `state.refreshToken = resp.data.refresh_token`.
- **Watts Vision is NOT the same API.** `smarthome.wattselectronics.com` is a European product by Watts Electronics, unrelated to SunStat/Tekmar.
- **15-minute token expiry.** Even at 60 s polling, the driver will need to refresh the access token every 15 minutes. The `getValidToken()` guard runs before every HTTP call.

## Source

`seanami/homebridge-tekmar-wifi`: https://github.com/seanami/homebridge-tekmar-wifi  
— `src/lib/api/auth.ts`, `docs/AUTHENTICATION.md` (captured 2026-01-18)
