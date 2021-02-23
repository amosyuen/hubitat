/**
 *	Copyright 2021 Amos Yuen
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *			http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 *	VERSION HISTORY 
 *	0.0.0 (2020-02-22) [Amos Yuen] - Initial Release
 */

metadata {
	definition (name: "Wake Up Alarm Device", namespace: "amosyuen", author: "amosyuen") {
	capability "Actuator"
	capability "Sensor"
		attribute "fadeProgress", "decimal"
		attribute "snoozeCount", "number"
		attribute "state", "string"
		command "warmup"
		command "start"
		command "snooze"
		command "finish"
	}
}

def warmup() {
	parent.warmup()
}

def start() {
	parent.start()
}

def snooze() {
	parent.snooze()
}

def finish() {
	parent.finish()
}