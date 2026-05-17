# Skill: Tuya Cloud Local Key Extraction (tinytuya Path)

**Confidence:** Medium (verified end-to-end, 2026-05-17)  
**Status:** Complete ✅

## Context

Extracting the `local_key` credential from a Tuya device is a one-time, non-reversible operation required to enable local LAN control via `interfaces.rawSocket` in Hubitat drivers. The key identifies the device and is used for AES-encrypted communication on the local network.

## Prerequisites

- Tuya WiFi device (confirmed through SmartLife app)
- Python 3.8+ with `pip`
- Administrator access to a Windows/Mac/Linux machine on the same LAN as target device
- Tuya IoT Cloud account (free tier with 1-month free trial, no card required)

## Step-by-Step Playbook

### 1. Create Tuya IoT Cloud Project

1. Go to https://iot.tuya.com
2. Sign up with email or WeChat (free account)
3. Create new Cloud Project (any name, e.g., "Hubitat Integration")
4. In **Products** tab, add your device (search by product code or model number)
   - Example: Touchstone Sideline Elite → product code `nc1lwvgjse1ujlr` (Tuya category "qn" = electric fireplace)

### 2. Subscribe Required APIs (CRITICAL — Not Auto-Enabled)

**This is the #1 blocker that causes silent wizard failures.** A new Cloud Project does NOT automatically subscribe to required APIs.

Go to **Service → API Management** and manually enable (all have free trials):

- ✅ **IoT Core**
- ✅ **Authorization Token Management**
- ✅ **Smart Home Basic Service**
- ✅ **Device Status Notification**

Confirm each shows "Trial subscribed" (green). No credit card required.

**Why:** The `tinytuya wizard` queries these APIs to fetch your device's DP map. If any API is not subscribed, the wizard fails silently (returns empty device list).

### 3. Link SmartLife Account

1. In Cloud Project, go to **Assets → Devices**
2. Click **Link SmartLife Account**
3. Scan QR code with SmartLife app on phone
4. Authorize the Cloud Project to access your SmartLife devices
5. Your device should now appear in the **Assets → Devices** list

### 4. Prepare tinytuya (Python)

```powershell
# Windows PowerShell
pip install tinytuya --upgrade
python -m tinytuya scan
```

This performs a network scan to find your device's local IP and model info. Example output:

```
[DEVICE_ID_HERE] 70223053e8db84d10b53 [192.168.1.38] 3.3
```

Note the IP address (e.g., `192.168.1.38`) — you'll need this for the driver.

### 5. Run tinytuya Wizard

```powershell
python -m tinytuya wizard
```

When prompted:

- **API Access ID:** Leave blank (press Enter) — not needed for this path
- **API Access Secret:** Leave blank (press Enter)
- **Device ID:** Paste from Cloud Project or tinytuya scan output (e.g., `70223053e8db84d10b53`)
- **Local IP:** Paste from tinytuya scan (e.g., `192.168.1.38`)
- **API Key (local_key):** **This is what you need.** Copy from Cloud Project → Device → Click Device → View Details → **Auth Key** field (16–32 character hex string)

**Do NOT let wizard attempt auto-detect; paste manually.**

### 6. Save Credentials Securely

Wizard writes to `devices.json` in your current directory. Example:

```json
{
  "devices": [
    {
      "name": "Touchstone Fireplace",
      "id": "70223053e8db84d10b53",
      "key": "uDH...(16–32 chars)...xyz",
      "ip": "192.168.1.38",
      "ver": "3.3"
    }
  ]
}
```

**⚠️ SECURITY:**
- Store `devices.json` **outside the Git repo** (e.g., `C:\Users\<username>\devices.json`)
- **NEVER commit the local_key to version control**
- Reference the stored key path in documentation only: `<stored at C:\Users\madsk\devices.json>`

### 7. Verify Local Connectivity

```python
import tinytuya
import json

with open("devices.json") as f:
    config = json.load(f)["devices"][0]

device = tinytuya.OutletDevice(
    dev_id=config["id"],
    address=config["ip"],
    local_key=config["key"],
    version=config["ver"]
)

status = device.status()
print(json.dumps(status, indent=2))
```

Expected output: DP map with integer keys and values:

```json
{
  "dps": {
    "1": true,
    "2": 22,
    "3": 18,
    "101": "1",
    "102": "5",
    ...
  }
}
```

If this works, **local key extraction is complete and verified.**

## Key Learnings

1. **API subscription is manual** — New Tuya IoT Cloud Projects do NOT auto-enable required APIs. This blocks the wizard with a silent failure (empty device list). Always check all four APIs are "Trial subscribed" before running wizard.

2. **local_key is permanent** — Once extracted, it persists until the device is re-paired with SmartLife. Cloud account status doesn't matter afterward.

3. **Network isolation** — Device and Hubitat hub must be on the same LAN (not different VLANs or subnets). Local control requires direct rawSocket connectivity.

4. **Single TCP connection** — Most Tuya modules allow only one active TCP connection at a time. Close the SmartLife app before the Hubitat driver connects.

5. **Security:** Never inline the local_key in README or chat logs. Reference only the storage path.

## References

- Tuya IoT Platform: https://iot.tuya.com
- tinytuya GitHub: https://github.com/jasonacox/tinytuya
- Tuya API docs: https://developer.tuya.com/en/docs/iot/device-control-reference
