# Parked: Emad's home-screen features (before 4.2.1)

When the original's 4.2.1 was merged, its home screen (MainActivity) was taken as it is. These files were
written against the old MainActivity and no longer build against the new one, so they are kept here,
outside the build, to be ported later:

- `Showcase.java`: the focused favourite's own posters, the lens, the small clock in the header
- `ShahidLive.java`: Shahid's free live channels in a card (needs `androidx.media3:media3-exoplayer(-hls):1.4.1`)
- `ThmanyahMatches.java`: ثمانية's league matches as cards
- `TvSettings.java`: «إعدادات تلفزيونك», the three-column settings screen (icons `res/drawable/ic_set_*`)
- `Sounds.java`: the home screen's own sounds (`res/raw/ui_*.wav`)
- `NoteCard.java`: Home Assistant pop-ups in the home card's place (the screensaver still shows them)

The full old versions, MainActivity included, are on the `emad` branch.
