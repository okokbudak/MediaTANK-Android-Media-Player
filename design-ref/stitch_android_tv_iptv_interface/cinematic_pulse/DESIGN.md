---
name: Cinematic Pulse
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#393939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#201f1f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353534'
  on-surface: '#e5e2e1'
  on-surface-variant: '#c1c6d7'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#8b90a0'
  outline-variant: '#414755'
  surface-tint: '#adc6ff'
  primary: '#adc6ff'
  on-primary: '#002e69'
  primary-container: '#4b8eff'
  on-primary-container: '#00285c'
  inverse-primary: '#005bc1'
  secondary: '#c2c1ff'
  on-secondary: '#1c0b9f'
  secondary-container: '#3834b6'
  on-secondary-container: '#b2b1ff'
  tertiary: '#ffb595'
  on-tertiary: '#571e00'
  tertiary-container: '#ef6719'
  on-tertiary-container: '#4c1a00'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#d8e2ff'
  primary-fixed-dim: '#adc6ff'
  on-primary-fixed: '#001a41'
  on-primary-fixed-variant: '#004493'
  secondary-fixed: '#e2dfff'
  secondary-fixed-dim: '#c2c1ff'
  on-secondary-fixed: '#0c006a'
  on-secondary-fixed-variant: '#3631b4'
  tertiary-fixed: '#ffdbcc'
  tertiary-fixed-dim: '#ffb595'
  on-tertiary-fixed: '#351000'
  on-tertiary-fixed-variant: '#7c2e00'
  background: '#131313'
  on-background: '#e5e2e1'
  surface-variant: '#353534'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 56px
    fontWeight: '700'
    lineHeight: 64px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 40px
    fontWeight: '600'
    lineHeight: 48px
  headline-md:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
  title-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '500'
    lineHeight: 32px
  body-lg:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 26px
  label-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  safe-margin-x: 80px
  safe-margin-y: 60px
  gutter: 24px
  card-gap: 32px
  focus-scale: '1.1'
---

## Brand & Style
The design system is engineered for a premium, high-end IPTV experience on Android TV. The brand personality is cinematic, immersive, and high-tech, focusing on content-first consumption. It utilizes a **Glassmorphic** style layered over a deep, dark canvas to provide depth and hierarchy without distracting from media thumbnails. The emotional response is one of effortless sophistication, where the interface feels like an organic extension of the television hardware. Navigation is optimized for D-pad input, utilizing scale transforms and glowing accents to provide immediate, unambiguous feedback from a 10-foot viewing distance.

## Colors
The palette is dominated by a deep charcoal background to ensure high contrast with vibrant media content. The primary **Electric Blue** is reserved exclusively for the "Focus State"—the active selection in the D-pad flow—and key action triggers. 

A subtle secondary purple-indigo is used for progress bars and secondary accents to maintain a "neon-tech" aesthetic. Surfaces use a layered approach:
- **Base Background:** Deep Charcoal (#121212) with a slight radial gradient originating from the top-left to add atmospheric depth.
- **Surface Panels:** Slightly lighter charcoal (#1C1C1E) with 40% opacity and a 20px background blur to achieve the glass effect.
- **Selection Glow:** A translucent outer glow using the primary color (20% opacity) applied to the focused element.

## Typography
Legibility is the highest priority for the 10-foot UI. **Inter** is used for its excellent x-height and clear character differentiation. 
- **Display & Headlines:** Used for movie titles and category headers. Bold weights ensure text remains crisp even on lower-resolution panels.
- **Body:** Minimum size is 18px to ensure readability from across a room. 
- **Labels:** Uppercase styling is used for metadata (e.g., "4K", "HDR", "LIVE") to distinguish it from narrative text.
- **Contrast:** All primary text is White (#FFFFFF) at 90% opacity. Secondary text/metadata is at 60% opacity.

## Layout & Spacing
The layout follows a **Fixed Grid** model optimized for 16:9 displays.
- **Overscan Safety:** A mandatory margin of 80px (horizontal) and 60px (vertical) is maintained to avoid edge clipping on older TVs.
- **Focus Dynamics:** Spacing must account for "Focus Scaling." When a card is selected, it expands by 10% (`1.1 scale`). Layout containers must have sufficient padding to prevent focused cards from clipping adjacent elements.
- **Horizontal Rails:** Content is organized in horizontal scrolling rows. The active row sits vertically centered or slightly below center to leave room for large hero metadata at the top.

## Elevation & Depth
Depth is communicated through **Glassmorphism** and **Z-axis scaling** rather than traditional drop shadows.
- **Layer 0:** Background with subtle ambient gradients.
- **Layer 1:** Content cards and rail containers.
- **Layer 2 (Focus):** When an element is focused, it scales up and gains a 2px solid border in the primary color, accompanied by a soft blue outer glow (blur: 24px).
- **Layer 3:** Overlays, Modals, and Side Navigation. These use a 60% blur intensity to obscure the background content while maintaining the cinematic mood.

## Shapes
This design system utilizes a consistent **16px (1rem)** corner radius for all primary UI components, including thumbnail cards, buttons, and input fields. 
- **Cards:** 16px radius.
- **Selection Brackets:** If a focus ring is used instead of a full border, it should mirror the 16px radius of the card it surrounds.
- **Modals:** Larger containers use `rounded-xl` (24px) to feel more like a physical overlay.

## Components
- **Media Cards:** Aspect ratios are 2:3 (Movie posters) or 16:9 (Live TV/Episodes). On focus, the title and metadata (Year, Rating) appear below the card or scale with it.
- **Sidebar Navigation:** Icons-only when collapsed; expands to icons + text on focus. The active tab is indicated by a vertical "pill" of the primary color.
- **Buttons:** Fully rounded (pill-shaped) for secondary actions; 16px rounded for primary CTA. On focus, the button color shifts to the primary electric blue.
- **Channel List (EPG):** A horizontal timeline. The "Current Time" is marked by a vertical primary color line. Focused programs expand to show a brief synopsis.
- **Status Badges:** Small, high-contrast labels for "LIVE", "4K", or "NEW". These use the `label-sm` type style with a subtle glass background.
- **Search Input:** A large, full-width glass component. On selection, the on-screen keyboard appears, and the input field gains the primary blue glow.