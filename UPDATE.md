# Spoticious — Update v1.0.1

## Raport z dzisiejszej pracy

---

## 1. Playlista w Extra

### Przywrócenie funkcjonalności
- **Kafelek „Playlista”** w ExtraScreen (obok Wrapped, Record Preview)
- Tytuł: „Playlista”, podtytuł: „Kolejka odtwarzania”
- Kliknięcie otwiera **PlaylistScreen**

### Nawigacja
- **MainScreen** → klik EXTRA → **ExtraScreen** → klik „Playlista” → **PlaylistScreen**
- Przycisk Wstecz w PlaylistScreen wraca do ExtraScreen

---

## 2. PlaylistScreen.kt (nowy plik)

| Element | Opis |
|---------|------|
| **Lista utworów** | Numerowana lista z playlisty (1–N, max 1000) |
| **Kliknięcie** | Odtwarza wybrany utwór |
| **Długie przytrzymanie** | Dialog „Przenieś do pozycji” – zmiana kolejności |
| **Shuffle** | Ikona obok tytułu (czerwona gdy włączone, szara gdy wyłączone) |
| **Stan pusty** | „Brak utworów w playliście” |
| **Podpowiedź** | „X utworów • przytrzymaj aby zmienić ranking” |

---

## 3. PlayerViewModel — nowe funkcje

| Funkcja | Opis |
|---------|------|
| **moveTrack(fromIndex, toIndex)** | Przenosi utwór w playliście; aktualizuje `currentIndex` przy przenoszeniu aktualnie odtwarzanego utworu |

---

## 4. Przycisk EXTRA (prawy górny róg)

- **Ramka** 1.5 dp, kolor MiamiPink
- **Efekt glow** w `drawBehind` (neon)
- `RoundedCornerShape(4.dp)`

---

## 5. FoldersScreen

| Zmiana | Opis |
|--------|------|
| **Kafelek folderu** | `RoundedCornerShape(16.dp)` |

---

## 6. FullPlayerScreen — nazwa utworu

| Zmiana | Opis |
|--------|------|
| **basicMarquee** | Nazwa utworu: animacja marquee (iterations = Int.MAX_VALUE, repeatDelayMillis = 1000, initialDelayMillis = 2000, velocity = 50.dp) |
| **Usunięto** | `overflow = TextOverflow.Ellipsis` |

---

## 7. Pliki zmodyfikowane

| Plik | Zmiany |
|------|--------|
| **ExtraScreen.kt** | Kafelek Playlist, `onPlaylistClick`, `ExtraCard` z opcjonalnym `onClick` |
| **FullPlayerScreen.kt** | basicMarquee na nazwie utworu, usunięto Ellipsis |
| **FoldersScreen.kt** | `RoundedCornerShape(16.dp)` na kafelku folderu |
| **MainScreen.kt** | `showPlaylistScreen`, nawigacja Extra↔PlaylistScreen, przycisk EXTRA (border + glow) |
| **PlaylistScreen.kt** | Nowy plik |
| **PlayerViewModel.kt** | `moveTrack()` |

---

## 8. Stan przed/po

| Przed | Po |
|-------|-----|
| Brak PlaylistScreen | Pełny ekran playlisty z listą, shuffle, zmianą kolejności |
| Kafelek Playlist usunięty z Extra | Kafelek przywrócony |
| Brak `moveTrack` | Funkcja dodana |
| Nazwa utworu z Ellipsis | Nazwa z animacją marquee |
| Kafelek folderu 8.dp | Kafelek folderu 16.dp |

---

## 9. Wersja

- **Bazowa:** 1.0
- **Docelowa:** 1.0.1
- **build.gradle.kts:** `versionName = "1.0"` — do aktualizacji na `"1.0.1"` przy wydaniu

---

## 10. Pomysł (do zatwierdzenia): sekcja **dBFS** na Full Player

**Status:** tylko opis — **bez implementacji**, do Twojej decyzji (tak / nie / zmiany).

### Cel
- Pokazać **poziom sygnału w stylu DAW** (skala **dBFS**, jak Cubase / Reaper): wartość liczona z **sygnału pliku / PCM**, **bez** kalibracji pod model słuchawek (to nie SPL w dB „na uchu”).

### Gdzie w UI (żeby nie psuć flow Master Data)
- **Nie** w jednym rzędzie z tabelą Master Data (Peak, LUFS, itd.) — osobno, żeby nie rozjeżdżać layoutu.
- **Trzecia sekcja** na full playerze, **pod** rozwiniętym blokiem **Extra** i **Master Data** (kolejność: Extra → Master Data → **dBFS**).
- Sekcja **nie jest** „drugim rozwiń/zwiń” jak Master Data: **zawsze jest miejsce na treść** (np. wąski pasek / wiersz z wartością **real time** podczas odtwarzania), albo pusta / „—” gdy brak odtwarzania — do doprecyzowania przy wdrożeniu.

### Ukrywanie (żeby nie irytować)
- Obok (lub w nagłówku sekcji) **przycisk / przełącznik „Ukryj”** (lub podobnie).
- Stan **zapamiętany** (np. `DataStore` / `SharedPreferences`): **ukryte trwa**, dopóki użytkownik **ręcznie** nie włączy z powrotem — bez zmuszania do patrzenia na metryki przy każdym wejściu w playera.

### Nazwa
- Roboczo sekcja może się nazywać **„dBFS”** (albo „Poziom (dBFS)”); finalna etykieta do ustalenia.

### Technicznie (krótko, na później)
- Real time = aktualizacja z **tego samego typu pomiaru co sygnał cyfrowy** (peak / RMS z bufora), bez zmiany reszty odtwarzacza dopóki nie zaakceptujesz pomysłu.

---

*Raport wygenerowany na podstawie sesji z dnia pracy nad Spoticious.*
