import groovy.json.JsonSlurper
import org.codehaus.groovy.control.CompilerConfiguration

// Self-contained Hubitat doubles. No sockets, device traffic, or downloaded dependencies.
def loader = new GroovyClassLoader(this.class.classLoader)
loader.parseClass('''
package hubitat.zwave
class Command {
	static Map registry = [:]
	static int sequence = 0
	Map values = [:]
	String kind
	Command(Map properties = [:]) { values.putAll(properties) }
	def propertyMissing(String name) { values[name] }
	void propertyMissing(String name, value) { values[name] = value }
	String format() {
		String token = "command:${++sequence}"
		registry[token] = this
		token
	}
	def encapsulate(Command inner) { values.inner = inner; this }
	def encapsulatedCommand(Map versions = [:]) { values.versions = versions; values.inner }
	List encapsulatedCommands(Map versions = [:]) { values.versions = versions; values.commands ?: [] }
}
''')
loader.parseClass('''
package hubitat.device
class Protocol { static final String ZWAVE = "zwave" }
class HubAction {
	Object command
	Object protocol
	HubAction(Object command, Object protocol) { this.command = command; this.protocol = protocol }
}
class HubMultiAction extends HubAction {
	HubMultiAction(Object command, Object protocol) { super(command, protocol) }
}
''')
loader.parseClass('''
package hubitat.helper
class HexUtils {
	static int hexStringToInt(String value) { Integer.parseInt(value, 16) }
	static String integerToHexString(int value, int size) { String.format("%0${size * 2}X", value) }
}
''')
loader.parseClass('''
package hubitat.scheduling
class AsyncResponse {
	Map values
	AsyncResponse(Map values = [:]) { this.values = values }
	boolean hasError() { values.error == true }
	int getStatus() { values.status == null ? 200 : values.status }
	String getData() { values.data ?: "" }
	String getErrorMessage() { values.message ?: "stubbed transport failure" }
	def getJson() { values.json ?: new groovy.json.JsonSlurper().parseText(getData()) }
}
''')
loader.parseClass('''
class ReliabilityLog {
	List messages = []
	def methodMissing(String name, args) { messages << [name, args.toList()] }
}
class ReliabilityDevice {
	String deviceNetworkId = "12"
	String displayName = "Test device"
	String label = "Test device"
	String name = "Test device"
	Map hub = [hardwareID: "stub-hub"]
	Map values = [:]
	Map dataValues = [:]
	Map settings = [:]
	List events = []
	def currentValue(String name) { values[name] }
	void updateSetting(String name, value) {
		settings[name] = value instanceof Map ? value.value : value
	}
	void updateDataValue(String name, value) { dataValues[name] = value }
	void setLabel(String label) { this.label = label; this.displayName = label }
	void parse(List events) { events.each { values[it.name] = it.value; this.events << it } }
}
class ReliabilityCommandFactory {
	String family
	def methodMissing(String name, args) {
		def command = new hubitat.zwave.Command(args && args[0] instanceof Map ? args[0] : [:])
		command.kind = name
		command.values.family = family
		command
	}
}
class ReliabilityZwave {
	def propertyMissing(String family) { new ReliabilityCommandFactory(family: family) }
}
abstract class ReliabilityHub extends Script {
	Map state = [:]
	Map settings = [:]
	ReliabilityDevice device = new ReliabilityDevice(settings: settings)
	Map location = [temperatureScale: "C", timeZone: TimeZone.getTimeZone("UTC"), hub: [localIP: "192.0.2.1"]]
	def log = new ReliabilityLog()
	def zwave = new ReliabilityZwave()
	Integer zwaveHubNodeId = 1
	List childDevices = []
	List actions = []
	List requests = []
	Map jobs = [:]
	boolean failSend = false
	boolean failHttp = false
	long clock = 1000000L
	def propertyMissing(String name) {
		if (settings.containsKey(name)) return settings[name]
		if (metaClass.respondsTo(this, name)) return name
		null
	}
	void metadata(Closure ignored) {}
	void preferences(Closure ignored) {}
	void sendEvent(Map event) { device.values[event.name] = event.value; device.events << new LinkedHashMap(event) }
	void sendHubCommand(action) {
		if (failSend) throw new IllegalStateException("stubbed send failure")
		actions << action
	}
	List delayBetween(List commands, Number delay = 200) { commands }
	String zwaveSecureEncap(command) { command instanceof String ? command : command.format() }
	long now() { clock }
	String getTemperatureScale() { location.temperatureScale }
	def convertTemperatureIfNeeded(value, String unit, int precision) {
		BigDecimal result = value as BigDecimal
		if (unit != location.temperatureScale) {
			result = unit == "F" ? (result - 32G) * 5G / 9G : result * 9G / 5G + 32G
		}
		result.setScale(precision, BigDecimal.ROUND_HALF_UP)
	}
	void runIn(Number delay, method, Map options = [:]) { jobs[method.toString()] = [delay: delay, options: options] }
	void runInMillis(Number delay, method, Map options = [:]) { runIn(delay, method, options) }
	void schedule(String expression, method) { runIn(0, method) }
	void unschedule(String method = null) { if (method == null) jobs.clear(); else jobs.remove(method) }
	def methodMissing(String name, args) {
		if (name.startsWith("runEvery")) { runIn(0, args[0]); return }
		throw new MissingMethodException(name, this.class, args)
	}
	void fire(String method) {
		Map job = jobs.remove(method)
		assert job != null : "No scheduled job: ${method}"
		if (job.options.containsKey("data")) invokeMethod(method, job.options.data)
		else invokeMethod(method, null)
	}
	void asynchttpGet(String callback, Map params, Map data = null) { captureHttp(callback, params, data) }
	void asynchttpPost(String callback, Map params, Map data = null) { captureHttp(callback, params, data) }
	void captureHttp(String callback, Map params, Map data) {
		if (failHttp) throw new IllegalStateException("stubbed HTTP submission failure")
		requests << [callback: callback, params: params, data: data]
	}
	def getChildDevice(String dni) { childDevices.find { it.deviceNetworkId == dni } }
	def addChildDevice(String namespace, String type, String dni, Map options) {
		def child = new ReliabilityDevice(deviceNetworkId: dni, label: options.label, displayName: options.label)
		childDevices << child
		child
	}
	def getDataValue(String name) { device.dataValues[name] }
	void updateDataValue(String name, value) { device.dataValues[name] = value }
}
''')

def names = ['honeywell-t6-pro', 'minoston-mp24z', 'philio-pst02', 'daikin-wifi', 'fully-kiosk']
def sources = names.collectEntries { name ->
	File file = new File(new File('drivers', name), name + '.groovy')
	assert file.isFile(): "Run this test from the repository root: ${file}"
	[(name): file.getText('UTF-8').replaceFirst('^\uFEFF', '')]
}
def commandTypes = sources.values().collectMany { source ->
	(source =~ /hubitat\.zwave\.commands\.[A-Za-z0-9_]+\.[A-Za-z0-9_]+/).collect { it }
}.unique()
commandTypes.each { String type ->
	int split = type.lastIndexOf('.')
	String packageName = type.substring(0, split)
	String className = type.substring(split + 1)
	loader.parseClass("package ${packageName}; class ${className} extends hubitat.zwave.Command { ${className}(Map properties = [:]) { super(properties) } }")
}
def config = new CompilerConfiguration(scriptBaseClass: 'ReliabilityHub')
def drivers = new GroovyClassLoader(loader, config)
def classes = sources.collectEntries { name, source ->
	[(name): drivers.parseClass(source, 'Driver_' + name.replace('-', '_') + '.groovy')]
}
println "Compiled all ${classes.size()} actual production drivers with Groovy ${GroovySystem.version}."

def fresh = { String name, Map settings = [:] ->
	def driver = classes[name].newInstance()
	driver.settings.putAll(settings)
	driver.run()
	driver
}
def report = { String type, Map values -> loader.loadClass('hubitat.zwave.commands.' + type).newInstance(values) }
def response = { Map values -> loader.loadClass('hubitat.scheduling.AsyncResponse').newInstance(values) }
def sent = { driver ->
	driver.actions.collectMany { action -> action.command instanceof List ? action.command : [action.command] }.collect {
		loader.loadClass('hubitat.zwave.Command').registry[it]
	}
}
def controlRequests = { driver -> driver.requests.findAll { it.params.uri.contains('/set_control_info?') } }
def query = { request ->
	new URI(request.params.uri.toString()).rawQuery.split('&').collectEntries { pair ->
		String[] parts = pair.split('=', 2)
		[(parts[0]): parts.length > 1 ? URLDecoder.decode(parts[1], 'UTF-8') : '']
	}
}
def controlBody = { Map overrides = [:] ->
	([ret: 'OK', pow: '1', mode: '4', stemp: '21.5', f_rate: '3', f_dir: '2', shum: '0', dt1: '22.0', dt3: '25.0', dt4: '21.5'] + overrides).collect { key, value -> "${key}=${value}" }.join(',')
}
def seedDaikin = { Map overrides = [:] ->
	def driver = fresh('daikin-wifi', [ip: '192.0.2.2'])
	driver.handleControlInfo(response([data: controlBody(overrides)]), [:])
	driver
}
int count = 0
List failures = []
def test = { String name, Closure body ->
	try { body(); count++; println "PASS ${name}" }
	catch (Throwable error) { failures << name; println "FAIL ${name}: ${error}"; error.printStackTrace(System.out) }
}

test('Philio scheduled wakeup explicitly dispatches all zero settings and sleep command') {
	def driver = fresh('philio-pst02', [pirInterval: 0, batteryInterval: 0, doorInterval: 0, luxInterval: 0, temperatureInterval: 0, tickInterval: 0, temperatureDifferential: 0])
	driver.state.pendingResync = true
	driver.state.pendingRefresh = true
	driver.zwaveEvent(report('wakeupv2.WakeUpNotification', [:]))
	driver.fire('deviceSync')
	def commands = sent(driver)
	assert commands.last().kind == 'wakeUpNoMoreInformation'
	[8, 10, 11, 12, 13, 20, 21].each { parameter ->
		assert commands.find { it.kind == 'configurationSet' && it.parameterNumber == parameter }.scaledConfigurationValue == 0
	}
	assert commands.any { it.kind == 'batteryGet' }
	assert driver.state.pendingResync == false && driver.state.pendingRefresh == false
}
test('Philio retains pending flags if explicit dispatch fails') {
	def driver = fresh('philio-pst02')
	driver.state.pendingResync = true
	driver.state.pendingRefresh = true
	driver.setFailSend(true)
	try { driver.deviceSync(); assert false } catch (IllegalStateException expected) {}
	assert driver.state.pendingResync && driver.state.pendingRefresh
}
test('Philio bundled and security-wrapped reports dispatch with explicit versions') {
	def driver = fresh('philio-pst02')
	def tamper = report('sensorbinaryv2.SensorBinaryReport', [sensorType: 8, sensorValue: 0])
	def motion = report('sensorbinaryv2.SensorBinaryReport', [sensorType: 12, sensorValue: 255])
	def bundle = report('multicmdv1.MultiCmdEncap', [commands: [tamper, motion]])
	def secure = report('securityv1.SecurityMessageEncapsulation', [inner: bundle])
	driver.zwaveEvent(secure)
	assert driver.device.currentValue('tamper') == 'clear'
	assert driver.device.currentValue('motion') == 'active'
	assert secure.versions[0x8F] == 1 && bundle.versions[0x30] == 2
}

test('Fully mute/unmute restores nonzero and zero volumes without losing saved state') {
	[40, 0].each { original ->
		def driver = fresh('fully-kiosk', [volumeStream: '1'])
		driver.device.values.volume = original
		driver.mute()
		assert driver.state.mute == original
		assert driver.device.currentValue('mute') == 'muted'
		driver.mute()
		assert driver.state.mute == original
		driver.unmute()
		assert driver.device.currentValue('volume') == original
		assert driver.state.mute == null
		assert driver.device.currentValue('mute') == 'unmuted'
	}
}
test('Fully volumeUp works from zero and explicit volume cancels mute') {
	def driver = fresh('fully-kiosk', [volumeStream: '1'])
	driver.device.values.volume = 0
	driver.volumeUp()
	assert driver.device.currentValue('volume') == 10
	driver.mute()
	driver.setVolume(25)
	assert driver.state.mute == null && driver.device.currentValue('mute') == 'unmuted'
}
test('Fully brightness callback matches normalized transmitted percentage') {
	[-20, 0, 1, 2, 50.5, 100, 200].each { level ->
		def driver = fresh('fully-kiosk', [serverIP: '192.0.2.3', serverPort: '2323', serverPassword: 'test-only'])
		driver.setLevel(level)
		def request = driver.requests.last()
		int brightness = query(request).value.toInteger()
		assert brightness >= 0 && brightness <= 255
		driver.setLevelCallback(response([status: 200]), request.data)
		assert driver.device.currentValue('level') == Math.round(brightness / 2.55).toInteger()
		assert driver.device.currentValue('level') in 0..100
		if (level == 1) assert brightness == 3
		if (level == 2) assert brightness == 5
	}
}
test('Fully logging timeout survives initialize and runs after preferences save') {
	def driver = fresh('fully-kiosk', [serverIP: '192.0.2.3', logEnable: true])
	driver.updated()
	assert driver.jobs.logsOff.delay == 1800
	driver.fire('logsOff')
	assert driver.settings.logEnable.toString() == 'false'
}

test('Honeywell reports normalize both scales and retain fractional setpoints') {
	def driver = fresh('honeywell-t6-pro')
	driver.device.values.thermostatMode = 'heat'
	driver.zwaveEvent(report('sensormultilevelv5.SensorMultilevelReport', [sensorType: 1, scale: 1, precision: 1, scaledSensorValue: 68.9G]))
	assert driver.device.currentValue('temperature') == 20.5G
	driver.zwaveEvent(report('thermostatsetpointv2.ThermostatSetpointReport', [setpointType: 1, scale: 1, precision: 1, scaledValue: 68.9G]))
	assert driver.device.currentValue('heatingSetpoint') == 20.5G
	assert driver.device.currentValue('thermostatSetpoint') == 20.5G
	assert driver.device.events.findAll { it.name in ['temperature', 'heatingSetpoint'] }.every { it.unit == 'C' }
	driver.location.temperatureScale = 'F'
	driver.zwaveEvent(report('sensormultilevelv5.SensorMultilevelReport', [sensorType: 1, scale: 0, precision: 1, scaledSensorValue: 20.5G]))
	assert driver.device.currentValue('temperature') == 68.9G
	driver.setHeatingSetpoint(68.9G)
	def setpoint = sent(driver).find { it.kind == 'thermostatSetpointSet' }
	assert setpoint.precision == 1 && setpoint.scale == 1 && setpoint.scaledValue == 68.9G
}
test('Honeywell clock maps every Java weekday to Z-Wave Monday=1 through Sunday=7') {
	def driver = fresh('honeywell-t6-pro')
	try {
		(0..6).each { offset ->
			Calendar fixed = new GregorianCalendar(TimeZone.getTimeZone('UTC'))
			fixed.clear()
			fixed.set(2026, Calendar.OCTOBER, 19 + offset, 12, 34)
			Calendar.metaClass.'static'.getInstance = { TimeZone zone -> fixed }
			driver.syncClock()
			def clock = sent(driver).last()
			assert clock.weekday == offset + 1
			assert clock.hour == 12 && clock.minute == 34
		}
	} finally { GroovySystem.metaClassRegistry.removeMetaClass(Calendar) }
}

test('Minoston legacy children receive reports and preserve aggregate state without duplicates') {
	['-1', '-01', '-ep1'].each { suffix ->
		def driver = fresh('minoston-mp24z')
		def first = loader.loadClass('ReliabilityDevice').newInstance(deviceNetworkId: '12' + suffix)
		def second = loader.loadClass('ReliabilityDevice').newInstance(deviceNetworkId: '12-02')
		second.values.switch = 'on'
		driver.childDevices.addAll([first, second])
		driver.initialize()
		assert driver.childDevices.size() == 2
		driver.zwaveEvent(report('switchbinaryv1.SwitchBinaryReport', [value: 0]), 1)
		assert first.currentValue('switch') == 'off'
		assert driver.device.currentValue('switch') == 'on'
		driver.childOn(first.deviceNetworkId)
		assert first.currentValue('switch') == 'on'
	}
}
test('Minoston creates only the missing child when upgrading partial legacy devices') {
	def driver = fresh('minoston-mp24z')
	driver.childDevices << loader.loadClass('ReliabilityDevice').newInstance(deviceNetworkId: '12-01')
	driver.initialize()
	assert driver.childDevices*.deviceNetworkId.sort() == ['12-01', '12-ep2']
}
test('Minoston association Add reconciles immediately and preserves lifeline') {
	def driver = fresh('minoston-mp24z')
	driver.state.actualAssociation1 = ['01']
	driver.setAssociationGroup(1, 'a', 'Add')
	assert sent(driver) : "${driver.state}; ${driver.log.messages}"
	assert sent(driver).find { it.kind == 'associationSet' }.nodeId == 10
	assert !sent(driver).any { it.kind == 'associationRemove' && it.nodeId == 1 }
}
test('Minoston defers unknown association edits until complete multipart discovery') {
	def driver = fresh('minoston-mp24z')
	driver.setAssociationGroup(2, 'a', 'Add')
	assert sent(driver) : "${driver.state}; ${driver.log.messages}"
	assert sent(driver)*.kind == ['associationGet']
	driver.zwaveEvent(report('associationv2.AssociationReport', [groupingIdentifier: 2, nodeId: [11], reportsToFollow: 1]))
	assert sent(driver)*.kind == ['associationGet']
	driver.zwaveEvent(report('associationv2.AssociationReport', [groupingIdentifier: 2, nodeId: [12], reportsToFollow: 0]))
	assert sent(driver).find { it.kind == 'associationSet' }.nodeId == 10
	assert !sent(driver).any { it.kind == 'associationRemove' }
	int sends = driver.actions.size()
	driver.zwaveEvent(report('associationv2.AssociationReport', [groupingIdentifier: 2, nodeId: [11, 12], reportsToFollow: 0]))
	assert driver.actions.size() == sends : 'Refused changes must not create an unbounded report/write loop'
}
test('Minoston Remove preserves unrelated nodes') {
	def driver = fresh('minoston-mp24z')
	driver.state.actualAssociation2 = ['0A', '0B']
	driver.setAssociationGroup(2, 'a', 'Remove')
	assert sent(driver).findAll { it.kind == 'associationRemove' }*.nodeId == [10]
}

test('Daikin power writes preserve confirmed mode and setpoint even when displayed off') {
	def driver = seedDaikin([pow: '0'])
	assert driver.device.currentValue('thermostatMode') == 'off'
	driver.on()
	def request = controlRequests(driver).last()
	assert query(request).subMap(['pow', 'mode', 'stemp', 'f_rate', 'f_dir']) == [pow: '1', mode: '4', stemp: '21.5', f_rate: '3', f_dir: '2']
	assert driver.device.currentValue('switch') == 'off' : 'Power attributes remain confirmed until readback'
	driver.handleSetControlInfo(response([data: 'ret=OK']), request.data)
	driver.handleControlInfo(response([data: controlBody()]), [:])
	driver.off()
	assert query(controlRequests(driver).last()).mode == '4'
	assert query(controlRequests(driver).last()).pow == '0'
	assert !driver.jobs.containsKey('applyPowerOnDefaults')
}
test('Daikin first command waits for confirmed control information') {
	def driver = fresh('daikin-wifi', [ip: '192.0.2.2'])
	driver.on()
	assert !controlRequests(driver)
	assert driver.requests.last().params.uri.endsWith('/get_control_info')
	driver.handleControlInfo(response([data: controlBody([pow: '0', mode: '3'])]), [:])
	assert query(controlRequests(driver).last()).mode == '3'
}
test('Daikin mode changes use confirmed per-mode memory rather than optimistic attributes') {
	def driver = seedDaikin([mode: '3', stemp: '25.0', dt4: '19.5'])
	driver.device.values.heatingSetpoint = 30G
	driver.heat()
	def control = query(controlRequests(driver).last())
	assert control.mode == '4' && control.stemp == '19.5'
	driver.handleControlInfo(response([data: controlBody([mode: '3'])]), [:])
	assert driver.state.controlInfo.mode == '3' : 'Pre-write reads must not replace the cache while a write is in flight'
}
test('Daikin failed transport and rejected writes remain retryable') {
	def driver = seedDaikin([pow: '0'])
	driver.device.values.switch = 'on'
	driver.on()
	driver.handleSetControlInfo(response([error: true]), controlRequests(driver).last().data)
	assert driver.state.controlWriteInFlight == false
	assert driver.jobs.containsKey('getControlInfo')
	driver.on()
	assert controlRequests(driver).size() == 2
	driver.handleSetControlInfo(response([data: 'ret=PARAM NG']), controlRequests(driver).last().data)
	driver.on()
	assert controlRequests(driver).size() == 3
}
test('Daikin synchronous HTTP submission failures do not wedge the write queue') {
	def driver = seedDaikin([pow: '0'])
	driver.setFailHttp(true)
	driver.on()
	assert driver.state.controlWriteInFlight == false
	assert driver.state.pendingControlWrites.pow == '1'
	driver.setFailHttp(false)
	driver.on()
	assert controlRequests(driver).size() == 1
}
test('Daikin opposite power writes serialize and apply after confirmed readback') {
	def driver = seedDaikin()
	driver.off()
	driver.on()
	assert controlRequests(driver).size() == 1
	assert driver.state.pendingControlWrites.pow == '1'
	driver.handleSetControlInfo(response([data: 'ret=OK']), controlRequests(driver).last().data)
	driver.handleControlInfo(response([data: controlBody([pow: '0'])]), [:])
	assert controlRequests(driver).size() == 2
	assert query(controlRequests(driver).last()).pow == '1'
	assert query(controlRequests(driver).last()).mode == '4'
}
test('Daikin auto targets survive rejected writes and clear only after acceptance') {
	def driver = seedDaikin([mode: '3'])
	driver.setAutoSetpoint(23.5G)
	driver.auto()
	assert driver.device.currentValue('thermostatOperatingState') == 'idle'
	assert driver.state.pendingAutoSetpointC == '23.5'
	driver.handleSetControlInfo(response([data: 'ret=PARAM NG']), controlRequests(driver).last().data)
	assert driver.state.pendingAutoSetpointC == '23.5'
	driver.auto()
	def request = controlRequests(driver).last()
	assert query(request).stemp == '23.5'
	driver.handleSetControlInfo(response([data: 'ret=OK']), request.data)
	assert driver.state.pendingAutoSetpointC == null
}
test('Daikin logging timers survive initialization and cache is invalidated on preferences save') {
	def driver = seedDaikin()
	driver.settings.putAll([logEnable: true, traceEnable: true])
	driver.updated()
	assert driver.jobs.logsOff.delay == 1800 && driver.jobs.traceOff.delay == 1800
	assert driver.state.controlInfo == null
	driver.fire('logsOff')
	driver.fire('traceOff')
	assert driver.settings.logEnable.toString() == 'false' && driver.settings.traceEnable.toString() == 'false'
}

test('Fully siren shutdown restores an original zero volume') {
	def driver = fresh('fully-kiosk', [volumeStream: '1', sirenVolume: 100, sirenFile: 'http://192.0.2.4/siren.wav'])
	driver.device.values.volume = 0
	driver.siren()
	assert driver.state.siren == 0
	driver.off()
	assert driver.device.currentValue('volume') == 0
	assert driver.state.siren == null
}

test('Minoston association numeric action aliases and short node IDs normalize consistently') {
	def driver = fresh('minoston-mp24z')
	driver.state.actualAssociation2 = ['0A', '0B']
	driver.setAssociationGroup(2, 'a', 0)
	assert sent(driver).findAll { it.kind == 'associationRemove' }*.nodeId == [10]
	driver.state.actualAssociation2 = ['0B']
	driver.actions.clear()
	driver.setAssociationGroup(2, 'a', 1)
	assert sent(driver).findAll { it.kind == 'associationSet' }*.nodeId == [10]
}

test('All component manifests match source versions and release date') {
	def versions = ['honeywell-t6-pro': '0.5.1', 'minoston-mp24z': '1.0.8', 'philio-pst02': '1.4.2', 'daikin-wifi': '0.1.10', 'fully-kiosk': '0.6.3']
	versions.each { name, version ->
		def manifest = new JsonSlurper().parse(new File(new File('drivers', name), 'packageManifest.json'))
		assert manifest.version == version && manifest.drivers[0].version == version
		assert manifest.dateReleased == '2026-10-25'
		assert sources[name].contains('"' + version + '"') || sources[name].contains('Version: ' + version)
		UUID.fromString(manifest.drivers[0].id)
	}
}
assert failures.empty : "${failures.size()} regression failures: ${failures}"
println "Passed ${count} behavior/manifest regression tests; no network or hardware was used."
