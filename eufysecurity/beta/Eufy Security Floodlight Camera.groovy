/**
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 *	VERSION HISTORY
 *	0.1.3 (2021-04-05) [Amos Yuen]  Use 0/1 instead of false/true for floodlightOn param value. Parse  floodlightOn param value.
 *	0.1.2 (2021-03-31) [Amos Yuen] Fix logging method passing bug
 *	0.1.1 (2021-03-26) [Amos Yuen] Initial Release
 */

import groovy.json.JsonOutput
import groovy.transform.Field

private def textVersion() {
	return "Version: 0.1.3 beta - 2021-04-05"
}

private def textCopyright() {
	return "Copyright © 2021 Amos Yuen"
}

@Field static final int PARAM_TYPE_MOTION_DETECTION = 1011
@Field static final int PARAM_TYPE_AUTO_NIGHT_VISION = 1013
@Field static final int PARAM_TYPE_DETECTION_SENSITIVITY = 1210 // low value is high sensitivity
@Field static final int PARAM_TYPE_POWER_MODE = 1246
@Field static final int PARAM_TYPE_RECORD_CLIP_LENGTH = 1249
@Field static final int PARAM_TYPE_RECORD_RETRIGGER_INTERVAL = 1250
@Field static final int PARAM_TYPE_DETECTION_TYPE = 1252
@Field static final int PARAM_TYPE_AUDIO_RECORDING = 1288
@Field static final int PARAM_TYPE_SNOOZE_TYPE = 1271
@Field static final int PARAM_TYPE_FLOODLIGHT_ON = 1400
@Field static final int PARAM_TYPE_FLOODLIGHT_BRIGHTNESS = 1401
@Field static final int PARAM_TYPE_OFF = 99904

@Field final Map DETECTION_TYPE = [
    "0": "human",
    "2": "all",
]
@Field final List DETECTION_TYPES = DETECTION_TYPE.collect { it.value }
@Field final Map DETECTION_TYPE_REVERSE = DETECTION_TYPE.collectEntries { [(it.value): it.key] }

@Field final Map POWER_MODE = [
    "0": "battery",
    "1": "surveillance",
    "2": "custom",
]
@Field final List POWER_MODES = POWER_MODE.collect { it.value }
@Field final Map POWER_MODE_REVERSE = POWER_MODE.collectEntries { [(it.value): it.key] }

metadata {
	definition (name: "Eufy Security Floodlight Camera", namespace: "amosyuen", author: "Amos Yuen") {
		capability "Actuator"
		capability "Presence Sensor"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
				
		attribute "audioRecording", "bool"
		attribute "autoNightVision", "bool"
		attribute "detectionType", "enum", DETECTION_TYPES
		attribute "detectionSensitivity", "integer"
		attribute "floodlightBrightness", "integer"
		attribute "floodlightOn", "bool"
		attribute "motionDetection", "bool"
		attribute "powerMode", "enum", POWER_MODES
		attribute "pollIntervalSeconds", "number"
		attribute "recordClipLength", "integer"
		attribute "recordRetriggerInterval", "integer"
		attribute "snoozeDurationSeconds", "number"
				
		command "audioRecordingOn"
		command "audioRecordingOff"
		command "autoNightVisionOn"
		command "autoNightVisionOff"
		command "floodlightOn"
		command "floodlightOff"
		command "motionDetectionOn"
		command "motionDetectionOff"
		command "setDetectionSensitivity", [[
			name: "sensitivity",
			type: "NUMBER",
			description: "Higher value is less sensitive, lower value is more sensitive. " +
					"Typical values range from 14 to 192."]]
		command "setDetectionType", [[
			name: "detectionType",
			type: "ENUM",
			constraints: DETECTION_TYPES]]
		command "setFloodlightBrightness", [[
			name: "brightness",
			type: "NUMBER",
			description: "Values from 0 to 100."]]
		command "setPollIntervalSeconds", [[
			name: "Seconds",
			type: "NUMBER",
			description: "Interval in seconds to poll the mattress. A value of 0 disables polling. " +
				"Values greater than 60 will be rounded to the nearest minute."]]
		command "setPowerMode", [[
			name: "powerMode",
			type: "ENUM",
			constraints: POWER_MODES]]
		command "setRecordClipLength", [[
			name: "Seconds",
			type: "NUMBER",
			description: "Max clip length to record after detecting motion."]]
		command "setRecordRetriggerInterval", [[
			name: "Seconds",
			type: "NUMBER",
			description: "Interval after a clip is recorded before a new clip can be recorded."]]

		command "snooze", [[
            name: "seconds*",
            type: "NUMBER",
            description: "Snooze time in seconds"]]
		command "snoozeClear"
	}

	preferences {
		input(name: "debugLogging", type: "bool", title: "Log debug statements", defaultValue: false, submitOnChange: true, displayDuringSetup: false, required: false)
		input(name: "traceLogging", type: "bool", title: "Log trace statements", defaultValue: false, submitOnChange: true, displayDuringSetup: false, required: false)
		input(name: "paramChangeLogging", type: "bool", title: "Log param changes", defaultValue: false, submitOnChange: true, displayDuringSetup: false, required: false)
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
	logMsg("info", "init")
	unschedule()
	
	sendEvent(name: "version", value: textVersion(), displayed: false)
	
	setPollIntervalSeconds(device.currentPollIntervalSeconds == null ? 300 : device.currentPollIntervalSeconds)
	refresh()
}

//
// Actions
//

def on() {
	logMsg("debug", "on")
    setParams([(PARAM_TYPE_OFF): 0])
}

def off() {
	logMsg("debug", "off")
    setParams([(PARAM_TYPE_OFF): 1])
}

def audioRecordingOn() {
	logMsg("debug", "audioRecordingOn")
    setParams([(PARAM_TYPE_AUDIO_RECORDING): 1])
}

def audioRecordingOff() {
	logMsg("debug", "audioRecordingOff")
    setParams([(PARAM_TYPE_AUDIO_RECORDING): 0])
}

def autoNightVisionOn() {
	logMsg("debug", "autoNightVisionOn")
    setParams([(PARAM_TYPE_AUTO_NIGHT_VISION): 1])
}

def autoNightVisionOff() {
	logMsg("debug", "autoNightVisionOff")
    setParams([(PARAM_TYPE_AUTO_NIGHT_VISION): 0])
}

def floodlightOn() {
	logMsg("debug", "floodlightOn")
    setParams([(PARAM_TYPE_FLOODLIGHT_ON): 1])
}

def floodlightOff() {
	logMsg("debug", "floodlightOff")
    setParams([(PARAM_TYPE_FLOODLIGHT_ON): 0])
}

def motionDetectionOn() {
	logMsg("debug", "motionDetectionOn")
    setParams([(PARAM_TYPE_MOTION_DETECTION): 1])
}

def motionDetectionOff() {
	logMsg("debug", "motionDetectionOff")
    setParams([(PARAM_TYPE_MOTION_DETECTION): 0])
}

def setDetectionSensitivity(value) {
	logMsg("debug", "setDetectionSensitivity: value=${value}")
    if (value < 0) {
        throw new Exception("Sensitivity ${value} must be at least 0")
    }
    setParams([(PARAM_TYPE_DETECTION_SENSITIVITY): value])
}

def setDetectionType(value) {
	logMsg("debug", "setDetectionType: value=${value}")
    value = DETECTION_TYPE_REVERSE[value]
    if (!value) {
        throw new Exception("Detection type ${value} is not supported!")
    }
    setParams([(PARAM_TYPE_DETECTION_TYPE): value])
}

def setFloodlightBrightness(value) {
	logMsg("debug", "setFloodlightBrightness: value=${value}")
    if (value < 0 || value > 100) {
        throw new Exception("Floodlight brightness ${value} must be from 0 to 100 inclusive.")
    }
    setParams([(PARAM_TYPE_FLOODLIGHT_BRIGHTNESS): value])
}

def setPollIntervalSeconds(seconds) {
    logMsg("debug", "setPollIntervalSeconds: seconds=${seconds}")
	unschedule(poll)
    if (seconds == null) {
        seconds = "null"
    } else {
        if (seconds < 0) {
            throw new Exception("Poll interval seconds ${seconds} must be greater than or equal to 0")
        }
        if (seconds > 2419200)  {
            throw new Exception("Poll interval seconds ${seconds} must be less than or equal to 2419200")
        } 
	    if (seconds > 0) {
    		if (seconds >= 86400)  {
    			def days = Math.round(seconds / 86400) as Integer
    			seconds = days * 86400
    			schedule("0 0 0 */${days} * ?", poll)
    		} else if (seconds >= 3600)  {
    			def hours = Math.round(seconds / 3600) as Integer
    			seconds = hours * 3600
    			schedule("0 0 */${hours} * * ?", poll)
    		} else if (seconds >= 60)  {
    			def minutes = Math.round(seconds / 60) as Integer
    			seconds = minutes * 60
    			schedule("0 */${minutes} * * * ?", poll)
    		} else {
    		  schedule("*/${seconds} * * * * ?", poll)
    		}
    	}
    }
	sendEvent(name: "pollIntervalSeconds", value: seconds)
}

def setPowerMode(value) {
	logMsg("debug", "setPowerMode: value=${value}")
    value = POWER_MODE_REVERSE[value]
    if (!value) {
        throw new Exception("Power mode ${value} is not supported!")
    }
    setParams([(PARAM_TYPE_POWER_MODE): value])
}

def setRecordClipLength(value) {
	logMsg("debug", "setRecordClipLength: value=${value}")
    if (value <= 0) {
        throw new Exception("Clip length seconds ${value} must be greater than 0")
    }
    setParams([(PARAM_TYPE_RECORD_CLIP_LENGTH): value])
}

def setRecordRetriggerInterval(value) {
	logMsg("debug", "setRecordRetriggerInterval: value=${value}")
    if (value <= 0) {
        throw new Exception("Retrigger interval seconds ${value} must be greater than 0")
    }
    setParams([(PARAM_TYPE_RECORD_RETRIGGER_INTERVAL): value])
}

def snooze(seconds) {
    logMsg("debug", "snooze: seconds=${seconds}")
    if (seconds < 1) {
        throw new Exception("Snooze seconds ${seconds} must be greater than or equal to 1")
    }
    def chime = 0
    def motion = 0
    def startSeconds = (now() / 1000) as int
    def snoozeTypeJson = new groovy.json.JsonBuilder([
        account_id: "",
        snooze_time: seconds,
        startTime: startSeconds,
    ]).toString()
    logMsg("debug", "snooze: snoozeTypeJson=${snoozeTypeJson}")
    setParams([
        (PARAM_TYPE_SNOOZE_TYPE): snoozeTypeJson.bytes.encodeBase64().toString(),
    ])
}

def snoozeClear() {
	logMsg("debug", "snoozeClear")
    setParams([(PARAM_TYPE_SNOOZE_TYPE): ""])
}

def setParam(type, value) {
    logMsg("debug", "setParam: type=${type}, value=${value}")
    
    def body = [
        device_sn: device.deviceNetworkId,
        station_sn: state.stationSN,
        params: [[param_type: type, param_value: value.toString()]]
    ]
    apiPOST("/app/upload_devs_params", body)
    refreshParams()
}

def setParams(paramsMap) {
    logMsg("debug", "setParams: paramsMap=${paramsMap}")
    
    def params = []
    paramsMap.each { params.add([param_type: it.key, param_value: it.value.toString()]) }
    
    def body = [
        device_sn: device.deviceNetworkId,
        station_sn: state.stationSN,
        params: params,
    ]
    apiPOST("/app/upload_devs_params", body)
    refreshParams()
}

//
// Fetch Data
//

def poll() {
	refresh()
}

def refresh() {
	logMsg("info", "refresh")
    refreshParams()
}

def refreshParams() {
	def devices
    try { 
	    devices = apiPOST("/app/get_devs_list", [ device_sn: device.deviceNetworkId ])
        if (devices.size() == 0) {
            throw new Exception("Eufy device ${device.deviceNetworkId} not associated with account!")
        }
    } catch (Exception e) {
        sendEvent(name: "presence", value: "not present", displayed: true)
        throw e
    }
    
    sendEvent(name: "presence", value: "present", displayed: true)
    def device = devices[0]
    state.stationSN = device.station_sn
    def params = device.params
    for (param in params) {
        refreshParam(param)
    }
    logParamDeltas("params", params)
    return params
}

def refreshParam(param) {
    switch (param.param_type) {
        case PARAM_TYPE_OFF:
            parseBooleanParam("switch", param, "off", "on")
            return
        case PARAM_TYPE_AUDIO_RECORDING:
            parseBooleanParam("audioRecording", param)
            return
        case PARAM_TYPE_AUTO_NIGHT_VISION:
            parseBooleanParam("autoNightVision", param)
            return
        case PARAM_TYPE_DETECTION_SENSITIVITY:
            parseNonNegativeIntParam("detectionSensitivity", param)
            return
        case PARAM_TYPE_DETECTION_TYPE:
            parseEnumParam("detectionType", DETECTION_TYPE, param)
            return
		case PARAM_TYPE_FLOODLIGHT_BRIGHTNESS:
            parseNonNegativeIntParam("floodlightBrightness", param)
            return
		case PARAM_TYPE_FLOODLIGHT_ON:
            parseBooleanParam("floodlightOn", param)
            return
        case PARAM_TYPE_MOTION_DETECTION:
            parseBooleanParam("motionDetection", param)
            return
        case PARAM_TYPE_POWER_MODE:
            parseEnumParam("powerMode", POWER_MODE, param)
            return
        case PARAM_TYPE_RECORD_CLIP_LENGTH:
            parseNonNegativeIntParam("recordClipLength", param)
            return
        case PARAM_TYPE_RECORD_RETRIGGER_INTERVAL:
            parseNonNegativeIntParam("recordRetriggerInterval", param)
            return
        case PARAM_TYPE_SNOOZE_TYPE:
            parseSnooze(param)
            return
    }
}

def parseBooleanParam(name, param, onValue = true, offValue = false) {
    def value = param.param_value as int
    if (value > 1 || value < 0) {
        logMsg("error", "parseBooleanParam: Unsupported param name=${name} value=${param.param_value}")
        value = "null"
    }
    sendEvent(name: name, value: value == 1 ? onValue : offValue, displayed: true)
}

def parseNonNegativeIntParam(name, param) {
    def value = param.param_value as int
    if (value < 0) {
        logMsg("error", "parseNonNegativeInt: Unsupported param name=${name} value=${param.param_value}")
        value = "null"
    }
    sendEvent(name: name, value: value, displayed: true)
}

def parseEnumParam(name, map, param) {
    def value = map[param.param_value]
    if (value == null) {
        logMsg("error", "parseEnumParam: Unsupported param name=${name} value=${param.param_value}")
        value = "null"
    }
    sendEvent(name: name, value: value, displayed: true)
}

def parseSnooze(param) {
    logMsg("trace", "parseSnooze param=${param}")
	unschedule(snoozeClear)
    def snoozeDurationSeconds = 0
    if (param.param_value.size() > 0) { 
        def data = parent.decodeBase64Json(param.param_value)
        logMsg("trace", "parseSnooze data=${data}")
        snoozeDurationSeconds = data.snooze_time
        snoozeStartEpochSeconds = data.startTime
		if (snoozeStartEpochSeconds) {
			// startTime only exists if snooze was started from hubitat
			// NOTE: Eufy normally clears it some other way, we clear it by just running a timer
			def secondsToWait = Math.max(1, (snoozeStartEpochSeconds + snoozeDurationSeconds) - ((now() / 1000) as int))
			runIn(secondsToWait, snoozeClear)
		}
    }
    sendEvent(name: "snoozeDurationSeconds", value: snoozeDurationSeconds, displayed: true)
}

def logParamDeltas(key, params) {
    if (!paramChangeLogging) {
        state.remove(key)
        return
    }
    def init = false
    if (state[key] == null) {
        state[key] = [:]
        init = true
    }
    def newParams = [:]
    def deltas = [:]
    for (param in params) {
        def type = param.param_type
        def oldValue = state[key][type.toString()]
        def newValue = param.param_value
        switch (type) {
            case PARAM_TYPE_SNOOZE_TYPE:
            case 1204:
                newValue = parent.decodeBase64Json(newValue)
                break
        }
        if (newValue != oldValue) {
            deltas[type] = [
                old: oldValue,
                new: newValue,
            ]
        }
        newParams[type.toString()] = newValue
    }
    state[key] = newParams
    if (!init && deltas) {
        log.info("logParamDeltas: key=${key}, deltas=${deltas}")
    }
}

//
// Helpers
//

// Note we define our own api functions rather than using the parent methods so that logging is attributed
// to the device and exceptions are propagated correctly

def apiPOST(path, body) {
	return makeHttpCall("httpPost", path, body)
}

private def makeHttpCall(methodFn, path, body = [:], refreshToken = true) {
	def headers = parent.apiRequestHeaders(logMsg, refreshToken)
    def uri = "${parent.apiUrl()}${path}"
	logMsg("trace", "makeHttpCall methodFn=${methodFn},\nuri=${uri},\nbody=${body},\nheaders=${headers}")
	def response
	handleHttpErrors() {
		"${methodFn}"([
			uri: uri,
			body: body,
			requestContentType: 'application/json',
			headers: headers
		]) { response = it }
	}
	
	return handleResponseErrors(response)
}

def handleHttpErrors(Closure callback) {
	try {
		callback()
	} catch (groovyx.net.http.HttpResponseException e) {
		logMsg("error", "handleHttpErrors: HttpResponseException status=${e.statusCode}, body=${e.getResponse().getData()}")
		if (e.statusCode == 401) {
			// OAuth token is expired
			parent.clearAuthToken()
			logMsg("warn", "handleHttpErrors: Invalid access token. Need to login again.")
		}
		throw e
	} catch (java.net.SocketTimeoutException e) {
		logMsg("warn", "handleHttpErrors: Connection timed out", e)
		throw e
	}
}

def handleResponseErrors(response) {
	return handleErrors(response.status, response.data)
}

def handleErrors(status, data) {
	if (status >= 400) {
        throw new Exception("Error status=${status}, data=${data}")
	}
	logMsg("trace", "handleErrors: status=${status}, data=${data}")
	if (data.code != 0) {
        def errorMessage = parent.getErrorMessage(data)
		throw new Exception(errorMessage)
	}
	return data.data
}

@Field final Closure logMsg = { String level, String message ->
    switch(level) {
        case "trace":
            if (traceLogging) {
                log.trace(message)
            }
            break
        case "debug":
            if (debugLogging) {
                log.debug(message)
            }
            break
        case "info":
            log.info(message)
            break
        case "warn":
            log.warn(message)
            break
        case "error":
            log.error(message)
            break
        default:
            throw new Exception("Unsupported log level ${level}")
    }
}