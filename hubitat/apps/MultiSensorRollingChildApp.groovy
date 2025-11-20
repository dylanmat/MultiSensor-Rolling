import groovy.transform.Field

@Field static final String APP_VERSION = "0.2.5"
@Field static final String NAMESPACE = "dylanm.mra.child"
@Field static final String PARENT_NAMESPACE = "dylanm.mra"
@Field static final String PARENT_APP_NAME = "MultiSensor Rolling Average"
@Field static final String CHILD_DRIVER_NAMESPACE = "dylanm.mra"
@Field static final String CHILD_DRIVER = "MultiSensor Rolling Child"
@Field static final Integer MAX_SAMPLE_POINTS = 1000

definition(
    name: "MultiSensor Rolling Average Child",
    namespace: NAMESPACE,
    parent: "${PARENT_NAMESPACE}:${PARENT_APP_NAME}",
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
    migrateLegacyTimeframeSetting()
    dynamicPage(name: "mainPage", title: "Rolling Average Child", install: true, uninstall: true) {
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
            input "timeframeValue", "number", title: "Time frame amount", required: true, submitOnChange: true
            input "timeframeUnit", "enum", title: "Time frame unit", required: true, defaultValue: "minutes", options: [
                minutes: "Minutes",
                hours  : "Hours",
                days   : "Days"
            ]
            input "samplePoints", "number", title: "Data points to collect", required: true
            input "clearHistory", "bool", title: "Reset collected history?", defaultValue: false
            def intervalText = configuredIntervalText()
            if (intervalText) {
                paragraph "Samples will be taken every ${intervalText}."
            }
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

private void initialize() {
    if (!isConfigured()) {
        parent?.logWarn "Child ${app.getLabel() ?: app.name} is not fully configured"
        return
    }
    unschedule()
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
    state.nextSampleEpoch = null
    ensureChildDevice(cfg)
    scheduleHealthCheck()
    sampleNow()
}

private boolean isConfigured() {
    settings.sourceDevice && settings.sourceAttribute && timeframeMinutes() && timeframeMinutes() > 0 && samplePoints() && samplePoints() > 0
}

private Map buildConfig() {
    def device = settings.sourceDevice
    Long minutes = timeframeMinutes()
    Integer points = samplePoints()
    [
        label    : app.getLabel(),
        deviceId : device?.id as Long,
        attribute: settings.sourceAttribute as String,
        timeframe: minutes,
        points   : points,
        intervalSeconds: calculateIntervalSeconds(minutes, points)
    ]
}

void sampleNow() {
    Map cfg = state.childConfig
    if (!cfg) return
    state.history = trimHistory(state.history ?: [], cfg)
    collectSample(cfg)
    publishAverage(cfg)
    scheduleNextSample(cfg)
}

private void ensureChildDevice(Map cfg) {
    def child = getChildDevice(childDeviceId())
    if (!child) {
        child = addChildDevice(CHILD_DRIVER_NAMESPACE, CHILD_DRIVER, childDeviceId(), [name: cfg.label, label: cfg.label, isComponent: false])
        parent?.logInfo "Created child device ${cfg.label}"
    } else if (child.label != cfg.label) {
        child.label = cfg.label
    }
}

private void collectSample(Map cfg) {
    def device = settings.sourceDevice
    if (!device) {
        parent?.logWarn "Configured device ${cfg.deviceId} not found for ${app.getLabel()}"
        return
    }
    def stateValue = device.currentState(cfg.attribute)
    if (!stateValue) {
        parent?.logWarn "${device.displayName}.${cfg.attribute} has no current value to sample"
        return
    }
    BigDecimal numericValue = safeDecimal(stateValue.value)
    if (numericValue == null) {
        parent?.logWarn "Ignored non-numeric value '${stateValue.value}' for ${device.displayName}.${cfg.attribute}"
        return
    }
    List history = state.history ?: []
    history << [time: now(), value: numericValue, unit: stateValue.unit]
    state.history = trimHistory(history, cfg)
    parent?.logDebug "${app.getLabel()} sampled ${numericValue}${stateValue.unit ?: ''}"
}

private void scheduleNextSample(Map cfg) {
    Integer interval = cfg.intervalSeconds as Integer
    if (!interval || interval <= 0) {
        parent?.logWarn "Cannot schedule sampling without a valid interval"
        return
    }
    runIn(interval, "sampleNow", [overwrite: true])
    state.nextSampleEpoch = now() + (interval * 1000L)
    parent?.logDebug "${app.getLabel()} scheduled next sample in ${formatInterval(interval)}"
}

private void scheduleHealthCheck() {
    runEvery30Minutes("ensureSamplingActive")
}

void ensureSamplingActive() {
    Map cfg = state.childConfig
    if (!cfg) return
    Long expected = state.nextSampleEpoch as Long
    Integer interval = cfg.intervalSeconds as Integer
    Long graceMs = interval ? Math.max(60000L, (interval * 1000L).intdiv(2) as Long) : 60000L
    if (!expected || now() > (expected + graceMs)) {
        parent?.logWarn "${app.getLabel()} sampling schedule missing or stale; restarting sampling"
        unschedule("sampleNow")
        sampleNow()
    }
}

private void publishAverage(Map cfg) {
    def child = getChildDevice(childDeviceId())
    if (!child) return
    List history = state.history ?: []
    BigDecimal average = calculateAverage(history)
    String unit = history ? history.last().unit : null
    child.updateAverage(average, unit, cfg.attribute, history.size(), cfg.timeframe, cfg.intervalSeconds)
}

private List trimHistory(List history, Map cfg) {
    Long cutoff = now() - (cfg.timeframe as Long) * 60000L
    List trimmed = history.findAll { it.time >= cutoff }
    Integer configuredPoints = cfg.points as Integer
    Integer maxPoints = configuredPoints ? Math.min(configuredPoints, MAX_SAMPLE_POINTS) : null
    if (configuredPoints && configuredPoints > MAX_SAMPLE_POINTS && !(state.maxPointsWarned as Boolean)) {
        parent?.logWarn "History limited to ${MAX_SAMPLE_POINTS} points to avoid exceeding the supported maximum"
        state.maxPointsWarned = true
    }
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
    BigDecimal amount = numericSetting("timeframeValue")
    if (!amount) {
        amount = numericSetting("timeframeMinutes")
    }
    if (!amount) return null
    String unit = (settings.timeframeUnit ?: "minutes").toString().toLowerCase()
    BigDecimal minutes
    switch (unit) {
        case "days":
            minutes = amount * 1440G
            break
        case "hours":
            minutes = amount * 60G
            break
        default:
            minutes = amount
            break
    }
    Math.max(1L, Math.round(minutes.doubleValue()))
}

private Integer samplePoints() {
    BigDecimal value = numericSetting("samplePoints")
    if (!value) return null
    Integer rounded = Math.max(1, Math.round(value.doubleValue()) as Integer)
    if (rounded > MAX_SAMPLE_POINTS) {
        if (!(state.maxPointsWarned as Boolean)) {
            parent?.logWarn "Requested ${rounded} sample points exceeds supported maximum ${MAX_SAMPLE_POINTS}; clamping to ${MAX_SAMPLE_POINTS}"
            state.maxPointsWarned = true
        }
        return MAX_SAMPLE_POINTS
    }
    state.maxPointsWarned = false
    rounded
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
    String timeframeText = formatTimeframe(cfg.timeframe as Long)
    String intervalText = cfg.intervalSeconds ? formatInterval(cfg.intervalSeconds as Integer) : null
    Long nextSampleMs = state.nextSampleEpoch as Long
    if (avg == null) {
        String waiting = "Waiting for samples for ${cfg.attribute}."
        if (nextSampleMs) {
            waiting += " Next sample in ${formatInterval(Math.max(1, ((nextSampleMs - now()) / 1000L) as Integer))}."
        } else if (intervalText) {
            waiting += " Next sample in ${intervalText}."
        }
        return waiting
    }
    String parts = "Average ${cfg.attribute}: ${avg}${unitText} across ${history.size()} samples"
    if (timeframeText) {
        parts += " (time frame: ${timeframeText}"
        if (intervalText) {
            parts += ", interval: ${intervalText}"
        }
        parts += ")"
    }
    if (nextSampleMs) {
        long remainingSeconds = ((nextSampleMs - now()) / 1000L) as Long
        parts += " Next sample in ${formatInterval(Math.max(1, remainingSeconds as Integer))}."
    }
    parts
}

private void migrateLegacyTimeframeSetting() {
    if (settings.timeframeValue != null || settings.timeframeMinutes == null) return
    BigDecimal legacy = numericSetting("timeframeMinutes")
    if (legacy != null) {
        app.updateSetting("timeframeValue", [value: legacy.toPlainString(), type: "number"])
    }
    if (!settings.timeframeUnit) {
        app.updateSetting("timeframeUnit", [value: "minutes", type: "enum"])
    }
    app.removeSetting("timeframeMinutes")
}

private BigDecimal numericSetting(String name) {
    def value = settings[name]
    if (value instanceof Number) {
        return new BigDecimal(value.toString())
    }
    if (value instanceof String && value.toString().trim().isNumber()) {
        return new BigDecimal(value.toString().trim())
    }
    null
}

private Integer calculateIntervalSeconds(Long timeframeMinutes, Integer points) {
    if (!timeframeMinutes || timeframeMinutes <= 0L || !points || points <= 0) return null
    double seconds = (timeframeMinutes * 60D) / points
    Math.max(1, Math.round(seconds) as Integer)
}

private String formatInterval(Integer seconds) {
    if (!seconds) return null
    if (seconds % 86400 == 0) {
        int days = seconds / 86400
        return "${days} day${days == 1 ? '' : 's'}"
    }
    if (seconds % 3600 == 0) {
        int hours = seconds / 3600
        return "${hours} hour${hours == 1 ? '' : 's'}"
    }
    if (seconds % 60 == 0) {
        int minutes = seconds / 60
        return "${minutes} minute${minutes == 1 ? '' : 's'}"
    }
    "${seconds} second${seconds == 1 ? '' : 's'}"
}

private String formatTimeframe(Long minutes) {
    if (!minutes) return null
    if (minutes % 1440 == 0) {
        long days = minutes / 1440
        return "${days} day${days == 1 ? '' : 's'}"
    }
    if (minutes % 60 == 0) {
        long hours = minutes / 60
        return "${hours} hour${hours == 1 ? '' : 's'}"
    }
    "${minutes} minute${minutes == 1 ? '' : 's'}"
}

private String configuredIntervalText() {
    Long minutes = timeframeMinutes()
    Integer points = samplePoints()
    Integer seconds = calculateIntervalSeconds(minutes, points)
    seconds ? formatInterval(seconds) : null
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
