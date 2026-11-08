---
name: nexogov-design-system
description: Build or revise NexoGov screens using the NEXO family visual standard, accessible states, and the versioned Core UI tokens.
---

# NexoGov design system

1. Read `DESIGN.md`; it is the product visual contract. Use `PRODUCT.md` and the actual API implementation to ground labels, data, and available actions.
2. Import the pinned `@nexo/core-ui/tokens.css` package. Do not import NexoLar source code or copy its logo, photographs, condominium navigation, domain labels, or data.
3. Apply the Apple HIG-inspired system as the default on every new or changed screen: clear hierarchy, deliberate alignment and whitespace, restrained color, simple controls, readable states, and platform system typography. Preserve the NEXO blue/navy identity. Do not use Apple marks, SF Symbols assets, proprietary fonts, or copied native chrome. Use another visual system only when the user explicitly requests it.
4. Treat the public citizen portal (`/`) and staff area (`/servidor/`) as separate application entry points. The citizen bundle must not import or display staff pages; share only neutral API clients, contracts, tokens, and proven components. Never rely on the route or hidden controls for authorization.
5. Keep surfaces light, use `@nexo/core-ui` system-font tokens, readable hierarchy, deliberate whitespace, and restrained elevation. Use the shared 4 px spacing scale and tokenized radii.
6. Ground brand marks in the actual product identity. NexoGov uses a text wordmark; do not replace it with a boxed initial, generated symbol, or stock civic emblem.
7. Only expose workflows already implemented or being implemented in the same vertical slice. Never fill a screen with invented metrics, records, statuses, municipal facts, integrations, or compliance claims.
8. Include loading, empty, error, and success states for user actions. Keep server-side authorization authoritative; visual controls are not access control.
9. Verify keyboard focus, contrast, responsive layouts, clear text statuses, and reduced motion. Run the frontend build and applicable E2E workflow after UI edits.
10. In the citizen protocol form, present organization-configured online subjects as a labeled select and explain an empty catalog; do not accept free-text subjects that bypass the server catalog. In organization administration, pair each subject with a readable online/balcão state and a labeled control; tenant changes must clear stale catalog data.
11. For nested subject configuration, use a labeled disclosure and load data on demand to avoid an initial N+1 request. Keep the view text-first with thin separators, the system spacing scale, and compact labeled controls rather than adding another raised card. Show loading, recoverable error/retry, empty, saved, and removal-confirmation states. State clearly when configuration metadata exists but its dependent public workflow does not; a settings screen must not imply that unfinished participant registration or access is available.
12. Implement destructive confirmations with an accessible in-app dialog rather than `window.confirm`; this keeps the interaction keyboard-operable and lets the real-API browser test cover the same visible confirmation users see.
13. In the citizen portal, treat invitation links as bearer credentials. Bind any one-time invitation/receipt display to its protocol and applicant token, clear it when the credential changes, clear tracking immediately, and ignore stale async responses from older lookups. Never persist raw invite tokens; erase token fragments before the first request and verify in independent browser contexts. Explain the data use before collecting someone else's details and state plainly that possession of the link grants access; a supplied name is not identity verification.
14. Use container queries for reusable admin panels that can become narrow inside a wide desktop layout. A viewport-only media query misses a settings column beside a primary workspace. Keep stable field identity readable, stack creation controls when the panel is narrow, and verify that row controls fit without horizontal scrolling or overlap in the real browser test.
