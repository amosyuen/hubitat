/**
 *  Copyright 2021 Amos Yuen, Jonathan Porter, Kevin Tierney, C Steele
 *
 *  Licensed under the Apache License, Version 2.0 (the "License") you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

import groovy.transform.Field
import groovy.time.*

definition(
	name: "Better Laundry Monitor Instance",
	namespace: "amosyuen",
    author: "Amos Yuen, Johnathan Porter, Kevin Tierney, C Steele",
	description: "Child: powerMonitor capability, monitor the laundry cycle and alert when it's done.",
	category: "Green Living",
	    
	parent: "amosyuen:Better Laundry Monitor",
	
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: ""
)

//
// Preferences
//

preferences {
	page (name: "mainPage")
	page (name: "sensorPage")
	page (name: "thresholdPage")
	page (name: "notificationPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", install: true, uninstall: true) {
		section (title: "Name") {
			label title: "This child app's name", required: true, submitOnChange: true
            app.updateLabel(getLabelWithoutStatus())
            paragraph "A child device will be created for this app that has the same name and has switch and state attributes mirroring the app"
		}
		section("Settings") {
            href "sensorPage", title: "Sensors", description: "Sensors to monitor", state: powerMeter || accelerationSensor ? "complete" : null
            href "thresholdPage", title: "Sensor Thresholds", description: "Sensor thresholds that will trigger state changes"
            href "notificationPage", title: "Notification Messages"
		}
		section (title: "Debugging") {
			input (name: "isDisabled", type: "bool", title: "Disable app", defaultValue: false)
			input (name: "logDebugOutput", type: "bool", title: "Log Debug Output", defaultValue: false)
		}
        section {
            paragraph "${parent.versionInfo()}"
        }
	}
}

def sensorPage() {
	dynamicPage(name: "sensorPage") {
        section() {
            paragraph "If both a power monitor and acceleration monitor is specified, either can activate state changes"
        }
        section ("<b>Power Monitor to measure power usage</b>") {
            input "powerMeter", "capability.powerMeter", title: "Power Meter" , multiple: false, required: false, defaultValue: null
        }
        section("<b>Acceleration Sensor to measure vibrations</b>") {
            input "accelerationSensor", "capability.accelerationSensor", title: "Acceleration Sensor" , multiple: false, required: false, defaultValue: null
        }

		section("<b>Contact sensor to monitor if machine door was opened</b>") {
			input "contactSensor", "capability.contactSensor", title: "Contact Sensor" , multiple: false, required: false, defaultValue: null
		}
	}
}

def thresholdPage() {
	dynamicPage(name: "thresholdPage") {
		if (powerMeter) {
			section ("<b>Power Monitor Thresholds</b>", hidden: false) {
				input "startThresholdWatts", "decimal", title: "Start cycle when power raises above (W)", defaultValue: "10", required: false
				input "doorOpenWatts", "decimal", title: "Door is open when reading above (W)", required: false
			}
		}
        if (accelerationSensor && accelerationSensor.any{ it.hasAttribute("activityLevel") }) {
			section ("<b>Activity Level Thresholds</b>", hidden: false) {
				input "startThresholdActivityLevel", "number", title: "Instead of using acceleration attribute, start cycle when activity level raises above", defaultValue: "50", required: false
			}
		}
		section("<b>Time Thresholds</b>", hidden: false) {
			input "activeDurationSeconds", "number", title: "Number of seconds the reading is above start threshold before it is considered started.", required: false, defaultValue: 30
			input "inactiveDurationSeconds", "number", title: "Number of seconds the reading is below the start threshold before it is considered finished.", required: false, defaultValue: 60
		}
	}
}
 
def notificationPage() {
	dynamicPage(name: "notificationPage") {
		section ("<b>Send this message</b>") {
            paragraph "Notification messages are sent using parent app notification methods"
			input "messageStart", "text", title: "Running notification message", required: false
			input "messageEnd", "text", title: "Finished notification message", required: false
			input "messageKeepDoorOpen", "text", title: "Keep door open notification message", required: false
		}
	}
}

//
// Life Cycle
//

public static String STATE_INACTIVE() { return 'Inactive' }
public static String STATE_RUNNING() { return 'Running' }
public static String STATE_FINISHED() { return 'Finished' }
public static String STATE_OPENED() { return 'Opened' }

def installed() {
	atomicState.firstActivityMillis = null
	atomicState.lastActivityMillis = null
	atomicState.cycleStartMillis = null
	atomicState.cycleFinishMillis = null
    atomicState.state = STATE_INACTIVE()
	
	initialize()
}


def updated() {
	initialize()
} 

def initialize() {
    logDebug "initialize"
	unsubscribe()
	unschedule()
	updateMyLabel(/*init=*/true)
    createChildDeviceIfNotExist()
    updateChild()
	if (isDisabled) {
		return
	}
	
	if (powerMeter) {
		subscribe(powerMeter, "power", powerHandler)
        powerHandler()
	} 
	if (accelerationSensor) {
		if (startThresholdActivityLevel) {
			subscribe(accelerationSensor, "activityLevel", activityLevelHandler)
            activityLevelHandler()
		} else {
			subscribe(accelerationSensor, "acceleration", accelerationHandler)
            accelerationHandler()
		}
	}
	if (contactSensor) {
		subscribe(contactSensor, "contact", contactHandler)
		contactHandler([ value: contactSensor.currentContact ])
	}
    
    if (isState(STATE_RUNNING())) {
        checkIsInactive()
    } else {
        checkIsActive()
    }
}

def uninstalled() {
    logDebug "uninstalled"
	unschedule()
	getChildDevices().each {
		deleteChildDevice(it.deviceNetworkId)
	}
}

def setState(newState) {
	def oldState = atomicState.state
	if (oldState != newState) {
		atomicState.state = newState
        updateChild()
	    updateMyLabel()
        logDebug "setState: from ${oldState} to ${newState}"
	} 
}

def isState(state) {
	return atomicState.state == state
}

//
// Child
//

def createChildDeviceIfNotExist()  {
  if (getChild() == null) {
    log.info("createChildDeviceIfNotExist: creating child")
    addChildDevice("amosyuen", "Better Laundry Monitor Device", getChildId(), null, [name: getChildId(), label: getLabelWithoutStatus(), completedSetup: true])
  }
}

def getChild() {
  return getChildDevice(getChildId())
}

def getChildId() {
  return "laundry_monitor_${app.id}"
}

def updateChild()  {
  def child = getChild()
  if (!child) {
    return
  }
  
  def switchValue = isState(STATE_RUNNING()) ? "on" : "off"
  child.sendEvent(name: "switch", value: switchValue, descriptionText: "switch is ${switchValue}", displayed: true)
  def state = isDisabled ? "disabled" : atomicState.state
  child.sendEvent(name: "state", value: state, descriptionText: "state is ${state}", displayed: true)
  child.sendEvent(name: "stateHtml", value: getStateLabel(), descriptionText: "state is ${state}", displayed: false)
}

//
// Event Handlers
//

def accelerationHandler(evt) {
	def acceleration = accelerationSensor.currentAcceleration
	if (acceleration == "active") {
		onGreaterThanThreshold()
	} else {
		onLessThanThreshold()
	}
}

def activityLevelHandler(evt) {
	def activityLevel = accelerationSensor.currentActivityLevel.toInteger()
	if (activityLevel >= startThresholdActivityLevel) {
		onGreaterThanThreshold()
	} else {
		onLessThanThreshold()
	}
}

def powerHandler(evt) {
	def latestPower = powerMeter.currentPower
	if (latestPower >= startThresholdWatts) {
		onGreaterThanThreshold()
	} else {
		onLessThanThreshold()
	}
}

def contactHandler(evt) {
	if (evt.value == "open") {
		if (isState(STATE_FINISHED())) {
			toOpened()
		}
	} else if (messageKeepDoorOpen != null
			&& notificationKeepDoorOpenSeconds != null
			&& isWithinNotifyKeepDoorOpenTimeInterval()) {
		runIn(parent.notificationKeepDoorOpenDelaySeconds, notifyRepeat,
			[
				data: [
					state: STATE_OPENED(),
					msg: messageKeepDoorOpen,
					repeatCount: parent.notificationClusterCount
				]
			])
	}
}

//
// State Management
//

def toInactive() {
	log.info "Inactive"
    unschedule(checkIsActive)
    unschedule(checkIsInactive)
	setState(STATE_INACTIVE())
}

def onGreaterThanThreshold() {
    atomicState.lastActivityMillis = null
    if (isState(STATE_RUNNING()) || atomicState.firstActivityMillis != null) {
        logDebug "onGreaterThanThreshold: skip state=${atomicState.state} firstActivityMillis=${atomicState.firstActivityMillis}"
        return
    }
	logDebug "onGreaterThanThreshold: Set first active"
    atomicState.firstActivityMillis = now()
    unschedule(checkIsInactive)
    runIn(activeDurationSeconds + 1, checkIsActive)
}

def checkIsActive() {
    if (atomicState.firstActivityMillis == null) {
        logDebug "checkIsActive: cancelled"
        return
    }
    def millisRemaining = atomicState.firstActivityMillis + activeDurationSeconds * 1000.0 - now()
    logDebug "checkIsActive: millisRemaining=$millisRemaining"
    if (millisRemaining <= 0) {
		toRunning()
    } else {
        runIn((int)(Math.ceil(millisRemaining / 1000.0) + 1), checkIsActive)
    }
}

def toRunning() {
	log.info "Cycle started"
    if (atomicState.firstActivityMillis == null) {
        atomicState.firstActivityMillis = now()
    }
	atomicState.cycleStartMillis = atomicState.firstActivityMillis
    atomicState.firstActivityMillis = null
	setState(STATE_RUNNING())
	if (messageStart != null && messageStart != "") {
		notifyRepeat([
			state: STATE_RUNNING(),
			msg: messageStart,
			repeatCount: parent.notificationClusterCount
		])
	}
}

def onLessThanThreshold(evt) {
    atomicState.firstActivityMillis = null
    if (!isState(STATE_RUNNING()) || atomicState.lastActivityMillis != null) {
        logDebug "onLessThanThreshold: skip state=${atomicState.state} lastActivityMillis=${atomicState.lastActivityMillis}"
        return
    }
	logDebug "onLessThanThreshold: Set last active"
    atomicState.lastActivityMillis = now()
    unschedule(checkIsActive)
    runIn(activeDurationSeconds + 1, checkIsInactive)
}

def checkIsInactive() {
    if (atomicState.lastActivityMillis == null) {
        logDebug "checkIsInactive: cancelled"
        return
    }
    def millisRemaining = atomicState.lastActivityMillis + inactiveDurationSeconds * 1000.0 - now()
    logDebug "checkIsInactive: millisRemaining=$millisRemaining"
    if (millisRemaining <= 0) {
		toFinished()
    } else {
        runIn((int)(Math.ceil(millisRemaining / 1000.0) + 1), checkIsInactive)
    }
}

def toFinished() {
	log.info "Cycle finished"
    if (atomicState.lastActivityMillis == null) {
        atomicState.lastActivityMillis = now()
    }
    atomicState.cycleFinishMillis = atomicState.lastActivityMillis
	setState(STATE_FINISHED())
    if (messageEnd != null || messageEnd != "") {
		notifyRepeat([
			state: STATE_FINISHED(),
			msg: messageEnd,
			repeatCount: parent.notificationClusterCount
		])
    }
}

def toOpened() {
	log.info "Door Opened"
	setState(STATE_OPENED())
	unschedule(notifyRepeat)
}

//
// Notifications 
//

def notifyRepeat(data) {
	if (!isState(data.state)) {
		return
	}
	def repeatCluster = false
	switch (data.state) {
		case STATE_OPENED():
			if (!isWithinNotifyKeepDoorOpenTimeInterval()) {
				return
			}
			// fallthrough
		case STATE_FINISHED():
			if (contactSensor != null) {
				if (contactSensor.currentContact == "open") {
					return
				}
				repeatCluster = true
			}
			break
	}
	sendNotification(data.msg)
	if (data.repeatCount > 1) {
		data.repeatCount = data.repeatCount - 1
		runIn(parent.notificationRepeatDelay, notifyRepeat, [data: data])
	} else if (repeatCluster) {
		data.repeatCount = parent.notificationClusterCount
		runIn(parent.notificationClusterDelay, notifyRepeat, [data: data])
	}
}

def isWithinNotifyKeepDoorOpenTimeInterval() {
	return (isState(STATE_OPENED())
		&& atomicState.cycleFinishMillis != null
		&& now() - atomicState.cycleFinishMillis <= notificationKeepDoorOpenSeconds * 1000)

}

def sendNotification(msg) {
	logDebug "sendNotification: $msg"
	if (parent.pushNotificationDevices) {
		parent.pushNotificationDevices*.deviceNotification(msg)
	}
	if (parent.blockSpeechNotificationSwitch?.currentSwitch == "on") {
		return
	}
	if (parent.speechNotificationDevices) { 
		if (parent.speechEchoAnnouncement) {
			parent.speechNotificationDevices*.playAnnouncementAll(msg)
		} else {
			parent.speechNotificationDevices*.speak(msg)
		}
	}
	if (parent.musicPlayerDevices) {
		parent.musicPlayerDevices*.playText(msg)
	}
}

//
// Label
//

def getLabelWithoutStatus() {
    def index = app.label ? app.label.indexOf(" (<span") : -1
    return index == -1 ? app.label : app.label.substring(0, index)
}

def updateMyLabel(init = false) {
    String label = getLabelWithoutStatus() + " (${getStateLabel()})"
    logDebug "updateMyLabel: label=$label"
	if (app.label != label || init) {
		app.updateLabel(label)
        
        // Run job to update label once tomorrow arrives
        unschedule(updateMyLabel)
        if (label.matches('.*(today|yesterday).*')) {
	        def tomorrow = (new Date() + 1).clearTime()
            def delaySeconds = Math.ceil((tomorrow.getTime() - now()) / 1000 + 1).toInteger()
            runIn(delaySeconds, updateMyLabel)
        }
	}
}

def getStateLabel() {
	if (isDisabled) {
		return '<span style="color:Red">Disabled</span>'
	}
    if (isState(STATE_RUNNING())) {
		return "<span style=\"color:Orange\">Started ${fixDateTimeString(atomicState.cycleStartMillis)}</span>"
	}
    if (isState(STATE_FINISHED())) {
		return "<span style=\"color:Green\">Finished ${fixDateTimeString(atomicState.cycleFinishMillis)}</span>"
    }
    if (isState(STATE_OPENED())) {
		return "<span style=\"color:Green\">Finished and opened ${fixDateTimeString(atomicState.cycleFinishMillis)}</span>"
    }
	return "<span style=\"color:Grey\">Inactive</span>"
}
				   
def fixDateTimeString(eventDate) {
	def today = new Date().clearTime()
	def target = new Date(eventDate).clearTime()
	
	String date = ''	
	if (target == today) {
		date = 'today'	
	} else if (target == today-1) {
		date = 'yesterday'
	} else {
		date = 'on ' + target.format('MM-dd')
	}	 
	def time = new Date(eventDate).format('h:mma').toLowerCase() 
	return "${date} at ${time}"
}

//
// Misc
//

def logDebug(msg) {
	if (logDebugOutput) {
		log.debug(msg)
	}
}