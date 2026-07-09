# CLAUDE.md

## Munkaszervezés (a repo tulajdonosának kérése)
- A feladatokat alapértelmezésben **delegáld gyengébb/olcsóbb subagenteknek** (Agent tool): `haiku` — keresés, összegzés, mechanikus szerkesztés; `sonnet` — kód-review, körülhatárolt implementáció.
- Ügyelj rá, hogy a feladat **ne haladja meg a delegált agent tudását**; ami tényleg a legerősebb modellt igényli (architektúra-döntés, Folia-konkurrencia, kényes refaktor), azt tartsd magadnál, ahogy a subagent-eredmények ellenőrzését és integrálását is.
- **Légy token-takarékos**: tömör válaszok, célzott fájlolvasás, ne duplikáld a subagent munkáját.

A projekt-konvenciók az `AGENTS.md`-ben.
