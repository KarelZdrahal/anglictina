# Angličtina B1 → B1+

Roční kurz angličtiny pro dva účastníky: týdenní lekce (PDF + klíč + poslech) a Android aplikace pro denní opakování.

## Instalace aplikace
1. V telefonu otevři stránku **Releases** tohoto repozitáře a stáhni `anglictina.apk`
   (přímý odkaz: `https://github.com/<uživatel>/anglictina/releases/latest/download/anglictina.apk`).
2. Otevři stažený soubor. Telefon se zeptá, jestli povolit instalaci z tohoto zdroje (prohlížeče) – povol.
3. Nainstaluj a spusť. Nové lekce si aplikace stahuje sama, kvůli nim se nic nepřeinstalovává.

## Co aplikace umí
- **Denní opakování lekcí** – volitelná délka 5–30 min, slovíčka i gramatika z lekcí, opakování v rozestupech.
- **Slovní zásoba bez omezení** – zásoba slov podle témat a úrovní (B1–C1), volitelný počet nových slov denně, další dávky kdykoli.
- Výslovnost (hlasový výstup telefonu), statistiky, série dní, denní připomínka, tmavý režim, práce offline.

## Struktura
- `data/` – obsah pro aplikaci (`index.json`, `lessons/`, `bank/`), stahuje se z `raw.githubusercontent.com`.
- `lessons-src/` – zdrojové soubory lekcí (`lesson_XX.json`).
- `tools/` – skripty: `export_app.py` (lekce → data aplikace), `build_bank.py` (zásoba slov), `build_lesson.py` (PDF + MP3).
- `app/` – Android aplikace (Kotlin, Jetpack Compose).
