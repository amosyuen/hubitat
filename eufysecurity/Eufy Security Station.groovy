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
 *	0.0.7 (2020-03-31) [Amos Yuen] Fix logging method passing bug
 *	0.0.6 (2020-03-26) [Amos Yuen] Fix logging issues in closures
 *	0.0.5 (2020-03-09) [Amos Yuen] Decode all base64 params for log param changes
 *	0.0.2 (2020-02-16) [Amos Yuen] Add setting to log param changes for debugging
 *		- Support hourly and daily poll intervals up to 28 days
*		- Validate command params properly
 *	0.0.1 (2020-02-15) [Amos Yuen] - Update helper to support setting multiple params
 *	0.0.0 (2020-02-10) [Amos Yuen] - Initial Release
 */

import groovy.json.JsonOutput
import groovy.transform.Field

private def textVersion() {
	return "Version: 0.0.7 - 2020-03-31"
}

private def textCopyright() {
	return "Copyright © 2021 Amos Yuen"
}

@Field static final int PARAM_TYPE_MODE = 1224

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

metadata {
	definition (name: "Eufy Security Station", namespace: "amosyuen", author: "Amos Yuen") {
		capability "Actuator"
		capability "Presence Sensor"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
				
		attribute "mode", "enum", MODES
		attribute "pollIntervalSeconds", "number"
				
		command "setMode", [[
			name: "mode",
			type: "ENUM",
			constraints: MODES]]
		command "setPollIntervalSeconds", [[
			name: "Seconds",
			type: "NUMBER",
			description: "Interval in seconds to poll the mattress. A value of 0 disables polling. " +
				"Values greater than 60 will be rounded to the nearest minute."]]
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

def setMode(mode) {
	logMsg("debug", "setMode: mode=${mode}")
    def modeValue = MODE_REVERSE[mode]
    if (!modeValue) {
        throw new Exception("Mode ${mode} is not supported!")
    }
    setHubParams([(PARAM_TYPE_MODE): modeValue])
    refresh()
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

def setHubParams(paramsMap) {
    logMsg("debug", "setHubParams: paramsMap=${paramsMap}")
    
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
	logMsg("info", "refresh")
    refreshHubParams()
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

def parseEnumParam(name, map, param) {
    def value = map[param.param_value]
    if (value == null) {
        logMsg("error", "parseEnumParam: Unsupported param name=${name} value=${param.param_value}")
        value = "null"
    }
    sendEvent(name: name, value: value, displayed: true)
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
            case 1158:
            case 1159:
            case 1160:
            case 1204:
            case 1254:
            case 1256:
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