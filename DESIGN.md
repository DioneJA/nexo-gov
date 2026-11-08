---
name: NexoGov
description: Clear, accessible public-service workflows with the NEXO identity.
colors:
  primary: "#1769ff"
  action: "#0051d5"
  navy: "#07194f"
  control-primary: "#07194f"
  canvas: "#f8fafc"
  surface: "#ffffff"
  selection: "#eff6ff"
  text: "#0f172a"
  text-secondary: "#475569"
  text-tertiary: "#64748b"
  outline: "#cbd5e1"
  separator: "#dce1e8"
  field-outline: "#c5cdd8"
  success: "#15803d"
  success-surface: "#f0fdf4"
  success-outline: "#bbf7d0"
  danger: "#ba1a1a"
  danger-surface: "#fef2f2"
  danger-outline: "#fecaca"
typography:
  display:
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif'
    fontSize: "clamp(34px, 4.4vw, 46px)"
    fontWeight: 650
    lineHeight: 1.08
    letterSpacing: "-0.045em"
  headline:
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif'
    fontSize: "24px"
    fontWeight: 650
    lineHeight: 1.25
    letterSpacing: "-0.035em"
  title:
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif'
    fontSize: "14px"
    fontWeight: 750
    lineHeight: 1
    letterSpacing: "0.045em"
  body:
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif'
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.55
    letterSpacing: normal
  label:
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif'
    fontSize: "13px"
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: normal
rounded:
  sm: "7px"
  md: "10px"
  lg: "14px"
  xl: "18px"
spacing:
  space-1: "4px"
  space-2: "8px"
  space-3: "12px"
  space-4: "16px"
  space-5: "20px"
  space-6: "24px"
  space-7: "28px"
  space-8: "32px"
  space-10: "40px"
  space-12: "48px"
  space-16: "64px"
  space-20: "80px"
components:
  button-primary:
    backgroundColor: "{colors.control-primary}"
    textColor: "{colors.surface}"
    rounded: "{rounded.sm}"
    padding: "0 17px"
    height: "44px"
  button-outline:
    backgroundColor: "transparent"
    textColor: "{colors.control-primary}"
    rounded: "{rounded.sm}"
    padding: "0 17px"
    height: "44px"
  field-outlined:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.sm}"
    height: "44px"
  tabs-default:
    backgroundColor: "transparent"
    textColor: "{colors.text-secondary}"
    typography: "{typography.label}"
    padding: "0 18px"
    height: "50px"
  tabs-active:
    backgroundColor: "transparent"
    textColor: "{colors.navy}"
    typography: "{typography.label}"
    padding: "0 18px"
    height: "50px"
  status-label:
    textColor: "{colors.text-secondary}"
    typography: "{typography.label}"
  feedback-success:
    backgroundColor: "{colors.success-surface}"
    textColor: "{colors.success}"
    rounded: "{rounded.md}"
  feedback-error:
    backgroundColor: "{colors.danger-surface}"
    textColor: "{colors.danger}"
    rounded: "{rounded.md}"
---

# Design System: NexoGov

## Overview

**Creative North Star: "Civic Register"**

NexoGov is a working public-service desk. Its visual language takes cues from the legible hierarchy of a municipal record: the request is the page's subject, its status and next action are easy to find, and supporting guidance stays in the margin. The Apple Human Interface Guidelines inform clarity, alignment, restrained color, and familiar controls. Preserve the NEXO blue/navy family and platform system typography.

Visual reference: [NexoGov design board in Stitch](https://stitch.withgoogle.com/projects/4248314426590445887). Use it as a visual aid; this document, the consuming code, and the versioned Core tokens remain the source of truth.

The interface uses the operating system's sans-serif font stack, left-aligned content, neutral surfaces, and a small number of task-specific groupings. The NexoGov mark is a typographic wordmark; do not add a boxed initial or a stock civic emblem. Use recognizable public-service language and the real actions present in the product. This system applies to the implemented protocol request, public tracking, staff access and inbox, and organization administration. Design material must not imply that unfinished modules are available.

The public portal (`/`) and the authenticated staff area (`/servidor/`) are separate Vite application entry points. Share only neutral design tokens, API clients, and contracts. The citizen build does not import staff screens. Each surface keeps its own navigation, context, and user-facing language while the backend remains the authority for access decisions.

**Key Characteristics:**
- A typographic wordmark and clearly named public-service context.
- NEXO navy carries headings and primary actions; NEXO blue marks selection and links.
- System typography, visible keyboard focus, and responsive controls.

**The Civic Register Rule.** Put the person's current task first, show the record's actual state, and use layout only to clarify the next action.

### Public protocol portal

- Use a text-first NexoGov wordmark with no isolated letter tile.
- Give the citizen one clear entry point for opening a protocol and a separate, labeled route to follow an existing one.
- Keep fields and guidance in one aligned form region; place the actual next steps beside it as short text linked by a quiet rule.
- Use a modest header and purposeful content width. Do not center the form inside a large empty hero or repeat the task title in adjacent headings.
- Use only the organization choices, protocol fields, tracking token, and statuses provided by the API. Local fixture data must be visibly identified as local or evaluative.

### Staff work area

- Start with the authenticated inbox and keep protocol actions close to the corresponding record.
- Keep organization structure as a secondary, readable hierarchy; show only fields and permissions returned by the backend.
- Changing organization clears data and capabilities from the previous scope before another request begins.
- Include the public portal link in the staff shell; do not expose staff navigation from the citizen shell.

## Colors

The palette keeps most of the interface neutral, with navy for primary actions and NEXO blue for links and active emphasis.

### Primary
- **NEXO Navy:** headings, wordmark, and primary buttons in the public and staff applications.
- **NEXO Blue:** underline for the selected public task and focus treatment from the shared Core tokens.
- **NEXO Action Blue:** links and protocol identifiers.

### Neutral
- **Canvas and Surface:** light page ground and the top navigation bar.
- **Selection:** reserved for states that need a selected background.
- **Text, Secondary Text, and Tertiary Text:** primary content, supporting copy, and quiet metadata.
- **Field Outline and Separator:** input boundaries and quiet section dividers.

### Semantic
- **Success:** confirmation feedback uses a success surface, readable text, and a matching border.
- **Danger:** error feedback uses a danger surface, readable text, and a matching border.
- **The One Accent Rule.** Let neutral surfaces carry most of the screen; use navy for the primary action and NEXO blue for links and active emphasis.

## Typography

**Display Font:** operating-system UI sans-serif stack (`-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, `system-ui`, `sans-serif`).
**Body Font:** the same system stack; no font files are bundled.
**Label/Mono Font:** no separate label or monospace family is defined.

**Character:** compact, readable system typography with modest weight and spacing. Apple devices use their system UI face through the platform stack; other systems use their own available fallback.

### Hierarchy
- **Display** (650, responsive 34–46 px, line-height 1.08): page title.
- **Headline** (650, 22–24 px, line-height 1.25): section heading.
- **Title** (750, 14 px, uppercase with 0.045em tracking): typographic wordmark.
- **Body** (400, 14–16 px, line-height 1.5–1.65): explanatory and form-adjacent copy.
- **Label** (600, 12–13 px, line-height 1.4): compact metadata and statuses.

## Layout

The desktop shell centers content in a maximum 1360 px frame. Its top bar is 68 px tall, with 44 px horizontal padding; page content starts with 54 px top padding and 44 px side padding. The public request view places the form beside a 264 px guidance column. The staff view places the inbox beside organization structure. At 820 px these columns stack; at 600 px forms become single-column, the top bar shortens to 60 px, and page side padding becomes 18 px. Coarse-pointer controls grow to at least 48 px.

Use the shared 4 px spacing scale for new elements, while matching established local component spacing where it differs. Keep related fields grouped, headings aligned to their content, and long text within a comfortable reading width.

## Elevation & Depth

The interface is flat. Thin separators organize sections and neutral outlines define inputs; service content is not wrapped in a stack of raised cards. Keyboard focus uses the shared blue focus ring as a functional state, not as ambient elevation.

## Shapes

Inputs and buttons use restrained 7 px corners. The page has no enclosing form card, and statuses use plain text instead of pills. Keep field outlines thin and visible against the canvas.

## Components

### Buttons
- **Shape:** restrained 7 px corners, with a 44 px minimum height; coarse-pointer targets are at least 48 px.
- **Primary:** NEXO navy with white text and 17 px horizontal padding.
- **Outline:** white or transparent surface, NEXO text, and a subtle neutral outline.
- **Focus:** show the shared visible focus ring; retain a clear label and do not rely on an icon alone.

### Service Layout
- **Citizen portal:** the form and its supporting information sit directly on the page canvas, separated by alignment and a quiet rule.
- **Staff area:** the inbox leads; organization structure stays secondary and does not repeat cards around each row.

### Inputs / Fields
- **Style:** outlined white fields with a neutral border and restrained corners (7 px).
- **Focus:** stronger 2 px outline and visible keyboard focus treatment.
- **Sizing:** dense fields retain the shared 44 px minimum control height.

### Navigation
- **Style:** citizen task tabs with a 50 px minimum height, left alignment, muted inactive labels, and navy for the active tab label.
- **Active State:** a 2 px underline identifies the selected area; preserve text labels for each destination.
- **Responsive:** allow the tabs to occupy the available width on small screens.

### Status Label
- **Style:** plain secondary text with semibold weight; no capsule, border, or background.
- **Meaning:** status is written in full; color does not carry the meaning alone.

### Feedback Banner
- **Style:** success and error feedback use distinct semantic surface/text pairs and matching borders.
- **Meaning:** render as a status or alert according to the event; keep the actual message visible.

## Do's and Don'ts

### Do:
- **Do** use the system sans-serif stack and the NEXO Core token package.
- **Do** keep navy for primary actions and blue for links and active emphasis.
- **Do** provide readable text labels, visible keyboard focus, and reduced-motion support.
- **Do** make responsive changes at the implemented 820 px and 600 px breakpoints.
- **Do** show only workflows, statuses, and data that the current application actually provides.

### Don't:
- **Don't** use Apple logos, proprietary font files, SF Symbols, or Apple-specific interface chrome.
- **Don't** add unimplemented modules, fabricated metrics, sample municipal records, or unsupported claims to product screens.
- **Don't** use color as the only indicator of status, success, or error.
- **Don't** add visual effects that compete with task labels, form content, or next actions.
- **Don't** add boxed initials, eyebrow labels, repeated cards, decorative gradients, or stock civic symbols.
