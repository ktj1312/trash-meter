public static String version() { return "v0.0.3.20200523" }
/*
 *	2019/10/29 >>> v0.0.1.20191029 - first version
 *  2019/11/08 >>> v0.0.2 20191108 - weight bug fix thanks to dokyuim
 *  2020/05/23 >>> v0.0.3 20200523 - add displayed option
 */

metadata {
    definition (name: "Trash Meter", namespace: "ktj1312", author: "ktj1312") {
        capability "Body Weight Measurement"
        capability "Sensor"
        capability "Refresh"

        attribute "trashWeight", "number"
        attribute "trashFare", "number"
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
        input type: "paragraph", element: "paragraph", title: "Version", description: version(), displayDuringSetup: false
    }

    tiles {
        multiAttributeTile(name:"trashWeight", type: "generic", width: 6, height: 4) {
            tileAttribute ("device.weight", key: "PRIMARY_CONTROL") {
                attributeState "weight", label:'${currentValue} Kg\n이번달',  backgroundColors:[
                        [value: 5, 		color: "#DAF7A6"],
                        [value: 10, 	color: "#FFC300"],
                        [value: 15, 	color: "#FF5733"],
                        [value: 20, 	color: "#C70039"],
                        [value: 30, 	color: "#900C3F"]
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

        main (["trashWeight"])
    }
}

// parse events into attributes
def parse(String description) {
    log.debug "Parsing '${description}'"
}

def init(){
    if (settings.under20Kg == null || settings.under20Kg == "" ) settings.under20Kg = 187
    if (settings.beteen20Kg == null || settings.beteen20Kg == "" ) settings.beteen20Kg = 280
    if (settings.upper30Kg == null || settings.upper30Kg == "" ) settings.upper30Kg = 327

    refresh()
    schedule("0 0 0/1 * * ?", pollTrash)
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

    pollTrash()
}

def configure() {
    log.debug "Configuare()"
}

def pollTrash() {

    log.debug "pollTrash()"
    if (tagId && aptDong && aptHo) {

        def sdf = new java.text.SimpleDateFormat("yyyyMMdd")
        Date now = new Date()
        Calendar calendar = Calendar.getInstance()

        calendar.setTime(now)

        // cal first day of month
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMinimum(Calendar.DAY_OF_MONTH))
        def firstDateStr = sdf.format(calendar.getTime())

        calendar.add(Calendar.MONTH, 1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.DATE, -1)

        def lastDateStr = sdf.format(calendar.getTime())

        //log.debug "First day: " + firstDateStr
        //log.debug "Last day: " + lastDateStr

        def pageIdx = 1

        def params = [
                "uri" : "https://www.citywaste.or.kr/portal/status/selectDischargerQuantityQuickMonthNew.do?tagprintcd=${tagId}&aptdong=${aptDong}&apthono=${aptHo}&startchdate=${firstDateStr}&endchdate=${lastDateStr}&pageIndex=${pageIdx}",
                "contentType" : 'application/json'
        ]

        def fare = 0
        def totalQty = 0

        try {
            log.debug "request >> ${params}"

            def respMap = getHttpGetJson(params)

            if(respMap != null){
                if(respMap.totalCnt != 0){
                    def pages = respMap.paginationInfo.totalPageCount

                    log.debug "total pages >> ${pages}"

                    for(def i = 1 ; i < pages + 1 ; i++){
                        def lists = respMap.list

                        for(def j=0;j<lists.size();j++){
                            totalQty += lists[j].qtyvalue
                        }
                        if (pages == 1)
                            break

                        pageIdx = pageIdx + 1

                        respMap = getHttpGetJson(params)
                    }
                    pageIdx = 1
                }else{
                    log.debug "there is no data in this month"
                }
                fare = cal_fare(totalQty)

                log.debug "weight ${totalQty} fare ${fare}"

                sendEvent(name: "lastCheckin", value: now.format("yyyy MMM dd EEE h:mm:ss a", location.timeZone))
                sendEvent(name: "weight", value: totalQty, displayed: true)
                sendEvent(name: "charge", value: fare, displayed: true)
            }else{
                log.warn "retry to pollTrash cause server error try after 10 sec"
                runIn(10, pollTrash)
                //pollTrash()
            }
        } catch (e) {
            log.error "failed to update $e"
        }
    }
    else log.error "Missing settings tagId or aptDong or aptHo"
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

private cal_fare(weight){
    log.debug "cal_fare weight is ${weight} fare late ~20Kg ${under20Kg} 20Kg~30Kg ${beteen20Kg} 30Kg~ ${upper30Kg}"

    def sum = 0
    if (weight < 20){
        sum = settings.under20Kg * weight
    }
    else{
        sum = settings.under20Kg * 20
    }

    if (weight > 20){
        if (weight < 30){
            sum += settings.beteen20Kg * (weight - 20)
        }
        else{
            sum += settings.beteen20Kg * 10
        }
    }
    if (weight > 30){
        sum += settings.upper30Kg * (weight - 30)
    }
    return sum
}
