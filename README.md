# MultiSensor Rolling Average

## Overview
MultiSensor Rolling Average is a Hubitat Elevation parent app that builds child devices representing rolling averages for numeric sensor attributes. Each child device mirrors a selected attribute from a source device and continuously maintains an average across a configurable time window.

## Installation
1. Install all Groovy files in Hubitat:
   - `hubitat/apps/MultiSensorRollingApp.groovy`
   - `hubitat/apps/MultiSensorRollingChildApp.groovy`
   - `hubitat/drivers/MultiSensorRollingChild.groovy`
2. Add the **MultiSensor Rolling Average** parent app from Hubitat's Apps Code section and then install it from the Apps list.

## Configuration
1. Enable the app and optionally toggle debug logging for deeper troubleshooting logs.
2. Use **Add a new MultiSensor Rolling Average child** to create a dedicated child app for each rolling average you need.
3. Within each child app provide:
   - **Child device label** – name for the new device that will display the rolling average.
   - **Device to monitor** – the sensor that generates the numeric attribute events.
   - **Attribute** – one of the device's numeric attributes (temperature, humidity, illuminance, etc.).
   - **Time frame (minutes)** – the total number of minutes of history to retain for averaging.
   - **Data points to collect** – maximum number of readings preserved within the time frame.
   - Optionally toggle **Reset collected history?** to clear stored samples after changing configuration.
4. Repeat the process to add as many rolling averages as needed. Each child app manages its own rolling history and child device.

The created child device exposes:
- `rollingAverage` – current average value, reported with the same unit as the source device.
- `sampleCount` – number of readings currently used for the average.
- `attributeName` – attribute being averaged.
- `timeFrameMinutes` – configured time frame for the rolling window.

## Debug Logging
Enable the debug logging toggle to surface additional information in the Hubitat logs. Debug logging automatically disables itself after 30 minutes to avoid excessive noise.

## Changelog
- **0.1.6** – Correct app and child metadata namespaces and parent linkage to ensure installation succeeds.
- **0.1.5** – Replace the deprecated create-child button with Hubitat child apps and add the dedicated child app code file.
- **0.1.4** – Restore compatibility with Hubitat's `appButton` signature so the Create child device button renders correctly.
- **0.1.3** – Restore compatibility with Hubitat's `appButton` signature so the Create child device button renders correctly.
- **0.1.2** – Fixed the create-child button invocation error preventing new rolling-average devices from being created.
- **0.1.1** – Resolved the create-child installation error and standardized the namespace to `dylanm.mra` for app and driver.
- **0.1.0** – Initial release supporting multiple rolling-average child devices with configurable numeric attributes, time frames, and sample counts.
