/**
 *  Eight Sleep (Connect)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License") you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	VERSION HISTORY 
 *	3.0.0 (2021-01-22) [Amos Yuen] - Remove heatLevelReached and notifications
 *	2.0.1 (2021-01-05) [Amos Yuen] - Fixed credentials page and notifications for bed events
 *	2.0 (2021-01-04) [Amos Yuen] - Cleaned up code and ported to hubitat
 *			- Changed code to show each side of each device as an option. Removed partner login, just use partner sharing instead. 
 *	1.1 (2019-09-07) [Amos Yuen] - Show partner device if metrics sharing is enabled. Delete devices when unselected.
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
@Field final Integer MAX_ACCESS_TOKEN_RENEW_ATTEMPTS = 3

private def textVersion() {
	return "Version: 3.0.0 - 2020-01-22"
}

private def textCopyright() {
	return "Copyright © 2021\nAmos Yuen, Alex Cheung"
}

definition(
	name: "Eight Sleep (Connect)",
	namespace: "amosyuen",
	author: "Amos Yuen",
	description: "Connect your Eight Sleep Devices",
	iconUrl: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/8slp-icon.png",
	iconX2Url: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/8slp-icon.png",
	iconX3Url: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/8slp-icon.png"
)

preferences {
	page(name: "mainPage", title: "Eight Sleep (Connect)", install: true)
	page(name: "credentialsPage")
	page(name: "selectDevicePage")
}

def mainPage() {
	return dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
		def hasAuth = state.eightSleepAccessToken != null
		section("<b>Credentials</b>") {
			href("credentialsPage",
				title: "Eight Sleep Account Credentials",
				description: hasAuth ? "Authenticated as " + username : "Unauthenticated",
				state: hasAuth)
		}

		if (username != null && password != null) {
			if (hasAuth) {
				section ("<b>Eight Sleep Devices</b>") {
					def devices = devicesSelected()
					href("selectDevicePage", title: "Eight Sleep Devices", description: devices ? getDevicesSelectedString() : "No devices selected", state: devices)
				}
				section ("<b>App Name</b>") {
					label(name: "name", title: "App Name", required: true, state: (name ? "complete" : null), defaultValue: app.name)
				}
			} else {
				section {
					paragraph(getLoginErrorFormat())
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
		section("<h2>Eight Sleep Credentials</h2>") {
			input("username", "text", title: "Username", description: "Your Eight Sleep username (usually an email address)", required: true)
			input("password", "password", title: "Password", description: "Your Eight Sleep password", required: true, submitOnChange: true)	
		}		

		if (username != null && password != null) {
			if (getEightSleepAccessToken()) {
				section {
					paragraph("Successfully connected to Eight Sleep.")
				}
			} else {
				section {
					paragraph(getLoginErrorFormat())
				}
			}
		}

		section { footerParagraph() }
	}
}

def getLoginErrorFormat(msg) {
	return "<b style='color:red'>Login Error</b>\nThere was a problem connecting to Eight Sleep:\n${state.loginErrors}"
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

def footerParagraph() {
	return paragraph("<hr><div style='text-align:center;font-size:14px;font-weight:bold'>${textVersion()}<br>${textCopyright()}</div>")
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

// App lifecycle hooks

def installed() {
	initialize()
}

// called after settings are changed
def updated() {
	initialize()
}

def initialize() {
	logger.debug("initialize")
	updateDevicesAndSharedUsers()
	if (selectedEightSleep) {
		createEightSleepDevicesIfNotExist()
	
		getChildDevices()*.refresh()
	}
}

def uninstalled() {
	logger.info("uninstalled: Removing child devices...")
	unschedule()
	removeChildDevices(getChildDevices())
}

private removeChildDevices(devices) {
	devices.each {
		deleteChildDevice(it.deviceNetworkId)
	}
}

// Device Management

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
	getChildDevices().each {
		if (selectedDevices.contains("${it.deviceNetworkId}")) {
			return
		}
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

// Http Calls

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
		logger.error("makeHttpCall: HttpResponseException status=${e.statusCode}, body=${e.getResponse().getData()}")
		if (e.statusCode == 401) {
			// OAuth token is expired
			state.remove("eightSleepAccessToken")
			logger.warn("makeHttpCall: Access token is not valid")
		}
		throw e
	} catch (java.net.SocketTimeoutException e) {
		logger.warn("makeHttpCall: Connection timed out", e)
		throw e
	}
	
	if (response.status >= 400) {
		logger.error("makeHttpCall: Error response status=${response.status}, data=${response.data}")
		throw new Exception("Error response status=${response.status}, data=${response.data}")
	}
	logger.trace("makeHttpCall: status=${response.status}, data=${response.data}")
	return response.data
}

def apiUrl() { return "https://client-api.8slp.net/v1" }

Map apiRequestHeaders(logger, refreshToken = true) {
	if (refreshToken) {
   		def expirationTime = parseIsoTime(atomicState.expirationDate).getTime()
   		if (now() > expirationTime) {
			for (i = 0; i < MAX_ACCESS_TOKEN_RENEW_ATTEMPTS; i++) {
				logger.debug("apiRequestHeaders: Renewing access token attempt ${i}")
				if (getEightSleepAccessToken(logger)) {
					break
				}
			}

			if (!state.eightSleepAccessToken) {
				logger.error("apiRequestHeaders: Access token is invalid")
				throw new Exception("Access token is invalid")
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

private def getEightSleepAccessToken(logger = logger) {  
	def body = [ 
		"email": "${username}",
		"password" : "${password}"
	]
	def session
	try {
		session = apiPOST("/login", body, refreshToken=false).session
	} catch (groovyx.net.http.HttpResponseException e) {
		state.eightSleepAccessToken = null
		if (e.statusCode == 400) {
			state.loginErrors = "Invalid login credentials. Please make sure that you are using the right email and password."
		} else {
			state.loginErrors = "Status: ${e.statusCode}\nData:${e.getResponse().getData()}"
		}
		return null
	} catch (Exception e) {
		state.eightSleepAccessToken = null
		state.loginErrors = "${e}"
		return null
	}
	state.eightSleepAccessToken = session.token
	state.userId = session.userId
	atomicState.expirationDate = session.expirationDate
	logger.debug("getEightSleepAccessToken: eightSleepAccessToken=${session.token}")
	logger.debug("getEightSleepAccessToken: eightSleepUserId=${session.userId}")
	logger.debug("getEightSleepAccessToken: eightSleepTokenExpirationDate=${session.expirationDate}")
	state.loginErrors = null
	return state.eightSleepAccessToken
}

// Helpers

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
