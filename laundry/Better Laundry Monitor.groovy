
/**
 *  Copyright 2021 Amos Yuen, Jonathan Porter, Kevin Tierney, C Steele
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * 2.0 [Amos Yuen]
 *	  - Changed logic to properly wait for specified duration before state change
 *	  - Added ability to set activity level for accelerometer threshold
 *	  - Changed to specifying notification devices and settings in parent app
 */
public static String versionInfo() {
    return "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>" +
        "<div style='color:#1A77C9;text-align:center;font-weight:small;font-size:9px'>" +
        "Developed by: Amos Yuen, Jonathan Porter, Kevin Tierney, C Steele &copy;2020<br>Current Version: v2.0.0</div>"
}

definition(
    name: "Better Laundry Monitor",
    namespace: "amosyuen",
    author: "Amos Yuen, Johnathan Porter, Kevin Tierney, C Steele",
    description: "Monitor the laundry cycle and alert when it starts or finishes using a switch with powerMonitor capability or accelerometer. Also track if the laundry was opened using a contact sensor.",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    )

preferences {
     page (name: "mainPage")
     page (name: "notificationPage")
} 

def mainPage() {
	dynamicPage(name: "mainPage", install: true, uninstall: true) {
      	section ("<b>Child Apps</b>") {
            paragraph "Create an instance for each applicance you want to monitor"
            app(name: "instances", appName: "Better Laundry Monitor Instance", namespace: "amosyuen", title: "Create New Instance", multiple: true)
        }
        section ("<b>Notifications</b>") {
            href "notificationPage", title: "Notifications", description: "Ways to notify on state changes"
        }
        section {
            paragraph "${versionInfo()}"
        }
	}
}
 
def notificationPage() {
	dynamicPage(name: "notificationPage") {
		section (title: "<b>Notification Methods</b>") {
			input("pushNotificationDevices", "capability.notification", title: "Push Notification Devices",
                multiple: true, required: false, submitOnChange: true)
			input("speechNotificationDevices", "capability.speechSynthesis", title:"Speech Synthesis Devices",
                multiple: true, required: false, submitOnChange: true)
            if (speechNotificationDevices) {
			    input("speechEchoAnnouncement", "bool", title:"Use 'Echo Speaks' Play Announcement All (only need one echo device in above list)",
                    defaultValue: false, required: false)
            }
			input("musicPlayerDevices", "capability.musicPlayer", title:"Speech Synthesis Music Players",
                multiple: true, required: false, submitOnChange: true)
            if (speechNotificationDevices || musicPlayerDevices) {
			    input("blockSpeechNotificationSwitch", "capability.switch", title: "Switch to block speech synthesis if ON",
                    multiple: false, required: false)
            }
            if (pushNotificationDevices || speechNotificationDevices || musicPlayerDevices) {
			    input("notificationCount", "number", title:"Number of times to notify while contact sensor is closed",
                    min: 0, defaultValue: 2, required: false, submitOnChange: true)
                if (notificationCount > 0) {
    			    input("notificationRepeatDelay", "number", title:"Delay in seconds between the each announcements",
                        min: 1, defaultValue: 30, required: true)
                }
            }
		}
	}
}

def installed() {
	log.info "Installed with settings: ${settings}"
}

def updated() {
	log.info "Updated with settings: ${settings}"
}
