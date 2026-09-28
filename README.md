# Phone Health

A small, dependency-free Android app that tells you whether your phone is healthy — and whether
it is still fit to run as an always-on home server.

Built for a Samsung Galaxy A16 5G that hosts a web dashboard and a Minecraft server from Termux,
but it works on any phone running Android 11+.

## What it checks

| Tab | Checks |
|---|---|
| **Overview** | One verdict, every check listed worst-first |
| **Battery** | Level, temperature, voltage, charge/draw current (mA, W), cycle count, estimated capacity vs design, 24 h charts |
| **System** | Thermal throttling state and headroom, CPU clocks, RAM, storage, storage speed test (flash-wear baseline) |
| **Network** | Active transport, Wi-Fi RSSI, cell dBm per radio, latency |
| **Host** | Are your local services listening (configurable ports), on charger?, hours at ≥40 °C, 7-day temperature and uptime charts |
| **Tests** | Screen pixels, touch grid, volume keys, loudspeaker, earpiece, microphone, vibration, flashlight, sensors |

A background job samples every 15 minutes (7 days kept on-device) so the charts show how the
phone copes with running 24/7. Nothing leaves the phone.

## Notes

- **Capacity** is estimated from the fuel gauge's charge counter and is only graded from readings
  taken at ≥80 % charge. On One UI 6.1+, *Settings › About phone › Battery information* is the
  tie-breaker.
- Samsung kernels report current in mA rather than µA; the app detects the unit.
- One UI sleeps idle apps. Set Phone Health (and Termux, if you host from it) to *Unrestricted*
  battery use, or the logger stops.

## Build

Needs JDK 17–21 and the Android SDK (platform 36).

```
set JAVA_HOME=<path to JDK 21>
gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. Prebuilt APKs are on the
[Releases](../../releases) page.

## License

MIT
