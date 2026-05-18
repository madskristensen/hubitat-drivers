/**
 * TouchstoneHelpers.groovy
 * Pure-function helpers extracted from the Touchstone Fireplace driver
 * for unit testing outside the Hubitat sandbox.
 *
 * source: drivers/touchstone-fireplace/touchstone-fireplace.groovy@v0.1.18
 *
 * IMPORTANT: If touchstone-fireplace.groovy changes these helpers, update
 * this file in sync.
 * TODO: factor out properly once the POC is accepted.
 *
 * What IS testable here:
 *   - CRC32 lookup table + crc32() computation
 *   - intToBytes(), readUInt32(), concatBytes(), sliceBytes(), startsWithBytes()
 *   - protocol33HeaderBytes(), commandNeedsVersionHeader()
 *   - All label-↔-DP maps: FLAME_COLOR, CHARCOAL_COLOR, FLAME_BRIGHTNESS, FLAME_SPEED
 *   - buildTuyaFrame() (requires AES key + seqNo injection)
 *   - safeInt(), safeLong(), safeStr(), asBoolean(), coerceRawValue()
 *   - celsiusToFahrenheit(), fahrenheitToCelsius(), clampSetpoint()
 *   - normaliseTempUnit(), dpValueType()
 *
 * What is NOT testable (Hubitat sandbox globals):
 *   - installed(), updated(), initialize(), parse(), socketStatus()
 *   - sendEvent(), log, device, state, settings, interfaces, runIn, schedule
 *   - Any method that calls the above
 */
package touchstone

import groovy.transform.CompileStatic
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class TouchstoneHelpers {

    // -----------------------------------------------------------------------
    // Constants — verbatim from the driver
    // -----------------------------------------------------------------------

    static final String TUYA_VERSION = "3.3"
    static final Long   TUYA_PREFIX  = 0x000055AAL
    static final Long   TUYA_SUFFIX  = 0x0000AA55L
    static final int    TUYA_CMD_CONTROL    = 7
    static final int    TUYA_CMD_STATUS     = 8
    static final int    TUYA_CMD_HEARTBEAT  = 9
    static final int    TUYA_CMD_DP_QUERY   = 10
    static final int    TUYA_CMD_CONTROL_NEW = 13

    /** CRC32 lookup table — pure Groovy, no java.util.zip.CRC32 (blocked in Hubitat sandbox). */
    static final long[] CRC32_TABLE = (0..255).collect { int n ->
        long c = n as long
        8.times {
            c = ((c & 1L) != 0L) ? (0xEDB88320L ^ (c >>> 1)) : (c >>> 1)
        }
        c & 0xFFFFFFFFL
    } as long[]

    // Label → DP wire value maps
    static final Map<String, String> FLAME_COLOR_TO_DP = [
        "Orange":       "1",
        "Blue":         "2",
        "White":        "3",
        "Orange+Blue":  "4",
        "Orange+White": "5",
        "Blue+White":   "6"
    ]
    static final Map<String, String> DP_TO_FLAME_COLOR = [
        "1": "Orange",
        "2": "Blue",
        "3": "White",
        "4": "Orange+Blue",
        "5": "Orange+White",
        "6": "Blue+White"
    ]

    static final List<String> FLAME_COLOR_OPTIONS = [
        "Orange", "Blue", "White", "Orange+Blue", "Orange+White", "Blue+White"
    ]

    static final Map<String, String> FLAME_BRIGHTNESS_TO_DP = [
        "Dimmest": "1", "Dim": "2", "Medium": "3", "Brighter": "4", "Brightest": "5"
    ]
    static final Map<String, String> DP_TO_FLAME_BRIGHTNESS = [
        "1": "Dimmest", "2": "Dim", "3": "Medium", "4": "Brighter", "5": "Brightest"
    ]
    static final List<String> FLAME_BRIGHTNESS_OPTIONS = [
        "Dimmest", "Dim", "Medium", "Brighter", "Brightest"
    ]

    static final Map<String, String> CHARCOAL_COLOR_TO_DP = [
        "Orange": "1",  "Red": "2",     "Blue": "3",    "Yellow": "4",
        "Green": "5",   "Purple": "6",  "Cyan": "7",    "Magenta": "8",
        "White": "9",   "Pink": "10",   "Rainbow": "11","Spotlight": "12"
    ]
    static final Map<String, String> DP_TO_CHARCOAL_COLOR = [
        "1": "Orange",  "2": "Red",     "3": "Blue",    "4": "Yellow",
        "5": "Green",   "6": "Purple",  "7": "Cyan",    "8": "Magenta",
        "9": "White",   "10": "Pink",   "11": "Rainbow","12": "Spotlight"
    ]
    static final List<String> CHARCOAL_COLOR_OPTIONS = [
        "Orange", "Red", "Blue", "Yellow", "Green", "Purple",
        "Cyan", "Magenta", "White", "Pink", "Rainbow", "Spotlight"
    ]

    static final Map<String, String> FLAME_SPEED_TO_DP = ["Slow": "1", "Medium": "2", "Fast": "3"]
    static final Map<String, String> DP_TO_FLAME_SPEED  = ["1": "Slow", "2": "Medium", "3": "Fast"]

    // -----------------------------------------------------------------------
    // CRC32
    // -----------------------------------------------------------------------

    static long crc32(byte[] data) {
        long crc = 0xFFFFFFFFL
        for (byte b : data) {
            crc = CRC32_TABLE[((int)(crc ^ (b & 0xFF))) & 0xFF] ^ (crc >>> 8)
        }
        return (crc ^ 0xFFFFFFFFL) & 0xFFFFFFFFL
    }

    // -----------------------------------------------------------------------
    // Byte helpers
    // -----------------------------------------------------------------------

    static byte[] intToBytes(Long value) {
        byte[] bytes = new byte[4]
        bytes[0] = ((value >> 24) & 0xFF) as byte
        bytes[1] = ((value >> 16) & 0xFF) as byte
        bytes[2] = ((value >> 8)  & 0xFF) as byte
        bytes[3] =  (value        & 0xFF) as byte
        return bytes
    }

    static Long readUInt32(byte[] data, int offset) {
        return ((data[offset]     & 0xFFL) << 24) |
               ((data[offset + 1] & 0xFFL) << 16) |
               ((data[offset + 2] & 0xFFL) <<  8) |
                (data[offset + 3] & 0xFFL)
    }

    static byte[] concatBytes(byte[]... arrays) {
        int totalLength = 0
        for (byte[] part : arrays) { totalLength += part?.length ?: 0 }
        byte[] combined = new byte[totalLength]
        int offset = 0
        for (byte[] part : arrays) {
            if (!part) continue
            for (int i = 0; i < part.length; i++) {
                combined[offset + i] = part[i]
            }
            offset += part.length
        }
        return combined
    }

    static byte[] sliceBytes(byte[] source, int start, int length) {
        byte[] copy = new byte[length]
        for (int i = 0; i < length; i++) { copy[i] = source[start + i] }
        return copy
    }

    static boolean startsWithBytes(byte[] data, byte[] prefix) {
        if (!data || !prefix || data.length < prefix.length) return false
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false
        }
        return true
    }

    static byte[] protocol33HeaderBytes() {
        byte[] versionBytes = TUYA_VERSION.getBytes("UTF-8")
        byte[] header = new byte[versionBytes.length + 12]
        for (int i = 0; i < versionBytes.length; i++) { header[i] = versionBytes[i] }
        return header
    }

    static boolean commandNeedsVersionHeader(int cmd) {
        return !(cmd in [TUYA_CMD_DP_QUERY, TUYA_CMD_HEARTBEAT])
    }

    // -----------------------------------------------------------------------
    // AES helpers
    // -----------------------------------------------------------------------

    static byte[] aesEncrypt(byte[] plaintext, byte[] keyBytes) {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return cipher.doFinal(plaintext)
    }

    static byte[] aesDecrypt(byte[] ciphertext, byte[] keyBytes) {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        return cipher.doFinal(ciphertext)
    }

    // -----------------------------------------------------------------------
    // Frame builder (testable version — accepts seqNo directly, no state)
    // -----------------------------------------------------------------------

    /** Build a Tuya 3.3 frame. seqNo is injected so no state dependency. */
    static byte[] buildTuyaFrame(int cmd, String payloadJson, byte[] keyBytes, long seqNo) {
        byte[] payloadBytes   = payloadJson.getBytes("UTF-8")
        byte[] encryptedPayload = encryptTuyaPayload(cmd, payloadBytes, keyBytes)
        byte[] withoutCrc = concatBytes(
            intToBytes(TUYA_PREFIX),
            intToBytes(seqNo),
            intToBytes(cmd as Long),
            intToBytes((encryptedPayload.length + 8) as Long),
            encryptedPayload
        )
        long crc = crc32(withoutCrc)
        return concatBytes(withoutCrc, intToBytes(crc), intToBytes(TUYA_SUFFIX))
    }

    static byte[] encryptTuyaPayload(int cmd, byte[] payloadBytes, byte[] keyBytes) {
        if (cmd == TUYA_CMD_HEARTBEAT) return new byte[0]
        byte[] encrypted = aesEncrypt(payloadBytes, keyBytes)
        if (!commandNeedsVersionHeader(cmd)) return encrypted
        return concatBytes(protocol33HeaderBytes(), encrypted)
    }

    /**
     * Parse an OUTGOING Tuya frame built by buildTuyaFrame().
     * Outgoing frame layout (no retcode field):
     *   prefix(4) + seqNo(4) + cmd(4) + length(4) + encPayload(N) + CRC(4) + suffix(4)
     *   minimum size = 24 bytes (N=0 for heartbeat)
     *
     * NOTE: INCOMING frames from the device insert a retcode(4) field after length,
     * shifting payload to offset 20. That is handled by processFrame() in the real driver,
     * not here — these tests cover the outgoing (built) frame format only.
     *
     * Returns a map with: [prefix, seqNo, cmd, wireLen, payloadBytes, storedCrc, actualCrc, suffix, crcOk, suffixOk]
     */
    static Map<String, Object> parseTuyaFrameRaw(byte[] frame) {
        if (frame.length < 24) throw new IllegalArgumentException("Frame too short: ${frame.length}")
        long prefix    = readUInt32(frame, 0)
        long seqNo     = readUInt32(frame, 4)
        long cmd       = readUInt32(frame, 8)
        long wireLen   = readUInt32(frame, 12)
        long suffix    = readUInt32(frame, frame.length - 4)
        long storedCrc = readUInt32(frame, frame.length - 8)
        long actualCrc = crc32(sliceBytes(frame, 0, frame.length - 8))
        // payload sits between the 16-byte header and the 8-byte footer (CRC + suffix)
        int payloadLen = frame.length - 24
        byte[] payload = payloadLen > 0 ? sliceBytes(frame, 16, payloadLen) : new byte[0]
        return [
            prefix      : prefix,
            seqNo       : seqNo,
            cmd         : cmd,
            wireLen     : wireLen,
            payloadBytes: payload,
            suffix      : suffix,
            storedCrc   : storedCrc,
            actualCrc   : actualCrc,
            crcOk       : (storedCrc == actualCrc),
            suffixOk    : (suffix == TUYA_SUFFIX)
        ]
    }

    // -----------------------------------------------------------------------
    // Conversion helpers (pure, no sandbox globals)
    // -----------------------------------------------------------------------

    static Integer safeInt(Object value, Integer fallback = null) {
        if (value == null) return fallback
        try { return value as Integer }
        catch (ignored) {
            try { return Integer.parseInt(value.toString().trim()) }
            catch (ignored2) { return fallback }
        }
    }

    static Long safeLong(Object value, Long fallback = null) {
        if (value == null) return fallback
        try { return value as Long }
        catch (ignored) {
            try { return Long.parseLong(value.toString().trim()) }
            catch (ignored2) { return fallback }
        }
    }

    static String safeStr(Object value) {
        return value == null ? null : value.toString()
    }

    static Boolean asBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value
        String text = safeStr(value)?.trim()?.toLowerCase()
        if (text in ["true", "on",  "1"]) return true
        if (text in ["false","off", "0"]) return false
        return null
    }

    static Object coerceRawValue(String value) {
        String trimmed = safeStr(value)?.trim()
        if (trimmed == null) return ""
        if (trimmed.equalsIgnoreCase("true"))  return true
        if (trimmed.equalsIgnoreCase("false")) return false
        if (trimmed ==~ /^-?\d+$/) return Integer.parseInt(trimmed)
        return trimmed
    }

    static Integer celsiusToFahrenheit(Integer celsius) {
        return Math.round((celsius * 9.0d / 5.0d) + 32.0d) as Integer
    }

    static Integer fahrenheitToCelsius(Integer fahrenheit) {
        return Math.round((fahrenheit - 32.0d) * 5.0d / 9.0d) as Integer
    }

    static Integer clampSetpoint(Integer value, String unit) {
        if (unit == "C") return Math.max(19, Math.min(30, value))
        return Math.max(67, Math.min(88, value))
    }

    static String normaliseTempUnit(Object value) {
        String text = safeStr(value)?.trim()?.toUpperCase()
        if (!text) return null
        if (text.startsWith("F")) return "F"
        if (text.startsWith("C")) return "C"
        return null
    }

    static String dpValueType(Object value) {
        if (value == null)                     return "null"
        if (value instanceof Boolean)          return "bool"
        if (value instanceof Byte || value instanceof Short ||
            value instanceof Integer || value instanceof Long ||
            value instanceof BigInteger)       return "int"
        if (value instanceof Float || value instanceof Double ||
            value instanceof BigDecimal)       return "decimal"
        if (value instanceof String || value instanceof GString) return "string"
        if (value instanceof List)             return "list"
        if (value instanceof Map)              return "map"
        return "object"
    }
}
