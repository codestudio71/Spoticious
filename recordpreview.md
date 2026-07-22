# Record Preview / Freestyle — brief dla Claude FABLE

> **Projekt:** Spoticious (Android, Kotlin, Compose, ExoPlayer)  
> **Gałąź:** `ui-miami` (stan po testach usera, 2026-07-23)  
> **Cel tego pliku:** struktura problemu + mandat produktowy. **Nie łatać dalej starego miksu „sprytnymi” obejściami.**  
> **Autor raportu QA:** owner (telefon / signed APK). Sesja agentów Cursor: fixy sync/gain/clips — **user: nic się nie poprawiło w freestyle na beacie.**

---

## FINAL RESULT — o co chodzi z tą funkcją (produkt)

### Po co to jest
**Record Preview → Freestyle** = rap / freestyle / wokal **na podkładzie (beat)** w telefonie, offline, FOSS.

User:
1. Wybiera plik beatu z dysku.
2. Słucha beatu w **słuchawkach**, ustawia głośność bitu suwakiem (także **w trakcie** nagrywania).
3. Naciska REC — mówi / rapuje do mikrofonu, jednocześnie słysząc beat.
4. Naciska STOP.
5. Dostaje **jeden gotowy plik WAV**, który może odsłuchać, zapisać / udostępnić.
6. program potrafi odczytywać no mikrofon z słuchawke lub podlaczony przez usb slot i jest opcja wybrana inputu zarówno  mikrofon jak i glosniki /słuchawki ( zaauwazysz pewnie strukturę w obecnym kodzie ) 

Osobno jest tryb **Voice only** (sam dyktafon, bez beatu) — prostszy; ma zostać czysty.

### Jaki ma być efekt końcowy (Definition of Done produktowe)

Po STOP user odsłuchuje plik i słyszy:

| Warstwa | Jak ma brzmieć |
|---------|----------------|
| **Wokal** | Czysty, zrozumiały, jak porządny dyktafon — **bez** trzasków, dropów, „rozjechanego” processingu, bez ścięcia po ~7 s |
| **Beat** | Pod spodem, w sync z tym co słyszał przy REC, głośność zgodna z suwakiem |
| **Całość** | Jeden plik stereo/mono WAV (obecny kontrakt: mono mix), gotowy do share — **nie** dwa osobne pliki które user musi sam złożyć w DAW |

**Sukces =** „nagrałem freestyle na bicie → plik brzmi jak to co słyszałem + mój głos jest OK”.  
**Porażka (stan obecny) =** głos zepsuty / crash dźwięku po kilku sekundach / brak regulacji volume przy REC / długi „miksujący” proces który psuje plik.

### Czego to NIE jest
- Nie DAW, nie multitrack edytor, nie Autotune.
- Nie „nagraj wokal i ręcznie dodaj beat później” jako jedyna opcja — **cel produktu to jeden plik z miksem** (ale obecna implementacja miksu jest do wyrzucenia / przebudowy). tzn mix w sensie ze po prostu vokal i beat graja razem i razme się nagrywają to ma być dla raperow by mogli robic swoje preview nagrania pomyslow flow vibes pod dany beat rozumiesz i odpalai  sobie po configu inputu np. z czego nagranie czy mikrofon ktorys z tel czy zewnętrzny albo z słuchawek i zrodlo beatu czy na glosniki telefonu czy podlaczonesluchawki czy glosniki zewnętrzne     full custom daje swobode i robi te funckje unique rozumiesz .
- Audio Cut (osobny moduł Extra który jest już gotowy nie ruszaj tego ) = cięcie gotowych plików — **nie jest to ** część Record Preview.


Poznizej skrot mojego q&a z agentem  badz ostrozny i dorbze rozkmin bo niektore rzeczy powtarza co najwazniejsze masz to wyzej zanim rozpoczniesz zmiany zapytaj mnie i przedstaw koncept wszelkich zmian dopiero po moej akceptacji jedziemy z tym .

---

## 0. Mandat (czytaj najpierw)

1. Funkcja **miksu beat + wokal po STOP** (`FreestyleMixdown`) jest **porażką produktową**.
2. User stawia hipotezę (silnie potwierdzoną chronologią gita): **od wprowadzenia mixdown wszystko się sypie.**
3. Docelowo: **`FreestyleMixdown` → total delete albo przebudowa od zera (prostsza).**  
   Nie dokładać kolejnej warstwy „fixów” na ten sam pipeline, jeśli nie da się udowodnić czystego wokalu + czystego beatu osobno.
4. **Regulacja volume beatu MUSI działać w trakcie REC** — obecne „freeze suwaka” to **nie rozwiązanie**, tylko obejście które zabija UX.
5. Voice-only (bez beatu) traktować jako **baseline**: jeśli voice-only jest czyste, a freestyle nie — winny tor freestyle / mix / interakcja z ExoPlayer+mic, nie „zły mikrofon w ogóle”.

---

## 1. error z raportu QA (2026-07-23)

### 1.1 Freestyle na beacie — CRITICAL

| # | error  | Szczegóły usera |
|---|--------|-----------------|
| A | Fixy sesji **nic nie zmieniły** | Sync/gain/soft-limit itd. — nadal spierdolone |
| B | Suwak BEAT volume **freeze podczas nagrywania** | Brak regulacji w trakcie REC — „sprytny plan” agenta = nieakceptowalny |
| C | Plik po STOP = **tragedia** | Nie (tylko) balans beat/wokal — **wokal brzmi jak zepsuty processing** |
| D | Zakłócenia totalne | Porazka jakości nagrania |
| E | Czas: **do ~7 s OK, potem total crash dźwięku** | Silna wskazówka: błąd narasta / chunk / buffer / mix / seek / decode limitu — nie „zły gain od pierwszej próbki” |
| F | Proces mix po STOP **za długi** | Długi processing = ryzyko korupcji / UX hell; user wiąże błędy z tą fazą |

### 1.2 Hipoteza chronologiczna (user + git)

- **Zanim** pojawił się single-WAV mix beat+vocal — freestyle / record nie miał tego poziomu jebania (wg usera).
- **Od kiedy** agent wdrożył miksowanie do jednego pliku — zaczęło się psuć.

**Commit wprowadzający `FreestyleMixdown.kt`:**

| Pole | Wartość |
|------|---------|
| Hash | `21e1929` |
| Data | **2026-06-02** |
| Message | `v1.0.3: freestyle beat+vocal  single WAV` |
| Skutek | Po STOP: dekoduj beat (MediaCodec/PCM) + miksuj z wokalem WAV → **nadpisz** plik wokalu |

Późniejsze commity (nie naprawiły QA):

- `343657f` — Checkpoint: freestyle sync/gain fixes + Audio Cut…
- `770da91` — `spoticious record gain` (krzywa suwaka %² × 0.4) — **preview meter OK dla usera, final freestyle NIE**

---

## 2. Architektura obecna (jak jest zbudowane)

```
[UI RecordPreviewScreen]
        │
        ▼
[RecordViewModel] ──► [RecordingSession]
        │                      │
        │                      ├── VocalRecorder (AudioRecord → WAV mono 48k)
        │                      ├── BeatPreviewPlayer (osobny ExoPlayer, preview/loop)
        │                      ├── RecordingService (FGS)
        │                      └── po STOP + freestyle:
        │                            FreestyleMixdown.mixInPlace(vocalWav, beatUri)
        │                                 │
        │                                 ├── PcmStreamDecoder.decodeUriToMonoFloatLimited(beat)
        │                                 ├── chunkowane czytanie wokalu
        │                                 ├── softLimit(v + b)
        │                                 └── nadpisanie vocalWav
        ▼
   SavedTakePreviewPlayer (odsłuch wyniku)
```

### Kluczowe pliki

| Plik | Rola |
|------|------|
| `audio/record/FreestyleMixdown.kt` | **Główny podejrzany** — mix post-STOP |
| `audio/record/RecordingSession.kt` | Start/stop, offset beatu, woła mix, gain |
| `audio/record/VocalRecorder.kt` | Zapis mic → WAV |
| `audio/record/BeatPreviewPlayer.kt` | ExoPlayer podkładu (słyszany przy REC) |
| `audio/record/RecordViewModel.kt` | UI state, dBFS beatu z peak×gain |
| `audio/cut/PcmStreamDecoder.kt` | Decode beatu do float mono (używane w mixie) |
| `ui/screens/RecordPreviewScreen.kt` | UI; **blokada suwaka** przy REC |

### Dlaczego suwak jest „freeze” (fakt w kodzie, nie zgadywanie)

W `RecordPreviewScreen.kt`:

```kotlin
val beatUiLocked = isRecording || isBusy
// Slider beat: enabled = !beatUiLocked
```

Czyli **celowo wyłączony** podczas `Recording` / `Stopping`.

Powód historyczny sesji agentów: ciągłe `ExoPlayer.setVolume` przy drag → trzaski łapane przez mic.  
Agent „rozwiązał” to przez: (1) apply volume dopiero on-release **przed** REC, (2) **lock suwaka w trakcie REC**.

**User: to nie jest rozwiązanie. Regulacja must be during REC.**

Wymaganie dla FABLE:

- Dać live regulację gainu beatu w trakcie nagrywania **bez** zipper noise w mic (inna architektura: np. gain tylko w stanie/mix path, albo osobny bus, albo ramping, albo nie Exo volume na każdym ticku — **nie** lock UI).

---

## 3. Model błędu — jak myśleć (dla FABLE)

### 3.1 Rozdziel trzy tory (obowiązkowa diagnostyka przed rewrite)

1. **Mic-only WAV** (Voice only lub freestyle z gain beatu = 0 / bez mixu) — czy wokal czysty przez >7 s?  
2. **Beat preview live** (Exo przy REC) — czy słuchawki OK, bleed?  
3. **Post-STOP mix** (`FreestyleMixdown`) — czy korupcja powstaje **dopiero tu**?

User mówi: w finalnym pliku widać **błąd processingu wokalu**; do ~7 s OK potem crash.  
To sugeruje m.in.:

- chunk mix / encode 16↔24,
- błędne `bytesPerFrame` / alignment po N sekundach,
- limit decode beatu / pętla / offset,
- race nadpisywania pliku podczas preview,
- uszkodzenie nagłówka WAV / data size,
- albo bleed+AGC — ale user wskazuje **processing**, nie tylko „głośny beat”.

### 3.2 Decyzja produktowa (preferencja usera)

**A (preferowane):** usunąć post-mix; zapisać **osobno** wokal (i opcjonalnie nie mieszać w apce) **albo** prostszy miks online (jeden clock, jeden buffer) bez ciężkiego offline decode całego beatu.  
**B:** przebudowa `FreestyleMixdown` od zera — krótka, testowalna, streaming, bez „12 min float w RAM”, z walidacją bit-exact wokalu przed/po.

Długi offline mix = red flag (user: „za długo, pierdoli wszystko”).

---

## 4. Acceptance criteria (Freestyle) — Definition of Done

- [ ] Voice-only: czysty WAV, dowolna długość (>>7 s), bez artefaktów.
- [ ] Freestyle + beat (słuchawki): finalny plik = wokal zrozumiały + beat pod spodem, **bez** total crash po ~7 s.
- [ ] Suwak BEAT **działa w trakcie REC**; zmiana słyszalna od razu; **nie** zamrażać UI.
- [ ] Brak zipper/trzasków w mic przy ruchu suwaka (albo akceptowalny micro-ramp).
- [ ] Sync wokal↔beat w granicach percepcji (±~20–30 ms).
- [ ] Mix (jeśli zostaje) kończy się szybko na typowym telefonie / nie korumpuje wokalu.
- [ ] Gain suwaka = to samo w preview i w finalnym pliku (jedna definicja).

---

## 5. Co NIE robić

- Nie „fixować” tylko krzywej dBFS / soft-limit / kolejnego seek await, jeśli nie ma dowodu że to root cause korupcji po 7 s.
- Nie zostawiać `enabled = false` na suwaku jako „fixu jakości”.
- Nie mieszać w tym samym PR: EQ Audacious, Master Data clips, Audio Cut, sort menu — **osobne zadania**.

---

## 6. Szybki kontekst wersji

| Ref | Znaczenie |
|-----|-----------|
| `21e1929` | Wprowadzenie `FreestyleMixdown` |
| `versionName` 2.0.0 / code 5 | Oficjalny numer release w gradle |
| Commity `2.0.1`…`record gain` | Etykiety sesji — nie osobne GitHub releases |
| `public-1.0.2` / `backup-public` | Kopie starego public — nie ruszać przy pracy nad Record |

---




#  inne ustalenia QA (poza core Freestyle) dodartkowe  zadania  

> Dla pełnego obrazu sesji. **FABLE: Freestyle najpierw.** Reszta = backlog / inny agent.



## A. Master Data — Clips

- Plik ze studia (DAW: **0 clips**): Spoticious spadło z ~50 000 → ~**7 000** po fixie licznika runów — **nadal źle**. szczególnie dla formatow mp3 webm etc dla WAV jako  jedyne działa dobrze 
- Peak / LUFS na tym samym pliku: **OK / zgodne** (regresji poza Clips nie widać).
- Wniosek: definicja/próg/decode lossy nadal fałszywie dodatnie; potrzebna osobna sesja premium (nie „kolejna łatka ±1”).

## B. Master Data — WAV 60 MB szybsze niż MP3 6 MB

**To nie magia i raczej nie bug.**

- WAV → ścieżka `decodeWavStreaming` (prosty odczyt PCM).
- MP3 → `MediaCodec` full decode + analiza — CPU bound.
- Większy plik WAV może być **szybszy** niż mały MP3, bo nie ma kosztu dekompresji lossy.

## C. Master Data — opisy LUFS-M / LUFS-S vs Reaper LUFS -I  LRA  PEAK 


---

*Koniec briefu. Źródło prawdy QA: owner 2026-07-23. Kod: repo Spoticious / `ui-miami`.*
