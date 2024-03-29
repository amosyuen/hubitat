/**
 *  Copyright 2021 Amos Yuen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language ginning permissions and limitations under the License.
 */

import groovy.transform.Field;


def appVersion() {
	return "1.0.0"
}

definition(
	name: "Device Monitor",
	namespace: "amosyuen",
	author: "Amos Yuen",
	description: "Notify abnormal device behavior",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
)

preferences {
	 page (name: "mainPage")
} 

def mainPage() {
	dynamicPage(name: "mainPage", install: true, uninstall: true) {
	  	section ("<b>Devices</b>") {
			input "batteryDevices", "capability.battery", title: "Devices with a battery",
				multiple: true, required: false, submitOnChange: true
			input "presenceDevices", "capability.presenceSensor", title: "Devices whose presence marks device status",
				multiple: true, required: false, submitOnChange: true
			input "lastActivityDevices", "capability.actuator", title: "Devices with a \"lastActivity\" date attribute",
				multiple: true, required: false, submitOnChange: true
		}
		section (title: "<b>Check Options</b>") {
			input "checkIntervalSeconds", "number",
				title: "Seconds between each device check",
				min: 0, defaultValue: 3600, required: true
			input "waitAfterRefreshSeconds", "number",
				title: "How many seconds to wait after a refresh before checking the devices",
				min: 0, defaultValue: 60, required: true
				
			if (batteryDevices) {
				input "batteryThreshold", "number",
					title: "Notify when battery below threshold",
					min: 0, max: 100, defaultValue: 5, required: true
			}
			if (lastActivityDevices) {
				input "lastActivityNotifyThresholdSeconds", "number",
					title: "How many seconds after last activity before notifying",
					min: 0, defaultValue: 3600, required: true
			}
			
			input "checkNow", "button", title: "Check Now"
		}
		section (title: "<b>Notification Methods</b>") {
			input("pushNotificationDevices", "capability.notification", title: "Push Notification Devices",
                multiple: true, required: false, submitOnChange: true)
		}
		section("<b>Debug</b>") {
			input "traceLogging", "bool", title: "Log trace statements",
				defaultValue: false, submitOnChange: true
			input "debugLogging", "bool", title: "Log debug statements",
				defaultValue: false, submitOnChange: true
		} 
		section {
			paragraph "v${appVersion()}"
		}
	}
}

/**
 * Life Cycle
 */

def installed() {
	init()
}

def updated() {
	init()
}

def init() {
	logMsg("info", "init")
	unschedule()
	unsubscribe()

	lastActivityDevices.each {
		if (!it.hasAttribute('lastActivity')) {
			throw new Exception("Device ${it} does not have lastActivity property")
		}
		def date = new Date(it.currentLastActivity)
		if (!date) {
			throw new Exception("Device ${it} does not have date parsable lastActivity property")
		}
	}
	subscribe(location, "systemStart", checkAllDevices)

    scheduleCheck()
}

def scheduleCheck() {
	if (checkIntervalSeconds < 0) {
		throw new Exception("Check interval seconds ${checkIntervalSeconds} must be greater than or equal to 0")
	}
	if (checkIntervalSeconds > 2419200)  {
		throw new Exception("Check interval seconds ${checkIntervalSeconds} must be less than or equal to 2419200")
	} 
	if (checkIntervalSeconds > 0) {
		if (checkIntervalSeconds >= 86400)  {
			def days = Math.round(checkIntervalSeconds / 86400) as Integer
			checkIntervalSeconds = days * 86400
			schedule("0 0 0 */${days} * ?", checkAllDevices)
		} else if (checkIntervalSeconds >= 3600)  {
			def hours = Math.round(checkIntervalSeconds / 3600) as Integer
			checkIntervalSeconds = hours * 3600
			schedule("0 0 */${hours} * * ?", checkAllDevices)
		} else if (checkIntervalSeconds >= 60)  {
			def minutes = Math.round(checkIntervalSeconds / 60) as Integer
			checkIntervalSeconds = minutes * 60
			schedule("0 */${minutes} * * * ?", checkAllDevices)
		} else {
			schedule("*/${checkIntervalSeconds} * * * * ?", checkAllDevices)
		}
	}
}

def uninstalled() {
	logMsg("info", "uninstalled")
	unschedule()
	unsubscribe()
}

def appButtonHandler(buttonPressed) {
    switch (buttonPressed) {
        case "checkNow":
            checkAllDevices()
            break
    }
}

/**
 * Logic
 */

def checkAllDevices() {
	logMsg("info", "checkAllDevices")

	def devicesToRefreshMap = [:]
	addBatteryDevicesToCheck(devicesToRefreshMap)
	addLastActivityDevicesToCheck(devicesToRefreshMap)
	addPresenceDevicesToCheck(devicesToRefreshMap)
	refreshDevices(devicesToRefreshMap.values())

	// Run after delay to give time for refresh to process
	runIn(waitAfterRefreshSeconds, runCheckAllDevicesAfterRefresh)
}

def refreshDevices(devices) {
	logMsg("trace", "refreshDevices: devices=${devices}}")
	for (device in devices) {
		if (device.hasCommand('refresh')) {
			device.refresh()
		}
	}
}

def runCheckAllDevicesAfterRefresh() {
	logMsg("trace", "runCheckAllDevicesAfterRefresh")
	checkBatteryDevices()
	checkLastActivityDevices()
	checkPresenceDevices()
}

/**
 * Battery Devices
 */

def addBatteryDevicesToCheck(devicesToRefreshMap) {
	logMsg("trace", "addBatteryDevicesToCheck")
	batteryDevices.each {
		if (it.currentBattery <= batteryThreshold + 5) {
			devicesToRefreshMap[it.deviceNetworkId] = it
		}
	}
	return devices
}

def checkBatteryDevices() {
	logMsg("trace", "checkBatteryDevices")
	def lowBatteryDevices = []
	batteryDevices.each {
		if (it.currentBattery <= batteryThreshold) {
			lowBatteryDevices.add(it)
		}
	}

	if (lowBatteryDevices.size() > 0) {
		logMsg("debug", "checkBatteryDevices: lowBatteryDevices=${lowBatteryDevices}")
		sendNotification("Low battery devices: ${getDeviceNames(lowBatteryDevices)}")
	}
}

/**
 * Last Activity Devices
 */

def addLastActivityDevicesToCheck(devicesToRefreshMap) {
	logMsg("trace", "addLastActivityDevicesToCheck")
	def nowMillis = now()
	def refreshThresholdMillis = nowMillis
		- (lastActivityNotifyThresholdSeconds + waitAfterRefreshSeconds + 120) * 1000
	lastActivityDevices.each {
		def lastActivityMillis = addLastActivity(it)
		if (lastActivityMillis <= refreshThresholdMillis) {
			devicesToRefreshMap[it.deviceNetworkId] = it
		}
	}
}

def checkLastActivityDevices() {
	logMsg("trace", "checkLastActivityDevices")
	def nowMillis = now()
	def exceedThresholdMillis = nowMillis - lastActivityNotifyThresholdSeconds * 1000
	def exceededActivityDevices = []
	lastActivityDevices.each {
		def lastActivityMillis = addLastActivity(it)
		if (lastActivityMillis <= exceedThresholdMillis) {
			exceededActivityDevices.add(it)
		}
	}

	if (exceededActivityDevices.size() > 0) {
		logMsg("debug", "checkLastActivityDevice: exceededActivityDevices=${exceededActivityDevices}")
		sendNotification("Exceeded last activity threshold devices: ${getDeviceNames(exceededActivityDevices)}")
	}
}

def addLastActivity(device) {
	return new Date(device.currentLastActivity).getTime()
}

/**
 * Presence Devices
 */

def addPresenceDevicesToCheck(devicesToRefreshMap) {
	logMsg("trace", "addPresenceDevicesToCheck")
	presenceDevices.each {
		if (it.currentPresence == "not present") {
			devicesToRefreshMap[it.deviceNetworkId] = it
		}
	}
}

def checkPresenceDevices() {
	logMsg("trace", "checkPresenceDevices")
	def notPresentDevices = []
	presenceDevices.each {
		if (it.currentPresence == "not present") {
			notPresentDevices.add(it)
		}
	}

	if (notPresentDevices.size() > 0) {
		logMsg("debug", "checkPresenceDevices: notPresentDevices=${notPresentDevices}")
		sendNotification("Not present devices: ${getDeviceNames(notPresentDevices)}")
	}
}

/**
 * Helpers
 */

def getDeviceNames(devices) {
	logMsg("trace", "getDeviceNames: devices=${devices}")
	def deviceNames = []
	devices.each {
		deviceNames.add("${it}")
	}
	deviceNames.sort()
	return deviceNames.join(", ")
}

def sendNotification(msg) {
	logMsg("debug", "sendNotification: msg=${msg}")
	if (pushNotificationDevices) {
		pushNotificationDevices*.deviceNotification(msg)
	}
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
