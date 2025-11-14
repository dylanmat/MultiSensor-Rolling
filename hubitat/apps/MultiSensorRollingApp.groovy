import groovy.transform.Field
import java.util.UUID

@Field static final String APP_VERSION = "0.1.0"
@Field static final String CHILD_DRIVER = "MultiSensorRollingChild"
@Field static final String NAMESPACE = "com.msr"

definition(
    name: "MultiSensor Rolling Average",
    namespace: NAMESPACE,
    author: "MultiSensor-Rolling",
    description: "Create child devices that expose rolling averages for selected sensor attributes.",
    category: "Convenience",
    singleThreaded: true,
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "childConfigPage")
}

void appButtonHandler(String buttonName) {
    if (buttonName == "createChild") createChildFromInputs()
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "MultiSensor Rolling Average", install: true, uninstall: true) {
        section("Debugging") {
            input "enableDebug", "bool", title: "Enable debug logging", defaultValue: false, submitOnChange: true
        }
        section("Create rolling average child") {
            input "newChildLabel", "text", title: "Child device label", required: false
            input "newChildDevice", "capability.sensor", title: "Device to monitor", required: false, submitOnChange: true
            def attrOptions = getNumericAttributes(settings.newChildDevice)
            if (settings.newChildDevice && !attrOptions) {
                paragraph "Selected device does not expose numeric attributes."
            }
            if (attrOptions) {
                input "newChildAttribute", "enum", title: "Attribute", options: attrOptions, required: false
            } else {
                app.removeSetting("newChildAttribute")
            }
            input "newChildTimeframe", "number", title: "Time frame (minutes)", required: false
            input "newChildPoints", "number", title: "Data points to collect", required: false
            paragraph "Tap the button below to create a new child device using the provided settings."
            appButton "createChild", title: "Create child device"
        }
        section("Configured rolling averages") {
            if (!state.childConfigs) {
                paragraph "No rolling averages configured yet."
            } else {
                state.childConfigs.each { childId, cfg ->
                    def child = getChildDevice(childId)
                    href "childConfigPage",
                        title: child?.displayName ?: cfg.label,
                        description: childDescription(cfg),
                        params: [childId: childId]
                }
            }
        }
        section("Version") {
            paragraph "App version: ${APP_VERSION}"
        }
    }
}

def childConfigPage(params) {
    def childId = params.childId ?: state.activeChildConfig
    if (!childId) return mainPage()
    state.activeChildConfig = childId
    def cfg = state.childConfigs?.get(childId)
    def deviceSettingName = settingKey(childId, "device")
    def attributeSettingName = settingKey(childId, "attribute")
    def selectedDevice = settings[deviceSettingName] ?: getDeviceById(cfg?.deviceId as Long)
    def attrOptions = getNumericAttributes(selectedDevice)
    dynamicPage(name: "childConfigPage", title: cfg?.label ?: "Rolling Average") {
        section("Configuration") {
            input settingKey(childId, "label"), "text", title: "Child device label", defaultValue: cfg?.label, required: true
            input deviceSettingName, "capability.sensor", title: "Device to monitor", defaultValue: selectedDevice, required: true, submitOnChange: true
            if (attrOptions) {
                input attributeSettingName, "enum", title: "Attribute", options: attrOptions, defaultValue: cfg?.attribute, required: true
            } else {
                paragraph "Selected device does not expose numeric attributes."
            }
            input settingKey(childId, "timeframe"), "number", title: "Time frame (minutes)", defaultValue: cfg?.timeframe, required: true
            input settingKey(childId, "points"), "number", title: "Data points to collect", defaultValue: cfg?.points, required: true
            input settingKey(childId, "remove"), "bool", title: "Remove this rolling average?", defaultValue: false
        }
    }
}

def installed() {
    logInfo "Installed"
    initialize()
}

def updated() {
    logInfo "Updated"
    unschedule()
    unsubscribe()
    applyChildConfigChanges()
    initialize()
    scheduleDebugOff()
}

def uninstalled() {
    logInfo "Uninstalled"
}

private void initialize() {
    state.childConfigs = state.childConfigs ?: [:]
    state.history = state.history ?: [:]
    state.childConfigs.each { childId, cfg ->
        ensureChildDevice(childId, cfg)
        subscribeToSource(childId, cfg)
    }
}

private void createChildFromInputs() {
    def label = settingText("newChildLabel")
    def device = settings.newChildDevice
    String attribute = settings.newChildAttribute
    Long timeframe = settingNumber("newChildTimeframe")?.longValue()
    Integer points = settingNumber("newChildPoints")?.intValue()
    if (!label || !device || !attribute || !timeframe || !points) {
        logWarn "Cannot create child device; missing required values"
        return
    }
    def childId = "msr-${UUID.randomUUID()}"
    state.childConfigs = state.childConfigs ?: [:]
    state.history = state.history ?: [:]
    def config = [label: label, deviceId: device.id as Long, attribute: attribute, timeframe: timeframe, points: points]
    state.childConfigs[childId] = config
    state.history[childId] = []
    ensureChildDevice(childId, config)
    subscribeToSource(childId, config)
    clearNewChildInputs()
    logInfo "Configured new child ${label}"
}

private void clearNewChildInputs() {
    app.removeSetting("newChildLabel")
    app.removeSetting("newChildDevice")
    app.removeSetting("newChildAttribute")
    app.removeSetting("newChildTimeframe")
    app.removeSetting("newChildPoints")
}

private void subscribeToSource(String childId, Map cfg) {
    def device = getDeviceById(cfg.deviceId as Long)
    if (!device) {
        logWarn "Device ${cfg.deviceId} for child ${childId} not found"
        return
    }
    subscribe(device, cfg.attribute, "handleDeviceEvent")
    logDebug "Subscribed ${childId} to ${device.displayName}.${cfg.attribute}"
}

void handleDeviceEvent(evt) {
    def numericValue = safeDecimal(evt.value)
    if (numericValue == null) {
        logWarn "Ignored non-numeric value '${evt.value}' for ${evt.device}"
        return
    }
    state.childConfigs?.each { childId, cfg ->
        if (cfg.deviceId?.toString() == evt.deviceId?.toString() && cfg.attribute == evt.name) {
            recordDataPoint(childId, cfg, numericValue, evt.unit)
        }
    }
}

private void recordDataPoint(String childId, Map cfg, BigDecimal value, String unit) {
    def history = state.history ?: [:]
    List points = (history[childId] ?: []) as List
    Long nowMillis = now()
    points << [time: nowMillis, value: value]
    Long cutoff = nowMillis - (cfg.timeframe as Long) * 60000L
    points = points.findAll { it.time >= cutoff }
    Integer maxPoints = cfg.points as Integer
    if (maxPoints && points.size() > maxPoints) {
        points = points.takeRight(maxPoints)
    }
    history[childId] = points
    state.history = history
    def average = points ? (points.collect { it.value }.sum() / points.size()) : null
    logDebug "Child ${childId} average=${average} from ${points.size()} samples"
    def child = getChildDevice(childId)
    child?.updateAverage(average, unit, cfg.attribute, points.size(), cfg.timeframe)
}

private void ensureChildDevice(String childId, Map cfg) {
    def child = getChildDevice(childId)
    if (!child) {
        child = addChildDevice(NAMESPACE, CHILD_DRIVER, childId, [name: cfg.label, label: cfg.label, isComponent: false])
        logInfo "Created child device ${cfg.label} (${childId})"
    } else if (child.label != cfg.label) {
        child.label = cfg.label
    }
}

private void applyChildConfigChanges() {
    state.history = state.history ?: [:]
    Map<String, Map> updatedConfigs = [:]
    (state.childConfigs ?: [:]).each { childId, cfg ->
        if (settingBool(settingKey(childId, "remove"))) {
            deleteChild(childId)
            return
        }
        Map updated = buildConfigFromSettings(childId, cfg)
        if (!cfg?.equals(updated)) {
            state.history[childId] = []
        } else {
            state.history[childId] = state.history[childId] ?: []
        }
        updatedConfigs[childId] = updated
    }
    state.childConfigs = updatedConfigs
}

private Map buildConfigFromSettings(String childId, Map fallback) {
    String label = settingText(settingKey(childId, "label")) ?: fallback?.label
    def device = settings[settingKey(childId, "device")] ?: getDeviceById(fallback?.deviceId as Long)
    String attribute = settings[settingKey(childId, "attribute")] ?: fallback?.attribute
    Long timeframe = settingNumber(settingKey(childId, "timeframe"))?.longValue() ?: (fallback?.timeframe as Long)
    Integer points = settingNumber(settingKey(childId, "points"))?.intValue() ?: (fallback?.points as Integer)
    if (!device || !attribute || !timeframe || !points) {
        logWarn "Incomplete settings for child ${childId}; retaining previous configuration"
        return fallback
    }
    def config = [label: label, deviceId: device.id as Long, attribute: attribute, timeframe: timeframe, points: points]
    def child = getChildDevice(childId)
    if (child && child.label != label) child.label = label
    app.removeSetting(settingKey(childId, "remove"))
    return config
}

private void deleteChild(String childId) {
    def child = getChildDevice(childId)
    if (child) {
        deleteChildDevice(childId)
        logInfo "Deleted child device ${child.displayName}"
    }
    state.history?.remove(childId)
    app.removeSetting(settingKey(childId, "label"))
    app.removeSetting(settingKey(childId, "device"))
    app.removeSetting(settingKey(childId, "attribute"))
    app.removeSetting(settingKey(childId, "timeframe"))
    app.removeSetting(settingKey(childId, "points"))
    app.removeSetting(settingKey(childId, "remove"))
}

private String childDescription(Map cfg) {
    def device = getDeviceById(cfg.deviceId as Long)
    String deviceName = device?.displayName ?: "Device missing"
    return "${cfg.attribute} average over ${cfg.timeframe} minutes (${cfg.points} points) from ${deviceName}"
}

private List<String> getNumericAttributes(device) {
    def attrs = device?.supportedAttributes?.findAll { attr ->
        String type = attr?.dataType?.toString()?.toLowerCase()
        type in ["number", "decimal"]
    }?.collect { it.name }?.unique()?.sort()
    return attrs ?: []
}

private static String settingKey(String childId, String suffix) {
    "${suffix}_${childId}"
}

private boolean settingBool(String name) {
    settings[name] ? settings[name] as Boolean : false
}

private String settingText(String name) {
    settings[name] ? settings[name].toString().trim() : null
}

private Number settingNumber(String name) {
    settings[name] instanceof Number ? settings[name] : settings[name]?.toString()?.isNumber() ? settings[name].toString().toBigDecimal() : null
}

private BigDecimal safeDecimal(value) {
    if (value == null) return null
    String str = value.toString().trim()
    if (!str.isNumber()) return null
    str.toBigDecimal()
}

private void scheduleDebugOff() {
    if (enableDebug) runIn(1800, "disableDebug")
}

void disableDebug() {
    logInfo "Debug logging disabled"
    app.updateSetting("enableDebug", [type: "bool", value: "false"])
}

private void deleteChildDevice(String childId) {
    try {
        super.deleteChildDevice(childId)
    } catch (Exception ex) {
        logWarn "Failed to delete child ${childId}: ${ex.message}"
    }
}

private void logDebug(String msg) {
    if (enableDebug) log.debug "[MSR] ${msg}"
}

private void logInfo(String msg) {
    log.info "[MSR] ${msg}"
}

private void logWarn(String msg) {
    log.warn "[MSR] ${msg}"
}
