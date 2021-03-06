public static String version() { return "v0.0.1.20200523" }
/*
 *	2019/12/14 >>> v0.0.1.20191109 - first version
  *	2020/05/23 >>> v0.0.2.20200523 - add event displayed
 */

metadata {
    definition (name: "Apt Energy Meter", namespace: "ktj1312", author: "ktj1312") {
        capability "Sensor"
        capability "Energy Meter"
        capability "Refresh"

//        attribute "gasUsage", "number"
//        attribute "waterUsage", "number"
//        attribute "powerUsage", "number"
//        attribute "lastCheckin", "Date"

        command "refresh"
        command "pollEnergyInfo"
    }

    simulator {
    }

    preferences {
        input "host", "text", type: "text", title: "host", description: "apt server ip address", required: true
        input "userid", "text", type: "text", title: "userid", description: "userid", required: true
        input "address", "text", type: "text", title: "address", description: "address", required: true
        input "phoneuid", "text", type: "text", title: "phoneuid", description: "phoneuid", required: true
        input type: "paragraph", element: "paragraph", title: "Version", description: version(), displayDuringSetup: false
    }

    tiles(scale:2) {
        valueTile("view", "view", decoration: "flat") {
            state "view", label:'${currentValue} kWh', icon:'st.Entertainment.entertainment15'
        }

        multiAttributeTile(name:"energy_month", type: "generic", width: 6, height: 4) {
            tileAttribute ("device.energy", key: "PRIMARY_CONTROL") {
                attributeState "energy", label:'이번 달\n${currentValue} kWh',  backgroundColors:[
                        [value: 50, 	color: "#153591"],
                        [value: 100, 	color: "#1e9cbb"],
                        [value: 200, 	color: "#90d2a7"],
                        [value: 300, 	color: "#44b621"],
                        [value: 400, 	color: "#f1d801"],
                        [value: 500, 	color: "#d04e00"],
                        [value: 600, 	color: "#bc2323"]
                ]
            }
            tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
                attributeState("default", label:'Last Update: ${currentValue}', icon: "st.Health & Wellness.health9")
            }
        }
        valueTile("energy", "device.energy", width: 2, height : 2, decoration: "flat") {
            state "energy", label:'${currentValue}'
        }
        valueTile("energy_fare", "device.energy_fare", width: 2, height : 2, decoration: "flat") {
            state "energy_fare", label:'${currentValue}\n원'
        }

        valueTile("gas", "device.gas", width: 2, height : 2, decoration: "flat") {
            state "gas", label:'${currentValue}'
        }
        valueTile("gas_fare", "device.gas_fare", width: 2, height : 2, decoration: "flat") {
            state "gas_fare", label:'${currentValue}\n원'
        }

        valueTile("water", "device.water", width: 2, height : 2, decoration: "flat") {
            state "water", label:'${currentValue}'
        }
        valueTile("water_fare", "device.water_fare", width: 2, height : 2, decoration: "flat") {
            state "water_fare", label:'${currentValue}\n원'
        }

        valueTile("refresh", "device.refresh", width: 2, height : 2, decoration: "flat") {
            state "refresh", label:'REFRESH', action: 'refresh.refresh'
        }

        main (["view"])
        details (["energy_month","energy", "gas", "water", "energy_fare", "gas_fare", "water_fare", "refresh"])
    }
}

// parse events into attributes
def parse(String description) {
    log.debug "Parsing '${description}'"
}

def init(){
    refresh()
    schedule("0 0 0/1 * * ?", pollEnergyInfo)
}

def installed() {
    init()
}

def uninstalled() {
    unschedule()
}

def updated() {
    log.debug "updated()"
    unschedule()
    init()
}

def refresh() {
    log.debug "refresh()"

    pollEnergyInfo()
}

def configure() {
    log.debug "Configuare()"
}

def pollEnergyInfo() {

    if (userid && address && phoneuid && host) {
        pollGas()
        pollWater()
        pollEnergy()

        sendEvent(name: "lastCheckin", value: new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone))
    }
    else log.error "Missing settings host or userId or address or phoneuid"
}

def pollGas() {
    log.debug "pollGas()"

    def params = getRequestParam("gas")

    try {
        log.debug "request >> ${params}"

        def respMap = getHttpGetJson(params)

        def usage = respMap.result[0].myhome[getMonth()]
        def fare = cal_gas_fare(energy)

        sendEvent(name: "gas", value: usage, displayed: true)
        sendEvent(name: "gas_fare", value: fare, displayed: true)

    } catch (e) {
        log.error "failed to update $e"
    }
}

def pollWater() {
    log.debug "pollWater()"

    def params = getRequestParam("water")

    try {
        log.debug "request >> ${params}"

        def respMap = getHttpGetJson(params)

        def usage = respMap.result[0].myhome[getMonth()]
        def fare = cal_water_fare(energy)

        sendEvent(name: "water", value: usage, displayed: true)
        sendEvent(name: "water_fare", value: fare, displayed: true)

    } catch (e) {
        log.error "failed to update $e"
    }
}

def pollEnergy() {
    log.debug "pollEnergy()"

    def params = getRequestParam("energy")

    try {
        log.debug "request >> ${params}"

        def respMap = getHttpGetJson(params)

        def usage = respMap.result[0].myhome[getMonth()]
        def fare = cal_energy_fare(energy)

        sendEvent(name: "view", value: usage, displayed: true)
        sendEvent(name: "energy", value: usage, displayed: true)
        sendEvent(name: "energy_fare", value: fare, displayed: true)

    } catch (e) {
        log.error "failed to update $e"
    }
}

private getMonth(){
    java.util.Date date= new Date();
    Calendar cal = Calendar.getInstance();
    cal.setTime(date);
    return cal.get(Calendar.MONTH);
}

private getRequestParam(type){

    Date date = new Date()
    def df = new java.text.SimpleDateFormat("yyyy")
    def year = df.format(date)

    def params = [
            "uri" : "http://" + host + "/WizRemoteState/WizSmart_for_Web_ISAPI.dll/datasnap/rest/TsmWizSmart/spGetHistoryEnergyUsage/" + userid + "/" + address + "/" + phoneuid,
            "contentType" : 'application/json'
    ]

    if(type.equals("gas")){
        params.uri = params.uri + "/G/Monthly/" + year + "/10/7/17"
    }else if(type.equals("water")){
        params.uri = params.uri + "/W/Monthly/" + year + "/10/7/17"
    }else if(type.equals("energy")){
        params.uri = params.uri + "/E/Monthly/" + year + "/10/7/17"
    }else{
        log.error "undefined energy type " + type + " has been requested"
    }

    return params
}

private getHttpGetJson(param) {
    log.debug "getHttpGetJson>> params : ${param}"
    def jsonMap = null
    try {
        httpGet(param) { resp ->
            log.debug "getHttpGetJson>> resp: ${resp.data}"
            jsonMap = resp.data
        }
    } catch(groovyx.net.http.HttpResponseException e) {
        log.error "getHttpGetJson>> HTTP Get Error : ${e}"
    }

    return jsonMap

}

private cal_gas_fare(gas){

    def sum = 0

    return sum
}

private cal_water_fare(water){

    def sum = 0

    return sum
}

private cal_energy_fare(energy){

    def sum = 0

    return sum
}
