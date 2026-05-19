/**
 *  Philio PST02 Multi-Sensor (PST02-A/B/C) — Hubitat Fork
 *  Author:  Mads Kristensen
 *  Version: 1.1.0 — 2026-05-19
 *  License: Apache-2.0
 *
 *  Fork of kunPet/Denny Page "Philio PST02" Hubitat driver.
 *  Original upstream: Denny Page (PAT02), adapted for PST02 by kunPet.
 *  Forked because upstream does not accept PRs; this version replaces raw
 *  bitmask parameter inputs with human-readable guided dropdowns and adds
 *  Hubitat best-practice hardening.
 *
 *  Changelog:
 *    1.1.0 — 2026-05-19 — Fix implicit global in SecurityMessageEncapsulation; remove duplicate ConfigurationReport case 12 and dangling break; fix log.warn misuse in configure/refresh/updated; guard WakeUpNotification log.debug with logEnable; re-enable auto-disable debug logging after 30 min; remove German upstream comments.
 *    1.0.0 — 2026-05-19 — Initial Mads fork. Replace raw para5/para6/para7 bitmask inputs with guided human-readable dropdowns derived from Z-Wave JS device configs. Add variant auto-detection (PST02-A/C vs PST02-B), raw-override mode, and Hubitat-standard header/logging.
 */

metadata
{
    definition (
        name: "Philio PST02 Enhanced",
        namespace: "mads",
        author: "Mads Kristensen",
        importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/philio-pst02/philio-pst02.groovy"
    )
    {
        capability "TemperatureMeasurement"
        capability "Sensor"
        capability "Refresh"
        capability "Configuration"
        capability "Battery"
        capability "TamperAlert"
		capability "Contact Sensor"					//PST-AC only
		capability "Motion Sensor"					//PST-AB only
		capability "Illuminance Measurement"		//PST only

        command "clearTamper"

		fingerprint  mfr:"013C", prod:"0002", deviceId:"000E", inClusters:"0x5E,0x72,0x86,0x59,0x73,0x5A,0x8F,0x98,0x7A", outClusters:"0x20"	//PST-C , dec=14
		fingerprint  mfr:"013C", prod:"0002", deviceId:"000C", inClusters:"0x5E,0x72,0x86,0x59,0x73,0x5A,0x8F,0x98,0x7A", outClusters:"0x20"	//PST-A , dec=12
		fingerprint  mfr:"013C", prod:"0002", deviceId:"000D", inClusters:"0x5E,0x72,0x86,0x59,0x73,0x5A,0x8F,0x98,0x7A", outClusters:"0x20"	//PST-B , dec=13

        // 0x30 COMMAND_CLASS_SENSOR_BINARY_V2 (removed in later firmware)
        // 0x31 COMMAND_CLASS_SENSOR_MULTILEVEL_V5 (later firmware uses V11)
        // 0x59 COMMAND_CLASS_ASSOCIATION_GRP_INFO
        // 0x5A COMMAND_CLASS_DEVICE_RESET_LOCALLY
        // 0x5E COMMAND_CLASS_ZWAVEPLUS_INFO_V2
        // 0x70 COMMAND_CLASS_CONFIGURATION
        // 0x71 COMMAND_CLASS_NOTIFICATION_V4 (later firmware uses V8)
        // 0x72 COMMAND_CLASS_MANUFACTURER_SPECIFIC_V2
        // 0x73 COMMAND_CLASS_POWERLEVEL
        // 0x7A COMMAND_CLASS_FIRMWARE_UPDATE_MD_V2
        // 0x80 COMMAND_CLASS_BATTERY
        // 0x84 COMMAND_CLASS_WAKE_UP_V2
        // 0x85 COMMAND_CLASS_ASSOCIATION_V2
        // 0x86 COMMAND_CLASS_VERSION_V2 (later firmware uses V3)
        // 0x8F COMMAND_CLASS_MULTI_CMD
        // 0x98 COMMAND_CLASS_SECURITY
        // 0x9F COMMAND_CLASS_SECURITY_2 (only in later firmware)
    }
}

preferences
{
    // Device values not configurable by this driver but logged when configure: Parameter 3, 4, 9, 12, 22
	// !! button "configure" sets Defaults specified under deviceSync() - preferenence values are set after "set preference"
	
    input name: "parameterMode", title: "Parameter mode (5/6/7)", type: "enum",
          options: ["guided": "Guided (recommended)", "raw": "Advanced raw values"], defaultValue: "guided"
    input name: "variantOverride", title: "Sensor variant", type: "enum",
          options: ["auto": "Auto-detect from fingerprint", "pst02_ac": "PST02-A/C", "pst02_b": "PST02-B"], defaultValue: "auto"

    input name: "p5TestMode", title: "P5: Test mode", type: "bool", defaultValue: false
    input name: "p5DisableDoorWindow", title: "P5: Disable door/window function (A/C only)", type: "bool", defaultValue: false
    input name: "p5TempScale", title: "P5: Temperature scale", type: "enum",
          options: ["f": "Fahrenheit", "c": "Celsius"], defaultValue: "c"
    input name: "p5DisableIlluminationOnTrigger", title: "P5: Disable illumination report on trigger", type: "bool", defaultValue: true
    input name: "p5DisableTemperatureOnTrigger", title: "P5: Disable temperature report on trigger", type: "bool", defaultValue: true
    input name: "p5DisableBackKeyTest", title: "P5: Disable back key release into test mode", type: "bool", defaultValue: false

    input name: "p6DisableMagneticIllumination", title: "P6: Disable magnetic + illumination integration (A/C only)", type: "bool", defaultValue: false
    input name: "p6DisablePirIllumination", title: "P6: Disable PIR + illumination integration", type: "bool", defaultValue: true
    input name: "p6DisableMagneticPir", title: "P6: Disable magnetic + PIR integration (A/C only)", type: "bool", defaultValue: true
    input name: "p6DifferentRoom", title: "P6: Device and controlled light are in different rooms (A/C only)", type: "bool", defaultValue: false
    input name: "p6DisableDoorCloseDelayOff", title: "P6: Disable 5-second delay off after door close (A/C only)", type: "bool", defaultValue: false
    input name: "p6DisableAutoTurnOff", title: "P6: Disable auto turn-off after door-open light on (A/C only)", type: "bool", defaultValue: false

    input name: "p7SendMotionOff", title: "P7: Send motion OFF report", type: "bool", defaultValue: true
    input name: "p7PirSuperSensitivity", title: "P7: PIR super sensitivity mode", type: "bool", defaultValue: true
    input name: "p7DisableBasicOffAfterDoorClose", title: "P7: Disable BASIC OFF after door closes (A/C only)", type: "bool", defaultValue: false
    input name: "p7NotificationType", title: "P7: Notification type", type: "enum",
          options: ["notification": "Notification report", "binary": "Binary Sensor report"], defaultValue: "binary"
    input name: "p7DisableMultiCcAutoReports", title: "P7: Disable multi-command encapsulated auto reports", type: "bool", defaultValue: false
    input name: "p7DisableBatteryOnTrigger", title: "P7: Disable battery report when triggered", type: "bool", defaultValue: true

    input name: "para5Raw", title: "Advanced raw: Parameter 5", description: "Used only when Parameter mode is Advanced raw values", type: "number", defaultValue: "56", range: "0..255"
    input name: "para6Raw", title: "Advanced raw: Parameter 6", description: "Used only when Parameter mode is Advanced raw values", type: "number", defaultValue: "6", range: "0..255"
    input name: "para7Raw", title: "Advanced raw: Parameter 7", description: "Used only when Parameter mode is Advanced raw values", type: "number", defaultValue: "86", range: "0..255"

	 // PIR Redetect interval: Parameter 8, Range 0-127, default 3 changed to 12, units of Ticks. 0 disables auto reporting.
    input name: "pirInterval", title: "P8: PIR Redetect Ticks", description: "8s per Tick", type: "number", defaultValue: "12", range: "0..127"

    // Auto Report Battery interval: Parameter 10, Range 0-127, default 12, units of Ticks. 0 disables auto reporting.
    input name: "batteryInterval", title: "P10: Battery Auto Report Ticks", description: "0 disables auto reporting", type: "number", defaultValue: "12", range: "0..127"

	// Auto Report Door interval: Parameter 11, Range 0-127, default 12, units of Ticks. 0 disables auto reporting.
    input name: "doorInterval", title: "P11: Door Auto Report Ticks", description: "0 disables auto reporting", type: "number", defaultValue: "12", range: "0..127"

    // Auto Report Door interval: Parameter 12, Range 0-127, default 12, units of Ticks. 0 disables auto reporting.
    input name: "luxInterval", title: "P12: Lux Auto Report Ticks", description: "0 disables auto reporting", type: "number", defaultValue: "12", range: "0..127"

    // Auto Report Temperature interval: Parameter 13, Range 0-127, default 12 changed to 2, units of Ticks. 0 disables auto reporting.
    input name: "temperatureInterval", title: "P13: Temperature Auto Report Ticks", description: "0 disables auto reporting", type: "number", defaultValue: "2", range: "0..127"

//		    // Auto Report Humidity interval: Parameter 14, Range 0-127, default 12, units of Ticks. 0 disables auto reporting.
//		    input name: "humidityInterval", title: "Humidity Auto Report Ticks", description: "0 disables auto reporting", type: "number", defaultValue: "12", range: "0..127"

//		    // Auto Report Water interval: Parameter 15, Range 0-127, default 12, units of Ticks. 0 disables auto reporting.
//		    input name: "waterInterval", title: "Water Auto Report Ticks", description: "0 disables auto reporting", type: "number", defaultValue: "12", range: "0..127"

    // Auto Report Tick interval: Parameter 20, Range 0-255, default 30, units of minutes. 0 disables all auto reporting.
    input name: "tickInterval", title: "P20: Auto Report Tick minutes", description: "0 disables ALL auto reporting. Set Wakeup interval minutes to match (min 30)", type: "number", defaultValue: "30", range: "0..255"
 
	// Temperature differential report: Parameter 21, Range 0-127, default 1 changed to 3, units of degrees Fahrenheit
    input name: "temperatureDifferential", title: "Temperature differential report", description: "0 disables differential reporting", type: "number", defaultValue: "3", range: "0..127"

//		    // Humidity differential report: Parameter 23, Range 0-60, default 5, units of percent RH%
//		    input name: "humidityDifferential", title: "Humidity differential report", description: "0 disables differential reporting", type: "number", defaultValue: "5", range: "0..60"

	// Wakeup Interval: Number of minutes between wakeups, default 1440 changed to 180. Device minimum is 30 minutes.
    input name: "wakeUpInterval", title: "Wakeup interval minutes", description: "Device minimum: 30. Should match P20 tick minutes for timely report delivery", type: "number", defaultValue: "30", range: "30..7200"

    // Temperature offset: Adjustment amount for temperature measurement
    input name: "temperatureOffset", title: "Temperature offset degrees", type: "decimal", defaultValue: "0"

 //		   // Humidity offset: Adjustment amount for humidity measurement
 //		   input name: "humidityOffset", title: "Humidity offset percent", type: "decimal", defaultValue: "0"

	input name: "logEnable", title: "Enable debug logging", type: "bool", defaultValue: true

	input name: "txtEnable", title: "Enable descriptionText logging", type: "bool", defaultValue: true
}

def setBit(Integer value, Integer bitMask, Boolean enabled)
{
    enabled ? (value | bitMask) : (value & (~bitMask))
}

def isPst02BVariant()
{
    if (variantOverride == "pst02_b") return true
    if (variantOverride == "pst02_ac") return false
    String deviceId = (getDataValue("deviceId") ?: "").toUpperCase()
    return deviceId == "000D"
}

def resolveConfigParam5()
{
    if (parameterMode == "raw") return (para5Raw != null ? para5Raw.toInteger() : (isPst02BVariant() ? 61 : 56))

    Integer value = 0
    value = setBit(value, 0x02, p5TestMode as Boolean)
    if (!isPst02BVariant()) value = setBit(value, 0x04, p5DisableDoorWindow as Boolean)
    value = setBit(value, 0x08, (p5TempScale ?: "c") == "c")
    value = setBit(value, 0x10, p5DisableIlluminationOnTrigger as Boolean)
    value = setBit(value, 0x20, p5DisableTemperatureOnTrigger as Boolean)
    value = setBit(value, 0x80, p5DisableBackKeyTest as Boolean)
    return value
}

def resolveConfigParam6()
{
    if (parameterMode == "raw") return (para6Raw != null ? para6Raw.toInteger() : 6)

    Integer value = 0
    value = setBit(value, 0x02, p6DisablePirIllumination as Boolean)
    if (!isPst02BVariant()) {
        value = setBit(value, 0x01, p6DisableMagneticIllumination as Boolean)
        value = setBit(value, 0x04, p6DisableMagneticPir as Boolean)
        value = setBit(value, 0x08, p6DifferentRoom as Boolean)
        value = setBit(value, 0x10, p6DisableDoorCloseDelayOff as Boolean)
        value = setBit(value, 0x20, p6DisableAutoTurnOff as Boolean)
    }
    return value
}

def resolveConfigParam7()
{
    if (parameterMode == "raw") return (para7Raw != null ? para7Raw.toInteger() : (isPst02BVariant() ? 22 : 86))

    Integer value = 0
    value = setBit(value, 0x02, p7SendMotionOff as Boolean)
    value = setBit(value, 0x04, p7PirSuperSensitivity as Boolean)
    if (!isPst02BVariant()) value = setBit(value, 0x08, p7DisableBasicOffAfterDoorClose as Boolean)
    value = setBit(value, 0x10, (p7NotificationType ?: "binary") == "binary")
    value = setBit(value, 0x20, p7DisableMultiCcAutoReports as Boolean)
    value = setBit(value, 0x40, p7DisableBatteryOnTrigger as Boolean)
    return value
}

def deviceSync()
{
    resync = state.pendingResync
    refresh = state.pendingRefresh

    state.pendingResync = false
    state.pendingRefresh = false

    if (logEnable) log.debug "deviceSync: pendingResync ${resync}, pendingRefresh ${refresh}"

    def cmds = []
    if (resync)
    {
        cmds.add(zwaveSecureEncap(zwave.versionV2.versionGet()))
    }

    value = resolveConfigParam5()
    if (resync || state.para5 != value)
    {
        log.warn "Updating device para5: ${value}"
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 5, size: 1)))
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 5)))
    }

    value = resolveConfigParam6()
    if (resync || state.para6 != value)
    {
        log.warn "Updating device para6: ${value}"
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 6, size: 1)))
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 6)))
    }

    value = resolveConfigParam7()
    if (resync || state.para7 != value)
    {
        log.warn "Updating device para7: ${value}"
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 7, size: 1)))
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 7)))
    }

    value = pirInterval ? pirInterval.toInteger() : 12	// Para 8
    if (resync || state.pirInterval != value)
    {
        log.warn "Updating device pirInterval: ${value}"
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 8, size: 1)))
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 8)))
    }

    value = batteryInterval ? batteryInterval.toInteger() : 12	//Para 10
    if (resync || state.batteryInterval != value)
    {
        log.warn "Updating device batteryInterval: ${value}"
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 10, size: 1)))
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 10)))
    }

    value = doorInterval ? doorInterval.toInteger() : 12	//Para 11
    if (resync || state.doorInterval != value)
    {
        log.warn "Updating device doorInterval: ${value}"
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 11, size: 1)))
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 11)))
    }
    
    value = luxInterval ? luxInterval.toInteger() : 12	//Para 12
    if (resync || state.luxInterval != value)
    {
        log.warn "Updating device luxInterval: ${value}"
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 12, size: 1)))
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 12)))
    }

    value = temperatureInterval ? temperatureInterval.toInteger() : 2	//Para 13
    if (resync || state.temperatureInterval != value)
    {
        log.warn "Updating device temperatureInterval: ${value}"
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 13, size: 1)))
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 13)))
    }

//    value = humidityInterval ? humidityInterval.toInteger() : 0	//Para 14
//	if (resync || state.humidityInterval != value)
//	{
//		log.warn "Updating device humidityInterval: ${value}"
//		cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 14, size: 1)))
//		cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 14)))
//	}
//
//	value = waterInterval ? waterInterval.toInteger() : 0	//Para 15
//    if (resync || state.waterInterval != value)
//    {
//        log.warn "Updating device waterInterval: ${value}"
//        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 15, size: 1)))
//        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 15)))
//	}

    value = tickInterval ? tickInterval.toInteger() : 30	//Para 20
    if (resync || state.tickInterval != value)
    {
        log.warn "Updating device tickInterval: ${value}"
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 20, size: 1)))
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 20)))
    }

    value = temperatureDifferential ? temperatureDifferential.toInteger() : 3	//Para 21
    if (resync || state.temperatureDifferential != value)
    {
        log.warn "Updating device temperatureDifferential: ${value}"
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 21, size: 1)))
        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 21)))
    }

//	value = humidityDifferential ? humidityDifferential.toInteger() : 0	//Para 23
//    if (resync || state.humidityDifferential != value)
//    {
//        log.warn "Updating device humidityDifferential: ${value}"
//        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 23, size: 1)))
//        cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 23)))
//    }

    value = wakeUpInterval ? wakeUpInterval.toInteger() : 180
    if (resync || state.wakeUpInterval != value)
    {
        log.warn "Updating device wakeUpInterval: ${value}"
        cmds.add(zwaveSecureEncap(zwave.wakeUpV2.wakeUpIntervalSet(seconds: value * 60, nodeid: zwaveHubNodeId)))
        cmds.add(zwaveSecureEncap(zwave.wakeUpV2.wakeUpIntervalGet()))
    }

	if (resync)
	{
	    cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 3)))
	    cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 4)))
		cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 9)))
		cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 12)))
		cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 22)))
	}

	if (refresh)
    {
        cmds.add(zwaveSecureEncap(zwave.batteryV1.batteryGet()))
        cmds.add(zwaveSecureEncap(zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1)))
        cmds.add(zwaveSecureEncap(zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 5)))

//		cmds.add(zwaveSecureEncap(zwave.notificationV4.notificationGet(notificationType: 5, v1AlarmType: 0, event: 0)))
//		cmds.add(zwaveSecureEncap(zwave.notificationV4.notificationGet(notificationType: 5, v1AlarmType: 0, event: 2)))
//		cmds.add(zwaveSecureEncap(zwave.notificationV4.notificationGet(notificationType: 6, v1AlarmType: 0, event: 22)))
//		cmds.add(zwaveSecureEncap(zwave.notificationV4.notificationGet(notificationType: 6, v1AlarmType: 0, event: 23)))
//		cmds.add(zwaveSecureEncap(zwave.notificationV4.notificationGet(notificationType: 7, v1AlarmType: 0, event: 3)))
//		cmds.add(zwaveSecureEncap(zwave.notificationV4.notificationGet(notificationType: 7, v1AlarmType: 0, event: 8)))
//		cmds.add(zwaveSecureEncap(zwave.notificationV4.notificationGet(notificationType: 7, v1AlarmType: 0, event: 254)))

        cmds.add(zwaveSecureEncap(zwave.sensorBinaryV2.sensorBinaryGet(sensorType: 6)))
		cmds.add(zwaveSecureEncap(zwave.sensorBinaryV2.sensorBinaryGet(sensorType: 8)))
		cmds.add(zwaveSecureEncap(zwave.sensorBinaryV2.sensorBinaryGet(sensorType: 10)))
		cmds.add(zwaveSecureEncap(zwave.sensorBinaryV2.sensorBinaryGet(sensorType: 12)))
    }
		
    cmds.add(zwaveSecureEncap(zwave.wakeUpV2.wakeUpNoMoreInformation()))
    delayBetween(cmds, 250)
}

void logsOff()
{
    device.updateSetting("logEnable", [value:"false", type:"bool"])
    log.warn "debug logging disabled"
}

void installed()
{
    state.pendingResync = true
    state.pendingRefresh = true
    runIn(1, deviceSync)
    runIn(1800, logsOff)
}

void updated()
{
    if (logEnable) log.debug "Updated preferences"

    Integer value

    // Validate numbers in preferences
    if (parameterMode == "raw") {
        if (para5Raw != null)
        {
            value = para5Raw.toBigDecimal()
            if (value != para5Raw)
            {
                log.warn "para5Raw must be an integer: ${para5Raw} changed to ${value}"
                device.updateSetting("para5Raw", value)
            }
        }
        if (para6Raw != null)
        {
            value = para6Raw.toBigDecimal()
            if (value != para6Raw)
            {
                log.warn "para6Raw must be an integer: ${para6Raw} changed to ${value}"
                device.updateSetting("para6Raw", value)
            }
        }
        if (para7Raw != null)
        {
            value = para7Raw.toBigDecimal()
            if (value != para7Raw)
            {
                log.warn "para7Raw must be an integer: ${para7Raw} changed to ${value}"
                device.updateSetting("para7Raw", value)
            }
        }
    }
    if (pirInterval)
    {
        value = pirInterval.toBigDecimal()
        if (value != pirInterval)
        {
            log.warn "Para 8 = pirInterval must be an integer: ${pirInterval} changed to ${value}"
            device.updateSetting("pirInterval", value)
        }
    }	
    if (batteryInterval)
    {
        value = batteryInterval.toBigDecimal()
        if (value != batteryInterval)
        {
            log.warn "Para 10 = batteryInterval must be an integer: ${batteryInterval} changed to ${value}"
            device.updateSetting("batteryInterval", value)
        }
    }
    if (doorInterval)
    {
        value = doorInterval.toBigDecimal()
        if (value != doorInterval)
        {
            log.warn "Para 11 = doorInterval must be an integer: ${doorInterval} changed to ${value}"
            device.updateSetting("doorInterval", value)
        }
    }
    if (luxInterval)
    {
        value = luxInterval.toBigDecimal()
        if (value != luxInterval)
        {
            log.warn "Para 12 = luxInterval must be an integer: ${luxInterval} changed to ${value}"
            device.updateSetting("luxInterval", value)
        }
    }	
    if (temperatureInterval)
    {
        value = temperatureInterval.toBigDecimal()
        if (value != temperatureInterval)
        {
            log.warn "Para 13 = temperatureInterval must be an integer: ${temperatureInterval} changed to ${value}"
            device.updateSetting("temperatureInterval", value)
        }
    }
//    if (humidityInterval)
//    {
//        value = humidityInterval.toBigDecimal()
//        if (value != humidityInterval)
//        {
//            log.warn "Para 14 = humidityInterval must be an integer: ${humidityInterval} changed to ${value}"
//            device.updateSetting("humidityInterval", value)
//        }
//    }
//    if (waterInterval)
//    {
//        value = waterInterval.toBigDecimal()
//        if (value != waterInterval)
//        {
//            log.warn "Para 15 = waterInterval must be an integer: ${waterInterval} changed to ${value}"
//            device.updateSetting("waterInterval", value)
//        }
//    }
	if (tickInterval)
    {
        value = tickInterval.toBigDecimal()
        if (value != tickInterval)
        {
            log.warn "Para 20 = tickInterval must be an integer: ${tickInterval} changed to ${value}"
            device.updateSetting("tickInterval", value)
        }
    }
	if (temperatureDifferential)
    {
	value = temperatureDifferential.toBigDecimal()
        if (value != temperatureDifferential)
        {
            log.warn "Para 21 = temperatureDifferential must be an integer: ${temperatureDifferential} changed to ${value}"
            device.updateSetting("temperatureDifferential", value)
        }
    }
//    if (humidityDifferential)
//    {
//        value = humidityDifferential.toBigDecimal()
//        if (value != humidityDifferential)
//        {
//            log.warn "Para 23 = humidityDifferential must be an integer: ${humidityDifferential} changed to ${value}"
//            device.updateSetting("humidityDifferential", value)
//        }
//    }
    if (wakeUpInterval)
    {
        value = wakeUpInterval.toBigDecimal()
        if (value < 30)
        {
            value = 30
        }
        else if (value > 7200)
        {
            value = 7200
        }
        else
        {
            Integer r = value % 30
            if (r)
            {
                value += 30 - r
            }
        }
        if (value != wakeUpInterval)
        {
            log.warn "wakeUpInterval must be an integer multiple of 30 between 30 and 7200: ${wakeUpInterval} changed to ${value}"
            device.updateSetting("wakeUpInterval", value)
        }
    }

    log.info "debug logging is ${logEnable}"
    log.info "description logging is ${txtEnable}"
}

def configure()
{
    state.pendingResync = true
    log.info "Configuration will resync when device wakes up"
}

def refresh()
{
    state.pendingRefresh = true
    log.info "Data will refresh when device wakes up"
}

def clearTamper()
{
    def map = [:]
    map.name = "tamper"
    map.value = "clear"
    map.descriptionText = "${device.displayName}: tamper cleared"
    sendEvent(map)
    if (txtEnable) log.info "${device.displayName}: ${map.descriptionText}"
}

def parse(String description)
{
    hubitat.zwave.Command cmd = zwave.parse(description, commandClassVersions)
    if (cmd)
    {
        return zwaveEvent(cmd)
    }

    log.warn "Non Z-Wave parse event: ${description}"
    return null
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
    def map = [:]

    if (logEnable) log.trace "SensorMultilevelReport: ${cmd.toString()}"

    switch (cmd.sensorType)
    {
        case 1: // temperature
            def value = cmd.scaledSensorValue
            def precision = cmd.precision
            def unit = cmd.scale == 1 ? "F" : "C"

            map.name = "temperature"
            map.value = convertTemperatureIfNeeded(value, unit, precision)
            map.unit = getTemperatureScale()
            //	if (logEnable) log.info "${device.displayName} temperature sensor value is ${value}°${unit} (${map.value}°${map.unit})"

            if (temperatureOffset)
            {
                map.value = (map.value.toBigDecimal() + temperatureOffset.toBigDecimal()).toString()
                if (logEnable) log.info "${device.displayName} adjusted temperature by ${temperatureOffset} to ${map.value}°${map.unit}"
            }
            map.descriptionText = "temperature is ${map.value}°${map.unit}"
            break

        case 5: // humidity
            value = cmd.scaledSensorValue

            map.name = "humidity"
            map.value = value
            map.unit = "%"
            //	if (logEnable) log.info "${device.displayName} humidity sensor value is ${map.value}${map.unit}"

            if (humidityOffset)
            {
                map.value = (map.value.toBigDecimal() + humidityOffset.toBigDecimal()).toString()
                if (logEnable) log.info "${device.displayName} adjusted humidity by ${humidityOffset} to ${map.value}${map.unit}"
            }
            map.descriptionText = "humidity is ${map.value}${map.unit}"
            break

		case 3:	// luminance
            value = cmd.scaledSensorValue

            map.name = "illuminance"
            map.value = value
            map.unit = "lux"
            //	if (logEnable) log.info "${device.displayName} luminance sensor value is ${map.value}${map.unit}"

            map.descriptionText = "luminance is ${map.value}${map.unit}"
            break			

        default:
            log.warn "Unknown SensorMultilevelReport-Type: ${cmd.toString()}"
            return null
    }

    sendEvent(map)
    if (txtEnable) log.info "${device.displayName}: ${map.descriptionText}"
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd)
{
    def map = [:]

    if (logEnable) log.trace "BatteryReport: ${cmd.toString()}"

    def batteryLevel = cmd.batteryLevel
    if (batteryLevel == 0xFF)
    {
        log.warn "${device.displayName} low battery"
        batteryLevel = 1
    }

    map.name = "battery"
    map.value = batteryLevel
    map.unit = "%"
    map.descriptionText = "battery is ${map.value}${map.unit}"
    sendEvent(map)
    if (txtEnable) log.info "${device.displayName}: ${map.descriptionText}"
}

def zwaveEvent(hubitat.zwave.commands.notificationv4.NotificationReport cmd)
{
    def map = [:]

    if (logEnable) log.trace "NotificationReport: ${cmd.toString()}"

    switch (cmd.notificationType)
    {
        case 5: //water alarm
			map.name = "water"
            map.value = cmd.event ? "wet" : "dry"
            map.descriptionText = "sensor is ${map.value}"
			//	if (logEnable) log.info "${device.displayName} water sensor value ${map.value}"
            break
		case 6: //access control, contact sensor
            map.name = "contact"
			def event = cmd.event.toInteger()
            if (event == 22) map.value = "open"
     		if (event == 23) map.value = "closed"
			map.descriptionText = "contact is ${map.value}"
			//	if (logEnable) log.info "${device.displayName} door is ${map.value}"
            break
        case 7: // security
            def val = cmd.event.toInteger()
			if (val == 3) {
				map.name = "tamper"
				map.value = "detected"
				map.descriptionText = "tamper is ${map.value}"
				//	if (logEnable) log.info "${device.displayName} tamper is ${map.value}"
				break			
			} else if (val == 8) {
				map.name = "motion"
				map.value = "active"
				map.isStateChange = true
				map.descriptionText = "motion is ${map.value}"
				//	if (logEnable) log.info "${device.displayName} motion is ${map.value}"
				break
			} else if (val == 254) {
				map.name = "motion"
				map.value = "inactive"
				map.descriptionText = "motion is ${map.value}"
				//	if (logEnable) log.info "${device.displayName} motion is ${map.value}"
				break
			} else {
				log.warn "Unknown NotificationReport-Event: ${cmd.toString()}"
				return null
			}
        default:
            log.warn "Unknown NotificationReport-Type: ${cmd.toString()}"
            return null
    }

    sendEvent(map)
    if (txtEnable) log.info "${device.displayName}: ${map.descriptionText}"
}

def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd)
{
    // NB: Older firmware versions may send SensorBinaryReport instead of NotificationReport

    def map = [:]

    if (logEnable) log.trace "SensorBinaryReport: ${cmd.toString()}"

    switch (cmd.sensorType)
    {
        case 6: // water
            map.name = "water"
            map.value = cmd.sensorValue ? "wet" : "dry"
            map.descriptionText = "sensor is ${map.value}"
            break
        case 8: // tamper
            map.name = "tamper"			
            if (cmd.sensorValue.toInteger() > 0 ) {
                map.value = "detected"
            } else {
                map.value = "cleared"
            }
            map.descriptionText = "tamper value ${map.value}"
            break
		case 10: // contact sensor
            map.name = "contact"
            if (cmd.sensorValue.toInteger() > 0 ) {
                map.value = "open"
            } else {
                map.value = "closed"
            }
			map.descriptionText = "contact is ${map.value}"
            break
        case 12: // motion sensor
            map.name = "motion"
            if (cmd.sensorValue.toInteger() > 0 ) {
                map.value = "active"
				map.isStateChange = true
            } else {
                map.value = "inactive"
            }
			map.descriptionText = "motion is ${map.value}"
            break
        default:
            log.warn "Unknown SensorBinaryReport: ${cmd.toString()}"
            return null
    }

    sendEvent(map)
    if (txtEnable) log.info "${device.displayName}: ${map.descriptionText}"
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd)
{
    if (logEnable) log.trace "ConfigurationReport: ${cmd.toString()}"

    switch (cmd.parameterNumber)
    {
		case 5:	// Operation Mode
			state.para5 = cmd.configurationValue[0]
			if (state.para5.toInteger() != resolveConfigParam5()) log.warn "values state=${state.para5.toInteger()} neq expected=${resolveConfigParam5()}"
            break
		case 6:	// MultiSensor Fct Swictch
			state.para6 = cmd.configurationValue[0]
			if (state.para6.toInteger() != resolveConfigParam6()) log.warn "values state=${state.para6.toInteger()} neq expected=${resolveConfigParam6()}"
            break
		case 7:	// Customer Fct
            state.para7 = cmd.configurationValue[0]
			if (state.para7.toInteger() != resolveConfigParam7()) log.warn "values state=${state.para7.toInteger()} neq expected=${resolveConfigParam7()}"
            break
		case 8:	// PIR Redetect interval
			state.pirInterval = cmd.configurationValue[0]
			if (state.pirInterval.toInteger() != pirInterval) log.warn "values state=${state.pirInterval.toInteger()} neq preference=${pirInterval}"
			break
		case 10: // Auto Report Battery interval
            state.batteryInterval = cmd.configurationValue[0]
            break
		case 11: // Auto Report Door interval
			state.doorInterval = cmd.configurationValue[0]
			if (state.doorInterval.toInteger() != doorInterval) log.warn "values state=${state.doorInterval.toInteger()} neq preference=${doorInterval}"
			break
        case 12: // Auto Report Lux interval
			state.luxInterval = cmd.configurationValue[0]
			if (state.luxInterval.toInteger() != luxInterval) log.warn "values state=${state.luxInterval.toInteger()} neq preference=${luxInterval}"
			break
		case 13: // Auto Report Temperature interval
            state.temperatureInterval = cmd.configurationValue[0]
            break
//		case 14: // Auto Report Humidity interval
//			state.humidityInterval = cmd.configurationValue[0]
//			break
//		case 15: // Auto Report Water interval
//			state.waterInterval = cmd.configurationValue[0]
//			break
        case 20: // Auto Report tick interval
            state.tickInterval = cmd.configurationValue[0]
            break
        case 21: // Temperature Differential Report
            state.temperatureDifferential = cmd.configurationValue[0]
            break
//		case 23: // Humidity Differential Report
//			state.humidityDifferential = cmd.configurationValue[0]
// 			break
        case 3: case 4: case 9: case 22:
			break
		default:
            log.warn "Configuration Report with unspecified Parameter: ${cmd.toString()}"
    }
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd)
{
    state.wakeUpInterval = cmd.seconds / 60
    if (logEnable) log.trace "wakup interval ${state.wakeUpInterval} minutes"
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd)
{
    if (logEnable) log.debug "${device.displayName}: Received WakeUpNotification"
    runInMillis(200, deviceSync)
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd)
{
    if (logEnable) log.debug "VersionReport: ${cmd}"
    device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion}")
    device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
    device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd)
{
    def encapCmd = cmd.encapsulatedCommand()
    if (encapCmd)
    {
        return zwaveEvent(encapCmd)
    }

    log.warn "Unable to extract encapsulated cmd: ${cmd.toString()}"
    return null
}

def zwaveEvent(hubitat.zwave.Command cmd)
{
    log.warn "Unhandled cmd: ${cmd.toString()}"
    return null
}
