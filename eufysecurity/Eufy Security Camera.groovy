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
 *	0.0.2 (2020-02-16) [Amos Yuen] Add setting to log param changes for debugging
 *		- Support hourly and daily poll intervals up to 28 days
*		- Validate command params properly
 *	0.0.1 (2020-02-15) [Amos Yuen] - Add support for snooze
 *	0.0.0 (2020-02-10) [Amos Yuen] - Initial Release
 */

import groovy.json.JsonOutput
import groovy.transform.Field

private def textVersion() {
	return "Version: 0.0.2 - 2020-02-16"
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
	definition (name: "Eufy Security Camera", namespace: "amosyuen", author: "Amos Yuen") {
		capability "Actuator"
		capability "Battery"
		capability "Presence Sensor"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
				
		attribute "audioRecording", "bool"
		attribute "autoNightVision", "bool"
		attribute "detectionType", "enum", DETECTION_TYPES
		attribute "detectionSensitivity", "integer"
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
		command "setDetectionSensitivity", [[
			name: "sensitivity",
			type: "NUMBER",
			description: "Higher value is less sensitive, lower value is more sensitive. " +
					"Typical values range from 14 to 192."]]
		command "setDetectionType", [[
			name: "detectionType",
			type: "ENUM",
			constraints: DETECTION_TYPES]]
		command "motionDetectionOn"
		command "motionDetectionOff"
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
	logger.info("init")
	unschedule()
	
	sendEvent(name: "version", value: textVersion(), displayed: false)
	
	setPollIntervalSeconds(device.currentPollIntervalSeconds == null ? 300 : device.currentPollIntervalSeconds)
	refresh()
}

//
// Actions
//

def on() {
	logger.debug("on")
    setParams([(PARAM_TYPE_OFF): 0])
}

def off() {
	logger.debug("off")
    setParams([(PARAM_TYPE_OFF): 1])
}

def audioRecordingOn() {
	logger.debug("audioRecordingOn")
    setParams([(PARAM_TYPE_AUDIO_RECORDING): 1])
}

def audioRecordingOff() {
	logger.debug("audioRecordingOff")
    setParams([(PARAM_TYPE_AUDIO_RECORDING): 0])
}

def autoNightVisionOn() {
	logger.debug("autoNightVisionOn")
    setParams([(PARAM_TYPE_AUTO_NIGHT_VISION): 1])
}

def autoNightVisionOff() {
	logger.debug("autoNightVisionOff")
    setParams([(PARAM_TYPE_AUTO_NIGHT_VISION): 0])
}

def motionDetectionOn() {
	logger.debug("motionDetectionOn")
    setParams([(PARAM_TYPE_MOTION_DETECTION): 1])
}

def motionDetectionOff() {
	logger.debug("motionDetectionOff")
    setParams([(PARAM_TYPE_MOTION_DETECTION): 0])
}

def setDetectionSensitivity(value) {
	logger.debug("setDetectionSensitivity: value=${value}")
    if (value < 0) {
        throw new Exception("Sensitivity ${value} must be at least 0")
    }
    setParams([(PARAM_TYPE_DETECTION_SENSITIVITY): value])
}

def setDetectionType(value) {
	logger.debug("setDetectionType: value=${value}")
    value = DETECTION_TYPE_REVERSE[value]
    if (!value) {
        throw new Exception("Detection type ${value} is not supported!")
    }
    setParams([(PARAM_TYPE_DETECTION_TYPE): value])
}

def setPollIntervalSeconds(seconds) {
    logger.debug("setPollIntervalSeconds: seconds=${seconds}")
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
	logger.debug("setPowerMode: value=${value}")
    value = POWER_MODE_REVERSE[value]
    if (!value) {
        throw new Exception("Power mode ${value} is not supported!")
    }
    setParams([(PARAM_TYPE_POWER_MODE): value])
}

def setRecordClipLength(value) {
	logger.debug("setRecordClipLength: value=${value}")
    if (value <= 0) {
        throw new Exception("Clip length seconds ${value} must be greater than 0")
    }
    setParams([(PARAM_TYPE_RECORD_CLIP_LENGTH): value])
}

def setRecordRetriggerInterval(value) {
	logger.debug("setRecordRetriggerInterval: value=${value}")
    if (value <= 0) {
        throw new Exception("Retrigger interval seconds ${value} must be greater than 0")
    }
    setParams([(PARAM_TYPE_RECORD_RETRIGGER_INTERVAL): value])
}

def snooze(seconds) {
    logger.debug("snooze: seconds=${seconds}")
    if (seconds < 1) {
        throw new Exception("Snooze seconds ${seconds} must be greater than or equal to 1")
    }
    def chime = 0
    def motion = 0
    def startSeconds = (now() / 1000) as int
    def snoozeTypeJson = new groovy.json.JsonBuilder([
        account_id: "",
        snooze_time: seconds,
    ]).toString()
    logger.debug("snooze: snoozeTypeJson=${snoozeTypeJson}")
    setParams([
        (PARAM_TYPE_SNOOZE_TYPE): snoozeTypeJson.bytes.encodeBase64().toString(),
    ])
}

def snoozeClear() {
	logger.debug("snoozeClear")
    setParams([(PARAM_TYPE_SNOOZE_TYPE): ""])
}

def setParam(type, value) {
    logger.debug("setParam: type=${type}, value=${value}")
    
    def body = [
        device_sn: device.deviceNetworkId,
        station_sn: state.stationSN,
        params: [[param_type: type, param_value: value.toString()]]
    ]
    apiPOST("/app/upload_devs_params", body)
    refreshParams()
}

def setParams(paramsMap) {
    logger.debug("setParams: paramsMap=${paramsMap}")
    
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
	logger.info("refresh")
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
        logger.error("parseBooleanParam: Unsupported param name=${name} value=${param.param_value}")
        value = "null"
    }
    sendEvent(name: name, value: value == 1 ? onValue : offValue, displayed: true)
}

def parseNonNegativeIntParam(name, param) {
    def value = param.param_value as int
    if (value < 0) {
        logger.error("parseNonNegativeInt: Unsupported param name=${name} value=${param.param_value}")
        value = "null"
    }
    sendEvent(name: name, value: value, displayed: true)
}

def parseEnumParam(name, map, param) {
    def value = map[param.param_value]
    if (value == null) {
        logger.error("parseEnumParam: Unsupported param name=${name} value=${param.param_value}")
        value = "null"
    }
    sendEvent(name: name, value: value, displayed: true)
}

def decodeBase64Json(value) {
    if (!value) {
        return value
    }
    def json = new String(value.bytes.decodeBase64())
    return new groovy.json.JsonSlurper().parseText(json)
}

def parseSnooze(param) {
    logger.trace("parseSnooze param=${param}")
    def snoozeDurationSeconds = 0
    if (param.param_value.size() > 0) { 
        def data = decodeBase64Json(param.param_value)
        logger.trace("parseSnooze data=${data}")
        snoozeDurationSeconds = data.snooze_time
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
        if (param.param_type == PARAM_TYPE_SNOOZE_TYPE) {
            newValue = decodeBase64Json(newValue)
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
	def headers = parent.apiRequestHeaders(logger, refreshToken)
    def uri = "${parent.apiUrl()}${path}"
	logger.trace("makeHttpCall methodFn=${methodFn},\nuri=${uri},\nbody=${body},\nheaders=${headers}")
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
		logger.error("handleHttpErrors: HttpResponseException status=${e.statusCode}, body=${e.getResponse().getData()}")
		if (e.statusCode == 401) {
			// OAuth token is expired
			parent.clearAuthToken()
			logger.warn("handleHttpErrors: Invalid access token. Need to login again.")
		}
		throw e
	} catch (java.net.SocketTimeoutException e) {
		logger.warn("handleHttpErrors: Connection timed out", e)
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
	logger.trace("handleErrors: status=${status}, data=${data}")
	if (data.code != 0) {
        def errorMessage = parent.getErrorMessage(data)
		throw new Exception(errorMessage)
	}
	return data.data
}

@Field final Map logger = [
	trace: { if (traceLogging) { log.trace(it) } },
	debug: { if (debugLogging) { log.debug(it) } },
	info: { log.info(it) },
	warn: { log.warn(it) },
	error: { log.error(it) },
]