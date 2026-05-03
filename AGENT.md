# AGENT.md — Android "Clean Share" Build Script

> Paste this file as the system / project prompt for a coding agent
> (Claude Code, Cursor, Copilot Workspace, Aider, etc.).
> The agent MUST follow this document literally. Do not improvise scope.

---

## 1. Mission

Build a minimal Android app named **Clean Share** that registers as a
share target for `text/plain`, strips tracking parameters from any URL
it receives, and re-opens the system share sheet with the cleaned URL.

The deliverable is a buildable Android Studio project with a green
unit-test suite. Nothing else.

---

## 2. Hard Constraints

You MUST:
- Use Kotlin + Jetpack Compose. No Java, no XML layouts (manifest aside).
- Keep the sanitizer **pure** (no Android imports, no I/O, no logging).
- Write tests for the sanitizer **before** wiring it into the activity.
- Use only the Android SDK + Kotlin stdlib + JUnit 4. No DI framework,
  no networking library, no analytics SDK, no Room, no Hilt.
- Commit per task (T1, T2, …). One logical change per commit.

You MUST NOT:
- Add background services, foreground services, or WorkManager jobs.
- Read the clipboard, request `READ_LOGS`, or any runtime permission.
- Make any network call.
- Add Firebase, Crashlytics, Sentry, or any telemetry.
- Add a launcher icon activity or a "main" UI. The app is share-only.
- "Improve" the spec. If a requirement is missing, ask once, otherwise
  pick the smallest reasonable choice and document it in `DECISIONS.md`.

---

## 3. Tech Stack (pinned)

Use the scaffold provided by Android Studio in this directory.

---

## 4. Final File Layout

```
.
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/...
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── com/maroney/androidsharesanitzer/
│       │       ├── ShareActivity.kt
│       │       ├── domain/UrlSanitizer.kt
│       │       └── ui/SharePreviewScreen.kt
│       └── test/
│           └── kotlin/com/maroney/androidsharesanitzer/UrlSanitizerTest.kt
├── AGENT.md           (this file — do not modify)
├── DECISIONS.md       (you create — log every micro-decision here)
└── README.md          (you create at the end — see T8)
```

---

## 5. Execution Protocol

Run a strict TDD-shaped loop:

1. Read the task block (T1 … T8).
2. Write or update `DECISIONS.md` with anything ambiguous you resolved.
3. Make the smallest code change that addresses the task.
4. Run the verification command listed under **Verify**.
5. If verification fails, fix and retry. **Do not advance to the next
   task with a red build or red tests.**
6. Commit with the message format: `T<n>: <short summary>`.
7. Move to the next task.

If you get stuck for more than two retries on the same failure,
STOP and surface the blocker — do not silently disable tests, do not
add `@Ignore`, do not catch-and-swallow exceptions.

---

## 6. Tasks

### T1 — Project scaffold

**Goal.** Create a buildable empty Android project per §3 / §4.

**Do.**
- `settings.gradle.kts` declares `:app` and `pluginManagement` for AGP + Kotlin.
- `app/build.gradle.kts` enables Compose, sets namespace `com.cleanshare`,
  configures `applicationId = "com.cleanshare"`.
- Single empty `ShareActivity` in the manifest; no launcher intent filter yet.

**Verify.** `./gradlew :app:assembleDebug` succeeds.

---

### T2 — Register the share target

**Goal.** App appears in the system share sheet for `text/plain`.

**Do.** In `AndroidManifest.xml`, declare `ShareActivity` with:

```xml
<activity
    android:name=".ShareActivity"
    android:exported="true"
    android:theme="@style/Theme.Material3.DayNight.NoActionBar"
    android:excludeFromRecents="true"
    android:noHistory="true">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

There is **no** `MAIN` / `LAUNCHER` activity. Do not add one.

**Verify.** `./gradlew :app:assembleDebug` succeeds. Manual: install APK,
share a link from any app → "Clean Share" appears in the chooser.

---

### T3 — `UrlSanitizer` + tests (TDD)

Write the tests first, then the implementation.

**Contract.**

```kotlin
package com.cleanshare.domain

object UrlSanitizer {
    /**
     * Returns the input URL with known tracking parameters removed.
     * If [input] cannot be parsed as a URL, returns [input] unchanged.
     * Parameter order of the surviving params is preserved.
     */
    fun clean(input: String): String
}
```

**Rules.**

Strip these query parameters (case-insensitive on the key):
- `si`
- any key matching `utm_*` (e.g. `utm_source`, `utm_medium`, `utm_campaign`,
  `utm_term`, `utm_content`, `utm_id`)
- `fbclid`
- `igshid`
- `igsh`
- `feature` (YouTube share variant)
- `mc_cid`, `mc_eid` (Mailchimp)
- `gclid`, `dclid`, `gbraid`, `wbraid` (Google Ads)
- `yclid` (Yandex)
- `ref`, `ref_src`, `ref_url` (generic referrer)
- `_hsenc`, `_hsmi`, `__hstc`, `__hssc`, `__hsfp` (HubSpot)

Always preserve (allowlist beats denylist if there's any conflict):
- `t`, `start` (YouTube timestamp)
- `v` (YouTube watch ID)
- `list`, `index` (YouTube playlist context)

Behavior:
- If the cleaned URL has zero query params left, drop the trailing `?`.
- Preserve URL fragment (`#…`) untouched.
- Preserve scheme, host, path, and port exactly.
- If parsing throws, return `input` verbatim — never crash.

**Test matrix.** All of these MUST pass:

| # | Input | Expected output |
|---|-------|-----------------|
| 1 | `https://youtu.be/abc123?si=xyz&t=45` | `https://youtu.be/abc123?t=45` |
| 2 | `https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share&t=10` | `https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10` |
| 3 | `https://www.instagram.com/reel/CxYz/?igshid=abc123` | `https://www.instagram.com/reel/CxYz/` |
| 4 | `https://example.com/page?utm_source=newsletter&utm_medium=email&id=42` | `https://example.com/page?id=42` |
| 5 | `https://example.com/page?UTM_SOURCE=NL&id=42` | `https://example.com/page?id=42` |
| 6 | `https://example.com/page?fbclid=xyz` | `https://example.com/page` |
| 7 | `https://example.com/page` | `https://example.com/page` |
| 8 | `not a url` | `not a url` |
| 9 | `""` | `""` |
| 10 | `https://example.com/p?utm_source=x#section-2` | `https://example.com/p#section-2` |
| 11 | `https://example.com/p?a=1&utm_source=x&b=2` | `https://example.com/p?a=1&b=2` |
| 12 | `https://example.com:8443/p?si=x&q=ok` | `https://example.com:8443/p?q=ok` |

Add at least three more cases of your own that cover repeated keys
(e.g. `?a=1&a=2&utm_source=x`) and URL-encoded values.

**Verify.** `./gradlew :app:testDebugUnitTest` — all green.

Implementation hint: don't use `java.net.URL` (it can't reliably round-trip
modern URLs). Use `android.net.Uri`'s parser — but since the sanitizer is
pure Kotlin in `domain/`, depend on `androidx.core` is OK, OR write a tiny
parser using `String.split('?', limit = 2)` and `String.split('#', limit = 2)`.
Whichever you pick, document it in `DECISIONS.md`.

---

### T4 — Intent handling in `ShareActivity`

**Goal.** Extract the shared text, sanitize it, prepare for re-share.

**Do.** In `ShareActivity.onCreate`:

```kotlin
val raw = intent?.takeIf { it.action == Intent.ACTION_SEND }
    ?.getStringExtra(Intent.EXTRA_TEXT)
    ?.trim()

if (raw.isNullOrEmpty()) { finish(); return }

val cleaned = UrlSanitizer.clean(raw)
```

If the share contains multiple whitespace-separated URLs, sanitize each
and join with a single space. Document this choice in `DECISIONS.md`.

**Verify.** `./gradlew :app:assembleDebug` succeeds. Add a unit test for
the multi-URL helper if you extract one.

---

### T5 — Compose preview UI

**Goal.** Show the cleaned URL with two buttons: **Share** and **Copy**.

**Do.** `SharePreviewScreen(original, cleaned, onShare, onCopy)`:
- Two read-only text blocks labeled "Original" and "Cleaned".
- If `original == cleaned`, show a subtle "Nothing to clean" hint and
  still allow Share / Copy.
- Material 3, system theme, no custom colors.
- No scaffold app bar — this is a transient activity.

`ShareActivity` calls `setContent { SharePreviewScreen(...) }`.

**Verify.** `./gradlew :app:assembleDebug` succeeds. Manual smoke check
on a device or emulator: share a YouTube link, see preview.

---

### T6 — Re-share intent

**Goal.** "Share" button opens the system chooser with the cleaned URL.

**Do.**

```kotlin
val send = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, cleaned)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
startActivity(Intent.createChooser(send, "Share cleaned link"))
finish()
```

To avoid Clean Share appearing in its own chooser (infinite-loop UX),
exclude this app's component via
`Intent.EXTRA_EXCLUDE_COMPONENTS` on Android N+.

**Verify.** Manual: tap Share, the system chooser appears, Clean Share
itself is NOT listed, target apps receive the cleaned URL.

---

### T7 — Copy-to-clipboard

**Goal.** "Copy" button puts cleaned URL on the clipboard, finishes activity.

**Do.** Use `ClipboardManager` + `ClipData.newPlainText("Cleaned URL", cleaned)`.
On Android 13+ the system shows its own confirmation toast — don't add your own.

**Verify.** Manual.

---

### T8 — README + final gate

**Goal.** Write `README.md` (≤ 40 lines) covering: what it does, how to
build, how to run tests, supported tracking params (link to source of truth
in `UrlSanitizer.kt`), known limitations.

Then run the **Final Verification Gate** (§7). The build is only done
when every gate item is green.

---

## 7. Final Verification Gate

All of the following MUST hold before declaring done:

- [ ] `./gradlew clean :app:assembleDebug :app:testDebugUnitTest` exits 0.
- [ ] Lint clean: `./gradlew :app:lintDebug` — zero `error` severity.
- [ ] No file in `domain/` imports anything from `android.*` or `androidx.*`
      except `androidx.core.net.Uri` (if you chose that path in T3).
- [ ] All 12 test cases from §T3 pass, plus the 3 you added.
- [ ] `AndroidManifest.xml` contains exactly one `<activity>` and zero
      `<service>` / `<receiver>` / `<provider>` elements.
- [ ] `grep -R "INTERNET" app/src/main` returns nothing.
- [ ] `DECISIONS.md` documents every ambiguity you resolved.

---

## 8. Failure Modes (must be handled, not crash)

| Input situation | Required behavior |
|---|---|
| `EXTRA_TEXT` is null or blank | `finish()` immediately, no UI, no toast |
| `EXTRA_TEXT` is text but no URL | Treat the whole string as the URL; sanitizer returns it unchanged |
| URL parsing throws | Return the input verbatim |
| User taps Back on preview | `finish()`, no side effects |
| User shares from Clean Share itself (loop) | Excluded via `EXTRA_EXCLUDE_COMPONENTS`; if it still happens, treat as normal input |

---

## 9. Out of Scope (do not build, even if tempted)

- Configurable param rules / settings screen
- Per-domain cleaning strategies
- Direct Share targets / sharing shortcuts
- Multi-URL detection beyond the simple whitespace split in T4
- Dark-mode custom theme (system theme only)
- Crash reporting
- A launcher icon

These belong to v1.1+ and are explicitly deferred.

---

## 10. Definition of Done

The repository, when cloned fresh, satisfies:
1. `./gradlew :app:assembleDebug :app:testDebugUnitTest` is green.
2. APK installs on an Android 8.0+ device.
3. Sharing a YouTube `youtu.be/...?si=...&t=...` URL from any app
   produces a chooser whose payload is the URL with `si` removed and
   `t` preserved.
4. README explains the above in ≤ 40 lines.

When all four hold: stop. Do not add features.
