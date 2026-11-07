# Network audit

The rule: the home screen's network fetches run only while the home is in front and the display is on, and they refresh when it comes back
to the front. What must work in the background (the adhan, the sleep timer) is computed on the TV and needs no network.

The gate is `NetGate` (pure: `allowed(homeInFront, displayOn)`, `due(..., lastFetchAt, minGapMs, now)`, `backgroundOk(displayOn)`) with
`NetGateAndroid.displayOn` (`PowerManager.isInteractive()`). Tests: `tests/netgate_test.py`. Nothing here sends a name, an account, a serial,
an address of the TV or an advertising id. A request carries the TV's IP address (as any request does) and, where the table says so, the
**city the owner chose** (its coordinates), never where the TV is.

Verdicts: **ok** = already held the rule · **fixed** = changed by this audit · **exempt** = kept on purpose, the reason is in the row.

| What | Host | Trigger | Runs when | Data sent | Verdict |
|---|---|---|---|---|---|
| Greeting weather and dust (`Greeting`) | api.open-meteo.com, air-quality-api.open-meteo.com | home's minute tick and `onResume`; at most once in 30 min, a new city at once | now only home in front and display on (was: any tick) | the chosen city's latitude, longitude, time zone | fixed |
| Home card page (`HomeCard`, `dist/tv.html` in a WebView): prayers, weather, matches, markets, series, crests, quotes of the card chosen | api.aladhan.com, api.open-meteo.com, air-quality-api.open-meteo.com, www.thesportsdb.com, api.tvmaze.com, query1.finance.yahoo.com (fallbacks: api.allorigins.win, corsproxy.io), api.coingecko.com, www.wikidata.org, raw.githubusercontent.com (leagues, markets) | the page's own timers, `onResume` | `pause()` stops the page's timers (`pauseTimers`) when another app comes up; `resumeTimers` on return; the view is the launcher's only WebView | chosen city's coordinates, the team, league, show or ticker ids the owner chose | ok |
| Hosted wallpapers (`MainActivity.refreshPhotoIndex`, photo download) | raw.githubusercontent.com | each rotation (10 min) and `onResume`; the list once a day, a photo once, a failure not before an hour | now only home in front (`resumed`) and display on; the local photo is shown first, so a skipped step costs nothing | nothing (plain GET of a public file) | fixed |
| Aladhan refinement (`PrayerTimes.refresh`, `refreshIfDue`) | api.aladhan.com | the home's minute tick (30 min between tries, none once today's are kept); after each adhan alarm and at boot; the «تلاوة» screen | skipped with the display off; **kept** with the display on and another app in front (the alarm path), at most one answer a day, because the next adhan should use Aladhan's minute. The adhan rings on the times computed on the TV (`PrayerMath`) with or without this; the next day's refinement happens at the first tick after the home is back | the chosen city's coordinates, date, method, time zone | fixed |
| Adhan recordings kept offline (`Adhan.prefetchChosen`, from `AdhanScheduler.schedule`) | archive.org | after an alarm is set, a voice change; retried once on the home's return when skipped | skipped with the display off (`skippedInStandby`), asked once when the home is back; at most once an hour after a failure | nothing | fixed |
| All adhan recordings (`Adhan.prefetchAll`) | archive.org | opening «تلاوة»; once a day | the screen is in front by definition | nothing | ok |
| Adhan fetched while it plays (`MusicService`, `Adhan.download`) | archive.org | an adhan alarm whose recording is not kept yet, or a preview | the alarm plays only with the display on (`AdhanReceiver.screenOn`); the person's own listening | nothing | exempt (the adhan itself) |
| Quran and radio streaming (`MusicService`) | the reciters' and stations' hosts in `assets/quran` | the person pressed play | while playing | nothing | exempt (the person's playback) |
| Screensaver page (`ScreenView`, `src/template.html` / `dist/tv.html`) | the same data hosts as the home card, plus raw.githubusercontent.com (photos) | the dream starts; the page's timers | the dream exists only with the display on and is destroyed when it ends | chosen city's coordinates, the owner's team, league, show, ticker ids | ok |
| Photo cache (`PhotoCache.serve`) | raw.githubusercontent.com | the page asks for a hosted photo | inside the dream (WebView request) | nothing | ok |
| Page and version check (`Updater.checkIfDue`, from the dream's start) | raw.githubusercontent.com (`dist/tv.html`), github.com (`version.txt`, `releases/latest`) | the dream starts, at most every 6 hours, only if auto-update is on | now not with the display off (the dream already implies it; the check is deferred, `lastCheck` untouched) | nothing (an `If-None-Match` ETag) | fixed |
| `UpdateJob` (JobScheduler, every 6 hours, persisted) | none | the system | wakes the process and returns; no request (kept for TVs that need the process started at boot) | none | ok |
| Forced check (settings button, `saver.sh update`) | as the page and version check | the owner or the agent asked | at once, by request | nothing | exempt (the owner asked) |
| City, team, show search and the Arabic name (`SettingsActivity.fetch`, `Arabic.lookup`) | geocoding-api.open-meteo.com, www.thesportsdb.com, api.tvmaze.com, www.wikidata.org | the owner typed in the settings | while the settings screen is open | the text typed | ok |
| Settings previews (`ScreenView.createPreview`) | the card's data hosts | the settings' pickers | while the settings screen is open | as the card | ok |
| Phone remote update (`PhoneUpdater`, `UpdateFlow`) | github.com release files (`version.txt`, the APKs) | the owner pressed «افحص التحديث» / «ثبّت» on the phone | by request, with the screen on (a confirm on the TV) | nothing | exempt (the owner started it) |
| Family DNS check (`FamilyDnsAndroid`) | connectivitycheck.gstatic.com | the owner turned it on | by request, up to about 40 s | nothing (the 204 check of Android) | ok |
| Sleep timer (`SleepTimer`, AlarmManager) | none | the owner set it | an alarm; no request | none | ok |
| Adhan alarm (`AdhanScheduler`, AlarmManager) | none | computed times | rings with the display on, network or not; scheduling never waits for a request | none | ok |

**Counts:** 18 rows: 9 ok, 5 fixed, 4 exempt.

**Behaviour changes of this audit** (nothing new is asked, nothing sent changed): the weather and dust, the hosted photo list and photos, the
Aladhan refinement, the adhan recordings' prefetch and the page and version check now wait for «display on» (and, for the weather and the
photos, «home in front»); what was skipped is asked at the next tick or return, no earlier than it would have been.
