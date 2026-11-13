# Postup pro týdenní lekci (pro automatickou rutinu)

Každý čtvrtek ráno se vytvoří další lekce (datum v sylabu = čtvrtek, kdy vzniká a studuje se). Lekce 1 = 12. 11. 2026, lekce 2 vzniká 19. 11. 2026.

1. Přečti `course/sylabus.json` (plán) a `course/stav_kurzu.json` (co už proběhlo, `notes_for_next`).
   Další lekce = `next_week`. Typ týdne (`lesson` / `review` / `test`) určuje obsah.
2. Napiš `lessons-src/lesson_XX.json` ve stejném formátu jako `lesson_01.json`.
   **Úroveň (rozhodnutí Karla 13. 11. 2026):** opakovací část = B1, krátká; hlavní důraz na rozšiřování na úrovni B1+ až B2.
   Nesmí to být příliš jednoduché pro absolventa B1, ale ani kurz B2.
   - struktura podle dokumentu „Struktura lekce angličtiny“, cca 60 min, aspoň 4 cvičení ve dvojici (`"pair": true`):
     1) téma + slovní zásoba: základní ≈20 položek B1+/B2 (kolokace, frázová slovesa, ustálená spojení),
        rozšiřující ≈10 položek B2; **žádná běžná B1 slova jako „nová“** (typu shy, lazy, ticket);
     2) opakovací cvičení (≈12 min, úroveň B1): krátký přehled `review_b1` ze sylabu + 2–3 rychlá cvičení
        (gramatika, slovní zásoba minulých lekcí);
     3) rozšíření a rozvoj (≈35 min, B1+–B2): nová gramatika `grammar` se stručným přehledem, delší text
        ke čtení (≈350–450 slov) a poslech (≈2–3 min, přirozené tempo), cvičení na novou gramatiku a slovní zásobu,
        konverzace / diskuse ve dvojici;
     4) slovíčka k zapamatování (výběr ≈20 nejdůležitějších B1+/B2 položek).
   - Slovíčka se nesmí opakovat s dřívějšími lekcemi (seznam ve `stav_kurzu.json`).
   - `extra_practice`: aspoň 20 dalších položek (choose / gap / order) pro aplikaci; asi 1/4 opakování B1, zbytek B1+–B2.
   - Opakovací týden: bez nové gramatiky, krátké B1 opakování + upevnění rozšiřujících struktur bloku.
   - Testový týden: test (B1 základ krátce, hlavně rozšiřující struktury a slovní zásoba, čtení, poslech, psaní)
     + projekt ve dvojici; klíč s bodováním.
3. `python3 tools/build_lesson.py lessons-src/lesson_XX.json out/LXX --voices <dir s hlasy piper>`
   (hlasy: github.com/rhasspy/piper/releases/download/v0.0.2/voice-en-us-ryan-medium.tar.gz a voice-en-us-amy-low.tar.gz)
4. Zkopíruj `Lekce_XX_*.pdf` → `data/lessons/LXX.pdf`, `Lekce_XX_poslech.mp3` → `data/lessons/LXX.mp3`.
5. Přidej nové téma do zásoby slov: `tools/bank-src/NN_tema.txt` (50 položek, `en|cz|příklad|úroveň`; úroveň B1+ / B2 / C1,
   přibližně 20 % B1+, 50 % B2, 30 % C1; žádná základní B1 slova), bez duplicit s existujícími balíčky; pak `python3 tools/build_bank.py`.
6. `python3 tools/export_app.py lessons-src/lesson_XX.json` (aktualizuje `data/index.json`).
7. Aktualizuj `course/stav_kurzu.json` (záznam lekce, `next_week`, `notes_for_next`).
8. Commit + push do `main`. Aplikace si nový obsah stáhne sama.
9. Nahraj PDF (lekce + klíč) a MP3 na Google Disk do složky Angličtina/Lekce (id 1W7BSnwSlH6eUexx1i1VhggBe4D1aKoQi).
