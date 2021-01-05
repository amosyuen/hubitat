/**
 *  Eight Sleep (Connect)
 *
 *  Copyright 2020 Amos Yuen, Alex Lee Yuk Cheung
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
 *	VERSION HISTORY 
 *  2.0 (2021-01-04) [Amos Yuen] - Cleaned up code and ported to hubitat
 *			- Changed code to show each side of each device as an option. Removed partner login, just use partner sharing instead. 
 *  1.1 (2019-09-07) [Amos Yuen] - Show partner device if metrics sharing is enabled. Delete devices when unselected.
 *	1.0b (2017-01-26) [Alex Cheung] - Token renew error fix.
 *	1.0 (2017-01-26) [Alex Cheung] - Remove BETA label.
 *
 *	1.0 BETA 6 (2017-01-19) [Alex Cheung] - Added notification framework with option screen.
 *	1.0 BETA 5 (2017-01-12) [Alex Cheung] - Stop partner credentials being mandatory. Change device creation based on whether partner credentials are present.
 *	1.0 BETA 4 (2017-01-12) [Alex Cheung] - Enable changing of SmartApp name.
 *	1.0 BETA 3b (2017-01-12) [Alex Cheung] - Remove single instance lock for users with multiple mattresses.
 *	1.0 BETA 3 (2017-01-12) [Alex Cheung] - Better messaging within smart app on login errors.
 *	1.0 BETA 2 (2017-01-11) [Alex Cheung] - Support partner account authentication and session management.
 *	1.0 BETA 1 (2017-01-11) [Alex Cheung] - Initial Release
 */
import groovy.transform.Field
@Field final Integer MAX_ACCESS_TOKEN_RENEW_ATTEMPTS = 5

definition(
    name: "Eight Sleep (Connect)",
    namespace: "amosyuen",
    author: "Amos Yuen",
    description: "Connect your Eight Sleep device to SmartThings",
    iconUrl: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/8slp-icon.png",
    iconX2Url: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/8slp-icon.png",
    iconX3Url: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/8slp-icon.png"
)

preferences {
	page(name:"firstPage", title:"Eight Sleep Device Setup", content:"firstPage", install: true)
    page(name: "credentialsPage")
    page(name: "selectDevicePage")
    page(name: "notificationsPage")
}

def firstPage() {
    return dynamicPage(name: "firstPage", title: "", install: true, uninstall: true) {
		def hasAuth = stateTokenPresent()
		section("<b>Credentials</b>") {
            href("credentialsPage",
				title: "Eight Sleep Account Credentials",
				description: hasAuth ? "Authenticated as " + username : "Unauthenticated",
				state: hasAuth)
        }

        if (username != null && password != null) {
            if (hasAuth) {
                section ("<b>Eight Sleep Devices</b>") {
                    href("selectDevicePage", title: "Eight Sleep Devices", description: devicesSelected() ? getDevicesSelectedString() : "No devices selected", state: devicesSelected())
                }
                section ("<b>Notifications</b>") {
                    href("notificationsPage", title: "Notifications", description: notificationsSelected() ? getNotificationsString() : "No notifications configured", state: notificationsSelected())
                }
                section ("<b>App Name</b>") {
                    label(name: "name", title: "App Name", required: true, state: (name ? "complete" : null), defaultValue: app.name)
                }
            } else {
                section {
                    paragraph("There was a problem connecting to Eight Sleep. Check your user credentials and error logs in SmartThings web console.\n\n${state.loginerrors}")
                }
            }
        }

		section("<b>Debug</b>") {
			input("debugLogging", "bool", title: "Log debug statements", defaultValue: true, submitOnChange: true)
			input("traceLogging", "bool", title: "Log trace statements", defaultValue: false, submitOnChange: true)
		}

        section { footerParagraph() }
    }
}

def credentialsPage() {
    dynamicPage(name: "credentialsPage", title: "Login", uninstall: false, install: false) {
        section { footerParagraph() }

        section("<h2>Eight Sleep Credentials</h2>") {
            input("username", "text", title: "Username", description: "Your Eight Sleep username (usually an email address)", required: true)
            input("password", "password", title: "Password", description: "Your Eight Sleep password", required: true, submitOnChange: true)	
        }    	

        if (username != null && password != null) {
            if (getEightSleepAccessToken()) {
                section {
                    paragraph("You have successfully connected to Eight Sleep.")
                }
            } else {
                section {
                    paragraph("There was a problem connecting to Eight Sleep. Check your user credentials and error logs.\n\n${state.loginerrors}")
                }
            }
        }

        section { footerParagraph() }
    }
}

def selectDevicePage() {
	updateDevicesAndSharedUsers()
				
	dynamicPage(name: "selectDevicePage", title: "Devices", uninstall: false, install: false) {
		section("<b>Eight Sleep Devices</b>") {
			paragraph("If your partner's side of the bed is not showing up, please have your partner share their profile and metrics.")

			input("selectedEightSleep",
				"enum",
				image: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/eightsleep-device.png",
				title:"Eight Sleep Devices \n(${state.eightSleepDevices.size() ?: 0} found)",
				multiple:true,
				required: false,
				options: state.eightSleepDevices)
		}

        section { footerParagraph() }
  	}
}

def notificationsPage() {
	dynamicPage(name: "notificationsPage", title: "Preferences", uninstall: false, install: false) {
    	section {
        	input("pushNotificationDevices", "capability.notification", title: "Push Notification Devices", multiple: true, required: false, submitOnChange: true)

            if (pushNotificationDevices) {
				input "onNotification", "bool", title: "Notify when Eight Sleep heat is on ", required: false, defaultValue: false
				input "offNotification", "bool", title: "Notify when Eight Sleep heat is off ", required: false, defaultValue: false
				input "inBedNotification", "bool", title: "Notify when 'In Bed' event occurs", required: false, defaultValue: false
            	input "outOfBedNotification", "bool", title: "Notify when 'Out Of Bed' event occurs", required: false, defaultValue: false
				input "asleepNotification", "bool", title: "Notify when 'Asleep' event occurs", required: false, defaultValue: false
				input "awakeNotification", "bool", title: "Notify when 'Awake' event occurs", required: false, defaultValue: false
            	input "heatLevelReachedNotification", "bool", title: "Notify when desired heat level reached", required: false, defaultValue: false
            	input "sleepScoreNotification", "bool", title: "Notify when latest sleep score is updated", required: false, defaultValue: false
            }			
		}   
		
        section { footerParagraph() }     
    }
}

def footerParagraph() {
	return paragraph("<hr><div style='text-align:center;font-size:14px;font-weight:bold'>${textVersion()}<br>${textCopyright()}</div>")
} 

def stateTokenPresent() {
	return state.eightSleepAccessToken != null
}

def devicesSelected() {
	return (selectedEightSleep) ? "complete" : null
}

def getDevicesSelectedString() {
	if (state.eightSleepDevices == null) {
    	updateDevicesAndSharedUsers()
  	}
    
	def listString = ""
	selectedEightSleep.each { childDevice ->
        if (state.eightSleepDevices[childDevice] != null) {
            listString += state.eightSleepDevices[childDevice] + "\n"
        }
  	}
  	return listString
}

def notificationsSelected() {
    return pushNotificationDevices &&
    	(onNotification || offNotification || inBedAwakeNotification || inBedAsleepNotification || outOfBedNotification ||
        	heatLevelReachedNotification || sleepScoreNotification) ? "complete" : null
}

def getNotificationsString() {
	def listString = ""
    if (pushNotificationDevices) { 
    	listString += "Send the following notifications to " + pushNotificationDevices
    }
    
    if (pushNotificationDevices) {
    	listString += ":\n"
        if (onNotification) listString += "• Eight Sleep On\n"
        if (offNotification) listString += "• Eight Sleep Off\n"
  		if (inBedNotification) listString += "• In Bed\n"
  		if (outOfBedNotification) listString += "• Out Of Bed\n"
  		if (asleepNotification) listString += "• Asleep\n"
  		if (awakeNotification) listString += "• Awake\n"
  		if (heatLevelReachedNotification) listString += "• Desired Heat Level Reached\n"
  		if (sleepScoreNotification) listString += "• Sleep Score\n"
    }
    if (listString != "") listString = listString.substring(0, listString.length() - 1)
    return listString
}

// App lifecycle hooks

def installed() {
	logger.debug("installed")
	initialize()
	// Check for new devices and remove old ones every 3 hours
	runEvery3Hours('updateDevices')
    // execute refresh method every minute
    runEvery5Minutes('refreshDevices')
}

// called after settings are changed
def updated() {
	logger.debug("updated")
	initialize()
    unschedule('refreshDevices')
    runEvery5Minutes('refreshDevices')
}

def uninstalled() {
	logger.info("Uninstalling, removing child devices...")
	unschedule()
	removeChildDevices(getChildDevices())
}

private removeChildDevices(devices) {
	devices.each {
		deleteChildDevice(it.deviceNetworkId) // 'it' is default
	}
}

// Implement event handlers
def presenceEventHandler(evt) {
	logger.debug("Executing 'presenceEventHandler' for ${evt.displayName}")
    if (evt.value == "not present") {
		if (outOfBedNotification) {
			sendMessage("${evt.displayName} is out of bed.", false)
		}
    }
	else if (evt.value == "present") {
		if (inBedNotification) {
			sendMessage("${evt.displayName} is in bed.", false)
		}
    }
}

def asleepEventHandler(evt) {
	logger.debug("Executing 'asleepEventHandler' for ${evt.displayName}")
	if (evt.value == true) {
		if (asleepNotification) {
			sendMessage("${evt.displayName} is asleep.", false)
		}
    }
	else if (evt.value == false) {
		if (awakeNotification) {
			sendMessage("${evt.displayName} is awake.", false)
		}
    }
}

def switchEventHandler(evt) {
	logger.debug("Executing 'switchEventHandler' for ${evt.displayName}")
	if (evt.value == "on") {
		if (onNotification) {
			sendMessage("${evt.displayName} is on.", false)
		}
    }
	else if (evt.value == "off") {
		if (offNotification) {
			sendMessage("${evt.displayName} is off.", false)
		}
	}
}
    
def heatLevelReachedEventHandler(evt) {
	logger.debug("Executing 'heatLevelReachedEventHandler' for ${evt.displayName}")
    if (evt.value == "true") {
		if (heatLevelReachedNotification) {
			sendMessage("${evt.displayName} has reached desired temperature.", false)
		}
	}
}
    
def sleepScoreEventHandler(evt) {
	logger.debug("Executing 'sleepScoreEventHandler' for ${evt.displayName}")
    if (sleepScoreNotification) {
        sendMessage("${evt.displayName} sleep score is ${evt.value}.", false)
    }
}

def sendMessage(msg) {
	logger.debug("sendMessage: msg=${msg}")
    if (pushNotificationDevices) {
		pushNotificationDevices.deviceNotification(msg)
	}
}

// called after Done is hit after selecting a Location
def initialize() {
	logger.debug("initialize")
    updateDevicesAndSharedUsers()
	if (selectedEightSleep) {
		createEightSleepDevicesIfNotExist()
    
        def devices = getChildDevices()
        devices.each {
            if (notificationsSelected() == "complete") {
                subscribe(it, "asleep", asleepEventHandler, [filterEvents: false])
                subscribe(it, "battery", sleepScoreEventHandler, [filterEvents: false])
                subscribe(it, "desiredHeatLevelReached", heatLevelReachedEventHandler, [filterEvents: false])
                subscribe(it, "presence", presenceEventHandler, [filterEvents: false])
                subscribe(it, "switch", switchEventHandler, [filterEvents: false])
            }
            logger.debug("Refreshing device $it.name")
            it.refresh()
        }
	}
}

def updateDevicesAndSharedUsers() {
	if (!state.devices) {
		state.devices = [:]
	}
	def user = apiGET("/users/me").user
  	def eightSleepDevices = [:]

    def selectedDevices = []
    if (user) {
		def sharingMetricsFrom = user.sharingMetricsFrom
        user.devices.each { device ->
            logger.debug("updateDevicesAndSharedUsers: Found Device ${device}")
			def result = apiGET("/devices/${device}?filter=ownerId,leftUserId,rightUserId").result
            def baseName = "Eight Sleep ${device.substring(device.length() - 4)}"
            def ownerUserId = result.ownerId
            def leftUserId = result.leftUserId
            if (leftUserId) {
                parseDeviceSide(sharingMetricsFrom, device, "left", leftUserId,
					baseName, ownerUserId, eightSleepDevices, selectedDevices)
            }
            def rightUserId = result.rightUserId
            if (rightUserId) {
                parseDeviceSide(sharingMetricsFrom, device, "right", rightUserId,
					baseName, ownerUserId, eightSleepDevices, selectedDevices)
            }
        }
    }
	state.eightSleepDevices = eightSleepDevices
   	logger.debug("updateDevicesAndSharedUsers: eightSleepDevices=${eightSleepDevices}")
   	logger.debug("updateDevicesAndSharedUsers: selectedDevices=${selectedDevices}")
    
    //Remove devices if does not exist on the Eight Sleep platform
    getChildDevices().findAll { !selectedDevices.contains("${it.deviceNetworkId}") }.each {
		logger.info("updateDevicesAndSharedUsers: Deleting ${it.deviceNetworkId}")
        try {
			deleteChildDevice(it.deviceNetworkId)
        } catch (hubitat.exception.NotFoundException e) {
        	logger.warn("updateDevicesAndSharedUsers: Could not find device ${it.deviceNetworkId}. Assuming manually deleted.")
        } catch (hubitat.exception.ConflictException ce) {
        	logger.warn("updateDevicesAndSharedUsers: Device ${it.deviceNetworkId} still in use. Please manually delete.")
        }
	} 
}

def parseDeviceSide(sharingMetricsFrom, device, side, userId, baseName, ownerUserId, eightSleepDevices, selectedDevices) {
	def key = "${device}/${side}/${userId}"
	def last4UserId = userId.substring(userId.length() - 4)
	def name = "${baseName} [${side.charAt(0).toUpperCase()}${side.substring(1)}]"
	if (userId == ownerUserId) {
		name += " [Owner ${last4UserId}]"
	} else if (sharingMetricsFrom.contains(userId)) {
		name += " [User ${last4UserId}]"
	} else {
		logger.info("getNameForDeviceSide: Device ${key} has unshared user id=${userId}")
		return null
	}
	eightSleepDevices[key] = name
	// Must use .any{ it = key} comparison. Key is a GStringImpl whereas selectedEightSleep is a
	// string and normal .contains() does not work
	if (selectedEightSleep && selectedEightSleep.any{it == key}) {
		selectedDevices.add(key)
	}
}

def createEightSleepDevicesIfNotExist() {
	selectedEightSleep.each { device ->
		def childDevice = getChildDevice(device)
		def name = state.eightSleepDevices[device]
		if (!childDevice) {
			def data = [name: name, label: name]
			childDevice = addChildDevice("amosyuen", "Eight Sleep Mattress", device, null, data)
			logger.info("createEightSleepDevicesIfNotExist: Added device id=${device}, name=${name}")
		} else {
			logger.info("createEightSleepDevicesIfNotExist: Found existing device id=${device}, name=${name}")
		}
	}
}

def refreshDevices() {
	logger.info("refreshDevices")
    atomicState.renewAttempt = 0
    atomicState.renewAttemptPartner = 0
	getChildDevices().each { device ->
    	logger.info("refreshDevices: Refreshing child device ${device.name}")
    	device.refresh()
    }
}

private def apiGET(path) {
	return makeHttpCall("httpGet", path)
}

private def apiPOST(path, body = [:], refreshToken = true) {
	return makeHttpCall("httpPost", path, body, refreshToken)
}

private def makeHttpCall(methodFn, path, body = [:], refreshToken = true) {
    logger.debug("makeHttpCall: methodFn=${methodFn},\npath=${path},\nbody=${body}")
	def headers = apiRequestHeaders(logger, refreshToken)
	def response
	try {
        "${methodFn}"([
			uri: "${apiUrl()}${path}",
			body: body,
            contentType: "application/json",
			headers: headers
		]) { response = it }
	} catch (groovyx.net.http.HttpResponseException e) {
		logger.error("makeHttpCall: HttpResponseException status=${e.statusCode}, body=${e.getResponse().getData()}", e)
		if (e.statusCode == 401) {
			// OAuth token is expired
			state.remove("eightSleepAccessToken")
			logger.warn("makeHttpCall: Access token is not valid")
		}
	} catch (java.net.SocketTimeoutException e) {
		logger.warn("makeHttpCall: Connection timed out", e)
	}
    
	if (response.status >= 400) {
		throw new Error("handleResponse: Error status=${response.status}, data=${response.data}")
	}
	logger.trace("handleResponse: status=${response.status}, data=${response.data}")
	return response.data
}

def apiUrl() { return "https://client-api.8slp.net/v1" }

Map apiRequestHeaders(logger, refreshToken = true) {
	if (refreshToken) {
   		def expirationTime = parseIsoTime(atomicState.expirationDate).getTime()
   		if (now() > expirationTime) {
			int renewAttempts = atomicState.renewAttempts
        	while (renewAttempts < MAX_ACCESS_TOKEN_RENEW_ATTEMPTS) {
            	logger.debug("apiRequestHeaders: Renewing access token attempt ${renewAttempts}")
        		if (getEightSleepAccessToken(logger)) {
					break
				}

				renewAttempts++
				atomicState.renewAttempts = renewAttempts
			}

			if (!state.eightSleepAccessToken) {
                def error = "apiRequestHeaders: Access token is invalid"
                logger.error(error)
				throw new Error(error)
			}
		}
	}
   
	return [
		"API-Key": "api-key",
		"Application-Id": "morphy-app-id",
		"Connection": "keep-alive",
		"User-Agent" : "Eight%20AppStore/11 CFNetwork/808.2.16 Darwin/16.3.0",
		"Accept-Language": "en-gb",
		"Accept-Encoding": "gzip, deflate",
		"Accept": "*/*",
		"app-Version": "1.10.0",
		"Session-Token": state.eightSleepAccessToken,
	]
}

def getEightSleepAccessToken(logger) {  
	def body = [ 
		"email": "${username}",
		"password" : "${password}"
	]
	def resp = apiPOST("/login", body, refreshToken=false)
    if (resp.status == 200) {
		state.eightSleepAccessToken = resp.data.session.token
        state.userId = resp.data.session.userId
        atomicState.expirationDate = resp.data.session.expirationDate
        logger.debug("getEightSleepAccessToken: eightSleepAccessToken=${resp.data.session.token}")
        logger.debug("getEightSleepAccessToken: eightSleepUserId=${resp.data.session.userId}")
        logger.debug("getEightSleepAccessToken: eightSleepTokenExpirationDate=${resp.data.session.expirationDate}")
        state.loginerrors = null
		return state.eightSleepAccessToken
	}

	logger.error("getEightSleepAccessToken: Error status=${resp.status}, data=${resp.data}")
	state.eightSleepAccessToken = null
	state.loginerrors = "Error:\nStatus: ${resp.status}\nData: ${resp.data}"
    return null
}

def parseIsoTime(time) {
	def dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
	return dateFormat.parse(time)
}

@Field final Map logger = [
	trace: { if (traceLogging) { log.trace(it) } },
	debug: { if (debugLogging) { log.debug(it) } },
	info: { log.info(it) },
	warn: { log.warn(it) },
	error: { log.error(it) },
]

private def textVersion() {
    return "Version: 2.0 - 2020-01-04"
}

private def textCopyright() {
    return "Copyright © 2021\nAmos Yuen, Alex Cheung"
}