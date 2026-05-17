# Skill: Handling Long Secrets in Hubitat Drivers

**Domain:** Hubitat device driver development (Groovy, sandboxed)  
**Applies to:** Any driver that requires a long secret at setup (OAuth refresh tokens, API keys >1024 chars, JWT tokens)

---

## The Problem

Hubitat's preference UI has a hard limit of approximately **1024 characters** for `text` and `password` preference values. Attempting to save a longer value produces "failed to save preferences" — no further error detail. This is a platform limit, not a user error.

Tokens from modern identity providers (Azure AD B2C, AWS Cognito, Google OAuth 2.0) routinely issue refresh tokens of 1500–3000+ characters.

---

## Dead Ends (Don't Try These)

| Approach | Why it fails |
|---|---|
| `mappings { path(...) }` endpoint in driver | **Only Apps can define URL mappings.** Device drivers cannot host HTTP endpoints. |
| Split across multiple preferences (token1, token2...) | Forces user to manually count/split characters and re-split after every token rotation. Hostile UX. |
| External file storage | No file I/O in the Hubitat sandbox. |

---

## Recommended Pattern: Command Parameter Input

### Mechanism

Hubitat command `STRING` parameters are passed as method arguments, not stored through the preference system. They bypass the preference length limit entirely. Community usage confirms multi-thousand character strings work via command input.

### Implementation

**1. In `metadata { definition { ... } }`** — declare the command:

```groovy
command "setRefreshToken", [[name: "token*", type: "STRING",
    description: "Paste your full refresh token here"]]
```

**2. Implement the command:**

```groovy
def setRefreshToken(String token) {
    String t = token?.trim() ?: ""
    if (t.size() < 100) {
        log.warn "[MyDriver] setRefreshToken: token too short (${t.size()} chars)"
        return
    }
    log.info "[MyDriver] Refresh token stored (${t.size()} chars)"  // NEVER log token chars
    state.refreshToken = t
    state.remove("accessToken")      // force re-auth on next operation
    state.remove("tokenExpiresAt")
    initialize()
}
```

**3. Bootstrap check — use `state` only, no preference fallback:**

```groovy
private boolean tokenBootstrapReady() {
    return safeStr(state.refreshToken).size() > 0
}
```

**4. Do NOT add a `refreshToken` preference.** Remove it entirely. The command is the sole input path.

### User Flow

1. Install driver, save preferences (no token field — nothing blocks).
2. Go to device page → **Commands** tab.
3. Find **setRefreshToken**, paste full token, click **Run**.
4. Driver logs `Refresh token stored (NNNN chars)` and begins operating.

### README Note (copy-paste ready)

```markdown
> **Why the Commands tab, not Preferences?**  
> Watts Home refresh tokens are ~2000 characters. Hubitat's Preferences page
> limits saved values to ~1024 characters, causing "failed to save preferences".
> The `setRefreshToken` command bypasses this limit entirely.
```

---

## Security Considerations

- `state.*` is **not encrypted at rest** on Hubitat hubs. `password` preferences are encrypted.
- This is an acceptable trade-off for self-hosted hub owners (the hub is on their LAN, behind their auth).
- **Always log only the token length, never any portion of the token value.**
- If encryption at rest is required, the correct architecture is a companion **App** (Apps can store OAuth credentials in encrypted app state and handle token refresh without exposing the token to the driver preferences page).

---

## Fallback: If Command Parameter Also Fails

If the user reports the command input also rejects long strings (Hubitat does not formally document the command parameter limit):

**Option B: Hub Variable**

1. User creates a Hub String Variable (Settings → Hub Variables) — no documented size limit.
2. User pastes token into the variable.
3. Driver reads: `location.variable("myTokenVarName")`
4. Add a `text` preference for the variable name (short string, no length issue).

UX is worse (two-step setup, requires Hub Variables knowledge), but avoids all platform string limits.

---

## Related Platform Facts

| Platform element | Length limit | Notes |
|---|---|---|
| `text` / `password` preference value | ~1024 chars | Community-sourced; not officially documented |
| Command `STRING` parameter | ~4096+ chars | Anecdotal community reports; not officially documented |
| `state.*` value | No practical limit | Stored in hub DB; very large values may affect hub memory |
| Hub Variables | No documented limit | Visible to all drivers/apps on hub |
| `mappings {}` | N/A — Apps only | Drivers cannot define URL endpoints |
