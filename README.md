<p align="center">
  <img src="assets/logo.png" width="120" alt="Spoticious logo"/>
</p>

<h1 align="center">Spoticious</h1>

<p align="center">
  <b>An Android music player for people who care about audio. 100% FOSS.</b><br/>
  <i>v2.0 — Miami UI, freestyle record, Audio Cut, Audacious-faithful EQ.</i>
</p>

---

Yeah, every player says that. But hear me out.

I've used a dozen music players. Respect to every dev who built one.
None had what I actually needed — so I built my own.

Still offline. Still no account. Still no "cloud library" nonsense.
Just your files, solid playback, and tools that don't insult your ears.

---

## Screenshots

| Player | Equalizer | Master Data |
|--------|-----------|-------------|
| ![Full player](screenshots/player.png) | ![10-band EQ + preamp](screenshots/eq.png) | ![LUFS / peak / clips](screenshots/master.png) |

---

## What you get

**Playback** — Local library, folders, playlists, search, sort, track numbering.
Notification controls, sleep timer, the usual stuff done without drama.

**Master Data** — Per-track loudness: LUFS-I, LRA, peak, clip count.
ITU-R BS.1770-ish path with K-weighting and gating.
Think "DAW render stats on your phone" — useful if you master, freestyle, or just hate mystery clipping.
LUFS-M / LUFS-S in the UI are **max over the whole track** (offline), not a live Reaper needle.

**EQ** — 10-band + preamp, band layout after Audacious.
DSP ported closer to Audacious (`bp2`) — same engine for listen and Render.
Save / load presets. Import Audacious `.preset` files if you already live there.

**Render / Export** — Bake your EQ into AAC (up to 320 kbps) or WAV 24-bit via MediaCodec.
No FFmpegKit. Smaller APK. FOSS-store friendly.

**Record / Freestyle** — Mic take, optional beat under you, live beat gain while you record.
Mix streams to disk. Save to Music when you're happy.

**Audio Cut** — Pick a file, mark ranges, preview, export WAV clips, merge selected segments.
Name the file before it lands in Music — radical, I know.

**Wrapped** — Local listening stats. Spotify Wrapped vibes, fully offline.
Your data stays on the phone.

---

## Stack

![Kotlin](https://img.shields.io/badge/Kotlin-00D4FF?style=flat-square&logoColor=black&logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-FF6B9D?style=flat-square&logoColor=black)
![Material 3](https://img.shields.io/badge/Material%203-FF6B9D?style=flat-square&logoColor=black)
![ExoPlayer](https://img.shields.io/badge/ExoPlayer-00D4FF?style=flat-square&logoColor=black)
![MediaCodec](https://img.shields.io/badge/MediaCodec-00D4FF?style=flat-square&logoColor=black)
![AndroidX Media](https://img.shields.io/badge/AndroidX%20Media-00D4FF?style=flat-square&logoColor=black)

No FFmpegKit. No Play Services. Apache-friendly stack throughout.

---

## Install

> **Minimum: Android 7.0 (API 24)**

- **GitHub Releases** — grab the APK from [Releases](https://github.com/codestudio71/Spoticious/releases)
- **F-Droid** — not listed yet. Stack is compatible. Planned.

---

## Build

```bash
git clone https://github.com/codestudio71/Spoticious.git
```

Open in Android Studio. Boring Gradle project, no secret handshake.
Requirements: Android Studio · JDK 17 · min SDK 24

---

## Known Issues

- Render / export can differ a bit by device MediaCodec implementation
- Some lossy formats may show Clips as `*` when the phone can't count them honestly — Peak / LUFS / LRA still run
- Tiny LUFS / LRA deltas vs desktop meters on some sample rates — polish later, not a shrug forever

Found a bug? Reproduce it, then [open an issue](https://github.com/codestudio71/Spoticious/issues).

---

## Contributing

PRs welcome. Open an issue first so we don't both invent the same wheel.

---

## Hey, one more thing

If you have too much money and spend it on stupid stuff — consider donating instead.
Indie dev, own pocket, staying FOSS. No paywalls, no bullshit.

[Donate via PayPal](https://www.paypal.com/donate/?hosted_button_id=H9DVM6NZ8TD6A)

⚡ Bitcoin Lightning: `devteam@cake.cash`

No money? Tell a friend. Post it somewhere. That counts.

Thanks.

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
