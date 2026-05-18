/**
 * TouchstoneHelpersSpec.groovy
 * Spock unit tests for the pure-function helpers in the Touchstone Fireplace driver.
 *
 * Coverage:
 *   - CRC32 lookup table and crc32() computation        (tests 1-3)
 *   - intToBytes / readUInt32 round-trip                (test 4)
 *   - buildTuyaFrame structure validation               (tests 5-6)
 *   - parseTuyaFrameRaw round-trip (build → parse)      (test 7)
 *   - FLAME_COLOR label↔DP round-trips                 (test 8)
 *   - CHARCOAL_COLOR label↔DP round-trips              (test 9)
 *   - FLAME_BRIGHTNESS label↔DP round-trips            (test 10)
 *   - FLAME_SPEED label↔DP round-trips                 (test 11)
 *   - full write→parse round-trip                      (test 12)
 *
 * What is NOT tested here:
 *   - installed() / updated() / initialize() / parse() / socketStatus()
 *   - sendEvent(), log, device, state, settings — all Hubitat sandbox globals
 *   - Network I/O (interfaces.rawSocket)
 *   - Any method that reads/writes Hubitat state
 */
package touchstone

import spock.lang.Specification
import spock.lang.Unroll

class TouchstoneHelpersSpec extends Specification {

    // -----------------------------------------------------------------------
    // Test key — 16 ASCII chars, valid AES-128 key for test purposes only
    // -----------------------------------------------------------------------
    static final byte[] TEST_KEY = "abcdefghijklmnop".getBytes("UTF-8")

    // -----------------------------------------------------------------------
    // 1. CRC32 table is initialised correctly — spot-check against known values
    // -----------------------------------------------------------------------

    def "CRC32 table has 256 entries and entry[0] == 0"() {
        expect:
        TouchstoneHelpers.CRC32_TABLE.length == 256
        TouchstoneHelpers.CRC32_TABLE[0] == 0L
    }

    def "crc32 of empty byte array equals known CRC32 value"() {
        // Standard CRC32 of empty input = 0x00000000
        expect:
        TouchstoneHelpers.crc32(new byte[0]) == 0x00000000L
    }

    def "crc32 of standard test vector matches java.util.zip.CRC32"() {
        // "hello" = 0x3610A686 — verified against java.util.zip.CRC32
        given:
        byte[] data = "hello".getBytes("UTF-8")
        java.util.zip.CRC32 jdkCrc = new java.util.zip.CRC32()
        jdkCrc.update(data)
        long expected = jdkCrc.getValue()

        expect:
        TouchstoneHelpers.crc32(data) == expected
    }

    // -----------------------------------------------------------------------
    // 4. intToBytes / readUInt32 round-trip
    // -----------------------------------------------------------------------

    @Unroll
    def "intToBytes → readUInt32 round-trip for #hex"() {
        given:
        byte[] bytes = TouchstoneHelpers.intToBytes(value)

        expect:
        TouchstoneHelpers.readUInt32(bytes, 0) == value

        where:
        hex          | value
        "0x000055AA" | 0x000055AAL   // TUYA_PREFIX
        "0x0000AA55" | 0x0000AA55L   // TUYA_SUFFIX
        "0x00000000" | 0x00000000L
        "0xFFFFFFFF" | 0xFFFFFFFFL
        "0xDEADBEEF" | 0xDEADBEEFL
    }

    // -----------------------------------------------------------------------
    // 5. buildTuyaFrame — structural validation
    // -----------------------------------------------------------------------

    def "buildTuyaFrame produces frame with correct prefix, suffix, and cmd"() {
        given:
        String payload = '{"gwId":"abc","devId":"abc","t":"1234567890"}'
        byte[] frame = TouchstoneHelpers.buildTuyaFrame(
            TouchstoneHelpers.TUYA_CMD_DP_QUERY, payload, TEST_KEY, 1L)

        when:
        Map parsed = TouchstoneHelpers.parseTuyaFrameRaw(frame)

        then:
        parsed.prefix   == TouchstoneHelpers.TUYA_PREFIX
        parsed.suffix   == TouchstoneHelpers.TUYA_SUFFIX
        parsed.cmd      == TouchstoneHelpers.TUYA_CMD_DP_QUERY as Long
        parsed.crcOk    == true
        parsed.suffixOk == true
    }

    def "buildTuyaFrame heartbeat has empty payload and no version header"() {
        given:
        // Heartbeat (cmd 9) must produce an empty encrypted payload — no AES, no version header.
        byte[] frame = TouchstoneHelpers.buildTuyaFrame(
            TouchstoneHelpers.TUYA_CMD_HEARTBEAT, "", TEST_KEY, 1L)

        when:
        Map parsed = TouchstoneHelpers.parseTuyaFrameRaw(frame)

        then:
        parsed.cmd         == TouchstoneHelpers.TUYA_CMD_HEARTBEAT as Long
        parsed.crcOk       == true
        // Heartbeat encrypted payload is empty; frame = 24 bytes minimum
        // payloadBytes should be 0 bytes (frame.length - 24 = 24 - 24 = 0)
        (parsed.payloadBytes as byte[]).length == 0
        frame.length == 24
    }

    // -----------------------------------------------------------------------
    // 7. Full write → parse round-trip (build a CONTROL frame, parse it back)
    // -----------------------------------------------------------------------

    def "buildTuyaFrame → parseTuyaFrameRaw round-trip restores seqNo and cmd"() {
        given:
        long seqNo   = 42L
        int  cmd     = TouchstoneHelpers.TUYA_CMD_CONTROL
        String json  = '{"devId":"abc123","uid":"abc123","t":"1716000000","dps":{"1":true}}'
        byte[] frame = TouchstoneHelpers.buildTuyaFrame(cmd, json, TEST_KEY, seqNo)

        when:
        Map parsed = TouchstoneHelpers.parseTuyaFrameRaw(frame)

        then:
        parsed.seqNo  == seqNo
        parsed.cmd    == cmd as Long
        parsed.crcOk  == true

        and: "decrypting the payload restores the original JSON"
        // CONTROL cmd has version header: protocol33HeaderBytes() = 3-char "3.3" + 12 zero bytes = 15 bytes
        // The payload = versionHeader(15) + aesEncrypt(json, key)
        byte[] payload = parsed.payloadBytes as byte[]
        // Strip the 15-byte version header, then AES-decrypt the remainder
        byte[] encrypted = TouchstoneHelpers.sliceBytes(payload, 15, payload.length - 15)
        String decrypted = new String(
            TouchstoneHelpers.aesDecrypt(encrypted, TEST_KEY), "UTF-8")
        decrypted == json
    }

    // -----------------------------------------------------------------------
    // 8. FLAME_COLOR round-trip
    // -----------------------------------------------------------------------

    def "FLAME_COLOR_OPTIONS covers all 6 values and all round-trip correctly"() {
        expect:
        TouchstoneHelpers.FLAME_COLOR_OPTIONS.size() == 6

        // label → DP → label restores original
        TouchstoneHelpers.FLAME_COLOR_OPTIONS.every { label ->
            String dp  = TouchstoneHelpers.FLAME_COLOR_TO_DP[label]
            dp != null && TouchstoneHelpers.DP_TO_FLAME_COLOR[dp] == label
        }
    }

    def "FLAME_COLOR unknown label returns null — no DP mapping exists"() {
        expect:
        TouchstoneHelpers.FLAME_COLOR_TO_DP["NotAColor"] == null
        TouchstoneHelpers.DP_TO_FLAME_COLOR["99"]        == null
    }

    // -----------------------------------------------------------------------
    // 9. CHARCOAL_COLOR round-trip
    // -----------------------------------------------------------------------

    def "CHARCOAL_COLOR_OPTIONS covers all 12 values and round-trips correctly"() {
        expect:
        TouchstoneHelpers.CHARCOAL_COLOR_OPTIONS.size() == 12

        TouchstoneHelpers.CHARCOAL_COLOR_OPTIONS.every { label ->
            String dp  = TouchstoneHelpers.CHARCOAL_COLOR_TO_DP[label]
            dp != null && TouchstoneHelpers.DP_TO_CHARCOAL_COLOR[dp] == label
        }
    }

    @Unroll
    def "CHARCOAL_COLOR label '#label' maps to DP '#expectedDp'"() {
        expect:
        TouchstoneHelpers.CHARCOAL_COLOR_TO_DP[label] == expectedDp

        where:
        label       | expectedDp
        "Orange"    | "1"
        "Red"       | "2"
        "Blue"      | "3"
        "Yellow"    | "4"
        "Green"     | "5"
        "Purple"    | "6"
        "Cyan"      | "7"
        "Magenta"   | "8"
        "White"     | "9"
        "Pink"      | "10"
        "Rainbow"   | "11"
        "Spotlight" | "12"
    }

    // -----------------------------------------------------------------------
    // 10. FLAME_BRIGHTNESS round-trip
    // -----------------------------------------------------------------------

    def "FLAME_BRIGHTNESS_OPTIONS covers all 5 values and round-trips correctly"() {
        expect:
        TouchstoneHelpers.FLAME_BRIGHTNESS_OPTIONS.size() == 5

        TouchstoneHelpers.FLAME_BRIGHTNESS_OPTIONS.every { label ->
            String dp  = TouchstoneHelpers.FLAME_BRIGHTNESS_TO_DP[label]
            dp != null && TouchstoneHelpers.DP_TO_FLAME_BRIGHTNESS[dp] == label
        }
    }

    // -----------------------------------------------------------------------
    // 11. FLAME_SPEED round-trip
    // -----------------------------------------------------------------------

    def "FLAME_SPEED maps cover Slow/Medium/Fast and round-trip correctly"() {
        expect:
        TouchstoneHelpers.FLAME_SPEED_TO_DP.size() == 3
        ["Slow", "Medium", "Fast"].every { label ->
            String dp  = TouchstoneHelpers.FLAME_SPEED_TO_DP[label]
            dp != null && TouchstoneHelpers.DP_TO_FLAME_SPEED[dp] == label
        }
    }

    // -----------------------------------------------------------------------
    // Bonus: conversion helpers
    // -----------------------------------------------------------------------

    @Unroll
    def "celsiusToFahrenheit(#c) == #f"() {
        expect:
        TouchstoneHelpers.celsiusToFahrenheit(c) == f

        where:
        c  | f
        0  | 32
        20 | 68
        25 | 77
        30 | 86
    }

    @Unroll
    def "fahrenheitToCelsius(#f) ≈ #c (rounded)"() {
        expect:
        TouchstoneHelpers.fahrenheitToCelsius(f) == c

        where:
        f  | c
        32 | 0
        68 | 20
        77 | 25
        86 | 30
    }
}
