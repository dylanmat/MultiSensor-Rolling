import groovy.transform.Field

@Field static final String APP_VERSION = "0.2.5"
@Field static final String NAMESPACE = "dylanm.mra"
@Field static final String APP_NAME_BASE = "MultiSensor Rolling Average"
@Field static final String CHILD_APP_NAME = "MultiSensor Rolling Average Child"
@Field static final String CHILD_APP_NAMESPACE = "dylanm.mra.child"

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
}

def mainPage() {
    dynamicPage(name: "mainPage", title: APP_NAME_BASE, install: true, uninstall: true) {
        section("Configured rolling averages") {
            paragraph "Create and manage rolling averages using child apps."
            app(
                name: "childDevices",
                appName: CHILD_APP_NAME,
                namespace: CHILD_APP_NAMESPACE,
                title: "Add a new ${APP_NAME_BASE} child",
                multiple: true
            )
        }
        section("Debugging") {
            input "enableDebug", "bool", title: "Enable debug logging", defaultValue: false, submitOnChange: true
        }
        section("Version") {
            paragraph "App version: ${APP_VERSION}"
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
    scheduleDebugOff()
}

def uninstalled() {
    logInfo "Uninstalled"
}

private void scheduleDebugOff() {
    if (enableDebug) runIn(1800, "disableDebug")
}

void disableDebug() {
    logInfo "Debug logging disabled"
    app.updateSetting("enableDebug", [type: "bool", value: "false"])
}

boolean debugEnabled() {
    (settings.enableDebug as Boolean) == true
}

void logDebug(String msg) {
    if (debugEnabled()) log.debug "[MSR] ${msg}"
}

void logInfo(String msg) {
    log.info "[MSR] ${msg}"
}

void logWarn(String msg) {
    log.warn "[MSR] ${msg}"
}

void logError(String msg) {
    log.error "[MSR] ${msg}"
}

private void initialize() {
    scheduleDebugOff()
}
