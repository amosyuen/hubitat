# Eight Sleep README

# Capabilities

* Turn on or off
* Get and set current level
* Get and set target level for heating
* Get and set target level for cooling (if supported by bed)
* Get whether there is a person in the bed (up to an hour delayed)
* Get whether there is a person is asleep (up to an hour delayed)
* Get the bed temperature (only when in bed and up to an hour delayed)
* Get the room temperature (only when in bed and up to an hour delayed)
* Has the capabilities of a thermostat as this fits the bed model most closely
* Get push notifications on common events
* Polls the servers every `pollIntervalSeconds` seconds, where `pollIntervalSeconds` can be  set by the user. Defaults to every 5 minutes.

# Preferences

* You can create a child dimmer for controlling cooling
* You can create a child dimmer for controlling heating
* You can create a child presence for `inBed`
* You can create a child presence for `isAsleep`

# Support

Eight Sleep Pod Mattress supported, other mattresses untested, but probably should work.

# Usage

1. Install app and driver
1. Create instance of app
1. Sign in with eight sleep credentials
1. Choose beds to use in the app, it will create a device for each one

## Hubitat Dashboard

Recommend using a tile with `Thermostat` template.

## Google Home

### Settings

Recommended integration with Google Home, is to use hubitat [Google Home Community](https://community.hubitat.com/t/alpha-community-maintained-google-home-integration/34957) with the following settings:

* `Device type`: `Thermostat`
* `Google Home device type`: `Thermostat`
* `Device traits`
	* `Temperature Setting`
		* Set supported modes to `Off`, `Heat`, and `Cool` (only add cool if your mattress supports it)
		* Set `Minimum Set Point` to `-100` if cooling supported otherwise set it to `0`
		* Set `Maximum Set Point` to `100`
	* `On/Off`
		* Use default settings

### Interactions

The Google Home UI doesn't allow setting below 50 or above 90, but you can set it to anywhere from -100 to 100 using voice commands.

Voice Command | Action
---|---
turn on [device name] | turn device on
turn off [device name] | turn device off
set [device name] to cool<br>set [device name] to cold | set device to cool
set [device name] to heat<br>set [device name] to warm | set device to heat
heat [device name]<br>warm [device name] | increase target heat level by 3
cool [device name] | decrease target heat level by 3

WARNING: There is a bug currently with Google Home Community where you can't set the temperature if the device is off. See https://github.com/mbudnek/google-home-hubitat-community/issues/50. You can workaround this by first turning on the device.

### Alternatives

Another option is to use the child cool and heat dimmers, but those will show up as lights in Google Home. It can be annoying if you ask google to turn on lights and your bed turns on.

## Presence Detection and Temperature

In bed and is asleep presence detection is based on eight sleep hourly user data analysis, so will have up to an hour latency, but should be accurate on changes.

Bed and room temperature are only provided when a user is in bed and will have up to an hour latency.

### Suggested Actions

* Set location mode to `night` if `isAsleep` becomes `true` and location mode to `day` if `isAsleep` becomes `false`
* If `inBed` becomes `true` you know that someone is in the bedroom. If you use something like Room Manager you can set the bedroom to engaged state.
