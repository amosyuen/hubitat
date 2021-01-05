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
* Has some of the capabilities of a thermostat (thermostate mode and heating and colling setpoints) as this fits the bed model most closely
* Polls the servers every `pollIntervalSeconds` seconds, where `pollIntervalSeconds` can be  set by the user. Defaults to every 5 minutes.

# Preferences

* You can create child dimmer for controlling cooling
* You can create child dimmer for controlling heating
* You can create child presence for in bed
* You can create child presence for is asleep

# Support

Eight Sleep Pod Mattress supported, other mattresses untested, but probably should work.

# Usage

1. Install app and driver
1. Create instance of app
1. Sign in with eight sleep credentials
1. Choose beds to use in the app, it will create a device for each one

## Hubitat Dashboard

Recommend using thermostat tile.

## Google Home Integration

I found the best integration with Google Home, is to use hubitat [Google Home Community](https://community.hubitat.com/t/alpha-community-maintained-google-home-integration/34957) and expose it as a thermostat with device traits `Tempereature Control`, `Temeprature Setting`, and `On/Off`. The Google Home UI doesn't allow setting below 50 or above 90, but you can set it to anywhere from -100 to 100 using voice.

If you don't like that you can use the child cool and heat dimmers, but those will show up as lights in Goolge Home.

## Presence Detection and Temperature

In bed and is asleep presence detection is based on eight sleep hourly user data analysis, so will have up to an hour latency, but should be accurate on changes.

Bed and room temperature are only provided when a user is in bed and will have up to an hour latency.
