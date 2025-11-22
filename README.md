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
   - **Time frame amount** and **time frame unit** – select any combination of minutes, hours, or days to define how much history should be retained for the rolling window.
   - **Data points to collect** – maximum number of evenly spaced readings preserved within the time frame.
   - Optionally toggle **Reset collected history?** to clear stored samples after changing configuration.
4. Repeat the process to add as many rolling averages as needed. Each child app manages its own rolling history and child device.

The created child device exposes:
- The same attribute as the monitored device (for example `temperature`, `humidity`, `illuminance`, etc.) so automations can treat the child like a native sensor reporting the rolling average.
- `rollingAverage` – current average value, reported with the same unit as the source device.
- `sampleCount` – number of readings currently used for the average.
- `attributeName` – attribute being averaged.
- `timeFrameMinutes` – configured time frame for the rolling window.
- `samplingIntervalSeconds` – the calculated interval between each scheduled sample.

Each created device also exposes a preference to log attribute changes. Enable the option in the device preferences to mirror state changes to the Hubitat log at the info level.

## Debug Logging
Enable the debug logging toggle to surface additional information in the Hubitat logs. Debug logging automatically disables itself after 30 minutes to avoid excessive noise.

## Changelog
- **0.2.7** – Add a per-device toggle to log attribute changes at the info level.
- **0.2.6** – Round calculated rolling averages to two decimal places for consistent reporting.
- **0.2.5** – Fix sampling watchdog grace calculation to avoid type mismatches and keep stalled schedules restarting cleanly.
- **0.2.4** – Add a 1,000-point safety cap with warnings, trim history on every sample, and add a watchdog that restarts sampling if the schedule disappears or stalls.
- **0.2.3** – Replace the invalid `CurrentMeasurement` capability with Hubitat's `CurrentMeter` so drivers load without capability warnings.
- **0.2.2** – Child devices now emit the averaged reading using the original attribute name (e.g., `temperature`) so they behave like standard sensors while retaining the `rollingAverage` attribute for compatibility.
- **0.2.1** – Fix child driver name so new child devices can be created successfully.
- **0.2.0** – Add scheduled sampling with configurable day/hour/minute time frames so samples are evenly spaced across the window and expose the sampling interval on the child device.
- **0.1.6** – Correct app and child metadata namespaces and parent linkage to ensure installation succeeds.
- **0.1.5** – Replace the deprecated create-child button with Hubitat child apps and add the dedicated child app code file.
- **0.1.4** – Restore compatibility with Hubitat's `appButton` signature so the Create child device button renders correctly.
- **0.1.3** – Restore compatibility with Hubitat's `appButton` signature so the Create child device button renders correctly.
- **0.1.2** – Fixed the create-child button invocation error preventing new rolling-average devices from being created.
- **0.1.1** – Resolved the create-child installation error and standardized the namespace to `dylanm.mra` for app and driver.
- **0.1.0** – Initial release supporting multiple rolling-average child devices with configurable numeric attributes, time frames, and sample counts.
