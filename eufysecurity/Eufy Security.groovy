/**
 *  Eufy Security
 *
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
 *	0.0.3 (2020-02-16) [Amos Yuen] Another fix for two factor auth.
 *		- Have code re-login if domain changed on login.
 *	0.0.2 (2020-02-16) [Amos Yuen]
 *		- Fix bug in setting API URL
 *		- Add option to reset base API URL
 *	0.0.1 (2020-02-15) [Amos Yuen] - Fix bug in requesting two factor auth code. Use domain returned in login for api endpoint.
 *	0.0.0 (2020-02-10) [Amos Yuen] - Initial Release
 */
import groovy.transform.Field

private def textVersion() {
	return "Version: 0.0.3 - 2020-02-16"
}

private def textCopyright() {
	return "Copyright © 2021 Amos Yuen"
}

definition(
	name: "Eufy Security",
	namespace: "amosyuen",
	author: "Amos Yuen",
	description: "Integration with Eufy Security",
	iconUrl: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/8slp-icon.png",
	iconX2Url: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/8slp-icon.png",
	iconX3Url: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/8slp-icon.png"
)

preferences {
	page(name: "mainPage", title: "Eufy Security", install: true)
	page(name: "credentialsPage")
	page(name: "selectDevicePage")
}

@Field static final String BASE_API_URL = "https://mysecurity.eufylife.com/api/v1"

def mainPage() {
	return dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        if (app.getInstallationState() == 'COMPLETE') {
    		def loggedIn = isLoggedIn()
    		section("<b>Credentials</b>") {
    			href("credentialsPage",
    				title: "Eufy Security Account Credentials",
    				description: loggedIn ? "Authenticated as " + email : "Unauthenticated",
    				state: loggedIn)
    		}
    
    		if (email != null && password != null) {
    			if (state.loginErrors) {
    				section {
    					paragraph(state.loginErrors)
    				}
    			} else {
    				section ("<b>Eufy Security Devices</b>") {
    					def devices = devicesSelected()
    					href("selectDevicePage", title: "Eufy Security Devices", description: devices ? getDevicesSelectedString() : "No devices selected", state: devices)
    				}
    				section ("<b>App Name</b>") {
    					label(name: "name", title: "App Name", required: true, state: (name ? "complete" : null), defaultValue: app.name)
    				}
    			}
    		}

		    section("<b>Debugging</b>") {
			    input("debugLogging", "bool", title: "Log debug statements", defaultValue: true, submitOnChange: true)
			    input("traceLogging", "bool", title: "Log trace statements", defaultValue: false, submitOnChange: true)
		    }
        } else {
		    section {
    		    paragraph("Must install app before configuring. Click Done button.")
            }
        }

		section { footerParagraph() }
	}
}

def credentialsPage() {
	dynamicPage(name: "credentialsPage", title: "Login", uninstall: false, install: false) {
		def isLoggedIn = state.authToken && !state.needAuthCode
        def isSet = email != null && password != null
		section("<h2>Eufy Security Credentials</h2>") {
			input("email", "text", title: "Email", required: true, submitOnChange: true)
			input("password", "password", title: "Password", required: true, submitOnChange: true)
            if (state.authToken && state.needAuthCode) {
                input("authCode", "text", title: "Two Factor Authentication Code", required: true, submitOnChange: true)
                input("requestAuthCode", "button", title: "Request Email Authentication Code")
            }
            input("login", "button", title: "Login")
		}

		if (isSet) {
            section {
                if (state.loginErrors) {
					paragraph(state.loginErrors)
                } else {
                    paragraph("Successfully connected to Eufy Security.")
				}
			}
		}

		section("<b>Debugging</b>") {
			input("resetApiUrl", "button", title: "Reset base API URL. Please re-login afterwards.")
		}

		section { footerParagraph() }
	}
}

def setLoginError(msg, type = "Login") {
    state.loginErrors = msg ? "<b style='color:red'>${type} Error</b>\n${new Date()}\n${msg}" : null
    if (msg) {
        logger.error("${type}: ${msg}")
    }
}

def selectDevicePage() {
	updateDeviceList()
				
	dynamicPage(name: "selectDevicePage", title: "Devices", uninstall: false, install: false) {
		section("<b>Eufy Security Devices</b>") {
			input("selectedDevices",
				"enum",
				title:"Eufy Security Devices \n(${state.devices.size() ?: 0} found)",
				multiple:true,
				required: false,
				options: state.devices)
		}

		section { footerParagraph() }
  	}
}

def footerParagraph() {
	return paragraph("<hr><div style='text-align:center;font-size:14px;font-weight:bold'>${textVersion()}<br>${textCopyright()}</div>")
}

def devicesSelected() {
	return (selectedDevices) ? "complete" : null
}

def getDevicesSelectedString() {
	if (state.devices == null) {
		updateDeviceList()
  	}
	
	def listString = ""
	selectedDevices.each { childDevice ->
		if (state.devices[childDevice] != null) {
			listString += state.devices[childDevice] + "\n"
		}
  	}
  	return listString
}

def appButtonHandler(buttonPressed) {
    switch (buttonPressed) {
        case "login":
            if (login(logger, /* login= */ true)) {
                initPushNotifications()
            }
            break
        case "requestAuthCode":
            requestAuthCode()
            break
		case "resetApiUrl":
			state.apiUrl = BASE_API_URL
			logger.info("appButtonHandler: Resetting base API URL to ${BASE_API_URL}")
			break
    }
}

// App lifecycle hooks

def installed() {
	initialize()
}

// called after settings are changed
def updated() {
	initialize()
}

def isLoggedIn() {
	return state.authToken && !state.needAuthCode
}

def initialize() {
	logger.debug("initialize")

    if (!state.apiUrl) {
        state.apiUrl = BASE_API_URL
    }
	if (!isLoggedIn()) {
		return
	}

	def deviceData = updateDeviceList(returnDeviceData: true)
    syncChildDevices(deviceData)
	getChildDevices()*.refresh()

    // Set timeout to refresh push notification credentials
    initPushNotifications()
}

def uninstalled() {
	logger.info("uninstalled: Removing child devices...")
	unschedule()
	getChildDevices().each {
		deleteChildDevice(it.deviceNetworkId)
	}
}

// Auth

def String getErrorMessage(data) {
	switch (data.code) {
		case 26050: return "Two factor authentication code already used."
		case 26051: return "Two factor authentication code expired."
		case 26052: return "Logins requires two factor authentication code."
		case 26053: return "Request for two factor authentication code was throttled by the server. Try again after 24 hours."
		case 26054: return "Invalid two factor authentication code."
		case 26055: return "Password error."
		case 100028: return "Login throttled by server. Try again after 24 hours."
		default: return "Error response code=${data.code}, data=${data}"
	}
}

private def login(logger = logger, loginScreen = false) {  
    logger.trace("login: loginScreen=${loginScreen}")
	def data
	try {
    	def body = [ email: email, password: password ]
        if (state.authToken && state.needAuthCode) {
            body.verify_code = authCode
        }
		data = makeHttpCall("httpPost", "/passport/login", body, /* refreshToken= */false)
        if (body.verify_code) {
            addTrustDevice()
            clearAuthCode()
        }
	} catch (groovyx.net.http.HttpResponseException e) {
		switch (e.statusCode) {
			case 400:
                setLoginError("Invalid login credentials. Please make sure that you are using the right email and password.")
                break
		    default:
			    setLoginError("Status: ${e.statusCode}\nData:${e.getResponse().getData()}")
                break
		}
		return false
	} catch (Exception e) {
        setLoginError("${e}")
		return false
	}
    
    def domainChange = false
    if (data.data.domain) {
        def newApiUrl = "https://${data.data.domain}/v1"
        if (newApiUrl != state.apiUrl) {
            state.apiUrl = newApiUrl
            logger.info("login: Changing base API URL to ${newApiUrl}")
            domainChange = true
        }
    }
    
    if (data.code != 0) {
        if (loginScreen && data.code == 26052) {
    		try {
                clearAuthCode()
    			state.needAuthCode = true
                setAuthToken(data)
    			requestAuthCode()
    		} catch (Exception e) {
    			setLoginError("Error requesting two factor auth code: ${e}")
    		}
            return false
    	}
        setLoginError(getErrorMessage(data))
        return false
    }
    
    if (domainChange) {
        // Need to re-login if domain changed
        return login(logger, loginScreen)
    }
    
	setLoginError(null)
    state.remove("needAuthCode")
    setAuthToken(data)
    
	return true
}

def addTrustDevice() {
    def body = [
        verify_code: authCode,
    ]
    makeHttpCallAndHandleCode("httpPost", "/app/trust_device/add", body, /* refreshToken= */false)
}

def setAuthToken(data) {
	state.userId = data.data.user_id
	state.authToken = data.data.auth_token
    def authTokenExpirationEpochMillis = data.data.token_expires_at * 1000L
	atomicState.authTokenExpirationEpochMillis = authTokenExpirationEpochMillis
	logger.debug("setAuthToken: userId=${state.userId}")
	logger.debug("setAuthToken: authToken=${state.authToken}")
	logger.debug("setAuthToken: authTokenExpirationEpochMillis=${authTokenExpirationEpochMillis}")
}

private def requestAuthCode() {
    // Type 0 = SMS
    // Type 1 = Push
    // Type 2 = Email
    def body = [ message_type: 2 ]
    apiPOST("/sms/send/verify_code", body)
    setLoginError("Requested email authentication code. Please login with email authentication code.")
}

private def clearAuthCode() {
    app.updateSetting("authCode", [type:"string", value: ""])
}

// Push Notifications

def registerPushService() {
    def body = [
    ]
	def data = fcmApiPOST("/installations", body)
    logger.warn("registerPushService: ${data}")
}

// Device Management

@Field static final Map DEVICE_TYPE = [
    0: "Station",
    1: "Camera",
    //2: "SENSOR",
    3: "Floodlight",
    4: "Camera E",
    5: "Doorbell",
    7: "Battery Doorbell",
    8: "Camera 2C",
    9: "Camera 2",
    //10: "MOTION_SENSOR",
    //11: "KEYPAD",
    14: "Camera 2 Pro",
    15: "Camera 2C Pro",
    16: "Battery Doorbell 2",
    30: "Indoor Camera",
    31: "Indoor Pan & Tilt Camera",
    32: "Solo Camera",
    33: "Solo Camera Pro",
    34: "Indoor Camera 1080",
    35: "Indoor Pan & Tilt Camera 1080"
    //50: "LOCK_BASIC",
    //51: "LOCK_ADVANCED",
    //52: "LOCK_BASIC_NO_FINGER",
    //53: "LOCK_ADVANCED_NO_FINGER",
]

def isStation(deviceType) {
    return deviceType == 0
}

def isDoorbell(deviceType) {
    switch (deviceType) {
        case 5:
        case 7:
        case 16:
            return true
    }
    return false
}

def isCamera(deviceType) {
    switch (deviceType) {
        case 1:
        case 3:
        case 4:
        case 8:
        case 9:
        case 14:
        case 15:
        case 30:
        case 31:
        case 32:
        case 33:
        case 34:
        case 35:
            return true
    }
    return false
}

def updateDeviceList(returnDeviceData = false) {
	logger.info("updateDeviceList")
	if (!state.devices) {
		state.devices = [:]
    }
	def deviceOptions = [:]
    def deviceData = [:]
    
	def hubs = apiPOST("/app/get_hub_list")
	logger.trace("updateDeviceList: hubs=${hubs}")
	hubs.each {
        def deviceType = DEVICE_TYPE[it.device_type]
        if (deviceType == null) {
            deviceType = "Type ${it.device_type}"
        }
        if (returnDeviceData) {
            deviceData[it.station_sn] = it
        }
        deviceOptions[it.station_sn] = "[${deviceType}] ${it.station_name}"
	}
    
	def devices = apiPOST("/app/get_devs_list")
	logger.trace("updateDeviceList: devices=${devices}")
	devices.each {
        def deviceType = DEVICE_TYPE[it.device_type]
        if (deviceType == null) {
            deviceType = "Type ${it.device_type}"
        }
        if (returnDeviceData) {
            deviceData[it.device_sn] = it
        }
        deviceOptions[it.device_sn] = "[${deviceType}] ${it.device_name}"
	}
	state.devices = deviceOptions
    
    return deviceData
}

def syncChildDevices(deviceData) {
	// Remove devices that are not selected
	getChildDevices().each {
		if (selectedDevices?.contains(it.deviceNetworkId)) {
			return
		}
		logger.info("updateDevices: Deleting unselected device ${it.deviceNetworkId}")
		try {
			deleteChildDevice(it.deviceNetworkId)
		} catch (hubitat.exception.NotFoundException e) {
			logger.warn("updateDevices: Could not find device ${it.deviceNetworkId}. Assuming manually deleted.")
		} catch (hubitat.exception.ConflictException ce) {
			logger.warn("updateDevices: Device ${it.deviceNetworkId} still in use. Please manually delete.")
		}
	}
    
	selectedDevices?.each { id ->
		def childDevice = getChildDevice(id)
		def name = state.devices[id]
		if (!childDevice) {
            def device = deviceData[id]
            def driver
            if (isStation(device.device_type)) {
                driver = "Station"
            } else if (isDoorbell(device.device_type)) {
                driver = "Doorbell"
            } else if (isCamera(device.device_type)) {
                driver = "Camera"
            } else {
                logger.error("createDevicesIfNotExist: Unsupported device type: ${device.device_type}")
                return
            }
            def data = [name: "Eufy Security ${driver}", label: name]
            childDevice = addChildDevice("amosyuen", "Eufy Security ${driver}", id, null, data)
			logger.info("createDevicesIfNotExist: Added device id=${id}, name=${name}")
		} else {
			logger.info("createDevicesIfNotExist: Found existing device id=${id}, name=${name}")
		}
	}
}

// Eufy Security Http Calls

private def apiGET(path) {
	return makeHttpCallAndHandleCode("httpGet", path)
}

private def apiPOST(path, body = [:]) {
	return makeHttpCallAndHandleCode("httpPost", path, body)
}

private def makeHttpCallAndHandleCode(methodFn, path, body = [:], refreshToken = true) {
    def data = makeHttpCall(methodFn, path, body, refreshToken)
	if (data.code != 0) {
        def errorMessage = getErrorMessage(data)
        logger.error("makeHttpCallAndHandleCode: ${errorMessage}")
		throw new Exception(errorMessage)
	}
    return data.data
}

private def makeHttpCall(methodFn, path, body = [:], refreshToken = true) {
    def uri = "${apiUrl()}${path}"
	def headers = apiRequestHeaders(logger, refreshToken)
	def response
	try {
	    logger.trace("makeHttpCall methodFn=${methodFn},\nuri=${uri},\nbody=${body},\nheaders=${headers}")
		"${methodFn}"([
			uri: uri,
			body: body,
            requestContentType: 'application/json',
			headers: headers
		]) { response = it }
	} catch (groovyx.net.http.HttpResponseException e) {
		logger.error("makeHttpCall: HttpResponseException uri=${uri} status=${e.statusCode}, body=${e.getResponse().getData()}")
		if (e.statusCode == 401) {
			// OAuth token is expired
			state.authToken = null
			logger.warn("makeHttpCall: Invalid access token. Need to login again. uri=${uri}")
		}
		throw e
	} catch (java.net.SocketTimeoutException e) {
		logger.warn("makeHttpCall: Connection timed out. uri=${uri}", e)
		throw e
	}
	
	if (response.status >= 400) {
		logger.error("makeHttpCall: Error response uri=${uri} status=${response.status}, data=${response.data}")
        throw new Exception("Error response uri=${uri} status=${response.status}, data=${response.data}")
	}
	logger.trace("makeHttpCall: uri=${uri} status=${response.status}, data=${response.data}")
	return response.data
}

def apiUrl() {
    if (!state.apiUrl) {
        state.apiUrl = BASE_API_URL
    }
    return state.apiUrl
}

Map apiRequestHeaders(logger, refreshToken = true) {
	if (refreshToken && (!state.authToken || now() > atomicState.authTokenExpirationEpochMillis)) {
		logger.debug("apiRequestHeaders: Renewing access token")
		if (!login(logger)) {
			logger.error("apiRequestHeaders: Access token is invalid")
			throw new Exception("Access token is invalid")
		}
	}
   
	def headers = [
        // Needed for registering a trusted device and identifying if the current device is a trusted device
        phone_model: "Hubitat",
        openudid: getHubUID(),
    ]
    if (state.authToken) {
		headers["x-auth-token"] = state.authToken
    }
    return headers
}

// Push Notifications

@Field static final String APP_PACKAGE = "com.oceanwing.battery.cam"
@Field static final String APP_ID = "1:348804314802:android:440a6773b3620da7"
@Field static final String APP_SENDER_ID = "348804314802"
@Field static final String APP_CERT_SHA1 = "F051262F9F99B638F3C76DE349830638555B4A0A"
@Field static final String FCM_PROJECT_ID = "batterycam-3250a"
@Field static final String GOOGLE_API_KEY = "AIzaSyCSz1uxGrHXsEktm7O3_wv-uLGpC9BvXR8"
@Field static final String AUTH_VERSION = "FIS_v2"

private def initPushNotifications() {
    logger.trace("initPushNotifications")
    return
    def refreshMillis = state.firebaseAuthToken ? Math.max(0, state.firebaseAuthTokenExpirationEpochMillis - now() - 500) : 0
    def refresh = false
    if (refreshMillis <= 500) {
        if (!loginFirebase()) {
            return false
        }
    } else {
        runInMillis(refreshMillis, "initPushNotifications")
    }
    refresh = refresh || !state.checkinAndroidId
    if (refresh && !checkinAndroid()) {
        return false
    }
    refresh = refresh || !state.gcmToken
    if (refresh && !registerGCM()) {
        return false
    }
    return true
}

private def loginFirebase() {  
    logger.trace("loginFirebase")
    def refresh = state.fid != null
    def uri = "https://firebaseinstallations.googleapis.com/v1/projects/${FCM_PROJECT_ID}/installations"
    	def body
        if (refresh) {
            body = [:]
            uri += "/${state.fid}/authTokens:generate"
        } else {
			body = [
				appId: APP_ID,
				authVersion: AUTH_VERSION,
				sdkVersion: "a:16.3.1",
			]
        }
	def headers = [
		"X-Android-Package": APP_PACKAGE,
		"X-Android-Cert": APP_CERT_SHA1,
		"x-goog-api-key": GOOGLE_API_KEY,
    ]
    if (state.firebaseRefreshToken) {
        headers.Authorization = "${AUTH_VERSION} ${state.firebaseRefreshToken}"
    }
	def response
	try {
	    logger.trace("loginFirebase \nuri=${uri},\nbody=${body},\nheaders=${headers}")
        httpPost([
			uri: uri,
			body: body,
            requestContentType: 'application/json',
			headers: headers
		]) { response = it }
	} catch (groovyx.net.http.HttpResponseException e) {
        if (e.statusCode == 401) {
	        state.fid = null
	        state.firebaseRefreshToken = null
	        state.firebaseAuthToken = null
	        state.firebaseAuthTokenExpirationEpochMillis = null
        }
		logger.error("loginFirebase: status=${e.statusCode}\ndata=${e.getResponse().getData()}")
		return false
	} catch (Exception e) {
        logger.error("loginFirebase: ${e}")
		return false
	}
    
    def data = response.data
    def authToken
    if (refresh) {
        authToken = data
    } else {
        authToken = data.authToken
		state.fid = data.fid
		state.firebaseRefreshToken = data.refreshToken
    }
    state.firebaseAuthToken = authToken.token
    state.firebaseAuthTokenExpirationEpochMillis = getExpiresInMs(authToken.expiresIn)
    logger.debug("loginFirebase: fid=${state.fid}")
    logger.debug("loginFirebase: fcmAuthToken=${state.firebaseAuthToken}")
    logger.debug("loginFirebase: fcmAuthTokenExpirationEpochMillis=${state.firebaseAuthTokenExpirationEpochMillis}")
    
	return true
}

private def getExpiresInMs(expiresIn) {
	if (expiresIn.endsWith("ms")) {
		return now() + Integer.parseInt(expiresIn.substring(0, expiresIn.size() - 2));
	}
	if (expiresIn.endsWith("s")) {
		return now() + Integer.parseInt(expiresIn.substring(0, expiresIn.size() - 1)) * 1000;
	}
	throw new Exception("Unknown expiresIn format: ${expiresIn}");
}

private def checkinAndroid() {  
    logger.trace("checkinAndroid")
	def response
	try {
    	def body = [
			checkin: [ lastCheckinMs: 0 ],
			locale: "en",
            version: 3,
        ]
		httpPost([
			uri: "https://android.clients.google.com/checkin",
			body: body,
            requestContentType: 'application/json',
		]) { response = it }
	} catch (groovyx.net.http.HttpResponseException e) {
        if (e.statusCode == 401) {
	        state.checkinAndroidId = null
	        state.checkinAndroidSecurityToken = null
        }
		logger.error("checkinAndroid: status=${e.statusCode}\ndata=${e.getResponse().getData()}")
		return false
	} catch (Exception e) {
        logger.error("checkinAndroid: ${e}")
		return false
	}
    
    def data = response.data
	logger.trace("checkinAndroid: status=${response.status}, data=${data}")
    state.checkinAndroidId = data.android_id
    state.checkinAndroidSecurityToken = data.security_token
	logger.debug("checkinAndroid: checkinAndroidId=${state.checkinAndroidId}")
	logger.debug("checkinAndroid: checkinAndroidSecurityToken=${state.checkinAndroidSecurityToken}")
    
	return true
}

private def registerGCM() {  
    logger.trace("registerGCM")
	def response
	try {
    	def body = [
            sender: APP_SENDER_ID,
            "X-Goog-Firebase-Installations-Auth": state.firebaseAuthToken,
            "X-gmp_app_id": APP_ID,
            app: APP_PACKAGE,
            device: state.checkinAndroidId,
        ]
        def headers = [
            Authorization: "AidLogin ${state.checkinAndroidId}:${state.checkinAndroidSecurityToken}",
        ]
		httpPost([
			uri: "https://android.clients.google.com/c2dm/register3",
			body: body,
            headers: headers,
            contentType: "application/x-www-form-urlencoded",
		]) { response = it }
	} catch (groovyx.net.http.HttpResponseException e) {
        if (e.statusCode == 401) {
	        state.gcmToken = null
        }
		logger.error("registerGCM: status=${e.statusCode}\ndata=${e.getResponse().getData()}")
		return false
	} catch (Exception e) {
		logger.error("registerGCM: ${e}")
		return false
	}
    
    def data = response.data
	logger.trace("registerGCM: status=${response.status}, data=${data}")
    if (data.Error) {
		logger.error("registerGCM: ${data.Error}")
		return false
    }
    state.gcmToken = data.token
	logger.debug("registerGCM: token=${state.gcmToken}")
    
	return true
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
