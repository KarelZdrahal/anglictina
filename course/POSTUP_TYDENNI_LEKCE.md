# Postup pro týdenní lekci (pro automatickou rutinu)

Každou neděli večer se vytvoří lekce na následující pondělí.

1. Přečti `course/sylabus.json` (plán) a `course/stav_kurzu.json` (co už proběhlo, `notes_for_next`).
   Další lekce = `next_week`. Typ týdne (`lesson` / `review` / `test`) určuje obsah.
2. Napiš `lessons-src/lesson_XX.json` ve stejném formátu jako `lesson_01.json`:
   - struktura podle dokumentu „Struktura lekce angličtiny“: 1) téma + základní (≈25) a rozšiřující (≈12) slovní zásoba,
     2) opakovací cvičení všech typů (gramatika se stručným přehledem, čtení, poslech, slovní zásoba),
     3) upevnění a rozvoj, 4) slovíčka k zapamatování; cca 60 min; aspoň 4 cvičení ve dvojici (`"pair": true`).
   - Opakovací část zahrne i látku z minulých lekcí (viz `notes_for_next` a spirála v sylabu).
   - Slovíčka se nesmí opakovat s dřívějšími lekcemi (seznam ve `stav_kurzu.json`).
   - `extra_practice`: aspoň 20 dalších položek (choose / gap / order) pro aplikaci.
   - Opakovací týden: bez nové gramatiky, cvičení napříč lekcemi bloku; slovníček = výběr nejdůležitějších slov bloku.
   - Testový týden: test (gramatika, slovní zásoba, čtení, poslech, psaní) + projekt ve dvojici; klíč s bodováním.
3. `python3 tools/build_lesson.py lessons-src/lesson_XX.json out/LXX --voices <dir s hlasy piper>`
   (hlasy: github.com/rhasspy/piper/releases/download/v0.0.2/voice-en-us-ryan-medium.tar.gz a voice-en-us-amy-low.tar.gz)
4. Zkopíruj `Lekce_XX_*.pdf` → `data/lessons/LXX.pdf`, `Lekce_XX_poslech.mp3` → `data/lessons/LXX.mp3`.
5. Přidej nové téma do zásoby slov: `tools/bank-src/NN_tema.txt` (40 slov, `en|cz|příklad|úroveň`), bez duplicit
   s existujícími balíčky; pak `python3 tools/build_bank.py`.
6. `python3 tools/export_app.py lessons-src/lesson_XX.json` (aktualizuje `data/index.json`).
7. Aktualizuj `course/stav_kurzu.json` (záznam lekce, `next_week`, `notes_for_next`).
8. Commit + push do `main`. Aplikace si nový obsah stáhne sama.
9. Nahraj PDF (lekce + klíč) a MP3 na Google Disk do složky Angličtina/Lekce (id 1W7BSnwSlH6eUexx1i1VhggBe4D1aKoQi).
