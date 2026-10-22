---
name: iOS 18 Dark Architecture
colors:
  surface: '#131315'
  surface-dim: '#131315'
  surface-bright: '#39393b'
  surface-container-lowest: '#0e0e10'
  surface-container-low: '#1b1b1d'
  surface-container: '#1f1f21'
  surface-container-high: '#2a2a2c'
  surface-container-highest: '#353437'
  on-surface: '#e4e2e4'
  on-surface-variant: '#c0c6d6'
  inverse-surface: '#e4e2e4'
  inverse-on-surface: '#303032'
  outline: '#8b91a0'
  outline-variant: '#414754'
  surface-tint: '#aac7ff'
  primary: '#aac7ff'
  on-primary: '#003064'
  primary-container: '#3e90ff'
  on-primary-container: '#002957'
  inverse-primary: '#005db8'
  secondary: '#ffb3b2'
  on-secondary: '#680013'
  secondary-container: '#d60133'
  on-secondary-container: '#ffe7e6'
  tertiary: '#47e266'
  on-tertiary: '#003910'
  tertiary-container: '#00a73e'
  on-tertiary-container: '#00320d'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#d6e3ff'
  primary-fixed-dim: '#aac7ff'
  on-primary-fixed: '#001b3e'
  on-primary-fixed-variant: '#00468d'
  secondary-fixed: '#ffdad9'
  secondary-fixed-dim: '#ffb3b2'
  on-secondary-fixed: '#410008'
  on-secondary-fixed-variant: '#92001f'
  tertiary-fixed: '#6cff82'
  tertiary-fixed-dim: '#47e266'
  on-tertiary-fixed: '#002106'
  on-tertiary-fixed-variant: '#00531a'
  background: '#131315'
  on-background: '#e4e2e4'
  surface-variant: '#353437'
typography:
  display:
    fontFamily: Plus Jakarta Sans
    fontSize: 34px
    fontWeight: '700'
    lineHeight: 41px
  display-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 36px
  title-1:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 34px
  title-2:
    fontFamily: Plus Jakarta Sans
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
  title-3:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 25px
  headline:
    fontFamily: Plus Jakarta Sans
    fontSize: 17px
    fontWeight: '600'
    lineHeight: 22px
  body:
    fontFamily: Plus Jakarta Sans
    fontSize: 17px
    fontWeight: '400'
    lineHeight: 22px
  callout:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 21px
  subheadline:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 20px
  footnote:
    fontFamily: Plus Jakarta Sans
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  caption-1:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  caption-2:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 13px
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.25rem
  space-xl: 2rem
---

## Brand & Style

This design system expresses the native precision, depth, and tactility of iOS 18 in Dark Mode. Built around deep OLED absolute blacks, ultra-refined frosted glass materials, and focused, vibrant system accents, the interface establishes an immersive, content-first experience.

### Core Visual Principles
- **OLED Immersion:** Pure black (`#000000`) anchors the system canvas, allowing content, ambient glows, and typography to float with zero border friction.
- **Dynamic Optical Depth:** Visual elevation relies on Apple’s multi-layered material translucency (`ultraThinMaterial`, `thickMaterial`, and chrome backdrops) paired with continuous rounded corners (squircle curvature) and subtle 1px internal specular highlights (`rgba(255, 255, 255, 0.08)`).
- **Vibrant Precision:** Key accents—such as iOS electric blue, Apple Music neon pink/red, and system green—serve purely functional and expressive roles. They illuminate actions, status indicators, and playback dynamics without polluting the neutral canvas.
- **Tactile Fluidity:** Every component, from floating pill address bars to Dynamic Island expansions, utilizes responsive spring physics, subtle pressed scaling (`scale(0.97)`), and haptic readiness.

## Colors

The palette is engineered specifically for Dark Mode legibility, high contrast, and layered spatial hierarchy on OLED displays.

### Base System Surfaces
- **System Black (Canvas):** `#000000` — Pure OLED ground for full-bleed views, modal sheet scrims, and Dynamic Island rest states.
- **Secondary Background (Grouped Content):** `#121214` — Primary surface for grouped tables, list insets, and card backdrops.
- **Tertiary Background (Elevated Containers):** `#1C1C1E` — Elevated list rows, search bars, and floating controls.
- **Quaternary Background (Interactive Layers):** `#2C2C2E` — Hover states, pressed states, and grouped inset sub-elements.
- **Quinary / Separator Fill:** `#3A3A3C` — Subdued dividers, inactive toggle tracks, and structural outlines.

### Vibrancy & Accent Colors
- **System Blue:** `#0A84FF` — Primary action color, link labels, selected tab icons, Safari navigation.
- **Vibrant Neon Pink/Red:** `#FA2D48` — Apple Music primary brand accent, live playback controls, active scrubbers, favorite badges.
- **System Green:** `#30D158` — Battery status, success states, active connectivity indicators.
- **System Orange / Warning:** `#FF9F0A` — Low power mode, caution badges.
- **System Indigo / Purple:** `#5E5CE6` — AirPlay target active, Siri multi-modal glows.

### Labels & Text Hierarchies
- **Label (Primary):** `#FFFFFF` — Primary titles, active button text, key metrics.
- **Secondary Label:** `rgba(235, 235, 245, 0.60)` — Subheadings, secondary captions, metadata.
- **Tertiary Label:** `rgba(235, 235, 245, 0.30)` — Placeholder text, inactive icons, elapsed time markers.
- **Quaternary Label:** `rgba(235, 235, 245, 0.18)` — Disabled components, track backgrounds.

## Typography

Typography matches Apple's SF Pro proportions, tracking, and optical scale using clean, modern humanist geometry.

### Style Directives
- **Tracking & Kerning:** Apply negative tracking (`-0.4px` to `-0.2px`) for display and title tiers to create tight, cinematic header locks. Footnotes and captions use zero or slightly positive letter-spacing (`+0.1px`) to maintain legibility at micro scales.
- **Dynamic Scale Consistency:** Large Titles collapse to centered standard headlines (`17px`, Semi-Bold) upon view scrolling, maintaining strict inline header boundaries.
- **Tabular Figures:** Numbers in playback scrubbers, digital clocks, and stock/currency displays must use tabular numeric formatting to prevent layout shifting during real-time updates.

## Layout & Spacing

Layout adheres to iOS 18 safe area parameters, dynamic device insets, and edge-to-edge content presentation.

### Screen Layout & Safe Areas
- **Mobile Device Bounds:** 16px horizontal margins define the standard grouped content boundaries.
- **Top Safe Area:** 44px to 59px dedicated inset depending on Dynamic Island or notch hardware profile.
- **Bottom Safe Area:** 34px buffer dedicated to the Home Indicator bar. Floating glass bars, toolbars, and sheets float above or neatly integrate within this zone.

### Grid & Density
- Content uses an 8pt baseline rhythm with 4pt micro-steps for tight badge/label alignments.
- **Compact (Mobile):** Single-column layout with grouped insets (`16px` margin on screen sides).
- **Regular (Tablet / Split View):** Multi-column navigation sidebar (`320px` fixed) paired with fluid master-detail panels and adaptive modal sheets.

## Elevation & Depth

iOS 18 Dark Mode creates visual depth through translucent material physics, specular highlights, and contextual illumination rather than dense drop shadows.

### Material Backdrops
- **Ultra-Thin Material:** `background: rgba(28, 28, 30, 0.65); backdrop-filter: blur(25px) saturate(180%);` Used for floating navigation bars, bottom toolbars, and Safari address bars.
- **Thick / Chrome Material:** `background: rgba(44, 44, 46, 0.85); backdrop-filter: blur(40px) saturate(200%);` Applied to interactive sheets, context menus, and volume/brightness HUDs.
- **Dynamic Island Black:** `#000000` with continuous squircle border `rgba(255, 255, 255, 0.12)` 0.5px outline; no blur, blending directly into the OLED display cutout.

### Specular Edge & Glow Physics
- **1px Inner Border:** Glass surfaces feature a non-intrusive inner rim: `box-shadow: inset 0 1px 0 0 rgba(255, 255, 255, 0.12), inset 0 -1px 0 0 rgba(0, 0, 0, 0.40);`.
- **Album Art Ambient Backlight:** Playing screens project an animated, heavily blurred reflection of the album artwork (`blur(60px)`) at 40% opacity onto the `#000000` canvas.
- **Elevated Shadows:** When floating over multi-colored content, use smooth multi-stop shadows: `box-shadow: 0 12px 32px rgba(0, 0, 0, 0.5), 0 2px 6px rgba(0, 0, 0, 0.3);`.

## Shapes

The design system uses continuous curvature (Apple squircles) across all containers, badges, and controls.

### Curvature Hierarchies
- **Pill (Fully Rounded):** Primary action buttons, Safari floating search pills, Dynamic Island pills, segmented controls, and scrubber thumbs.
- **Container Squircles:** Inset grouped table cards and modal sheets feature 16px to 22px corner radii.
- **App Icons & Album Artwork:** Exactly 22.5% corner radius relative to component size, enclosed with a 0.5px hairline stroke (`rgba(255, 255, 255, 0.08)`).

## Components

### 1. Dynamic Island & Status Bar
- **Resting State:** Seamless pill (`height: 37px`, `border-radius: 20px`) housing the TrueDepth camera array. Flanked by left-aligned bold time (`9:41`) and right-aligned cellular signal bars, Wi-Fi icon, and battery capsule (showing system green fill `#30D158` when charging).
- **Expanded Mini-Player Activity:** Expands horizontally into a compact split capsule. Left side displays a 24x24px rounded album thumbnail; right side displays a real-time reactive neon pink/red audio waveform equalizer.

### 2. Floating Bottom Glass Bar (Safari Style)
- **Container:** Suspended pill floating 12px above the bottom safe area. Styled in `ultraThinMaterial` (`rgba(30, 30, 32, 0.75)`, `backdrop-filter: blur(30px)`), `border: 0.5px solid rgba(255, 255, 255, 0.12)`.
- **Items:** Left-aligned text size options (`AA`), centered domain label with lock privacy icon, right-aligned reload/reader triggers.
- **Interaction:** On scroll down, the pill shrinks into a compact bottom capsule showing only domain and progress bar; on tap or scroll up, it expands smoothly with spring physics.

### 3. Apple Music Now Playing Screen
- **Artwork Display:** Centered 320x320px squircle album cover with dynamic ambient colorful glow diffused behind it.
- **Track Metadata:** Primary title in `title-2` bold white; artist/album in `body` secondary label. Right-aligned heart/star favorite toggle in `#FA2D48`.
- **Scrubber Bar:** High-precision horizontal progress track (`height: 4px`, `background: rgba(255, 255, 255, 0.15)`). Filled portion colored in `#FA2D48` or solid white, terminating in an 8px circular thumb that scales to 16px on drag. Tabular mono numbers for elapsed and remaining time.
- **Transport Controls:** Play/Pause centered, oversized (64x64px), flanked by backward/forward skips. Below, volume slider and center-aligned AirPlay audio output picker displaying connected AirPods/HomePods.

### 4. Buttons & Controls
- **Pill Buttons:** Height 50px, full pill shape (`border-radius: 9999px`). Filled with primary accent (`#0A84FF` or `#FA2D48`) with bold white text. Secondary buttons use translucent dark fill (`#2C2C2E`) with primary-colored text.
- **Segmented Control:** Enclosed capsule in `#1C1C1E` containing sliding selection pill in `#3A3A3C` with subtle top highlight.

### 5. Inset Grouped Lists & Cards
- **Structure:** Rounded container (`border-radius: 16px`) set against `#000000` canvas with `#1C1C1E` row surfaces.
- **Dividers:** 0.5px hairline `#3A3A3C` insetting 16px from the leading icon edge, running flush to the right edge.
- **Disclosure Indicator:** Chevron right in tertiary label (`rgba(235, 235, 245, 0.30)`).

### 6. Inputs & Toggles
- **Switches:** Apple standard toggle (`width: 51px`, `height: 31px`). Active track fill in `#30D158` (green) or primary accent; inactive in `#3A3A3C`. Thumb is a pure white (`#FFFFFF`) floating circle with soft ambient drop shadow.
- **Search Field:** Height 36px, `border-radius: 10px`, background `#1C1C1E`. Left-aligned magnifying glass icon in tertiary label with clear button (`xmark.circle.fill`) on the right.