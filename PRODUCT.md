# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Native Android, Java, hand-built framework Views, no library dependencies (confirmed 28 Sep 2026:
keep it dependency-free). Material 3 behaviour — 48 dp targets, sp type, color roles, predictive
Back, edge-to-edge insets — is implemented by hand, not imported. minSdk 30, targetSdk 36.

## Users

People who self-host from an old Android phone: a web app, a game server, a bot, running in Termux
or similar on a handset that lives on a charger. The author's own case is a Galaxy A16 5G hosting a
Node dashboard (port 8080) and a Minecraft server (port 25565). The phone is a dedicated server,
not the owner's daily device.

Their job with this app: pick the server phone up, or glance at it on its charger, and learn in a
few seconds whether the box is fine — and if not, what is wrong and what to do.

## Product Purpose

Tell a self-hoster whether their phone is healthy and fit to keep serving 24/7, and catch slow
decay (battery stress from heat, flash wear, a service Android quietly killed) before it becomes
an outage. Success is a single honest verdict the owner trusts, with evidence one tap away.

## Positioning

Generic phone checkers answer "is my phone OK to use?". This one answers "is my phone OK to leave
running unattended?": it watches the owner's own services on 127.0.0.1, records a 7-day history of
heat, charge and uptime while nobody is looking, and treats Android killing background processes
as a health failure, not a user preference.

## Operating Context

- The phone sits plugged in on a shelf or desk, often for weeks; heat and constant charging are the
  main battery threats.
- One UI / OEM battery optimisation kills idle apps; Termux and this app's logger both need
  Unrestricted battery use.
- The owner usually reaches the server from another machine (SSH, web); opening this app on the
  phone itself is occasional and short.
- Hardware self-tests are run rarely: on setup, and when something seems off.

## Capabilities and Constraints

- Battery: level, temperature, voltage, charge/draw current (auto-detects Samsung's mA units),
  cycle count (Android 14+ where the OEM reports it), capacity estimate graded only from readings
  at ≥80 % charge.
- Thermal throttling state and headroom; CPU clocks where the kernel exposes them; RAM; storage and
  a storage speed test with a first-run baseline.
- Network: transport, Wi-Fi RSSI, cell dBm per radio, latency.
- Host: user-configurable ports probed on 127.0.0.1; 15-minute background sampling, 7 days kept.
- Nine hardware self-tests (pixels, touch, keys, loudspeaker, earpiece, mic, vibration, torch,
  sensors).
- Nothing leaves the device. No accounts, no network calls except the latency probe.
- Per-sensor temperatures are usually hidden from apps by SELinux; the app must say so, not fake it.

## Brand Commitments

Name: **Phone Health**. Launcher icon: heartbeat line through a phone outline. Voice: plain,
direct, specific numbers over adjectives; says when a reading is rough or unavailable.

## Evidence on Hand

Real readings from the author's A16 (Android 16) are the only data. No user counts, testimonials or
benchmarks exist; do not invent any.

## Product Principles

1. Verdict first, evidence second: the answer to "is it fine?" before any number.
2. Honest about uncertainty: rough estimates and hidden sensors are labelled, never dressed up.
3. Unattended time is the product: history and gaps while nobody watched matter more than now.
4. Every warning says what to do next.
5. Stay small: no dependencies, no network, no accounts.

## Accessibility & Inclusion

Follow the system font scale (sp) and dark/light setting; 48 dp touch targets; status never
conveyed by color alone (always a word alongside the dot).
