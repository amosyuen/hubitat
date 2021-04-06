# Epson Projector README

# Capabilities

* Turn on and off projector
* Get status
* Get and set source
* Get lamp runtime hours
* Poll projector every `X` seconds when off

# Turning On

You must set the `Standby Mode` to `Standby Mode: Communication On` or equivalent for your model so that the driver can turn on the projector when it is in standby. If you have this setup, you don't need to poll the projector every `X` seconds.

# Tested

Tested on models:

* Epson 5050UB Home Cinema

# Not Supported

Driver does not support projectors that require a password to access it over the network.
