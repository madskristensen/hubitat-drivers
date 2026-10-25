import org.codehaus.groovy.control.CompilerConfiguration
import java.time.ZonedDateTime
import java.time.LocalDateTime

class DataDevice {
    Map values = [:]
    String displayName = 'Test device'
    Object currentValue(String name) { values[name] }
}
abstract class DataDriverStub extends Script {
    Map state = [:]
    Map settings = [:]
    Map location = [timeZone: TimeZone.getTimeZone('UTC'), temperatureScale: 'C']
    DataDevice device = new DataDevice()
    List events = []
    List schedules = []
    long clock = ZonedDateTime.parse('2026-10-25T12:00:00Z').toInstant().toEpochMilli()
    def log = [debug: { x -> }, info: { x -> }, warn: { x -> }, error: { x -> }]
    long now() { clock }
    void sendEvent(Map event) { events << event; device.values[event.name] = event.value }
    Object propertyMissing(String name) { settings[name] }
    Object methodMissing(String name, Object args) {
        if (name == 'metadata') return null
        if (name == 'runIn' || name == 'unschedule') { schedules << [name, args]; return null }
        throw new UnsupportedOperationException("Unexpected stub call ${name}")
    }
}

def config = new CompilerConfiguration(scriptBaseClass: DataDriverStub.name, sourceEncoding: 'UTF-8')
def loader = new GroovyClassLoader(this.class.classLoader, config)
loader.parseClass('''package hubitat.scheduling
class AsyncResponse {
    Map payload
    boolean hasError() { false }
    int getStatus() { 200 }
    String getErrorMessage() { null }
    Map getJson() { payload }
}''')
def load = { String path ->
    def driver = loader.parseClass(new File(path)).newInstance()
    driver.run()
    driver
}
def calendar = load('drivers\\calendar-todo\\calendar-todo.groovy')
def weather = load('drivers\\open-meteo\\open-meteo-weather-enhanced.groovy')
def purple = load('drivers\\purpleair-aqi\\purpleair-aqi.groovy')
int checks = 0
def check = { boolean condition, String message -> assert condition: message; checks++ }
def ms = { String value -> ZonedDateTime.parse(value).toInstant().toEpochMilli() }
def event = { String start, String rule ->
    def zdt = ZonedDateTime.parse(start)
    [uid: 'series', summary: 'TODO task', startZdt: zdt, startMs: zdt.toInstant().toEpochMilli(), rrule: calendar.parseRRule(rule)]
}
def expand = { Map ev, String start, String end -> calendar.expandOccurrences([ev], ms(start), ms(end)) }

def daily = event('2015-01-01T10:00:00Z', 'FREQ=DAILY;COUNT=5000')
check(expand(daily, '2026-10-24T00:00:00Z', '2026-10-27T00:00:00Z').size() == 3, 'old COUNT daily reaches active window')
check(expand(daily, '2030-01-01T00:00:00Z', '2030-02-01T00:00:00Z').isEmpty(), 'COUNT exhaustion is preserved')
def weekly = event('2000-01-05T10:00:00Z', 'FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR;COUNT=2200')
def fullWeekly = expand(weekly, '2000-01-01T00:00:00Z', '2002-01-01T00:00:00Z')
def nearWeekly = expand(weekly, '2001-12-01T00:00:00Z', '2002-01-01T00:00:00Z')
check(nearWeekly*.startMs == fullWeekly.findAll { it.startMs >= ms('2001-12-01T00:00:00Z') }*.startMs, 'weekly BYDAY interval fast-forward preserves occurrences')
def oldWeekly = event('1980-01-02T10:00:00Z', 'FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=8000')
check(expand(oldWeekly, '2026-10-24T00:00:00Z', '2026-10-31T00:00:00Z').size() == 3, 'old weekly COUNT crosses former safety cap')
def limitedWeekly = event('2000-01-05T10:00:00Z', 'FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR;COUNT=155')
def allLimited = expand(limitedWeekly, '2000-01-01T00:00:00Z', '2003-01-01T00:00:00Z')
def tailLimited = expand(limitedWeekly, '2001-11-01T00:00:00Z', '2003-01-01T00:00:00Z')
check(allLimited.size() == 155 && tailLimited*.startMs == allLimited.findAll { it.startMs >= ms('2001-11-01T00:00:00Z') }*.startMs, 'weekly BYDAY preserves COUNT exhaustion after fast-forward')
check(expand(weekly, '2000-01-17T00:00:00Z', '2000-01-18T23:59:00Z')*.startMs == [ms('2000-01-17T10:00:00Z')], 'weekly partial end period includes Monday before Wednesday DTSTART weekday')
def oldMonthly = event('1900-01-31T10:00:00Z', 'FREQ=MONTHLY;COUNT=2000')
check(expand(oldMonthly, '2026-10-01T00:00:00Z', '2026-11-01T00:00:00Z')*.startMs == [ms('2026-10-31T10:00:00Z')], 'historical monthly cycles do not consume active-window safety budget')
def monthly = event('2026-01-31T10:00:00Z', 'FREQ=MONTHLY;COUNT=4')
check(expand(monthly, '2026-01-01T00:00:00Z', '2026-09-01T00:00:00Z')*.startZdt*.monthValue == [1,3,5,7], 'month ends skip invalid dates without consuming COUNT')
def yearly = event('2020-02-29T10:00:00Z', 'FREQ=YEARLY;COUNT=3')
check(expand(yearly, '2020-01-01T00:00:00Z', '2030-01-01T00:00:00Z')*.startZdt*.year == [2020,2024,2028], 'leap days preserve anchor')
def excluded = event('2026-10-24T10:00:00Z', 'FREQ=DAILY;COUNT=3;UNTIL=20261026T100000Z')
excluded.exdates = [ms('2026-10-25T10:00:00Z')]
check(expand(excluded, '2026-10-24T00:00:00Z', '2026-11-01T00:00:00Z')*.startMs == [ms('2026-10-24T10:00:00Z'),ms('2026-10-26T10:00:00Z')], 'EXDATE consumes COUNT and UNTIL remains inclusive')
def feed = { String contents -> calendar.parseICal("BEGIN:VCALENDAR\n${contents}\nEND:VCALENDAR") }
def master = '''BEGIN:VEVENT
UID:chore
DTSTART:20261025T100000Z
SUMMARY:TODO original
RRULE:FREQ=DAILY;COUNT=4
END:VEVENT'''
def moved = '''BEGIN:VEVENT
UID:chore
RECURRENCE-ID:20261025T100000Z
DTSTART:20261025T130000Z
SUMMARY:TODO moved
END:VEVENT'''
calendar.getSettings().putAll([prefix:'TODO', lookbackHours:24, lookaheadDays:5])
calendar.processEvents(feed(master + '\n' + moved))
check(calendar.device.values.todo != 'original' && calendar.device.values.nextTodo == 'moved', 'moved occurrence suppresses due original before expansion')
check(calendar.device.values.matchingEvents == 4, 'moved occurrence replaces instead of duplicating master')
calendar.setClock(ms('2026-10-25T13:30:00Z'))
calendar.processEvents(feed(master + '\n' + moved))
calendar.processEvents(feed(master + '\n' + moved))
check(calendar.events.count { it.name == 'switch' && it.value == 'on' } == 1 && calendar.device.values.todo == 'moved', 'replacement fires once across repeated polls')
calendar.setClock(ms('2026-10-25T12:00:00Z'))
calendar.device.values.clear(); calendar.events.clear(); calendar.state.clear()
calendar.processEvents(feed(master + '\n' + moved.replace('SUMMARY:TODO moved','SUMMARY:not a todo')))
check(!calendar.events.any { it.name == 'switch' && it.value == 'on' }, 'renamed nonmatching override suppresses matching original')
def cancelled = moved.replace('DTSTART:20261025T130000Z\nSUMMARY:TODO moved','STATUS:CANCELLED')
calendar.device.values.clear(); calendar.state.clear()
calendar.processEvents(feed(master + '\n' + cancelled))
check(calendar.device.values.matchingEvents == 3, 'cancellation without DTSTART/summary suppresses original')
calendar.device.values.clear(); calendar.state.clear()
calendar.processEvents(feed(master.replace('SUMMARY:TODO original','SUMMARY:ordinary') + '\n' + moved))
check(calendar.device.values.nextTodo == 'moved' && calendar.device.values.matchingEvents == 1, 'matching override survives nonmatching master')
check(calendar.reconcileOverrides(feed(master.replace('RRULE:', 'STATUS:CANCELLED\nRRULE:') + '\n' + moved)).isEmpty(), 'series cancellation suppresses overrides')
def allDay = feed('''BEGIN:VEVENT
UID:all-day
DTSTART;VALUE=DATE:20261025
SUMMARY:TODO date
RRULE:FREQ=DAILY;COUNT=2
END:VEVENT
BEGIN:VEVENT
UID:all-day
RECURRENCE-ID;VALUE=DATE:20261025
STATUS:CANCELLED
END:VEVENT''')
check(calendar.expandOccurrences(calendar.reconcileOverrides(allDay),ms('2026-10-25T00:00:00Z'),ms('2026-10-27T00:00:00Z')).size() == 1, 'date-valued recurrence IDs reconcile')

def forecast = { String time ->
    Map current = [time:time]
    ['temperature_2m','relative_humidity_2m','apparent_temperature','is_day','precipitation','weather_code','cloud_cover','pressure_msl','wind_speed_10m','wind_direction_10m','wind_gusts_10m'].each { current[it] = 1 }
    Map hourly = [time:(0..47).collect { LocalDateTime.parse('2026-10-25T00:00').plusHours(it).toString() }]
    ['temperature_2m','relative_humidity_2m','precipitation_probability','precipitation','weather_code','wind_speed_10m','uv_index'].each { hourly[it] = (0..47).collect { 1 } }
    Map dailyBlock = [sunrise:['2026-10-25T07:00','2026-10-26T07:00'],sunset:['2026-10-25T17:00','2026-10-26T17:00']]
    ['temperature_2m_max','temperature_2m_min','weather_code','precipitation_probability_max','uv_index_max'].each { dailyBlock[it] = [1,1] }
    [current:current, hourly:hourly, daily:dailyBlock]
}
def respond = { Map json ->
    def response = loader.loadClass('hubitat.scheduling.AsyncResponse').newInstance(payload:json)
    weather.httpResponse(response, null)
}
def json = forecast('2026-10-25T12:30')
json.hourly.precipitation[12] = 100
json.hourly.precipitation[13] = 2
json.hourly.precipitation[14] = 4
json.hourly.precipitation_probability[12] = 100
json.hourly.precipitation_probability[13] = 20
json.hourly.precipitation_probability[14] = 40
json.hourly.precipitation_probability[19] = 90
respond(json)
check(weather.device.values.precipitationNextHour == 3.000g, 'half-hour overlap weights the two future END-labelled buckets')
check(weather.device.values.precipitationProbabilityNextHour == 40, 'next-hour probability excludes expired bucket')
check(weather.device.values.precipitationProbabilityNext6h == 90, 'next-six-hours includes final partial bucket')
check(weather.device.values.status == 'ok' && weather.device.values.lastUpdated != null, 'complete forecast succeeds')
json.current.time = '2026-10-25T12:00'
respond(json)
check(weather.device.values.precipitationNextHour == 2.000g, 'exact-hour window uses only following endpoint bucket')
check(weather.device.values.precipitationProbabilityNext6h == 40, 'six-hour exact boundary excludes nonoverlapping final bucket')
json = forecast('2026-10-25T23:45'); json.hourly.precipitation[24] = 4; json.hourly.precipitation[25] = 8
respond(json)
check(weather.device.values.precipitationNextHour == 7.000g, 'midnight overlap handles next forecast day')
def invalidCases = [
    { it.remove('current') }, { it.remove('hourly') }, { it.remove('daily') },
    { it.current.remove('temperature_2m') }, { it.hourly.precipitation[14] = null },
    { it.hourly.precipitation_probability = [] }, { it.daily.temperature_2m_max = [1] },
    { it.hourly.time = ['bad'] }, { it.current.time = 'bad' },
    { it.hourly.time[14] = '2026-10-25T14:15' }, { it.daily.remove('sunrise') },
    { it.hourly.time = it.hourly.time.take(18); ['temperature_2m','relative_humidity_2m','precipitation_probability','precipitation','weather_code','wind_speed_10m','uv_index'].each { k -> it.hourly[k] = it.hourly[k].take(18) } }
]
invalidCases.eachWithIndex { corrupt, index ->
    def bad = forecast('2026-10-25T12:30'); corrupt(bad)
    weather.device.values.lastUpdated = 'unchanged'
    weather.events.clear(); respond(bad)
    check(weather.device.values.status.startsWith('error:') && weather.device.values.lastUpdated == 'unchanged', "incomplete forecast ${index} cannot report success")
    check(weather.events.every { it.name == 'status' }, "incomplete forecast ${index} cannot emit partial data")
}
[[0,0],[9,50],[9.1,51],[35.4,100],[35.5,101],[55.4,150],[55.5,151],[125.4,200],[125.5,201],[225.4,300],[225.5,301],[325.4,500],[425.3,699],[9.09,50],[-1,-1]].each { pair ->
    check(purple.getPart2_5_AQI(pair[0] as Float) == pair[1], "EPA PM2.5 boundary ${pair[0]}")
}
check(purple.getPart2_5_AQI(Float.NaN) == -1 && purple.getPart2_5_AQI(Float.POSITIVE_INFINITY) == -1, 'invalid PM input rejected')
println "PASS: ${checks} data-driver regression checks; all three production drivers compiled"
