# Spoticious — Roadmap do public release

## Faza 1 — Testy v1.0 (teraz)
- Zainstaluj APK na telefonie
- Spisz błędy i co nie działa
- Poprawki → nowy signed APK
- Powtarzaj aż stabilne

## Faza 2 — Release v1.1 (po testach)
- Wdróż poprawki z testów
- Nowy signed APK
- Commit + tag v1.1.0
- Testy ponowne → zatwierdzenie

## Faza 3 — Przygotowanie do public
- Usuń szczegóły z spoticious.md (zostaw ogólny roadmap, usuń szczegóły architektury)
- Squash commitów: `git reset --soft HEAD~X && git commit -m "Spoticious v1.1.0"`
- Napisz README po angielsku (ja piszę, ty dodajesz swoje teksty)
- Uzupełnij linki w ExtraScreen: PayPal + Bitcoin + Ethereum + (opcjonalnie Monero)

## Faza 4 — Public release
- Repo z private → public
- GitHub Release z APK
- Gotowe

## Faza 5 — Extra futures (v2.x)
- Real-time dBFS meter
- Wrapped / statystyki
- Record Preview
- Pitch detection
- Lyrics + AI

---

## Donate — co dodać

PayPal już masz placeholder. Dorzuć:
- **Bitcoin** — adres BTC (darmowy portfel: Electrum, Exodus)
- **Ethereum** — adres ETH
- **Monero (XMR)** — jeśli chcesz privacy coin, Monero jest standard w FOSS community

### Gdy będziesz miał adresy — instrukcja dla agenta:
W `ExtraScreen.kt` w sekcji "Wesprzyj projekt" dodaj:
- Bitcoin: [TWOJ_ADRES_BTC]
- Ethereum: [TWOJ_ADRES_ETH]
- Monero: [TWOJ_ADRES_XMR]

Każdy z ikoną i przyciskiem **kopiuj adres do schowka**.
