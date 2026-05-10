# CleanShare — Agent Guidance

## Design System

All compose modifier sizes must come from `app/src/main/java/com/maroney/cleanshare/ui/Dimensions.kt`. Raw `dp` literals are not allowed in UI files for padding, image sizes.

### Allowed scale

| Token | Value | Use for |
|-------|-------|---------|
| `Spacing.hairline` | 1 dp | Borders, dividers |
| `Spacing.xs` | 2 dp | Tight gaps between text lines |
| `Spacing.sm` | 8 dp | Gaps between related elements, small padding |
| `Spacing.md` | 16 dp | Standard padding, gaps between unrelated elements |
| `Spacing.lg` | 32 dp | Large padding, page margins |
| `Radius.sm` | 4 dp | Subtle rounding |
| `Radius.md` | 8 dp | Standard card / icon rounding |
| `IconSize.thumbnail` | 64 dp | Leading thumbnail images |
| `IconSize.favicon` | 32 dp | Site favicons |

If a value does not map to one of the tokens above, ask the user which token to use rather than introducing a new raw value.

## Git Workflow

- All commits go directly to `main` (no feature branches, no PRs).
- The pre-commit hook at `.githooks/pre-commit` runs unit tests **and** instrumented tests before every commit. Do not bypass it with `--no-verify`.
- To activate the hook in a fresh clone: `git config core.hooksPath .githooks`

## Custom Color System

Semantic colors live in `CleanShareColors` (see `ui/theme/CleanShareColors.kt`) and are provided via `LocalColors`. Always prefer `LocalColors.current.*` over hard-coded `Color(...)` values in composables.

## Testing

- Unit tests: `./gradlew test`
- Instrumented tests: `./gradlew connectedDebugAndroidTest` (requires connected device/emulator)
- Both must pass before committing.
