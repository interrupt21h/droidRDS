# droidRDS

Android FT232H / Si4713 laboratory diagnostics and offline RDS analysis.

[Latest CI builds](https://github.com/interrupt21h/droidRDS/actions)

The Si4713 is a transmitter, not an RDS receiver. This application keeps RF output disabled and supports offline inspection of captured RDS groups. Synthetic traffic alerts are not transmitted. An external receiver is required for live decoding.

References: [Silicon Labs AN332](https://cdn-shop.adafruit.com/datasheets/SiLabs%20Programming%20guide%20AN332.pdf), [ISO 14819-1:2021](https://www.iso.org/standard/76781.html). Full ALERT-C conformance requires licensed event and location tables and the complete normative standards.
