metadata {
    definition(
        name: "MultiSensor Rolling Child",
        namespace: "dylanm.mra",
        author: "MultiSensor-Rolling",
        singleThreaded: true,
        importUrl: "",
        iconUrl: "",
        iconX2Url: "",
        iconX3Url: ""
    ) {
        capability "Sensor"
        attribute "rollingAverage", "NUMBER"
        attribute "sampleCount", "NUMBER"
        attribute "attributeName", "STRING"
        attribute "timeFrameMinutes", "NUMBER"
    }
}

void updateAverage(BigDecimal value, String unit, String attributeName, Integer samples, Long timeframe) {
    if (value != null) {
        sendEvent(name: "rollingAverage", value: value, unit: unit)
    }
    sendEvent(name: "sampleCount", value: samples)
    sendEvent(name: "attributeName", value: attributeName)
    sendEvent(name: "timeFrameMinutes", value: timeframe)
}
