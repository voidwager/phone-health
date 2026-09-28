# Phone Health

A small, dependency-free Android app that tells you whether your phone is healthy — and whether
it is still fit to run as an always-on home server.

Built for a Samsung Galaxy A16 5G that hosts a web dashboard and a Minecraft server from Termux,
but it works on any phone running Android 11+.

## Look

The app is the server's front bezel. The verdict sits on a two-line character LCD (drawn dot by
dot from a 5x7 character ROM): blue backlight when everything is fine, amber when something needs
you. Under it, one inset bay per component with an LED, a printed label, the reading and a state
word; a 7-day x 24-hour LED matrix shows what happened while nobody was looking. Warnings are
hatched and worded, never colour alone. Light and dark themes follow the system.

## What it checks

| Screen | Checks |
|---|---|
| **Status** | LCD verdict, 8 component bays (battery, power, thermal, storage, memory, network, services, logger) that open to cause + remedy, 7-day hourly LED matrix |
| **Readings** | Battery level/temperature/voltage/current, cycles, capacity vs design, thermal state and headroom, CPU clocks, RAM, storage + speed test (flash-wear baseline), network and cell signal |
| **History** | Watched ports (configurable), logging coverage, hours at ≥40 °C, 7-day temperature / services / charge charts, keep-alive settings |
| **Tests** | Nine-step hardware procedure: screen pixels, touch grid, volume keys, loudspeaker, earpiece, microphone, vibration, flashlight, sensors |

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

MIT. The bundled Barlow Condensed font is under the SIL Open Font License 1.1
(`third_party/BarlowCondensed-OFL.txt`).
