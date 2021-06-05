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

def appVersion() {
	return "1.0.0"
}

definition(
	name: "Bed Presence Combined",
	namespace: "amosyuen",
	author: "Amos Yuen",
	description: "Creates a combined presence indicator from a presence and contact sensor",
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
		section("<b>Main</b>") {
			label title: "Assign a name"
		}
	  	section ("<b>Options</b>") {
			input "presenceSensor", "capability.presenceSensor", title: "Presence Sensor", required: true
			input "contactSensor", "capability.contactSensor", title: "Contact Sensor (closed = in bed)",
				required: true
			input "contactOpenDebounceSeconds", "number",
				title: "How many seconds to debounce contact opening",
				min: 0, defaultValue: 60, required: true
		}
		section("<b>Debug</b>") {
			input "debugLog", "bool", title: "Log debug statements",
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
	log.info("init")
	unschedule()
	
	if (atomicState.lastContactOpenMillis == null) {
		atomicState.lastContactOpenMillis = 0
	}

	createChildIfNotExist()
	subscribe(contactSensor, "contact", contactSensorHandler)
	subscribe(presenceSensor, "presence", presenceSensorHandler)

	def present = (
		contactSensor.currentContact == "closed"
		|| getWaitContactSensorOpenRemainingMillis() > 0)
	updateChildPresence(present)
	contactSensorOpen()
}

def uninstalled() {
	log.info("uninstalled")
	unschedule()
	getChildDevices().each {
		deleteChildDevice(it.deviceNetworkId)
	}
}

/**
 * Event Handlers
 */

def contactSensorHandler(evt) {
	logDebug("contactSensorHandler: deviceId=${evt.getDevice()} ${evt.name}=${evt.value}")
	if (evt.value == "closed") {
		updateChildPresence(true)
		unschedule(contactSensorOpen)
	} else {
		atomicState.lastContactOpenMillis = now()
		contactSensorOpen()
	}
}

def getWaitContactSensorOpenRemainingMillis() {
	return contactOpenDebounceSeconds * 1000 - (now() - atomicState.lastContactOpenMillis)
}

def contactSensorOpen() {
	logDebug("contactSensorOpen")
	if (contactSensor.currentContact == "closed") {
		return
	}
	def remainingMillis = getWaitContactSensorOpenRemainingMillis()
	logDebug("contactSensorOpen: remainingMillis=${remainingMillis}")
	if (remainingMillis <= 0) {
		updateChildPresence(false)
	} else {
		runInMillis(remainingMillis, contactSensorOpen)
	}
}

def presenceSensorHandler(evt) {
	logDebug("presenceSensorHandler: deviceId=${evt.getDevice()} ${evt.name}=${evt.value}")
	if (evt.value == "not present") {
		updateChildPresence(false)
	}
}

/**
 * Child
 */

def getChildId() {
	return "bed_presence_combined_${app.id}"
}

def createChildIfNotExist() {
	def id = getChildId()
    def child = getChildDevice(id)
    if (!child) {
        log.info("createChildIfNotExist: creating child device")
        addChildDevice("hubitat", "Virtual Presence", id, /*hubId=*/ null,
			[name: id, label: app.label, completedSetup: true])
    }
}

def updateChildPresence(present) {
	logDebug("updateChildPresence: present=${present}")
	def childPresence = getChildDevice(getChildId())
	if (childPresence) {
		def value = present ? "present" : "not present"
		childPresence.sendEvent(name: "presence", value: value, displayed: true)
	}
}

/**
 * Helpers
 */

def logDebug(msg){
	if (debugLog == true) {
		log.debug msg
	}
}
