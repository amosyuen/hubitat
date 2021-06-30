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
 *
 * 1.0.2 [Amos Yuen] Fix bug with queue not getting updated after window passes
 * 1.0.1 [Amos Yuen] Fix bug with push queue not getting cleared
 * 1.0.0 [Amos Yuen] Initial Version
 */

def appVersion() {
	return "1.0.2"
}

definition(
	name: "Motion In Window",
	namespace: "amosyuen",
	author: "Amos Yuen",
	description: "Creates a motion sensor that is active if there are X instances of motion in a window of Y seconds",
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
			input "motionSensors", "capability.motionSensor", title: "Motion Sensors", repeated: true, required: true
			input "numberOfMotionEvents", "number", title: "Number of motion events within window required to trigger active", min: 1, defaultValue: 2, required: true
			input "windowSeconds", "number", title: "Window in seconds", min: 1, required: true
		}
		section("<b>Debug</b>") {
			input "traceLog", "bool", title: "Log trace statements", defaultValue: false, submitOnChange: true
			input "debugLog", "bool", title: "Log debug statements", defaultValue: false, submitOnChange: true
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
	
	createChildIfNotExist()
	subscribe(motionSensors, "motion.active", motionSensorHandler)

	if (atomicState.eventsPush) {
		updateEvents()
	} else {
		atomicState.eventsPush = []
		atomicState.eventsPop = []
	}
}

def uninstalled() {
	log.info("uninstalled")
	unschedule()
	getChildDevices().each {
		deleteChildDevice(it.deviceNetworkId)
	}
}

/**
 * Events
 */

def updateEvents(newEventMillis = null) {
	def eventsPop = atomicState.eventsPop
	def eventsPush = atomicState.eventsPush
	def nowMillis = now()
	def windowMillis = windowSeconds * 1000
	if (newEventMillis) {
		eventsPush.add(newEventMillis)
	}
	for (def i = eventsPop.size() - 1; i >= 0; i--) {
		if (nowMillis - eventsPop[i] <= windowMillis) {
			break
		}
		eventsPop.remove(i)
	}
	if (eventsPop.size() == 0 && eventsPush.size() > 0) {
		for (def i = eventsPush.size() - 1; i >= 0; i--) {
			def millis = eventsPush[i]
			if (nowMillis - millis <= windowMillis) {
				eventsPop.add(millis)
                eventsPush.remove(i)
            }
		}
        eventsPush.clear()
	}
	atomicState.eventsPop = eventsPop
	atomicState.eventsPush = eventsPush
    logTrace("updateEvents: eventsPop=${eventsPop}")
    logTrace("updateEvents: eventsPush=${eventsPush}")

    int index = (eventsPop.size() + eventsPush.size() - numberOfMotionEvents) as int
    boolean active = index >= 0
    logDebug("updateEvents: active=${active}")
	String value = active ? "active" : "inactive"
	def child = getChildDevice(getChildId())
	child?.sendEvent(name: "motion", value: value, displayed: true)
    
    if (active) {
        def millis = index < eventsPop.size()
                ? eventsPop[eventsPop.size() - 1 - index]
                : eventsPush[index - eventsPop.size()]
        def waitMillis = millis + windowSeconds * 1000 - nowMillis
        runInMillis(waitMillis, updateEvents)
        logDebug("updateEvents: run update in ${waitMillis} millis")
    }
}

def motionSensorHandler(evt) {
	logDebug("motionSensorHandler: deviceId=${evt.getDevice()} ${evt.name}=${evt.value} @ ${evt.getUnixTime()}")
	updateEvents(evt.getUnixTime())
}

/**
 * Child
 */

def getChildId() {
	return "motion_in_window_${app.id}"
}

def createChildIfNotExist() {
	def id = getChildId()
    def child = getChildDevice(id)
    if (!child) {
        log.info("createChildIfNotExist: creating child device")
        addChildDevice("hubitat", "Generic Component Motion Sensor", id, /*hubId=*/ null, [name: id, label: app.label, completedSetup: true])
    }
}

def componentRefresh(componentDevice) {
	if (!componentDevice) { return }
	refresh()
}

/**
 * Helpers
 */

def logDebug(msg){
	if (debugLog == true) {
		log.debug msg
	}
}

def logTrace(msg){
	if (traceLog == true) {
		log.trace msg
	}
}
