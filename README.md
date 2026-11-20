# droidRDS

Android FT232H / Si4713 laboratory diagnostics and offline RDS analysis.

[Latest CI builds](https://github.com/interrupt21h/droidRDS/actions)

The Si4713 is a transmitter, not an RDS receiver. This application keeps RF output disabled and supports offline inspection of captured RDS groups. Synthetic traffic alerts are not transmitted. An external receiver is required for live decoding.

References: [Silicon Labs AN332](https://cdn-shop.adafruit.com/datasheets/SiLabs%20Programming%20guide%20AN332.pdf), [ISO 14819-1:2021](https://www.iso.org/standard/76781.html). Full ALERT-C conformance requires licensed event and location tables and the complete normative standards.

## Si4703 receiver

Connect D0 to SCLK, D1+D2 to SDIO, D3 to RST, common ground; SEN must be high. Use verified 3.3 V logic and suitable pull-ups. Disconnect other targets before resetting. Connect USB, initialize the receiver (select internal crystal only for crystal-equipped modules), tune on the 100 kHz grid from 87.5 to 108 MHz, seek or capture RDS for ten seconds. Audio is analog at the module, not USB audio. RDA5807M also has model-specific initialization, tuning, seeking and volume controls.

The GPIO-bitbanged transport may be too slow to capture all RDS groups. Capture is best effort, with possible duplicate and missing groups. Hardware validation remains outstanding. See [AN230](https://www.skyworksinc.com/-/media/Skyworks/SL/documents/public/application-notes/AN230.pdf) and [Si4702/03-C19 datasheet](https://www.skyworksinc.com/-/media/Skyworks/SL/documents/public/data-sheets/Si4702-03-C19.pdf).

[Latest successful Android workflow](https://github.com/interrupt21h/droidRDS/actions/workflows/android.yml?query=branch%3Amain+is%3Asuccess)

### Receiver detection diagnostics

Use Check I2C bus to inspect idle levels without sending an address. For Si4703, choose Detect and initialize Si4703 after confirming exclusive wiring. The adapter asserts RST, drives SDIO low with SCLK released, checks line levels, releases RST, then releases SDIO and checks idle. An ACK at 0x10 must be followed by chip identity verification before configuration. SEN is externally strapped high if the board has no pull-up. RST must connect to D3. A shared address is not proof of device type. Hardware validation is still required.

### Device profiles and live telemetry

Si4703 is the default profile. Select RDA5807M, Si4713 diagnostics or Offline tools to reveal their controls. Initialize the selected receiver explicitly after connecting USB. Foreground telemetry attempts a refresh every two seconds while idle, without overlapping manual operations. Si4703 telemetry includes frequency, RSSI, stereo, RDS synchronization/readiness, STC, AFC rail, seek failure, firmware, block errors, raw RDS blocks and all 16 registers. GPIO levels and read latency are diagnostic observations, not electrical measurements. Three consecutive polling failures require reinitialization. Copy live diagnostics separately from command output. Profile selection changes visible controls, not the initialized physical receiver. Hardware timing and Android build validation remain outstanding.
