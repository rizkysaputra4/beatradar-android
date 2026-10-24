# Languages: what to translate and how

The single reference for app translations. **Read it before adding a language or changing app text, and update it
in the same change** (the tables below, the prefix map, and `tools/translate-prompt.txt` §6 when a prefix is added).
Last updated 2026-10-24.

## What gets translated

- **In packs:** every `<string>` and `<plurals>` in `app/src/main/res/values/strings*.xml`, except `app_name`,
  `widget_description` and `tile_label` (the system shows those, not the app) and anything `translatable="false"`.
  That's **633 strings + 31 plurals** today. `tools/export_strings.py` defines this set (`english()`, `SKIP`).
- **A pack** is `{ "name", "nativeName", "strings": { key: text } }`. A plural is one key per CLDR form,
  `name#one`, `name#other`, … Keys left out show in English.
- **Not in packs (English only today):** emails (`beatradar-backend/templates/email.html`, `src/auth/mod.rs`), the
  web pages (share, verify, reset, delete, privacy), the backoffice, notification channel names set by the system,
  the Play Store listing.

## Where the strings are (key prefix = screen = download chunk)

The prefix (text before the first `_`) is also the chunk the phone downloads (`i18n/LanguageSync.kt`). **CORE**
chunks arrive before the app switches language (first screens, Quick Setup, notifications, widget); the rest follow
in **ORDER**; an unlisted prefix comes last, so its screen shows English for a while.

Regenerate this table with `python tools/export_strings.py --inventory`.

| Prefix (chunk) | Strings | Plurals | Source file | Download | Screen |
|---|---:|---:|---|---|---|
| `app_` | 71 | 0 | strings_main.xml | CORE | App frame: tabs, titles, dialogs, tip sheet, update screen (`app_update_`), **Quick Setup** (`app_setup_`), Library consent texts (`app_consent_`) |
| `sets_` | 57 | 6 | strings_library.xml | ORDER #5 | Sets tab: own playlists, building and sharing a set |
| `settings_` | 60 | 1 | strings_settings.xml | ORDER #4 | Settings sheet, incl. "Email not verified" row |
| `account_` | 54 | 0 | strings_settings.xml | ORDER #9 | Sign in, create account, email code, password reset with code |
| `badge_` | 48 | 2 | strings_system.xml | ORDER #8 | Badge names, descriptions, celebration |
| `library_` | 45 | 2 | strings_library.xml | ORDER #2 | Discover tab: search, filters, sorting |
| `log_` | 44 | 1 | strings_settings.xml | ORDER #1 | Log tab: history, naming unnamed songs |
| `listen_` | 39 | 2 | strings_main.xml | CORE | Listen tab: dial, live BPM, presets, recent songs |
| `shared_` | 33 | 0 | strings_settings.xml | ORDER #13 | "What I've shared" |
| `song_` | 27 | 5 | strings_library.xml | ORDER #3 | Song page: tempo, votes, links, reports |
| `profile_` | 30 | 2 | strings_system.xml | ORDER #7 | Profile page: stats, pictures, streak, tempo zone, pinned sets |
| `rewards_` | 26 | 2 | strings_system.xml | ORDER #6 | Beats, leaderboards, rewards sheet |
| `notif_` | 21 | 4 | strings_system.xml | CORE | Notifications: listening, match, weekly recap |
| `recap_` | 14 | 3 | strings_main.xml | ORDER #12 | Weekly recap card |
| `language_` | 15 | 0 | strings_language.xml | CORE | Language picker |
| `playlist_` | 11 | 1 | strings_library.xml | ORDER #11 | "BPM of my playlist" |
| `practice_` | 10 | 0 | strings_library.xml | ORDER #10 | Practice click (metronome) |
| `tempo_` | 7 | 0 | strings_main.xml | CORE | Target range editor (Listen tab, Quick Setup step 1) |
| `main_` | 6 | 0 | strings_main.xml, strings_system.xml | CORE | Small app-wide messages and toasts |
| `widget_` | 6 | 0 | strings_system.xml | CORE | Home-screen widget |
| `tile_` | 4 | 0 | strings_system.xml | CORE | Quick Settings tile |
| `billing_` | 3 | 0 | strings_settings.xml | ORDER #15 | Tip sizes |
| `data_` | 2 | 0 | strings_system.xml | CORE | Default song labels ("Song #3") |
| **total** | **633** | **31** | | | |

## Languages

Packs live in `tools/packs/<code>.json` and are **the source of truth** (the old `tools/build_<lang>.py` builders are
gitignored and stale: running one drops strings). "Missing" counts string names with no translation (from
`--inventory`). "Server" = uploaded through BO → Languages; all packs except `ar` were uploaded on 2026-10-24.

| Code | Language | Native name | Plural forms | Missing | Server | Notes |
|---|---|---|---|---:|---|---|
| `en` | English | English | one other | 0 | built in | Generated: `python tools/export_strings.py > tools/packs/en.json` (gitignored) |
| `id` | Indonesian | Bahasa Indonesia | other | 0 | uploaded 10-24 | |
| `de` | German | Deutsch | one other | 0 | uploaded 10-24 | |
| `es` | Spanish | Español | one other | 0 | uploaded 10-24 | |
| `fr` | French | Français | one other | 0 | uploaded 10-24 | |
| `pt-BR` | Portuguese (Brazil) | Português (Brasil) | one other | 0 | uploaded 10-24 | |
| `ja` | Japanese | 日本語 | other | 0 | uploaded 10-24 | `settings_delete_text` describes the older English |
| `zh-CN` | Chinese (Simplified) | 简体中文 | other | 0 | uploaded 10-24 | |
| `ru` | Russian | Русский | one few many other | 0 | uploaded 10-24 | |
| `ko` | Korean | 한국어 | other | 0 | uploaded 10-24 | New 10-24, polite 해요체 |
| `vi` | Vietnamese | Tiếng Việt | other | 0 | uploaded 10-24 | New 10-24 |
| `th` | Thai | ไทย | other | 0 | uploaded 10-24 | New 10-24; Log = ประวัติ |
| `ms` | Malay | Bahasa Melayu | other | 0 | uploaded 10-24 | New 10-24; Malaysian vocabulary, "anda" |
| `tr` | Turkish | Türkçe | one other | 0 | uploaded 10-24 | New 10-24; no suffixes glued to placeholders |
| `it` | Italian | Italiano | one many other | 0 | uploaded 10-24 | New 10-24 |
| `pl` | Polish | Polski | one few many other | 0 | uploaded 10-24 | New 10-24 |
| `hi` | Hindi | हिन्दी | one other | 0 | uploaded 10-24 | New 10-24; formal आप; badge names kept in English |
| `fil` | Filipino | Filipino | one other | 0 | uploaded 10-24 | New 10-24; Taglish; badge names kept in English |
| `ar` | Arabic | العربية | zero one two few many other | 0 | **don't publish** | New 10-24 (MSA); blocked by the RTL work below |

All new packs (10-24) were machine-translated by Claude; have a native speaker review them before or soon after
publishing, starting with badge names, `listen_confidence_bars` ("bars" = musical measures) and the recap texts.

## When you add or change app text (screens, settings, messages)

1. Put the English in the right `strings_*.xml`, using an existing prefix from the map above. Read it in code through
   the i18n layer (`str()` / `Context.str()` in `i18n/Strings.kt`), never `stringResource` directly.
2. **New prefix?** Add it to `CORE` (shown before or on the first screens) or `ORDER` in `i18n/LanguageSync.kt`, to
   the map above, and to `tools/translate-prompt.txt` §6.
3. `python tools/export_strings.py > tools/packs/en.json`.
4. Add the translation of every new or changed key to **every** pack in `tools/packs/` (edit the JSON; keep each
   file's style: UTF-8 without BOM, indent 2, `ensure_ascii=False`). A changed English text means re-translating that
   key everywhere; a removed string is removed from every pack.
5. `python tools/export_strings.py --check tools/packs/<code>.json` for every pack: it must say `OK: ready to upload`.
6. Re-run `--inventory` and update the tables in this file (counts, notes).
7. Uploading the packs to the server (BO → Languages, or `--sql`) is a production change: ask first.

## Adding a language

1. Pick the code (BCP 47 as the server and app use it: `id`, `pt-BR`, `zh-CN`, `fil`) and its CLDR plural forms.
2. Translate `tools/packs/en.json` following `tools/translate-prompt.txt` §1–8 (tone, placeholders, plurals,
   do-not-translate list, glossary consistency). `python tools/export_strings.py --prompt "<Language>"` prints the
   prompt with the English filled in. Skip §9 (database) unless the user asked for the upload.
3. Write `tools/packs/<code>.json`, run `--check` until it says OK.
4. Add a row to the Languages table here.
5. Upload only when the user says so; then check in the app: Language picker → the new language, the first screen
   and Quick Setup switch at once, the other screens follow within seconds.

## Rules that are easy to break

- Placeholders (`%1$s`, `%2$d`, `%%`) must match the English exactly; their order in the sentence may change.
- Each plural needs the language's CLDR forms, and every form needs the same placeholders as `#other`
  (`--check` enforces it). Languages with only `other` show "1 new badge!" instead of "New badge!": that's fine.
- Keep tab labels (`app_tab_*`) to one short word.
- Brand names, BPM, symbols and emoji stay as they are (translate-prompt §5).
- RTL (`ar`, and later `he`, `fa`, `ur`): **not supported yet; don't publish an RTL pack.** Audit 2026-10-24
  (paths under `app/src/main/`, `java/…` = `java/com/boimin/beatradar/`):
  - Blocker: choosing a pack never switches layout direction. `i18n/Strings.kt` `use()` only swaps strings and
    `MainActivity.kt` sets no `LocalLayoutDirection`. Fix: provide it from the pack's locale at the root, and in
    dialogs, sheets, widget and notifications.
  - Blocker: `String.format(pack.locale, …)` in `i18n/Strings.kt` may print Arabic-Indic digits; use a Latin-digit
    locale (`ar-u-nu-latn`).
  - Should fix: `ic_arrow_back.xml` (and `ic_list`, `ic_queue`, `ic_playlist_add`, `ic_note`) need
    `android:autoMirrored="true"`; BPM ranges like "160–170" flip without bidi isolation (`library/SongRows.kt`
    `bpmSpan`, `ui/ListenScreen.kt` target chip, `ui/RecapSheet.kt`, strings `library_bpm_range`, `song_more_at`,
    `log_filter_bpm_range`, `profile_tempo_zone_range`, `profile_set_bpm`); the recap share card draws left-anchored
    text on a Canvas (`ui/RecapSheet.kt`); numbers use the phone's locale, not the pack's (`NumberFormat` in
    `rewards/Badges.kt`, `rewards/ProfileScreen.kt`, `rewards/RewardsUi.kt`, `notify/Notifier.kt`).
  - Already fine: `supportsRtl="true"`, ICU plural rules per pack locale, no Left/Right-specific Compose code,
    swipe-to-delete uses `EndToStart`.
  - `ar.json` turns trailing "→" into "←" and "‹" into "›"; recheck those once the layout really flips.
