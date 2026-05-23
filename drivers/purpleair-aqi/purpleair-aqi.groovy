/**
 *  PurpleAir AQI Virtual Sensor
 *  Author:  Mads Kristensen
 *  Version: 0.4.1 — 2026-05-22 — BUG FIXES: weighted-avg silently dropped sensors with missing position_rating; humidity history bucketed by 12-hour clock (AM/PM collisions); stray double-plus typo in geo-search bbox; one-time HUMIDITY_HISTORY migration to clear stale 12-hour buckets
 *  License: MIT
 *
 *  Reads AQI data from the PurpleAir cloud API (api.purpleair.com/v1/sensors).
 *  Supports geolocation-based multi-sensor averaging or a specific sensor index.
 *  Implements US EPA Barkjohn 2021 AQI correction for wildfire smoke, plus
 *  Woodsmoke, AQ&U, LRAPA, and CF=1 correction algorithms.
 *
 *  Source: github.com/pfmiller0/Hubitat — forked from v1.3.2 by Peter Miller
 *  (pfmiller0). Original upstream code is preserved below with its copyright.
 *  This fork applies bug fixes and Hubitat best-practice improvements; it is a
 *  permanent driver in this repo, not a staging area.
 *
 *  Changelog:
 *    0.4.1 — 2026-05-22 — BUG FIXES: weighted-avg silently dropped sensors with missing position_rating; humidity history bucketed by 12-hour clock (AM/PM collisions); stray double-plus typo in geo-search bbox; one-time HUMIDITY_HISTORY migration to clear stale 12-hour buckets
 *    0.4.0 — 2026-05-18 — BUG FIXES: failCount string-multiplication, disabled-poll retry storm, lat/lng degree math, weighted-avg NaN at distance=0; POLISH: refresh-on-save, canonical async error handling, AirQuality capability, runEvery schedules, hub temp scale, cleaner sites/AQI units
 *    0.3.0 — 2026-05-18 — Added pm2_5, temperature, humidity, and confidence attributes; parseJson guard for blank search_coords + empty API bodies; API key help now points to develop.purpleair.com
 *    0.2.0 — 2026-05-18 — Namespace → mads; emitIfChanged on all poll events (eliminates ~35,040 duplicate events/year at 1-hr default cadence); descriptionText on sites event uses device.displayName; 1-min interval quota warning added; UUID in packageManifest; fix IQAir→PurpleAir log prefix; logsOff auto-disable after 30 min; lastActivity (Pattern B); sentinel .isNumber() guards on pm2.5 field before .toFloat() parse
 *    0.1.0 — 2026-05-18 — Initial fork; Trinity audit fixes: AQ&U string mismatch, LRAPA/Woodsmoke case + wrong PM2.5 field, failCount precedence
 *
 *  [original pfmiller0 copyright block preserved verbatim below]
 */

/**
 *  PurpleAir AQI Virtual Sensor
 *
 *  PurpleAir sensor map: https://map.purpleair.com/
 *  API documentation: https://api.purpleair.com/
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "0.4.1"

metadata {
	definition (
		name: "PurpleAir AQI Virtual Sensor",
		namespace: "mads",
		author: "Mads Kristensen",
		importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/purpleair-aqi/purpleair-aqi.groovy"
	)
	{
		capability "Sensor"
		capability "Polling"
		capability "Initialize"
		capability "AirQuality"
		capability "TemperatureMeasurement"
		capability "RelativeHumidityMeasurement"

		attribute "aqi", "number"
		attribute "conversion", "string" // Conversion algorithm
		attribute "category", "string" // Description of current air quality
		attribute "sites", "string" // List of sensor sites used
		attribute "pm2_5", "number" // Raw PM2.5 mass concentration
		attribute "confidence", "number" // PurpleAir sensor confidence score
		attribute "lastActivity", "string"

		command "refresh"
	}

	preferences {
		input "X_API_Key", "text", title: "PurpleAir API key", required: true, description: "Get a free API key at https://develop.purpleair.com/"
		input "update_interval", "enum", title: "Update interval", required: true, options: [["1": "1 min"], ["5": "5 min"], ["10": "10 min"], ["15": "15 min"], ["30": "30 min"], ["60": "1 hr"], ["180": "3 hr"], ["0": "disabled"]], defaultValue: "60", description: "⚠️ The '1 min' option generates ~43,800 API requests/month per sensor — close to the free tier's 1M-points/month limit if multiple sensors are queried. Use 60 min (default) for normal operation."
		input "conversion", "enum", title: "Apply conversion", required: false, description: "See map.purpleair.com for details", options: [["US EPA": "US EPA"], ["Woodsmoke": "Woodsmoke"], ["AQ&U": "AQ&U"], ["CF=1": "CF=1"], ["LRAPA": "LRAPA"]]
		if (! conversion) {
			input "avg_period", "enum", title: "Averaging period", required: true, description: "Readings averaged over what time", options: [["pm2.5": "1 min"], ["pm2.5_10minute": "10 mins"], ["pm2.5_30minute": "30 mins"], ["pm2.5_60minute": "1 hour"], ["pm2.5_6hour": "6 hours"], ["pm2.5_24hour": "1 day"], ["pm2.5_1week": "1 week"]], defaultValue: "pm2.5_60minute"
		} else if ( conversion == "US EPA" ) {
			input "hum_history", "bool", title: "Humidity history", required: false, description: "Keep recent history of humidity values to detect bad sensors", defaultValue: false
		}
		input "device_search", "bool", title: "Search for devices", required: true, description: "If false specify device index to use", defaultValue: true

		if ( device_search ) {
			input "search_coords", "text", title: "Search coordinates [lat, long]", required: true, description: "Coordinates at center of sensor search box", defaultValue: "[" + location.latitude + "," + location.longitude + "]"
			input "search_range", "decimal", title: "Search range", required: true, description: "Size of sensor search box (+/- center of search box coordinates)", defaultValue: 1.5
			input "unit", "enum", title: "Unit", required: true, options: ["miles", "kilometers"], defaultValue: "miles"
			input "weighted_avg", "bool", title: "Weighted average", required: true, description: "Calculate device average weighted by distance", defaultValue: true
			//input "confidenceThreshold", "number", title: "Confidence threshold", required: true, description: "Filter out measurments below this confidence", range: "0..100", defaultValue: 90
		} else {
			input "Read_Key", "text", title: "Private key", required: false, description: "Required to access private devices"
			input "sensor_index", "number", title: "Sensor index", required: true, description: "Select=INDEX in URL when viewing a sensor on map.purpleair.com", defaultValue: 82101
		}
		input "logEnable", "bool", title: "Enable debug logging (auto-off after 30 minutes)", defaultValue: false
	}
}

// Parse events into attributes. Required for device drivers but not used
def parse(String description) {
	if (logEnable) log.debug("PurpleAir: Parsing '${description}'")
}

def installed() {
	// Do nothing on install because an API key is required
}

def refresh() {
	sensorCheck()
}

def poll() {
	sensorCheck()
}

def configure() {
	unschedule()

	if (! conversion) {
		device.deleteCurrentState('conversion')
	}
	if (conversion != "US EPA" || ! hum_history || hum_history == "0") {
		state.remove('HUMIDITY_HISTORY')
	}

	Integer updateIntervalMinutes = normalizedUpdateIntervalMinutes(null)
	if (updateIntervalMinutes == null) {
		log.error "Invalid update_interval '${update_interval}' — defaulting to 60 minutes"
		updateIntervalMinutes = 60
	}
	applyRefreshSchedule(updateIntervalMinutes)
}

def initialize() {
	configure()
}

def updated() {
	unschedule()
	migrateHumidityHistoryBuckets()
	if (logEnable) {
		runIn(1800, "logsOff")
	}
	initialize()
	refresh()
}

def uninstalled() {
	unschedule()
}

void sensorCheck() {
	final String URL="https://api.purpleair.com/v1/sensors"
	String pm25_count = avg_period
	if (conversion) {
		// FIX #2 (Trinity audit): case was "lrapa"/"woodsmoke" — never matched preference values "LRAPA"/"Woodsmoke",
		// causing both conversions to silently request pm2.5 (atmospheric) instead of pm2.5_cf_1 (required by both formulas).
		if (conversion == "LRAPA" || conversion == "Woodsmoke" || conversion == "CF=1") {
			pm25_count="pm2.5_cf_1"
		} else {
			pm25_count = "pm2.5"
		}
	}

	String query_fields = "name,confidence,temperature,humidity,${pm25_count}"
	if (device_search && weighted_avg) {
		query_fields += ",latitude,longitude,position_rating"
	}

	Map httpQuery
	Float[] coords

	if (device_search) {
		coords = parseSearchCoords()
		if (!coords) {
			return
		}
		Float[] dist2deg = distance2degrees(coords[0])
		Float[] range = []

		if ( unit == "miles" ) {
			range = [search_range/dist2deg[0], search_range/dist2deg[1]]
		} else { // Convert to km
			range = [(search_range/1.609)/dist2deg[0], (search_range/1.609)/dist2deg[1]]
		}
		httpQuery = [fields: query_fields, location_type: "0", max_age: 3600, nwlat: coords[0] + range[0], nwlng: coords[1] - range[1], selat: coords[0] - range[0], selng: coords[1] + range[1]]
	} else {
		if ( Read_Key ) {
			httpQuery = [fields: query_fields, read_key: Read_Key, show_only: "$sensor_index"]
		} else {
			httpQuery = [fields: query_fields, show_only: "$sensor_index"]
		}
	}

	Map params = [
		uri: URL,
		headers: ['X-API-Key': X_API_Key],
		query: httpQuery,
		timeout: 30
	]

	try {
		asynchttpGet('httpResponse', params, [coords: coords, pm25_count: pm25_count])
	} catch (SocketTimeoutException e) {
		log.error("PurpleAir request timed out (${requestContext(coords)}; status=n/a): ${e.message}")
	} catch (Exception e) {
		log.error("PurpleAir request failed (${requestContext(coords)}; status=n/a): ${e.message}")
	}
}

void httpResponse(hubitat.scheduling.AsyncResponse response, Map data) {
	Integer updateIntervalMinutes = normalizedUpdateIntervalMinutes(60)
	Map RESPONSE_FIELDS = [:]
	Integer aqi2_5Value = -1
	List sensorData = []
	String sites
	List<Map> sensors = []

	if (response == null) {
		log.warn "[httpResponse] null response (${requestContext(data?.coords as Float[])})"
		return
	}

	if (response.hasError() || response.getStatus() != 200) {
		// Groovy/Hubitat preference enum values are Strings; coerce once before retry math so "60" * 5 does not become "6060606060".
		state.failCount = (state.failCount ?: 0) + 1
		unschedule('refresh')
		String errorMessage = response.getErrorMessage() ?: "Unexpected status"
		String context = requestContext(data?.coords as Float[])
		Integer retryDelaySeconds = calculateRetryDelaySeconds(updateIntervalMinutes, state.failCount as Integer)
		if (state.failCount <= 4) {
			log.warn "[httpResponse] HTTP error (${context}): ${response.getStatus()} ${errorMessage}"
		} else if (state.failCount == 5) {
			log.warn "[httpResponse] HTTP error (${context}): ${response.getStatus()} ${errorMessage} (muting errors)"
		}
		scheduleRetryGuarded(updateIntervalMinutes, retryDelaySeconds, context)
		return
	}

	if (state.failCount > 0) {
		if (state.failCount >= 5) {
			log.info "HTTP error from PurpleAir resolved (${state.failCount})"
		}
		state.failCount = 0
		configure()
	}

	Map respJson = safeResponseJson(response)
	if (!respJson) {
		return
	}
	if (!(respJson.fields instanceof List) || !(respJson.data instanceof List)) {
		log.warn "[httpResponse] PurpleAir response is missing fields or data"
		return
	}

	// Set field lookup map
	respJson.fields.eachWithIndex { it, index -> RESPONSE_FIELDS[(it)] = index }

	List rawSensorData = respJson.data as List

	if (device_search) {
		Integer confidenceThreshold = resolveConfidenceThreshold()
		// Filter out lower quality devices
		//sensorData = rawSensorData.findAll {it[RESPONSE_FIELDS["confidence"]] >= (confidenceThreshold as Integer) }
		sensorData = rawSensorData.findAll {
			String rawConfidence = it[RESPONSE_FIELDS["confidence"]]?.toString()
			rawConfidence?.isNumber() && rawConfidence.toInteger() >= confidenceThreshold
		}
		if (logEnable) {
			List confidence = rawSensorData.collect { row ->
				String rawConfidence = row[RESPONSE_FIELDS['confidence']]?.toString()
				if (!rawConfidence?.isNumber()) {
					return ['name': row[RESPONSE_FIELDS['name']], 'confidence': null]
				}
				['name': row[RESPONSE_FIELDS['name']], 'confidence': rawConfidence.toInteger()]
			}
			List dropped = confidence.findAll { it['confidence'] == null || it['confidence'] < confidenceThreshold }
			if (dropped) {
				logDebug "Sensor confidence: ${confidence}"
				logDebug "Low confidence sensors dropped: ${dropped}"
			}
		}
	} else {
		sensorData = rawSensorData
	}

	// Some sensors don't return humidity, fill in missing data with avg from other devices
	// Also detect broken humidity sensors by looking for ones that never change
	// TODO: make function for this?
	Map humidity_history = [:]
	Integer avg_humidity = 50
	if (conversion == "US EPA" && hum_history) {
		humidity_history = state.HUMIDITY_HISTORY ?: [:]

		sensorData.each { row ->
			String rawHumidity = row[RESPONSE_FIELDS['humidity']]?.toString()
			if (rawHumidity?.isNumber()) {
				humidityHistoryUpdate(humidity_history, row[RESPONSE_FIELDS['name']], rawHumidity.toInteger())
			}
		}

		List<Map> humiditySamples = sensorData.collect { row ->
			String rawHumidity = row[RESPONSE_FIELDS['humidity']]?.toString()
			if (!rawHumidity?.isNumber()) {
				return null
			}
			[humidity: humidityDeviceUpdating(humidity_history, row[RESPONSE_FIELDS['name']]) ? rawHumidity.toInteger() : null]
		}.findAll { it?.humidity instanceof Number }
		Float avgHumidityValue = sensorAverage(humiditySamples, 'humidity')
		if (avgHumidityValue != null) {
			avg_humidity = Math.round(avgHumidityValue)
		} else {
			log.error 'No valid humidity data returned from sites and "US EPA" conversion selected. US EPA requires humidity data, please choose another option!'
			return
		}

		state.HUMIDITY_HISTORY = humidity_history
	}

	//logDebug "RESPONSE_FIELDS: ${RESPONSE_FIELDS}"

	// initialize sensor maps
	// TODO: make function for this?
    final int HUMIDITY_FUDGE = 4 // PurpleAir states humidity sensors are ~4% below ambiant humidity
	Float[] sensor_coords = data.coords
	Float pm25_conv

	//log.debug(sensorData)
	sensorData.each { row ->
		String rawHumidity = row[RESPONSE_FIELDS['humidity']]?.toString()
		Integer reportedHumidity = rawHumidity?.isNumber() ? rawHumidity.toInteger() : null
		Integer humidityForConversion = (reportedHumidity != null && (!hum_history || humidityDeviceUpdating(humidity_history, row[RESPONSE_FIELDS['name']]))) ? reportedHumidity : avg_humidity
		Integer this_humidity = humidityForConversion + HUMIDITY_FUDGE
		if (device_search && weighted_avg) {
			String rawLatitude = row[RESPONSE_FIELDS['latitude']]?.toString()
			String rawLongitude = row[RESPONSE_FIELDS['longitude']]?.toString()
			sensor_coords = (rawLatitude?.isNumber() && rawLongitude?.isNumber()) ? [rawLatitude.toFloat(), rawLongitude.toFloat()] as Float[] : data.coords
		}
		// Sentinel guard: PurpleAir may return null/non-numeric pm2.5 fields for faulted sensors
		String rawPm25 = row[RESPONSE_FIELDS[data.pm25_count]]?.toString()
		//logDebug "${row[RESPONSE_FIELDS['name']]} raw pm25: ${rawPm25}"
		if (!rawPm25?.isNumber()) {
			log.error("${row[RESPONSE_FIELDS['name']]} has no valid pm25 data (value: ${rawPm25})")
		} else {
			String rawTemperature = row[RESPONSE_FIELDS['temperature']]?.toString()
			String rawConfidence = row[RESPONSE_FIELDS['confidence']]?.toString()
			String rawPositionRating = RESPONSE_FIELDS.containsKey('position_rating') ? row[RESPONSE_FIELDS['position_rating']]?.toString() : null
			Float pm25Value = rawPm25.toFloat()
			pm25_conv = apply_conversion(conversion ?: "none", pm25Value, this_humidity)
			sensors << [
				'site': row[RESPONSE_FIELDS['name']],
				'pm25': pm25Value,
				'pm25_conv': pm25_conv,
				'temperature': rawTemperature?.isNumber() ? rawTemperature.toFloat() : null,
				'humidity': reportedHumidity,
				'confidence': rawConfidence?.isNumber() ? rawConfidence.toInteger() : null,
				'distance': distance(data.coords, sensor_coords),
				'coords': sensor_coords,
				'position_rating': rawPositionRating?.isNumber() ? rawPositionRating.toInteger() : -1
			]
		}
	}
	if ( logEnable ) {
		log.debug "coords: ${data.coords}"
		log.debug "site: ${sensors.collect { it['site'] }}"
		log.debug "particle ct query: ${data.pm25_count}"
		log.debug "confidence: ${sensors.collect { it['confidence'] }}"
		log.debug "humidity: ${sensors.collect { it['humidity'] }}"
		log.debug "pm2.5: ${sensors.collect { it['pm25'] }}"
		log.debug "pm2.5_conv: ${sensors.collect { it['pm25_conv'] }}"
		log.debug "PM2.5 AQIs: ${sensors.collect { getPart2_5_AQI(it['pm25']) }}"
		log.debug "PM2.5 AQIs (${conversion?:"none"}): ${sensors.collect { getPart2_5_AQI(it['pm25_conv']) }}"
		log.debug "distance: ${sensors.collect { it['distance'] }}"
		log.debug "position_rating: ${sensors.collect { it['position_rating'] }}"
		if ( device_search ) {
			log.debug "unweighted av PM 2.5 aqi (${conversion?:"none"}): ${getPart2_5_AQI(sensorAverage(sensors, 'pm25_conv'))}"
			log.debug "weighted av PM 2.5 aqi (${conversion?:"none"}): ${getPart2_5_AQI(sensorAverageWeighted(sensors, 'pm25_conv', data.coords))}"
		}
	}

	if (sensors.size() == 0) {
		if (device_search) {
			log.error "No sensors found in search area: ${formatCoords(data.coords)} within ${search_range} ${unit}. Check coords / increase range / verify confidence threshold (currently ${resolveConfidenceThreshold()}%)."
		} else {
			log.error "Selected sensor returned no valid data (${requestContext(data?.coords as Float[])})"
		}
		return
	}

	Float aqiSourceValue
	Float pm25RawValue
	Float temperatureValue
	Float humidityValue
	if (weighted_avg && device_search) {
		aqiSourceValue = sensorAverageWeighted(sensors, conversion ? 'pm25_conv' : 'pm25', data.coords)
		pm25RawValue = sensorAverageWeighted(sensors, 'pm25', data.coords)
		temperatureValue = sensorAverageWeighted(sensors, 'temperature', data.coords)
		humidityValue = sensorAverageWeighted(sensors, 'humidity', data.coords)
	} else {
		aqiSourceValue = sensorAverage(sensors, conversion ? 'pm25_conv' : 'pm25')
		pm25RawValue = sensorAverage(sensors, 'pm25')
		temperatureValue = sensorAverage(sensors, 'temperature')
		humidityValue = sensorAverage(sensors, 'humidity')
	}
	if (aqiSourceValue == null) {
		log.error "No valid PM2.5 data returned from PurpleAir (${requestContext(data?.coords as Float[])})"
		return
	}

	aqi2_5Value = getPart2_5_AQI(aqiSourceValue)
	String AQIcategory = getCategory(aqi2_5Value)
	BigDecimal pm25Display = roundToScale(pm25RawValue, 1)
	Float hubTemperatureValue = convertTemperatureToHubScale(temperatureValue)
	BigDecimal temperatureDisplay = roundToScale(hubTemperatureValue, 1)
	String temperatureUnit = (location?.temperatureScale == "C") ? "°C" : "°F"
	Integer humidityDisplay = humidityValue != null ? Math.round(humidityValue) : null
	Integer confidenceValue = sensors.collect { it['confidence'] }.findAll { it instanceof Number }.collect { it.toInteger() }.min()

	List<String> siteNames = sensors.collect { it['site']?.toString() }.findAll { it }.sort()
	sites = siteNames.join(', ')

	if (sensors.size() == 1) {
		emitIfChanged("sites", sites, "${device.displayName} sites is ${sites}")
	} else {
		emitIfChanged("sites", sites, "${device.displayName} sites averaged from ${sensors.size()} sites: ${sites}")
	}
	if (pm25Display != null) {
		emitIfChanged("pm2_5", pm25Display, "${device.displayName} pm2_5 is ${pm25Display} µg/m³", "µg/m³")
	}
	if (temperatureDisplay != null) {
		emitIfChanged("temperature", temperatureDisplay, "${device.displayName} temperature is ${temperatureDisplay}${temperatureUnit}", temperatureUnit)
	}
	if (humidityDisplay != null) {
		emitIfChanged("humidity", humidityDisplay, "${device.displayName} humidity is ${humidityDisplay}%", "%")
	}
	if (confidenceValue != null) {
		String confidenceDesc = (device_search && sensors.size() > 1) ?
			"${device.displayName} confidence is ${confidenceValue}% (lowest contributing sensor score)" :
			"${device.displayName} confidence is ${confidenceValue}%"
		emitIfChanged("confidence", confidenceValue, confidenceDesc, "%")
	}
	emitIfChanged("category", AQIcategory, "${device.displayName} category is ${AQIcategory}")
	if (conversion) {
		emitIfChanged("conversion", conversion, "${device.displayName} conversion is ${conversion}")
	}
	String aqiDesc = conversion ? "${device.displayName} AQI is ${aqi2_5Value} (${conversion})" : "${device.displayName} AQI is ${aqi2_5Value}"
	emitIfChanged("aqi", aqi2_5Value, aqiDesc, "AQI")
	emitIfChanged("airQualityIndex", aqi2_5Value, "${device.displayName} air quality index is ${aqi2_5Value}", "AQI")
	touchActivity()
}

void humidityHistoryUpdate(Map history, String site, Integer val) {
	// FIX (0.4.1): Calendar.HOUR returns 0-11 (12-hour clock) so AM and PM samples collided in
	// the same bucket. Use HOUR_OF_DAY (0-23) for distinct hourly buckets across the full day.
	String hr = (new Date())[Calendar.HOUR_OF_DAY].toString()

	if ( history.containsKey(site) ) {
		history[(site)][(hr)] = val
	} else {
		history[(site)] = [(hr): val]
	}
}

Boolean humidityDeviceUpdating(Map history, String site) {
	final int MIN_HISTORY = 3
	Integer hr = (new Date())[Calendar.HOUR_OF_DAY]

	if (! history.containsKey(site) ) {
		return null
	}

	String last_hr
	Integer last_val = 0
	for (int i = 0; i <= 23; i++) {
		last_hr = ((hr + 24 - i) % 24).toString()
		//history[(site)].each { logDebug "${it.key}"; q(it.key)}
		if ( history[(site)][(last_hr)] ) {
			last_val = history[(site)][(last_hr)]
			break
		}
	}
	//logDebug "last_val: ${last_val}"

	if (last_val == 0) {
		logDebug "${site} hum offline"
		return false
	} else if ( history[(site)].size() < MIN_HISTORY ) {
		logDebug "${site} hum passing. insufficient history"
		return true
	}

	if (! history[(site)].any {it.value != last_val} ) {
		logDebug "${site} hum not updating ${history[(site)]}"
	}

	return history[(site)].any {it.value != last_val}
}

Float sensorAverage(List<Map> sensors, String field) {
	Integer count = 0
	Float sum = 0.0

	sensors.each { sensor ->
		Float value = numericValue(sensor[field])
		if (value != null) {
			sum += value
			count += 1
		}
	}

	if (count > 0) {
		return sum / count
	}
	log.warn "sensorAverage: No numeric '${field}' values available"
	return null
}

Float sensorAverageWeighted(List<Map> sensors, String field, Float[] coords) {
	List<Map> validSensors = sensors.findAll { numericValue(it[field]) != null }
	if (!validSensors) {
		log.warn "sensorAverageWeighted: No numeric '${field}' values available"
		return null
	}

	List<Map> zeroDistanceSensors = validSensors.findAll {
		Float sensorDistance = numericValue(it['distance'])
		sensorDistance != null && sensorDistance <= 0.001f
	}
	if (zeroDistanceSensors) {
		return sensorAverage(zeroDistanceSensors, field)
	}

	Float count = 0.0
	Float sum = 0.0
	ArrayList distances = []
	Float nearest = 0.0

	// Weighted average. First find nearest sensor. Then divide sensors distances by nearest distance to get weights.
	validSensors.each { sensor ->
		distances.add(numericValue(sensor['distance']) ?: 0.0)
	}
	nearest = distances.min()

	validSensors.eachWithIndex { sensor, i ->
		Float val = numericValue(sensor[field])
		Float positionRating = numericValue(sensor['position_rating'])
		// FIX (0.4.1): A missing position_rating was previously coerced to -1, making (rating+1)=0
		// and silently excluding the sensor from the weighted average. Treat missing as neutral (0)
		// so the sensor contributes baseline distance-based weight without a position-rating boost.
		Float ratingBoost = (positionRating != null && positionRating >= 0) ? positionRating + 1 : 1
		Float weight = nearest / Math.sqrt(distances[i]) * ratingBoost
		sum += val * weight
		count += weight
	}
	if (count > 0) {
		return sum / count
	}
	log.warn "sensorAverageWeighted: No usable weighted '${field}' values available"
	return null
}

// getAQI and AQILinear functions from https://www.airnow.gov/aqi/aqi-calculator
// (https://www.airnow.gov/sites/default/files/custom-js/conc-aqi.js)
Integer getPart2_5_AQI(Float partCount) {
	Float c = Math.floor(10*partCount)/10
	if ( c >= 0 && c < 12.1 ) {
		return AQILinear(50,0,12,0,c)
	} else if ( c >= 12.1 && c < 35.5 ) {
		return AQILinear(100,51,35.4,12.1,c)
	} else if ( c >= 35.5 && c < 55.5 ) {
		return AQILinear(150,101,55.4,35.5,c)
	} else if ( c >= 55.5 && c < 150.5 ) {
		return AQILinear(200,151,150.4,55.5,c)
	} else if ( c >= 150.5 && c < 250.5 ) {
		return AQILinear(300,201,250.4,150.5,c)
	} else if ( c >= 250.5 && c < 350.5 ) {
		return AQILinear(400,301,350.4,250.5,c)
	} else if ( c >= 350.5 && c < 500.5 ) {
		return AQILinear(500,401,500.4,350.5,c)
	} else if ( c >= 500.5 ) {
		return Math.round(c)
	} else {
		return -1
	}
}

Integer getPart10_AQI(Float partCount) {
	Float c = Math.floor(partCount);
	if (c>=0 && c<55) {
		return AQILinear(50,0,54,0,c);
	} else if (c>=55 && c<155) {
		return AQILinear(100,51,154,55,c);
	} else if (c>=155 && c<255) {
		return AQILinear(150,101,254,155,c);
	} else if (c>=255 && c<355) {
		return AQILinear(200,151,354,255,c);
	} else if (c>=355 && c<425) {
		return AQILinear(300,201,424,355,c);
	} else if (c>=425 && c<505) {
		return AQILinear(400,301,504,425,c);
	} else if (c>=505 && c<605) {
		return AQILinear(500,401,604,505,c);
	} else if ( c >= 605 ) {
		return Math.round(c)
	} else {
		return -1
	}
}

Integer AQILinear(Integer AQIhigh, Integer AQIlow, Float Conchigh, Float Conclow, Float Concentration) {
	Float a = ((Concentration-Conclow)/(Conchigh-Conclow))*(AQIhigh-AQIlow)+AQIlow
	return Math.round(a)
}

String getCategory(Integer AQI) {
	if ( AQI >= 0 && AQI <= 50 ) {
		return "Good"
	} else if ( AQI > 50 && AQI <= 100 ) {
		return "Moderate"
	} else if ( AQI > 100 && AQI <= 150 ) {
		return "Unhealthy for sensitive groups"
	} else if ( AQI > 150 && AQI <= 200 ) {
		return "Unhealthy"
	} else if ( AQI > 200 && AQI <= 300 ) {
		return "Very unhealthy"
	} else if ( AQI > 300 && AQI <= 500) {
		return "Hazardous"
	} else if ( AQI > 500 ) {
		return "Extremely hazardous!"
	} else {
		return "error"
	}
}

Float apply_conversion(String conversion, Float PM25, Float RH) {
	if ( conversion == "US EPA" ) {
		return us_epa_conversion(PM25, RH)
	} else if ( conversion == "Woodsmoke" ) {
		return woodsmoke_conversion(PM25)
	// FIX #1 (Trinity audit): was "AQ and U" — never matched preference value "AQ&U"; AQ&U conversion was dead code.
	} else if ( conversion == "AQ&U" ) {
		return AQandU_conversion(PM25)
	} else if ( conversion == "LRAPA" ) {
		return lrapa_conversion(PM25)
	} else {
		return PM25
	}
}

Float us_epa_conversion(Float PM, Float RH) {
	// y={0 ≤ x <30: 0.524*x - 0.0862*RH + 5.75}
	// y={30≤ x <50: (0.786*(x/20 - 3/2) + 0.524*(1 - (x/20 - 3/2)))*x -0.0862*RH + 5.75}
	// y={50 ≤ x <210: 0.786*x - 0.0862*RH + 5.75}
	// y={210 ≤ x <260: (0.69*(x/50 – 21/5) + 0.786*(1 - (x/50 – 21/5)))*x - 0.0862*RH*(1 - (x/50 – 21/5)) + 2.966*(x/50 – 21/5) + 5.75*(1 - (x/50 – 21/5)) + 8.84*(10^{-4})*x^{2}*(x/50 – 21/5)}
	// y={260 ≤ x: 2.966 + 0.69*x + 8.84*10^{-4}*x^2}
	//
	// y= corrected PM2.5 µg/m3
	// x= PM2.5 cf_atm (lower)
	// RH= Relative humidity as measured by the PurpleAir
	//
	// Source: https://cfpub.epa.gov/si/si_public_record_report.cfm?dirEntryId=353088&Lab=CEMM
	// PDF, p26

	Float c

	if ( PM < 30 ) {
		c = 0.524 * PM - 0.0862 * RH + 5.75
	} else if ( PM < 50 ) {
		c = (0.786 * (PM/20 - 3/2) + 0.524 * (1 - (PM/20 - 3/2))) * PM -0.0862 * RH + 5.75
	} else if ( PM < 210 ) {
		c = 0.786 * PM - 0.0862 * RH + 5.75
	} else if ( PM < 260 ) {
		c = 0.69*(PM/50 - 21/5) + 0.786*(1 - (PM/50 - 21/5))
		c = c*PM - 0.0862*RH * (1 - (PM/50 - 21/5))
		c = c + 2.966*(PM/50 - 21/5) + 5.75*(1 - (PM/50 - 21/5))
		c = c + 8.84*(10**(-4))*PM**2*(PM/50 - 21/5)
	} else {
		c = 2.966 + 0.69*PM + 8.84*(10**(-4))*(PM**2)
	}

	return (c >= 0)?c:0
}

Float woodsmoke_conversion(Float PM) {
	// Woodsmoke PM2.5 (µg/m³) = 0.55 x PA (pm2.5_cf_1) + 0.53
	// Source: map.purpleair.com
	return 0.55 * PM + 0.53
}

Float AQandU_conversion(Float PM) {
	// PM2.5 (µg/m³) = 0.778 x PA + 2.65
	// Source: map.purpleair.com
	return 0.778 * PM + 2.65
}

Float lrapa_conversion(Float PM) {
	// 0 - 65 µg/m³ range:
	// LRAPA PM2.5 (µg/m³) = 0.5 x PA (pm2.5_cf_1) – 0.66
	// Source: map.purpleair.com
	//
	// Deprecated? per https://www.lrapa.org/aqi101/
	Float c = 0.5 * PM - 0.66
	return (c >= 0)?c:0
}

Float distance(Float[] coorda, Float[] coordb) {
	if ( coorda == null || coordb == null ) return 0.0
	// Haversine function from http://www.movable-type.co.uk/scripts/latlong.html
	Double R = 6371000; // metres
	Double φ1 = Math.toRadians(coorda[0]); // φ, λ in radians
	Double φ2 = Math.toRadians(coordb[0]);
	Double Δφ = Math.toRadians(coordb[0]-coorda[0]);
	Double Δλ = Math.toRadians(coordb[1]-coorda[1]);

	Double a = Math.sin(Δφ/2) * Math.sin(Δφ/2) + Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ/2) * Math.sin(Δλ/2);
	Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

	Double d = (R * c) / 1000; // in km
	return d / 1.609 // in miles
}

// Returns miles per degree for a given latitude
Float[] distance2degrees(Float latitude) {
	Float clampedLatitude = Math.max(-89.5f, Math.min(89.5f, latitude ?: 0.0f))
	Float latMilesPerDegree = 69.172f
	Float longMilesPerDegree = 69.172f * Math.cos(Math.toRadians(clampedLatitude))

	return [latMilesPerDegree, longMilesPerDegree]
}

private Float[] parseSearchCoords() {
	if (!search_coords?.trim()) {
		log.warn "[refresh] search_coords is empty — geolocation search requires lat/lng input"
		return null
	}
	try {
		def coords = parseJson(search_coords)
		if (!(coords instanceof List) || coords.size() < 2) {
			log.warn "[refresh] search_coords must be JSON [lat, lng]"
			return null
		}
		String lat = coords[0]?.toString()
		String lng = coords[1]?.toString()
		if (!lat?.isNumber() || !lng?.isNumber()) {
			log.warn "[refresh] search_coords must be JSON [lat, lng]"
			return null
		}
		return [lat.toFloat(), lng.toFloat()] as Float[]
	} catch (Exception e) {
		log.warn "[refresh] search_coords must be JSON [lat, lng]"
		return null
	}
}

private String safeResponseBody(hubitat.scheduling.AsyncResponse resp) {
	try {
		def body = resp?.getData()
		if (body != null) {
			return body.toString()
		}
	} catch (ignored) {
	}
	try {
		def body = resp?.data
		return body != null ? body.toString() : ""
	} catch (ignoredAgain) {
		return ""
	}
}

private Map safeResponseJson(hubitat.scheduling.AsyncResponse resp) {
	try {
		if (resp?.json instanceof Map) {
			return resp.json as Map
		}
	} catch (ignored) {
	}
	try {
		def parsed = resp?.getJson()
		if (parsed instanceof Map) {
			return parsed as Map
		}
	} catch (Exception e) {
		String body = safeResponseBody(resp)
		if (!body?.trim()) {
			log.warn "[httpResponse] PurpleAir returned an empty response body (status ${resp?.getStatus()})"
		} else {
			log.warn "[httpResponse] PurpleAir returned invalid JSON (status ${resp?.getStatus()}): ${e.message}"
		}
		return null
	}
	String body = safeResponseBody(resp)
	if (!body?.trim()) {
		log.warn "[httpResponse] PurpleAir returned an empty response body (status ${resp?.getStatus()})"
	}
	return null
}

private Float numericValue(value) {
	if (value instanceof Number) {
		return value.toFloat()
	}
	String text = value?.toString()
	return text?.isNumber() ? text.toFloat() : null
}

private BigDecimal roundToScale(Float value, Integer scale = 1) {
	if (value == null) {
		return null
	}
	return BigDecimal.valueOf(value.toDouble()).setScale(scale, java.math.RoundingMode.HALF_UP)
}

private Integer normalizedUpdateIntervalMinutes(Integer defaultValue = 60) {
	Integer interval = update_interval?.toString()?.isNumber() ? update_interval.toString().toInteger() : null
	if ([0, 1, 5, 10, 15, 30, 60, 180].contains(interval)) {
		return interval
	}
	return defaultValue
}

private void applyRefreshSchedule(Integer updateIntervalMinutes) {
	switch (updateIntervalMinutes) {
		case 0:
			return
		case 1:
			runEvery1Minute('refresh')
			return
		case 5:
			runEvery5Minutes('refresh')
			return
		case 10:
			runEvery10Minutes('refresh')
			return
		case 15:
			runEvery15Minutes('refresh')
			return
		case 30:
			runEvery30Minutes('refresh')
			return
		case 60:
			runEvery1Hour('refresh')
			return
		case 180:
			runEvery3Hours('refresh')
			return
		default:
			runEvery1Hour('refresh')
	}
}

private Integer calculateRetryDelaySeconds(Integer updateIntervalMinutes, Integer failCount) {
	Integer multiplier = (failCount <= 4) ? failCount : (failCount == 5 ? failCount : 6)
	return updateIntervalMinutes * multiplier * 60
}

private void scheduleRetryGuarded(Integer updateIntervalMinutes, Integer delaySeconds, String context) {
	if (updateIntervalMinutes == 0) {
		log.warn "[httpResponse] Polling disabled; skipping retry schedule (${context})"
		return
	}
	runIn(delaySeconds, 'refresh')
}

private String requestContext(Float[] coords = null) {
	if (device_search) {
		return "mode=geo-search, coords=${formatCoords(coords)}"
	}
	return "mode=single-sensor, sensor_index=${sensor_index}"
}

private String formatCoords(Float[] coords = null) {
	if (coords instanceof Float[] && coords.length >= 2) {
		return "[${coords[0]}, ${coords[1]}]"
	}
	return search_coords?.trim() ?: "[missing search_coords]"
}

private Integer resolveConfidenceThreshold() {
	String raw = settings?.confidence_threshold?.toString()
	return raw?.isNumber() ? raw.toInteger() : 90
}

private Float convertTemperatureToHubScale(Float temperatureFahrenheit) {
	if (temperatureFahrenheit == null) {
		return null
	}
	if (location?.temperatureScale == 'C') {
		return (temperatureFahrenheit - 32.0f) * 5.0f / 9.0f
	}
	return temperatureFahrenheit
}

void logDebug(String s) {
	if (logEnable) log.debug s
}

private void emitIfChanged(String name, value, String descTxt, String unit = null) {
	def current = device.currentValue(name)
	boolean changed
	if (current instanceof Number || value instanceof Number) {
		try { changed = (current as BigDecimal) != (value as BigDecimal) }
		catch (Exception e) { changed = current?.toString() != value?.toString() }
	} else {
		changed = current?.toString() != value?.toString()
	}
	if (!changed) return
	Map evt = [name: name, value: value, descriptionText: descTxt]
	if (unit) evt.unit = unit
	sendEvent(evt)
}

private void touchActivity() {
	Long lastEmittedAt = (state.lastActivityEmittedAt ?: 0L) as Long
	if ((now() - lastEmittedAt) < 60000L) {
		return
	}
	state.lastActivityEmittedAt = now()
	sendEvent(name: "lastActivity",
	          value: new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX"),
	          descriptionText: "${device.displayName} last activity")
}

void logsOff() {
	log.info "PurpleAir AQI: debug logging disabled"
	device.updateSetting("logEnable", [value: false, type: "bool"])
}

// One-time migration: 0.4.0 and earlier bucketed HUMIDITY_HISTORY by Calendar.HOUR (0–11, 12-hour
// clock) which collided AM/PM samples. 0.4.1 switched to HOUR_OF_DAY (0–23). Clear stale buckets
// so the new logic doesn't read 12-hour keys as 24-hour keys. Runs once per device.
private void migrateHumidityHistoryBuckets() {
	if (state.humidityHistorySchema == 24) {
		return
	}
	if (state.HUMIDITY_HISTORY) {
		log.info "PurpleAir AQI: clearing legacy 12-hour HUMIDITY_HISTORY buckets (0.4.1 migration)"
		state.remove('HUMIDITY_HISTORY')
	}
	state.humidityHistorySchema = 24
}
