# Zemismart Zigbee Blind README

# Prerequisites

You should follow the instructions that came with your device to set the open and close limits. Some models set the limits using the device, others require the remote. This driver cannot set limits. This is needed in order to open or close properly.

# Installation Options

* **[Recommended]** [Hubitat Package Manager](https://community.hubitat.com/t/beta-hubitat-package-manager/38016)
* Raw code https://github.com/amosyuen/hubitat/tree/main/zemismart

# Product Links

Links to purchase the Zemismart Zigbee Blind motor.

* [ZIGBEE 3.0 Smart Rope Motor Remote Control Voice Control Smart Blinds Engine By Mobile APP/Alexa/Google Smart Home Accessory|Automatic Curtain Control System| - AliExpress ](https://www.aliexpress.com/item/1005001775307474.html?spm=a2g0s.12269583.0.0.58264e2eZLRHif)
* [Zemismart Tuya Zigbee Roller Shade Driver Smart Motorized Chain Roller Blinds Alexa Google Assistant SmartThings Voice Control ](https://www.zemismart.com/products/zemismart-tuya-zigbee-roller-shade-driver-smart-motorized-chain-roller-blinds-alexa-google-assistant-smartthings-voice-control)

# Capabilities

* Open
* Close
* Stop
* Step Open
* Step Close
* Push Button (1 = Open, 2 = Close, 3 = Stop, 4 = Step Open, 5 = Step Close)
* Get and set direction
* Get and set position [0-100]
* Get and set mode (if supported by device)
* Get and set speed [0-100] (if supported by device)
* Configurable open and closed position thresholds
* Configurable default step amount

## Tested Devices

Has only been directly tested with devices listed blow. But since the code was adapted from a driver used with other Zemismart Zigbee Blind models, it likely will work with other Zemismart Zigbee Blind models.

### Has Speed / Mode Support

* AM43-0.45/40-ES-EZ

### No Speed / Mode Support

* ZM25TQ
    * You can unpair the remote by pressing the setup button in the remote control until you see number 5 and then pressing the up key according to @jcastrillo

# Google Home Integration

Recommended integrating with Google Home through hubitat [Google Home Community](https://community.hubitat.com/t/alpha-community-maintained-google-home-integration/34957) with the following settings:

* `Device type`: `Window Shade`
* `Google Home device type`: `Curtain`
* `Device traits`
	* `Open/Close`
		* Set `Open/Close attribute` to `windowShade`
		* Set `Open Position Command` to `setPosition`