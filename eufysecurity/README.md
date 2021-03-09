# Eufy Security README

WARNING: Still in Alpha

# Capabilities

## Station

* Get and set guard mode
* Polls the servers every `pollIntervalSeconds` seconds, where `pollIntervalSeconds` can be  set by the user. Defaults to every 5 minutes.

    ### Tested Devices

    * HomeBase 2

## Doorbell

WARNING: Setting values doesn't work for wireless doorbells connected directly to homebase

* Turn on or off
* Get battery (wireless doorbell only)
* Get and set guard mode
* Get and set audio recording setting
* Get and set motion detection setting
* Get and set dection type setting
* Get and set dection sensitivity setting
* Get and set snooze
* Polls the servers every `pollIntervalSeconds` seconds, where `pollIntervalSeconds` can be  set by the user. Defaults to every 5 minutes.

    ### Tested Devices

    * Wired 2k Doorbell

## Cameras

WARNING: Setting values doesn't work for cameras connected directly to homebase

* Turn on or off
* Get battery
* Get and set audio recording setting
* Get and set motion detection setting
* Get and set dection type setting
* Get and set dection sensitivity setting
* Get and set power mode setting
* Get and set record clip length setting
* Get and set record retrigger interval setting
* Get and set snooze
* Polls the servers every `pollIntervalSeconds` seconds, where `pollIntervalSeconds` can be  set by the user. Defaults to every 5 minutes.

    ### Tested Devices

    * EufyCam2

I don't have other Eufy devices to test with, but app should work with most of the cameras and doorbells assuming they work similarly. Please let me know if it works for you for other devices not in the tested list.

# Known Issues
* Devices directly connected to the homebase such as security cameras and wireless doorbells are unable to set settings with this implementation. Still working on figuring out if there is a way to get around that as they use some obscure P2P protocol.

# Debugging
Steps to get helpful debugging info if things aren't working.

### Device not in list
If your device isn't showing up and is a doorbell / camera please do the following:
1. Enable trace logging in the app
1. In the app go to the page to list devices
1. In your logs for the app, look for the logged response right after a "get_devs_list" call. Please PM me that response and explain what devices aren't getting listed. If you see any errors, also send those to me.

### Device settings not showing up on device page
If your device settings are not showing on refresh, please do the following:
1. Enable trace logging in the device page
1. Click "Save Preferences"
1. Click refresh on the device page
1. In your logs for the device look for the logged response right after a "get_devs_list" call. Please PM me that response and explain what setting isn't getting set. If you see any errors, also send those to me.

### Changing device settings not working
**NOTE:** If the device is connected directly to the home base (all the outdoor cameras and wireless doorbells), this is currently expected.

If your device settings are not getting changed do the following:
1. Enable trace logging in the device page
1. Click "Save Preferences"
1. Click refresh on the device page
1. In your logs for the device look for the logged response right after a "upload_devs_params" call. Please PM me that response and explain what setting isn't getting set. If you see any errors, also send those to me.
