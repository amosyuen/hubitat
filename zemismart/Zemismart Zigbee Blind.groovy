/**
 *  Copyright 2021
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 * This DTH is coded based on iquix's tuya-window-shade DTH and these other files:
 * https://github.com/iquix/Smartthings/blob/master/devicetypes/iquix/tuya-window-shade.src/tuya-window-shade.groovy
 * https://raw.githubusercontent.com/shin4299/XiaomiSJ/master/devicetypes/shinjjang/zemismart-zigbee-blind.src/zemismart-zigbee-blind.groovy
 * https://templates.blakadder.com/zemismart_YH002.html
 *
 * VERSION HISTORY
 * 2.0.0 (2021-03-09) [Amos Yuen] - Change tilt mode open()/close() commands to use set position
 *			to open/close all the way.
 *		- Rename pause() to stop()
 *		- Remove superfluous setDirection() setMode() functions
 * 1.0.0 (2021-03-09) [Amos Yuen] - Initial Commit
 */

import groovy.json.JsonOutput
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.helper.HexUtils

private def textVersion() {
	return "2.0.0 - 2021-03-09"
}

private def textCopyright() {
	return "Copyright ©2021\nAmos Yuen, iquix, ShinJjang"
}

metadata {
	definition(name: "ZemiSmart Zigbee Blind", namespace: "amosyuen", author: "Amos Yuen",
			ocfDeviceType: "oic.d.blind", vid: "generic-shade") {
		capability "Actuator"
        capability "Configuration"
		capability "Window Shade"

		attribute "speed", "integer"

		command "stop"
		command "setSpeed", [[
			name: "speed",
			type: "NUMBER",
			description: "Motor speed (0 to 100). Values below 5 may not work."]]

		fingerprint(endpointId: "01", profileId: "0104",
                    inClusters: "0000, 0003, 0004, 0005, 0006", outClusters: "0019",
                    manufacturer: "_TYST11_wmcdj3aq", model: "mcdj3aq",
                    deviceJoinName: "Zemismart Zigbee Blind")
        fingerprint(endpointId: "01", profileId: "0104",
                    inClusters: "0000, 0003, 0004, 0005, 0006", outClusters: "0019",
                    manufacturer: "_TYST11_cowvfr", model: "owvfni3",
                    deviceJoinName: "Zemismart Zigbee Blind")
		fingerprint(endpointId: "01", profileId: "0104",
                    inClusters: "0000, 0003, 0004, 0005, 0006", outClusters: "0019",
                    deviceJoinName: "Zemismart Zigbee Blind")
        // AM43-0.45/40-ES-EZ
		fingerprint(endpointId: "01", profileId: "0104",
                    inClusters: "0000, 0004, 0005, EF00", outClusters: "0019, 000A",
                    manufacturer: "_TZE200_zah67ekd", model: "TS0601",
                    deviceJoinName: "Zemismart Zigbee Blind Motor")
    }

	preferences {
		input("mode", "enum", title: "Mode",
            description: "<li><b>lift</b> - motor moves until button pressed again</li>"
                    + "<li><b>tilt</b> - pressing button < 1.5s, movement stops on release"
                    + "; pressing button > 1.5s, motor moves until button pressed again</li>",
			options: MODE_MAP, required: true, defaultValue: "1")
		input("direction", "enum", title: "Direction",
			options: DIRECTION_MAP, required: true, defaultValue: 0)
	    input("enableDebugLog", "bool", title: "Enable debug logging", required: true, defaultValue: false)
	    input("enableTraceLog", "bool", title: "Enable trace logging", required: true, defaultValue: false)
		input("enableUnexpectedMessageLog", "bool", title: "Log unexpected messages", required: true, defaultValue: false)   
    }
}

@Field final String MODE_TILT = "0"
@Field final Map MODE_MAP = [1: "lift", 0: "tilt"]
@Field final Map MODE_MAP_REVERSE = MODE_MAP.collectEntries { [(it.value): it.key] }
@Field final List MODES = MODE_MAP.collect { it.value }
@Field final Map DIRECTION_MAP = [0: "forward", 1: "reverse"]
@Field final Map DIRECTION_MAP_REVERSE = DIRECTION_MAP.collectEntries { [(it.value): it.key] }
@Field final List DIRECTIONS = DIRECTION_MAP.collect { it.value }

//
// Life Cycle
//

def installed() {
	configure()
}

def updated() {
	configure()
}

def configure() {
	logDebug("configure")
	state.version = textVersion()
    state.copyright = textCopyright()

    // Must run async otherwise, one will block the other
	runIn(1, setMode)
	runIn(2, setDirection)
}

def setDirection() {
    def directionValue = direction as int
    logDebug("setDirection: directionText=${DIRECTION_MAP[directionValue]}, directionValue=${directionValue}")
	sendTuyaCommand(DP_ID_DIRECTION, DP_TYPE_ENUM, directionValue, 2)
}

def setMode() {
    def modeValue = mode as int
    logDebug("setMode: modeText=${MODE_MAP[mode]}, modeValue=${modeValue}")
    sendTuyaCommand(DP_ID_MODE, DP_TYPE_ENUM, modeValue, 2)
}

//
// Messages
//

@Field final int CLUSTER_TUYA = 0xEF00
@Field final int SETDATA = 0x00

@Field final int DP_ID_COMMAND = 0x01
@Field final int DP_ID_TARGET_POSITION = 0x02
@Field final int DP_ID_CURRENT_POSITION = 0x03
@Field final int DP_ID_DIRECTION = 0x05
@Field final int DP_ID_COMMAND_REMOTE = 0x07
@Field final int DP_ID_MODE = 0x65
@Field final int DP_ID_SPEED = 0x69

@Field final int DP_TYPE_BOOL = 0x01
@Field final int DP_TYPE_VALUE = 0x02
@Field final int DP_TYPE_ENUM = 0x04

@Field final int DP_COMMAND_OPEN = 0x00
@Field final int DP_COMMAND_STOP = 0x01
@Field final int DP_COMMAND_CLOSE = 0x02
@Field final int DP_COMMAND_CONTINUE = 0x03

/*
 * Data (sending and receiving) generally have this format:
 * [2 bytes] (packet id)
 * [1 byte] (dp ID)
 * [1 byte] (dp type)
 * [2 bytes] (fnCmd length in bytes)
 * [variable bytes] (fnCmd)
 */
def parse(String description) {
	if (description == null || (!description.startsWith('catchall:') && !description.startsWith('read attr -'))) {
        logUnexpectedMessage("parse: Unhandled description=${description}")
		return
    }

	Map descMap = zigbee.parseDescriptionAsMap(description)
	if (!descMap?.data || descMap.data.size() < 7
			|| descMap.clusterInt != CLUSTER_TUYA
			|| (descMap.command != "01" && descMap.command != "02")) {
		logUnexpectedMessage("parse: Unhandled map=${descMap}")
		return
	}
	logTrace("parse: map=${descMap}")

	def data = descMap.data
	def dp = zigbee.convertHexToInt(data[2])
	def dataValue = zigbee.convertHexToInt(data[6..-1].join())
	switch (dp) {
		case DP_ID_COMMAND: // 0x01 Command
			switch (dataValue) {
                case DP_COMMAND_OPEN: // 0x00
				    logDebug("parse: opening")
				    updateWindowShadeOpening()
                    break
			    case DP_COMMAND_STOP: // 0x01
				    logDebug("parse: stopping")
                    break
			    case DP_COMMAND_CLOSE: // 0x02
				    logDebug("parse: closing")
				    updateWindowShadeClosing()
                    break
			    case DP_COMMAND_CONTINUE: // 0x03
				    logDebug("parse: continuing")
                    break
			    default:
				    logUnexpectedMessage("parse: Unexpected DP_ID_COMMAND dataValue=${dataValue}")
                    break
			}
			break
		
		case DP_ID_TARGET_POSITION: // 0x02 Target position
			if (dataValue >= 0 && dataValue <= 100) {
				logDebug("parse: moving to position ${dataValue}")
				updateWindowShadeMoving(dataValue)
                updatePosition(dataValue)
			} else {
				logUnexpectedMessage("parse: Unexpected DP_ID_TARGET_POSITION dataValue=${dataValue}")
			}
			break
		
		case DP_ID_CURRENT_POSITION: // 0x03 Current Position
			if (dataValue >= 0 && dataValue <= 100) {
				logDebug("parse: arrived at position ${dataValue}")
				updateWindowShadeArrived(dataValue)
                updatePosition(dataValue)
			} else {
				logUnexpectedMessage("parse: Unexpected DP_ID_CURRENT_POSITION dataValue=${dataValue}")
			}
			break
		
		case DP_ID_DIRECTION: // 0x05 Direction
            def directionText = DIRECTION_MAP[dataValue]
			if (directionText != null) {
                logDebug("parse: direction=${directionText}")
				updateDirection(dataValue)
			} else {
				logUnexpectedMessage("parse: Unexpected DP_ID_DIRECTION dataValue=${dataValue}")
			}
			break
		
		case DP_ID_COMMAND_REMOTE: // 0x07 Remote Command
			if (dataValue == 0) {
				logDebug("parse: opening from remote")
				updateWindowShadeOpening()
			} else if (dataValue == 1) {
				logDebug("parse: closing from remote")
				updateWindowShadeClosing()
			} else {
				logUnexpectedMessage("parse: Unexpected DP_ID_COMMAND_REMOTE dataValue=${dataValue}")
			}
			break
		
		case DP_ID_MODE: // 0x65 Mode
            def modeText = MODE_MAP[dataValue]
			if (modeText != null) {
                logDebug("parse: mode=${modeText}")
				updateMode(dataValue)
			} else {
				logUnexpectedMessage("parse: Unexpected DP_ID_MODE dataValue=${dataValue}")
			}
			break
		
		case DP_ID_SPEED: // 0x69 Motor speed
			if (dataValue >= 0 && dataValue <= 100) {
                logDebug("parse: speed=${dataValue}")
				updateSpeed(dataValue)
			} else {
				logUnexpectedMessage("parse: Unexpected DP_ID_SPEED dataValue=${dataValue}")
			}
			break
		
		default:
			logUnexpectedMessage("parse: Unknown DP_ID dp=0x${data[2]}, dataType=0x${data[3]} dataValue=${dataValue}")
			break
	}
}

private ignorePositionReport(position) {
	def lastPosition = device.currentPosition
	logDebug("ignorePositionReport: position=${position}, lastPosition=${lastPosition}")
	if (lastPosition == "undefined" || isWithinOne(position)) {
		logTrace("Ignore invalid reports")
		return true
	}
	return false
}

private isWithinOne(position) {
	def lastPosition = device.currentPosition
	logTrace("isWithinOne: position=${position}, lastPosition=${lastPosition}")
	if (lastPosition != "undefined" && Math.abs(position - lastPosition) <= 1) {
		return true
	}
	return false
}

private updateDirection(directionValue) {
	def directionText = DIRECTION_MAP[directionValue]
	logDebug("updateDirection: directionText=${directionText}, directionValue=${directionValue}")
    if (directionValue != (direction as int)) {
		setDirection()
	}
}

private updateMode(modeValue) {
	def modeText = MODE_MAP[modeValue]
	logDebug("updateMode: modeText=${modeText}, modeValue=${modeValue}")
    if (modeValue != (mode as int)) {
		setMode()
	}
}

private updatePosition(position) {
	logDebug("updatePosition: position=${position}")
    if (isWithinOne(position)) {
        return
    }
    sendEvent(name: "position", value: position)
}

private updateSpeed(speed) {
	logDebug("updateSpeed: speed=${speed}")
    sendEvent(name: "speed", value: speed)
}

private updateWindowShadeMoving(position) {
	def lastPosition = device.currentPosition
	logDebug("updateWindowShadeMoving: position=${position}, lastPosition=${lastPosition}")

	if (lastPosition < position) {
		updateWindowShadeOpening()
	} else if (lastPosition > position) {
		updateWindowShadeClosing()
	}
}

private updateWindowShadeOpening() {
	logTrace("updateWindowShadeOpening")
    sendEvent(name:"windowShade", value: "opening")
}

private updateWindowShadeClosing() {
	logTrace("updateWindowShadeClosing")
    sendEvent(name:"windowShade", value: "closing")
}

private updateWindowShadeArrived(position) {
	logDebug("updateWindowShadeArrived: position=${position}")
	if (position < 0 || position > 100) {
		log.warn("updateWindowShadeArrived: Need to setup limits on device")
    	sendEvent(name: "windowShade", value: "unknown")
	} else if (position <= 1) { // Sometimes off by one
    	sendEvent(name: "windowShade", value: "closed")
    } else if (position >= 99) { // Sometimes off by one
    	sendEvent(name: "windowShade", value: "open")
    } else {
		sendEvent(name: "windowShade", value: "partially open")
    }
}

//
// Actions
//

def close() {
	logDebug("close")
    sendEvent(name: "position", value: 0)
    if (mode == MODE_TILT) {
        setPosition(0)
    } else {
	    sendTuyaCommand(DP_ID_COMMAND, DP_TYPE_ENUM, DP_COMMAND_CLOSE, 2)
    }
}

def open() {
	logDebug("open")
    sendEvent(name: "position", value: 100)
    if (mode == MODE_TILT) {
        setPosition(100)
    } else {
	    sendTuyaCommand(DP_ID_COMMAND, DP_TYPE_ENUM, DP_COMMAND_OPEN, 2)
    }
}

def stop() {
	logDebug("stop")
	sendTuyaCommand(DP_ID_COMMAND, DP_TYPE_ENUM, DP_COMMAND_STOP, 2)
}

def setPosition(position) {
    logDebug("setPosition: position=${position}")
    if (position < 0 || position > 100) {
        throw new Exception("Invalid position ${position}. Position must be between 0 and 100 inclusive.")
    }
    if (isWithinOne(position)) {
        // Motor is off by one sometimes, so set it to desired value if within one
        sendEvent(name: "position", value: position)
    }
    sendTuyaCommand(DP_ID_TARGET_POSITION, DP_TYPE_VALUE, position.intValue(), 8)
}

def setSpeed(speed) {
    logDebug("setSpeed: speed=${speed}")
    if (speed < 0 || speed > 100) {
        throw new Exception("Invalid speed ${speed}. Speed must be between 0 and 100 inclusive.")
    }
    sendTuyaCommand(DP_ID_SPEED, DP_TYPE_ENUM, speed.intValue(), 8)
}

//
// Helpers
//

private sendTuyaCommand(int dp, int dpType, int fnCmd, int fnCmdLength) {
	def dpHex = zigbee.convertToHexString(dp, 2)
	def dpTypeHex = zigbee.convertToHexString(dpType, 2)
	def fnCmdHex = zigbee.convertToHexString(fnCmd, fnCmdLength)
    logTrace("sendTuyaCommand: dp=0x${dpHex}, dpType=0x${dpTypeHex}, fnCmd=0x${fnCmdHex}, fnCmdLength=${fnCmdLength}")
    def message = (randomPacketId().toString()
                   + dpHex
                   + dpTypeHex
                   + zigbee.convertToHexString((fnCmdLength / 2) as int, 4)
                   + fnCmdHex)
	logTrace("sendTuyaCommand: message=${message}")
	zigbee.command(CLUSTER_TUYA, SETDATA, message)
}

private randomPacketId() {
    zigbee.convertToHexString(new Random().nextInt(65536), 4)
}

private logDebug(text) {
    if (!enableDebugLog) {
        return
    }
    log.debug(text)
}

private logTrace(text) {
    if (!enableTraceLog) {
        return
    }
    log.trace(text)
}

private logUnexpectedMessage(text) {
    if (!enableUnexpectedMessageLog) {
        return
    }
    log.warn(text)
}
