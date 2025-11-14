import groovy.transform.Field

@Field static final String APP_VERSION = "0.1.5"
@Field static final String NAMESPACE = "dylanm.mra"
@Field static final String CHILD_DRIVER = "MultiSensorRollingChild"

definition(
    name: "MultiSensor Rolling Average Child",
    namespace: NAMESPACE,
    parent: "${NAMESPACE}.MultiSensorRollingApp",
    author: "MultiSensor-Rolling",
    description: "Calculate a rolling average for a selected sensor attribute.",
    category: "Convenience",
    singleThreaded: true,
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Rolling Average Child", uninstall: true) {
        section("Configuration") {
            label title: "Child device label", required: true
            input "sourceDevice", "capability.sensor", title: "Device to monitor", required: true, submitOnChange: true
            def attrOptions = getNumericAttributes(settings.sourceDevice)
            if (settings.sourceDevice && !attrOptions) {
                paragraph "Selected device does not expose numeric attributes."
            }
            if (attrOptions) {
                input "sourceAttribute", "enum", title: "Attribute", options: attrOptions, required: true
            } else {
                app.removeSetting("sourceAttribute")
            }
            input "timeframeMinutes", "number", title: "Time frame (minutes)", required: true
            input "samplePoints", "number", title: "Data points to collect", required: true
            input "clearHistory", "bool", title: "Reset collected history?", defaultValue: false
        }
        section("Status") {
            paragraph currentStatus()
        }
    }
}

def installed() {
    parent?.logInfo "Installed child ${app.getLabel()}"
    initialize()
}

def updated() {
    parent?.logInfo "Updated child ${app.getLabel()}"
    unsubscribe()
    unschedule()
    initialize()
}

def uninstalled() {
    parent?.logInfo "Uninstalled child ${app.getLabel()}"
    removeChildDevice()
}

void handleDeviceEvent(evt) {
    BigDecimal numericValue = safeDecimal(evt.value)
    if (numericValue == null) {
        parent?.logWarn "Ignored non-numeric value '${evt.value}' for ${evt.device}"
        return
    }
    Map cfg = state.childConfig
    if (!cfg) return
    List history = state.history ?: []
    history << [time: now(), value: numericValue, unit: evt.unit]
    state.history = trimHistory(history, cfg)
    parent?.logDebug "${app.getLabel()} average=${calculateAverage(state.history)} from ${state.history.size()} samples"
    publishAverage(cfg)
}

private void initialize() {
    if (!isConfigured()) {
        parent?.logWarn "Child ${app.getLabel() ?: app.name} is not fully configured"
        return
    }
    Map cfg = buildConfig()
    Map previous = state.childConfig ?: [:]
    if (settings.clearHistory) {
        state.history = []
        app.updateSetting("clearHistory", [value: "false", type: "bool"])
    }
    if (previous.deviceId != cfg.deviceId || previous.attribute != cfg.attribute) {
        state.history = []
    }
    state.childConfig = cfg
    state.history = trimHistory(state.history ?: [], cfg)
    ensureChildDevice(cfg)
    subscribeToSource(cfg)
    publishAverage(cfg)
}

private boolean isConfigured() {
    settings.sourceDevice && settings.sourceAttribute && timeframeMinutes() && timeframeMinutes() > 0 && samplePoints() && samplePoints() > 0
}

private Map buildConfig() {
    def device = settings.sourceDevice
    [
        label    : app.getLabel(),
        deviceId : device?.id as Long,
        attribute: settings.sourceAttribute as String,
        timeframe: timeframeMinutes(),
        points   : samplePoints()
    ]
}

private void ensureChildDevice(Map cfg) {
    def child = getChildDevice(childDeviceId())
    if (!child) {
        child = addChildDevice(NAMESPACE, CHILD_DRIVER, childDeviceId(), [name: cfg.label, label: cfg.label, isComponent: false])
        parent?.logInfo "Created child device ${cfg.label}"
    } else if (child.label != cfg.label) {
        child.label = cfg.label
    }
}

private void subscribeToSource(Map cfg) {
    def device = settings.sourceDevice
    if (!device) {
        parent?.logWarn "Configured device ${cfg.deviceId} not found for ${app.getLabel()}"
        return
    }
    subscribe(device, cfg.attribute, "handleDeviceEvent")
    parent?.logDebug "Subscribed ${app.getLabel()} to ${device.displayName}.${cfg.attribute}"
}

private void publishAverage(Map cfg) {
    def child = getChildDevice(childDeviceId())
    if (!child) return
    List history = state.history ?: []
    BigDecimal average = calculateAverage(history)
    String unit = history ? history.last().unit : null
    child.updateAverage(average, unit, cfg.attribute, history.size(), cfg.timeframe)
}

private List trimHistory(List history, Map cfg) {
    Long cutoff = now() - (cfg.timeframe as Long) * 60000L
    List trimmed = history.findAll { it.time >= cutoff }
    Integer maxPoints = cfg.points as Integer
    if (maxPoints && trimmed.size() > maxPoints) {
        trimmed = trimmed.takeRight(maxPoints)
    }
    trimmed
}

private BigDecimal calculateAverage(List history) {
    if (!history) return null
    (history.collect { it.value }.sum() / history.size())
}

private List<String> getNumericAttributes(device) {
    def attrs = device?.supportedAttributes?.findAll { attr ->
        String type = attr?.dataType?.toString()?.toLowerCase()
        type in ["number", "decimal"]
    }?.collect { it.name }?.unique()?.sort()
    attrs ?: []
}

private BigDecimal safeDecimal(value) {
    if (value == null) return null
    String str = value.toString().trim()
    if (!str.isNumber()) return null
    str.toBigDecimal()
}

private Long timeframeMinutes() {
    def value = settings.timeframeMinutes
    value instanceof Number ? value.toLong() : value?.toString()?.isNumber() ? value.toString().toBigDecimal().toLong() : null
}

private Integer samplePoints() {
    def value = settings.samplePoints
    value instanceof Number ? value.toInteger() : value?.toString()?.isNumber() ? value.toString().toBigDecimal().toInteger() : null
}

private String childDeviceId() {
    "msr-${app.id}"
}

private String currentStatus() {
    if (!state.childConfig) return "Not configured."
    List history = state.history ?: []
    BigDecimal avg = calculateAverage(history)
    String unit = history ? history.last().unit : ""
    Map cfg = state.childConfig
    String unitText = unit ? " ${unit}" : ""
    if (avg == null) {
        return "Waiting for samples for ${cfg.attribute}."
    }
    "Average ${cfg.attribute}: ${avg}${unitText} across ${history.size()} samples"
}

private void removeChildDevice() {
    def child = getChildDevice(childDeviceId())
    if (!child) return
    try {
        super.deleteChildDevice(child.deviceNetworkId)
    } catch (Exception ex) {
        parent?.logWarn "Failed to delete child device ${child.deviceNetworkId}: ${ex.message}"
    }
}
