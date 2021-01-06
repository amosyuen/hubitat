/**
 *	Eight Sleep Mattress
 *
 *	Copyright 2020 Amos Yuen, Alex Lee Yuk Cheung
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *			http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 *	VERSION HISTORY
 *	2.0.2 (2021-01-05) [Amos Yuen] - Remove stray schedule call and add missing thermostat methods
 *	2.0.1 (2021-01-05) [Amos Yuen] - Add heatLevelReached attribute
 *	2.0 (2021-01-04) [Amos Yuen] - Clean up code and port to hubitat
 *	1.2 (2020-01-28) [Amos Yuen] - Add support for cooling on the pod
 *			- Use the user interval for bed and room temperature detection
 *			- Use the user trend for presence and asleep detection
 *	1.1 (2017-11-13) [Alex Cheung] - Add back up method for determining sleep event if presence values from API become unreliable.
 *	1.0 (2017-01-26) [Alex Cheung] - Remove BETA label.
 *
 *	1.0 BETA 8c (2017-01-25) [Alex Cheung] - Stop Infinite Loop / Divide by Errors.
 *	1.0 BETA 8b (2017-01-20) [Alex Cheung] - Ensure one sleep score notification per day.
 *	1.0 BETA 8 (2017-01-19) [Alex Cheung] - Sleep score stored as "battery" capability for rule building. 
 *			- Sleep score notifications via Eight Sleep (Connect) app. 
 *			- Tweaks to 8slp bed event frequency. 
 *			- Tile display changes.
 *	1.0 BETA 7f (2017-01-17) [Alex Cheung] - Bug fix. Out of Bed detection.
 *	1.0 BETA 7e (2017-01-17) [Alex Cheung] - Bug fix. Mark device as Connected after offline event has finished. More tweaks to bed presence logic.
 *	1.0 BETA 7d (2017-01-15) [Alex Cheung] - Further tweaks to bed presence logic. Fix to chart when missing sleep data.
 *	1.0 BETA 7c (2017-01-15) [Alex Cheung] - Bug Fix. Time zone support. Added device handler version information.
 *	1.0 BETA 7b (2017-01-15) [Alex Cheung] - Bug Fix. Broken heat duration being sent on "ON" command. Tweak to "out of bed" detection.
 *	1.0 BETA 7 (2017-01-12) [Alex Cheung] - Tweaks to bed presence logic.
 *	1.0 BETA 6c (2017-01-13) [Alex Cheung] - 8Slp Event minor fixes. Not important to functionality.
 *	1.0 BETA 6b (2017-01-13) [Alex Cheung] - Bug fix. Stop heatingDurationSeconds being reset when "on" command is sent while device is already on.
 *	1.0 BETA 6 (2017-01-13) [Alex Cheung] - Changes to bed presence contact behaviour.
 *			- Handle scenario of no partner credentials. 
 *	1.0 BETA 5 (2017-01-13) [Alex Cheung] - Historical sleep chart improvements showing SleepScore.
 *	1.0 BETA 4c (2017-01-12) [Alex Cheung] - Better "Offline" detection and handling.
 *	1.0 BETA 4b (2017-01-12) [Alex Cheung] - Minor event messaging improvements.
 *	1.0 BETA 4 (2017-01-12) [Alex Cheung] - Further refinements to bed presence contact behaviour.
 *	1.0 BETA 3c (2017-01-11) [Alex Cheung] - Use Google Chart image API for Android support
 *	1.0 BETA 3b (2017-01-11) [Alex Cheung] - Further Chart formatting update
 *	1.0 BETA 3 (2017-01-11) [Alex Cheung] - Chart formatting update
 *			- Attempt to improve bed detection
 *	1.0 BETA 2 (2017-01-11) [Alex Cheung] - Change set level behaviour
 *			- Support partner sleep trend data
 *			- Timer display changes
 *			- Add slider control on main tile
 *			- Many bug fixes
 *	1.0 BETA 1 (2017-01-11) [Alex Cheung] - Initial
 */

import groovy.transform.Field

private def textVersion() {
	 def text = "Eight Sleep Mattress\nVersion: 2.0.2\nDate: 2021-01-05"
}

private def textCopyright() {
	 def text = "Copyright © 2021 Amos Yuen, Alex Cheung"
}

metadata {
	definition (name: "Eight Sleep Mattress", namespace: "amosyuen", author: "Amos Yuen") {
		capability "Actuator"
		capability "Battery"
		capability "Presence Sensor"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
		capability "Thermostat"
		capability "Thermostat Fan Mode"
		capability "Thermostat Cooling Setpoint"
		capability "Thermostat Heating Setpoint"
		capability "Thermostat Mode"
		capability "Thermostat Operating State"
		capability "Temperature Measurement"
				
		attribute "pollIntervalSeconds", "number"
		attribute "heatLevel", "number"
		attribute "targetHeatLevel", "number"
		attribute "lastUpdated", "string"
		attribute "bedTemperature", "number"
		attribute "bedTemperatureLastUpdated", "string"
		attribute "roomTemperature", "number"
		attribute "roomTemperatureLastUpdated", "string"
		attribute "inBed", "boolean"
		attribute "isAsleep", "boolean"
		attribute "heatLevelReached", "boolean"
				
		command "setPollIntervalSeconds", [[
			name: "Target Heat Level",
			minimum: 0,
			type: "NUMBER",
			description: "Interval in seconds to poll the mattress. A value of 0 disables polling. " +
				"Values greater than 60 will be rounded to the nearest minute."]]
		command "setTargetHeatLevel", [[
			name: "Target Heat Level",
			minimum: -100,
			maximum: 100,
			type: "NUMBER",
			description: "Target Heat level. Negative level is cooling."]]
		command "componentOn"
		command "componentOff"
		command "componentSetLevel"
		command "componentRefresh"
	}

	preferences {
		input(name: "createCoolDimmerChild", type: "bool", title: "Create child dimmer to control cooling", defaultValue: false, submitOnChange: true, displayDuringSetup: false, required: false)
		input(name: "createHeatDimmerChild", type: "bool", title: "Create child dimmer to control heating", defaultValue: false, submitOnChange: true, displayDuringSetup: false, required: false)
		input(name: "createBedPresenceChild", type: "bool", title: "Create child presence for bed presence", defaultValue: false, submitOnChange: true, displayDuringSetup: false, required: false)
		input(name: "createAsleepPresenceChild", type: "bool", title: "Create child presence for asleep presence", defaultValue: false, submitOnChange: true, displayDuringSetup: false, required: false)
		input(name: "debugLogging", type: "bool", title: "Log debug statements", defaultValue: false, submitOnChange: true, displayDuringSetup: false, required: false)
		input(name: "traceLogging", type: "bool", title: "Log trace statements", defaultValue: false, submitOnChange: true, displayDuringSetup: false, required: false)
	}
}

//
// Life Cycle
//

def installed() {
	init()
}

def updated() {
	init()
}

def init() {
	logger.info("init")
	unschedule()

	def tokens = device.deviceNetworkId.tokenize("/")
	state.bedId = tokens[0]
	state.bedSide = tokens[1]
	state.userId = tokens[2]
	
	createChildDevicesIfNotExist()
	
	if (device.currentPollIntervalSeconds == null) {
		setPollIntervalSeconds(300)
	}
	if (device.currentTargetHeatLevel == null) {
		sendEvent(name: "targetHeatLevel", value: 10, displayed: false)
	}
	
	sendEvent(name: "supportedThermostatModes", value: ["cool", "heat", "off"])
	sendEvent(name: "supportedThermostatFanModes", value: [])
	sendEvent(name: "thermostatFanMode", value: "auto")
	sendEvent(name: "version", value: textVersion(), displayed: false)
	
	refresh()
}

//
// Child
//

def createChildDevicesIfNotExist() {
	logger.debug("createChildDevicesIfNotExist")
	updateChildDevice(state.supportsCooling && createCoolDimmerChild, "Generic Component Dimmer", getChildCoolId(), device.label + " Cool")
	updateChildDevice(createHeatDimmerChild, "Generic Component Dimmer", getChildHeatId(), device.label + " Heat")
	updateChildDevice(createBedPresenceChild, "Virtual Presence", getChildBedPresenceId(), device.label + " Presence")
	updateChildDevice(createAsleepPresenceChild, "Virtual Presence", getChildAsleepPresenceId(), device.label + " Asleep Presence")
}

def updateChildDevice(shouldCreate, type, id, label) {
	def child = getChildDevice(id)
	
	if (child) {
		if (!shouldCreate) {
			logger.info("updateChildDevice: Deleting child device ${label}")
			deleteChildDevice(id)
		}
	} else if (shouldCreate) {
		logger.info("updateChildDevice: Creating child device ${label}")
		addChildDevice("hubitat", type, id, 
				[
					label: label,
					componentName: id,
					componentLabel: label,
					completedSetup: true
				])
	}
}

def getChildCoolId() {
	return "${device.deviceNetworkId}_cool"
}

def getChildHeatId() {
	return "${device.deviceNetworkId}_heat"
}

def getChildBedPresenceId() {
	return "${device.deviceNetworkId}_bed"
}

def getChildAsleepPresenceId() {
	return "${device.deviceNetworkId}_asleep"
}

//
// Child Event Handlers
//

def componentSetLevel(componentDevice, level, transitionTime = null) {
	if (!componentDevice) { return }
	logger.debug("componentSetLevel: componentDevice=${componentDevice} level=${level}")
	if (componentDevice.deviceNetworkId == getChildHeatId()) {
		setTargetHeatLevel(level)
	} else if (componentDevice.deviceNetworkId == getChildCoolId()) {
		setTargetHeatLevel(-level)
	}
}

def componentOn(componentDevice) {
	if (!componentDevice) { return }
	logger.debug("componentOn: componentDevice=${componentDevice}")
	on()
	if (componentDevice.deviceNetworkId == getChildHeatId()) {
		setTargetHeatLevel(Math.max(10, device.currentTargetHeatLevel as Integer))
	} else if (componentDevice.deviceNetworkId == getChildCoolId()) {
		setTargetHeatLevel(Math.min(-10, device.currentTargetHeatLevel as Integer))
	}
}

def componentOff(componentDevice) {
	if (!componentDevice) { return }
	logger.debug("componentOff: componentDevice=${componentDevice}")
	off()
}

def componentRefresh(componentDevice) {
	if (!componentDevice) { return }
	refresh()
}

//
// Actions
//

def on() {
	logger.debug("on")
	setTargetHeatLevel(device.currentTargetHeatLevel == 0 ? 10 : device.currentTargetHeatLevel as Integer)
}

def off() {
	logger.debug("off")
	setTargetHeatLevel(0)
}

def cool() {
	logger.debug("cool")
	if (!state.supportsCooling) {
		logger.warn("cool() is unsupported for this device")
		return
	}
	setTargetHeatLevel(Math.min(-10, device.currentTargetHeatLevel as Integer))
}

def heat() {
	logger.debug("heat")
	setTargetHeatLevel(Math.max(10, device.currentTargetHeatLevel as Integer))
}

def auto() {
	logger.warn("auto() is unsupported")
}


def emergencyHeat() {
	logger.warn("emergencyHeat() is unsupported")
}

def setThermostatMode(mode) {
	logger.debug("setThermostatMode: mode=${mode}")
	switch (mode) {
		case "heat":
			heat()
			break
		case "cool":
			cool()
			break
		case "off":
			off()
			break
		default:
			logger.warn("setThermostatMode: Unsupported mode ${mode}")
			break
	}
}

def setCoolingSetpoint(setpoint) {
	logger.debug("setCoolingSetpoint: setpoint=${setpoint}")
	if (!state.supportsCooling) {
		logger.warn("setCoolingSetpoint() is unsupported for this device")
		return
	}
	setTargetHeatLevel(setpoint)
}

def setHeatingSetpoint(setpoint) {
	logger.debug("setHeatingSetpoint: setpoint=${setpoint}")
	setTargetHeatLevel(setpoint)
}

def fanAuto() {
	logger.warn("fanAuto() is unsupported")
}

def fanCirculate() {
	logger.warn("fanCirculate() is unsupported")
}

def fanOn() {
	logger.warn("fanOn() is unsupported")
}

def setThermostatFanMode(mode) {
	logger.warn("setThermostateFanMode() is unsupported")
}

def setSchedule(schedule) {
	logger.warn("setSchedule() is unsupported")
}

// Custom Commands
def setTargetHeatLevel(level) {
	logger.debug("setTargetHeatLevel: level=${level}")
	level = Math.min(100, level as Integer)
	level = Math.max(state.supportsCooling ? -100 : 0, level)
	def body = [
		"${state.bedSide}TargetHeatingLevel": level,
		// Value doesn't seem to matter as long as it is greater than 0
		// Server will change the heating duration
		"${state.bedSide}HeatingDuration": level == 0 ? 0 : 3600
	]
	def result = apiPUT("/devices/${state.bedId}", body).device
	updateFromBedResult(result)
}

def setPollIntervalSeconds(seconds) {
	sendEvent(name: "pollIntervalSeconds", value: seconds)
	unschedule(poll)
	if (pollIntervalSeconds > 0) {
		if (pollIntervalSeconds >= 60)  {
			def pollIntervalMinutes = Math.round(pollIntervalSeconds / 60).toInteger()
			pollIntervalSeconds = pollIntervalMinutes * 60
			schedule("0 */${pollIntervalMinutes} * * * ?",  poll)
		} else {
		  schedule("*/${pollIntervalSeconds} * * * * ?",  poll)
		}
	}
}

//
// Fetch Data
//
def poll() {
	refresh()
}

def refresh() {
	logger.info("refresh")
	
	def headers = parent.apiRequestHeaders(logger)
	if (!headers) {
		logger.error("Error getting header")
		return
	}
	
	// These are all run async
	refreshBed(headers)
	refreshUserInterval(headers)
	refreshUserTrend(headers)
}

def refreshBed(headers) {
	def fields = [
		"features",
		"lastHeard",
		"online",
		"sensorInfo",
		"${state.bedSide}NowHeating",
		"${state.bedSide}HeatingLevel",
		"${state.bedSide}HeatingDuration",
		"${state.bedSide}TargetHeatingLevel",
	]
	asyncApiGET("handleBedResponse", "/devices/${state.bedId}", headers, [filter: fields.join(",")])
}

def handleBedResponse(response, additionalData) {
	logger.debug("handleBedResponse")
	
	def data = handleAsyncResponse(response)
	updateFromBedResult(data.result)
}

def updateFromBedResult(result) {
	if (!result.online || !result.sensorInfo.connected) { 
		logger.debug("updateFromBedResult: Device is offline")
		setOffline()
		return
	}

	sendEvent(name: "presence", value: "present", displayed: true)
	def dateFormat = getLocalDateFormat("EEE, d MMM yyyy HH:mm:ss")
	sendEvent(name: "lastUpdated", value: dateFormat.format(parent.parseIsoTime(result.lastHeard)), displayed: false )

	state.supportsCooling = result.features.contains("cooling")
	def nowHeating = result["${state.bedSide}NowHeating"]
	sendEvent(name: "switch", value: nowHeating ? "on" : "off", displayed: true)
	def targetHeatLevel = result["${state.bedSide}TargetHeatingLevel"] as Integer
	def heatLevel = result["${state.bedSide}HeatingLevel"]
	if (targetHeatLevel != 0) {
		// targetHeatLevel is always 0 if not on. So don't set it if 0 so that we keep the old
		// value, which we can use when we turn it back on
		sendEvent(name: "targetHeatLevel", value: targetHeatLevel, displayed: false)
		sendEvent(name: "heatingSetpoint", value: targetHeatLevel, displayed: true)
		sendEvent(name: "coolingSetpoint", value: targetHeatLevel, displayed: true)
		sendEvent(name: "heatLevelReached", value: heatLevel == targetLevel, displayed: true)
	}
	sendEvent(name: "thermostatOperatingState", value: nowHeating ? (targetHeatLevel > 0 ? "heating" : "cooling") : "idle")
	sendEvent(name: "thermostatMode", value: nowHeating ? (targetHeatLevel > 0 ? "heat" : "cool") : "off", displayed: true)
	sendEvent(name: "heatLevel", value: heatLevel, displayed: true)
	sendEvent(name: "temperature", value: heatLevel)
	def heatingDurationSeconds = nowHeating ? result["${state.bedSide}HeatingDuration"] : 0
	def formattedTime = convertSecondsToString(heatingDurationSeconds)
	sendEvent(name: "heatingDuration", value: formattedTime, descriptionText: "Heating Duration ${formattedTime}", displayed: false)

	// Update child switches
	def childCool = getChildDevice(getChildCoolId())
	if (childCool) {
		childCool.sendEvent(name: "switch", value: targetHeatLevel < 0 ? "on" : "off", displayed: true)
		if (targetHeatLevel < 0) {
			childCool.sendEvent(name: "level", value: -targetHeatLevel, displayed: true)
		}
	}
	def childHeat = getChildDevice(getChildHeatId())
	if (childHeat) {
		childHeat.sendEvent(name: "switch", value: targetHeatLevel > 0 ? "on" : "off", displayed: true)
		if (targetHeatLevel > 0) {
			childHeat.sendEvent(name: "level", value: targetHeatLevel, displayed: true)
		} 
	}
}

def setOffline() {
	sendEvent(name: "presence", value: "not present" as String)
	sendEvent(name: "switch", value: "offline")
		
	def childCool = state.supportsCooling ? getChildDevice(getChildCoolId()) : null
	if (childCool) {
		childCool.sendEvent(name: "switch", value: "offline", displayed: true)
	}
	
	def childHeat = getChildDevice(getChildHeatId())
	if (childHeat) {
		childHeat.sendEvent(name: "switch", value: "offline", displayed: true)
	}
}

def refreshUserInterval(headers) {
	asyncApiGET("handleUserIntervalResponse", "/users/${state.userId}/intervals", headers)
}

def handleUserIntervalResponse(response, additionalData) {
	logger.debug("handleUserIntervalResponse")
	
	def data = handleAsyncResponse(response)
	def intervals = data.intervals
	def latestTempBedC
	def latestTempRoomC
	if (intervals.size() > 0) {
		def userInterval = intervals[0]
		def timeseries = userInterval.timeseries
		if (userInterval.incomplete && timeseries) {
			if (timeseries.tempBedC.size() > 0) {
				latestTempBedC = timeseries.tempBedC[timeseries.tempBedC.length - 1]
			}
			if (timeseries.tempRoomC.size() > 0) {
				latestTempRoomC = timeseries.tempRoomC[timeseries.tempRoomC.length - 1]
			}
		}
	}

	def dateFormat = getLocalDateFormat("EEE, d MMM yyyy HH:mm:ss")
	if (latestTempBedC) {
		sendEvent(name: "bedTemperature", value: scaleTemperatureC(latestTempBedC[1]), unit: location.temperatureScale, displayed: true)
		def date = parent.parseIsoTime(latestTempBedC[0])
		sendEvent(name: "bedTemperatureLastUpdated", value: dateFormat.format(parent.parseIsoTime(latestTempBedC[0])))
	} else {
		sendEvent(name: "bedTemperature", value: "50", unit: location.temperatureScale)
		sendEvent(name: "bedTemperatureLastUpdated", value: null)
	}

	if (latestTempRoomC) {
		sendEvent(name: "roomTemperature", value: scaleTemperatureC(latestTempRoomC[1]), unit: location.temperatureScale, displayed: true)
		sendEvent(name: "roomTemperatureLastUpdated", value: dateFormat.format(parent.parseIsoTime(latestTempRoomC[0])))
	} else {
		sendEvent(name: "roomTemperature", value: null)
		sendEvent(name: "roomTemperatureLastUpdated", value: null)
	}
}

def refreshUserTrend(headers) {
	// API returns the last week, so to only get today, request date 6 days in the future
	def date = getLocalDateFormat("yyyy-MM-dd").format(new Date() + 6)
	def query = [
		tz: location.timeZone.getID(),
		from: date,
		to: date,
	]
	asyncApiGET("handleUserTrendResponse", "/users/${state.userId}/trends", headers, query)
}

def handleUserTrendResponse(response, additionalData) {
	logger.debug("handleUserTrendResponse")
	
	def data = handleAsyncResponse(response)
	def days = data.days
	def userTrend
	if (days.size() > 0) {
		def day = days[days.size()-1]
		if (day.incomplete) {
			userTrend = day
		}
	}

	updateInBed(userTrend != null)
	updateIsAsleep(userTrend != null && userTrend.sleepEnd == userTrend.presenceEnd)
}

def updateInBed(boolean inBed) {
	def description = inBed ? "In bed" : "Out of bed"  
	sendEvent(name: "inBed", value: inBed, descriptionText: description, displayed: true) 
	def childBedPresence = getChildDevice(getChildBedPresenceId())
	if (childBedPresence) {
		childBedPresence.sendEvent(name: "presence",  value: inBed ? "present" : "not present", descriptionText: description, displayed: true)
	}
}

def updateIsAsleep(boolean isAsleep) {
	def description = isAsleep ? "Asleep" : "Awake"	
	sendEvent(name: "isAsleep", value: isAsleep, descriptionText: description, displayed: true) 
	def childAsleepPresence = getChildDevice(getChildAsleepPresenceId())
	if (childAsleepPresence) {
		childAsleepPresence.sendEvent(name: "presence", value: isAsleep ? "present" : "not present", descriptionText: description, displayed: true)
	}
}

//
// Helpers
//

def scaleTemperatureC(tempC) {
	if (location.temperatureScale == "F") {
		return tempC * 9 / 5 + 32
	}
	return tempC
}

def scaleTemperatureF(tempF) {
	if (location.temperatureScale == "C") {
		return (tempF - 32) * 5 / 9
	}
	return tempF
}

def convertSecondsToString(seconds) {
	def hour = (seconds / 3600) as Integer
	def minute = (seconds - (hour * 3600)) / 60 as Integer
	
	def hourString = "${hour < 10 ? "0" : ""}${hour}"
	def minuteString = "${minute < 10 ? "0" : ""}${minute}"
	
	return "${hourString}hr:${minuteString}mins"
}

def getLocalDateFormat(format = "EEE, d MMM yyyy HH:mm:ss") {
	def dateFormat = new java.text.SimpleDateFormat(format)
	dateFormat.setTimeZone(location.timeZone)
	return dateFormat
}

// Note we define our own api functions rather than using the parent methods so that logging is attributed
// to the device and exceptions are propagated correctly

def apiPUT(path, body) {
	return makeHttpCall("httpPut", path, body)
}

def makeHttpCall(methodFn, path, body = [:]) {
	logger.debug("makeHttpCall: methodFn=${methodFn},\npath=${path},\nbody=${body}")
	def headers = parent.apiRequestHeaders(logger)
	def response
	handleHttpErrors() {
		"${methodFn}"([
			uri: "${parent.apiUrl()}${path}",
			body: body,
			contentType: "application/json",
			headers: headers,
		]) { response = it }
	}
	
	if (response.status >= 400) {
		def error = "handleResponse: Error status=${response.status}, data=${response.data}"
		logger.error(error)
		throw new Error(error)
	}
	logger.trace("handleResponse: status=${response.status}, data=${response.data}")
	return response.data
}

def handleHttpErrors(Closure callback) {
	try {
		callback()
	} catch (groovyx.net.http.HttpResponseException e) {
		logger.error("makeHttpCall: HttpResponseException status=${e.statusCode}, body=${e.getResponse().getData()}", e)
		if (e.statusCode == 401) {
			// OAuth token is expired
			state.remove("eightSleepAccessToken")
			logger.warn("makeHttpCall: Access token is not valid")
		}
	} catch (java.net.SocketTimeoutException e) {
		logger.warn("makeHttpCall: Connection timed out", e)
	}
}

def asyncApiGET(callbackFn, path, headers, query = [:]) {
	logger.debug("asyncApiGet: methodFn=${callbackFn},\npath=${path},\nheaders=${headers},\nquery=${query}")
	handleHttpErrors() {
		asynchttpGet(
			callbackFn,
			[
				uri: "${parent.apiUrl()}${path}",
				query: query,
				headers: headers,
			]
		)
	}
}

def handleAsyncResponse(response) {
	if (response.hasError() || response.getStatus() >= 400) {
		throw new Error("handleAsyncResponse: Error status=${response.getStatus()}, errorMessage=${response.getErrorMessage()}, errorData=${response.getErrorData()}")
	}
	def data = response.getJson()
	logger.trace("handleAsyncResponse: status=${response.getStatus()}, data=${data}")
	return data
}

@Field final Map logger = [
	trace: { if (traceLogging) { log.trace(it) } },
	debug: { if (debugLogging) { log.debug(it) } },
	info: { log.info(it) },
	warn: { log.warn(it) },
	error: { log.error(it) },
]
