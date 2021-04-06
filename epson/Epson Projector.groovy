/**
 * Epson Projector Device Handler
 *
 * Copyright 2021 Amos Yuen
 */

import groovy.transform.Field
import hubitat.helper.HexUtils

private def textVersion() {
	return "1.0.0 (2021-04-05)"
}

private def textCopyright() {
	return "Copyright ©2021\nAmos Yuen"
}

@Field final Map STATUS_MAP = [
	"off": "off",
    0: "standby (network off)",
    1: "on",
    2: "warmup",
    3: "cooldown",
    4: "standby (network on)",
    5: "abnormal standby",
    9: "A/V standby",
]
@Field final List STATUSES = STATUS_MAP.collect { it.value }

metadata {
	definition (name: "Epson Projector", namespace: "amosyuen", author: "Amos Yuen") {
		capability "Actuator"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
        
		attribute "status", "enum", STATUSES
		attribute "lampHours", "number"
		attribute "pollIntervalSeconds", "number"
		attribute "source", "string"

		command "setPollIntervalSeconds", [[
			name: "Seconds",
			type: "NUMBER",
			description: "Interval in seconds to poll the projector when it is off."
				+ " A value of 0 disables polling."
				+ " Values greater than 60 will be rounded to the nearest minute."]]
		command "setSource", [[
			name: "Source",
			type: "STRING"]]
	}
    
    preferences {
    	input("hubIP", "text", title: "Hub IP", required: true, displayDuringSetup: true)
        input("debugLogging", "bool", title: "Enable debug logging", required: true, defaultValue: false)
	    input("traceLogging", "bool", title: "Enable trace logging", required: true, defaultValue: false)
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
    unschedule()

	state.version = textVersion()
    state.copyright = textCopyright()
    
	if (device.currentSwitch == null) {
		offline()
	}

    setPollIntervalSeconds(device.currentPollIntervalSeconds == null ? 300 : device.currentPollIntervalSeconds)
}

//
// Actions
//

def on() {
    logMsg("debug", "on")
	sendCommand("PWR ON")
}

def off() {
    logMsg("debug", "off")
	sendCommand("PWR OFF")
}

def poll() {
	refresh()
}

def refreshIfOffline() {
	if (device.currentSwitch == "offline") {
		refresh()
	}
}

def refresh() {
    logMsg("debug", "refresh")
    if (state.refreshing) {
        return
    }
    state.refreshing = true
    runIn(1, refreshDelayed)
    runIn(30, refreshFinished)
}

def refreshDelayed() {    
    sendCommand("PWR?")
    // Other values will be refreshed sequentially
}

def refreshFinished() {
    unschedule(refreshFinished)
    state.refreshing = false
}

def setPollIntervalSeconds(seconds) {
    logMsg("debug", "setPollIntervalSeconds: seconds=${seconds}")
	unschedule(refreshIfOffline)
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
    			schedule("0 0 0 */${days} * ?", refreshIfOffline)
    		} else if (seconds >= 3600)  {
    			def hours = Math.round(seconds / 3600) as Integer
    			seconds = hours * 3600
    			schedule("0 0 */${hours} * * ?", refreshIfOffline)
    		} else if (seconds >= 60)  {
    			def minutes = Math.round(seconds / 60) as Integer
    			seconds = minutes * 60
    			schedule("0 */${minutes} * * * ?", refreshIfOffline)
    		} else {
    		  schedule("*/${seconds} * * * * ?", refreshIfOffline)
    		}
    	}
    }
	sendEvent(name: "pollIntervalSeconds", value: seconds)
}

def setSource(source) {
    logMsg("debug", "setSource: source=${source}")
    if (source.size() <= 0) {
        throw new Exception("Invalid source ${source}")
    }
    sendCommand("SOURCE ${source}")
}

//
// Helpers
//

def connect() {
	logMsg("debug", "connect")
    try {
        interfaces.rawSocket.connect(hubIP, 3629, byteInterface: true)
        // message format https://support.atlona.com/hc/en-us/articles/360048888054-IP-Control-of-Epson-Projectors
        sendMessage("${HexUtils.byteArrayToHexString("ESC/VP.net".bytes)}100300000000")
    } catch (Exception e) {
		offline()
        if (e instanceof java.net.NoRouteToHostException) {
            // Projector is off
            return
        }
        throw e
    }
}

def sendCommand(command) {
	try {
		if (device.currentSwitch == "offline") {
			state.lastCommand = command
			connect()
			return
		}
		sendCommandRaw(command)
	} catch (Exception e) {
        refreshFinished()
        throw e
    }
}

def sendCommandRaw(command) {
    // All commands should end with 0d (carriage return)
    def cmd = "${HexUtils.byteArrayToHexString(command.bytes)}0d"
	logMsg("debug", "sendCommand: cmd=${cmd}")
	sendMessage(cmd)
}

def sendMessage(msg) {
	interfaces.rawSocket.sendMessage(msg)
    runIn(10, messageTimeout)
}

def messageTimeout() {
    logMsg("debug", "messageTimeout")
    offline()
    refreshFinished()
}

def offline() {
    sendEvent(name: "switch", value: "offline")
	sendEvent(name: "status", value: "off")
}

def parse(String message) {
    logMsg("trace", "parse: hex=$message")
    def value = new String(HexUtils.hexStringToByteArray(message))
    logMsg("trace", "parse: value=$value")
    
	unschedule(messageTimeout)

	value.split(":").each {
		if (it.size() > 0) {
			parseResponse(it)
		}
	}
	
	if (value.endsWith(":")) {
        logMsg("debug", "parse: Confirmation of command")
		// Confirmation of set command.
		runIn(10, refresh)
	}
}

def parseResponse(response) {
	logMsg("trace", "parseResponse: response=${response}")

	//
	// Command Responses
	//
    if (response.startsWith("ESC/VP.net")) {
		log.info("response size ${response.size()}")
		if (response.size() >= 16) {
			// message format https://support.atlona.com/hc/en-us/articles/360048888054-IP-Control-of-Epson-Projectors
			def version = response.bytes[10] as int
			def type = response.bytes[11] as int
			def seqNo = (response.bytes[12] * 256 + response.bytes[13]) as int
			def status = response.bytes[14] as int
			def headerCount = response.bytes[15] as int
			logMsg("debug", "parseResponse: version=${version} type=${type} seqNo=${seqNo} status=${status} headerCount=${headerCount}")
			if (type != 3) {
				throw new Exception("Not connected. type=${type}")
			}
		}

		if (state.lastCommand) {
			def cmd = state.lastCommand
			state.remove("lastCommand")
			sendCommandRaw(cmd)
		} else {
			sendCommandRaw("PWR?")
		}
        return
    }
    
    if (response.startsWith("PWR=")) {
        def status = response[4..-2] as int
        if (updateStatus(status)) {
			if (state.refreshing) {
				sendCommandRaw("SOURCE?")
			}
		}
		return
    }
    
    if (response.startsWith("SOURCE=")) {
        def source = response[7..-2]
        logMsg("debug", "parseResponse: source=${source}")
        sendEvent(name: "source", value: source)
        
		if (state.refreshing) {
        	sendCommandRaw("LAMP?")
		}
		return
    }
    
    if (response.startsWith("LAMP=")) {
        def lampHours = response[5..-2] as int
        logMsg("debug", "parseResponse: lampHours=${on}")
        sendEvent(name: "lampHours", value: lampHours)
        
        refreshFinished()
		return
    }

	//
	// Events
	//
	if (response.startsWith("IMEVENT=")) {
		def eventCode = response[8..11] as int
		switch (eventCode) {
			case 1:
				if (!state.refreshing) {
					runIn(5, refresh)
				}
				return
		}
        logMsg("debug", "parseResponse: Unhandled IMEVENT eventCode=${eventCode} response=${response}")
		return
	}

	logMsg("debug", "parseResponse: Unhandled response=${response}")
}

def updateStatus(int status) {
	def on = status != 0 && status != 4
	logMsg("debug", "updateStatus: status=${STATUS_MAP[status]} value=${status} on=${on}")
	sendEvent(name: "status", value: STATUS_MAP[status])
	sendEvent(name: "switch", value: on ? "on" : "off")
	return on
}

def socketStatus(String status) {
    logMsg("info", "socketStatus: status=${status}")
	if (status.contains("disconnect")) {
		offline()
	}
}

def logMsg(level, message) {
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