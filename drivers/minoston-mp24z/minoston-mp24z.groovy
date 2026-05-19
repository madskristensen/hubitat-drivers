/**
 *  Minoston Smart Plug 2-Channel (MP24Z) — Hubitat Fork
 *  Author:  Mads Kristensen
 *  Version: 1.0.5 — 2026-05-19
 *  License: Apache-2.0
 *
 *  Fork of sky-nie/hubitat "Smart Plug 2-Channel" driver.
 *  Source: https://raw.githubusercontent.com/sky-nie/hubitat/main/evalogik/evalogik-outdoor-smart-plug.groovy
 *  Forked because upstream does not accept PRs; this version applies reliability and Hubitat-quality hardening.
 *
 *  Changelog:
 *    1.0.5 — 2026-05-19 — Reduce hub/radio load with light-first child command path, duplicate child command suppression, throttled lastActivity writes, and stale verify cancellation.
 *    1.0.4 — 2026-05-19 — Remove script-scope private/static modifiers for Hubitat parser compatibility.
 *    1.0.3 — 2026-05-19 — Accept legacy child DNI formats when resolving endpoint number (supports prior child formats like -01 and -1 in addition to -ep1).
 *    1.0.2 — 2026-05-19 — Strengthen child endpoint on/off delivery: send set/get sequence twice per child command and force a parent refresh pass to reconcile state quickly.
 *    1.0.1 — 2026-05-19 — Fix child component command dispatch (child on/off/refresh now actively sends Z-Wave). Add parent endpoint verification and targeted retry when one outlet misses on/off.
 *    1.0.0 — 2026-05-19 — Initial Mads-maintained fork. Remove broken supervision references, add explicit logging preferences, remove unsupported PushableButton capability, fix event emission patterns, harden secure command flow, and improve child/aggregate switch synchronization.
 */

metadata {
    definition(
        name: "Minoston Smart Plug 2-Channel (MP24Z)",
        namespace: "mads",
        author: "Mads Kristensen",
        importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/minoston-mp24z/minoston-mp24z.groovy"
    ) {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"
        capability "Polling"
        capability "Refresh"
        capability "Health Check"
        capability "Configuration"
        capability "Initialize"

        attribute "lastActivity", "String"
        attribute "lastEvent", "String"

        command "setAssociationGroup", [[name: "Group Number*", type: "NUMBER", description: "Provide the association group number to edit"],
                                        [name: "Z-Wave Node*", type: "STRING", description: "Enter the node number (in hex) associated with the node"],
                                        [name: "Action*", type: "ENUM", constraints: ["Add", "Remove"]],
                                        [name: "Multi-channel Endpoint", type: "NUMBER", description: "Currently not implemented"]]

        fingerprint mfr: "0312", prod: "C000", deviceId: "C007", deviceJoinName: "Smart Plug 2-Channel", inClusters: "0x5E,0x6C,0x55,0x9F"
        fingerprint mfr: "0312", prod: "C000", deviceId: "C007", deviceJoinName: "Smart Plug 2-Channel", inClusters: "0x86,0x25,0x85,0x8E,0x59,0x60,0x72,0x5A,0x73,0x70,0x7A"
        fingerprint mfr: "0312", prod: "A700", deviceId: "0037", deviceJoinName: "Dual Outlet 2-Channel"
        fingerprint mfr: "0312", prod: "A700", deviceId: "0097", deviceJoinName: "Dual Outlet 2-Channel"
        fingerprint mfr: "0312", prod: "AC01", deviceId: "0097", deviceJoinName: "Dual Outlet 2-Channel"
        fingerprint mfr: "0312", prod: "FF01", deviceId: "FF97", deviceJoinName: "Smart Plug 2-Channel"
    }

    preferences {
        input "autoOff1", "number", title: "Auto Off Channel 1\n\nAutomatically turn switch off after this number of seconds\nRange: 0 to 32767", required: false, range: "0..32767"
        input "autoOff2", "number", title: "Auto Off Channel 2\n\nAutomatically turn switch off after this number of seconds\nRange: 0 to 32767", required: false, range: "0..32767"
        input "ledIndicator", "enum", title: "LED Indicator\n\nTurn LED indicator on when switch is:\n", required: false, options: [["0": "On"], ["1": "Off"], ["2": "Disable"]], defaultValue: "0"
        input "txtEnable", "bool", title: "Enable description text logging", defaultValue: true
        input "logEnable", "bool", title: "Enable debug logging", defaultValue: false
        input description: "Use the \"Z-Wave Association Tool\" SmartApp to set device associations. (Firmware 1.02+)\n\nGroup 2: Sends on/off commands to associated devices when switch is pressed (BASIC_SET).", title: "Associations", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    }
}

Map getCommandClassVersions() {
    [
        0x20: 1, // Basic
        0x25: 1, // Switch Binary
        0x60: 3, // Multi Channel
        0x6C: 1, // Supervision
        0x70: 2, // Configuration
        0x72: 2, // Manufacturer Specific
        0x85: 2, // Association
        0x86: 1, // Version
        0x8E: 2  // Multi Channel Association
    ]
}

def debugLog(String msg) {
    if (logEnable) log.debug msg
}

def parentVerifyMaxRetries() { 2 }
def childVerifyMaxRetries() { 1 }
def lastActivityThrottleMs() { 60000L }

def formatNow() {
    location.timeZone ? new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone) : new Date().format("yyyy MMM dd EEE h:mm:ss a")
}

def recordLastActivity(Boolean force = false) {
    Long nowMs = now()
    Long lastMs = state.lastActivityEpoch as Long
    if (!force && lastMs != null && nowMs - lastMs < lastActivityThrottleMs()) {
        return
    }
    state.lastActivityEpoch = nowMs
    sendEvent(name: "lastActivity", value: formatNow(), displayed: false)
}

def endpointIsOn(int ep) {
    def childDevice = getChildDevice("${device.deviceNetworkId}-ep${ep}")
    return childDevice?.currentValue("switch") == "on"
}

def updateEndpointSwitchEvent(Integer ep, String value) {
    def child = getTargetDeviceByEndPoint(ep)
    if (child && child.currentValue("switch") != value) {
        child.sendEvent(name: "switch", value: value, type: "physical")
    }
}

def getMapState(String key) {
    (state[key] instanceof Map) ? (state[key] as Map) : [:]
}

def putMapState(String key, Map value) {
    state[key] = value
}

def shouldSkipDuplicateChildCommand(Integer endpoint, String target) {
    Map pendingTargets = getMapState("childPendingTargets")
    pendingTargets["${endpoint}"] == target
}

def markChildPending(Integer endpoint, String target) {
    Map pendingTargets = getMapState("childPendingTargets")
    pendingTargets["${endpoint}"] = target
    putMapState("childPendingTargets", pendingTargets)
}

def clearChildPending(Integer endpoint) {
    Map pendingTargets = getMapState("childPendingTargets")
    pendingTargets.remove("${endpoint}")
    putMapState("childPendingTargets", pendingTargets)

    Map retryCounts = getMapState("childRetryCounts")
    retryCounts.remove("${endpoint}")
    putMapState("childRetryCounts", retryCounts)

    Map tokens = getMapState("childVerifyTokens")
    tokens.remove("${endpoint}")
    putMapState("childVerifyTokens", tokens)
}

def nextChildVerifyToken(Integer endpoint) {
    Long token = ((state.childTokenSequence ?: 0L) as Long) + 1L
    state.childTokenSequence = token
    Map tokens = getMapState("childVerifyTokens")
    tokens["${endpoint}"] = token
    putMapState("childVerifyTokens", tokens)
    return token
}

def isCurrentChildVerifyToken(Integer endpoint, Long token) {
    Map tokens = getMapState("childVerifyTokens")
    (tokens["${endpoint}"] as Long) == token
}

def updateAggregateSwitch() {
    boolean anyOn = endpointIsOn(1) || endpointIsOn(2)
    String value = anyOn ? "on" : "off"
    if (device.currentValue("switch") != value) {
        sendEvent(name: "switch", value: value, type: "physical", descriptionText: "${device.displayName} is ${value}")
    }
}

def getTargetDeviceByEndPoint(Integer ep = null) {
    ep ? getChildDevice("${device.deviceNetworkId}-ep${ep}") : device
}

def secure(hubitat.zwave.Command cmd, Integer ep = null) {
    if (ep != null) {
        return zwaveSecureEncap(zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint: 0, bitAddress: 0, res01: 0, destinationEndPoint: ep).encapsulate(cmd))
    }
    return zwaveSecureEncap(cmd)
}

def encap(hubitat.zwave.Command cmd, Integer endpoint = null) {
    endpoint != null ? zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: endpoint).encapsulate(cmd) : cmd
}

def command(hubitat.zwave.Command cmd) {
    zwaveSecureEncap(cmd)
}

def commands(List<hubitat.zwave.Command> commandList, Integer delay = 300) {
    delayBetween(commandList.collect { command(it) }, delay)
}

def sendCommands(List<hubitat.zwave.Command> commandList, Integer delay = 300) {
    List<String> payload = commands(commandList, delay)
    sendHubCommand(new hubitat.device.HubMultiAction(payload, hubitat.device.Protocol.ZWAVE))
}

def channelNumber(String dni) {
    if (!dni) {
        return 1
    }
    def match = (dni =~ /(?:-ep)?(\d+)$/)
    if (match.find()) {
        return (match.group(1) as Integer)
    }
    return 1
}

def parse(String description) {
    hubitat.zwave.Command cmd = zwave.parse(description, commandClassVersions)
    if (cmd) {
        debugLog "Parsed ${cmd}"
        zwaveEvent(cmd)
    } else {
        debugLog "Non-parsed event: ${description}"
    }

    recordLastActivity()
    return []
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, Integer ep = null) {
    debugLog "BasicReport ${cmd} - ep ${ep}"
    String value = cmd.value == 0xFF ? "on" : "off"

    if (ep != null) {
        updateEndpointSwitchEvent(ep, value)
        updateAggregateSwitch()
    } else {
        sendEvent(name: "switch", value: value, type: "physical", descriptionText: "${device.displayName} is ${value}")
    }
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, Integer ep = null) {
    debugLog "SwitchBinaryReport ${cmd} - ep ${ep}"
    String value = cmd.value == 0xFF ? "on" : "off"

    if (ep != null) {
        updateEndpointSwitchEvent(ep, value)
        updateAggregateSwitch()
    } else {
        sendEvent(name: "switch", value: value, type: "physical", descriptionText: "${device.displayName} is ${value}")
    }
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
    } else {
        debugLog "Unable to extract encapsulated command from ${cmd}"
    }
}

def zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, Integer ep = null) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
    if (encapsulatedCommand) {
        ep != null ? zwaveEvent(encapsulatedCommand, ep) : zwaveEvent(encapsulatedCommand)
    }

    hubitat.zwave.Command confirmationReport = new hubitat.zwave.commands.supervisionv1.SupervisionReport(
        sessionID: cmd.sessionID,
        reserved: 0,
        moreStatusUpdates: false,
        status: 0xFF,
        duration: 0
    )
    sendHubCommand(new hubitat.device.HubAction(secure(confirmationReport, ep), hubitat.device.Protocol.ZWAVE))
}

def zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionReport cmd, Integer ep = null) {
    def target = getTargetDeviceByEndPoint(ep)
    switch (cmd.status as Integer) {
        case 0x00:
            log.warn "Device ${target?.displayName ?: device.displayName}: supervision returned No Support (session ${cmd.sessionID})."
            break
        case 0x01:
            debugLog "Device ${target?.displayName ?: device.displayName}: supervision Working (session ${cmd.sessionID})."
            break
        case 0x02:
            log.warn "Device ${target?.displayName ?: device.displayName}: supervision reported failure (session ${cmd.sessionID})."
            break
        case 0xFF:
            debugLog "Device ${target?.displayName ?: device.displayName}: supervision Success (session ${cmd.sessionID})."
            break
        default:
            debugLog "Device ${target?.displayName ?: device.displayName}: supervision status ${cmd.status} (session ${cmd.sessionID})."
            break
    }
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    debugLog "ManufacturerSpecificReport ${cmd}"
    String msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
    updateDataValue("MSR", msr)
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
    debugLog "${device.displayName} parameter '${cmd.parameterNumber}' size '${cmd.size}' value '${cmd.configurationValue}'"
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
    List<String> nodeIds = []
    if (cmd.nodeId) {
        cmd.nodeId.each { nodeId ->
            nodeIds << Integer.toHexString(nodeId as Integer).padLeft(2, "0").toUpperCase()
        }
    }
    state."actualAssociation${cmd.groupingIdentifier}" = nodeIds
    debugLog "Associations for Group ${cmd.groupingIdentifier}: ${nodeIds}"
    updateDataValue("associationGroup${cmd.groupingIdentifier}", "${nodeIds}")
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationGroupingsReport cmd) {
    debugLog "Supported association groups: ${cmd.supportedGroupings}"
    state.associationGroups = cmd.supportedGroupings
    sendEvent(name: "groups", value: cmd.supportedGroupings)
}

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    if (cmd.applicationVersion != null && cmd.applicationSubVersion != null) {
        String firmware = "${cmd.applicationVersion}.${cmd.applicationSubVersion.toString().padLeft(2, "0")}"
        sendEvent(name: "status", value: "fw: ${firmware}")
        updateDataValue("firmware", firmware)
    }
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    debugLog "Unhandled Event: ${cmd}"
}

def on() {
    debugLog "on()"
    unschedule("verifyParentState")
    state.pendingParentTarget = "on"
    state.pendingParentRetries = 0
    state.pendingParentVerifyPass = 0
    sendCommands([
        encap(zwave.basicV1.basicSet(value: 0xFF), 1),
        encap(zwave.basicV1.basicSet(value: 0xFF), 2),
        encap(zwave.switchBinaryV1.switchBinaryGet(), 1),
        encap(zwave.switchBinaryV1.switchBinaryGet(), 2)
    ])
    runIn(1, "verifyParentState")
}

def off() {
    debugLog "off()"
    unschedule("verifyParentState")
    state.pendingParentTarget = "off"
    state.pendingParentRetries = 0
    state.pendingParentVerifyPass = 0
    sendCommands([
        encap(zwave.basicV1.basicSet(value: 0x00), 1),
        encap(zwave.basicV1.basicSet(value: 0x00), 2),
        encap(zwave.switchBinaryV1.switchBinaryGet(), 1),
        encap(zwave.switchBinaryV1.switchBinaryGet(), 2)
    ])
    runIn(1, "verifyParentState")
}

def childOn(String dni) {
    Integer endpoint = channelNumber(dni)
    if (shouldSkipDuplicateChildCommand(endpoint, "on")) {
        debugLog "childOn($dni) skipped duplicate pending target"
        return
    }
    debugLog "childOn($dni)"
    markChildPending(endpoint, "on")
    Long token = nextChildVerifyToken(endpoint)
    sendCommands([
        encap(zwave.basicV1.basicSet(value: 0xFF), endpoint),
        encap(zwave.switchBinaryV1.switchBinaryGet(), endpoint)
    ], 200)
    runIn(1, "verifyChildEndpoint", [data: [ep: endpoint, target: "on", token: token]])
}

def childOff(String dni) {
    Integer endpoint = channelNumber(dni)
    if (shouldSkipDuplicateChildCommand(endpoint, "off")) {
        debugLog "childOff($dni) skipped duplicate pending target"
        return
    }
    debugLog "childOff($dni)"
    markChildPending(endpoint, "off")
    Long token = nextChildVerifyToken(endpoint)
    sendCommands([
        encap(zwave.basicV1.basicSet(value: 0x00), endpoint),
        encap(zwave.switchBinaryV1.switchBinaryGet(), endpoint)
    ], 200)
    runIn(1, "verifyChildEndpoint", [data: [ep: endpoint, target: "off", token: token]])
}

def childRefresh(String dni) {
    Integer endpoint = channelNumber(dni)
    debugLog "childRefresh($dni)"
    sendCommands([
        encap(zwave.switchBinaryV1.switchBinaryGet(), endpoint)
    ], 150)
}

def componentOn(cd) {
    if (txtEnable) log.info "${device.displayName}: componentOn(${cd})"
    childOn(cd.deviceNetworkId)
}

def componentOff(cd) {
    if (txtEnable) log.info "${device.displayName}: componentOff(${cd})"
    childOff(cd.deviceNetworkId)
}

def componentRefresh(cd) {
    if (txtEnable) log.info "${device.displayName}: componentRefresh(${cd})"
    childRefresh(cd.deviceNetworkId)
}

def poll() {
    debugLog "poll()"
    refresh()
}

def refresh() {
    debugLog "refresh()"
    sendCommands([
        encap(zwave.switchBinaryV1.switchBinaryGet(), 1),
        encap(zwave.switchBinaryV1.switchBinaryGet(), 2)
    ])
}

def ping() {
    debugLog "ping()"
    refresh()
}

def createChildDevices() {
    state.oldLabel = device.label
    (1..2).each { Integer i ->
        String childDni = "${device.deviceNetworkId}-ep${i}"
        if (!getChildDevice(childDni)) {
            addChildDevice("hubitat", "Generic Component Switch", childDni, [
                completedSetup: true,
                label: "${device.displayName} (CH${i})",
                isComponent: true,
                componentName: "ep${i}",
                componentLabel: "Channel ${i}"
            ])
        }
    }
}

def syncChildLabels() {
    if (device.label != state.oldLabel) {
        childDevices.each { child ->
            Integer ep = channelNumber(child.deviceNetworkId)
            String expectedOld = "${state.oldLabel} (CH${ep})"
            if (child.label == expectedOld) {
                child.setLabel("${device.displayName} (CH${ep})")
            }
        }
        state.oldLabel = device.label
    }
}

def integer2Cmd(Integer value, Integer size) {
    switch (size) {
        case 1:
            return [value & 0xFF]
        case 2:
            return [((value >> 8) & 0xFF), (value & 0xFF)]
        case 3:
            return [((value >> 16) & 0xFF), ((value >> 8) & 0xFF), (value & 0xFF)]
        case 4:
            return [((value >> 24) & 0xFF), ((value >> 16) & 0xFF), ((value >> 8) & 0xFF), (value & 0xFF)]
        default:
            throw new IllegalArgumentException("Unsupported size '${size}' in integer2Cmd")
    }
}

def installed() {
    initialize()
}

def updated() {
    if (!state.lastRan || now() >= (state.lastRan as Long) + 2000L) {
        debugLog "updated()"
        state.lastRan = now()
        if (logEnable) runIn(1800, "logsOff")
        initialize()
    } else {
        debugLog "updated() ran within the last 2 seconds. Skipping execution."
    }
}

def logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def configure() {
    debugLog "configure()"
    return initialize()
}

def initialize() {
    debugLog "initialize()"
    if (!childDevices) {
        createChildDevices()
    } else {
        syncChildLabels()
    }

    sendEvent(name: "checkInterval", value: 3 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
    recordLastActivity(true)

    List<hubitat.zwave.Command> cmds = []
    cmds << zwave.associationV2.associationGroupingsGet()
    cmds.addAll(processAssociations())
    cmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: ledIndicator != null ? ledIndicator.toInteger() : 0, parameterNumber: 1, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 1)
    cmds << zwave.configurationV1.configurationSet(configurationValue: autoOff1 != null ? integer2Cmd(autoOff1.toInteger(), 2) : integer2Cmd(0, 2), parameterNumber: 2, size: 2)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 2)
    cmds << zwave.configurationV1.configurationSet(configurationValue: autoOff2 != null ? integer2Cmd(autoOff2.toInteger(), 2) : integer2Cmd(0, 2), parameterNumber: 3, size: 2)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 3)
    cmds << encap(zwave.switchBinaryV1.switchBinaryGet(), 1)
    cmds << encap(zwave.switchBinaryV1.switchBinaryGet(), 2)
    sendCommands(cmds)
}

def verifyParentState() {
    String target = state.pendingParentTarget as String
    if (!(target in ["on", "off"])) return

    Integer verifyPass = (state.pendingParentVerifyPass ?: 0) as Integer
    if (verifyPass == 0) {
        state.pendingParentVerifyPass = 1
        sendCommands([
            encap(zwave.switchBinaryV1.switchBinaryGet(), 1),
            encap(zwave.switchBinaryV1.switchBinaryGet(), 2)
        ], 150)
        unschedule("verifyParentState")
        runIn(1, "verifyParentState")
        return
    }

    boolean targetOn = target == "on"
    List<Integer> mismatched = []
    if (endpointIsOn(1) != targetOn) mismatched << 1
    if (endpointIsOn(2) != targetOn) mismatched << 2

    if (!mismatched) {
        debugLog "Parent ${target} verified on both outlets"
        unschedule("verifyParentState")
        state.remove("pendingParentTarget")
        state.remove("pendingParentRetries")
        state.remove("pendingParentVerifyPass")
        return
    }

    Integer retries = (state.pendingParentRetries ?: 0) as Integer
    if (retries >= parentVerifyMaxRetries()) {
        log.warn "Parent ${target} not fully applied after retries. Mismatched endpoints: ${mismatched}"
        unschedule("verifyParentState")
        state.remove("pendingParentTarget")
        state.remove("pendingParentRetries")
        state.remove("pendingParentVerifyPass")
        return
    }

    state.pendingParentRetries = retries + 1
    state.pendingParentVerifyPass = 0
    debugLog "Retrying parent ${target} for endpoints ${mismatched} (attempt ${state.pendingParentRetries}/${parentVerifyMaxRetries()})"

    short setValue = (short) (targetOn ? 0xFF : 0x00)
    List<hubitat.zwave.Command> retryCmds = []
    mismatched.each { Integer ep ->
        retryCmds << encap(zwave.basicV1.basicSet(value: setValue), ep)
        retryCmds << encap(zwave.switchBinaryV1.switchBinaryGet(), ep)
    }
    sendCommands(retryCmds, 200)
    unschedule("verifyParentState")
    runIn(1, "verifyParentState")
}

def verifyChildEndpoint(Map data = [:]) {
    Integer endpoint = (data.ep ?: data.endpoint) as Integer
    String target = data.target as String
    Long token = data.token as Long
    if (!(endpoint in [1, 2]) || !(target in ["on", "off"])) return
    if (!isCurrentChildVerifyToken(endpoint, token)) return

    boolean targetOn = target == "on"
    if (endpointIsOn(endpoint) == targetOn) {
        clearChildPending(endpoint)
        return
    }

    Map retryCounts = getMapState("childRetryCounts")
    Integer retries = (retryCounts["${endpoint}"] ?: 0) as Integer
    if (retries >= childVerifyMaxRetries()) {
        log.warn "Child endpoint ${endpoint} did not reach ${target} after retries."
        clearChildPending(endpoint)
        return
    }

    retryCounts["${endpoint}"] = retries + 1
    putMapState("childRetryCounts", retryCounts)

    short setValue = (short) (targetOn ? 0xFF : 0x00)
    sendCommands([
        encap(zwave.basicV1.basicSet(value: setValue), endpoint),
        encap(zwave.switchBinaryV1.switchBinaryGet(), endpoint)
    ], 200)
    runIn(1, "verifyChildEndpoint", [data: [ep: endpoint, target: target, token: token]])
}

def setDefaultAssociations() {
    String hubId = String.format("%02X", zwaveHubNodeId)
    state.defaultG1 = [hubId]
    state.defaultG2 = []
    state.defaultG3 = []
}

def setAssociationGroup(group, nodes, action, endpoint = null) {
    action = "${action}" == "1" ? "Add" : "${action}" == "0" ? "Remove" : "${action}"
    group = "${group}" =~ /\d+/ ? (group as Integer) : group
    List<String> requestedNodes = (nodes instanceof Collection ? nodes : [nodes]).collect { "${it}".trim().toUpperCase() }

    if (!requestedNodes.every { it ==~ /(?i)^[0-9A-F]{1,2}$/ }) {
        log.error "Invalid nodes: ${requestedNodes}. Use 1-2 digit hex node IDs."
        return
    }

    Integer maxGroups = maxAssociationGroup()
    if ((group as Integer) < 1 || (group as Integer) > maxGroups) {
        log.error "Association group is invalid. Expected 1 <= group <= ${maxGroups}"
        return
    }

    List<String> associations = state."desiredAssociation${group}" ?: []
    requestedNodes.each { String node ->
        if (action == "Remove") {
            debugLog "Removing node ${node} from association group ${group}"
            associations = associations - node
        } else if (action == "Add") {
            debugLog "Adding node ${node} to association group ${group}"
            associations << node
        }
    }
    state."desiredAssociation${group}" = associations.unique()
}

def maxAssociationGroup() {
    (state.associationGroups ?: 5) as Integer
}

def processAssociations() {
    List<hubitat.zwave.Command> cmds = []
    setDefaultAssociations()
    Integer associationGroups = maxAssociationGroup()

    for (Integer i = 1; i <= associationGroups; i++) {
        if (state."actualAssociation${i}" != null) {
            if (state."desiredAssociation${i}" != null || state."defaultG${i}") {
                boolean refreshGroup = false
                ((state."desiredAssociation${i}" ? state."desiredAssociation${i}" : [] + state."defaultG${i}") - state."actualAssociation${i}").each { String nodeHex ->
                    debugLog "Adding node ${nodeHex} to group ${i}"
                    cmds << zwave.associationV2.associationSet(groupingIdentifier: i, nodeId: hubitat.helper.HexUtils.hexStringToInt(nodeHex))
                    refreshGroup = true
                }
                ((state."actualAssociation${i}" - state."defaultG${i}") - state."desiredAssociation${i}").each { String nodeHex ->
                    debugLog "Removing node ${nodeHex} from group ${i}"
                    cmds << zwave.associationV2.associationRemove(groupingIdentifier: i, nodeId: hubitat.helper.HexUtils.hexStringToInt(nodeHex))
                    refreshGroup = true
                }
                if (refreshGroup) {
                    cmds << zwave.associationV2.associationGet(groupingIdentifier: i)
                } else {
                    debugLog "No association actions required for group ${i}"
                }
            }
        } else {
            debugLog "Association info unknown for group ${i}; requesting from device."
            cmds << zwave.associationV2.associationGet(groupingIdentifier: i)
        }
    }
    return cmds
}
