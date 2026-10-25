import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.codehaus.groovy.control.CompilerConfiguration

class TestDevice {
	String displayName = 'Regression device'
	String deviceNetworkId = 'test-child'
	Map attributes = [:]
	Map data = [wattsDeviceId: 'watts-test']
	def currentValue(String name) { attributes[name] }
	String getDataValue(String name) { data[name] }
	void updateSetting(String name, Map value) { }
	void deleteCurrentState(String name) { attributes.remove(name) }
}

class TestResponse {
	int status = 200
	boolean error = false
	Object data = ''
	String errorMessage = 'simulated transport failure'
	int getStatus() { status }
	boolean hasError() { error }
	def getJson() { data instanceof String ? new JsonSlurper().parseText(data) : data }
}

class TestHttpException extends RuntimeException {
	Object response
}

abstract class DriverFixture extends Script {
	Map state = [:]
	Map settings = [txtEnable: false, logEnable: false]
	Map location = [temperatureScale: 'F']
	TestDevice device = new TestDevice()
	Object parent
	Map children = [:]
	Map scheduled = [:]
	List requests = []
	List events = []
	List syncResponses = []
	List messages = []
	long clock = 1800000000000L
	def log = [info: { Object s -> messages << s }, warn: { Object s -> messages << s }, error: { Object s -> messages << s }, debug: { Object s -> messages << s }, trace: { Object s -> messages << s }]
	def interfaces = [rawSocket: [connect: { Object... args -> }, close: { -> }, sendMessage: { String s -> }]]

	long now() { clock }
	def currentValue(String name) { device.currentValue(name) }
	void metadata(Closure definition) { }
	void sendEvent(Map event) { events << event; device.attributes[event.name] = event.value }
	void runIn(Number delay, String method, Map options = [:]) { scheduled[method] = delay }
	void runInMillis(Number delay, String method) { scheduled[method] = delay / 1000 }
	void unschedule(String method = null) { if (method) scheduled.remove(method); else scheduled.clear() }
	void runEvery1Minute(String method) { scheduled[method] = 60 }
	void runEvery5Minutes(String method) { scheduled[method] = 300 }
	void runEvery10Minutes(String method) { scheduled[method] = 600 }
	void schedule(String expression, String method) { scheduled[method] = expression }
	def getChildDevice(String dni) { children[dni] }
	def getChildDevices() { children.values() as List }
	def getProperty(String name) {
		if (name == 'childDevices') return getChildDevices()
		return super.getProperty(name)
	}
	void asynchttpGet(String callback, Map params, Map data) { record('GET', callback, params, data) }
	void asynchttpPut(String callback, Map params, Map data) { record('PUT', callback, params, data) }
	void asynchttpPost(String callback, Map params, Map data) { record('POST', callback, params, data) }
	void asynchttpPatch(String callback, Map params, Map data) { record('PATCH', callback, params, data) }
	void record(String method, String callback, Map params, Map data) {
		if (settings.failAsyncDispatch) throw new IllegalStateException('simulated dispatch failure')
		requests << [method: method, callback: callback, params: params,
			data: new JsonSlurper().parseText(JsonOutput.toJson(data))]
	}
	void httpPatch(Map params, Closure callback) {
		requests << [method: 'PATCH', params: params]
		def response = syncResponses ? syncResponses.remove(0) : new TestResponse(data: [:])
		if (response instanceof Exception) throw response
		callback(response)
	}
	void httpPost(Map params, Closure callback) {
		callback(new TestResponse(data: [access_token: 'new-access', refresh_token: 'new-refresh', expires_on: (clock / 1000L).toLong() + 3600L]))
	}
}

def config = new CompilerConfiguration(scriptBaseClass: DriverFixture.name)
def shell = new GroovyShell(this.class.classLoader, new Binding(), config)
shell.classLoader.parseClass('''
package hubitat.helper
class HexUtils {
	static String byteArrayToHexString(byte[] bytes) { bytes.collect { String.format('%02X', it & 255) }.join('') }
	static byte[] hexStringToByteArray(String hex) { (0..<hex.length()).step(2).collect { Integer.parseInt(hex.substring(it, it+2), 16) as byte } as byte[] }
}
''')
Map sources = [
	gemstone: 'drivers\\gemstone-lights\\gemstone-lights.groovy',
	touchstone: 'drivers\\touchstone-fireplace\\touchstone-fireplace.groovy',
	parent: 'drivers\\sunstat-thermostat\\sunstat-thermostat-parent.groovy',
	child: 'drivers\\sunstat-thermostat\\sunstat-thermostat-child.groovy'
]
Map classes = sources.collectEntries { name, path -> [(name): shell.parse(new File(path)).class] }
println 'PASS: all four production drivers compile with Groovy 2.4.21'
def make = { String name -> def d = classes[name].newInstance(); d.run(); d }
def authenticate = { d ->
	d.state.accessToken = 'test-access'
	d.state.tokenExpiry = (d.clock / 1000L).toLong() + 3600L
	d.state.tokenExpiresAt = d.state.tokenExpiry
	d.state.refreshToken = 'test-refresh'
}
def gemstone = {
	def d = make('gemstone'); authenticate(d)
	d.settings += [accountEmail: 'test@example.invalid', accountPassword: 'fake-test-password']
	d.state.deviceId = 'test-controller'; d.state.lastOnState = true
	d.device.attributes += [switch: 'on', hue: 20, saturation: 100, level: 50, colorMode: 'RGB']
	d
}
def touchstone = {
	def d = make('touchstone')
	d.settings += [deviceIP: '192.0.2.1', deviceId: 'test-tuya', localKey: '0123456789abcdef']
	d.state.socketOpen = true; d.device.attributes.switch = 'off'
	d
}
def thermostat = { String hubUnit = 'F', String apiUnit = 'F' ->
	def p = make('parent'); authenticate(p)
	def c = make('child'); c.parent = p; p.children[c.device.deviceNetworkId] = c
	c.location.temperatureScale = hubUnit
	c.parseDeviceState([isConnected: true, data: [TempUnits: [Val: apiUnit],
		Target: [Min: apiUnit == 'F' ? 40 : 5, Max: apiUnit == 'F' ? 95 : 35, Steps: apiUnit == 'F' ? 1 : 0.5, Heat: apiUnit == 'F' ? 68 : 20],
		SchedEnable: [Val: 'On'], Schedule: [FloorMin: apiUnit == 'F' ? 40 : 5, FloorMax: apiUnit == 'F' ? 85 : 30, Floor: [W: apiUnit == 'F' ? 70 : 21, A: apiUnit == 'F' ? 50 : 10]]]])
	assert !c.messages.any { it.contains('parseDeviceState exception:') } : c.messages
	[p, c]
}
def body = { request -> new JsonSlurper().parseText(request.params.body) }
def finishPatch = { p, request, int status, boolean error = false, String data = '' ->
	p.sendDevicePatchCallback(new TestResponse(status: status, error: error, data: data), request.data)
}
def wireFrame = { d, int cmd, long seq, Object payload, int retcode = 0 ->
	byte[] encrypted = payload == null ? new byte[0] : d.encryptTuyaPayload(cmd, (payload instanceof Map ? JsonOutput.toJson(payload) : payload.toString()).getBytes('UTF-8'))
	byte[] frame = d.concatBytes(d.intToBytes(0x55AAL), d.intToBytes(seq), d.intToBytes(cmd as Long),
		d.intToBytes((encrypted.length + 12) as Long), d.intToBytes(retcode as Long), encrypted)
	byte[] full = d.concatBytes(frame, d.intToBytes(d.crc32(frame)), d.intToBytes(0xAA55L))
	shell.classLoader.loadClass('hubitat.helper.HexUtils').byteArrayToHexString(full)
}

int passed = 0
def test = { String name, Closure assertion ->
	assertion()
	passed++
	println "PASS: ${name}"
}

test('Gemstone numeric zero saturation produces white') {
	def d = gemstone(); d.setSaturation(0)
	assert body(d.requests.last()).pattern.colors == [4294967295L]
	assert d.device.currentValue('saturation') == 0
}
test('Gemstone numeric zero color level sends off') {
	def d = gemstone(); d.setColor([hue: 25, saturation: 0, level: 0])
	assert body(d.requests.last()).onState == false
}
test('Gemstone same Kelvin with different brightness sends a PUT, identical pair skips') {
	def d = gemstone(); d.device.attributes += [colorMode: 'CT', colorTemperature: 3000, level: 20]
	d.setColorTemperature(3000, 80)
	assert body(d.requests.last()).pattern.brightness == 204
	int count = d.requests.size(); d.setColorTemperature(3000, 80)
	assert d.requests.size() == count
}
test('Gemstone failed uncached selection terminates without starting another fetch') {
	def d = gemstone()
	d.state.effectCatalog = [Cached: 'cached-id']; d.state.effectPatterns = ['cached-id': [id: 'cached-id', name: 'Cached']]
	d.state.pendingEffectRequests = [[type: 'name', value: 'Not cached']]
	d.handleEffectCatalogRefreshFailure()
	assert d.requests.empty
	assert !d.state.pendingEffectRequests && !d.state.effectCatalogRefreshInFlight
}
test('Gemstone failure can replay a resolvable cached favorite') {
	def d = gemstone(); d.state.lastOnState = false
	d.state.effectCatalog = [Cached: 'cached-id']; d.state.effectPatterns = ['cached-id': [id: 'cached-id', name: 'Cached', brightness: 255]]
	d.state.pendingEffectRequests = [[type: 'name', value: 'Cached']]
	d.handleEffectCatalogRefreshFailure()
	assert d.requests.size() == 1 && body(d.requests[0]).onState == true
}
test('Touchstone ON then immediate OFF supersedes pending ON') {
	def d = touchstone(); d.on(); long generation = d.state.powerCommandGeneration
	d.off()
	assert d.state.desiredPower == 'off'
	assert d.state.powerCommandGeneration > generation
	assert d.state.pendingRequests.any { it.reason == 'power off' }
	d.responseTimeout()
	assert !d.state.pendingRequests.any { it.reason == 'power on' }
}
test('Touchstone OFF then immediate ON supersedes pending OFF') {
	def d = touchstone(); d.device.attributes.switch = 'on'; d.off(); d.on()
	assert d.state.desiredPower == 'on'
	assert d.state.pendingRequests.any { it.reason == 'power on' }
}
test('Touchstone retry timer preserves unrelated active request') {
	def d = touchstone(); d.refresh(); Map original = d.state.inFlight
	d.state.deferredRetryRequest = [cmd: 7, payloadJson: '{}', reason: 'power on', powerGeneration: 1]
	d.state.powerCommandGeneration = 1
	d.retryPendingRequests()
	assert d.state.inFlight == original && d.state.awaitingResponse
	assert d.state.pendingRequests.size() == 1
}
test('Touchstone heartbeat and unrelated DP push do not acknowledge status query') {
	def d = touchstone(); d.captureBaseline(); long seq = d.state.inFlight.seq
	d.parse(wireFrame(d, 9, seq, null))
	d.parse(wireFrame(d, 8, 0L, [dps: ['101': '2']]))
	assert d.device.currentValue('flameColor') == 'Blue'
	assert d.state.awaitingResponse && d.scheduled.containsKey('responseTimeout')
	assert !d.state.dpBaseline
	d.parse(wireFrame(d, 8, seq, [dps: ['1': false, '101': '2']]))
	assert !d.state.awaitingResponse && d.state.dpBaseline['101'] == '2'
}
test('Touchstone rejects nonzero return codes, corrupt CRCs, and invalid plaintext') {
	def d = touchstone(); d.refresh(); long seq = d.state.inFlight.seq
	d.parse(wireFrame(d, 8, seq, [dps: ['1': false]], 1))
	d.parse(wireFrame(d, 8, seq, 'not-json'))
	String valid = wireFrame(d, 8, seq, [dps: ['1': false]])
	String corrupt = valid.substring(0, valid.length()-16) + '00000000' + valid.substring(valid.length()-8)
	d.parse(corrupt)
	assert d.state.awaitingResponse && d.scheduled.containsKey('responseTimeout')
}
test('Touchstone only matching command ACK completes a write') {
	def d = touchstone(); d.on(); long seq = d.state.inFlight.seq
	d.parse(wireFrame(d, 7, seq + 100, null)); assert d.state.awaitingResponse
	d.parse(wireFrame(d, 7, seq, null)); assert !d.state.awaitingResponse
}
test('Touchstone split frames retain buffering and correlate when complete') {
	def d = touchstone(); d.refresh(); long seq = d.state.inFlight.seq
	String frame = wireFrame(d, 8, seq, [dps: ['1': false]])
	d.parse(frame.substring(0, 40)); assert d.state.awaitingResponse
	d.parse(frame.substring(40)); assert !d.state.awaitingResponse && !d.state.rxBuffer
}
test('SunStat Celsius hub sends Fahrenheit target with API bounds and rounding') {
	def (p, c) = thermostat('C', 'F'); c.setHeatingSetpoint(21)
	assert body(p.requests.last()).Settings.Heat == 70
	assert c.device.currentValue('heatingSetpoint') == 21.1G
	c.setFloorMinTemp(22)
	assert body(p.requests.last()).Settings.Schedule.Floor == [W: 72, A: 50]
}
test('SunStat Fahrenheit hub sends Celsius half-step target') {
	def (p, c) = thermostat('F', 'C'); c.setHeatingSetpoint(72)
	assert body(p.requests.last()).Settings.Heat == 22
	c.setFloorMinTemp(74)
	assert body(p.requests.last()).Settings.Schedule.Floor == [W: 23.5, A: 10]
}
test('SunStat same-unit bounds remain effective and unknown units prevent writes') {
	def (p, c) = thermostat(); c.setHeatingSetpoint(200)
	assert body(p.requests.last()).Settings.Heat == 95
	c.state.remove('apiTemperatureUnit'); int count = p.requests.size(); c.setHeatingSetpoint(72)
	assert p.requests.size() == count
}
test('SunStat boost converts hub delta and restores only after actual success') {
	def (p, c) = thermostat('C', 'F'); c.settings.boostDelta = 3
	c.setBoost(30)
	assert body(p.requests.last()).Settings == [Heat: 73, SchedEnable: 'Off']
	Map activation = p.requests.last(); c.cancelBoost()
	assert p.requests.size() == 1 && c.state.boostRestoring
	finishPatch(p, activation, 200)
	Map restore = p.requests.last()
	assert body(restore).Settings == [Heat: 68, SchedEnable: 'On']
	finishPatch(p, restore, 503, true)
	assert c.state.boostActive && c.state.preBoostApiSetpoint == 68
	assert c.scheduled.retryBoostRestore == 60
	c.retryBoostRestore(); Map retry = p.requests.last()
	assert retry.data.completion.operationId != restore.data.completion.operationId
	finishPatch(p, restore, 200)
	assert c.state.boostActive
	finishPatch(p, retry, 200)
	assert !c.state.boostActive && !c.state.preBoostApiSetpoint
	assert !c.scheduled.containsKey('retryBoostRestore')
}
test('SunStat boost preserves pre-existing manual schedule mode') {
	def (p, c) = thermostat(); c.state.confirmedScheduleEnabled = 'off'
	c.setBoost(10); Map activation = p.requests.last()
	assert !body(activation).Settings.containsKey('SchedEnable')
	finishPatch(p, activation, 200); c.cancelBoost()
	assert !body(p.requests.last()).Settings.containsKey('SchedEnable')
}
test('SunStat missing callback watchdog and reboot retain restoration state') {
	def (p, c) = thermostat(); c.setBoost(1)
	c.boostActivationTimeout()
	assert c.state.boostRestoring && c.scheduled.retryBoostRestore == 240
	String stale = c.state.boostRestoreOperationId
	c.initialize(); assert c.scheduled.retryBoostRestore == 2
	c.retryBoostRestore(); assert c.state.boostRestoreOperationId != stale
	assert c.state.preBoostApiSetpoint == 68
}
test('SunStat application errors and HTTP 429 do not clear boost recovery') {
	def (p, c) = thermostat(); c.setBoost(1); finishPatch(p, p.requests.last(), 200); c.cancelBoost()
	finishPatch(p, p.requests.last(), 200, false, '{"errorNumber":7,"errorMessage":"not applied","body":null}')
	assert c.state.boostActive && c.scheduled.retryBoostRestore == 60
	c.retryBoostRestore(); finishPatch(p, p.requests.last(), 429, true)
	assert c.state.boostActive && c.state.preBoostApiSetpoint == 68
}
test('SunStat failed activation still restores saved state') {
	def (p, c) = thermostat(); c.setBoost(1)
	finishPatch(p, p.requests.last(), 500, true)
	assert c.state.boostActive && c.state.boostRestoring
	assert body(p.requests.last()).Settings.Heat == 68
}
test('SunStat PATCH 401 retries preserve child completion identity') {
	def (p, c) = thermostat(); c.setBoost(1); finishPatch(p, p.requests.last(), 200); c.cancelBoost()
	Map original = p.requests.last(); finishPatch(p, original, 401, true)
	Map retry = p.requests.last()
	assert retry.data.retry401 == false
	assert retry.data.completion == original.data.completion
	finishPatch(p, retry, 200)
	assert !c.state.boostActive
}
test('SunStat token unavailable during restore leaves saved state retryable') {
	def (p, c) = thermostat(); c.setBoost(1); finishPatch(p, p.requests.last(), 200)
	p.state.remove('accessToken'); p.state.remove('refreshToken'); c.cancelBoost()
	assert c.state.boostRestoring && c.state.preBoostApiSetpoint == 68
	assert c.scheduled.retryBoostRestore == 60
}
test('SunStat away 401 retries and failures never poison deduplication') {
	def p = make('parent'); authenticate(p); p.state.locationId = 'test-location'
	p.syncResponses.addAll([new TestResponse(status: 401), new TestResponse(data: [awayState: 1])])
	p.setAway(); assert p.requests.size() == 2 && p.device.currentValue('awayMode') == 'away' : p.messages
	p.setAway(); assert p.requests.size() == 2
	p.syncResponses.addAll([new TestResponse(status: 500), new TestResponse(data: [awayState: 0])])
	p.setHome(); assert p.device.currentValue('awayMode') == 'away'
	p.setHome(); assert p.requests.size() == 4 && p.device.currentValue('awayMode') == 'home'
}

test('SunStat empty HTTP 204 confirms restoration success') {
	def (p, c) = thermostat(); c.setBoost(1); finishPatch(p, p.requests.last(), 204); c.cancelBoost()
	finishPatch(p, p.requests.last(), 204)
	assert !c.state.boostActive && c.state.preBoostApiSetpoint == null
}
test('SunStat dispatch exceptions retain restoration state and schedule retries') {
	def (p, c) = thermostat(); c.setBoost(1); finishPatch(p, p.requests.last(), 200)
	p.settings.failAsyncDispatch = true; c.cancelBoost()
	assert c.state.boostRestoring && c.state.preBoostApiSetpoint == 68
	assert c.scheduled.retryBoostRestore == 60
	p.settings.failAsyncDispatch = false; c.retryBoostRestore(); finishPatch(p, p.requests.last(), 200)
	assert !c.state.boostActive
}
test('SunStat restoration converts saved API units even after hub and API scales change') {
	def (p, c) = thermostat(); c.setBoost(1); finishPatch(p, p.requests.last(), 200)
	c.location.temperatureScale = 'C'
	c.state += [apiTemperatureUnit: 'C', setpointMin: 5, setpointMax: 35, setpointStep: 0.5, confirmedScheduleEnabled: 'off']
	c.cancelBoost()
	assert body(p.requests.last()).Settings == [Heat: 20, SchedEnable: 'On']
}
test('SunStat thrown HTTP 401 retries away without optimistic deduplication') {
	def p = make('parent'); authenticate(p); p.state.locationId = 'test-location'
	p.syncResponses.addAll([new TestHttpException(response: new TestResponse(status: 401)), new TestResponse(data: [awayState: 1])])
	p.setAway()
	assert p.requests.size() == 2 && p.device.currentValue('awayMode') == 'away'
}
test('SunStat away API errors leave previous confirmed display and allow retry') {
	def p = make('parent'); authenticate(p); p.state.locationId = 'test-location'
	p.device.attributes.awayMode = 'home'
	p.syncResponses.addAll([new TestResponse(data: [errorNumber: 8, body: null]), new TestResponse(data: [awayState: 1])])
	p.setAway(); assert p.device.currentValue('awayMode') == 'home'
	p.setAway(); assert p.requests.size() == 2 && p.device.currentValue('awayMode') == 'away'
}

test('SunStat missing temperature units never silently assume Fahrenheit') {
	def p = make('parent'); authenticate(p)
	def c = make('child'); c.parent = p
	c.parseDeviceState([isConnected: true, data: [Target: [Heat: 20]]])
	c.setHeatingSetpoint(22)
	assert p.requests.empty && !c.state.apiTemperatureUnit
	c.state.apiTemperatureUnit = 'C'
	c.parseDeviceState([isConnected: true, data: [Target: [Heat: 20]]])
	assert c.state.apiTemperatureUnit == 'C'
	assert !c.messages.any { it.contains('parseDeviceState exception:') } : c.messages
}

println "${passed} offline behavioral regressions passed; no device/network calls made."
