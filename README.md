<p align="center"><img src="docs/brand/logo.svg" alt="droidRDS — FM radio and traffic intelligence" width="720"></p>

<p align="center">
  <a href="https://github.com/interrupt21h/droidRDS/actions/workflows/android.yml"><img src="https://github.com/interrupt21h/droidRDS/actions/workflows/android.yml/badge.svg" alt="Android build"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0 or newer">
  <img src="https://img.shields.io/badge/Kotlin-JVM%2017-7F52FF" alt="Kotlin JVM 17">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-69E6D2" alt="MIT license"></a>
</p>

**droidRDS** is an Android radio engineering console for exploring FM reception, Radio Data System (RDS) broadcasts and ALERT-C Traffic Message Channel (TMC) observations using an external USB-connected receiver.

[Download APK](https://github.com/interrupt21h/droidRDS/actions/workflows/android.yml?query=branch%3Amain+is%3Asuccess) · [Report an issue](https://github.com/interrupt21h/droidRDS/issues) · [Hardware setup](#hardware-setup) · [Build from source](#build-from-source)

## Features

| Radio operation | Traffic intelligence | Engineering tools |
| --- | --- | --- |
| Frequency tuning and seeking | Continuous 8A observation feed | USB and I²C diagnostics |
| RSSI and stereo telemetry | Event catalogue lookup | Raw receiver registers |
| RDS reception indicators | Raw location words, hex and decimal | Reset and oscillator tests |
| Dedicated radio workspace | Filters and stable manual browsing | Offline RDS analysis |
| Si4703 and RDA5807M controls | Session copying and timestamps | Chip identity and power diagnostics |

## Supported hardware

| Component | Role |
| --- | --- |
| FT232H | USB-to-I²C bridge via Android USB host |
| Si4703 | Primary FM/RDS receiver |
| RDA5807M | Alternative receiver with dedicated register controls |
| Si4713 | Transmitter diagnostics only; not a receiver |

The application is experimental. Reception is best effort: GPIO-bitbanged I²C may miss groups or read duplicates. Audio is available at the receiver module's analog output, not over USB. Synthetic traffic alerts are not transmitted; RF output remains disabled.

## Install and operate

1. Open the [successful Android workflows](https://github.com/interrupt21h/droidRDS/actions/workflows/android.yml?query=branch%3Amain+is%3Asuccess), select a completed run and download its artifact.
2. Extract the APK and install it on an Android 8.0+ device with USB host support.
3. Connect the FT232H using USB OTG and grant USB access.
4. Open **Setup & diagnostics**, select the receiver profile, inspect the bus and initialize the receiver.
5. Switch to **Radio**, select a frequency and tune or seek.
6. Enable traffic recording. Scroll to browse older observations without interrupting acquisition; enable Follow latest to return to the live feed.

Each CI artifact includes a uniquely named APK and SHA256SUMS.txt. The build identifier includes the run ID, attempt and abbreviated commit SHA.

## Hardware setup

### Si4703 wiring

| FT232H | Si4703 |
| --- | --- |
| D0 | SCLK |
| D1 + D2 | SDIO |
| D3 | RST |
| GND | GND |

Use verified 3.3 V logic, appropriate pull-ups and module-specific power requirements. SEN must be high. Disconnect other targets before reset.

The reset sequence holds SDIO low while releasing RST to select two-wire operation. An ACK at 0x10 is not sufficient to identify a receiver; chip identity must also be checked. Select crystal initialization only for crystal-equipped modules. The Si4703 tuning grid is 87.5–108 MHz in 100 kHz steps.

### Troubleshooting

Use Check I2C bus to inspect idle levels. If initialization fails, run Reset only, Enable crystal only (adjustable 500–10000 ms), and Power enable separately. Compare register-02-only and register-02–07 writes in independent reset trials. Raw register inspection is available before initialization succeeds.

Telemetry includes frequency, RSSI, stereo, RDS sync/readiness, STC, AFC rail, seek failure, firmware, block errors and registers. GPIO levels and read latency are diagnostic observations, not electrical measurements. XOSCEN and ENABLE readback alone do not establish oscillator operation or firmware readiness. Three consecutive polling failures require reinitialization. Profile selection changes visible controls, not the initialized physical receiver.

## RDS-TMC interpretation

The dashboard distinguishes traffic candidates, ALERT-C announcements, system/tuning groups and multi-group fragments. It preserves the raw RDS blocks and location words even when geographic resolution is unavailable.

On first launch, the app downloads and validates the [OpenStreetMap Wiki TMC Event Code List](https://wiki.openstreetmap.org/wiki/TMC/Event_Code_List), caches it privately for offline use and displays event metadata. Refresh it manually in Setup & diagnostics. A user-supplied TSV catalogue can also be imported.

Event descriptions are not proof that an incident is current or geographically resolved. Location references require the correct service and location table; encrypted values remain raw observations. Repeated reads are not necessarily distinct broadcasts. Full multi-group assembly, geographic resolution and normative ALERT-C conformance are not claimed.

## Build from source

Requirements: JDK 17, Gradle 8.10.2, Android SDK 35. No wiki access is required at build time.

```bash
git clone https://github.com/interrupt21h/droidRDS.git
cd droidRDS
gradle --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to app/build/outputs/apk/debug/app-debug.apk. See the workflow badge for current CI status.

## Project structure

| Component | Responsibility |
| --- | --- |
| Ft232h.kt | USB bridge and I²C transport |
| Si4703.kt / Rda5807.kt | Receiver operations |
| Rds.kt / RdsDecoder.kt | RDS group analysis |
| TrafficFeed.kt | Bounded traffic observation storage |
| TrafficDashboard.kt | Live traffic cards and browsing |
| TrafficEvents.kt | Event catalogue lookup and import |
| tools/generate_catalog.py | Catalogue generation |

## Technical references

- [Si4702/03-C19 datasheet](https://www.skyworksinc.com/-/media/Skyworks/SL/documents/public/data-sheets/Si4702-03-C19.pdf)
- [Silicon Labs AN230 — Si4700/01/02/03 programming](https://www.skyworksinc.com/-/media/Skyworks/SL/documents/public/application-notes/AN230.pdf)
- [Silicon Labs AN332 — Si47xx programming](https://cdn-shop.adafruit.com/datasheets/SiLabs%20Programming%20guide%20AN332.pdf)
- [ISO 14819-1 — ALERT-C protocol](https://www.iso.org/standard/76781.html)
- [OpenStreetMap Wiki — TMC event codes](https://wiki.openstreetmap.org/wiki/TMC/Event_Code_List)

## License

Original application code and artwork are available under the [MIT License](LICENSE). The downloaded event catalogue is attributed to OpenStreetMap Wiki contributors under [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0/); its attribution is included in the cached catalogue. Third-party specifications retain their respective rights.

droidRDS is an independent experimental project, not a certified traffic information or navigation service.
