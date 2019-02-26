metadata {
	definition(name: "Tasmota-Dimmer", namespace: "BrettSheleski", author: "Brett Sheleski", ocfDeviceType: "oic.d.light") {
		capability "Momentary"
		capability "Switch"
		capability "Switch Level"
	}

	// UI tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: '${name}', action: "momentary.push", icon: "st.Home.home30", backgroundColor: "#79b821"
				attributeState "off", label: '${name}', action: "momentary.push", icon: "st.Home.home30", backgroundColor: "#ffffff"
			}
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action: "setLevel"
            }
            tileAttribute ("brightnessLabel", key: "SECONDARY_CONTROL") {
                attributeState "Brightness", label: '${name}', defaultState: true
            }
		}

		valueTile("powerChannel", "powerChannel", width: 6, height: 1) {
			state "powerChannel", label: 'Channel ${currentValue}', backgroundColor: "#ffffff"
		}

		valueTile("gpio", "gpio", width: 6, height: 1) {
			state "gpio", label: 'GPIO ${gpio}', backgroundColor: "#ffffff"
		}

		main "switch"
		details(["switch", "powerChannel", "gpio", "levelSlider"])
	}

	preferences {
		section("Main") {
            input(name: "powerChannel", type: "number", title: "Power Channel", description: "", displayDuringSetup: true, required: true)
			input(name: "gpio", type: "number", title: "GPIO", description: "", displayDuringSetup: false, required: false)
		}
	}
}

def initializeChild(Map options){
	log.debug "OPTIONS: $options"

	sendEvent(name : "powerChannel", value: options["powerChannel"]);
	sendEvent(name : "gpio", value: options["gpio"]);
}

def on(){
	log.trace "Executing 'on'"
    setPower("on")
}

def off(){
	log.trace "Executing 'off'"
    setPower("off")
}

def push(){
	log.trace "Executing 'toggle'"
    setPower("toggle")
}

def setPower(power){
	log.debug "Setting power to: $power"

	def channel = device.latestValue("powerChannel")
	def commandName = "Power$channel";
	def payload = power;

	log.debug "COMMAND: $commandName ($payload)"

	def command = parent.createCommand(commandName, payload, "setPowerCallback");;

    sendHubCommand(command);
}

def setDimmer(value){
	log.debug "Setting Dimming to: $value"

	def commandName = "Dimmer";
	def payload = value;

	log.debug "COMMAND: $commandName $payload"

	def command = parent.createCommand(commandName, payload, "setPowerCallback");;

    sendHubCommand(command);
}


def setPowerCallback(physicalgraph.device.HubResponse response){
	
	def channel = device.latestValue("powerChannel")
	
	log.debug "Finished Setting power (channel: $channel), JSON: ${response.json}"

    def on = response.json."POWER${channel}" == "ON";

	if ("$channel" == "1"){
		// if this is channel 1, there may not be any other channels.
		// In this case the property of the JSON response is just POWER (not POWER1)
		on = on || response.json.POWER == "ON";
	}

    setSwitchState(on);
}

def updateStatus(status){

	// Device power status(es) are reported back by the Status.Power property
	// The Status.Power property contains the on/off state of all channels (in case of a Sonoff 4CH or Sonoff Dual)
	// This is binary-encoded where each bit represents the on/off state of a particular channel
	// EG: 7 in binary is 0111.  In this case channels 1, 2, and 3 are ON and channel 4 is OFF

	def powerMask = 0b0001;

	def powerChannel = device.latestValue("powerChannel");

	powerMask = powerMask << ("$powerChannel".toInteger() - 1); // shift the bits over 

	def on = (powerMask & status.Status.Power);

	setSwitchState(on);
}

def setSwitchState(on){
	log.debug "Setting switch to ${on ? 'ON' : 'OFF'}"

	sendEvent(name: "switch", value: on ? "on" : "off")
}

def setLevel(value) {
    log.trace "Executing setLevel $value"
    Map levelEventMap = buildSetLevelEvent(value)
    if (levelEventMap.value == 0) {
        setPower("off")
        // notice that we don't set the level to 0'
    } else {
        setDimmer("$value")
        sendEvent(levelEventMap)
    }
}

private Map buildSetLevelEvent(value) {
    def intValue = value as Integer
    def newLevel = Math.max(Math.min(intValue, 100), 0)
    Map eventMap = [name: "level", value: newLevel, unit: "%", isStateChange: true]
    return eventMap
}

def installed() {
    setLevel(100)
}
