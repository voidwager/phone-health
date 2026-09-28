---
name: Phone Health
description: The front bezel of a phone that is a server — verdict on a character LCD, evidence in inset bays.
colors:
  bezel-silver: "#D7DCE0"
  bay-well-light: "#C9CFD4"
  bay-edge-light: "#9FA7AE"
  lip-highlight-light: "#EEF1F3"
  rule-light: "#AEB5BC"
  vent-hole-light: "#8E979F"
  ink-light: "#1B1F24"
  muted-light: "#454E57"
  touch-blue-light: "#2159C4"
  touch-tonal-light: "#B9CCF2"
  led-ok-light: "#1C9A45"
  led-check-light: "#D9930C"
  led-fault-light: "#CC3326"
  led-off-light: "#7F878E"
  fault-text-light: "#9E2218"
  bezel-graphite: "#23282D"
  bay-well-dark: "#181B1F"
  bay-edge-dark: "#0C0E10"
  lip-highlight-dark: "#3A4148"
  rule-dark: "#30363C"
  vent-hole-dark: "#0A0C0E"
  ink-dark: "#E4E7EA"
  muted-dark: "#A4ACB4"
  touch-blue-dark: "#6F9BF0"
  touch-tonal-dark: "#2A3E63"
  led-ok-dark: "#34C46A"
  led-check-dark: "#F0B429"
  led-fault-dark: "#F0584A"
  led-off-dark: "#4A5057"
  fault-text-dark: "#F0584A"
  on-touch: "#FFFFFF"
  lcd-frame: "#101214"
  lcd-blue-glass: "#2F63C9"
  lcd-blue-pixel-on: "#EAF2FF"
  lcd-blue-pixel-off: "#3A6ED4"
  lcd-amber-glass: "#E9A21C"
  lcd-amber-pixel-on: "#2B1B02"
  lcd-amber-pixel-off: "#EDAB33"
typography:
  display:
    fontFamily: "5x7 dot-matrix ROM (drawn, not a font)"
    fontSize: "20 cells across the content width"
  headline:
    fontFamily: "Barlow Condensed SemiBold, sans-serif-condensed"
    fontSize: "17sp"
    fontWeight: 600
    letterSpacing: "0.07em"
  title:
    fontFamily: "Roboto Medium (sans-serif-medium)"
    fontSize: "22sp"
    fontWeight: 500
    lineHeight: 1
    fontFeature: "tnum"
  body:
    fontFamily: "Roboto (sans-serif)"
    fontSize: "15sp"
    fontWeight: 400
    lineHeight: 1.25
  body-small:
    fontFamily: "Roboto (sans-serif)"
    fontSize: "13sp"
    fontWeight: 400
  label:
    fontFamily: "Barlow Condensed SemiBold, sans-serif-condensed"
    fontSize: "13sp"
    fontWeight: 600
    letterSpacing: "0.07em"
  label-small:
    fontFamily: "Barlow Condensed SemiBold, sans-serif-condensed"
    fontSize: "11sp"
    fontWeight: 600
    letterSpacing: "0.07em"
rounded:
  glass: "3dp"
  bay: "6dp"
  lip: "7dp"
  pill: "16dp"
spacing:
  bay-gap: "10dp"
  inner: "12dp"
  panel-x: "14dp"
  gutter: "16dp"
  section: "18dp"
  touch: "48dp"
components:
  bay:
    backgroundColor: "{colors.bay-well-light}"
    rounded: "{rounded.bay}"
    padding: "12dp 8dp 12dp 12dp"
    height: "100dp min"
  panel:
    backgroundColor: "{colors.bay-well-light}"
    rounded: "{rounded.bay}"
    padding: "6dp 14dp"
  row:
    textColor: "{colors.ink-light}"
    typography: "{typography.body}"
    padding: "8dp 0"
    height: "44dp min"
  button-touch:
    backgroundColor: "transparent"
    textColor: "{colors.touch-blue-light}"
    typography: "{typography.label}"
    rounded: "{rounded.bay}"
    padding: "0 16dp"
    height: "{spacing.touch}"
  nav-item-active:
    backgroundColor: "{colors.touch-tonal-light}"
    textColor: "{colors.ink-light}"
    rounded: "{rounded.pill}"
    size: "64x32dp pill, 80dp item"
  nav-item:
    textColor: "{colors.muted-light}"
  lcd:
    backgroundColor: "{colors.lcd-frame}"
    rounded: "{rounded.bay}"
  lcd-healthy:
    backgroundColor: "{colors.lcd-blue-glass}"
    textColor: "{colors.lcd-blue-pixel-on}"
    rounded: "{rounded.glass}"
  lcd-needs-you:
    backgroundColor: "{colors.lcd-amber-glass}"
    textColor: "{colors.lcd-amber-pixel-on}"
    rounded: "{rounded.glass}"
---

# Design System: Phone Health

## Overview

**Creative North Star: "Rack Bezel"**

The phone is a server, so the app is its front bezel. Every screen is a powder-coated faceplate (silver on a lit shelf, graphite in a dark room) with wells pressed into it. The verdict lives on a two-line, 20-column backlit character LCD, drawn dot by dot from an HD44780-style 5x7 ROM, the way rack servers report health. Below it, component bays each carry an LED lens, a silkscreened label, a tabular reading and, when not OK, a state word.

Density is moderate and practical: a 2x4 bay grid in the first viewport, then printed section labels over inset panels of rows. Colour is almost entirely neutral metal; hue belongs to three things only: the LCD backlight, LED lenses, and blue touch points. One OK / CHECK / FAULT state per component, rolled up by worst-wins, drives the LCD, the LEDs and the words together.

**Key Characteristics:**
- Two-line dot-matrix LCD as the single verdict surface; blue backlight = all OK, amber = something needs you.
- Inset bays (darker well, 2dp shadowed top, 1dp lit bottom lip), never raised cards.
- Round LED lenses, diagonally hatched for CHECK (sparse) and FAULT (dense).
- Silkscreen condensed caps for everything printed on the metal; system sans for prose and readings.
- Blue marks what you can press, outlined, never filled.
- Hex-perforated vent strip as the one purely material band.

## Colors

Neutral cool metal in two finishes, with hue reserved for backlight, lamps and touch points.

### Primary
- **Touch-Point Blue** (touch-blue-light / touch-blue-dark): outlines and label text of every pressable control, the open-bay ring (2dp), and the open chevron. On server hardware blue marks the parts that are safe to handle.
- **Touch Tonal** (touch-tonal-light / touch-tonal-dark): only the active navigation pill.

### Secondary
- **Backlight Blue / Backlight Amber** (lcd-*): the LCD glass. Blue glass with near-white pixels when no component is CHECK or FAULT; amber glass with dark-brown pixels otherwise. Unlit pixels stay faintly visible (pixel-off), as on real glass. The LCD sits in a near-black frame (lcd-frame) in both themes; on graphite it gets a 1dp highlight rim.

### Tertiary
- **LED Green / Amber / Red / Off** (led-ok, led-check, led-fault, led-off): lens fills only, plus the chart threshold line (dashed, led-check). Never text, never fills of surfaces.
- **Fault Red text** (fault-text-light / -dark): the FAULT word and failed-test text. Darker than the LED red in light mode so 11–13sp text holds 4.5:1 on the bay well.

### Neutral
- **Bezel** (bezel-silver / bezel-graphite): window, header and nav background.
- **Bay Well** (bay-well-*): every bay and panel fill; one step darker than the bezel.
- **Bay Edge** (bay-edge-*): the shadowed top edge of a well, unlit LED rings, empty matrix cells.
- **Lip Highlight** (lip-highlight-*): the lit bottom lip of a well, the LCD rim in dark, vent hole edges in dark.
- **Ink** (ink-*): readings, labels in rows, data traces in charts, CHECK words.
- **Muted** (muted-*): silkscreen section and bay labels, details, hints, inactive nav.
- **Rule** (rule-*): row dividers (hairline), chart gridlines, the rule above the nav bar.
- **Vent Hole** (vent-hole-*): hex perforations.

### Named Rules
**The Lamp-Only Rule.** State colours (LED green/amber/red) appear only as LED lenses, matrix cells and the one dashed threshold line. Text states use ink (CHECK) or fault-text (FAULT); surfaces never take a state tint.

**The Ink-Trace Rule.** Chart lines are drawn in ink, never in a state colour. The threshold is the only coloured element on a chart.

**The Two-Finish Rule.** Light and dark are the same bezel in two powder coats; every neutral token has a paired value and the LCD, LED hues and structure do not change between them.

## Typography

**Display Font:** the LCD's own 5x7 character ROM, drawn as dots (not a typeface)
**Silkscreen Font:** Barlow Condensed SemiBold (bundled, SIL OFL; fallback sans-serif-condensed bold)
**Body Font:** Roboto via system sans-serif (400) and sans-serif-medium (500)

**Character:** DIN-like condensed caps printed on the metal, against a plain system sans for anything you read as a sentence or a number.

### Hierarchy
- **Display** (5x7 dots, pitch fitted so 20 cells fill the glass): LCD only. Uppercase; lines longer than 20 break at a word.
- **Headline** (Barlow 600, 17sp, 0.07em, caps): the app name in the header.
- **Title** (Roboto Medium 500, 22sp, tnum): the bay reading. 17sp medium, 1.2 line spacing, for the service-note cause line; 16sp medium for test names.
- **Body** (Roboto 400, 15sp): row labels; row readings are 15sp medium with tnum. Notes are 14sp at 1.25 line spacing.
- **Body small** (Roboto 400, 13sp): bay detail, test hints, legend.
- **Label** (Barlow 600, 13–14sp, 0.07em, caps, muted): section labels over panels, bay labels, bay state words, touch-button text (15sp).
- **Label small** (Barlow 600, 10–12sp, caps): row state words (11sp), test state (11sp), chart titles (12sp), chart axis and matrix axis labels (10–11sp, tnum).

### Named Rules
**The Tabular Reading Rule.** Every number that changes in place uses `tnum` so rows never shift as values tick.

**The Silkscreen Rule.** Anything printed on the metal (section names, bay labels, state words, button labels) is uppercase Barlow at 0.07em tracking. Sentences are never silkscreened.

## Layout

A single scrolling column on the bezel with a 16dp side gutter (plus system insets, edge-to-edge), a header row (20dp side padding), and a fixed 80dp bottom navigation of four equal items behind a 1dp rule. Status opens with: vent strip (22dp), LCD (6dp below), then the bay grid 14dp below: two columns, 10dp gutters both ways, bays at least 100dp tall. Opening a bay inserts its service note as a full-width panel 12dp under the grid. Sections below are a silkscreen label (18dp above, 8dp below, 4dp inset) over an inset panel (14dp horizontal, 6dp vertical padding). Rows are at least 44dp with 8dp vertical padding and hairline dividers between them; every touch target is 48dp.

## Elevation & Depth

Depth is pressed in, not lifted. Surfaces are flat bezel with wells stamped into it: the well is one tone darker, a bay-edge band shows 2dp along its top (shadow) and a lip-highlight band shows 1dp along its bottom (lit edge). The LCD reads as a window cut through the bezel into a black frame. There are no drop shadows on bezel components. The one exception is the snackbar, a transient Material overlay that floats above the bezel at 6dp elevation, because it sits over content rather than in it.

### Named Rules
**The Pressed-In Rule.** Groups are wells, never raised cards. Depth comes from the two-band lip, never from elevation or blur.

## Shapes

Sheet-metal radii, small and consistent: 6dp for bays, panels, the LCD frame and touch buttons (7dp for the lip layer beneath a well), 3dp for the LCD glass. The only large radius is the 16dp active-nav pill. LEDs and matrix cells are true circles; vent holes are pointy-top hexagons (3.2dp radius, offset rows). Icons and chevrons are a single drawn family: 24-unit grid, 2-unit stroke, round caps and joins.

## Components

### LCD (signature)
Two lines, 20 columns, frame 6dp inset to the glass, 12dp/10dp inset to the first dot, dots 82% of pitch.
- **Line 1:** `SYSTEM OK`, or `CHECK: <COMPONENT>` / `FAULT: <COMPONENT>` naming the worst component once.
- **Line 2:** steps through values every 2.5s tick, never repeating line 1's label.
- **Backlight:** blue when nothing is CHECK or FAULT; amber when anything is.
- **FAULT flash:** on alternate ticks the 6-cell `FAULT:` prefix renders in inverse video (solid lit band, glyphs knocked out in glass colour), so a FAULT cannot be mistaken for a steady CHECK. Skipped when system animations are off. The word itself never leaves the glass.

### LED lens
12dp circle (10dp in legends), 1dp dark ring, small white glint top-left when lit. CHECK hatches the lens with diagonal strokes at 0.8r spacing; FAULT at 0.55r. Unlit (no data) is the off colour with a bay-edge ring and no glint. LEDs are hidden from accessibility; the word beside them speaks.

### Bay (drive caddy)
- **Anatomy:** LED + silkscreen label (14sp muted, single line) + chevron on top; 22sp tabular reading; footer with 13sp detail left and the state word right (CHECK in ink, FAULT in fault-text, 13sp silkscreen). OK prints no word.
- **States:** tappable with a bounded ripple (white 20% in dark, black 13% in light). Open bay gets a 2dp touch-blue ring and a blue up-chevron; closed chevron is muted and points down.

### Panel + row
Silkscreen section label on the bezel, then an inset panel. Row: optional 12dp LED, 15sp label filling the line, right-aligned 15sp medium tabular value (muted when NA), and an 11sp state word under it for CHECK/FAULT.

### Buttons (touch points)
- **Shape:** 6dp radius, 1.5dp touch-blue outline, transparent fill, 48dp tall, 16dp side padding.
- **Label:** 15sp silkscreen caps in touch-blue.
- **Press:** ripple of touch-blue at 22% bounded to the shape. No state-list elevation.
- Full-width inside panels (8dp above and below); wrap-content "Run/Rerun" beside test rows.

### Navigation
Four equal 80dp items on the bezel: drawn 24dp icon in a 64x32dp pill, 12sp medium label 4dp below. Active: touch-tonal pill, ink icon and label. Inactive: no pill, muted. Switching destinations fades content in from 0 alpha and 8dp down over 200ms, decelerate(2); reselecting scrolls to top. Icons: Status = LCD window, Readings = gauge, History = 3x3 lamps, Tests = clipboard with tick.

### LED activity matrix
7 day rows x 24 hour lenses; lens radius 34% of pitch. Silkscreen day labels (TODAY in ink, others muted) and hour ticks every 6h. Hatch strokes mark CHECK (one) and FAULT (two). No sample = off lens at ~43% alpha with a ring; future hours draw nothing.

### Chart
120dp single series in ink, 2dp stroke, round joins; three hairline gridlines; the line breaks at gaps over 45 min (the logger was asleep); optional dashed 4/4dp threshold in LED amber; silkscreen tnum axis labels.

### Vent strip
Full-width, 22dp, hex perforations in vent-hole colour; on graphite each hole gets a 1dp highlight edge. Purely decorative, hidden from accessibility.

## Do's and Don'ts

### Do:
- **Do** drive LCD backlight, LEDs and state words from one worst-wins OK / CHECK / FAULT state.
- **Do** pair every CHECK or FAULT lens with a hatch and a printed word; state is never colour alone.
- **Do** mark every pressable thing with touch-blue: outline for buttons, 2dp ring for an open bay.
- **Do** use tabular figures for every live reading, axis label and matrix label.
- **Do** keep 48dp touch targets and sp type so system font scale reflows the bays.
- **Do** gate every motion (tab fade, FAULT flash, snackbar fade) on system animations being enabled.

### Don't:
- **Don't** raise surfaces with drop shadows or elevation; bays are pressed in.
- **Don't** tint text, fills or chart lines with LED colours; lamps and the threshold only.
- **Don't** fill touch points solid blue; the outline marks them without shouting.
- **Don't** put more than 20 characters on an LCD line or repeat line 1's label on line 2.
- **Don't** set sentences in silkscreen caps, or set silkscreen labels in the body sans.
