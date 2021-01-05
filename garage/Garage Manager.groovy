/**
 *  Copyright 2021 Amos Yuen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License") you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Automates:
 * - Opening the garage door when any presence sensor arrives
 *    - Presence must have been away for a minimum amount of time
 *    - Door must not have been activated for a minimum amount of time
 * - Closing the garage door after all presence sensors departs or it has been open for a while
 *    - Delays closing the door if there has been motion recently
 *    - Retries closing the door if close fails
 * - Notifications on automatic garage door changes
 */

def appVersion() {
  return "1.0.0"
}

definition(
  name: 'Garage Manager',
  namespace: 'amosyuen',
  author: 'Amos Yuen',
  description: 'Manage a garage door open and close state',
  iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
  iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png'
)

preferences {
  page(name: 'pageMain')
}

def pageMain() {
  dynamicPage(name: 'pageMain', uninstall: true, install: true) {
    section("Main") {
      label title: "Assign a name", required: true
    }
    
    section('Garage Door') {
      input name: 'doorControl', type: 'capability.doorControl', title: 'Garage Door Control', required: true
    }

    section('Sensors') {
      input name: 'presenceSensors', type: 'capability.presenceSensor', title: 'Presence Sensors', required: false, multiple: true
      input "motionSensors", "capability.motionSensor", title: "Motion sensors", multiple: true, required: false
    }
    
    section("Auto Open") {
      input "openModes", "mode", title: "Modes", multiple: true, required: false, defaultValue: ["Away", "Day"]
      input "doorDebounceSeconds", "number", title: "Seconds since last door change before it can trigger the door to open", required: true, defaultValue: 300, range: "1..*"
      input "presenceDebounceSeconds", "number", title: "Seconds presence must be not present before it can trigger the door to open", required: true, defaultValue: 600, range: "1..*"
      input "autoOpenChangeMode", "mode", title: "Change mode to", required: false, defaultValue: "Day"
    }
    
    section("Auto Close") {
      input "closeSeconds", "number", title: "Timeout in seconds", required: false, defaultValue: 600, range: "1..*"
      input "closeAwaySeconds", "number", title: "Timeout in seconds when everyone is away", required: false, defaultValue: 60, range: "1..*"
      input "closeEngagedSeconds", "number", title: "Timeout in seconds when garage is engaged", required: false, defaultValue: 10800, range: "1..*"
    }
    
    section("Notifications") {
      input "notificationDevices", "capability.notification", title: "Notification Devices", multiple: true, required: false
    }
    
    section("Debug") {
      input "debugLog", "bool", title: "Log debug statements", defaultValue: false, submitOnChange: true
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
  
  atomicState.engaged = atomicState.engaged || false
  // Re-initialize maps to prevent them growing from removed old presence objects
  def oldLastPresenceChangeTime = atomicState.lastPresenceChangeTime;
  def lastPresenceValue = [:]
  def lastPresenceChangeTime = [:]
  
  def millis = new Date().getTime()
  presenceSensors.each{
    def id = it.id.toString() // Maps only use string keys
    if (it.currentPresence != lastPresenceValue[id]) {
      lastPresenceChangeTime[id] = millis
    } else {
      def lastMillis = lastPresenceChangeTime[id]
      lastPresenceChangeTime[id] = lastMillis == null ? millis : lastMillis
    }
    lastPresenceValue[id] = it.currentPresence
    logDebug("init: update last presence ${id}"
             + " lastPresenceValue=${lastPresenceValue[id]}" 
             + " lastPresenceChangeTime=${lastPresenceChangeTime[id]}")
  }
  atomicState.lastPresenceValue = lastPresenceValue
  atomicState.lastPresenceChangeTime = lastPresenceChangeTime
  
  atomicState.lastMotionTime = 0
  
  if (doorControl.currentDoor != atomicState.lastDoor) {
    atomicState.lastDoor = doorControl.currentDoor
    atomicState.lastDoorChangeTime = millis
  }
  
  subscribe(doorControl, "door", doorControlHandler)  
  subscribe(presenceSensors, "presence", presenceHandler) 
  subscribe(motionSensors, "motion", motionSensorHandler)
  
  closeDoorAfterTimeout()
}

//
// Event Handlers
//

def doorControlHandler(evt) {
  logDebug("doorControlHandler: ${evt.name}: ${evt.value}")
  if (atomicState.lastDoor == evt.value) {
    return
  }
  switch (evt.value) {
    case "closed":
      unschedule()
      updateEngaged(false)
      updateLastDoor(evt)
      break;
    case "open":
      updateLastDoor(evt)
      closeDoorAfterTimeout()
      break
    case "unknown":
      updateLastDoor(evt)
      if (notificationDevices) {
        notificationDevices.deviceNotification("ERROR: Garage door is in unknown state!")
      }
      break
  }
}

def updateLastDoor(evt) {
  atomicState.lastDoor = evt.value
  atomicState.lastDoorChangeTime = evt.date.getTime()
  logInfo("updateLastDoor: lastDoor=${atomicState.lastDoor}"
           + " lastDoorChangeTime=${atomicState.lastDoorChangeTime}")
}

def openDoorForPresence(evt) {
  if (doorControl.currentDoor != "closed") {
    logDebug("openDoorForPresence: Failed condition currentDoor ${doorControl.currentDoor} is not closed")
    return
  }
    
  if (openModes && !openModes.contains(location.mode)) {
    logDebug("openDoorForPresence: Failed condition location.mode ${location.mode} is not in openModes ${openModes}")
    return
  }
    
  if (openModes && !openModes.contains(location.mode)) {
    logDebug("openDoorForPresence: Failed condition location.mode ${location.mode} is not in openModes ${openModes}")
    return
  }
    
  def id = evt.deviceId.toString() // Maps only use string keys
  if (atomicState.lastPresenceValue[id] == "present") {
    logDebug("openDoorForPresence: Failed condition lastPresenceValue[${id}]=${atomicState.lastPresenceValue[id]} is already present")
    return
  }
    
  def presenceMillisSinceLastChange = evt.date.getTime() - atomicState.lastPresenceChangeTime[id]
  if (presenceMillisSinceLastChange < presenceDebounceSeconds * 1000) {
    logDebug("openDoorForPresence: Failed condition presenceMillisSinceLastChange ${presenceTimeSinceLastChangeMillis} < ${presenceDebounceSeconds * 1000}")
    return
  }
    
  def doorMillisSinceLastChange = evt.date.getTime() - atomicState.lastDoorChangeTime
  if (doorMillisSinceLastChange < doorDebounceSeconds * 1000) {
    logDebug("openDoorForPresence: Failed condition doorMillisSinceLastChange ${doorMillisSinceLastChange} < ${doorDebounceSeconds * 1000}")
    return
  }
    
  logInfo("openDoorForPresence: Opening door for presence ${id}")
  if (notificationDevices) {
    notificationDevices.deviceNotification("Auto-opening garage door!")
  }
  if (autoOpenChangeMode) {
    location.setMode(autoOpenChangeMode)
  }
  openGarage()
}

def presenceHandler(evt) {
  logDebug("presenceHandler: ${evt.name}: ${evt.value}")
  def id = evt.deviceId.toString() // Maps only use string keys
  if (evt.value == "present") {
    openDoorForPresence(evt)
  } else {
    if (everyoneAway() && doorControl.currentDoor != "closed") {
      logInfo("presenceHandler: Everyone has left the house, auto-closing garage door")
      closeDoorAfterTimeout()
    }
  }
  if (atomicState.lastPresenceValue[id] != evt.value) {
    updateAtomicStateMap('lastPresenceValue', id, evt.value)
    updateAtomicStateMap('lastPresenceChangeTime', id, evt.date.getTime())
    logDebug("presenceHandler: update last presence ${id}"
             + " lastPresenceValue=${atomicState.lastPresenceValue[id]}"
             + " lastPresenceChangeTime=${atomicState.lastPresenceChangeTime[id]}")
  }
}

def motionSensorHandler(evt) {
  logDebug("motionSensorHandler ${evt.name}: ${evt.value}")
  if (evt.value == "active") {
    atomicState.lastMotionTime = evt.date.getTime()
	closeDoorAfterTimeout()
  }
}

def switchHandler(evt) {
  logDebug("switchHandler: ${evt.name}: ${evt.value}")
  atomicState.engaged = evt.value == "on"
  if (evt.value == "on") {
    openGarage()
  }
  closeDoorAfterTimeout()
}

//
// Logic
//

def openGarage() {
  if (doorControl.currentDoor == "open") {
    return 
  }
  
  logInfo("openGarage: Opening garage door")
  doorControl.open()
}

def everyoneAway() {
  return presenceSensors.every{ it.currentPresence == "not present" }
}

def getTimeoutSeconds() {
  if (atomicState.engaged) {
    logDebug("getTimeoutSeconds: engaged ${closeEngagedSeconds}")
    return closeEngagedSeconds
  }
  if (everyoneAway()) {
    logDebug("getTimeoutSeconds: away ${closeAwaySeconds}")
    return closeAwaySeconds
  }
  logDebug("getTimeoutSeconds: normal ${closeSeconds}")
  return closeSeconds
}

def closeDoorAfterTimeout() {
  if (doorControl.currentDoor == "closed") {
    return 
  }
  
  def timeoutSeconds = getTimeoutSeconds()
  def waitMillis = Math.max(atomicState.lastDoorChangeTime, atomicState.lastMotionTime) + timeoutSeconds * 1000 - new Date().getTime()
  logDebug("closeDoorAfterTimeout: wait ${waitMillis} millis")
  if (waitMillis <= 0) {
    atomicState.closeDoorTries = 3
    tryToCloseDoor()
  } else {
    def waitSeconds = Math.ceil(waitMillis / 1000f + 1).toInteger()
    logInfo("closeDoorAfterTimeout: wait ${waitSeconds} seconds to re-evaluate")
    runIn(waitSeconds, closeDoorAfterTimeout)
  }
}

def tryToCloseDoor() {
  def tries = atomicState.closeDoorTries
  logInfo("tryToCloseDoor: tries ${tries}, door ${doorControl.currentDoor}")
  if (doorControl.currentDoor == "closed") {
    logInfo("tryToCloseDoor: Door closed")
    return
  }
  if (tries == 0)  {
    logInfo("tryToCloseDoor: Unable to close door")
    if (notificationDevices) {
      notificationDevices.deviceNotification("ERROR: Unable to close garage door!")
    }
    return
  }
  
  if (notificationDevices) {
    notificationDevices.deviceNotification("Auto-closing garage door!")
  }
  doorControl.close()  
  
  atomicState.closeDoorTries--
  runIn(120, tryToCloseDoor)
}

//
// Helpers
//

// atomicState maps don't get updated if you try to update it directly
def updateAtomicStateMap(name, key, value) {
  def map = atomicState[name]
  map[key] = value
  atomicState[name] = map
}

//
// Debug
//

def logDebug(msg){
  if (debugLog == true) {
    log.debug "<b>${appVersion()}</b> ${msg}"
  }
}

def logInfo(msg){
  log.info "<b>${appVersion()}</b> ${msg}"
}

def logWarn(msg) {
  log.warn "<b>${appVersion()}</b> ${msg}"
}