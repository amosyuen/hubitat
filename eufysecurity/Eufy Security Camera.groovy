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
 *	0.0.0 (2020-02-10) [Amos Yuen] - Initial Release
 */

import groovy.json.JsonOutput
import groovy.transform.Field

private def textVersion() {
	return "Version: 0.0.0 - 2020-02-10"
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
				
		command "audioRecordingOn"
		command "audioRecordingOff"
		command "autoNightVisionOn"
		command "autoNightVisionOff"
		command "setDetectionSensitivity", [[
			name: "sensitivity",
			minimum: 0,
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
			minimum: 0,
			type: "NUMBER",
			description: "Interval in seconds to poll the mattress. A value of 0 disables polling. " +
				"Values greater than 60 will be rounded to the nearest minute."]]
		command "setPowerMode", [[
			name: "powerMode",
			type: "ENUM",
			constraints: POWER_MODES]]
		command "setRecordClipLength", [[
			name: "Seconds",
			minimum: 0,
			type: "NUMBER",
			description: "Max clip length to record after detecting motion."]]
		command "setRecordRetriggerInterval", [[
			name: "Seconds",
			minimum: 0,
			type: "NUMBER",
			description: "Interval after a clip is recorded before a new clip can be recorded."]]
	}

	preferences {
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
	
	sendEvent(name: "version", value: textVersion(), displayed: false)
	
	setPollIntervalSeconds(device.currentPollIntervalSeconds == null ? 300 : device.currentPollIntervalSeconds)
	refresh()
}

//
// Actions
//

def on() {
	logger.debug("on")
    setParam(PARAM_TYPE_OFF, 0)
}

def off() {
	logger.debug("off")
    setParam(PARAM_TYPE_OFF, 1)
}

def audioRecordingOn() {
	logger.debug("audioRecordingOn")
    setParam(PARAM_TYPE_AUDIO_RECORDING, 1)
}

def audioRecordingOff() {
	logger.debug("audioRecordingOff")
    setParam(PARAM_TYPE_AUDIO_RECORDING, 0)
}

def autoNightVisionOn() {
	logger.debug("autoNightVisionOn")
    setParam(PARAM_TYPE_AUTO_NIGHT_VISION, 1)
}

def autoNightVisionOff() {
	logger.debug("autoNightVisionOff")
    setParam(PARAM_TYPE_AUTO_NIGHT_VISION, 0)
}

def motionDetectionOn() {
	logger.debug("motionDetectionOn")
    setParam(PARAM_TYPE_MOTION_DETECTION, 1)
}

def motionDetectionOff() {
	logger.debug("motionDetectionOff")
    setParam(PARAM_TYPE_MOTION_DETECTION, 0)
}

def setDetectionSensitivity(value) {
	logger.debug("setDetectionSensitivity: value=${value}")
    setParam(PARAM_TYPE_DETECTION_SENSITIVITY, value)
}

def setDetectionType(value) {
	logger.debug("setDetectionType: value=${value}")
    value = DETECTION_TYPE_REVERSE[value]
    if (!value) {
        throw new Exception("Detection type ${value} is not supported!")
    }
    setParam(PARAM_TYPE_DETECTION_TYPE, value)
}

def setPollIntervalSeconds(seconds) {
    logger.debug("setPollIntervalSeconds: seconds=${seconds}")
	unschedule(poll)
	if (seconds > 0) {
		if (seconds >= 60)  {
			def minutes = Math.round(seconds / 60) as Integer
			seconds = minutes * 60
			schedule("0 */${minutes} * * * ?", poll)
		} else {
		  schedule("*/${seconds} * * * * ?", poll)
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
    setParam(PARAM_TYPE_POWER_MODE, value)
}

def setRecordClipLength(value) {
	logger.debug("setRecordClipLength: value=${value}")
    if (value < 0) {
        throw new Exception("Clip length ${value} is not supported!")
    }
    setParam(PARAM_TYPE_RECORD_CLIP_LENGTH, value)
}

def setRecordRetriggerInterval(value) {
	logger.debug("setRecordRetriggerInterval: value=${value}")
    if (value < 0) {
        throw new Exception("Retrigger interval ${value} is not supported!")
    }
    setParam(PARAM_TYPE_RECORD_RETRIGGER_INTERVAL, value)
}

def setParam(type, value) {
    logger.debug("setParam: type=${type}, value=${value}")
    
    def body = [
        device_sn: device.deviceNetworkId,
        station_sn: state.stationSN,
        params: [[param_type: type, param_value: value.toString()]]
    ]
    apiPOST("/app/upload_devs_params", body)
    refresh()
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
    state.stationSN = device.station_sn
    def params = device.params
    for (param in params) {
        refreshParam(param)
    }
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
    }
}

def parseBooleanParam(name, param, onValue = true, offValue = false) {
    def value = param.param_value as int
    if (value > 1 || value < 0) {
        logger.error("parseBooleanParam: Unsupported param name=${name} value=${param.param_value}")
    }
    sendEvent(name: name, value: value == 1 ? onValue : offValue, displayed: true)
}

def parseNonNegativeIntParam(name, param) {
    def value = param.param_value as int
    if (value < 0) {
        logger.error("parseNonNegativeInt: Unsupported param name=${name} value=${param.param_value}")
    }
    sendEvent(name: name, value: value, displayed: true)
}

def parseEnumParam(name, map, param) {
    def value = map[param.param_value]
    if (value == null) {
        logger.error("parseEnumParam: Unsupported param name=${name} value=${param.param_value}")
    }
    sendEvent(name: name, value: value, displayed: true)
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