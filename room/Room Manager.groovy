/***********************************************************************************************************************
*
*  A smartapp to manage rooms.
*
*  Copyright (C) 2021 Amos Yuen
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
*  Name: Room Manager
*
***********************************************************************************************************************/

definition (
	name: "Room Manager",
	namespace: "amosyuen",
	author: "Amos Yuen",
	description: "Manage Rooms",
	category: "Convenience",
	singleInstance: true,
	iconUrl: "https://cdn.rawgit.com/adey/bangali/master/resources/icons/roomOccupancy.png",
	iconX2Url: "https://cdn.rawgit.com/adey/bangali/master/resources/icons/roomOccupancy@2x.png",
	iconX3Url: "https://cdn.rawgit.com/adey/bangali/master/resources/icons/roomOccupancy@3x.png"
)

preferences	{
	page(name: "pageMain")
}

def pageMain()	{
	def appChildren = app.getChildApps().sort { it.label }
	dynamicPage(name: "pageMain", title: 'Rooms', install: true, uninstall: true, submitOnChange: true)	{
		section() {
			app(name: "Rooms", appName: "Room", namespace: "amosyuen", title: "New Room", multiple: true)
		}
	}
}
