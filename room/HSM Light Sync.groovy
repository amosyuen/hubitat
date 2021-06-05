/***********************************************************************************************************************
*
*  A smartapp to set lights based on HSM status and contact states
*
*  Copyright (C) 2020 amosyuen
*
*  License:
*  This program is free software: you can redistribute it and/or modify it under the terms of the GNU
*  General Public License as published by the Free Software Foundation, either version 3 of the License, or
*  (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
*  implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
*  for more details.
*
*  You should have received a copy of the GNU General Public License along with this program.
*  If not, see <http://www.gnu.org/licenses/>.
*
***********************************************************************************************************************/

definition (
	name: "HSM Light Sync",
	namespace: "amosyuen",
	author: "Amos Yuen",
	description: "Sync lights to HSM status and contact states",
	category: "Convenience",
	singleInstance: true,
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: ""
)

//
// Preferences
//

preferences	{
	page(name: "pageMain")
}

def pageMain()	{
	def appChildren = app.getChildApps().sort { it.label }
	dynamicPage(name: "pageMain", title: 'Contacts', install: true, uninstall: true, submitOnChange: true)	{
        section("<b>Name</b>") {
            label title: "Assign a name"
        }
        section("<b>Colors</b>")  {
			input "contactOpenHexColor", "color", title: "Contact Open Color", defaultValue: "#FBFF00", required: false
			input "intrusionHexColor", "color", title: "Intrusion Color", defaultValue: "#FF0000", required: false
			input "armingFailureHexColor", "color", title: "Arming Failure Color", defaultValue: "#CC00FF", required: false
			input "armedHexColor", "color", title: "Armed Color", defaultValue: "#FF7700", required: false
			input "armingHexColor", "color", title: "Arming Color", defaultValue: "#FF7700", required: false
			input "engagedHexColor", "color", title: "Room Engaged Color", defaultValue: "#00FF00", required: false
			input "neutralHexColor", "color", title: "Neutral Color", defaultValue: "#0000FF", required: false
		}
		section("<b>Rooms</b>") {
			app(name: "Room", appName: "HSM Light Sync Room", namespace: "amosyuen", title: "New Room to Light Mapping", multiple: true)
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
	
    subscribe(location, "hsmAlert", hsmAlertHandler)
    subscribe(location, "hsmStatus", hsmStatusHandler)
    
	state.status = getStatus()
    
	updateLights()
}

def hsmAlertHandler(evt) {
    logDebug("hsmAlertHandler: $evt.value")
	def alert
	switch(evt.value) {
		case "intrusion":
		case "intrusion-home":
		case "intrusion-night":
		case "smoke":
			alert = "intrusion"
            break
		case "arming":
			alert = "failed"
            break
        case "cancel":
            alert = null
            break
	}
	if (alert != state.alert) {
        logInfo("hsmAlertHandler: alert changed to $alert")
		state.alert = alert
        updateLights()
	}
}

def hsmStatusHandler(evt) {
	def status = getStatus()
    logDebug("hsmStatusHandler: $status")
	if (status != state.status) {
        logInfo("hsmStatusHandler: status changed to $status")
		state.status = status
		if (state.alert == null) {
			updateLights(getFailureHexColor())
		}
	}
}

def getStatus() {
    logDebug("getStatus: $location.hsmStatus")
	switch(location.hsmStatus) {
		case "armedAway":
		case "armedHome":
		case "armedNight":
			return "armed"
		case "armingAway":
		case "armingHome":
		case "armingNight":
			return "arming"
		case "disarmed":
		case "allDisarmed":
		default:
			return "disarmed"
	}
}

def updateLights(failureHexColor) {	
    logDebug("updateLights")
    getChildApps().each({child -> updateChildLights(child)})
}

def updateChildLights(child) {
    if (!child.lights) {
        return
    }
    def colorMap = getColorMap(getChildColor(child))
    logDebug("updateChildLights: set child ${child.label}(${child.id}) lights to ${colorMap}")
    child.lights.setColor(colorMap)
}

def getChildColor(child) {
	if (child.contactSensors && child.contactSensors.currentContact.contains("open")) {
        logDebug("getChildColor: ${child.label}(${child.id}) is contact open color ${contactOpenHexColor}")
        return contactOpenHexColor
    }
	def failureHexColor = getFailureHexColor()
	if (failureHexColor != null) {
        logDebug("getChildColor: ${child.label}(${child.id}) is a failure color ${failureHexColor}")
		return failureHexColor
	}
	def armingHexColor = getArmingHexColor(child)
	if (armingHexColor != null) {
        logDebug("getChildColor: ${child.label}(${child.id}) is arming color ${armingHexColor}")
		return armingHexColor
	}
	def occupancyHexColor = getOccupancyHexColor(child)
	if (occupancyHexColor != null) {
        logDebug("getChildColor: ${child.label}(${child.id}) is occupancy color ${occupancyHexColor}")
		return occupancyHexColor
	}
    logDebug("getChildColor: ${child.label}(${child.id}) is neutral ${neutralHexColor}")
    return neutralHexColor
}

def getFailureHexColor() {
	switch (state.alert) {
		case "intrusion":
			return intrusionHexColor
		case "failed":
			return armingFailureHexColor
	}
    return null
}

def getArmingHexColor(child) {
    if (!child.showArmingState) {
        return null
    }
    switch (state.status) {
        case "armed":
            return armedHexColor
        case "arming":
            return armingHexColor
    }
	return null
}

def getOccupancyHexColor(child) {
	if (child.room) {
		switch (child.room.currentOccupancy) {
			case "engaged":
				return engagedHexColor
		}
	}
	return null
}

def getColorMap(hexColor) {
    def red = Integer.parseInt(hexColor.substring(1,3), 16)
    def green = Integer.parseInt(hexColor.substring(3,5), 16)
    def blue = Integer.parseInt(hexColor.substring(5,7), 16)
    return rgbToHSV(red, green, blue)
}

// All values of HSL returned is from 0-100
def rgbToHSV(red, green, blue) {
    float r = red / 255f
    float g = green / 255f
    float b = blue / 255f
    float max = [r, g, b].max()
    float min = [r, g, b].min()
    def level = (100 * max).round()
    def hue = 0
    def saturation = 0
    if (max != min) {
        float hueFloat
        if (max == r) {
            hueFloat = (g - b) / (max - min)
        } else if (max == g) {
            hueFloat = (b - r) / (max - min) + 2
        } else {
            hueFloat = (r - g) / (max - min) + 4
        }
        hue = (hueFloat * 100 / 6).round()
        if (hue < 0) {
            hue += 100
        }
        
        float luminance = (min + max) / 2
        if (luminance < 0.5) {
            saturation = (max - min) / (max + min)
        } else {
            saturation = (max - min) / (2 - max - min)
        }
        saturation = (saturation * 100).round()
    }
    
    return [
        hue: hue.asType(int),
        saturation: saturation.asType(int),
        level: level.asType(int),
    ]
}


//
// Debug
//

def logDebug(msg){
  if (debugLog == true) {
    log.debug "${msg}"
  }
}

def logInfo(msg){
  log.info "${msg}"
}
