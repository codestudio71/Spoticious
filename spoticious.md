# Spoticious – stan projektu

## Co to za projekt

**Spoticious** – odtwarzacz muzyczny na Androida (Kotlin, Jetpack Compose), inspirowany Audacious i Spotify:
- **EQ**: 10 pasm (31 Hz–16 kHz) + preamp, identyczny jak Audacious (Q=1.41, pasma 31/62/125/250/500/1k/2k/4k/8k/16k Hz)
- **Render**: eksport AAC 320 kbps (najwyższy standard) i WAV 24-bit po zastosowaniu EQ (MediaCodec)
- **Master Data**: analiza LUFS jak w Reaperze – ITU-R BS.1770-4, wartości zgodne z EBU R128 (±0.5 LU)
- **UI**: styl Miami (ciemne tło, gradient cyan→różowy) – zmodyfikowany, ale nadal w klimacie Miami
- **Biblioteki**: ExoPlayer, Jetpack Compose, MediaCodec – bez FFmpegKit (MediaCodec wystarcza, mniejszy APK, pełna zgodność Apache 2.0 / F-Droid)

---

## Stan realizacji

### ZROBIONE (v1.0)

| Moduł | Opis |
|-------|------|
| **Utwory** | TracksScreen – lista plików audio, sortowanie |
| **Foldery** | FoldersScreen – przeglądarka folderów |
| **EQ** | 10 suwaków + preamp, jak Audacious, zapis w SharedPreferences |
| **Render** | AAC 320 kbps + WAV 24-bit z EQ (AudioRenderPipeline) |
| **Master Data** | ITU-R BS.1770-4, streaming, kolorowanie, zgodność z Reaperem |
| **Splash** | Logo na czerni (~0,9 s) → menu; backup v2 w SplashScreenLegacy |
| **Full Player** | CustomSeekBar (ikona Play/Pause), seek, Extra, Master Data |
| **Mini Player** | Slider, play/pause |
| **Persystencja** | Ostatni utwór, pozycja (PlaybackStateRepository) |
| **Kontrolki** | Next/Prev, Shuffle, Repeat, zapętlenie playlisty |
| **Wyszukiwarka** | Belka Szukaj, filtrowanie, odtwarzanie po kliknięciu |
| **Granie w tle** | PlaybackService, MediaSession, notyfikacja |
| **O aplikacji** | ExtraScreen: PayPal, mail, GitHub |
| **Ikona** | Własna ikona (S + fale) |

### WAŻNE – przed wydaniem

| Element | Opis |
|---------|------|
| **Link PayPal** | Po założeniu konta PayPal uzupełnij URL w `ExtraScreen.kt` (przycisk „Wesprzyj projekt”). Obecnie: `https://paypal.me/codestudio71` – zamień na swój link gdy konto będzie gotowe. |

---

## v1.0.1 — Poprawki (PRIORYTET)

### Błędy z testów v1.0

| # | Opis | Uwagi |
|---|------|-------|
| **2** | **Przycisk X w notyfikacji** | Brak przycisku X do wyłączenia apki z belki systemowej. Gdy rozwijasz playera w notyfikacji – tylko prev/next/slider. Potrzebny X który faktycznie killuje proces, nie gra w tle. Audio gra dopóki sam nie zrobisz pauzy – za mało. |
| **5** | **Master Data – Clips** | Błąd w algorytmie: czasami 0, czasami 9999 gdy tak nie jest. Wymagany code review. Reszta sekcji OK. |
| **5** | **Master Data – limit czasu** | Ustalić limit minut utworu. Dla albumów 45 min nie ma sensu. Funkcja dla singli (90% &lt;6 min). Bezpieczny limit 10 min, dalej: „Utwór za długi, używaj tylko dla singli”. |
| **5** | **Master Data – Peak** | Może nieco przekalibrowany (np. +0,2 gdy utwór -0,2)? Niepewne, code review. |
| **4.1** | **EQ – Preamp** | Napis „preamp” nie mieści się na standardowym 5,7–6" ekranie. |
| **3** | **Player – suwak** | Cursor play na seekbarze: czasami po kliknięciu (np. 30 s dalej) muzyka skacze ale strzałka się nie przesuwa. Intermittent. Bug w Full Player, NIE w menu głównym (tam OK). |

### Upgrade'y z testów v1.0

| # | Opis | Uwagi |
|---|------|-------|
| **1** | **Belka Szukaj** | Okienko – obramowanie za blisko napisu EXTRA. Szukaj nie działa na Render i EQ (tylko Utwory/Foldery) – do rozkminienia: bug czy future? |
| **1.2** | **Klik w wyszukany utwór** | Po kliknięciu – przejście do okienka playera? Do oceny. |
| **1.3** | **Menu 3 kropki na górnej belce** | Dodać ⋮ obok Szukaj/EXTRA. Wyskakujące okno w kolorze apki. Opcje sortowania: Tytuł (A–Z, Z–A), Artysta (domyślnie A–Z), Czas trwania (rosn./malej.), Data utworzenia (alfabetycznie). Przedzielić mikro-paskiem. Clear + rosnąca/malejąca – jak w każdym playerze. Kropki: gradient niebieski → cyan (3 kropki). |
| **3** | **Player – tło** | Czy menu główne playera powinno się odznaczać od listy (gradient jak w EQ)? Do rozkminienia. |
| **3** | **Player – GUI** | Tło playera jak w EQ (gradient fioletowo-niebiesko-ciemny). Cursor play: 100% większy, cienka ramka neonowa (świecąca), środek półprzezroczysty – futurystyczny styl. |
| **3.1** | **Player – nazwa utworu** | Pełna nazwa nie wyświetla się. Po kliknięciu rozwijać? Co przy zjebanej wielkości liter? Do rozkminienia. |
| **3.1** | **Player – wyłącznik czasowy** | Sekwencja: 5, 10, 15, 30, 45, 60 min + custom do 10 h. |
| **3.2** | **Extra – Playlisty** | Dodać playlisty w Extra? Lepiej niż album/artyści. Co wywalić, co dodać – nie przeładowywać. |
| **4.1** | **EQ – Preamp** | Kolor paska preampa na czerwony – odróżnić od EQ. Preamp niebezpieczny przy +15 dB, to balans. |
| **4.2** | **EQ – kolory kulek** | Zastanowić: czerwony jak w Render AAC? Zostawić? Albo osobny update „Custom Interface” w Extra (Classic Vice City, Sunset, Black night). |
| **4.3** | **EQ – bass 31/62/125** | Na tym telefonie bass zbyt dudni/przester. W innej apce bass czystszy. Winna telefonu czy algorytm? Zapisać, spytać deva od podobnej apki. |
| **6** | **Render AAC** | Dodać opcję 44,1 kHz (obecnie 48k). |
| **6** | **Extra – napis EXTRA** | Po naciśnięciu bardziej premium. Neonowa ramka wokół (przezroczysta jak lampa). Albo: światło skanuje od prawej do lewej, neon shine. Zachować kopię oryginału. |
| **6** | **Ramki w tle** | Mniej kanciaste (np. foldery) – bardziej zaokrąglone, 90° ale nie agresywne. |
| **7** | **Donate – portfele** | PayPal link. Crypto: najlepiej cały wallet (Cake Wallet?) – ETH + XMR w jednym. Gdzie linki prowadzą gdy user nie ma Cake? Madrze zrobić – wsparcie z różnych programów. |
| **8** | **Lokalizacja PL/EN** | Wg języka systemowego, z poszanowaniem privacy. Switch w apce zbędny. Jak robią inni devy – natywnie z systemu. |

### Opcjonalne / większy upgrade

| Opis |
|------|
| Tło belki menu – z czarnego na coś fajniejszego. |
| Foldery – ramka oddzielająca pola: dyskretna 50% przezroczysta neonowa niebieska. Środek niemal przezroczysty. Spójność z gradientem. |

---

## v2.x — Extra futures (PO naprawie błędów)

### v2.x – Player & statystyki

| Funkcja | Opis | Uwagi techniczne |
|---------|------|------------------|
| **Real-time dB / SPL** | Wskaźnik w prawym górnym rogu: zielony (cicho), żółty (średnio), czerwony (głośno). Ostrzeżenie przed uszkodzeniem słuchu. | Wykonalne bez specyfikacji słuchawek – mierzymy amplitudę sygnału wyjściowego (dBFS). Konwersja na SPL wymagałaby kalibracji per urządzenie/słuchawki (np. Apple ~95–105 dB max). Prostsza wersja: względny poziom (0–100%) + kolory. Biblioteki: AudioTrack.getPlaybackHeadPosition + próbki, lub ExoPlayer AudioProcessor do tapowania sygnału. |
| **Wrapped / statystyki** | Liczba odtworzeń, łączny czas, ranking miesięczny/roczny. Przycisk „Udostępnij” → grafika Top 10 (jak Spotify Wrapped). | Proste: SharedPreferences/SQLite – play count, total ms per track. Trudniejsze: generowanie grafiki (Canvas/Compose), export do PNG. |

### v2.x – Extra (5. zakładka)

Struktura po kliknięciu **Extra**:
- **Wrapped** – statystyki, rankingi
- **Record Preview** – nagraj demo (freestyle na beat)
- **O mnie / O programie** (na dole) – Wesprzyj (PayPal), mail (mailto:), Git (link do repo)
- *(ewentualnie)* **Współpraca** – np. studio nagraniowe, link do rezerwacji (do oznaczenia jako reklama w F-Droid / Play Store)

### v3.x – Lyrics & AI

| Funkcja | Opis | Uwagi techniczne |
|---------|------|------------------|
| **Lyrics (synchronized)** | Wyświetlanie tekstu utworu zsynchronizowanego z muzyką podczas odtwarzania (jak karaoke). Ikona w playerze (lewy dolny róg, na poziomie belki play/pause). Źródło: plik LRC, metadane, lub AI (transkrypcja + align timestamps). | Trudne. Format LRC (timestamp + linia) lub podobny. AI: model do transkrypcji wokalu + dopasowanie do czasu – do doprecyzowania. |

### v3.x – Dla artystów

| Funkcja | Opis | Uwagi techniczne |
|---------|------|------------------|
| **Record Preview** | Wybierz plik (beat) → odtwarzaj → kliknij Nagraj → dyktafon włącza się, beat leci, nagrywasz freestyle. **Problem:** głos często nie słychać – głośniki blisko mikrofonu. **Rozwiązanie:** (1) Słuchawki – beat w słuchawkach, głos do mikrofonu; (2) Belka wyboru mikrofonu: Wbudowany / Zewnętrzny (USB-C). Android: `AudioManager.getDevices()`, `AudioRecord.setPreferredDevice()`. W Extra → Record Preview (nagraj demo). | Średnia trudność. AudioRecord, miks z beatem, zapis. Wybór urządzenia wejściowego – `AudioDeviceInfo`. |
| **Sprawdź tonację głosu** | 15 s nagrania → wykrycie tonacji (np. B min) i częstotliwości (np. 200 Hz, uśrednione). Jak Auto-Tune live – widok skali (A–G) + Hz. | Trudne. Wymaga: pitch detection (np. YIN, CREPE), konwersja Hz→nuta, wyświetlanie w czasie rzeczywistym. Biblioteki: aubio, Essentia, lub własna implementacja. |

### Współpraca / reklamy

- **Współpraca ze studiem** – promocja studia w apce, link do rezerwacji sesji. Do oznaczenia jako reklama (F-Droid, Play Store).

---

## Strategia wydania

v1.0 wydane. **v1.0.1**: najpierw błędy (PRIORYTET), potem upgrade'y z testów. Extra futures (v2.x) wprowadzać etapami po stabilizacji.

---

## Testowanie APK – na czym się skupić

- **Master Data**: porównanie z Reaperem (MP3, WAV)
- **EQ**: słuchowo vs Audacious
- **Render**: AAC 320 – bitrate, WAV – brak artefaktów
- **Granie w tle**: (po implementacji) Home, powrót, zamknięcie z notyfikacji
- **Różne urządzenia**: minSdk 24
- **Uprawnienia**: READ_MEDIA_AUDIO, READ_EXTERNAL_STORAGE (Android <13)
- **OOM**: długie pliki (Master Data streaming)
- **Seek**: CustomSeekBar – tap, drag, granice

---

## Struktura plików

```
app/src/main/java/com/example/spoticious/
├── MainActivity.kt
├── player/
│   ├── PlayerViewModel.kt
│   ├── PlaybackStateRepository.kt
│   ├── EqualizerAudioProcessor.kt
│   ├── EqRenderersFactory.kt
│   ├── MasterData.kt
│   ├── MasterDataAnalyzer.kt
│   ├── AudioRenderPipeline.kt
│   └── RenderViewModel.kt
├── folders/
│   └── FoldersViewModel.kt
└── ui/
    ├── components/MiniPlayer.kt
    ├── screens/
    │   ├── SplashScreen.kt
    │   ├── SplashScreenLegacy.kt
    │   ├── MainScreen.kt
    │   ├── TracksScreen.kt
    │   ├── FoldersScreen.kt
    │   ├── EqScreen.kt
    │   ├── FullPlayerScreen.kt
    │   └── RenderScreen.kt
    └── theme/
```

---

## Master Data – szczegóły

- **Algorytm**: ITU-R BS.1770-4, K-weighting, gating (-70 LU, -10 LU)
- **Wartości**: Peak (sample), Clips, Max LUFS-M/S, LUFS-I, LRA
- **Kolorowanie**: Peak (czerwony/pomarańczowy), Clips (czerwony), LUFS-I -16..-9 (zielony), LRA &lt;3 LU (pomarańczowy)
- **Clips**: liczenie na surowych shortach przed konwersją do float
- **Testy**: porównanie z Reaperem, różne formaty

---

## Biblioteki – uzasadnienie

| Biblioteka | Uzasadnienie |
|------------|--------------|
| **ExoPlayer** | Odtwarzanie, wsparcie MediaSession |
| **MediaCodec** | Dekodowanie/kodowanie – systemowe API |
| **Bez FFmpegKit** | MediaCodec wystarcza; FFmpegKit (GPL) zwiększa APK i komplikuje licencje |

---

## Zależności i licencje (F-Droid / Apache 2.0)

| Zależność | Licencja |
|-----------|----------|
| AndroidX (core, lifecycle, activity, compose) | Apache 2.0 |
| ExoPlayer | Apache 2.0 |
| JUnit, Espresso | Apache 2.0 / EPL 1.0 (testy) |

Brak konfliktów z Apache 2.0 i F-Droid.
