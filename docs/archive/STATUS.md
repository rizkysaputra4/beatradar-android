# BeatRadar — current state

_Last updated: 2026-10-18 · version 0.2.0 (debug) · package `com.boimin.beatradar` (was `com.rizky.beatradar`)_

BeatRadar is an Android app that listens to music through the **microphone**, shows the song's tempo (BPM) live, and **vibrates once** when a song stays inside your target BPM range for 10 seconds. Every song it hears goes into a searchable log. Without a target range, it simply logs each song's BPM.

---

## At a glance

| Area | State |
|---|---|
| Build | ✅ `gradlew assembleDebug` builds without errors |
| Unit tests | ✅ 104 tests, 0 failures (+2 benchmark tests and 2 live-server tests, skipped unless asked for) |
| Emulator (API 37) | ✅ End to end with recorded test songs fed in place of the mic |
| Real phone + real mic | ✅ Samsung **SM-S731B**: listening, circle toggle, ripples, range ring, noise benchmark (see [Tempo](#tempo)) |
| Shazam flow | ⚠️ Not tested |
| Release build / Play Store | ⚠️ `assembleRelease` builds (R8 on, unsigned **4.7 MB**; debug 43 MB). No signing, no store listing, not run on a device yet |
| Git | ⚠️ One commit (`7484a94`); everything in the [changelog](#changelog) below is **uncommitted** |
| Library backend | ✅ Connected: uploads, Library, accounts, log backup, loves, sharing, rewards, recognition ([`../beatradar-backend`](../beatradar-backend)). Checked end to end against a local server (see [Testing](#testing)) |
| Placeholders to fill | ⚠️ `GOOGLE_WEB_CLIENT_ID`, `SHARE_HOST`, release `API_BASE_URL`, `CLOUD_PROJECT_NUMBER` in `app/build.gradle.kts`; Play products `coffee_small/medium/large` |

---

## What the app does

### Listening
- **Microphone only.** There's no share-screen prompt. Capturing other apps' audio is deliberately not supported (see [Decisions](#key-decisions)).
- Runs as a **foreground service** with a notification showing the BPM, plus Pause and Stop. Keeps working in the background and with the screen off.
- The source label always reads **"Microphone"**. It adds *"hearing \<app\> on this phone"* when a music app on this phone is playing, otherwise *"hearing music around you"*.

### Tempo
- Each hit the mic hears (drum, pluck, chord) is reported within ~25–50 ms for the ripples; steady noise (fan, traffic, chatter) makes none.
- A quick first guess after **~1.5 s** (shown dimmed, never counted toward a match or the log), settled from **~3 s**, then a new reading every **0.5 s**, continuously.
- 60–200 BPM, generally within ±2 BPM on test signals.
- Noise gate: a quiet room or plain noise never shows a BPM (confidence threshold 0.15 plus a level gate at about −60 dBFS).
- Readings are smoothed (median of 3), and half/double-tempo flips are folded back so the number stays steady.
- Listens two ways at once and uses whichever is surer: the whole spectrum, and weighted toward kicks and bass (below ~250 Hz), which ignores most chatter and clatter.
- Noisy rooms, measured on the SM-S731B (39 clips of rock/EDM/acoustic with room, traffic, chatter and clatter noise played from PC speakers, 2026-10-17). Share of readings showing the right BPM:

  | Noise vs music | Before | Now |
  |---|---|---|
  | Clean | 95% | 96% |
  | Music clearly louder (+10 dB) | 69% | 94% |
  | +5 dB | 53% | 90% |
  | Noise as loud as music (0 dB) | 25% | 65% (8% wrong) |

  Rerun offline with `NOISE_BENCH=1 gradlew :app:testDebugUnitTest --tests '*NoiseBench*' -i` (`CHAIN_HZ=150` mimics speaker + mic bass loss; `BENCH_OUT=<dir>` writes the clips as WAV files).
- Half/double tempo is still ambiguous at the ends of the range (85 can read as 170, 174 as 87); the rhythm alone can't settle it. The *count half/double tempo* setting covers it.
- Tried and dropped: choosing the octave from offbeat hits (broke other tempos), trusting steady low-confidence readings (noise looks steady too: up to 88% false readings), per-band noise-floor subtraction (no gain), blending high bands back into the bass view (clatter errors returned), bass view alone (failed a clean rock clip on the phone).

### Verdict (target range)
- A ring around the BPM circle **fills clockwise over 10 s** while the tempo stays in range. Dips out of range shorter than 1.5 s are forgiven; longer ones reset it.
- At 10 s: **Match!** The circle turns coral, the phone vibrates (3 pulses, alarm usage so it works on silent), and a notification with an "Identify with Shazam" action is posted.
- **One alert per song:**
  - Within one play, a song never alerts twice, however often the tempo drifts. A new steady tempo at 2:1, 3:2 or 4:3 of the logged one is treated as the same song (a misread section), not a new one.
  - Across plays and sources (phone speaker, PC speaker, café), a song already matched earlier is recognised and not announced again. The screen shows *"Already in your list"*.
- Optional *count half/double tempo* (e.g. 65 BPM counts as 130).

### Recognising the same song
A song counts as already known if either of these matches:
1. **Title**: from a music app's media session on the phone, from Shazam, from Pixel "Now Playing", or typed by the user. Titles are compared by a normalised key (lowercase, no accents or punctuation, without "(Official Video)", "[Lyrics]", "- Remastered", "feat. …"), so "Get Lucky (Official Video)" and "Get Lucky" are one song.
2. **Audio fingerprint**: a Shazam-style landmark fingerprint is saved for the whole time a song plays, whatever its tempo reads (about 10–15 landmarks/s, up to 3,600 per song, ~150 KB). About 12 s of a part already heard, from any speaker, is enough to recognise it. A fingerprint only matches the part of the song it came from, so saving every part matters.
   - Landmarks are held back 12 s before saving, so the start of the next song isn't filed under the previous one.
   - Only the **200 most recently heard songs** keep fingerprints (~30 MB at most); older entries are recognised by title, and get fingerprints again when heard again.
   - Kick-drum and bass sounds and repeating drum patterns are ignored, because many songs share them.
   - Match threshold is **8**. In tests, the same song through another speaker scores about 17, a different song with identical drums scores 5 or less, and unrelated music scores 0–1.
   - Fingerprints are only added to an earlier entry when the match is certain (same title, or a score of at least 16), so one wrong match can't spread.

### Song names (optional, free)
- With **Notification access** granted, titles come from music apps' media sessions, Shazam's result notifications, and Pixel "Now Playing".
- **Identify** opens Shazam. *Auto Shazam* in the Shazam app names songs automatically.
- **Saved clips** (setting, **on by default**): 12 s of a song without a name are kept (22 kHz WAV, ~0.5 MB), so it can be replayed (▶) or played to Shazam (Identify) from the Log.
  - *All songs, not just matches* (**on** by default): every unnamed song gets a clip once 12 s of it were heard after it was logged (so the previous song's tail never ends up in it). In-range songs still get theirs at the moment of the match. Off = only in-range songs, as before.
  - Clips are kept for the **last 30 songs** (10 / 30 / 50 / 100 in Settings); older clips are deleted automatically and their entries stay in the log without one. Lowering the limit deletes the extras at once.
  - A clip is **deleted as soon as the song has a name**, and also when its entry is deleted or the log is cleared. Clips never leave the phone.

### Labelling (PLAN §5b)
- An unnamed entry shows as **`Song #N`**: a number per phone, never reused, and the same song heard again keeps it. The number is never sent anywhere.
- **Name sources, strongest first:** media session / Shazam / Now Playing → server (later) → typed by the user. An automatic name only replaces a weaker one; typing a name always applies.
- **Merge on name:** when an entry gets a name that another entry already has (same normalised key), the two become one: the older entry is kept, plays are added up, fingerprints move over, the clip is deleted.
- **While it plays:** the Listen screen's title line reads *Song #N · Identify*; tapping it starts Shazam.
- **Afterwards:** the Log shows "N songs without a name · Identify", which filters to unnamed songs that still have a clip.
- **Rename:** tap any row (or the pencil on an unnamed one without a clip) to type title and artist.

### Library (shared, PLAN §7–§8)
- **Third tab, open to guests.** Search, a tempo range (starts at your target range, same 60–200 scale), ½× / 2×, sort (Popular, Confirmed, Most loved, New, BPM) and, signed in, a *Loved* filter. Paged as you scroll.
- **Rows:** BPM badge, title, artist, artwork when known, and counters 👥 submitted · ✓ confirmed · ♥ loves (outlined ✓ while nobody has confirmed it).
- **Song page:** artwork, big BPM disc with "also heard as N", *N submitted · N confirmed* (tap for the explanation), ♥ love (guests get the sign-in sheet, and the love completes after signing in), *Listen on* Spotify / YouTube / Apple Music (a 🔍 marks search links), *Brought in by* → public profile, Share and Report.
- **Share:** "<title> – <artist> · 128 BPM on BeatRadar https://<share host>/s/<slug>"; that link opens the song in the app (App Links, `autoVerify`).
- Offline, empty and error states; the rest of the app keeps working offline.

### Uploads and recognition (PLAN §5a, §7)
- Every **named** play is uploaded once, as `id:playCount`, so a song heard again on another day counts again; retries are harmless (the server answers *duplicate*). Refused items are not retried, except the daily cap. Unnamed songs, and entries pulled from other phones, are never uploaded.
- Songs named by the media session, Shazam or Now Playing also upload their **fingerprint** (packed landmarks). Once the server has it, the phone deletes its local copy and recognises the song through the server.
- When a song is logged or matched, the phone first asks the server (`/v1/recognize`, last 12 s, 1.5 s timeout); a *trusted* answer names an unnamed entry for free (source "server"), a *candidate* only stops a repeat alert. Offline or unknown → the local fingerprints, as before.
- Runs in WorkManager (`SyncWorker`, network required, exponential backoff), triggered on app start, after logging or naming a song, and by pull-to-refresh on the Log.
- **Contribute to the Library** (Settings, on by default; first-run notice): off = nothing is uploaded and queued items are dropped. Recognition still works.

### Naming unnamed songs later (PLAN §5c)
- A song that finished without a name (not heard for 5 min) uploads its fingerprint (`POST /v1/unnamed`, the entry's id as `clientRef`, ≤ 8,000 landmarks, batches of ≤ 50 / ~1.5 MB). Only with *Contribute* on; turning it off drops what's queued. A sync is scheduled ~6 min after an unnamed song is logged; a song still playing waits.
- When someone names the same song, the server suggests it. The app picks suggestions up from the upload answer and from `GET /v1/suggestions` on every sync (app start, after songs, pull-to-refresh); suggestions the server no longer has disappear.
- **Log row:** "Is this *Title – Artist*?" with **Yes** / **No** under the unnamed row (▶, Identify and Rename stay). The "songs without a name" line adds "· N suggested names", and its filter includes suggested songs. **Listen screen:** the same question with Yes / No under the *Song #N · Identify* line while that song plays.
- **Yes** → accept, then the entry is named with source *server* under the usual rules (merge, clip deleted, upload queued) and linked to its Library page. **No** → reject; the next match shows if the server has one.
- Named another way (Shazam, media session, rename, recognition) or deleted after its fingerprint went up → `DELETE /v1/unnamed/{id}`, queued by database triggers so no path can miss it. Local fingerprints stay until the song is named (offline recognition).

### Account and log backup (PLAN §3, §7a)
- Anonymous install first (random id + secret in an Android-Keystore-encrypted store); signing in links it, so earlier uploads count for the account.
- **Sign-in sheet:** *Continue with Google* (Credential Manager; needs the Web client id) or email (sign in, create account → verification email, forgot password, resend verification). Friendly messages for every server error code.
- **Settings → Account:** nickname (rename), *Show me on leaderboards*, Sign out (asks "Keep this phone's log?"), Delete account (confirmed).
- **Backup:** signed in, the log syncs with the account: adds, renames and deletes push (deletes as tombstones), entries from other phones pull in (unnamed ones get a local `Song #N`), newest change wins. The 1,000-entry auto-trim stays local and is never pushed as a delete. Entries the Library knows get a ♫ button to open their page.

### Rewards (PLAN §7b)
- **Beats chip** in the header (🔥 streak · all-time beats) opens the Rewards sheet: beats this week / all time with ranks, streak, badge grid (medal in bronze / silver / gold, crown for Groove Master, grey with progress while locked), leaderboard *This week · All time* with your own row pinned, last week's Groove Master. Guests see their install's beats and a sign-in hint.
- New badges are celebrated once on app open (dialog + light haptic).
- Play Integrity (standard request bound to the install) runs once per install, best effort; skipped while `CLOUD_PROJECT_NUMBER` is 0 or without Play services.

### Buy me a coffee
- Settings row (hidden when Play Billing or the products aren't available) → three consumable tips with Play's local prices → thank-you. Pending payments are handled; tips paid while the app was closed are consumed on next start. No external payment links.

### Song log
- One row per song: BPM badge (coral if it matched), title (or `Song #N`) / artist / source, and *Heard 3× · 5 minutes ago · nearby speaker* (or *on phone · Spotify*).
- Search, plus filter chips **In range** and **Identified** (they can be combined), and the "songs without a name" filter.
- Swipe to delete (with Undo), Clear all (with confirmation), Export as CSV. Pull down to sync.
- Auto-trims to the newest 1,000 entries.

### Design
- Material 3 Compose with two tabs (Listen, Log) and a small pill tab bar.
- Slim header: logo, title, settings icon.
- Theme follows the phone (System), or can be forced to Light or Dark. Dynamic color on Android 12+.
- **Listen screen:** source chip → BPM circle → one-line status → Pause / Identify (only while listening) → range card. There's no separate Start button and no wave graph any more. Kept deliberately simple: elements are replaced rather than added.
  - **The circle is the mic switch**: tap to start, tap again to stop. Grey with a mic icon when off.
  - **Mic on:** three calm rings roll out steadily, so it's clear the mic is listening.
  - **Ripples:** a shockwave (a glow plus a zigzag front that smooths as it spreads) rolls out past the screen edges. Before a beat is found, every hit the mic hears sends one and bumps the disc; once the beat is found, one leaves on each beat, together with the disc's pulse.
  - **BPM number:** a first guess shows dimmed after ~1.5 s and brightens when it settles (~3 s).
  - **Progress ring:** a zigzag that fills clockwise over the 10 s hold, with a flat track for the rest. Its teeth kick taller on each beat and crawl slowly around (BeatRadar's own take on Material's wavy progress indicator).
- **Range card:** on/off switch, typed **Min BPM / Max BPM** boxes, and a linked slider (60–200).
- Brand: indigo `#3D2C8D`, coral `#FF5A5F` as the "match" colour. Adaptive launcher icon (radar arcs plus a beat line) with an Android 13+ themed (monochrome) layer.

---

## Settings

| Setting | Default | Notes |
|---|---|---|
| Find a BPM range | On, 120–130 | Off = log every song's BPM only |
| Theme | System | System / Light / Dark |
| Vibrate on match | On | Buzzes even on silent |
| Count half / double tempo | Off | |
| Save clips of unnamed songs | **On** | 12 s per song, kept on the phone only |
| All songs, not just matches | **On** | Off = in-range songs only; disabled when clips are off |
| Keep clips of the last N songs | **30** | 10 / 30 / 50 / 100 (~5 / 15 / 25 / 50 MB) |
| Contribute to the Library | **On** | Titles, tempos, fingerprints; never audio. Off = no uploads |
| Account | Signed out | Sign in, nickname, leaderboard visibility, sign out, delete |
| Buy me a coffee | — | Shown only when Play Billing works |
| Song names (notification access) | Not granted | Opens Android's notification-access screen |

## Permissions

`INTERNET`, `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `VIBRATE`, plus the optional notification-listener binding. No analytics and no ads. What reaches the server: titles, tempos, fingerprints of named songs (with *Contribute* on), and the log backup when signed in. Never audio or clips. Release builds are HTTPS only; debug builds allow plain HTTP to `10.0.2.2` / `localhost` for the dev server.

---

## How it works

```
Mic (44.1 kHz) ──► BeatEngine ──► decimate to 11 kHz
                     │
                     ├─► OnsetBpmDetector: FFT → two onset views (full spectrum, bass-weighted)
                     │        each: spectral flux → autocorrelation → comb + tempo prior; the surer view wins
                     │        └─► BPM, confidence, settled?, time of last beat  (every 0.5 s, from ~1.5 s)
                     ├─► hits (bass-weighted onsets) ─► MicHits ─► ripples
                     └─► Fingerprinter: peak pairs → landmarks

CaptureService (one background task at a time)
   BPM ─► SongJudge ─► progress 0..1 · Match (10 s in range) · SongLogged (10 s steady)
   SongLogged / Match ─► same song? (title, or FingerprintMatcher over the DB)
                        ├─ known   → reuse entry, no alert ("Already in your list")
                        └─ new     → insert / mark in range, vibrate + notify, save clip (if enabled)
   Monitor (StateFlow) ─► UI + notification (updated at most every 2 s)
```

### Source map (`app/src/main/java/com/boimin/beatradar/`)

| Package | File | Role |
|---|---|---|
| `.` | `BeatRadarApplication.kt` | DB and settings singletons, notification channels, names the entry when a Shazam result arrives for a clip identified from the Log |
| | `MainActivity.kt` | Permissions, start/stop, CSV export, Identify-clip flow, share links in/out, coffee billing |
| `account` | `AccountRepository.kt` | Install identity, tokens (refresh, re-register), sign-in/out, profile; the API's token source |
| | `SecureStore.kt`, `GoogleAuth.kt`, `IntegrityCheck.kt` | Keystore-encrypted secrets, Credential Manager, Play Integrity |
| | `AccountViewModel.kt`, `SignInSheet.kt` | Sign-in flows, rewards and leaderboard state |
| `net` | `ApiClient.kt`, `ApiModels.kt`, `Landmarks.kt` | OkHttp + kotlinx.serialization client, DTOs from `openapi.json`, fingerprint packing |
| `sync` | `LibrarySync.kt`, `SyncWorker.kt`, `SyncRules.kt` | Uploads, log push/pull, integrity; WorkManager; pure upload/merge rules |
| `library` | `LibraryRepository.kt`, `LibraryViewModel.kt`, `LibraryScreen.kt`, `SongDetailScreen.kt`, `SongCounters.kt` | Library tab, song page, counters |
| `rewards` | `RewardsUi.kt` | Beats chip, Rewards sheet, badges, profile sheet, celebration |
| `billing` | `CoffeeBilling.kt` | Play Billing consumables |
| `capture` | `CaptureService.kt` | Foreground mic service, song identity, logging, alerts, clips |
| | `Monitor.kt` | Live state shared with the UI (including `bpmSettled`) |
| | `MicHits.kt` | Hits the mic hears, as a flow for the ripples |
| `dsp` | `BpmDetector.kt` | Tempo + beat-phase detector with two onset views |
| | `BeatEngine.kt` | PCM → readings, noise gate, early/settled readings, smoothing, hit reports, landmarks |
| | `SongJudge.kt` | 10 s verdict, grace period, per-song logging |
| | `Fingerprinter.kt` | Landmark fingerprint and `FingerprintMatcher` |
| | `Fft.kt` | Radix-2 FFT |
| `data` | `BeatLog.kt` | Room: `beat_log`, `fingerprint`, DAO, migrations 1→2, 2→3, 3→4, CSV export |
| | `Naming.kt` | `NameSource` precedence and `SongKey` (normalised artist\|title) |
| | `Placeholders.kt` | Hands out `Song #N` numbers |
| | `Settings.kt` | DataStore settings, range matching |
| | `ClipStore.kt` | Save / delete / play WAV clips, clip limit |
| `meta` | `NowPlayingListener.kt` | Media sessions + Shazam / Now Playing notifications |
| | `ShazamBridge.kt` | Launch Shazam, parse its notifications |
| | `SongNamer.kt` | Single place to name an entry: precedence, deletes its clip, merges with an entry of the same song |
| | `NowPlayingHub.kt` | In-process flows for track info and recognitions |
| `notify` | `Notifier.kt`, `Haptics.kt` | Notifications and vibration |
| `ui` | `AppRoot.kt`, `ListenScreen.kt`, `LogScreen.kt`, `SettingsSheet.kt`, `MainViewModel.kt`, `theme/Theme.kt` | Compose UI |

### Database (Room, version 5)
- `beat_log`: id, timestamp, title, artist, sourceApp, bpm, confidence, inRange, clipPath, placeholderNo, playCount, nameSource, matchKey (indexed), phoneApp, uploadState, songSlug, syncState, updatedAt, remoteInstallId, remoteClientRef, printUpload, sugslug / sugtitle / sugartist / sugscore / sugstate (the suggested name)
- `unnamed_forget`: entry ids whose unnamed fingerprint the server should drop; filled by three triggers on `beat_log` (named, tombstoned or deleted after the upload). Created in the 4→5 migration and, for fresh installs, in the database's `onCreate` callback.
- `fingerprint`: id, entryId (→ beat_log, cascade delete), hash, frame; indexed on hash and entryId
- Migration 1→2 adds `clipPath` and the `fingerprint` table and keeps existing entries. Verified on the emulator.
- Migration 2→3 adds the label columns, numbers existing unnamed entries `Song #1…` in the order they were heard, and gives named ones their match key and a name source guessed from their source label. Not yet run on a device.
- Migration 3→4 adds upload and backup state: named entries are queued for upload, every entry is marked changed (so it goes up after signing in), `updatedAt` starts at the entry's time. Not yet run on a device with an old database. New logs now store the real confidence (older entries upload with the 0.15 floor they were logged at).
- Migration 4→5 adds `printUpload`, the suggestion columns, `unnamed_forget` and its triggers, and queues unnamed entries that have fingerprints. **Run on the SM-S731B** (version 4 → 5, its four unnamed songs uploaded right after).

### Tech stack
Kotlin 2.4.21 · AGP 9.4.1 (built-in Kotlin) · Gradle 9.8.1 · Compose BOM 2026.09.00 · Material 3 · Room 2.8.5 + KSP 2.3.12 · DataStore · minSdk 29, target/compile SDK 37. No third-party audio libraries; the DSP is our own code.

---

## Testing

**Unit tests (104, all passing; +4 opt-in)**, `gradlew :app:testDebugUnitTest`:

| Suite | Covers |
|---|---|
| `BpmDetectorTest` (12) | 60/90/128/174/123.5 BPM clicks, drum loops at 100/140, silence, nothing before 1.5 s, early guesses marked unsettled, settled by 3.6 s, beat-phase accuracy (±40 ms) |
| `BeatEngineTest` (6) | Early then settled readings every 0.5 s, beats stay on the grid, silence handling, noise is never a beat, one hit per click within 110 ms, steady noise makes no hits |
| `NoiseBench` (2, skipped by default) | Noise benchmark and WAV export; see [Tempo](#tempo) |
| `SongJudgeTest` (9) | Match at 10 s, reset when leaving the range, 1.5 s grace, one match per stay, log mode, low confidence, next song gets its own match, 3:2 / 4:3 misreads stay one song |
| `FingerprinterTest` (4) | Same song from another speaker and another part matches; unrelated songs and same-drum songs don't; landmark density |
| `SettingsTest` (6), `ShazamBridgeTest` (4) | Range and half/double matching, clip defaults, CSV, Shazam / Now Playing parsing |
| `NamingTest` (9) | Name-source precedence, match key normalisation (accents, punctuation, video noise, feat.), remixes stay separate, `Song #N` label |
| `ClipLimitTest` (4) | Which clips the limit deletes (oldest first) |
| `FingerprintVectorsTest` (2) | The matcher test vectors shared with the server give the same song and score |
| `UnnamedRulesTest` (10) | Unnamed upload body (local id, no play count, no odd tempo), batching, answer → state (daily cap stays pending), which suggestions show vs. are forgotten, server bodies, "Is this …?" and the suggested-names line |
| `SyncRulesTest` (19) | Measurement mapping, per-play client refs, prints only for reliable names, batching by count and size, answer → state, backup push mapping, newest-wins merge, tombstones, trimmed entries stay gone |
| `ApiModelsTest` (9), `ApiClientTest` (5), `LandmarksTest` (5) | Parsing real server bodies, null-free requests, error codes, bearer + query encoding, 401 → retry once, offline; little-endian landmark packing |
| `LiveServerTest` (2, opt-in) | Against a running server (`BEATRADAR_E2E_URL`, `BEATRADAR_E2E_LOG`): install registration, upload (accepted / duplicate / rejected, prints), consensus, search, recognition, guest limits, rewards; register → verify → login, profile, log push/pull, refresh rotation + reuse detection, delete account |

**On the SM-S731B** (test audio played from the PC speakers):
- The circle toggles the mic on and off; calm rings while on; ripples from the first hit; dimmed early BPM, then settled.
- The zigzag progress ring fills while in range (175 BPM loop against a 170–181 range).
- Noise benchmark: 39 clips × 30 s, three detector versions compared; results under [Tempo](#tempo). Readings were captured with temporary debug logging, since removed.
- The benchmark runs added test entries ("Unknown song", 100/120/128 BPM, about 21:53–23:15) to the phone's Log; delete them by hand.

**On the emulator** (test WAVs fed in place of the mic through a temporary debug hook, since removed):
- 128 BPM shows after ~3–5 s; the ring fills, then Match! with vibration, notification and a log entry.
- The same song through a muffled "second speaker" scores 22 → *Already in your list*, no vibration, no new row.
- A different song with the same drums scores 2 → treated as a new song, its own Match.
- Clips are saved only for in-range unnamed songs.
- Light and dark mode, typed range boxes, filters and the database migration all checked.

---

**Against the local server (2026-10-18):** `LiveServerTest` passed both flows. On the emulator (debug build with `-PapiBaseUrl=http://localhost:8080/` + `adb reverse`, since this PC's emulator can't reach `10.0.2.2:8080`): first-run notice, install registered, Library list with counters, song page, ♥ as guest → email sign-in → love completed (checked in the database), Rewards sheet, Settings account section, log entries pushed from a second install pulled in (unnamed one became `Song #1`), swipe-delete reached the backup as a tombstone and a rename went back under the other phone's id, a share link opened the song page. Not checked: Google sign-in (no client id), Play Integrity, billing, recognition and uploads of songs heard through the mic.

## Known limitations

- **A muted phone can't be heard.** With the mic only, the music must be audible (phone speaker or a nearby source). Hearing a muted phone would need Android's share-screen consent every session, which was removed on purpose.
- **Tested with synthetic songs only.** The phone runs used generated rock/EDM/acoustic loops through PC speakers; real recorded music in real venues is still to be tried. Fingerprint thresholds haven't been checked on the phone yet.
- **Very loud rooms:** with noise as loud as the music, the right BPM shows ~65% of the time and a wrong one ~8% (mostly sharp clatter over rock). A wrong reading alone can't trigger the alert, which needs 10 s steady in range.
- **Shazam integration is unofficial.** It relies on Shazam's notifications and an undocumented tagging intent, so a Shazam update could break it. The clip-to-Shazam flow needs the volume up, and is untested.
- **Old entries have no fingerprints.** Songs logged before fingerprinting can only be recognised by title.
- **A named song counts as "already listed" for good.** Delete its log entry to be alerted again.
- **Octave errors are possible** (e.g. 87 vs 174 BPM); the half/double setting covers this.
- UI text is English only and not yet in string resources.
- Google sign-in, Play Integrity and coffee tips need the real ids (see placeholders); until then Google says "not set up", integrity is skipped, and the coffee row stays hidden.
- On this PC the emulator can't reach the dev server at `10.0.2.2:8080` (probably the firewall); build with `-PapiBaseUrl=http://localhost:8080/` and run `adb reverse tcp:8080 tcp:8080`, or use the PC's LAN IP on a real phone.
- Orphaned clip files can remain if entries are trimmed by the 1,000-entry limit. Minor.

## Key decisions

| Decision | Why |
|---|---|
| Microphone only, no share-screen capture | The user didn't want the consent dialog. The Android `Visualizer` API was tested as a no-prompt alternative: it hears audible output, even from Spotify/Chrome, but reads silence when muted. |
| Own DSP instead of TarsosDSP | Avoids the GPL licence; small, testable, cheap on battery |
| Free song naming via Shazam / media sessions | No paid recognition API |
| Fingerprint ignores drums and bass | Shared drum samples caused false matches in testing |
| Clips for every unnamed song, on by default, last 30 songs | Every song that gets a name can feed the planned Library; clips stay on the phone, and the limit caps storage (~15 MB) |
| `Song #N` placeholders, no recognizer of our own | BeatRadar is a beat log; names come from the phone, other apps or the user (PLAN §5b) |

---

## Build & run

```bash
# Windows: set JAVA_HOME to Android Studio's JBR if no JDK 17+ is on PATH
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleDebug          # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # install on every connected device/emulator
```

Or open the folder in Android Studio and press **Run**. `local.properties` points at the local Android SDK (not meant for version control).

## Suggested next steps

1. **Commit** the work in the changelog below.
2. Try real recorded songs in a real noisy place (café, gym), and check fingerprint matching on the phone. In debug builds, logcat tag `BeatRadar` prints the mic level and fingerprint scores.
3. Install Shazam and try the clip-identify flow end to end.
4. Measure battery use over 1 h with the screen off (`dumpsys batterystats`); the second onset view doubles the (small) tempo-analysis cost.
5. Start the Library backend: milestones 0–1 in [`../beatradar-backend/PLAN.md`](../beatradar-backend/PLAN.md).
6. Move UI text into `strings.xml` (and add Indonesian), and prepare a signed release build.

---

## Changelog

**2026-10-18 (Name unnamed songs later, uncommitted)**
- PLAN §5c / M8: unnamed songs upload their fingerprint; server suggestions show as "Is this …?" with Yes / No on the Log row and the Listen screen; Yes names (source server), No rejects (next suggestion shown), named another way / deleted → the server forgets the fingerprint. Room version 5.
- Checked on the SM-S731B against the running server: migration 4→5, the four unnamed songs uploaded with all their landmarks, after a named upload of the same audio from the PC the suggestion ("M8 Test Song – Crowd Namer", a test name) arrived on the phone's Song #2. The phone was locked, so Yes / No / rename were checked on the emulator with a copy of the phone's log: Yes named it and uploaded a `server` measurement, No was remembered by the server, a rename made the server drop the fingerprint.

**2026-10-18 (Library, uncommitted)**
- Connected the app to the Rust backend (M1–M7 app parts): uploads with fingerprints, server recognition first, Library tab and song page, accounts (email + Google), log backup with tombstones, loves, share links + App Links, streaming links + artwork, rewards (beats chip, Rewards sheet, badges, leaderboard, profiles, celebration), Play Integrity, report a song / nickname, Buy me a coffee, first-run sharing notice + *Contribute* switch, offline states. Room version 4. Version 0.2.0.
- New dependencies: OkHttp 5.5, kotlinx-serialization 1.11, WorkManager 2.12, Credential Manager 1.6 + googleid 1.2.1, Coil 3.6, Play Billing 9.1, Play Integrity 1.6, Fragment 1.9.1 (MockWebServer for tests).

**2026-10-18 (later, uncommitted)**
- Package renamed `com.rizky.beatradar` → `com.boimin.beatradar` (namespace, applicationId, source folders). A phone with the old build keeps it as a separate app; its log does not carry over.
- `BuildConfig.API_BASE_URL` per build type (debug → `http://10.0.2.2:8080/`; release domain still to be decided). Not used yet.
- Clips for every unnamed song (not just matches), on by default, kept for the last 30 songs (setting), ▶ to replay one in the Log.
- Labelling: `Song #N` placeholders, *Heard N×* and where it was heard, *Song #N · Identify* on the Listen screen, "songs without a name" filter, Rename, name-source precedence and merge-on-name. Room version 3.
- Not yet checked on the phone.

**2026-10-18 (uncommitted)**
- Fixed repeat alerts within one song (seen with The Vamps – *Somebody To You*). Causes: fingerprints were saved only while the tempo matched the logged one, so the sections that drift were never saved and couldn't be recognised; a 3:2 or 4:3 tempo misread counted as a new song; recognition checks dropped landmarks without saving them; a borderline match stopped saving even when it matched the song already playing.
- Now: the whole song is fingerprinted (held back 12 s), 3:2 / 4:3 readings stay the same song, octave flips no longer skew the steady-tempo average, per-song cap 2,500 → 3,600 landmarks, fingerprints kept for the last 200 songs only.
- Not yet checked on the phone with real music.

**2026-10-17 (evening, uncommitted)**
- Listen screen: removed the wave graph and the Start/Stop button; the BPM circle is now the mic switch. Pause and Identify show only while listening.
- Ripples: bigger shockwaves out to the screen edges with a zigzag front; calm rings while the mic is on; ripples react to real hits straight away, then follow the circle's beat once it's found.
- Progress ring redrawn as a beat-driven zigzag.
- First BPM guess after ~1.5 s (dimmed, never counted toward a match or the log), settled at ~3 s; the mic is read in 25 ms chunks.
- Noise robustness: two onset views (full spectrum + bass-weighted); stricter hit threshold so steady noise makes no ripples. On the phone, right BPM with noise 5 dB below the music went from 53% to 90%, and with noise as loud as the music from 25% to 65%.
- Added `NoiseBench` (offline benchmark + WAV export, opt-in through environment variables).
- Planned the Library backend (accounts, shared song/BPM library, loves, sharing, streaming links, log backup) in `../beatradar-backend/PLAN.md`.
