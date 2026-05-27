/**
 *  Calendar Todo Switch
 *  Author:  Mads Kristensen
 *  Version: 0.6.1 — 2026-05-26 — Idle icon/text now refresh on Save Preferences (previously only seeded on first install). Default idle text changed to "Clear".
 *  License: MIT
 *
 *  Subscribes to a webcal/iCal feed (https://...) and exposes a Switch that
 *  turns on when a calendar event whose title starts with a configured prefix
 *  (default ✅) reaches its start time. The cleaned event title (prefix
 *  removed) is published as the `todo` attribute, so dashboards like
 *  SharpTools can render the current chore on a tile.
 *
 *  The switch stays on until either:
 *    1. The user manually turns it off (e.g. via a dashboard "Done" tile), or
 *    2. The next matching event starts (latest-wins).
 *
 *  Recurring events (RRULE) are expanded into individual occurrences across
 *  the lookback/lookahead window. Each occurrence is tracked separately so a
 *  weekly chore fires fresh every week.
 *
 *  Changelog:
 *    0.6.1 — 2026-05-26 — Idle icon/text now refresh whenever preferences are saved (was only seeded on first install / when null). Default idle text changed to "Clear".
 *    0.6.0 — 2026-05-26 — New `idleEmoji` and `idleText` preferences. `todo` / `nextTodo` now publish the idle text (default "Clear") when there is no active or upcoming chore. `todoIcon` idle defaults to ✔️ but can be changed.
 *    0.5.2 — 2026-05-26 — Use a single-space placeholder for idle text attributes (`todo`, `todoStart`, `nextTodo`, `nextTodoStart`). Hubitat treats `value: ""` as "remove the attribute", which was causing the Todo row to disappear from the device page.
 *    0.5.1 — 2026-05-26 — Initialize `todo` / `todoIcon` / `todoStart` (and `nextTodo*`) to non-null defaults so dashboards never display "null". Idle icon is ✔️.
 *    0.5.0 — 2026-05-26 — Expose emoji as separate `todoIcon` / `nextTodoIcon` attributes. `todo` / `nextTodo` are now the bare cleaned title (no inlined emoji). Breaking change for v0.4.0 dashboards that relied on the inlined prefix.
 *    0.4.0 — 2026-05-26 — Per-todo emoji mapping. New `emojiMap` preference (one rule per line: `🗑️ = trash, garbage`) chooses a category icon. Whole-word case-insensitive match, first matching line wins, configurable fallback emoji (default 🔔).
 *    0.3.6 — 2026-05-26 — Fix NPE in RRULE INTERVAL/COUNT parsing when those params are absent (Elvis default returned literal but ternary re-read the null source).
 *    0.3.5 — 2026-05-26 — Detect and base64-decode response bodies. Hubitat base64-encodes responses whose Content-Type it doesn't recognize as text (Google serves text/calendar).
 *    0.3.4 — 2026-05-26 — Drop `textParser: true` so body decode returns a String directly; previous Reader-based path returned the Reader's toString() and failed the BEGIN:VCALENDAR check.
 *    0.3.3 — 2026-05-26 — Use Hubitat's `now()` instead of `System.currentTimeMillis()` (sandbox-blocked).
 *    0.3.2 — 2026-05-26 — Replaced `instanceof Reader` body decode (blocked by sandbox) with duck-typed read.
 *    0.3.1 — 2026-05-26 — Removed unused `java.time.temporal.ChronoUnit` import (blocked by the Hubitat sandbox).
 *    0.3.0 — 2026-05-26 — ContactSensor → Switch; commands renamed to on/off; attribute renamed contact → switch.
 *    0.2.0 — 2026-05-26 — RRULE expansion (DAILY/WEEKLY/MONTHLY/YEARLY + BYDAY + EXDATE); default prefix → ✅; occurrence-keyed dedupe.
 *    0.1.0 — 2026-05-26 — Initial release.
 */

import groovy.transform.Field
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Field static final String DRIVER_VERSION = "0.6.1"
@Field static final String DEFAULT_PREFIX = "\u2705" // ✅
@Field static final String DEFAULT_FALLBACK_EMOJI = "\uD83D\uDD14" // 🔔
@Field static final String DEFAULT_IDLE_ICON = "\u2714\uFE0F" // ✔️ (shown when no active todo)
@Field static final String DEFAULT_IDLE_TEXT = "Clear"
@Field static final Integer MAX_TRIGGERED_HISTORY = 500
@Field static final Integer MAX_RRULE_INSTANCES = 1000

metadata {
	definition(
		name: "Calendar Todo Switch",
		namespace: "mads",
		author: "Mads Kristensen",
		importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/calendar-todo/calendar-todo.groovy"
	) {
		capability "Sensor"
		capability "Switch"
		capability "Polling"
		capability "Initialize"
		capability "Refresh"

		attribute "todo", "string"
		attribute "todoIcon", "string"
		attribute "todoStart", "string"
		attribute "nextTodo", "string"
		attribute "nextTodoIcon", "string"
		attribute "nextTodoStart", "string"
		attribute "lastChecked", "string"
		attribute "matchingEvents", "number"

		command "clearTodo"
	}

	preferences {
		input name: "icalUrl", type: "text", title: "iCal feed URL",
			  description: "Full https:// URL to your calendar's iCal feed. webcal:// links work too — they're treated as https.",
			  required: true
		input name: "prefix", type: "text", title: "Title prefix / keyword",
			  description: "Events whose title starts with this string trigger the switch. Default is the ✅ emoji.",
			  defaultValue: DEFAULT_PREFIX, required: true
		input name: "matchMode", type: "enum", title: "Match mode",
			  options: [["prefix": "Starts with"], ["contains": "Contains anywhere"]],
			  defaultValue: "prefix", required: true
		input name: "emojiMap", type: "textarea", title: "Emoji rules (optional)",
			  description: "One rule per line: <code>EMOJI = keyword1, keyword2, ...</code>. The first rule whose keyword appears (whole word, case-insensitive) in the cleaned title wins. Example:<br><code>🗑️ = trash, garbage, waste, recycling</code><br><code>🧺 = laundry, washing</code><br><code>💊 = pills, meds, medication</code>",
			  required: false
		input name: "defaultEmoji", type: "text", title: "Fallback emoji",
			  description: "Used as todoIcon / nextTodoIcon when an event matches no rule. Leave blank for none.",
			  defaultValue: DEFAULT_FALLBACK_EMOJI, required: false
		input name: "idleEmoji", type: "text", title: "Idle icon",
			  description: "Shown as todoIcon when there is no active todo. Default ✔️.",
			  defaultValue: DEFAULT_IDLE_ICON, required: false
		input name: "idleText", type: "text", title: "Idle text",
			  description: "Shown as todo / nextTodo when there is no active or upcoming chore. Hubitat hides attributes set to empty string, so something visible works best.",
			  defaultValue: DEFAULT_IDLE_TEXT, required: false
		input name: "pollInterval", type: "enum", title: "Poll interval",
			  options: [["1": "1 min"], ["5": "5 min"], ["10": "10 min"], ["15": "15 min"], ["30": "30 min"], ["60": "1 hr"]],
			  defaultValue: "5", required: true
		input name: "lookbackHours", type: "number", title: "Lookback window (hours)",
			  description: "How far back to consider events that may still be 'active'. Default 24.",
			  defaultValue: 24, required: true
		input name: "lookaheadDays", type: "number", title: "Lookahead window (days)",
			  description: "How far ahead to scan for upcoming events. Default 14.",
			  defaultValue: 14, required: true
		input name: "logEnable", type: "bool", title: "Enable debug logging (auto-off after 30 minutes)",
			  defaultValue: false
	}
}

// ---------- Lifecycle ----------

def installed() {
	log.info "Calendar Todo: installed (v${DRIVER_VERSION})"
	state.triggeredUIDs = []
	sendEvent(name: "switch", value: "off")
	clearTodo()
	sendEvent(name: "nextTodo", value: idleTextValue())
	sendEvent(name: "nextTodoIcon", value: idleIconValue())
	sendEvent(name: "nextTodoStart", value: idleTextValue())
}

def updated() {
	log.info "Calendar Todo: preferences updated"
	unschedule()
	if (logEnable) runIn(1800, "logsOff")
	initialize()
}

def initialize() {
	if (state.triggeredUIDs == null) state.triggeredUIDs = []
	if (device.currentValue("switch") == null) {
		sendEvent(name: "switch", value: "off")
	}
	String txt = idleTextValue()
	String icon = idleIconValue()
	// When the switch is off there's no active todo, so refresh the idle
	// values every time preferences are saved (in case idleEmoji / idleText
	// changed). Otherwise just backfill anything that's missing.
	boolean refreshIdle = (device.currentValue("switch") != "on")
	if (refreshIdle || !device.currentValue("todo"))       sendEvent(name: "todo", value: txt)
	if (refreshIdle || !device.currentValue("todoIcon"))   sendEvent(name: "todoIcon", value: icon)
	if (refreshIdle || !device.currentValue("todoStart"))  sendEvent(name: "todoStart", value: txt)
	// nextTodo is recomputed by pollFeed; only backfill so we don't clobber a
	// freshly-published preview between polls.
	if (!device.currentValue("nextTodo"))      sendEvent(name: "nextTodo", value: txt)
	if (!device.currentValue("nextTodoIcon"))  sendEvent(name: "nextTodoIcon", value: icon)
	if (!device.currentValue("nextTodoStart")) sendEvent(name: "nextTodoStart", value: txt)
	schedulePolling()
	runIn(2, "pollFeed")
}

def uninstalled() {
	unschedule()
}

void logsOff() {
	log.warn "Calendar Todo: disabling debug logging"
	device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// ---------- Commands ----------

def refresh() {
	pollFeed()
}

def poll() {
	pollFeed()
}

def on() {
	if (logEnable) log.debug "Calendar Todo: manual on"
	sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch is on (manual)")
}

def off() {
	if (logEnable) log.debug "Calendar Todo: manual off"
	sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} switch is off (manually cleared)")
	clearTodo()
}

def clearTodo() {
	sendEvent(name: "todo", value: idleTextValue())
	sendEvent(name: "todoIcon", value: idleIconValue())
	sendEvent(name: "todoStart", value: idleTextValue())
}

// Idle text shown on `todo` / `nextTodo` / `*Start` when there is no active
// or upcoming chore. Configurable via the `idleText` preference; falls back
// to "All done". A blank pref is treated as the default to avoid Hubitat
// dropping the attribute (it removes attributes whose value is "").
String idleTextValue() {
	String s = (idleText != null) ? (idleText as String) : DEFAULT_IDLE_TEXT
	return s?.length() ? s : DEFAULT_IDLE_TEXT
}

String idleIconValue() {
	String s = (idleEmoji != null) ? (idleEmoji as String) : DEFAULT_IDLE_ICON
	return s?.length() ? s : DEFAULT_IDLE_ICON
}

// ---------- Scheduling ----------

void schedulePolling() {
	Integer minutes = (pollInterval ?: "5").toString().toInteger()
	switch (minutes) {
		case 1:  runEvery1Minute("pollFeed");  break
		case 5:  runEvery5Minutes("pollFeed"); break
		case 10: runEvery10Minutes("pollFeed"); break
		case 15: runEvery15Minutes("pollFeed"); break
		case 30: runEvery30Minutes("pollFeed"); break
		case 60: runEvery1Hour("pollFeed");    break
		default: runEvery5Minutes("pollFeed")
	}
	if (logEnable) log.debug "Calendar Todo: polling every ${minutes} min"
}

// ---------- Feed fetch ----------

void pollFeed() {
	String url = (icalUrl ?: "").trim()
	if (!url) {
		log.warn "Calendar Todo: no iCal URL configured"
		return
	}
	// Normalize webcal:// → https://
	if (url.toLowerCase().startsWith("webcal://")) {
		url = "https://" + url.substring("webcal://".length())
	}

	Map params = [
		uri: url,
		timeout: 30,
		headers: ["Accept": "text/calendar, text/plain, */*"]
	]
	if (logEnable) log.debug "Calendar Todo: fetching ${url}"
	try {
		asynchttpGet("handleFeed", params)
	} catch (Exception e) {
		log.error "Calendar Todo: fetch failed — ${e.message}"
	}
}

void handleFeed(hubitat.scheduling.AsyncResponse response, Map data) {
	sendEvent(name: "lastChecked", value: nowIsoString())
	if (response == null) {
		log.warn "Calendar Todo: null response"
		return
	}
	if (response.hasError() || response.getStatus() != 200) {
		log.warn "Calendar Todo: HTTP ${response.getStatus()} ${response.getErrorMessage() ?: ''}"
		return
	}

	String body = null
	try {
		body = response.getData()
		if (!body && response.data != null) {
			body = response.data.toString()
		}
	} catch (Exception e) {
		log.warn "Calendar Todo: could not read response body — ${e.message}"
		return
	}

	if (logEnable) log.debug "Calendar Todo: response body length=${body?.length() ?: 0}, head='${body?.take(80)}'"

	// Hubitat base64-encodes the body when the response Content-Type isn't recognized as text
	// (Google serves text/calendar). Detect by looking for the iCal sentinel and decode if absent.
	if (body && !body.contains("BEGIN:VCALENDAR") && body ==~ /[A-Za-z0-9+\/=\r\n]+/) {
		try {
			byte[] decoded = body.decodeBase64()
			String text = new String(decoded, "UTF-8")
			if (text.contains("BEGIN:VCALENDAR")) {
				body = text
				if (logEnable) log.debug "Calendar Todo: base64-decoded body (decoded length=${body.length()})"
			}
		} catch (Exception e) {
			if (logEnable) log.debug "Calendar Todo: base64 decode attempt failed — ${e.message}"
		}
	}

	if (!body) {
		log.warn "Calendar Todo: empty feed body"
		return
	}
	if (!body.contains("BEGIN:VCALENDAR")) {
		log.warn "Calendar Todo: response does not look like an iCal feed (no BEGIN:VCALENDAR)"
		return
	}

	List<Map> events = parseICal(body)
	if (logEnable) log.debug "Calendar Todo: parsed ${events.size()} VEVENT(s)"

	processEvents(events)
}

// ---------- Event processing ----------

void processEvents(List<Map> events) {
	long now = now()
	long lookbackMs = ((lookbackHours ?: 24) as long) * 3600L * 1000L
	long lookaheadMs = ((lookaheadDays ?: 14) as long) * 86400L * 1000L
	long windowStart = now - lookbackMs
	long windowEnd = now + lookaheadMs

	String prefixStr = (prefix ?: DEFAULT_PREFIX)
	String mode = matchMode ?: "prefix"

	// Expand RRULE-bearing events into individual occurrences in [windowStart, windowEnd]
	List<Map> expanded = expandOccurrences(events, windowStart, windowEnd)
	if (logEnable) log.debug "Calendar Todo: ${expanded.size()} occurrence(s) after RRULE expansion"

	List<Map> matching = expanded.findAll { Map ev ->
		if (ev.startMs == null) return false
		if (ev.startMs < windowStart || ev.startMs > windowEnd) return false
		String summary = (ev.summary ?: "").trim()
		if (!summary) return false
		return (mode == "contains") ? summary.contains(prefixStr) : summary.startsWith(prefixStr)
	}.sort { a, b -> (a.startMs <=> b.startMs) }

	sendEvent(name: "matchingEvents", value: matching.size())

	List<String> triggered = (state.triggeredUIDs ?: []) as List<String>

	// Drop UIDs for events no longer in the lookback window (cleanup)
	Set<String> currentUids = matching.collect { it.uid }.findAll { it } as Set<String>
	triggered = triggered.findAll { currentUids.contains(it) }

	// Most recently started, currently-due event among matching set
	Map activeEvent = matching.findAll { it.startMs <= now }.max { it.startMs }
	Map nextEvent = matching.find { it.startMs > now }

	// Trigger logic: any matching event whose start has passed and which hasn't
	// been triggered yet → fire the latest such event.
	Map untriggered = matching.findAll { it.startMs <= now && !triggered.contains(it.uid) }.max { it.startMs }
	if (untriggered) {
		String cleanTitle = cleanSummary(untriggered.summary as String, prefixStr, mode)
		String icon = pickEmoji(cleanTitle)
		String startStr = isoFromMs(untriggered.startMs as long)
		log.info "Calendar Todo: switching on for '${icon ? icon + ' ' : ''}${cleanTitle}' (start ${startStr})"
		sendEvent(name: "switch", value: "on",
				  descriptionText: "${device.displayName} switch is on — ${icon ? icon + ' ' : ''}${cleanTitle}")
		sendEvent(name: "todo", value: cleanTitle)
		sendEvent(name: "todoIcon", value: icon)
		sendEvent(name: "todoStart", value: startStr)
		triggered << (untriggered.uid as String)
		// Bound the history list
		if (triggered.size() > MAX_TRIGGERED_HISTORY) {
			triggered = triggered[-MAX_TRIGGERED_HISTORY..-1]
		}
	} else if (activeEvent && device.currentValue("switch") == "on") {
		// Keep current todo string in sync with the active event (in case title changed in calendar)
		String cleanTitle = cleanSummary(activeEvent.summary as String, prefixStr, mode)
		String icon = pickEmoji(cleanTitle)
		if (device.currentValue("todo") != cleanTitle) {
			sendEvent(name: "todo", value: cleanTitle)
			sendEvent(name: "todoStart", value: isoFromMs(activeEvent.startMs as long))
		}
		if (device.currentValue("todoIcon") != icon) {
			sendEvent(name: "todoIcon", value: icon)
		}
	}

	state.triggeredUIDs = triggered

	// Publish next-upcoming preview
	if (nextEvent) {
		String nextClean = cleanSummary(nextEvent.summary as String, prefixStr, mode)
		sendEvent(name: "nextTodo", value: nextClean)
		sendEvent(name: "nextTodoIcon", value: pickEmoji(nextClean))
		sendEvent(name: "nextTodoStart", value: isoFromMs(nextEvent.startMs as long))
		// Schedule an exact trigger at the next event's start time, so the
		// switch turns on promptly even between polls.
		long delay = ((nextEvent.startMs as long) - now) / 1000L
		if (delay > 0 && delay < 24L * 3600L) {
			runIn((delay as Integer) + 1, "pollFeed", [overwrite: false])
			if (logEnable) log.debug "Calendar Todo: scheduled trigger in ${delay}s for '${nextEvent.summary}'"
		}
	} else {
		sendEvent(name: "nextTodo", value: idleTextValue())
		sendEvent(name: "nextTodoIcon", value: idleIconValue())
		sendEvent(name: "nextTodoStart", value: idleTextValue())
	}
}

String cleanSummary(String summary, String prefixStr, String mode) {
	if (!summary) return ""
	String s = summary.trim()
	if (mode == "prefix" && prefixStr && s.startsWith(prefixStr)) {
		s = s.substring(prefixStr.length()).trim()
	} else if (mode == "contains" && prefixStr) {
		s = s.replace(prefixStr, "").replaceAll("\\s+", " ").trim()
	}
	return s
}

// Returns just the matching emoji for a cleaned title, or the fallback emoji
// (default 🔔), or "" if no rule matches and the fallback is blank.
String pickEmoji(String cleanTitle) {
	if (!cleanTitle) return ""
	String lowered = cleanTitle.toLowerCase()
	List<List> rules = parseEmojiMap(emojiMap as String)
	for (List rule : rules) {
		String emoji = rule[0] as String
		List<String> keywords = rule[1] as List<String>
		for (String kw : keywords) {
			if (!kw) continue
			String escaped = kw.replaceAll(/[\\.\[\]{}()*+?^$|]/, '\\\\$0')
			String pattern = "(?i)\\b" + escaped + "\\b"
			if (lowered =~ pattern) return emoji
		}
	}
	String fallback = (defaultEmoji != null) ? (defaultEmoji as String) : DEFAULT_FALLBACK_EMOJI
	return fallback?.trim() ? fallback.trim() : ""
}

// Parses the emojiMap preference into an ordered list of [emoji, [keywords]].
// One rule per line; format: `EMOJI = kw1, kw2, ...`. Lines starting with `#`
// and blank lines are ignored.
List<List> parseEmojiMap(String text) {
	List<List> rules = []
	if (!text) return rules
	for (String rawLine : text.split("\\r?\\n")) {
		String line = rawLine?.trim()
		if (!line || line.startsWith("#")) continue
		int eq = line.indexOf('=')
		if (eq <= 0) continue
		String emoji = line.substring(0, eq).trim()
		String rhs = line.substring(eq + 1).trim()
		if (!emoji || !rhs) continue
		List<String> keywords = []
		for (String kw : rhs.split(',')) {
			String k = kw?.trim()?.toLowerCase()
			if (k) keywords << k
		}
		if (keywords) rules << [emoji, keywords]
	}
	return rules
}

// ---------- iCal parser ----------

// Unfolds RFC 5545 line folding (continuation lines start with space or tab),
// splits into VEVENT blocks, and pulls DTSTART/DTEND/SUMMARY/UID. Ignores
// VTIMEZONE definitions and RRULE expansion.
List<Map> parseICal(String body) {
	List<String> rawLines = body.split(/\r?\n/) as List<String>
	List<String> lines = []
	for (String line : rawLines) {
		if (line == null) continue
		if ((line.startsWith(" ") || line.startsWith("\t")) && !lines.isEmpty()) {
			int last = lines.size() - 1
			lines[last] = lines[last] + line.substring(1)
		} else {
			lines << line
		}
	}

	List<Map> events = []
	Map current = null
	for (String line : lines) {
		String trimmed = line?.trim()
		if (!trimmed) continue
		if (trimmed == "BEGIN:VEVENT") {
			current = [:]
		} else if (trimmed == "END:VEVENT") {
			if (current != null) {
				events << current
				current = null
			}
		} else if (current != null) {
			int colon = trimmed.indexOf(':')
			if (colon <= 0) continue
			String left = trimmed.substring(0, colon)
			String value = trimmed.substring(colon + 1)
			// left may be "PROP" or "PROP;PARAM=VAL;PARAM2=VAL2"
			List<String> parts = left.split(';') as List<String>
			String name = parts[0].toUpperCase()
			Map<String, String> params = [:]
			for (int i = 1; i < parts.size(); i++) {
				int eq = parts[i].indexOf('=')
				if (eq > 0) {
					params[parts[i].substring(0, eq).toUpperCase()] = parts[i].substring(eq + 1)
				}
			}
			switch (name) {
				case "SUMMARY":
					current.summary = unescapeIcsText(value)
					break
				case "UID":
					current.uid = value
					break
				case "DTSTART":
					Map parsed = parseIcsDateTimeFull(value, params)
					if (parsed != null) {
						current.startMs = parsed.ms
						current.startZdt = parsed.zdt
						current.allDay = parsed.allDay
					}
					break
				case "DTEND":
					Map parsedEnd = parseIcsDateTimeFull(value, params)
					if (parsedEnd != null) current.endMs = parsedEnd.ms
					break
				case "RRULE":
					current.rrule = parseRRule(value)
					break
				case "EXDATE":
					if (current.exdates == null) current.exdates = []
					for (String part : value.split(',')) {
						Map parsedEx = parseIcsDateTimeFull(part.trim(), params)
						if (parsedEx != null) (current.exdates as List).add(parsedEx.ms as Long)
					}
					break
			}
		}
	}

	// Synthesize a UID if missing (rare; defensive)
	events.eachWithIndex { Map ev, int idx ->
		if (!ev.uid) {
			ev.uid = "synthetic-${ev.startMs ?: 0}-${(ev.summary ?: '').hashCode()}-${idx}"
		}
	}

	return events
}

String unescapeIcsText(String s) {
	if (s == null) return null
	return s.replace("\\,", ",")
			.replace("\\;", ";")
			.replace("\\n", "\n")
			.replace("\\N", "\n")
			.replace("\\\\", "\\")
}

Map parseIcsDateTimeFull(String value, Map<String, String> params) {
	if (!value) return null
	try {
		String v = value.trim()
		// All-day (VALUE=DATE) — YYYYMMDD
		if (params["VALUE"] == "DATE" || (v.length() == 8 && !v.contains("T"))) {
			LocalDate d = LocalDate.parse(v, DateTimeFormatter.ofPattern("yyyyMMdd"))
			ZoneId zone = hubZone()
			ZonedDateTime zdt = d.atStartOfDay(zone)
			return [ms: zdt.toInstant().toEpochMilli(), zdt: zdt, allDay: true]
		}
		// UTC — YYYYMMDDTHHMMSSZ
		if (v.endsWith("Z")) {
			String stripped = v.substring(0, v.length() - 1)
			LocalDateTime ldt = LocalDateTime.parse(stripped, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
			ZonedDateTime zdt = ldt.atZone(ZoneId.of("UTC"))
			return [ms: zdt.toInstant().toEpochMilli(), zdt: zdt, allDay: false]
		}
		// Floating or TZID — YYYYMMDDTHHMMSS
		LocalDateTime ldt = LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
		ZoneId zone
		String tzid = params["TZID"]
		if (tzid) {
			try {
				zone = ZoneId.of(tzid)
			} catch (Exception e) {
				if (logEnable) log.debug "Calendar Todo: unknown TZID '${tzid}', using hub zone"
				zone = hubZone()
			}
		} else {
			zone = hubZone()
		}
		ZonedDateTime zdt = ldt.atZone(zone)
		return [ms: zdt.toInstant().toEpochMilli(), zdt: zdt, allDay: false]
	} catch (Exception e) {
		if (logEnable) log.debug "Calendar Todo: could not parse date '${value}' — ${e.message}"
		return null
	}
}

Long parseIcsDateTime(String value, Map<String, String> params) {
	Map m = parseIcsDateTimeFull(value, params)
	return m?.ms as Long
}

// ---------- RRULE expansion ----------

Map parseRRule(String value) {
	Map rule = [:]
	if (!value) return rule
	for (String part : value.split(';')) {
		int eq = part.indexOf('=')
		if (eq > 0) rule[part.substring(0, eq).toUpperCase()] = part.substring(eq + 1)
	}
	return rule
}

// Expand each VEVENT into one or more occurrences within [windowStart, windowEnd].
// Non-recurring events pass through unchanged. Supports FREQ=DAILY/WEEKLY/MONTHLY/
// YEARLY with INTERVAL, COUNT, UNTIL, and (for WEEKLY) BYDAY. Honors EXDATE.
List<Map> expandOccurrences(List<Map> events, long windowStart, long windowEnd) {
	List<Map> out = []
	for (Map ev : events) {
		if (!(ev.rrule instanceof Map) || !ev.startZdt) {
			if (ev.startMs != null) out << ev
			continue
		}
		Map rule = ev.rrule as Map
		String freq = (rule.FREQ ?: "").toString().toUpperCase()
		String intervalStr = (rule.INTERVAL ?: "1").toString()
		int interval = intervalStr.isInteger() ? intervalStr.toInteger() : 1
		if (interval < 1) interval = 1
		String countStr = (rule.COUNT ?: "").toString()
		Integer countLimit = countStr.isInteger() ? countStr.toInteger() : null
		Long untilMs = null
		if (rule.UNTIL) {
			Map untilParsed = parseIcsDateTimeFull(rule.UNTIL as String, [:])
			if (untilParsed != null) untilMs = untilParsed.ms as Long
		}
		List<Long> exdates = (ev.exdates ?: []) as List<Long>
		Set<DayOfWeek> byDays = parseByDay((rule.BYDAY ?: "") as String)

		ZonedDateTime start = ev.startZdt as ZonedDateTime
		String summary = ev.summary as String
		String baseUid = (ev.uid ?: "rrule") as String
		long endLimit = (untilMs != null) ? Math.min(windowEnd, untilMs) : windowEnd

		int emitted = 0
		int safety = 0
		ZonedDateTime cursor = start
		while (safety++ < MAX_RRULE_INSTANCES) {
			if (cursor.toInstant().toEpochMilli() > endLimit) break
			if (countLimit != null && emitted >= countLimit) break

			List<ZonedDateTime> occurrences
			if (freq == "WEEKLY" && byDays) {
				// Enumerate listed weekdays within the week of `cursor`
				ZonedDateTime weekStart = cursor.with(DayOfWeek.MONDAY)
				occurrences = []
				for (int i = 0; i < 7; i++) {
					ZonedDateTime candidate = weekStart.plusDays(i)
					if (byDays.contains(candidate.getDayOfWeek())) {
						// Preserve the original time-of-day from `start`
						ZonedDateTime withTime = candidate.withHour(start.hour).withMinute(start.minute).withSecond(start.second).withNano(start.nano)
						if (!withTime.isBefore(start)) occurrences << withTime
					}
				}
			} else {
				occurrences = [cursor]
			}

			for (ZonedDateTime occ : occurrences) {
				long occMs = occ.toInstant().toEpochMilli()
				if (occMs > endLimit) break
				if (countLimit != null && emitted >= countLimit) break
				if (exdates.contains(occMs)) {
					emitted++ // EXDATE still counts toward COUNT per RFC 5545
					continue
				}
				if (occMs >= windowStart) {
					out << [
						uid: "${baseUid}::${occMs}".toString(),
						summary: summary,
						startMs: occMs,
						startZdt: occ,
						endMs: ev.endMs
					]
				}
				emitted++
			}

			// Advance cursor by INTERVAL of FREQ
			switch (freq) {
				case "DAILY":   cursor = cursor.plusDays(interval); break
				case "WEEKLY":  cursor = cursor.plusWeeks(interval); break
				case "MONTHLY": cursor = cursor.plusMonths(interval); break
				case "YEARLY":  cursor = cursor.plusYears(interval); break
				default:
					if (logEnable) log.debug "Calendar Todo: unsupported FREQ '${freq}' on '${summary}'"
					cursor = cursor.plusYears(100) // bail
			}
		}
	}
	return out
}

Set<DayOfWeek> parseByDay(String byDay) {
	if (!byDay) return null
	Map<String, DayOfWeek> map = [MO: DayOfWeek.MONDAY, TU: DayOfWeek.TUESDAY, WE: DayOfWeek.WEDNESDAY,
								  TH: DayOfWeek.THURSDAY, FR: DayOfWeek.FRIDAY, SA: DayOfWeek.SATURDAY, SU: DayOfWeek.SUNDAY]
	Set<DayOfWeek> result = [] as Set
	for (String part : byDay.split(',')) {
		String code = part.trim().toUpperCase().replaceAll('^[+-]?\\d+', '')
		if (map.containsKey(code)) result << map[code]
	}
	return result.isEmpty() ? null : result
}

ZoneId hubZone() {
	try {
		if (location?.timeZone?.ID) return ZoneId.of(location.timeZone.ID)
	} catch (Exception ignored) { }
	return ZoneId.systemDefault()
}

String nowIsoString() {
	return Instant.now().toString()
}

String isoFromMs(long ms) {
	return ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), hubZone()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

def parse(String description) {
	if (logEnable) log.debug "Calendar Todo: parse '${description}'"
}
