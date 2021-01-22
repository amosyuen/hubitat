/***********************************************************************************************************************
*
*  A smartapp to set lights based on HSM status and contact states. Designed for use with Room Manager.
*
*  Copyright (C) 2021 Amos Yuen
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

definition(
	name: "HSM Light Sync Room",
	namespace: "amosyuen",
	parent: "amosyuen:HSM Light Sync",
	author: "Amos Yuen",
	description: "DO NOT INSTALL DIRECTLY. Create instances using HSM Light Sync parent app.",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "")

//
// Preferences
//

preferences {
	page(name: "pageMain")
}

def pageMain() {
	dynamicPage(name: "pageMain", title: "Room Settings", install: true, uninstall: true) {
		section("")	{
			label title: "Assign a name"
			input "contactSensors", "capability.contactSensor", title: "Contact Sensors", multiple: true, required: false
			input "room", "capability.switch", title: "Room Device", required: false
			input "lights", "capability.colorControl", title: "Color Lights", multiple: true, required: true
			input "showArmingState", "bool", title: "Show arming state colors",  default: false, required: true
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
	unsubscribe()
	unschedule()
	
	subscribe(contactSensors, "contact", eventHandler)
	subscribe(room, "occupancy", eventHandler)
	parent.updated()
}

def eventHandler(evt) {
	parent.updateChildLights(this)
}