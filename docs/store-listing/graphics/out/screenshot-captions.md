# Screenshot Captions — PrismTask

Each row lists the primary caption baked into the SVG plus one alternate variant if the primary does not land. Swap in the alt by editing the SVG `<text>` nodes before re-rendering.

| # | File (SVG / PNG) | Theme | Primary caption | Alternate caption |
|---|---|---|---|---|
| 1 | `01-today-cyberpunk` | Cyberpunk | Your day, organized. | Today, on one screen. |
| 2 | `02-eisenhower-synthwave` | Synthwave | Auto-prioritized by AI. | What matters, made obvious. |
| 3 | `03-habits-matrix` | Matrix | Streaks that forgive. | Habits that survive real life. |
| 4 | `04-pomodoro-void` | Void | Focus, on your terms. | Deep work without the timer-shaming. |
| 5 | `05-ai-weekly-review-cyberpunk` | Cyberpunk | Reflect with AI. | A weekly review that writes itself. |
| 6 | `06-ai-time-block-synthwave` | Synthwave | Plan your week in seconds. | Your calendar, blocked for focus. |
| 7 | `07-theme-picker-all` | All 4 (composite) | Four themes. Your mood. | Four looks. One app. |
| 8 | `08-onboarding-matrix` | Matrix | Set up in under a minute. | Zero to useful, fast. |

## Primary vs alternate — when to swap

- **Ship the primary** if the closed-testing audience is mixed (developers, designers, general productivity users).
- **Swap to alternates 3/4/5/8** if store-listing performance data shows the primary is confusing beta readers (ambiguous "forgive"? the alt is more literal).
- **Never** edit captions without updating the test-plan for the Phase 3 verification check — char counts and font-fit depend on caption length.

## Secondary subtitle lines

Each screenshot also carries a secondary subtitle in theme-accent color underneath the primary caption. These are subordinate and can be freely edited to match the final feature set. Current values are baked into each SVG's closing `<text>` nodes.
