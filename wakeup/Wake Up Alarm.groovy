/**
 *  Licensed under the Apache License, Version 2.0 (the "License") you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	VERSION HISTORY 
 *	0.0.0 (2020-02-22) [Amos Yuen] - Initial Release
 */
import groovy.transform.Field

private def textVersion() {
	return "Version: 0.0.0 - 2020-02-22"
}

private def textCopyright() {
	return "Copyright © 2021 Amos Yuen"
}

definition(
	name: "Wake Up Alarm",
	namespace: "amosyuen",
	author: "Amos Yuen",
	description: "Gentle wake up alarm designed to work with Sleep with Android",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: ""
)

preferences {
	page(name: "mainPage", title: "Wake Up Alarm", install: true)
}

def mainPage() {
	return dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        section ("<b>App Name</b>") {
            label(name: "name", title: "App Name", required: true, defaultValue: app.name)
        }
        
        section ("<b>Lights</b>") {
            input("lights", "capability.switchLevel", title: "Lights to fade",
                  required: true, multiple: true)
        }
        
        section ("<b>Warmup</b>") {
            paragraph("Light fading before wake up")
            
            input("lightFadeSecondsWarmup", "number", title: "Warmup seconds to fade light",
                  required: true, min: 0, defaultValue: 900, submitOnChange: true)
			if (lightFadeSecondsStartWarmup > 0) {
				input("updateIntervalSecondsWarmup", "number", title: "Warmup update interval in seconds",
                      required: true, min: 1, defaultValue: 30)
			}

            input("lightLevelStartWarmup", "number", title: "Warmup light level to start fade",
                  required: true, min: 0, max: 100, defaultValue: 0)
			if (lightFadeSecondsStartWarmup > 0) {
            	input("lightLevelEndWarmup", "number", title: "Warmup light level to end fade",
                      required: true, min: 0, max: 100, defaultValue: 25)
			}

            input("lightColorTemperatureStartWarmup", "number", title: "Warmup light color temperature to start fade",
                  min: 0, defaultValue: 2700)
			if (lightFadeSecondsStartWarmup > 0) {
            	input("lightColorTemperatureEndWarmup", "number", title: "Warmup light color temperature to end fade",
                      min: 0, defaultValue: 2700)
			}
        }
        
        section ("<b>Wakeup</b>") {
            input("lightFadeSecondsStart", "number", title: "Wakeup seconds to fade light value",
                  required: true, min: 0, defaultValue: 180, submitOnChange: true)
			if (lightFadeSecondsStart > 0) {
				input("updateIntervalSeconds", "number", title: "Wakeup update interval in seconds",
                      required: true, min: 1, defaultValue: 5)
			}

            input("lightLevelStart", "number", title: "Wakeup light level to start fade",
                  required: true, min: 0, max: 100, defaultValue: 0)
			if (lightFadeSecondsStart > 0) {
            	input("lightLevelEnd", "number", title: "Wakeup light level to end fade",
                      required: true, min: 0, max: 100, defaultValue: 100)
			}

            input("lightColorTemperatureStart", "number", title: "Wakeup light temperature to start fade",
                  min: 0, defaultValue: 2700)
			if (lightFadeSecondsStart > 0) {
            	input("lightColorTemperatureEnd", "number", title: "Wakeup light temperature to end fade",
                      min: 0, defaultValue: 7000)
                
				input("snoozeFadeTimeRatio", "decimal", title: "Ratio to reduce fade light time on each snooze",
                      required: true, min: 0, max: 1, defaultValue: 0.5)
			}
        }

        section("<b>Debugging</b>") {
            input("debugLogging", "bool", title: "Log debug statements", defaultValue: true, submitOnChange: true)
            input("traceLogging", "bool", title: "Log trace statements", defaultValue: false, submitOnChange: true)
        }

		section { footerParagraph() }
	}
}

def footerParagraph() {
	return paragraph("<hr><div style='text-align:center;font-size:14px;font-weight:bold'>" +
			"${textVersion()}<br>${textCopyright()}</div>")
}

//
// App lifecycle hooks
//

@Field final String WARMUP = "warmup"
@Field final String STARTED = "started"
@Field final String SNOOZED = "snoozed"
@Field final String FINISHED = "finished"

def installed() {
	init()
}

def updated() {
	init()
}

def init() {
	logger.debug("init")
    unsubscribe()
    
	createChildDeviceIfNotExist()
    
    if (!atomicState.state) {
        setState(STOPPED)
        setSnoozeCount(0)
    }
    
	subscribe(lights, "switch.off", switchOffHandler)
	subscribe(level, "level", levelHandler)
}

//
// Child
//

def createChildDeviceIfNotExist() {
	createOrUpdateLabel("amosyuen", "Wake Up Alarm Device", getChildId(), app.label)
}

def createOrUpdateLabel(namespace, typeName, deviceNetworkId, label) {
    def child = getChildDevice(deviceNetworkId)
    if (!child) {
        logger.info("createOrUpdateLabel: creating child device namespace=${namespace}, typeName=${typeName}, deviceNetworkId=${deviceNetworkId}, label=${label}")
        addChildDevice(namespace, typeName, deviceNetworkId, /*hubId=*/ null, [name: deviceNetworkId, label: label, completedSetup: true])
    }
}

def getChildId() {
	return "wakeup_${app.id}"
}

//
// Event Handlers
//

def switchOffHandler(evt) {
    logger.debug("switchOffHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
    switchOff()
}

def levelHandler(evt) {
    logger.debug("levelHandler: ${evt.getDevice()} ${evt.name}=${evt.value}")
    def value = evt.value as int
    if (value == 0) {
        switchOff()
    }
}

def switchOff() {
    if (atomicState.state == STARTED) {
		setState(SNOOZED)
    	setSnoozeCount(atomicState.snoozeCount + 1)
    }
}

//
// Actions
//

def warmup() {
    logger.debug("warmup")
    unschedule()
    
    setState(WARMUP)
    setSnoozeCount(0)
	def millis = now()
	atomicState.startMillis = millis
	updateLightsWarmup(millis)
}

def start() {
    logger.debug("start")
    unschedule()
    if (atomicState.state != STARTED && atomicState.state != SNOOZED) {
    	setSnoozeCount(0)
    }
    
    setState(STARTED)
	def millis = now()
	atomicState.startMillis = millis
	updateLightsWakeup(millis)
}

def snooze() {
    logger.debug("snooze")
    unschedule()
	atomicState.startMillis = null
    
	if (atomicState.state == STARTED) {
    	setSnoozeCount(atomicState.snoozeCount + 1)
	}
    setState(SNOOZED)
    lights.setLevel(0)
}

def finish() {
    logger.debug("finish")
    unschedule()
	atomicState.startMillis = null
    
    setState(FINISHED)
    setSnoozeCount(0)
}

//
// Helpers
//

def setState(state) {
    atomicState.state = state
    
	def child = getChildDevice(getChildId())
	if (!child) {
		return
	}
	child.sendEvent(name: "state", value: state, displayed: true)
}

def setSnoozeCount(snoozeCount) {
    atomicState.snoozeCount = snoozeCount
    
	def child = getChildDevice(getChildId())
	if (!child) {
		return
	}
	child.sendEvent(name: "snoozeCount", value: snoozeCount, displayed: true)
}

def setFadeProgress(progress) {    
	def child = getChildDevice(getChildId())
	if (!child) {
		return
	}
	child.sendEvent(name: "fadeProgress", value: progress, displayed: true)
}

def updateLightsWarmup(millis = null) {
    logger.debug("updateLightsWarmup: millis=${millis}")
	if (!updateLights(lightFadeSecondsStartWarmup, lightLevelStartWarmup, lightLevelEndWarmup,
                      lightColorTemperatureStartWarmup, lightColorTemperatureEndWarmup,
					  updateIntervalSecondsWarmup, millis)) {
	    runIn(millis == null ? updateIntervalSecondsWarmup : Math.min(5, updateIntervalSecondsWarmup), updateLightsWarmup)
    }
}

def updateLightsWakeup(millis = null) {
    logger.debug("updateLightsWakeup: millis=${millis}")
	if (!updateLights(lightFadeSecondsStart, lightLevelStart, lightLevelEnd,
                      lightColorTemperatureStart, lightColorTemperatureEnd,
					  updateIntervalSeconds, millis)) {
	    runIn(millis == null ? updateIntervalSeconds : Math.min(5, updateIntervalSeconds), updateLightsWakeup)
    }
}

def updateLights(lightFadeSecondsStart, lightLevelStart, lightLevelEnd,
                 lightColorTemperatureStart, lightColorTemperatureEnd,
				 updateIntervalSeconds, millis = null) {
	def startMillis = atomicState.startMillis
	if (startMillis == null) {
        logger.debug("updateLights: cancelled")
		return false
	}
	if (millis != null) {
        logger.debug("updateLights: start")
		setLights(lightLevelStart, lightColorTemperatureStart)
    	if (lightFadeSecondsStart == 0) {
            logger.debug("updateLights: instant")
    		setFadeProgress(100)
    		return true
    	}
    	return false
	}

	millis = now()
	def fadeMillis = lightFadeSecondsStart * 1000 * Math.pow(snoozeFadeTimeRatio, atomicState.snoozeCount)
	def elapsedMillis = millis - startMillis
	def remainingMillis = Math.max(0, fadeMillis - elapsedMillis)
	def intervalSeconds = Math.min((updateIntervalSeconds + 1) as int, Math.ceil(remainingMillis / 1000.0) as int)
	def progress = Math.min(100, (100 * elapsedMillis / fadeMillis) as int)
	setFadeProgress(progress)

	def targetRatio = Math.min(1.0, (elapsedMillis + intervalSeconds * 1000) / fadeMillis)
	def level = ((1.0 - targetRatio) * lightLevelStart + targetRatio * lightLevelEnd) as int
	def colorTemperature = lightColorTemperatureStart
			? ((1.0 - targetRatio) * lightColorTemperatureStart + targetRatio * lightColorTemperatureEnd) as int
			: null
	setLights(level, colorTemperature, intervalSeconds)
        
    return progress == 100
}

def setLights(level, colorTemperature, intervalSeconds = 0) {
    logger.debug("setLights: level=${level}, colorTemperature=${colorTemperature}, intervalSeconds=${intervalSeconds}")
	for (it in lights) {
		if (it.currentLevel != level || (level > 0 && it.switch == "off")) {
			if (intervalSeconds == 0) {
				it.setLevel(level)
				logger.trace("setLights: Setting ${it} level to ${level}")
			} else {
				it.setLevel(level, intervalSeconds)
				logger.trace("setLights: Setting ${it} level to ${level} over ${intervalSeconds} seconds")
			}
		}
		if (colorTemperature && it.hasCommand('setColorTemperature')
				&& (it.currentColorMode != 'CT' || it.currentColorTemperature != colorTemperature)) {
			it.setColorTemperature(colorTemperature)
			logger.trace("setLights: Setting ${it} color temperature to ${colorTemperature}")
		}
	}
}

@Field final Map logger = [
	trace: { if (traceLogging) { log.trace(it) } },
	debug: { if (debugLogging) { log.debug(it) } },
	info: { log.info(it) },
	warn: { log.warn(it) },
	error: { log.error(it) },
]