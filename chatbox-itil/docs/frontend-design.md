# Frontend visual system

The frontend uses native HTML, CSS and JavaScript. No UI framework, package installation or build step is required.

## Files

- `public/design-tokens.css`: shared colors, type, spacing and focus states.
- `public/styles.css`: agent workspace, ticket rows, full timeline, assistant, knowledge, metrics and responsive layouts.
- `public/ui.js`: presentation-only navigation, focus management, accessible dialogs and notifications.
- `public/assets/icons.svg` and `public/assets/assistant-mark.svg`: original geometric artwork for this interface. Inline versions avoid depending on SVG MIME configuration in the current static server.
- `public/assets/fonts/InterVariable.woff2`: locally hosted Inter, distributed under the accompanying `OFL.txt` license. Source: https://github.com/rsms/inter .

Institution-specific content must continue to use the application configuration. These design assets do not define categories, teams, shifts, metrics, business rules or API contracts.

## Layout

Desktop navigation is 232 px wide, reduced to 208 px on laptops. Below 1024 px navigation becomes a keyboard-accessible drawer. Below 768 px tickets, forms and assistant context stack vertically. The dashboard uses the existing metrics response; bars are a visual representation of category counts, not new business indicators.

Ticket detail renders every original log entry, including author, timestamp, full text and minutes. AI conversation content remains separate from that history. Loading, error and empty states are visually distinct. Reduced-motion preferences disable transitions and repeated animations.

## Verification

Run `node tests/redesign-smoke.cjs` with Node 22 and an installed Chrome or Edge. If needed, set `BROWSER_PATH` to a Chromium executable.

The check starts an isolated static server with simulated API responses and a temporary browser profile. It never starts the application backend or writes production data. Screenshots are written to the temporary directory printed by the command.

Coverage includes the four main views at 360, 768, 1024, 1366 and 1920 px, layout overflow, local fonts, complete ticket history, modal focus restoration, Escape, mobile navigation, knowledge forms, cancellation of deletion, comment payloads, assistant sources, empty states, reduced motion.

Live backend integration and generated AI responses are outside this isolated visual test. Existing business operations and API handlers are retained.
