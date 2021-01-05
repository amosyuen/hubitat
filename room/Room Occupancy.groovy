/***********************************************************************************************************************
*
*  A device handler to allow handling rooms as devices which have states for occupancy.
*
*  Copyright (C) 2021 Amos Yuen, Bangali
*
*  License:
*  This program is free software: you can redistribute it and/or modify it under the terms of the GNU
*  General Public License as published by the Free Software Foundation, either version 3 of the License, or
*  (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
*  implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
*  for more details.
*
*  You should have received a copy of the GNU General Public License along with this program.
*  If not, see <http://www.gnu.org/licenses/>.
*
*  Name: Rooms Occupancy
*  Modified by amosyuen
*  Source: https://github.com/adey/bangali/blob/master/devicetypes/bangali/rooms-occupancy.src/rooms-occupancy.groovy
*
***********************************************************************************************************************/


metadata {
  definition (name: "Room Occupancy", namespace: "amosyuen", author: "amosyuen") {
    capability "Actuator"
    capability "PushableButton"
    capability "Sensor"
    capability "Switch"
    attribute "contact", "enum", ['open', 'closed']
    attribute "motion", "enum", ['active', 'inactive']
    attribute "occupancy", "enum", ['occupied', 'engaged', 'checking', 'vacant']
    attribute "occupancyIcon", "String"
    attribute "buttonsMap", "String"
    attribute "light", "String"
    attribute "nobodyHasExited", "boolean"
    attribute "nightLight", "String"
    attribute "timeout", "String"
    command "push"
    command "engaged"
    command "occupied"
    command "checking"
    command "vacant"
    command "nightLightsOn"
    command "nightLightsOff"
    command "occupiedLightsOn"
    command "occupiedLightsOff"
  }

  simulator {
  }

  preferences {
  }
}

def installed() {
  initialize()
}

def updated() {
  initialize()
}

def initialize()  {
  if (parent) {
    parent.logDebug("initialize")
  }
  unschedule()
  
  sendEvent(name: "numberOfButtons", value: 4, descriptionText: "set number of buttons", displayed: true)
  sendEvent(name: "buttonsMap", value: "[1:occupied, 2:engaged, 3:checking, 4:vacant]", descriptionText: "mapping of buttons to occupancy states", displayed: true)
}

def on() {
  if (parent) {
    parent.logInfo("command on")
  }
  occupied()
  setSwitch(true)
}

def off() {
  if (parent) {
    parent.logInfo("command off")
  }
  vacant()
  setSwitch(false)
}

def setSwitch(on) {
  def value = on ? 'on' : 'off'
  sendEvent(name: "switch", value: value, descriptionText: "switch is ${value}", displayed: true)
}

def push(button)    {
  if (parent) {
    parent.logInfo("command push $button")
  }
  switch(button)    {
    case 1:
      occupied()
      break
    case 2:
      engaged()
      break
    case 3:
      checking()
      break
    case 4:
      vacant()
      break
    default:
      sendEvent(name: "pushableButton", value: button, descriptionText: "${device.displayName} button ${button} was pushed", displayed: true)
      break
  }
}

def occupied() {
  if (parent) {
    parent.logInfo("command occupied")
    parent.setTrigger("childFunctionCall")
    parent.occupied()
  }
}

def engaged() {
  if (parent) {
    parent.logInfo("command engaged")
    parent.setTrigger("childFunctionCall")
    parent.engaged()
  }
}

def checking() {
  if (parent) {
    parent.logInfo("command checking")
    parent.setTrigger("childFunctionCall")
    parent.checking()
  }
}

def vacant() {
  if (parent) {
    parent.logInfo("command vacant")
    parent.setTrigger("childFunctionCall")
    parent.vacant()
  }
}

def nightLightsOn()	{
  if (parent) {
    parent.logInfo("command nightLightsOn")
    parent.nightLightsOn()
  }
}

def nightLightsOff()	{
  if (parent) {
    parent.logInfo("command nightLightsOff")
    parent.nightLightsOff()
  }
}

def occupiedLightsOn()	{
	if (parent) {
    parent.logInfo("command occupiedLightsOn")
      parent.occupiedLightsOn()
    }
}

def occupiedLightsOff()	{
  if (parent) {
    parent.logInfo("command occupiedLightsOff")
    parent.occupiedLightsOff()
  }
}
