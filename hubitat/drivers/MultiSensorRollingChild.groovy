import groovy.transform.Field

@Field static final Set<String> SUPPORTED_ATTRIBUTES = [
    "temperature",
    "humidity",
    "illuminance",
    "ultravioletIndex",
    "power",
    "energy",
    "pressure",
    "voltage",
    "current"
] as Set<String>

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
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "IlluminanceMeasurement"
        capability "UltravioletIndex"
        capability "PowerMeter"
        capability "EnergyMeter"
        capability "PressureMeasurement"
        capability "VoltageMeasurement"
        capability "CurrentMeter"
        attribute "rollingAverage", "NUMBER"
        attribute "sampleCount", "NUMBER"
        attribute "attributeName", "STRING"
        attribute "timeFrameMinutes", "NUMBER"
        attribute "samplingIntervalSeconds", "NUMBER"
        SUPPORTED_ATTRIBUTES.each { attr ->
            attribute attr, "NUMBER"
        }
    }
}

void updateAverage(BigDecimal value, String unit, String attributeName, Integer samples, Long timeframe, Integer intervalSeconds) {
    if (value != null) {
        sendEvent(name: "rollingAverage", value: value, unit: unit)
        String attr = attributeName?.toString()?.trim()
        if (attr) {
            if (!device?.hasAttribute(attr) && !SUPPORTED_ATTRIBUTES.contains(attr)) {
                List warned = (state.warnedAttributes ?: []) as List
                if (!warned.contains(attr)) {
                    log.warn "Unsupported attribute '${attr}' requested for rolling average; sending custom event"
                    warned << attr
                    state.warnedAttributes = warned
                }
            }
            String description = "${device.displayName} rolling ${attr ?: 'value'} average is ${value}${unit ?: ''}"
            sendEvent(name: attr, value: value, unit: unit, descriptionText: description)
        }
    }
    sendEvent(name: "sampleCount", value: samples)
    sendEvent(name: "attributeName", value: attributeName)
    sendEvent(name: "timeFrameMinutes", value: timeframe)
    if (intervalSeconds != null) {
        sendEvent(name: "samplingIntervalSeconds", value: intervalSeconds)
    }
}
