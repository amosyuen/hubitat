/***********************************************************************************************************************
*
*	A smartapp to manage rooms.
*
*	Copyright (C) 2020 amosyuen
*
*	License:
*	This program is free software: you can redistribute it and/or modify it under the terms of the GNU
*	General Public License as published by the Free Software Foundation, either version 3 of the License, or
*	(at your option) any later version.
*
*	This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
*	implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
*	for more details.
*
*	You should have received a copy of the GNU General Public License along with this program.
*	If not, see <http://www.gnu.org/licenses/>.
*
*	Name: Room
*
***********************************************************************************************************************/

import groovy.transform.Field

@Field final String ICONS_URL = "https://cdn.rawgit.com/adey/bangali/master/resources/icons/"
@Field final Integer IMG_SIZE = 36
// Ignore any light changes within this millis right after an occupancy transition 
@Field final Integer OCCUPANCY_TRANSITION_IGNORE_MILLIS = 1000
// Any changes after this many millis are automatically user changes
@Field final Integer OCCUPANCY_TRANSITION_MAX_MILLIS = 15000
@Field final Integer LIGHT_CHANGE_DELAY_SECONDS = 1
@Field final Integer MIN_RETRIGGER_INTERVAL_MILLIS = 500
@Field final Integer NIGHT_LIGHT_DELAY_SECONDS = 1
@Field final Float KEEP_LIGHTS_OFF_SECONDS = 20
@Field final Map OCCUPANCY_ENUM_VALUE = [
    vacant: 0,
    checking: 1,
    occupied: 2,
    engaged: 3
]

def appVersion() {
	return "1.0.0"
}

definition(
	name: "Room",
	namespace: "amosyuen",
	parent: "amosyuen:Room Manager",
	author: "Amos Yuen",
	description: "DO NOT INSTALL DIRECTLY. Create instances using room manager parent app.",
	category: "Convenience",
	iconUrl: "https://cdn.rawgit.com/adey/bangali/master/resources/icons/roomOccupancy.png",
	iconX2Url: "https://cdn.rawgit.com/adey/bangali/master/resources/icons/roomOccupancy@2x.png",
	iconX3Url: "https://cdn.rawgit.com/adey/bangali/master/resources/icons/roomOccupancy@3x.png")

//
// Preferences
//

preferences {
	page(name: "pageMain")
	page(name: "pageDevices")
	page(name: "pageOccupied")
	page(name: "pageEngaged")
	page(name: "pageChecking")
	page(name: "pageVacant")
	page(name: "pageNightLight")
}

def pageMain() {
	dynamicPage(name: "pageMain", title: "Room Settings", install: true, uninstall: true) {
		section("Main") {
			label title: "Assign a name", required: true
		}
		section("")	{
			href "pageDevices", title:"${addImage("roomsOtherDevices.png")}Room Devices", description: "Configure Room Devices"
		}
		section("")	{
			href "pageOccupied", title:"${addImage("roomsOccupied.png")}Occupied Settings", description: "Configure Occupied Settings"
		}
		section("")	{
			href "pageEngaged", title:"${addImage("roomsEngaged.png")}Engaged Settings", description: "Configure Engaged Settings"
		}
		section("")	{
			href "pageChecking", title:"${addImage("roomsChecking.png")}Checking Settings", description: "Configure Checking Settings"
		}
		section("")	{
			href "pageVacant", title:"${addImage("roomsVacant.png")}Vacant Settings", description: "Configure Vacant Settings"
		}
		section("")	{
			href "pageNightLight", title:"${addImage("roomsAsleep.png")}Night Light Settings", description: "Configure Night Light Settings"
		}
		section("Debug") {
			input "pause", "bool", title: "Pause app", defaultValue: false, submitOnChange: true
			input "debugLog", "bool", title: "Log debug statements", defaultValue: false, submitOnChange: true
		} 
	}
}

def pageDevices() {
	dynamicPage(name: "pageDevices", title: "Room Devices Settings", install: false, uninstall: false) {
		section("Contact Sensors") {
			input "contactSensors", "capability.contactSensor", title: "Contact sensors keep room occupied", multiple: true, required: false
			input "contactSensorsOpen", "capability.contactSensor", title: "Contact sensors make room occupied on open", multiple: true, required: false
		}
		section("Motion Sensors") {
			input "motionSensors", "capability.motionSensor", title: "Motion sensors", multiple: true, required: false
			if (motionSensors) {
				input "preventStateChangeIfNoMovementAtExits", "bool", title: "Prevent state change until a contact is opened or motion at exits is detected", defaultValue: false, submitOnChange: true
				if (preventStateChangeIfNoMovementAtExits) {
					input "exitMotionSensors", "capability.motionSensor", title: "Motion sensors at exits", multiple: true, required: false
					input "nobodyHasExitedTimeoutSeconds", "number", title: "Timeout in seconds", defaultValue: 43200, range: "1..*"
				}
			}
		}
		section("Light Sensors") {
			input "lightSensors", "capability.illuminanceMeasurement", title: "Light sensors", required: false, multiple: true, submitOnChange: true
		}
	}
}

def pageOccupied() {
	dynamicPage(name: "pageOccupied", title: "Room Occupied Settings", install: false, uninstall: false) {
		section("Additional Triggers") {
			paragraph "Already triggers on motion, contact being opened, light being turned on, or dimmer level changed"
			input "occupiedTriggerSwitchesOn", "capability.switch", title: "Switches that trigger occupied state if turned on", multiple: true, required: false
			input "occupiedTriggerSwitchesOff", "capability.switch", title: "Switches that trigger occupied state if turned off", multiple: true, required: false
		}
		section("Turn On Lights") {
			paragraph "Lights will be only turned on if no lights are already on"
			input "occupiedLightSwitches", "capability.switch", title: "Switches to turn on", multiple: true, required: false, submitOnChange: true
            if (occupiedLightSwitches?.any{ s -> s.hasCommand('setLevel') }) {
				input "occupiedLightLevel", "number", title: "Dimmer level", range: "1..100", defaultValue: 25, required: false
			    input "explicitlyTurnOnDimmers", "bool", title: "Explicitly turn on dimmers", defaultValue: true, required: false
			}
            if (occupiedLightSwitches?.any{ s -> s.hasCommand('setColorTemperature') }) {
				input "occupiedLightTemperature", "number", title: "Color temperature", range: "1..30000", defaultValue: 2700, required: false
			}
		}
		section ("Turn On Lights Conditions") {		 
			input "occupiedModes", "mode", title: "Modes", multiple: true, required: false
			if (lightSensors) {
				input "occupiedLuxThresholdOn", "number", title: "Light Turn On Lux threshold", required: false, range: "1..*", defaultValue: 2000
				input "occupiedLuxThresholdOff", "number", title: "Light Turn Off Lux threshold", required: false, range: "1..*", defaultValue: 2250
			}
		}
		section("Cancel Triggers") {
			paragraph "Changes state to checking if state is occupied and switch changes"
			input "occupiedTriggerCancelSwitchesOn", "capability.switch", title: "Switches that trigger checking state if turned on", multiple: true, required: false
			input "occupiedTriggerCancelSwitchesOff", "capability.switch", title: "Switches that trigger checking state if turned off", multiple: true, required: false
		}
		section("Timeout")	{
			paragraph "Changes state to checking if there is no activity in the timeout period"
			input "occupiedTimeoutSeconds", "number", title: "Timeout in seconds", defaultValue: 45, range: "1..*"
		}
	}
}

def pageEngaged() {
	dynamicPage(name: "pageEngaged", title: "Room Engaged Settings", install: false, uninstall: false) {
		section("Additional Triggers") {
			input "engageFromUserLightChanges", "bool", title: "Engage room on user light changes", default: false
			input "engagedTriggerSwitchesOn", "capability.switch", title: "Switches that trigger engaged state if turned on", multiple: true, required: false
			input "engagedTriggerSwitchesOff", "capability.switch", title: "Switches that trigger engaged state if turned off", multiple: true, required: false
		}
		section("Actions") {
			input "engagedSwitches", "capability.switch", title: "Additional switches to turn on besides occupied switches", multiple: true, required: false, submitOnChange: true
            if (occupiedLightSwitches?.any{ s -> s.hasCommand('setLevel') } || engagedSwitches?.any{ s -> s.hasCommand('setLevel') }) {
				input "engagedLightLevel", "number", title: "Dimmer level", range: "1..100", defaultValue: 100, required: false
			}
            if (occupiedLightSwitches?.any{ s -> s.hasCommand('setColorTemperature') } || engagedSwitches?.any{ s -> s.hasCommand('setColorTemperature') }) {
				input "engagedLightTemperature", "number", title: "Color temperature", range: "1..30000", defaultValue: 2700, required: false
			}
			if (occupiedLightSwitches || engagedSwitches) {
				input "engagedScenes", "capability.switch", title: "Do not change lights if any of these scenes are on", multiple: true, required: false
			}
		}
		section("Cancel Triggers") {
			paragraph "Changes state to occupied if state is engaged and switch changes"
			input "engagedTriggerCancelSwitchesOn", "capability.switch", title: "Switches that trigger occupied state if turned on", multiple: true, required: false
			input "engagedTriggerCancelSwitchesAllOff", "capability.switch", title: "Switches that trigger occupied state if all turned off", multiple: true, required: false
			input "engagedTriggerCancelSwitchesAnyOff", "capability.switch", title: "Switches that trigger occupied state if any turned off", multiple: true, required: false
			input "engagedTriggerCancelContactsAllClosed", "capability.contactSensor", title: "Contacts that trigger occupied state if all closed", multiple: true, required: false
		}
		section("Timeout")	{
			paragraph "Changes state to checking if there is no activity in the timeout period"
			input "engagedTimeoutSeconds", "number", title: "Timeout in seconds", defaultValue: 4800, range: "1..*"
            input "engagedTimeoutPreventSwitches", "capability.switch", title: "Prevent timeout if any switch is on", multiple: true, required: false
		}
	}
}

def pageChecking() {
	dynamicPage(name: "pageChecking", title: "Room Checking Settings", install: false, uninstall: false) {
		section("Actions") {
			if (occupiedDimmersOn) {
				input "checkingLightLevel", "number", title: "Dimmer Level", range: "0..100", defaultValue: 10, required: true
			}
		}
		section("Timeout")	{
			paragraph "Changes state to vacant if there is no activity in the timeout period"
			input "checkingTimeoutSeconds", "number", title: "Timeout in seconds", defaultValue: 15, range: "1..*"
		}
	}
}

def pageVacant() {
	dynamicPage(name: "pageVacant", title: "Room Vacant Settings", install: false, uninstall: false) {
		section("Triggers") {
			input "vacantModes", "mode", title: "Modes", multiple: true, required: false, defaultValue: ["Away"]
		}
		section("Actions") {
			paragraph "Already turns off lights turned on in occupied"
			input "vacantSwitchesOn", "capability.switch", title: "Switches to turn on", multiple: true, required: false
			input "vacantSwitchesOff", "capability.switch", title: "Switches to turn off", multiple: true, required: false
		}
	}
}

def pageNightLight() {
	dynamicPage(name: "pageNightLight", title: "Room Night Light Settings", install: false, uninstall: false) {
		section("Turn On Lights") {
			input "nightLightSwitches", "capability.switch", title: "Switches to turn on", multiple: true, required: false, submitOnChange: true
			if (nightLightSwitches?.any{ s -> s.hasCommand('setLevel') }) {
				input "nightLightLevel", "number", title: "Dimmer level", range: "1..100", defaultValue: 5, required: true
			}
		}
		section ("Turn On Light Conditions") {		 
			input "nightModes", "mode", title: "Modes", multiple: true, required: false
		}
	}
}

//
// Lifecycle
//

def installed() {
	init()
}

def updated() {
	init()
}

def init() {
	logInfo("init")
	unsubscribe()
	unschedule()
	
	if (!atomicState.appLightChangeTime) {
		atomicState.appLightChangeTime = 0
	}
	if (!atomicState.lastTriggerTime) {
		atomicState.lastTriggerTime = 0
	}
	if (atomicState.nobodyHasExited == null) {
		atomicState.nobodyHasExited = false
	}
	if (!atomicState.lastLightOffTime) {
		atomicState.lastLightOffTime = 0
	}
	if (!atomicState.targetLevel) {
		atomicState.targetLevel = 0
	}
	if (!atomicState.previousLevelMap) {
		atomicState.previousLevelMap = [:]
	}
	if (lightSensors) {
		if (atomicState.inLuxRange) {
			atomicState.inLuxRange = getAverageLux() <= occupiedLuxThresholdOff
		} else {
			atomicState.inLuxRange = getAverageLux() <= occupiedLuxThresholdOn
		}
	} else {
		atomicState.inLuxRange = true
	}
	
	createChildDeviceIfNotExist()
	if (!atomicState.occupancy) {
		updateOccupancy("vacant")
	}
	
	updateChildContact()
	updateChildMotion()
	updateChildNobodyHasExited()
	updateChildLight()
	updateChildNightLight()
	updateChildTimeout()
    
    if (pause) {
	    logInfo("init: paused, skipping subscriptions again")
        return
    }
	
    if (occupiedModes || vacantModes || nightModes) {
	    subscribe(location, "mode", locationModeHandler)
    }
	subscribe(lightSensors, "illuminance", lightSensorHandler)
	
	subscribe(contactSensors, "contact", contactSensorHandler)
	subscribe(contactSensorsOpen, "contact", contactSensorOpenHandler)
	subscribe(motionSensors, "motion", motionSensorHandler)
	if (preventStateChangeIfNoMovementAtExits) {
		subscribe(exitMotionSensors, "motion.active", exitMotionSensorHandler)
	}

	subscribe(occupiedLightSwitches, "switch.on", occupiedLightOnHandler)
	subscribe(occupiedLightSwitches, "switch.off", occupiedLightOffHandler)
	subscribe(occupiedLightSwitches, "level", occupiedLightLevelHandler)
	subscribe(occupiedLightSwitches, "colorTemperature", occupiedLightColorTemperatureHandler)
	subscribe(occupiedLightSwitches, "hue", occupiedLightHueHandler)
	
	subscribe(occupiedTriggerSwitchesOn, "switch.on", occupiedTriggerSwitchHandler)
	subscribe(occupiedTriggerSwitchesOff, "switch.off", occupiedTriggerSwitchHandler)
	subscribe(occupiedTriggerCancelSwitchesOn, "switch.on", occupiedTriggerCancelSwitchHandler)
	subscribe(occupiedTriggerCancelSwitchesOff, "switch.off", occupiedTriggerCancelSwitchHandler)
	
	subscribe(engagedTriggerSwitchesOn, "switch.on", engagedTriggerSwitchHandler)
	subscribe(engagedTriggerSwitchesOff, "switch.off", engagedTriggerSwitchHandler)
	subscribe(engagedTriggerCancelSwitchesOn, "switch.on", engagedTriggerCancelSwitchHandler)
	subscribe(engagedTriggerCancelSwitchesAnyOff, "switch.off", engagedTriggerCancelSwitchHandler)
	subscribe(engagedTriggerCancelSwitchesAllOff, "switch.off", engagedTriggerCancelLightSwitchAllOffHandler)
	subscribe(engagedTriggerCancelContactsAllClosed, "contact.close", engagedTriggerCancelContactAllClosedHandler)
	
	def engagedSwitch = getChildEngagedSwitch()
	subscribe(engagedSwitch, "switch.on", childEngagedSwitchOnHandler)
	subscribe(engagedSwitch, "switch.off", childEngagedSwitchOffHandler)
	
	runDelayEvaluateState()
}

//
// Child
//

def createChildDeviceIfNotExist() {
	app.updateLabel(app.label)
	createOrUpdateLabel("amosyuen", "Room Occupancy", getChildId(), app.label)
	createOrUpdateLabel("hubitat", "Virtual Switch", getChildEngagedSwitchId(), "Engage ${app.label}")
}

def createOrUpdateLabel(namespace, typeName, deviceNetworkId, label) {
    def child = getChildDevice(deviceNetworkId)
    if (child) {
	    child.setLabel(label)
    } else {
        logInfo("createOrUpdateLabel: creating child device namespace $namespace, typeName $typeName, deviceNetworkId $deviceNetworkId, label $label")
        addChildDevice(namespace, typeName, deviceNetworkId, /*hubId=*/ null, [name: deviceNetworkId, label: label, completedSetup: true])
    }
	
}

def getChild() {
	return getChildDevice(getChildId())
}

def getChildId() {
	return "room_${app.id}"
}

def getChildEngagedSwitch() {
	return getChildDevice(getChildEngagedSwitchId())
}

def getChildEngagedSwitchId() {
	return "room_${app.id}_engaged_switch"
}

def updateChildContact(open = null)	{
	def child = getChild()
	if (!child) {
		return;
	}
	
	if (open == null) {
		open = motionSensors && motionSensors.currentMotion.contains('open')
	}

	def value = open ? 'open' : 'closed'
	logDebug("updateChildContact: $value")
	child.sendEvent(name: "contact", value: value, descriptionText: "contact is ${value}", displayed: true)
}

def updateChildMotion(motion = null)	{
	def child = getChild()
	if (!child) {
		return;
	}
	
	if (motion == null) {
		motion = motionSensors && motionSensors.currentMotion.contains('active')
	}

	def value = motion ? 'active' : 'inactive'
	logDebug("updateChildMotion: $value")
	child.sendEvent(name: "motion", value: value, descriptionText: "motion is ${value}", displayed: true)
}

def updateChildNobodyHasExited(nobodyHasExited = null) {
	def child = getChild()
	if (!child) {
		return
	}
	
	if (nobodyHasExited == null) {
		nobodyHasExited = preventStateChangeIfNoMovementAtExits && atomicState.nobodyHasExited
	}

	logDebug("updateChildNobodyHasExited: $nobodyHasExited")
	child.sendEvent(name: "nobodyHasExited", value: nobodyHasExited, descriptionText: "nobodyHasExited is ${nobodyHasExited}", displayed: true)
}

def updateChildLight(on = null) {
	def child = getChild()
	if (!child) {
		return;
	}
	
	if (on == null) {
		switch(atomicState.occupancy) {
			case "occupied":
				on = isAnyOccupiedLightOn()
				break;
			case "engaged":
				on = true
				break;
			case "checking":
				on = true
				break;
			case "vacant":
				on = false
				break;
		}
	}

	def value = on ? "on" : "off"
	child.sendEvent(name: "light", value: value, descriptionText: "light is ${value}", displayed: true)
}

def updateChildNightLight(on = nul) {
	def child = getChild()
	if (!child) {
		return;
	}
	
	if (on == null) {
		on = nightLightSwitches && nightLightSwitches.currentSwitch.contains("on")
	}

	def value = on ? "on" : "off"
	child.sendEvent(name: "nightlight", value: value, descriptionText: "night light is ${value}", displayed: true)
}

def updateOccupancy(occupancy)	{
	if (occupancy == atomicState.occupancy) {
		return
	}
    
	logDebug("updateOccupancy: $occupancy")
	atomicState.occupancy = occupancy
	atomicState.appLightChangeTime = now()
	
	def child = getChild()
	if (!child) {
		return
	}

	child.sendEvent(name: "occupancy", value: occupancy, descriptionText: "occupancy changed to ${occupancy}", displayed: true)
	def value = occupancy == "vacant" ? 'off' : 'on'
	child.sendEvent(name: "switch", value: value, descriptionText: "switch is ${value}", displayed: true)
	
	def url = ICONS_URL + "rooms${occupancy.capitalize()}State.png"
	child.sendEvent(name: "occupancyIcon", value: "<img src=${url} height=75 width=75>", descriptionText: "${occupancy} icon", displayed: false)
	
	def childEngagedSwitch = getChildEngagedSwitch()
	if (!childEngagedSwitch) {
		return
	}
	def engagedSwitchValue = occupancy == "engaged" ? "on" : "off"
	childEngagedSwitch.sendEvent(name: "switch", value: engagedSwitchValue, descriptionText: "switch is ${engagedSwitchValue}", displayed: true)
}

def updateChildTimeout() {
	def child = getChild()
	if (!child) {
		return;
	}

	def value = "never"
	if (atomicState.lastTriggerTime > 0) {
		def timeoutMillis = getTimeoutMillis()
		if (timeoutMillis != null) {
			def simpleDateFormat = new java.text.SimpleDateFormat("HH:mm:ss.SSS dd-MM-yyyy z");
			value = simpleDateFormat.format(new Date(timeoutMillis));
		}
	}
	logDebug("updateChildTimeout: $value")
	child.sendEvent(name: "timeout", value: value, descriptionText: "occupancy state times out at ${value}", displayed: true)
}

//
// Event Handlers
//

def locationModeHandler(evt) {
	logDebug("locationModeHandler: ${evt.name}=${evt.value}")
	if (vacantModes && vacantModes.contains(evt.value)) {
		logInfo("locationModeHandler: Changed to vacant mode")
		vacant()
		return
	}	
	if (atomicState.occupancy == "occupied") {
		logInfo("locationModeHandler: Re-evaluate occupied mode")
		// Re-evaluate occupied lights since it may only run in certain modes
        atomicState.appLightChangeTime = now()
		updateOccupiedLights()
	}
}

def lightSensorHandler(evt) {
	logDebug("lightSensorHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
	def lux = getAverageLux()
	def oldInLuxRange = atomicState.inLuxRange
	def newInLuxRange = oldInLuxRange
	if (newInLuxRange) {
		if (lux > occupiedLuxThresholdOff) {
			newInLuxRange = false
		}
	} else if (lux <= occupiedLuxThresholdOn) {
	    newInLuxRange = true
	}
    
    if (newInLuxRange != oldInLuxRange) {
    	atomicState.inLuxRange = newInLuxRange
    	switch (atomicState.occupancy) {
    		case "occupied":
    			logDebug("lightSensorHandler: Re-evaluating occupied lights because of lux change")
                atomicState.appLightChangeTime = now()
    			updateOccupiedLights()
    			break
	    }
    }
}

def contactSensorHandler(evt) {
	logDebug("contactSensorHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
	if (evt.value == "open") {
		contactSensorOpen()
	} else if (evt.value == "closed") {
		setTrigger("contact")
		updateChildContact()
		runDelayEvaluateState()
	}
}

// Contact sensors that trigger occupied only on open
def contactSensorOpenHandler(evt) {
	logDebug("contactSensorOpenHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
	if (evt.value == "open") {
		contactSensorOpen()
	} else if (evt.value == "closed") {
		updateChildContact()
	}
}

def contactSensorOpen() {
	def exited = preventStateChangeIfNoMovementAtExits && atomicState.nobodyHasExited
	if (exited) {
		atomicState.nobodyHasExited = false
		updateChildNobodyHasExited(false)
	}
	switch (atomicState.occupancy) {
		case "checking":
		case "vacant":
			setOccupancyTrigger("occupied", "contact", /* updateLights= */ true)
			break
		default:
			setTrigger("contact")
			if (exited) {
				runDelayEvaluateState()
			}
			break
	}
	updateChildContact(true)
}

def motionSensorHandler(evt) {
	logDebug("motionSensorHandler ${evt.getDevice()} ${evt.name}=${evt.value}")
	if (evt.value == "active") {
		def nobodyHasExited = preventStateChangeIfNoMovementAtExits &&
				(!contactSensors || !contactSensors.currentContact.contains("open")) &&
				(!contactSensorsOpen || !contactSensorsOpen.currentContact.contains("open")) &&
				(!exitMotionSensors || !exitMotionSensors.currentMotion.contains("active")) &&
				!atomicState.nobodyHasExited
		if (nobodyHasExited) {
			logInfo("motionSensorHandler: Preventing state change because there was motion " +
					"inside room while contacts were closed and exits had no motion")
			atomicState.nobodyHasExited = true
			updateChildNobodyHasExited(true)
		}
		switch (atomicState.occupancy) {
			case "checking":
			case "vacant":
				setOccupancyTrigger("occupied", "motion", /* updateLights= */ true)
				break
			default:
				setTrigger("motion")
				if (nobodyHasExited) {
					runDelayEvaluateState()
				}
				break
		}
		updateChildMotion(true)
	} else if (evt.value == "inactive") {
		updateChildMotion()
		runDelayEvaluateState()
	}
}

def exitMotionSensorHandler(evt) {
	logDebug("exitMotionSensorHandler ${evt.getDevice()} ${evt.name}=${evt.value}")
	if (atomicState.nobodyHasExited) {
		atomicState.nobodyHasExited = false
		updateChildNobodyHasExited(false)
		updateChildTimeout()
		runDelayEvaluateState()
	}
}

def occupiedLightOnHandler(evt) {
	logDebug("occupiedLightOnHandler: ${evt.getDevice()} switch=${evt.value}")
	atomicState.lastLightOffTime = 0
	updateChildLight(true)
	
    setStateIfUserChange(engageFromUserLightChanges ? "engaged" : "occupied",
                         value, evt, "isOccupiedLightOnUserChange", "occupiedLightOn")
}

def isOccupiedLightOnUserChange(occupancy, value, deviceId) {
	return atomicState.targetLevel == 0
}

def occupiedLightOffHandler(evt) {
	logDebug("occupiedLightOffHandler: ${evt.getDevice()} switch=${evt.value}")
	def occupancy = atomicState.occupancy
	if (occupancy != "vacant") {
		logDebug("occupiedLightOff: User light off")
		setTrigger("light")
		atomicState.lastLightOffTime = now()
	}
	if (!isAnyOccupiedLightOn()) {
		updateChildLight(false)
		if (occupancy != "engaged") {
			turnOnOrOffNightLights()
		}
	}
}

def occupiedLightLevelHandler(evt) {
    logDebug("occupiedLightLevelHandler: ${evt.getDevice()} level=${evt.value}")
    setStateIfUserChange(engageFromUserLightChanges ? "engaged" : "occupied",
                         evt.value, evt, "isOccupiedLightLevelUserChange", "occupiedLightLevel")
}

def isOccupiedLightLevelUserChange(occupancy, value, deviceId) {
    value = value as int
	def targetValue = atomicState.targetLevel
	if (targetValue == 0) {
		return true
	}

	def previousLevelMap = atomicState.previousLevelMap
	def previousLevel = previousLevelMap[deviceId.toString()]
	previousLevelMap[deviceId] = value
	atomicState.previousLevelMap = previousLevelMap
	if (targetValue >= previousLevel) {
		return value < previousLevel || value > targetValue
	}
	return value < targetValue || value > previousLevel
}

def occupiedLightColorTemperatureHandler(evt) {
    logDebug("occupiedLightColorTemperatureHandler: ${evt.getDevice()} colorTemperature=${evt.value}")
    setStateIfUserChange(engageFromUserLightChanges ? "engaged" : "occupied",
                         evt.value, evt, "isOccupiedLightColorTemperatureUserChange", "occupiedLightColorTemperature")
}

def isOccupiedLightColorTemperatureUserChange(occupancy, value, deviceId) {
    value = value as int
	def targetValue
	switch (occupancy) {
		case "engaged":
			targetValue = engagedLightTemperature
			break
		case "checking":
		case "occupied":
			targetValue = occupiedLightTemperature
			break
		case "vacant":
			break
		default:
			throw new Exception("Unhandled occupancy ${occupancy}")
	}
	return value != targetValue
}

def occupiedLightHueHandler(evt) {
    logDebug("occupiedLightHueHandler: ${evt.getDevice()} hue=${evt.value}")
    setOccupiedIfNotEngaged("occupiedLightHue");
}

def setStateIfUserChange(newOccupancy, value, evt, isUserChangeFn, source) {
    def occupancy = atomicState.occupancy
	if (OCCUPANCY_ENUM_VALUE[occupancy] >= OCCUPANCY_ENUM_VALUE[newOccupancy]) {
		setTrigger(source)
		return
	}

	def userChange = evt.physical
    if (!userChange) {
        def millisSinceOccupancyChange = ((time ? time : now()) - atomicState.appLightChangeTime)
        logDebug("${source}: millisSinceOccupancyChange ${millisSinceOccupancyChange}")
        if (millisSinceOccupancyChange > OCCUPANCY_TRANSITION_IGNORE_MILLIS) {
		    userChange = millisSinceOccupancyChange > OCCUPANCY_TRANSITION_MAX_MILLIS
	        if (!userChange) {
                userChange = "${isUserChangeFn}"(occupancy, value, evt.getDeviceId())
	        }
        }
    }
    if (userChange) {
        setOccupancyTrigger(newOccupancy, source)
    }
}

def setOccupiedIfNotEngaged(source, occupancy = null) {
	if (occupancy == null) {
		occupancy = atomicState.occupancy
	}
	switch (occupancy) {
		case "engaged":
		case "occupied":
			setTrigger(source)
			break
		case "checking":
		case "vacant":
			setOccupancyTrigger("occupied", source)
			break
		default:
			throw new Exception("Unhandled occupancy ${occupancy}")
	}
}

def occupiedTriggerSwitchHandler(evt) {
    logDebug("occupiedTriggerSwitchHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
	setOccupancyTrigger("occupied", "occupiedTriggerSwitch");
}

def occupiedTriggerCancelSwitchHandler(evt) {
	logDebug("occupiedTriggerCancelSwitchHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
	if (atomicState.occupancy == "occupied") {
		setOccupancyTrigger("checking", "occupiedTriggerCancelSwitch");
	}
}

def engagedTriggerSwitchHandler(evt) {
	logDebug("engagedTriggerSwitchHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
	setOccupancyTrigger("engaged", "engagedTriggerSwitch");
}

def engagedTriggerCancelSwitchHandler(evt) {
	logDebug("engagedTriggerCancelSwitchHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
	if (atomicState.occupancy == "engaged") {
		setOccupancyTrigger("occupied", "engagedTriggerCancelSwitch");
	}
}

def engagedTriggerCancelLightSwitchAllOffHandler(evt) {
	logDebug("engagedTriggerCancelLightSwitchAllOffHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
	if (atomicState.occupancy == "engaged" && !engagedTriggerCancelSwitchesAllOff.currentSwitch.contains("on")) {
		setOccupancyTrigger("occupied", "engagedTriggerCancelLightSwitchAllOff");
	}
}

def engagedTriggerCancelContactAllClosedHandler(evt) {
	logDebug("engagedTriggerCancelContactAllClosedHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
	if (atomicState.occupancy == "engaged" && !engagedTriggerCancelContactsAllClosed.currentContact.contains("open")) {
		setOccupancyTrigger("occupied", "engagedTriggerCancelContactAllClosed");
	}
}

def childEngagedSwitchOnHandler(evt) {
	logDebug("childEngagedSwitchOnHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
	if (atomicState.occupancy != "engaged") {
		setOccupancyTrigger("engaged", "childEngagedSwitchOn")
	}
}

def childEngagedSwitchOffHandler(evt) {
	logDebug("childEngagedSwitchOnHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
	if (atomicState.occupancy == "engaged") {
		setOccupancyTrigger("occupied", "childEngagedSwitchOff")
	}
}

def setOccupancyTrigger(newOccupancy, source, updateLights = false) {
    def occupancy = atomicState.occupancy
	def occupancyChange = occupancy != newOccupancy
	setTrigger(source, occupancyChange)
	if (occupancyChange) {
        logDebug("setOccupancyTrigger: Changing to ${newOccupancy} from ${occupancy} because of ${source}")
		"${newOccupancy}"()
    } else if (occupancy == "occupied" && updateLights) {
        updateOccupiedLights()
    }
}

//
// Occupancy Modes
//

def occupied() {
	logInfo("occupancy: occupied")
	updateOccupancy("occupied")
	
	updateChildTimeout()
	unschedule()
	updateOccupiedLights()
	
	runDelayEvaluateState()
}

def updateOccupiedLights() {
	def turnOffNightLight = false
	if (occupiedLightSwitches) {
		if (atomicState.lastTriggerSource?.startsWith("occupiedLight") &&
                (now() - atomicState.lastTriggerTime) < OCCUPANCY_TRANSITION_MAX_MILLIS) {
			logDebug("updateOccupiedLights: Not changing occupied lights because user changed lights")
			// User triggered lights on, so don't change the lights.
			// Turn off night lights if any light is on.
			turnOffNightLight = isAnyOccupiedLightOn()
		} else if (checkOccupiedTurnOnLightsConditions()) {
			turnOffNightLight = true
			occupiedLightsOn(occupiedLightLevel)
		} else {
			runIn(NIGHT_LIGHT_DELAY_SECONDS, occupiedLightsOff)
		}
	}
	turnOnOrOffNightLights(turnOffNightLight)
}

def occupiedLightsOn(level) {
	logDebug("occupiedLightsOn")
	atomicState.targetLevel = level
	saveOccupiedLightSwitchLevels()
	turnOnSwitches(occupiedLightSwitches, level, occupiedLightTemperature)
}

def occupiedLightsOff() {
	logDebug("occupiedLightsOff")
	atomicState.targetLevel = 0
	atomicState.previousLevelMap = [:]
	turnOffSwitches(occupiedLightSwitches)
}

def checkOccupiedTurnOnLightsConditions() {
	if (occupiedModes && !occupiedModes.contains(location.mode)) {
		logDebug("checkOccupiedTurnOnLightsConditions: location mode ${location.mode} is not one of ${occupiedModes}")
		return false
	}
	if (!atomicState.inLuxRange) {
		logDebug("checkOccupiedTurnOnLightsConditions: lux not in range")
		return false
	}
	def secondsSinceLastLightOff = getSecondsSinceLastLightOff()
	if (secondsSinceLastLightOff <= KEEP_LIGHTS_OFF_SECONDS) {
		logDebug("checkOccupiedTurnOnLightsConditions: only ${secondsSinceLastLightOff} seconds since light was turned off by user ")
		return false
	}
	logDebug("checkOccupiedTurnOnLightsConditions: true")
	return true
}

def engaged() {
	logInfo("engaged")
	atomicState.lastLightOffTime = 0
	updateOccupancy("engaged")
	updateChildTimeout()
	unschedule()
	
	// Don't change lights if:
	// 1) Engaged state was caused by light change
	// 2) Any engagedScenes switch is on
	if (!atomicState.lastTriggerSource?.startsWith("occupiedLight")
			&& (!engagedScenes || !engagedScenes.currentSwitch.contains("on"))) {
		atomicState.targetLevel = engagedLightLevel
		saveOccupiedLightSwitchLevels()
		turnOnSwitches(occupiedLightSwitches, engagedLightLevel, engagedLightTemperature)	
		turnOnSwitches(engagedSwitches, engagedLightLevel, engagedLightTemperature)	
	}
	runIn(NIGHT_LIGHT_DELAY_SECONDS, nightLightsOff)
	
	runDelayEvaluateState()
}

def checking() {
	logInfo("checking")
	atomicState.lastLightOffTime = 0
	updateOccupancy("checking")	
	updateChildTimeout()
	unschedule()

	if (preventStateChangeIfNoMovementAtExits && !atomicState.nobodyHasExited) {
		atomicState.nobodyHasExited = false
		updateChildNobodyHasExited(false)
	}
	
	def turnOffNightLight = false
	if (occupiedLightSwitches) {
		if (isAnyOccupiedLightOn()) {
			turnOffNightLight = true
			occupiedLightsOn(checkingLightLevel)
		} else {
			runIn(NIGHT_LIGHT_DELAY_SECONDS, occupiedLightsOff)
		}
	}
	turnOnOrOffNightLights(turnOffNightLight)
	
	runDelayEvaluateState()
}

def vacant() {
	logInfo("vacant")
	atomicState.lastTriggerTime = 0
	atomicState.lastTriggerSource = ""
	atomicState.lastLightOffTime = 0
	updateOccupancy("vacant")
	occupiedLightsOff()
	updateChildTimeout()
	unschedule()

	if (preventStateChangeIfNoMovementAtExits && !atomicState.nobodyHasExited) {
		atomicState.nobodyHasExited = false
		updateChildNobodyHasExited(false)
	}
	
	if (vacantSwitchesOn) {
		logDebug("Turning vacant switches on")
		turnOnSwitches(vacantSwitchesOn)
	}
	if (vacantSwitchesOff) {
		logDebug("Turning vacant switches off")
		turnOffSwitches(vacantSwitchesOff)
	}
	
	turnOnOrOffNightLights()
}

def checkVacantConditions() {
	if (contactSensors && contactSensors.currentContact.contains("open")) {
		logDebug("checkVacantConditions: contact is open")
		return false
	}
	// contactSensorsOpen should not prevent state transition
	if (motionSensors && motionSensors.currentMotion.contains("active")) {
		logDebug("checkVacantConditions: motion is detected")
		return false
	}
	logDebug("checkVacantConditions: true")
	return true
}

def turnOnOrOffNightLights(forceOff = false) {
	logDebug("turnOnOrOffNightLights: forceOff ${forceOff}")
	if (!nightLightSwitches) {
		return
	}
	if (!forceOff && checkTurnOnNightLightConditions()) {
		nightLightsOn()
	} else {
		runIn(NIGHT_LIGHT_DELAY_SECONDS, nightLightsOff)
	}
}

def nightLightsOn() {
	logInfo("nightLightsOn")
	turnOnSwitches(nightLightSwitches, nightLightLevel)
	updateChildNightLight(true)
}

def nightLightsOff() {
	logInfo("nightLightsOff")
	turnOffSwitches(nightLightSwitches)
	updateChildNightLight(false)
}

def checkTurnOnNightLightConditions() {
	if (nightModes && !nightModes.contains(location.mode)) {
		logDebug("checkNightLightTurnOnLightsConditions: location mode ${location.mode} is not one of ${nightModes}")
		return false;
	}
	if (lightSensors && !atomicState.inLuxRange) {
		logDebug("checkNightLightTurnOnLightsConditions: not in lux threshold")
		return false;
	}
	logDebug("checkNightLightTurnOnLightsConditions: true")
	return true
}

def setTrigger(source, occupancyChange = false) {
	logDebug("setTrigger: source ${source}")
	def time = now()
	if (!occupancyChange && time - atomicState.lastTriggerTime < MIN_RETRIGGER_INTERVAL_MILLIS) {
		return
	}
	atomicState.lastTriggerTime = now()
	atomicState.lastTriggerSource = source
	
	// Don't update child as the occupancy change code will do that
	if (!occupancyChange) {
		updateChildTimeout()
	}
}

def runDelayEvaluateState() {
	unschedule(runDelayEvaluateState)
    if (pause) {
	    logInfo("runDelayEvaluateState: paused")
        return
    }
	def occupancy = atomicState.occupancy
	if (occupancy == "vacant") {
		return
	}
	logDebug("runDelayEvaluateState: Current occupancy: ${occupancy}")
	
	def timeoutMillis = getTimeoutMillis()
	if (timeoutMillis == null) {
		logWarn("runDelayEvaluateState: timeoutMillis should not be null");
		return
	}
	def waitMillis = timeoutMillis - now()
	logDebug("runDelayEvaluateState: waitMillis ${waitMillis}")
	if (waitMillis <= 0) {
		if (checkVacantConditions()) {
			atomicState.lastTriggerTime = now()
			atomicState.lastTriggerSource = "timeout"
			switch (occupancy) {
				case "engaged":
                    if (engagedTimeoutPreventSwitches && engagedTimeoutPreventSwitches.currentSwitch.contains("on")) {
			            logDebug("runDelayEvaluateState: engage conditions not successful")
                    } else {
					    logInfo("runDelayEvaluateState: changing to occupied state")
					    occupied()
                    }
					break
				case "occupied":
					logInfo("runDelayEvaluateState: changing to checking state")
					checking()
					break
				default:
					logInfo("runDelayEvaluateState: changing to vacant state")
					vacant()
					break
			}
		} else {
			logDebug("runDelayEvaluateState: vacant conditions not successful")
		}
	} else {
		def waitSeconds = Math.ceil(waitMillis / 1000f + 1).toInteger()
		logDebug("runDelayEvaluateState: wait ${waitSeconds} seconds to re-evaluate")
		runIn(waitSeconds, runDelayEvaluateState)
	}
}

//
// Helpers
//

def isAnyOccupiedLightOn() {
	return occupiedLightSwitches && occupiedLightSwitches.currentSwitch.contains("on")
}

def getAverageLux()	{
	def luxes = lightSensors.currentIlluminance
	def lux = 0.0f
	for (def lx : luxes) {
		lux = lux + lx;
	}
	lux = (lux / luxes.size()).round(0).toInteger()
	logDebug("getAverageLux: lux ${lux}")
	return lux
}

def getTimeoutMillis() {
	def lastTriggerTime = atomicState.lastTriggerTime
	if (lastTriggerTime == 0) {
		return null
	}
	def timeoutSeconds

	if (preventStateChangeIfNoMovementAtExits && atomicState.nobodyHasExited) {
		timeoutSeconds = nobodyHasExitedTimeoutSeconds
	} else {
		switch (atomicState.occupancy) {
			case "checking":
				timeoutSeconds = checkingTimeoutSeconds
				break;
			case "occupied":
				timeoutSeconds = occupiedTimeoutSeconds
				break;
			case "engaged":
				timeoutSeconds = engagedTimeoutSeconds
				break;
			default:
				return null;
		}
	}
	
	return lastTriggerTime + timeoutSeconds * 1000
}

def addImage(icon)	{
	def url = ICONS_URL + icon
	return "<img src=${url} height=${IMG_SIZE} width=${IMG_SIZE}>	"
}

def getSecondsSinceLastLightOff() {
	def secondsSinceLastLightOff = (now() - atomicState.lastLightOffTime) / 1000
	logDebug("getSecondsSinceLastLightOff: ${secondsSinceLastLightOff}")
	return secondsSinceLastLightOff
}

def saveOccupiedLightSwitchLevels() {
	def previousLevel = [:]
	occupiedLightSwitches.each{
        if (it.hasCommand('setLevel')) {
			previousLevel[it.id] = it.currentLevel
        }
	}
	atomicState.previousLevelMap = previousLevel
}

def turnOnSwitches(switches, level = null, colorTemperature = null) {
	switches.each{
        def setLevel = level && it.hasCommand('setLevel') && it.currentLevel != level
		if ((!setLevel || explicitlyTurnOnDimmers) && it.currentSwitch == "off") {
            logDebug("turnOnSwitches: Turning on ${it}")
			it.on()
		}
        if (setLevel) {
			it.setLevel(level)
            logDebug("turnOnSwitches: Setting ${it} level to ${level}")
        }
		if (colorTemperature && it.hasCommand('setColorTemperature')
                    && (it.currentColorMode != 'CT' || it.currentColorTemperature != colorTemperature)) {
			it.setColorTemperature(colorTemperature)
            logDebug("turnOnSwitches: Setting ${it} color temperature to ${colorTemperature}")
		}
	}
}

def turnOffSwitches(switches) {
	switches.each{
		if (it.currentSwitch == "on") {
			it.off()
		} else {
				logDebug("turnOffSwitches: Skipping switch ${it} because it is already off")
		}
	}
}

//
// Debug
//

def logDebug(msg){
	if (debugLog == true) {
		log.debug msg
	}
}

def logInfo(msg){
	log.info msg
}

def logWarn(msg) {
	log.warn msg
}
