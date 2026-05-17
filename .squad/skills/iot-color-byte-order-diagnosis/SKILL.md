---
name: "iot-color-byte-order-diagnosis"
description: "Diagnose ARGB vs ABGR byte-order mismatches when IoT device colors render wrong after an RGB setColor call."
domain: "iot-drivers"
confidence: "high"
source: "earned"
---

## Context
This skill applies when an IoT device driver packs a color as a 32-bit integer and sends it to a cloud API, but the rendered color on the physical device differs from what was requested.

## Diagnostic Pattern: Red→Blue, Green→Green, Blue→Red

When you observe:
- **Red** renders as **blue** (or a bluish tint)
- **Green** renders as **green** (correct)
- **Blue** renders as **red**

…this is the exact empirical signature of an **ARGB vs ABGR byte-order mismatch**.

### Why green is invariant
In a 32-bit color word `0xAA_XX_GG_YY`, green always occupies the middle byte (bits 15–8). Swapping the R and B byte positions does not move green — so green-channel colors appear correct while red and blue are swapped.

### Root cause
- Driver packs: `0xAARRGGBB` (ARGB)
- API wire format: `0xAABBGGRR` (ABGR)
- Result: R and B channels are swapped on the device

### Fix
Swap the R and B byte positions in every color-packing function:

```groovy
// WRONG (ARGB): R at bits 23-16, B at bits 7-0
return ((0xFFL << 24) | ((r & 0xFFL) << 16) | ((g & 0xFFL) << 8) | (b & 0xFFL)) as Long

// CORRECT (ABGR): B at bits 23-16, R at bits 7-0
return ((0xFFL << 24) | ((b & 0xFFL) << 16) | ((g & 0xFFL) << 8) | (r & 0xFFL)) as Long
```

Also reverse the assignment in any color-unpacking function (reading ABGR back into r/g/b variables):

```groovy
// ABGR unpack: high data byte is B, low data byte is R
float b = ((argbLong >> 16) & 0xFFL) / 255.0f
float g = ((argbLong >> 8)  & 0xFFL) / 255.0f
float r = (argbLong         & 0xFFL) / 255.0f
```

## Confirmed by Real Device
Gemstone Lights cloud API (AWS API Gateway, `mytpybpq12.execute-api.us-west-2.amazonaws.com`) confirmed ABGR wire format in v0.4.5 empirical testing (2026-05-16). Pure red `0xFFFF0000` packed as ARGB → device displayed blue. Fix: swap to ABGR encoding.

## Quick Reference
| Sent (ARGB packed) | Device interprets as (ABGR) | Visible color |
|---|---|---|
| `0xFFFF0000` (red)   | R=0, G=0, B=255   | Blue          |
| `0xFF00FF00` (green) | R=0, G=255, B=0   | Green ✓       |
| `0xFF0000FF` (blue)  | R=255, G=0, B=0   | Red           |
