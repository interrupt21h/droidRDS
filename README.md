# droidRDS

Android FT232H / Si4713 laboratory diagnostics and offline RDS analysis.

[Latest CI builds](https://github.com/interrupt21h/droidRDS/actions)

The Si4713 is a transmitter, not an RDS receiver. This application keeps RF output disabled and supports offline inspection of captured RDS groups. Synthetic traffic alerts are not transmitted. An external receiver is required for live decoding.

References: [Silicon Labs AN332](https://cdn-shop.adafruit.com/datasheets/SiLabs%20Programming%20guide%20AN332.pdf), [ISO 14819-1:2021](https://www.iso.org/standard/76781.html). Full ALERT-C conformance requires licensed event and location tables and the complete normative standards.

## Si4703 receiver

Connect D0 to SCLK, D1+D2 to SDIO, D3 to RST, common ground; SEN must be high. Use verified 3.3 V logic and suitable pull-ups. Disconnect other targets before resetting. Connect USB, initialize the receiver (select internal crystal only for crystal-equipped modules), tune on the 100 kHz grid from 87.5 to 108 MHz, seek or capture RDS for ten seconds. Audio is analog at the module, not USB audio. RDA5807M remains snapshot-only.

The GPIO-bitbanged transport may be too slow to capture all RDS groups. Capture is best effort, with possible duplicate and missing groups. Hardware validation remains outstanding. See [AN230](https://www.skyworksinc.com/-/media/Skyworks/SL/documents/public/application-notes/AN230.pdf) and [Si4702/03-C19 datasheet](https://www.skyworksinc.com/-/media/Skyworks/SL/documents/public/data-sheets/Si4702-03-C19.pdf).

[Latest successful Android workflow](https://github.com/interrupt21h/droidRDS/actions/workflows/android.yml?query=branch%3Amain+is%3Asuccess)
