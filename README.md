# Clean Share

Android share-target that strips tracking parameters from URLs before re-sharing.

## What it does

Appears in the system share sheet for `text/plain`. Removes known tracking
params, shows a before/after preview, then lets you **Share** (opens a new
chooser with the cleaned link) or **Copy** (puts it on the clipboard).
No network calls, analytics, or persistent state.

## Build

```bash
./gradlew :app:assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

## Tests

```bash
./gradlew :app:testDebugUnitTest   # 18 JVM unit tests, no device needed
```

## Stripped tracking parameters

See `DENY_SET` in [`UrlSanitizer.kt`](app/src/main/java/com/maroney/androidsharesanitizer/domain/UrlSanitizer.kt):

`utm_*` · `si` · `feature` · `fbclid` · `igshid` · `igsh` · `mc_cid` ·
`mc_eid` · `gclid` · `dclid` · `gbraid` · `wbraid` · `yclid` · `ref` ·
`ref_src` · `ref_url` · `_hsenc` · `_hsmi` · `__hstc` · `__hssc` · `__hsfp`

Always preserved (YouTube playback context): `t` `start` `v` `list` `index`

## Known limitations

- Param list is hard-coded; no settings screen (v1.1+).
- Multi-URL detection splits on whitespace only.
- Share-only app — no launcher icon or home-screen entry point.

## Potential Features
- Ability to reshare from the CleanShare app
- Item details
  - See the full url vs trimmed url
  - Description
  - Add in notes section
- Widget
  - Last several shared items
  - Quick access to reshare
- Use-case: Share to CleanShare → Send to common contacts?