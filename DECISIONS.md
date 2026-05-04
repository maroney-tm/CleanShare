# DECISIONS.md — Clean Share Agent Log

## T1: Package / namespace conflict

**Ambiguity:** T1 specifies `namespace = "com.cleanshare"` and
`applicationId = "com.cleanshare"`, but §3 says "Use the scaffold provided
by Android Studio in this directory" and §4's file layout shows paths under
`com/maroney/androidsharesanitzer/`.

**Decision:** Keep the scaffold's existing namespace
`com.maroney.androidsharesanitizer` and applicationId
`com.maroney.androidsharesanitizer`. The §4 file layout (which uses maroney
paths) is treated as the authoritative guide; T1's `com.cleanshare` names
are generic placeholders that contradict the actual scaffold.

## T1: Activity naming

**Ambiguity:** Scaffold generates `MainActivity`. T1 requires `ShareActivity`.

**Decision:** Delete `MainActivity.kt` and create `ShareActivity.kt` in its
place. The `MainActivity` was the only activity in the scaffold; the app
must be share-only per §2.

## T1: Launcher icon / resources

**Ambiguity:** The scaffold includes launcher icons and mipmap resources,
but §9 says "no launcher icon". §2 says "no launcher icon activity".

**Decision:** The spec prohibits a launcher activity (MAIN/LAUNCHER intent
filter), not the icon assets themselves. Assets are left in place — removing
them is not required and they cause no functional harm. The manifest will
not declare any MAIN/LAUNCHER activity.

## T2: Window theme for ShareActivity

**Ambiguity:** T2 specifies `android:theme="@style/Theme.Material3.DayNight.NoActionBar"`.
This style is only available if `com.google.android.material:material` is
on the classpath. The Compose material3 BOM does not provide XML activity
themes.

**Decision:** Define `Theme.Material3.DayNight.NoActionBar` in `themes.xml`
as an alias of `android:Theme.Material.Light.NoActionBar` (which is part of
the Android framework). This satisfies the manifest reference without adding
a new dependency.

## T3: UrlSanitizer — parsing approach

**Ambiguity:** The hint in T3 offers two choices: `android.net.Uri` /
`androidx.core.net.Uri`, or a tiny String-based parser.

**Decision:** Use the String-based parser (`split('?', limit=2)` /
`split('#', limit=2)`). Reasons:
1. Unit tests run on the JVM; `android.net.Uri` is not available without
   Robolectric or Android instrumentation.
2. Keeping `domain/` free of any Android imports satisfies the §7 gate
   check with zero exceptions.
3. The parser is self-contained and straightforward for the URL shapes
   this app handles.

## T4: Multi-URL handling

**Ambiguity:** T4 says "if the share contains multiple whitespace-separated
URLs, sanitize each and join with a single space."

**Decision:** Split `raw` on `\s+` regex, call `UrlSanitizer.clean()` on
each token, re-join with a single space. This is the simplest interpretation
of "whitespace-separated". Edge cases (leading/trailing spaces, tabs) are
collapsed into a single space.

## T5: No Scaffold / AppBar

**Decision:** `SharePreviewScreen` uses a plain `Column` with vertical
scroll — no `Scaffold`, no `TopAppBar`. The activity is transient; an app
bar would feel wrong and the spec explicitly says "no scaffold app bar."

## T6: Self-exclusion from chooser

**Decision:** `Intent.EXTRA_EXCLUDE_COMPONENTS` is used on all API levels
(minSdk = 33, well above the N requirement). No API-level guard needed.

---

## Revamp (post-T8)

### Architecture change: transparent trampoline + history launcher

**Change:** User confirmed the desired flow is:
- Share → `ShareActivity` (invisible) → strip → save → Android Sharesheet
- Launcher → `MainActivity` → history screen

`SharePreviewScreen.kt` deleted; `ShareActivity` now has no Compose UI at all.

**Decision:** `ShareActivity` uses `Theme.Transparent` (defined in `themes.xml`).
This keeps the calling app visible behind the trampoline during the brief
coroutine execution (Room write + `startActivity` + `finish`).

### Storage: Room DB (not SharedPreferences)

**Decision:** User explicitly ruled out SharedPreferences and requested Room.
`ShareDatabase` is a singleton via `companion object` in the database class —
no custom `Application` subclass needed to avoid manifest churn.

### KSP version

**Decision:** KSP `2.2.10-1.0.25` chosen to match Kotlin `2.2.10`.
If the build rejects this, update `ksp` in `libs.versions.toml` to the
exact KSP release that ships for this Kotlin version:
https://github.com/google/ksp/releases

### Launcher activity added

**Decision:** Original spec forbade a launcher activity. User has explicitly
requested one for the history screen. `MainActivity` is now the MAIN/LAUNCHER
entry point. The spec constraint is superseded by user intent.
