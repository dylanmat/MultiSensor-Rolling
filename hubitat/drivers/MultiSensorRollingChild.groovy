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

preferences {
    input name: "logAttributeChanges", type: "bool", title: "Log attribute changes to info", defaultValue: false, required: false
}

void updateAverage(BigDecimal value, String unit, String attributeName, Integer samples, Long timeframe, Integer intervalSeconds) {
    if (value != null) {
        sendTrackedEvent(name: "rollingAverage", value: value, unit: unit)
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
            sendTrackedEvent(name: attr, value: value, unit: unit, descriptionText: description)
        }
    }
    sendTrackedEvent(name: "sampleCount", value: samples)
    sendTrackedEvent(name: "attributeName", value: attributeName)
    sendTrackedEvent(name: "timeFrameMinutes", value: timeframe)
    if (intervalSeconds != null) {
        sendTrackedEvent(name: "samplingIntervalSeconds", value: intervalSeconds)
    }
}

private void sendTrackedEvent(Map event) {
    if (!event?.name) return
    boolean changed = attributeChanged(event.name as String, event.value, event.unit?.toString())
    sendEvent(event)
    if (changed) {
        logAttributeChange(event)
    }
}

private boolean attributeChanged(String name, value, String unit) {
    def current = device?.currentState(name)
    if (!current) return true
    String newValue = value?.toString()
    String currentValue = current?.value?.toString()
    if (newValue != currentValue) return true
    unit && current?.unit?.toString() != unit
}

private void logAttributeChange(Map event) {
    if (!(settings.logAttributeChanges as Boolean)) return
    String description = event.descriptionText ?: "${device.displayName} ${event.name} is ${event.value}${event.unit ?: ''}"
    log.info description
}
