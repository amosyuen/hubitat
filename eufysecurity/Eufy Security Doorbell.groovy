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
 *	0.0.3 (2020-02-16) [Amos Yuen] Fix snooze not clearing after snooze duration
 *	0.0.2 (2020-02-16) [Amos Yuen] Add setting to log param changes for debugging
 *		- Support hourly and daily poll intervals up to 28 days
*		- Validate command params properly
 *	0.0.1 (2020-02-15) [Amos Yuen] - Add support for chime and motion snooze
 *	0.0.0 (2020-02-10) [Amos Yuen] - Initial Release
 */

import groovy.json.JsonOutput
import groovy.transform.Field

private def textVersion() {
	return "Version: 0.0.3 - 2020-02-16"
}

private def textCopyright() {
	return "Copyright © 2021 Amos Yuen"
}

@Field static final int PARAM_TYPE_MODE = 1224
@Field static final int PARAM_TYPE_SNOOZE_TYPE = 1271
@Field static final int PARAM_TYPE_ON = 2001
@Field static final int PARAM_TYPE_AUTO_NIGHT_VISION = 2002
@Field static final int PARAM_TYPE_DETECTION_TYPE = 2004
@Field static final int PARAM_TYPE_DETECTION_SENSITIVITY = 2005
@Field static final int PARAM_TYPE_MOTION_DETECTION = 2027
@Field static final int PARAM_TYPE_SNOOZE_START_SECONDS = 2037
@Field static final int PARAM_TYPE_AUDIO_RECORDING = 2042

@Field final Map DETECTION_TYPE = [
    "1": "human_and_motion",
    "2": "all",
    "3": "human",
]
@Field final List DETECTION_TYPES = DETECTION_TYPE.collect { it.value }
@Field final Map DETECTION_TYPE_REVERSE = DETECTION_TYPE.collectEntries { [(it.value): it.key] }
@Field final Map MODE = [
    "0": "away",
    "1": "home",
    "2": "schedule",
    "3": "custom1",
    "4": "custom2",
    "5": "custom3",
    "6": "off",
    "47": "geofencing",
    "63": "disarmed"
]
@Field final List MODES = MODE.collect { it.value }
@Field final Map MODE_REVERSE = MODE.collectEntries { [(it.value): it.key] }
@Field final String SNOOZE_TYPE_CHIME = "chime"
@Field final String SNOOZE_TYPE_CHIME_AND_MOTION = "chime_and_motion"
@Field final String SNOOZE_TYPE_MOTION = "motion"
@Field final List SNOOZE_TYPES = [SNOOZE_TYPE_MOTION, SNOOZE_TYPE_CHIME, SNOOZE_TYPE_CHIME_AND_MOTION]

metadata {
	definition (name: "Eufy Security Doorbell", namespace: "amosyuen", author: "Amos Yuen") {
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
		attribute "mode", "enum", MODES
		attribute "motionDetection", "bool"
		attribute "pollIntervalSeconds", "number"
		attribute "snoozeType", "enum", SNOOZE_TYPES
		attribute "snoozeDurationSeconds", "number"
		attribute "snoozeStartEpochSeconds", "number"
				
		command "audioRecordingOn"
		command "audioRecordingOff"
		command "autoNightVisionOn"
		command "autoNightVisionOff"
		command "motionDetectionOn"
		command "motionDetectionOff"

		command "setDetectionSensitivity", [[
			name: "sensitivity",
			type: "NUMBER",
			description: "Typically 1 for \"human\" and \"all\" detection types." +
                " Typically is 1-3 for \"human_and_motion\" detection type"]]
		command "setDetectionType", [[
			name: "detectionType",
			type: "ENUM",
			constraints: DETECTION_TYPES]]
		command "setMode", [[
			name: "mode",
			type: "ENUM",
			constraints: MODES]]
		command "setPollIntervalSeconds", [[
			name: "Seconds",
			type: "NUMBER",
			description: "Interval in seconds to poll the mattress. A value of 0 disables polling. " +
				"Values greater than 60 will be rounded to the nearest minute."]]
        
		command "snooze", [[
            name: "snoozeType*",
            type: "ENUM",
            constraints: SNOOZE_TYPES
        ], [
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
    setParams([(PARAM_TYPE_ON): true])
}

def off() {
	logger.debug("off")
    setParams([(PARAM_TYPE_ON): false])
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
    setParams([(PARAM_TYPE_AUTO_NIGHT_VISION): true])
}

def autoNightVisionOff() {
	logger.debug("autoNightVisionOff")
    setParams([(PARAM_TYPE_AUTO_NIGHT_VISION): false])
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

def setMode(mode) {
	logger.debug("setMode: mode=${mode}")
    def modeValue = MODE_REVERSE[mode]
    if (!modeValue) {
        throw new Exception("Mode ${mode} is not supported!")
    }
    setHubParams([(PARAM_TYPE_MODE): modeValue])
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

def snooze(type, seconds) {
    logger.debug("snooze: type=${type}, seconds=${seconds}")
    if (seconds < 1) {
        throw new Exception("Snooze seconds ${seconds} must be greater than or equal to 1")
    }
    def chime = 0
    def motion = 0
    switch(type) {
        case SNOOZE_TYPE_CHIME:
            chime = 1
            break
        case SNOOZE_TYPE_MOTION:
            motion = 1
            break
        case SNOOZE_TYPE_CHIME_AND_MOTION:
            chime = 1
            motion = 1
            break
        default:
            throw new Exception("Invalid snooze type ${type}")
    }
    def startSeconds = (now() / 1000) as int
    def snoozeTypeJson = new groovy.json.JsonBuilder([
        account_id: "",
        chime_onoff: chime,
        homebase_onoff: 0,
        motion_notify_onoff: motion,
        snooze_time: seconds,
        startTime: startSeconds
    ]).toString()
    logger.debug("snooze: snoozeTypeJson=${snoozeTypeJson}")
    setParams([
        (PARAM_TYPE_SNOOZE_START_SECONDS): startSeconds + 1,
        (PARAM_TYPE_SNOOZE_TYPE): snoozeTypeJson.bytes.encodeBase64().toString(),
    ])
}

def snoozeClear() {
	logger.debug("snoozeClear")
    setParams([(PARAM_TYPE_SNOOZE_TYPE): ""])
}

def setParams(paramsMap) {
    logger.debug("setParams: paramsMap=${paramsMap}")
    
    def params = []
    paramsMap.each { params.add([param_type: it.key, param_value: it.value.toString()]) }
    
    def body = [
        device_sn: device.deviceNetworkId,
        station_sn: device.deviceNetworkId,
        params: params,
    ]
    apiPOST("/app/upload_devs_params", body)
    refreshParams()
}

def setHubParams(paramsMap) {
    logger.debug("setHubParams: paramsMap=${paramsMap}")
    
    def params = []
    paramsMap.each { params.add([param_type: it.key, param_value: it.value.toString()]) }
    
    def body = [
        station_sn: device.deviceNetworkId,
        params: params,
    ]
    apiPOST("/app/upload_hub_params", body)
    refreshHubParams()
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
    refreshHubParams()
    state.remove("mode")
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
    def params = device.params
    for (param in params) {
        refreshParam(param)
    }
    logParamDeltas("params", params)
    return params
}

def refreshParam(param) {
    switch (param.param_type) {
        case PARAM_TYPE_ON:
            parseStringBooleanParam("switch", param, "on", "off")
            return
        case PARAM_TYPE_AUDIO_RECORDING:
            parseIntBooleanParam("audioRecording", param)
            return
        case PARAM_TYPE_AUTO_NIGHT_VISION:
            parseStringBooleanParam("autoNightVision", param)
            return
        case PARAM_TYPE_DETECTION_SENSITIVITY:
            parseNonNegativeIntParam("detectionSensitivity", param)
            return
        case PARAM_TYPE_DETECTION_TYPE:
            parseEnumParam("detectionType", DETECTION_TYPE, param)
            return
        case PARAM_TYPE_MOTION_DETECTION:
            parseIntBooleanParam("motionDetection", param)
            return
        case PARAM_TYPE_SNOOZE_TYPE:
            parseSnooze(param)
            return
    }
}

def refreshHubParams() {
	def stations
    try { 
	    stations = apiPOST("/app/get_hub_list", [ station_sn: device.deviceNetworkId ])
        if (stations.size() == 0) {
            throw new Exception("Eufy security station ${device.deviceNetworkId} not associated with account!")
        }
    } catch (Exception e) {
        sendEvent(name: "presence", value: "not present", displayed: true)
        throw e
    }
    
    sendEvent(name: "presence", value: "present", displayed: true)
    def station = stations[0]
    def params = station.params
    for (param in params) {
        refreshHubParam(param)
    }
    logParamDeltas("hubParams", params)
    return params
}

def refreshHubParam(param) {
    switch (param.param_type) {
        case PARAM_TYPE_MODE:
            parseEnumParam("mode", MODE, param)
            return
    }
}

def parseStringBooleanParam(name, param, onValue = true, offValue = false) {
    def value = param.param_value == "true"
    if (param.param_value != "true" && param.param_value != "false") {
        logger.error("parseStringBooleanParam: Unsupported param name=${name} value=${param.param_value}")
        value = "null"
    }
    sendEvent(name: name, value: value ? onValue : offValue, displayed: true)
}

def parseIntBooleanParam(name, param, onValue = true, offValue = false) {
    def value = param.param_value as int
    if (value > 1 || value < 0) {
        logger.error("parseIntBooleanParam: Unsupported param name=${name} value=${param.param_value}")
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
    def snoozeType = "null"
    def snoozeDurationSeconds = 0
    def snoozeStartEpochSeconds = 0
    if (param.param_value.size() > 0) { 
        def data = decodeBase64Json(param.param_value)
        logger.trace("parseSnooze data=${data}")
        def chime = data.chime_onoff == 1
        def motion = data.motion_on_off == 1
        if (chime) {
            if (motion) {
                snoozeType = SNOOZE_TYPE_CHIME_AND_MOTION
            } else {
                snoozeType = SNOOZE_TYPE_CHIME
            }
        } else {
            snoozeType = SNOOZE_TYPE_MOTION
        }
        snoozeDurationSeconds = data.snooze_time
        snoozeStartEpochSeconds = data.startTime
		// NOTE: Eufy normally clears it some other way, we clear it by just running a timer
		def secondsToWait = Math.max(1, (snoozeStartEpochSeconds + snoozeDurationSeconds) - ((now() / 1000) as int))
		runIn(secondsToWait, snoozeClear)
    } else {
		unschedule(snoozeClear)
	}
    sendEvent(name: "snoozeType", value: snoozeType, displayed: true)
    sendEvent(name: "snoozeDurationSeconds", value: snoozeDurationSeconds, displayed: true)
    sendEvent(name: "snoozeStartEpochSeconds", value: snoozeStartEpochSeconds, displayed: true)
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