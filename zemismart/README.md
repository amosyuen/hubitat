# Zemismart Zigbee Blind README

# Prerequisites

You should set the open and close limits first using the manual. This is needed in order to open or close properly.

# Capabilities

* Open
* Close
* Pause
* Get and set direction
* Get and set position [0-100]
* Get and set mode (if supported by device)
* Get and set speed [0-100] (if supported by device)

## Tested Devices

* AM43-0.45/40-ES-EZ

Has not been directly tested on other Zemismart Zigbee Blind models, but since the code was adapted from a driver used with other Zemismart Zigbee Blind models, it likely will work.

# Google Home Integration

Recommended integrating with Google Home through hubitat [Google Home Community](https://community.hubitat.com/t/alpha-community-maintained-google-home-integration/34957) with the following settings:

* `Device type`: `Window Shade`
* `Google Home device type`: `Curtain`
* `Device traits`
	* `Open/Close`
		* Set `Open/Close attribute` to `windowShade`
		* Set `Open Position Command` to `setPosition`