# Master Data — Clips — brief dla Claude FABLE

> **Projekt:** Spoticious · gałąź `ui-miami`  
> **Cel:** Clips w Master Data ma być **wiarygodne** jak Peak/LUFS (te już user akceptuje).  
> **QA:** 2026-07-23 — plik ze studia / DAW = **0 clips**, Spoticious nadal pokazuje **tysiące**.  
> **Release 2.0 dziś** — to premium feature; fałszywe tysiące = wstyd w tabeli Master Data.

Powiązane: Freestyle = osobny brief `recordpreview.md`. **Peak / LUFS / LRA nie ruszać**, jeśli działają — tylko Clips (+ ewentualnie flaga reliability UI).

---

## FINAL RESULT — o co chodzi

### Po co jest kolumna Clips
User (mastering / home studio) otwiera Master Data i chce wiedzieć:

> **Czy ten plik cyfrowo uderza w pełną skalę (clip), czy jest czysty?**

- **0** = czysto (jak w DAW / Youlean / izotope na tym samym pliku)  
- **mała liczba** = realne, rzadkie uderzenia w sufit  
- **tysiące** na czystym masterze = **bug** — niszczy zaufanie do całej tabeli

Clips to nie „ile próbek jest głośnych”. To sygnał: **czy mastering jest cyfrowo clipped**.

### Sukces (Definition of Done)

| Kryterium | Wymaganie |
|-----------|-----------|
| Plik referencyjny ze studia (user: DAW = 0 clips) | Spoticious Clips = **0** (albo `*` / `—` jeśli format uznany za niewiarygodny — **nie** fałszywe 7000) |
| Peak / LUFS / LRA | **Bez regresji** — te same wartości co przed fixem |
| WAV (PCM) | Potwierdzić 0 na czystym pliku; user mówi że WAV „zdaje się OK” — **i tak zweryfikować** |
| Lossy (MP3/AAC…) | Albo poprawny wynik, albo uczciwy `*` / `—` + nota w glossary — **nigdy** losowe tysiące |
| UI | Liczba albo `*` / `—`; bez kłamstwa „profesjonalnego” |

---

## Problem (stan obecny)

### Raport usera
1. Ten sam plik, DAW / pro studio: **0 clips**.  
2. Spoticious wcześniej: ~**50 000**.  
3. Po „fixie” licznika (run ≥2 próbek): ~**7 000**.  
4. **Nadal zjebane** — 7k vs 0 to nie „prawie dobrze”.  
5. Peak + LUFS na tym samym pliku: **OK** (nie ruszaj tej matematyki bez potrzeby).  
6. Wrażenie usera: **WAV raczej OK**, problemy głównie przy **lossy / kontenerach skompresowanych**.

### Co już zmieniano (i dlaczego to za mało)
W `MasterDataAnalyzer.kt`:
- `CLIP_THRESHOLD = 0.999f`
- Licznik: **1 clip = run ≥2 kolejnych próbek na szynie** (nie prawie każda próbka FS)
- Opus/Vorbis/WebM: `clipsReliable = false` → UI `*`

To ścięło rząd wielkości, **nie naprawiło prawdy** względem DAW.

### Podejrzenia (do sprawdzenia — nie zgadywać w ciemno)

1. **MediaCodec path** zawsze czyta PCM jak **int16** (`asShortBuffer`) — na float/24-bit wyjściu = śmieci → fałszywe „rail”.  
   (W Audio Cut jest już `KEY_PCM_ENCODING` w `PcmStreamDecoder` — Master Data **nie**.)  
2. **Lossy decode** (MP3/AAC) potrafi wygenerować ±FS nawet gdy source w DAW był czysty — wtedy albo lepsza definicja clipu, albo `*` dla niewiarygodnych MIME.  
3. Próg 0.999 / definicja „event” vs true-peak overs — rozjazd z DAW.  
4. Encoder delay / padding skip — edge cases.

**Zasada:** najpierw ten sam plik jako **WAV z DAW export (0 clips)** → musi dać **0**.  
Potem ten sam master jako **MP3/AAC** → albo 0 / blisko prawdy, albo `*` z uzasadnieniem.

---

## Formaty — priorytet testów (top zestaw Spoticious)

User pyta: MP3, AAC, Opus, FLAC, WebM (+ WAV).  
**Tak — to jest sensowny top zestaw** pod realne użycie (lokalny player / yt-dlp / studio).

| Priorytet | Format | Typowe rozszerzenia | Uwagi |
|-----------|--------|---------------------|--------|
| **P0** | **WAV** | `.wav` | PCM 16/24/32; user: „chyba OK” → **i tak obowiązkowy test** na pliku 0-clip z DAW |
| **P0** | **FLAC** | `.flac` | Lossless; powinien zachować się jak WAV (ten sam PCM po decode) |
| **P0** | **MP3** | `.mp3` | Najczęstszy; tu najczęściej fałszywe clips |
| **P0** | **AAC** | `.m4a`, `.aac`, `.mp4` (audio) | Jak MP3 — lossy + MediaCodec |
| **P1** | **Opus / WebM** | `.opus`, `.webm` | Dziś często `*` — OK jeśli uczciwie; nie pokazywać tysięcy |
| **P2** | Ogg Vorbis | `.ogg` | Już w `isClipsUnreliableMime` (vorbis) — zweryfikować UI |

**„Top 5” do DoD release 2.0:**  
1. WAV · 2. FLAC · 3. MP3 · 4. AAC/M4A · 5. Opus/WebM  

Reszta = nice, nie blokuje jeśli te pięć jest uczciwe.

### Macierz akceptacji (wypełnić przy teście)

Dla **tego samego mastera** (user: 0 clips w DAW), wyeksportuj / zakoduj warianty:

| Plik | Oczekiwane Clips | Peak/LUFS |
|------|------------------|-----------|
| `ref.wav` (PCM z DAW) | **0** | bez regresji |
| `ref.flac` | **0** | bez regresji |
| `ref.mp3` | **0** albo `*` (nie tysiące) | bez regresji Peak/LUFS |
| `ref.m4a` (AAC) | j.w. | j.w. |
| `ref.webm` / opus | `*` / `—` OK; liczba tylko jeśli wiarygodna | Peak/LUFS OK lub już wspierane |

---

## Pliki w kodzie (gdzie pracować)

| Plik | Rola |
|------|------|
| `player/MasterDataAnalyzer.kt` | Decode WAV + MediaCodec, próg, licznik clips |
| `player/MasterData.kt` | `formatClips()`, `clipsReliable`, `CLIPS_UNSUPPORTED` |
| `ui/screens/FullPlayerScreen.kt` | Tabela + glossary / `*` nota |
| `res/values/strings.xml` (+ pl) | Opisy Clips / unavailable |
| (wzór) `audio/cut/PcmStreamDecoder.kt` | Poprawne `KEY_PCM_ENCODING` — **reuse idei**, nie kopiuj na ślepo całego Cut |

**Nie zmieniać** bez potrzeby: algorytm LUFS/LRA/peak (poza wspólnym odczytem PCM jeśli trzeba).

---

## Preferowana strategia naprawy (kolejność)

1. **Ground truth WAV** — 0 w DAW → 0 w Spoticious. Jeśli tu FAIL = bug w liczniku/threshold/WAV path.  
2. **FLAC** — powinno iść jak lossless.  
3. **MediaCodec PCM encoding** — nie zakładać zawsze int16 (patrz Cut).  
4. **MP3/AAC** — jeśli po poprawnym PCM nadal fałszywe FS z kodeka:  
   - albo ostrzejsza / zgodna z DAW definicja,  
   - albo `clipsReliable = false` dla tych MIME (uczciwy `*`) zamiast kłamliwych tysięcy.  
5. Opus/WebM — utrzymać / doprecyzować `*`; glossary po ludzku.

User woli **prawdę** (0 albo `*`) niż „lepszą liczbę która nadal jest zła”.

---

## Czego nie robić

- Nie „przybliżać” progu aż wszystko pokaże 0 (ukrywanie realnych clipów).  
- Nie psuć Peak/LUFS „przy okazji”.  
- Nie mieszać w tym PR Freestyle / EQ / Cut UI.  
- Nie uważać 7000→500 za sukces — sukces to zgodność z DAW / uczciwy `*`.

---

## Acceptance checklist (FABLE → owner)

- [ ] `ref.wav` (0 w DAW) → Clips **0**  
- [ ] `ref.flac` → **0**  
- [ ] `ref.mp3` → **0** lub `*` (udokumentować wybór)  
- [ ] `ref.m4a` → j.w.  
- [ ] Opus/WebM → nie pokazuje tysięcy  
- [ ] Peak / LUFS / LRA na tych plikach bez regresji  
- [ ] Krótka nota w glossary jeśli `*` (PL+EN), zrozumiała dla zwykłego usera  

---

*QA owner 2026-07-23. Po fixie: smoke na telefonie → razem z Freestyle pod release 2.0.0.*
