# Spoticious – lista zadań dla agenta

**Projekt:** Odtwarzacz audio na Androida (Kotlin, Jetpack Compose, ExoPlayer).  
**Kontekst:** Pełny roadmap w `spoticious.md`. Poniżej lista do realizacji.

---

## v1.0 – przed wydaniem (priorytet)

### 1. Kontrolki playera
- [ ] Przycisk **następny utwór**
- [ ] **Przewijanie** – long press lub double tap na przycisk play
- [ ] **Shuffle** (odtwarzanie losowe)
- [ ] **Repeat** (powtarzanie utworu)  
**Pliki:** `FullPlayerScreen.kt`, `MiniPlayer.kt`, `PlayerViewModel.kt`

### 2. Wyszukiwarka
- [ ] Dyskretna etykieta „Szukaj” u góry (kolorowa, w stylu Miami)
- [ ] Po kliknięciu – wysuwana belka wyszukiwania (nowoczesna, dopasowana do layoutu)
- [ ] Filtrowanie listy utworów po wpisaniu tekstu
- [ ] Kliknięcie w wynik → odtwarzanie utworu  
**Pliki:** `TracksScreen.kt`, `FoldersScreen.kt`, `FoldersViewModel.kt`

### 3. Granie w tle
- [ ] Foreground Service (MediaBrowserServiceCompat)
- [ ] MediaSession + MediaSessionCompat
- [ ] Notification z kontrolkami (play/pause, zamknij)
- [ ] Muzyka gra po wyjściu z apki (np. przycisk Home)  
**Pliki:** nowy `MediaPlaybackService.kt`, `PlayerViewModel.kt`, `AndroidManifest.xml`

### 4. O aplikacji
- [ ] „Wesprzyj projekt” → link do PayPal
- [ ] Adres e-mail → `mailto:`
- [ ] Link do repozytorium Git  
**Lokalizacja:** do ustalenia – Extra (5. zakładka) lub osobny ekran

### 5. Ikona aplikacji
- [ ] Własna ikona zamiast domyślnej (np. na bazie logo)  
**Pliki:** `res/mipmap/`, `res/drawable/`

### 6. Extra (5. zakładka)
- [ ] Rozważyć dodanie 5. zakładki „Extra” do belki nawigacyjnej
- [ ] Struktura: Wrapped, Record Preview, O mnie (na dole)
- [ ] Zachować spójny układ z obecnymi 4 zakładkami (Utwory, Foldery, EQ, Render)

---

## Po v1.0 (updatey)

- [ ] Playlisty
- [ ] (Opcjonalnie) EQ zakres ±12 dB zamiast ±15

---

## Przydatne pliki

| Plik | Opis |
|------|------|
| `spoticious.md` | Pełny roadmap, Extra Futures (v2, v3), testowanie |
| `PlayerViewModel.kt` | Logika odtwarzania, EQ, Master Data |
| `MainScreen.kt` | Nawigacja, zakładki |
| `FullPlayerScreen.kt` | Pełny odtwarzacz, CustomSeekBar |
| `FoldersViewModel.kt` | Lista plików audio |

---

## Zasady

- **UI:** Jetpack Compose, Material 3, styl Miami (MiamiCyan, MiamiPink)
- **Bez FFmpegKit** – używamy MediaCodec
- **Licencje:** Apache 2.0, zgodność z F-Droid
