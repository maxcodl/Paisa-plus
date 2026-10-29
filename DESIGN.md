---
name: paisa+
description: Warm-brown Material 3 personal finance tracker for Android
colors:
  # M3 TonalSpot scheme, seed #95492B. Light values first, dark under *-dark.
  primary: "#8F4C33"
  on-primary: "#FFFFFF"
  primary-container: "#FFDBCF"
  on-primary-container: "#72361E"
  secondary: "#77574C"
  on-secondary: "#FFFFFF"
  secondary-container: "#FFDBCF"
  on-secondary-container: "#5D4035"
  tertiary: "#695E2F"
  tertiary-container: "#F2E2A8"
  on-tertiary-container: "#50471A"
  error: "#BA1A1A"
  error-container: "#FFDAD6"
  surface: "#FFF8F6"
  on-surface: "#231A16"
  surface-variant: "#F5DED6"
  on-surface-variant: "#53433E"
  outline: "#85736D"
  outline-variant: "#D8C2BB"
  surface-container-lowest: "#FFFFFF"
  surface-container-low: "#FFF1EC"
  surface-container: "#FCEAE4"
  surface-container-high: "#F7E4DF"
  surface-container-highest: "#F1DFD9"
  primary-fixed-dim: "#FFB59A"
  on-primary-fixed: "#3A0B00"
  income: "#2E7D32"
  expense: "#C62828"
  income-fill: "#66BB6A"
  expense-fill: "#EF5350"
  primary-dark: "#FFB59A"
  on-primary-dark: "#55200A"
  primary-container-dark: "#72361E"
  on-primary-container-dark: "#FFDBCF"
  secondary-dark: "#E7BEAF"
  secondary-container-dark: "#5D4035"
  on-secondary-container-dark: "#FFDBCF"
  tertiary-dark: "#D5C68E"
  tertiary-container-dark: "#50471A"
  on-tertiary-container-dark: "#F2E2A8"
  error-dark: "#FFB4AB"
  surface-dark: "#1A110E"
  on-surface-dark: "#F1DFD9"
  on-surface-variant-dark: "#D8C2BB"
  outline-dark: "#A08D86"
  outline-variant-dark: "#53433E"
  surface-container-lowest-dark: "#140C09"
  surface-container-low-dark: "#231A16"
  surface-container-dark: "#271E1A"
  surface-container-high-dark: "#322824"
  surface-container-highest-dark: "#3D322F"
  income-dark: "#4EDE80"
  expense-dark: "#FFB4AB"
typography:
  display:
    fontFamily: "Outfit"
    fontSize: "40sp"
    fontWeight: 600
    lineHeight: "48sp"
    fontFeature: "tnum"
  headline:
    fontFamily: "Outfit"
    fontSize: "24sp"
    fontWeight: 500
    lineHeight: "32sp"
  title-large:
    fontFamily: "Outfit"
    fontSize: "22sp"
    fontWeight: 500
    lineHeight: "28sp"
  title-medium:
    fontFamily: "Outfit"
    fontSize: "18sp"
    fontWeight: 500
    lineHeight: "24sp"
  title-small:
    fontFamily: "Outfit"
    fontSize: "15sp"
    fontWeight: 600
    lineHeight: "20sp"
    fontFeature: "tnum"
  body-large:
    fontFamily: "Outfit"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  body-medium:
    fontFamily: "Outfit"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
  body-small:
    fontFamily: "Outfit"
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "16sp"
  label-large:
    fontFamily: "Outfit"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "20sp"
  label-medium:
    fontFamily: "Outfit"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
rounded:
  xs: "8dp"
  sm: "12dp"
  md: "16dp"
  lg: "20dp"
  xl: "28dp"
  full: "999dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
  xxl: "40dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    height: "56dp"
    width: "220dp"
  balance-card:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    rounded: "{rounded.xl}"
    padding: "16dp"
  overview-tile:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface-variant}"
    rounded: "{rounded.lg}"
    padding: "12dp"
  segmented-selected:
    backgroundColor: "{colors.primary-fixed-dim}"
    textColor: "{colors.on-primary-fixed}"
    rounded: "{rounded.full}"
    height: "40dp"
  chip-selected:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-medium}"
    rounded: "{rounded.full}"
  chip-unselected:
    backgroundColor: "{colors.tertiary-container}"
    textColor: "{colors.on-tertiary-container}"
    typography: "{typography.label-medium}"
    rounded: "{rounded.xs}"
  option-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.full}"
    height: "56dp"
  text-field:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.full}"
    height: "56dp"
---

# Design System: paisa+

## Overview

**Creative North Star: "The Warm Ledger"**

A money app that feels like a well-kept leather notebook rather than a bank terminal. One warm hue (seed #95492B, M3 hue 39) carries the whole scheme. Light mode is cream paper. Dark mode is near-black roasted brown with peach accents. Brand shows in the palette, the rounded geometric Outfit face and generous pill shapes. Structure, navigation and components stay strictly Material 3.

Density is calm. Amounts are the loudest element on every screen, and decoration never competes with them. Each budget, category, loan and rule gets its own pastel tonal card derived from its color, so a list reads as a set of distinct objects rather than a table.

**Key Characteristics:**

- One brand hue, generated as a full M3 TonalSpot scheme (HCT via material-kolor, 2025 Expressive spec by default). Material You (wallpaper dynamic color) is the default on Android 12+; the brand hue is used when it is off or unsupported.
- User-tunable (Settings › Appearance): scheme variant (TonalSpot/Vibrant/Expressive/Fidelity/Content/Rainbow/FruitSalad/Neutral/Monochrome), contrast level (reduced → high), 2021/2025 spec, and "Colourful surfaces" (accent chroma flooded into background/surface roles at each role's own tone, so hierarchy and text contrast hold).
- Per-item tonal cards: tone 90 containers on light, tone 30 on dark.
- Pills everywhere a finger lands: CTAs, segmented tabs, option cards, text fields, the selected chip.
- Wavy progress for anything measured against a limit.
- Tabular numerals on every amount.

## Colors

A monochrome warm-brown scheme, plus green and red reserved for money direction.

### Primary

- **Roasted Clay** (`primary`): the filled CTA, selected chips, the Recurring "due" card and links. In dark mode it becomes **Peach Glow** (`primary-dark`), which is also the overview amounts color.
- **Peach Cream / Burnt Sienna** (`primary-container` / `primary-container-dark`): the Total balance card and the FAB. This is the one large saturated block on Home.
- **Apricot** (`primary-fixed-dim`): the selected segment in segmented tabs, identical in both themes.

### Secondary

- **Cocoa** (`secondary-container`, `secondary-container-dark`): the navigation bar indicator pill.

### Tertiary

- **Butter** (`tertiary-container`): unselected month and type chips.

### Neutral

- **Cream Paper** (`surface`) / **Espresso** (`surface-dark`): the screen background for onboarding and pushed screens.
- **Home ground** (`surface-container-low`): the Home background and the nav bar.
- **Tile** (`surface-container`): overview tiles, the onboarding text field, the currency row.
- **Ink** (`on-surface`), **Muted ink** (`on-surface-variant`): primary text, then labels and meta.
- **Hairline** (`outline-variant`): option-card strokes and list dividers.

### Named Rules

**The Money Color Rule.** Green (`income`) and red (`expense`) mean money direction and nothing else. Never use them for decoration, success or error states. Errors use `error`.

**The Contrast-First Amount Rule.** Amount text uses the text tokens (`income` / `expense`, ≥4.5:1). The brighter `income-fill` / `expense-fill` are only for bars, tints and icons.

**The Tonal Card Rule.** An item card's colors come from its own color run through the M3 tone ramp: light uses container T90, content T40, track T80; dark uses container T30, content T80, track T40. Never use a raw category color as a background.

## Typography

**Display Font:** Outfit (variable, bundled at `res/font/outfit.ttf`)
**Body Font:** Outfit

**Character:** A rounded geometric sans with a double-storey a and single-storey g. Friendly at display sizes and plain at body sizes.

### Hierarchy

- **Display** (600, 40sp/48sp, tnum): the Total balance amount only.
- **Headline** (500, 24sp/32sp): onboarding page titles.
- **Title Large** (500, 22sp/28sp): top-bar titles, overview tile titles and tile amounts.
- **Title Medium** (500, 18sp/24sp): the greeting (name at 700), section headers, card titles.
- **Title Small** (600, 15sp/20sp, tnum): amounts in cards and rows.
- **Body** (400, 16/14/12sp): field text, descriptions, meta and "compared to" lines.
- **Label** (600 16sp for buttons, 500 12sp for chips, nav and badges).

### Named Rules

**The Tabular Rule.** Every rendered amount sets `tnum`, so columns of money align.

## Layout

- 16dp screen gutter. Cards sit 12dp apart, with 16dp inner padding and 24dp between sections.
- Home stacks a greeting, the balance card, then a 2-column Overview grid. The grid becomes 1 column when font scale is above 1.5.
- Onboarding pages are a centered illustration zone, then title, description and a single pill CTA anchored 40dp above the gesture bar, with an "n/N" counter at the top.
- Heights grow with content; there are no fixed-height text containers.

## Elevation & Depth

Flat, tonal layering only: surface → container-low → container → container-high. There are no drop shadows except Material's own on the FAB and sheets. Bottom sheets use `surface-container-low`.

## Shapes

Soft and pill-heavy:

- Fully rounded (`full`): anything a finger presses, including CTAs, segmented tabs, option cards, text fields, the selected chip and the FAB.
- 20dp (`lg`): content cards.
- 28dp (`xl`): the hero balance card and sheets.
- 8dp (`xs`): unselected chips, which morph to a pill when selected (Expressive ToggleButton).
- Icons sit in 40dp tonal circles.

## Components

### Buttons

- **Shape:** fully rounded pill, 56dp tall, at least 220dp wide on onboarding.
- **Primary:** `primary` fill with `on-primary` label in label-large. Disabled follows M3 (on-surface at 12% fill and 38% label).

### Chips

- **Style:** unselected chips are `tertiary-container` with an 8dp radius. Selected chips are `primary` with an `on-primary` label and morph to a pill.
- **Use:** the month row on Reports and the type filter on Categories.

### Segmented Tabs

- A pill container with a 1dp `primary-container` stroke (`outline-variant` in dark). The selected segment is an `primary-fixed-dim` pill with `on-primary-fixed` text that animates between segments. Each segment is a 48dp touch target.

### Cards / Containers

- **Balance card:** `primary-container`, 28dp radius. Shows the Total balance label, a display amount, a hide-amount eye toggle, and an income/expense split with change vs last month.
- **Overview tile:** `surface-container`, 20dp radius. Icon, title and chevron at top; count and amount at the bottom.
- **Tonal card:** item-derived colors (see the Tonal Card Rule), 20dp radius, 16dp padding.

### Inputs / Fields

- **Style:** a filled pill on `surface-container` with no stroke and a leading icon. The focus cursor is `primary`.

### Navigation

- An M3 NavigationBar on `surface-container-low` with a `secondary-container` indicator pill and labels always shown: Home, Accounts, Reports, Search.

### Progress

- **Wavy progress** (`LinearWavyProgressIndicator`) for budgets, loans and credit usage. It uses the tonal content color with a T80/T40 track. Amplitude drops to 0 below 2% progress, so near-empty bars are flat.

### Option Card

- Onboarding choices: a 56dp outlined pill with a 1dp `outline-variant` stroke. Leading 36dp circle in `primary-container` with a `primary` icon; trailing chevron.

## Do's and Don'ts

### Do:

- **Do** pull every color from the scheme or the tonal function. Hex values appear only in `ui/theme/`.
- **Do** keep touch targets at least 48dp, give icon buttons labels, and merge amount semantics ("Expense, 256 dollars").
- **Do** let amounts shrink to fit one line instead of wrapping.

### Don't:

- **Don't** show features that don't exist (goals, assets, heatmap, Drive backup) as placeholders.
- **Don't** show Paisa's store reviews or ask for ratings.
- **Don't** use green or red outside money direction.
- **Don't** add drop shadows to cards.
