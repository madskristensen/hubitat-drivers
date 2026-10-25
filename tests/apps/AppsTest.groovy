import org.codehaus.groovy.control.CompilerConfiguration

class AppTestDevice {
	String id
	String deviceNetworkId
	String label = 'Test device'
	String displayName = 'Test device'
	Map attributes = [:]
	List calls = []
	def currentValue(String name) { attributes[name] }
	boolean hasAttribute(String name) { attributes.containsKey(name) }
	boolean hasCommand(String name) { true }
	void setLabel(String value) { label = value }
	void sendEvent(Map event) { attributes[event.name] = event.value }
	def methodMissing(String name, args) {
		calls << [name: name, args: args as List]
		if (name in ['on', 'off']) { attributes.switch = name }
		if (name == 'applyStatus') { attributes.applianceStatus = args[0] }
	}
}

abstract class AppTestScript extends Script {
	Map state = [:]
	Map settings = [:]
	Map location = [mode: 'Home', temperatureScale: 'F', timeZone: TimeZone.getTimeZone('UTC'), modes: []]
	Map app = [id: 'test', label: 'Test app']
	Map children = [:]
	List jobs = []
	List subscriptions = []
	List errors = []
	long clock = Date.parse('yyyy-MM-dd HH:mm', '2026-10-25 18:00', TimeZone.getTimeZone('UTC')).time
	def log = new Expando(debug: { Object ignored -> }, info: { Object ignored -> },
			warn: { Object ignored -> }, error: { Object ignored -> })

	Object getProperty(String name) {
		try { return super.getProperty(name) }
		catch (MissingPropertyException ignored) {
			if (getMetaClass().respondsTo(this, name)) { return name }
			return this.@settings[name]
		}
	}

	long now() { binding.hasVariable('clock') ? binding.getVariable('clock') as long : this.@clock }
	void metadata(Closure ignored) { }
	void preferences(Closure ignored) { }
	void definition(Map ignored) { }
	void subscribe(Object device, String attribute, Object handler) {
		subscriptions << [device: device, attribute: attribute, handler: handler.toString()]
	}
	void unsubscribe() { subscriptions.clear() }
	void unschedule(String handler = null) {
		if (handler == null) { jobs.clear() }
		else { jobs.removeAll { it.handler == handler } }
	}
	void runIn(Number seconds, Object handler, Map options = [:]) {
		if (options.overwrite != false) { unschedule(handler.toString()) }
		jobs << [handler: handler.toString(), seconds: seconds, options: options]
	}
	void schedule(Object time, Object handler) { jobs << [handler: handler.toString(), time: time, daily: true] }
	void setLocationMode(String mode) { location.mode = mode }
	Date timeToday(Object value, TimeZone zone) {
		Calendar day = Calendar.getInstance(zone)
		day.timeInMillis = now()
		List parts = value.toString().split(':').collect { it as int }
		day.set(Calendar.HOUR_OF_DAY, parts[0])
		day.set(Calendar.MINUTE, parts[1])
		day.set(Calendar.SECOND, 0)
		day.set(Calendar.MILLISECOND, 0)
		day.time
	}
	def getChildDevice(String dni) { children[dni] }
	List getChildDevices() { children.values() as List }
	def addChildDevice(String ns, String driver, String dni, Map options) {
		children[dni] = new AppTestDevice(id: dni, deviceNetworkId: dni, label: options.label)
	}
	void deleteChildDevice(String dni) { children.remove(dni) }
}

def config = new CompilerConfiguration(scriptBaseClass: AppTestScript.name)
def loader = new GroovyClassLoader(getClass().classLoader, config)
['drivers/climate-advisor/climate-advisor-device.groovy',
 'drivers/generic-appliance/generic-appliance-device.groovy'].each { String source ->
	loader.parseClass(new File(source)).newInstance().run()
}
def loadApp = { String name, Map preferences = [:] ->
	File source = new File(System.getProperty('hubitat.sourceRoot', '.'), "apps/${name}/${name == 'appliance-cycle-monitor' || name == 'climate-advisor' ? name + '-app' : name}.groovy")
	AppTestScript script = loader.parseClass(source).newInstance() as AppTestScript
	script.settings.putAll(preferences)
	script.run()
	script
}

def presence = new AppTestDevice(id: 'presence', attributes: [presence: 'not present'])
def away = loadApp('auto-away-mode', [presenceSensors: [presence], homeMode: 'Home', awayMode: 'Away',
		pausedModes: ['Night'], awayInactiveMinutes: 1, modeChangeCooldownMinutes: 0])
away.installed()
assert away.subscriptions.any { it.device == away.location && it.attribute == 'mode' }
away.location.mode = 'Night'
away.locationModeHandler([value: 'Night'])
assert !away.jobs.any { it.handler == 'evaluateOccupancy' }
away.location.mode = 'Home'
away.locationModeHandler([value: 'Home'])
assert away.jobs.any { it.handler == 'evaluateOccupancy' }
away.state.lastActivityTs = away.clock - 120000L
away.state.presenceInactiveSinceTs = away.clock - 120000L
away.state.lastModeChangeTs = away.clock - 1000L
away.evaluateOccupancy()
assert away.location.mode == 'Away': 'Zero cooldown must not become twenty minutes'
away.settings.modeChangeCooldownMinutes = 20
away.location.mode = 'Home'
away.evaluateOccupancy()
assert away.location.mode == 'Home': 'A positive cooldown must still hold Away transitions'

def meter = new AppTestDevice(id: 'meter', attributes: [power: 0])
def appliance = loadApp('appliance-cycle-monitor', [applianceCount: 1, applianceName_1: 'Washer',
		runSourceType_1: 'power', powerDevice_1: meter, finishDelayMin_1: 0])
appliance.installed()
String dni = 'appliance-cycle-monitor-test-1'
appliance.state.appliances[dni].status = 'Running'
appliance.state.appliances[dni].runningSince = appliance.clock - 60000L
appliance.state.appliances[dni].pendingFinishAt = appliance.clock + 30000L
appliance.updated()
appliance.seedAll()
assert appliance.jobs.any { it.handler == 'checkTimers' && it.seconds == 30 }
appliance.clock += 31000L
appliance.checkTimers()
assert appliance.children[dni].attributes.applianceStatus == 'Finished'
appliance.applyManualStatus(dni, 'Running')
appliance.seedAll()
assert appliance.state.appliances[dni].pendingFinishAt == appliance.clock
assert appliance.jobs.any { it.handler == 'checkTimers' && it.seconds == 1 }
appliance.clock += 1000L
appliance.checkTimers()
assert appliance.children[dni].attributes.applianceStatus == 'Finished'

def vibration = new AppTestDevice(id: 'vibration', attributes: [acceleration: 'active'])
def vibrating = loadApp('appliance-cycle-monitor', [applianceCount: 1, applianceName_1: 'Dryer',
		runSourceType_1: 'accel', accelDevice_1: vibration, accelSustainMin_1: 2])
vibrating.installed()
vibrating.seedAll()
Long pending = vibrating.state.appliances[dni].pendingRunAt
vibrating.updated()
vibrating.seedAll()
assert vibrating.state.appliances[dni].pendingRunAt == pending
assert vibrating.jobs.any { it.handler == 'checkTimers' }
vibrating.clock = pending
vibrating.checkTimers()
assert vibrating.children[dni].attributes.applianceStatus == 'Running'

def door = new AppTestDevice(id: 'door', attributes: [contact: 'open'])
appliance.settings.contactDevice_1 = door
appliance.state.appliances[dni].pendingReadyAt = appliance.now() + 15000L
appliance.updated()
appliance.seedAll()
assert appliance.jobs.any { it.handler == 'checkTimers' && it.seconds == 15 }
door.attributes.contact = 'closed'
appliance.clock += 16000L
appliance.checkTimers()
assert appliance.children[dni].attributes.applianceStatus == 'Finished': 'Recovered unload timers must recheck the door'
appliance.settings.powerThreshold_1 = 0
appliance.seedAll()
assert appliance.children[dni].attributes.applianceStatus == 'Running': 'Zero power thresholds are not missing settings'

def mainLight = new AppTestDevice(id: 'main')
def alwaysLight = new AppTestDevice(id: 'always')
def lights = loadApp('away-lights', [awayLights: [mainLight], alwaysOnLights: [alwaysLight],
		awayMode: 'Away', awayDebounceMinutes: 0, onTime: '16:00', offTime: '22:00', randomizeMinutes: 0])
lights.location.mode = 'Away'
lights.initialize()
assert mainLight.attributes.switch == 'on'
assert alwaysLight.attributes.switch == 'on'
lights.location.mode = 'Home'
lights.modeHandler([value: 'Home'])
assert lights.jobs.any { it.handler == 'offTimeHandler' && it.daily }
lights.offTimeHandler()
assert mainLight.attributes.switch == 'off'
assert alwaysLight.attributes.switch == 'off': 'Always-on-only selections must shut down too'
int commandsAfterOff = mainLight.calls.size()
lights.location.mode = 'Away'
lights.doLightsOn()
assert mainLight.calls.size() == commandsAfterOff: 'Stale delayed starts must not relight'
lights.settings.maxStartTime = '17:00'
lights.onTimeHandler()
assert mainLight.calls.size() == commandsAfterOff: 'Daily starts must honor maximum start time'
lights.settings.maxStartTime = null
lights.modeHandler([value: 'Away'])
assert mainLight.attributes.switch == 'on'
lights.offTimeHandler()
assert mainLight.attributes.switch == 'off'

lights.state.away_lights_cleanup_pending = true
lights.jobs << [handler: 'doLightsOff', seconds: 60]
lights.location.mode = 'Home'
lights.modeHandler([value: 'Home'])
assert lights.jobs.any { it.handler == 'doLightsOff' }: 'Returning home must retain a delayed shutdown when immediate off is disabled'
lights.clock = Date.parse('yyyy-MM-dd HH:mm', '2026-10-25 23:00', TimeZone.getTimeZone('UTC')).time
lights.location.mode = 'Away'
lights.modeHandler([value: 'Away'])
assert lights.jobs.any { it.handler == 'doLightsOff' }: 'An out-of-window arrival must not cancel pending shutdown'
mainLight.on()
lights.initialize()
assert mainLight.attributes.switch == 'off': 'Preference saves must complete pending cleanup outside the window'

def overnight = loadApp('away-lights', [awayLights: [mainLight], awayMode: 'Away', awayDebounceMinutes: 0,
		onTime: '23:00', offTime: '06:00', maxStartTime: '01:00', randomizeMinutes: 0])
overnight.clock = Date.parse('yyyy-MM-dd HH:mm', '2026-10-25 23:30', TimeZone.getTimeZone('UTC')).time
overnight.location.mode = 'Away'
overnight.onTimeHandler()
assert overnight.state.away_lights_active: 'A next-day max start must allow late-evening starts'
overnight.doLightsOff()
overnight.clock = Date.parse('yyyy-MM-dd HH:mm', '2026-10-26 02:00', TimeZone.getTimeZone('UTC')).time
overnight.onTimeHandler()
assert !overnight.state.away_lights_active

def weather = new AppTestDevice(id: 'weather', attributes: [weather: 'Clear', temperatureMax: 80,
		precipitationProbabilityNextHour: 10, humidity: 40, ultravioletIndex: 2])
def outdoor = new AppTestDevice(id: 'outdoor', attributes: [temperature: 70, humidity: 50])
def climate = loadApp('climate-advisor', [weatherDevice: weather, outdoorTempDevice: outdoor])
climate.installed()
['weather', 'temperatureMax', 'precipitationProbabilityNextHour', 'humidity', 'ultravioletIndex'].each { attribute ->
	assert climate.subscriptions.any { it.device == weather && it.attribute == attribute }
}
assert climate.subscriptions.any { it.device == outdoor && it.attribute == 'humidity' }
assert !climate.subscriptions.any { it.device == weather && it.attribute == 'windSpeed' }
climate.debounceHandler([value: '80'])
assert climate.jobs.count { it.handler == 'evaluateAll' } == 1

println 'App regressions passed (mode/cooldown, restored timers, zero delay, light cleanup, overnight windows, weather subscriptions).'