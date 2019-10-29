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
        input "pollingInterval", "number", title: "업데이트간격 (in minutes).", description: "업데이트 간격", defaultValue:60, displayDuringSetup: false
        input type: "paragraph", element: "paragraph", title: "Version", description: version(), displayDuringSetup: false
    }

    tiles {
        valueTile("view", "view", decoration: "flat") {
            state "view", label:'${currentValue} Kg', icon:'st.Entertainment.entertainment15'
        }
        multiAttributeTile(name:"month", type: "generic", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute ("device.energy", key: "PRIMARY_CONTROL") {
                attributeState "energy", label:'이번 달\n${currentValue} Kg',  backgroundColors:[
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
    refresh()
}

def refresh() {
    log.debug "refresh()"
    unschedule()

    def pollTrashInterval = 60

    if ($settings.pollingInterval != null) {
        pollTrashInterval = Integer.parseInt($settings.pollingInterval)
    }

    log.debug "pollTrash pollingInterval $pollTrashInterval"

    schedule("0 $pollTrashInterval * * * ?", pollTrash)
}

def configure() {
    log.debug "Configuare()"
}

def pollTrash() {
    log.debug "pollTrash()"
    if (tagId && aptDong && aptHo) {

//        LocalDate today = LocalDate.now()
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
//        log.debug "First day: " + today.withDayOfMonth(1).format(formatter)
//        log.debug "Last day: " + today.withDayOfMonth(today.lengthOfMonth()).format(formatter)

        def params = [
            "uri" : "https://www.citywaste.or.kr/portal/status/selectDischargerQuantityQuickMonthNew.do",
            "contentType" : 'application/json',
            "headers" : [
                "HOST": "www.citywaste.or.kr"
            ],
            "requestContentType":"application/json",
            "body" : [
                tagprintcd : tagId,
                aptdong : aptDong,
                apthono : aptHo,
                startchdate :  "20191001",
                endchdate : "20191031",
                pageIndex : 1
            ]
        ]

        try {
            log.debug "request >> ${params}"

            httpPost(params) {resp ->
                resp.headers.each {
                    log.debug "${it.name} : ${it.value}"
                }
                // get the contentType of the response
                log.debug "response contentType: ${resp.contentType}"
                // get the status code of the response
                log.debug "response status code: ${resp.status}"
                if (resp.status == 200) {
                    // get the data from the response body
                    log.debug "response data: ${resp.data}"
                }
                else if (resp.status==429) log.debug "You have exceeded the maximum number of refreshes today"
                else if (resp.status==500) log.debug "Internal server error"
            }
        } catch (e) {
            log.error "error: $e"
        }
    }
    else log.debug "Missing settings tagId or aptDong or aptHo"
}