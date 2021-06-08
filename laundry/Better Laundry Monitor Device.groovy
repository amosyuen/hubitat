/**
 *  Copyright 2020 Amos Yuen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */


metadata {
  definition (name: "Better Laundry Monitor Device", namespace: "amosyuen", author: "amosyuen") {
    capability "Refresh"
    capability "Switch"
    attribute "state", "String"
    attribute "stateHtml", "String"
    command "inactive"
    command "running"
    command "finished"
    command "opened"
  }

  simulator {
  }

  preferences {
  }
}

def refresh() {
    parent.updateChild()
}

def on() {
  if (!parent.isState(parent.STATE_RUNNING())) {
    parent.toRunning()
  }
}

def off() {
  if (parent.isState(parent.STATE_RUNNING())) {
    parent.toFinished()
  }
}

def inactive() {
  parent.toInactive()
}

def running() {
  parent.toRunning()
}

def finished() {
  parent.toFinished()
}

def opened() {
  parent.toOpened()
}
