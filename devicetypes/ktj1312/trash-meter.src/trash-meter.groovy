public static String version() { return "v0.0.1.20191029" }
/*
 *	2019/10/29 >>> v0.0.1.20191029 - first version
 */

metadata {
    definition (name: "Trash Meter", namespace: "ktj1312", author: "ktj1312") {
        capability "Body Weight Measurement"
        capability "Sensor"
        capability "Refresh"

        attribute "lastCheckin", "Date"

        command "refresh"
        command "pollTrash"
    }

    simulator {
    }

    preferences {
        input "tagId", "text", type: "text", title: "태그 ID", description: "카드에 적힌 TagID를 입력하세요", required: true
        input "aptDong", "text", title: "동", description: "아파트 동", required: true
        input "aptHo", "text", title: "호", description: "아파트 호", required: true
        input "under20Kg", "decimal", title: "20kg 이하 요금", defaultValue: 187, description: "20Kg 이하일 때 KG당 요금 기본값 : 187", required: false
        input "beteen20Kg", "decimal", title: "20kg ~30KG 요금", defaultValue: 280, description: "20Kg ~ 30KG 일 때 KG당 요금 기본값 : 280", required: false
        input "upper30Kg", "decimal", title: "30kg 이상 요금", defaultValue: 327, description: "30Kg 이상일 때 KG당 요금 기본값 : 327", required: false
        input name: "refreshRateMin", title: "Update time in every hour", type: "enum", options:[60 : "60", 120 : "120"], defaultValue: "60", displayDuringSetup: true
        input type: "paragraph", element: "paragraph", title: "Version", description: version(), displayDuringSetup: false
    }

    tiles {
        multiAttributeTile(name:"trashWeight", type: "generic", width: 6, height: 4) {
            tileAttribute ("device.trashWeight", key: "PRIMARY_CONTROL") {
                attributeState "device.trashWeight", label:'이번 달\n${currentValue} Kg',  backgroundColors:[
                        [value: 50, 		color: "#153591"],
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
        valueTile("fare", "device.charge", width: 2, height : 2, decoration: "flat") {
            state "fare", label:'${currentValue}\n원'
        }

        valueTile("refresh", "device.refresh", width: 2, height : 2, decoration: "flat") {
            state "refresh", label:'REFRESH', action: 'refresh.refresh'
        }
    }
}

// parse events into attributes
def parse(String description) {
    log.debug "Parsing '${description}'"
}

def installed() {
    refresh()
}

def uninstalled() {
    unschedule()
}

def updated() {
    log.debug "updated()"
    unschedule()

    def trashPollInterval = 60

    if ($settings != null && $settings.refreshRateMin != null) {
        trashPollInterval = Integer.parseInt($settings.refreshRateMin)
    }

    log.debug "trashPollInterval $trashPollInterval"

    schedule("0 0 0/1 * * ?", pollTrash)
}

def refresh() {
    log.debug "refresh()"

    pollTrash()
}

def configure() {
    log.debug "Configuare()"
}

def pollTrash() {
    log.debug "pollTrash()"
    if (tagId && aptDong && aptHo) {

        def sdf = new java.text.SimpleDateFormat("yyyyMMdd")
        Date now = new Date();
        Calendar calendar = Calendar.getInstance()

        calendar.setTime(now)

        // cal first day of month
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMinimum(Calendar.DAY_OF_MONTH))
        def firstDateStr = sdf.format(calendar.getTime())

        calendar.add(Calendar.MONTH, 1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.DATE, -1)

        def lastDateStr = sdf.format(calendar.getTime())

        log.debug "First day: " + firstDateStr
        log.debug "Last day: " + lastDateStr

        def pageIdx = 1

        def params = [
                "uri" : "https://www.citywaste.or.kr/portal/status/selectDischargerQuantityQuickMonthNew.do?tagprintcd=${tagId}&aptdong=${aptDong}&apthono=${aptHo}&startchdate=${firstDateStr}&endchdate=${lastDateStr}&pageIndex=${pageIdx}",
                "contentType" : 'application/json'
        ]

        try {
            log.debug "request >> ${params}"

            def respMap = getHttpGetJson(params)

            if(respMap != null){
                def pages = respMap.paginationInfo.totalPageCount

                log.debug "total pages >> ${pages}"

                def totalQty = 0
                for(def i = 1 ; i < pages + 1 ; i++){
                    def lists = respMap.list
                    for(def j=0;i<lists.size();i++){
                        totalQty += lists[i].qtyvalue

                        if (pages == 1)
                            break

                        pageIdx = pageIdx + 1

                        respMap = getHttpGetJson(params)
                    }
                }
                pageIdx = 1

                //def fare = cal_fare(totalQty)
                def fare = 0

                sendEvent(name: "lastCheckin", value: now)

                log.debug "weight ${totalQty} fare ${fare}"

                sendEvent(name: "trashWeight", value: totalQty)
                sendEvent(name: "charge", value: 11120)

            }
            else{
                log.error "result is null"
            }



        } catch (e) {
            log.error "error: $e"
        }




    }
    else log.debug "Missing settings tagId or aptDong or aptHo"
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
        log.warn "getHttpGetJson>> HTTP Get Error : ${e}"
    }

    return jsonMap

}

private cal_fare(weight){
    log.debug "start cal_fare weight is ${weight} fare late ~20Kg ${under20Kg} 20Kg~30Kg ${beteen20Kg} 30Kg~ ${upper30Kg}"

    def sum = 0

    sum = ${under20Kg} * 20

    log.debug sum
    return sum
}