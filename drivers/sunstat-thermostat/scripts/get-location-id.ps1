<#
.SYNOPSIS
    Fetches your Watts Home locationId — run once when setting up the Hubitat SunStat driver.

.DESCRIPTION
    Reads the refresh_token from tokens.json (written by the homebridge-tekmar-wifi CLI),
    exchanges it for a fresh access_token at the Watts B2C endpoint, then calls
    GET https://home.watts.com/api/Location and prints your locationId.

    Paste the printed locationId into the parent device's "Watts Home location ID" preference
    in Hubitat to unblock discoverDevices.

.USAGE
    cd C:\Users\madsk\source\repos\homebridge-tekmar-wifi
    pwsh .\..\..\GitHub\hubitat-drivers\drivers\sunstat-thermostat\scripts\get-location-id.ps1

    Or from anywhere:
    pwsh "C:\Users\madsk\GitHub\hubitat-drivers\drivers\sunstat-thermostat\scripts\get-location-id.ps1"
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
$TokensFile  = "C:\Users\madsk\source\repos\homebridge-tekmar-wifi\tokens.json"
$TokenUrl    = "https://login.watts.io/tfp/wattsb2cap02.onmicrosoft.com/B2C_1A_Residential_UnifiedSignUpOrSignIn/oauth2/v2.0/token"
$ClientId    = "c832c38c-ce70-4ebc-83b6-b4548083ac90"
$Scope       = "https://wattsb2cap02.onmicrosoft.com/wattsapiresi/manage offline_access openid profile"
$LocationUrl = "https://home.watts.com/api/Location"
$ApiVersion  = "2.0"
$UserAgent   = "SunStatBootstrap/0.1.3"

# ---------------------------------------------------------------------------
# Step 1: Read refresh_token
# ---------------------------------------------------------------------------
if (-not (Test-Path $TokensFile)) {
    Write-Error "tokens.json not found at: $TokensFile`nRun: node dist/cli/index.js login  (from homebridge-tekmar-wifi directory)"
}
$stored = Get-Content $TokensFile -Raw | ConvertFrom-Json
$refreshToken = $stored.refresh_token
if (-not $refreshToken) {
    Write-Error "refresh_token field is empty in tokens.json"
}
Write-Host "✓ Read refresh_token from tokens.json ($($refreshToken.Length) chars)"

# ---------------------------------------------------------------------------
# Step 2: Exchange refresh_token for access_token
# ---------------------------------------------------------------------------
$body = @{
    grant_type    = "refresh_token"
    client_id     = $ClientId
    refresh_token = $refreshToken
    scope         = $Scope
}
Write-Host "→ Refreshing access token at Watts B2C endpoint…"
$tokenResp = Invoke-RestMethod -Uri $TokenUrl -Method POST -Body $body `
    -ContentType "application/x-www-form-urlencoded" -UserAgent $UserAgent
$accessToken = $tokenResp.access_token
if (-not $accessToken) {
    Write-Error "Token refresh failed — no access_token in response"
}
Write-Host "✓ Got fresh access_token ($($accessToken.Length) chars)"

# Optionally persist the rotated tokens back to tokens.json
$nowEpoch   = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$newStored  = @{
    access_token              = $tokenResp.access_token
    refresh_token             = $tokenResp.refresh_token
    expires_at                = $nowEpoch + [int]$tokenResp.expires_in
    refresh_token_expires_at  = $nowEpoch + [int]$tokenResp.refresh_token_expires_in
}
$newStored | ConvertTo-Json | Set-Content $TokensFile -Encoding utf8
Write-Host "✓ Saved rotated tokens back to tokens.json"

# ---------------------------------------------------------------------------
# Step 3: GET /Location
# ---------------------------------------------------------------------------
Write-Host "→ Calling GET $LocationUrl …"
$headers = @{
    Authorization = "Bearer $accessToken"
    "Api-Version" = $ApiVersion
    "User-Agent"  = $UserAgent
    Accept        = "application/json"
}
$locResp = Invoke-RestMethod -Uri $LocationUrl -Method GET -Headers $headers
# API wraps in { errorNumber: 0, errorMessage: null, body: [...] }
$locations = if ($locResp.body) { $locResp.body } else { $locResp }
if (-not $locations -or $locations.Count -eq 0) {
    Write-Error "No locations returned from /Location. Is your account set up?"
}

# ---------------------------------------------------------------------------
# Step 4: Print results
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "===== LOCATIONS FOUND =====" -ForegroundColor Cyan
$i = 0
foreach ($loc in $locations) {
    $i++
    Write-Host "  [$i] locationId : $($loc.locationId)" -ForegroundColor Yellow
    Write-Host "       name       : $($loc.name)"
    Write-Host "       isDefault  : $($loc.isDefault)"
    Write-Host "       devices    : $($loc.devicesCount)"
    Write-Host ""
}
Write-Host "============================" -ForegroundColor Cyan
Write-Host ""
Write-Host "ACTION: Copy the locationId above and paste it into the" -ForegroundColor Green
Write-Host "        'Watts Home location ID' preference on the SunStat parent device in Hubitat." -ForegroundColor Green
Write-Host "        Then press 'discoverDevices' again." -ForegroundColor Green
