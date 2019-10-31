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

        def params = [
                "uri" : "https://www.citywaste.or.kr/portal/status/selectDischargerQuantityQuickMonthNew.do",
                "contentType" : 'application/x-www-form-urlencoded',
                "headers" : [
                        "Host": "www.citywaste.or.kr",
                        "Content-Type": "application/x-www-form-urlencoded",
                        "Accept": "*/*",
                        "Accept-Encoding": "gzip, deflate",
                        "Connection": "keep-alive",
                        "cache-control": "no-cache"
                ],
                "body" : [
                        tagprintcd : tagId,
                        aptdong : aptDong,
                        apthono : aptHo,
                        startchdate : firstDateStr ,
                        endchdate : lastDateStr,
                        pageIndex : 1
                ]
        ]

        try {
            log.debug "request >> ${params}"

            httpPost(params) {resp ->
                //resp.headers.each {
                //   log.debug "${it.name} : ${it.value}"
                // }
                // get the contentType of the response
                log.debug "response contentType: ${resp.contentType}"
                // get the status code of the response
                log.debug "response status code: ${resp.status}"

                if(resp.status == 200){
                    // get the data from the response body
                    log.debug "resp >> ${resp.data}"
                    def t = ${resp.data}.replace(":null","")
                    log.debug "tt >> ${t}"
                    def results = new groovy.json.JsonSlurper().parseText(${t})
                    log.debug "re >> ${results}"
                }

                else if (resp.status==429) log.debug "You have exceeded the maximum number of refreshes today"
                else if (resp.status==500) log.debug "Internal server error"
                else log.debug resp
            }
        } catch (e) {
            log.error "error: $e"
        }

        sendEvent(name: "trashWeight", value: 23)
        sendEvent(name: "lastCheckin", value: now)
        sendEvent(name: "charge", value: "1234")
    }
    else log.debug "Missing settings tagId or aptDong or aptHo"
}