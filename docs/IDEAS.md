# Ötlettár (IDEAS) — index

Fejlesztési ötletek gyűjtője — **nincs elköteleződés**, ez a brainstorm-réteg. Ami innen
zöld utat kap, az a [ROADMAP.md](../ROADMAP.md)-be kerül tervezett tételként; a technikai
adósság külön él a [ideas/O-refaktor.md](ideas/O-refaktor.md)-ben.

A tár **356 kidolgozott ötletet** tartalmaz 12 kategória-fájlban (`docs/ideas/`); ebből **49
lore-vezérelt tétel** a kódex ([LORE.md](LORE.md)) alapján az **L-válogatásba** került
át (a forrás-fájlban mutató maradt a helyükön).
Minden tétel azonos sablonnal készült: **Mi ez / Hogyan működne / Miért jó /
Építőkövek / Buktatók** — konkrét parancsokkal, config-kulcsokkal, a meglévő
managerekre/mintákra hivatkozva, Folia-megjegyzésekkel ahol kell.

Jelölés minden tételnél: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) •
`[TOP]` = ajánlott következő kör.

---

## Kategóriák

| Kategória | Fájl | Tételek | Miről szól |
|---|---|---|---|
| **A) Polish / meglévő átdolgozás** | [ideas/A-polish.md](ideas/A-polish.md) | A1–A72 | GUI/HUD/spell-QoL, kényelmi funkciók, hozzáférhetőség. **A1–A16 + A43–A62 + A69–A72 ✅ implementálva** (teszt: PLAYTEST.md); A17 = HP-rendszer átdolgozás (tulaj kérése, külön kör) |
| **B) Új mechanika** | [ideas/B-mechanika.md](ideas/B-mechanika.md) | B1–B62 | Progresszió-rendszerek, item-mechanikák, céhek, dungeonök, claim/territórium-mélyítés, kockázat/jutalom |
| **C) Admin / infra** | [ideas/C-infra.md](ideas/C-infra.md) | C1–C29 | Balansz-adat, moderáció, teljesítmény-riportok, üzemeltetés, fejlesztői eszközök |
| **D) Világ, hangulat, közösség** | [ideas/D-vilag.md](ideas/D-vilag.md) | D1–D26 | Immerzió, fővárosi élet, közösségi események, világ-narratíva, játékos-nyomhagyás |
| **E) Kaszt és specializáció** | [ideas/E-kaszt.md](ideas/E-kaszt.md) | E1–E32 | Mind a 13 kaszt ≥2 saját ötlettel: erőforrás-identitás, spec-szinergia, signature-pillanatok |
| **F) Gazdaság és kereskedelem** | [ideas/F-gazdasag.md](ideas/F-gazdasag.md) | F1–F26 | Árfolyam-mélyítés, adó/vám, szerződések, biztosítás, luxus-sinkek — mind a „nincs addolt pénz" elvre igazítva |
| **G) PvP, frakció-háború** | [ideas/G-pvp.md](ideas/G-pvp.md) | G1–G25 | Raid-variánsok, skirmish, rivalizálás, morál/felzárkóztatás, taktikai réteg — a bűn-rendszer viszonyával |
| **H) PvE, világesemények, végjáték** | [ideas/H-pve.md](ideas/H-pve.md) | H1–H25 | Új esemény-típusok, boss-mélyítés, mob-öko, ko-op tartalom, végjáték-hurok |
| **I) Szakmák, gyűjtögetés, készítés** | [ideas/I-szakmak.md](ideas/I-szakmak.md) | I1–I25 | Mestermű, érc-ér események, műhelyek, recept-láncok, szakma-presztízs |
| **J) Questek, story, progresszió** | [ideas/J-quest.md](ideas/J-quest.md) | J1–J24 | Új objektíva-típusok, story-eszközök (döntés-flagek, fejezetek), NPC-emlékezet, quest-admin |
| **K) Lore-integráció / világ-tartalom** | [ideas/K-lore.md](ideas/K-lore.md) | K1–K10 → **L-be költözött** | A [LORE.md](LORE.md) beépítési terve — a teljes kategória az L-válogatás „A kódex beépítési terve" szakaszában él |
| **L) Lore-kiemelt válogatás** | [ideas/L-lore-kiemelt.md](ideas/L-lore-kiemelt.md) | 49 tétel | A lore-vezérelt tartalmi ütemterv: a kódex beépítési terve (K1–K10) + a legerősebben illeszkedő ötletek (S: 6 • A: 27 • B/határeset: 6), lore-horgonnyal |
| **M) Bootstrap / Loader** | [ideas/M-bootstrap.md](ideas/M-bootstrap.md) | 10 tétel | A Paper bootstrap/loader-szint kiaknázása: rúna-tekercsek, zóna-damage-type-ok, átok-enchant, RP-függő registry-ötletek (banner/festmény/zene/trim) |

---

## Ajánlott következő kör (érték/munka arány szerint)

*(Az A1–A16 kör ✅ kész — az ajánlás a maradékra frissítve.)*

1. **B1 Heti Királyi Megbízások** — továbbra is a legjobb érték/munka arányú új tartalom.
2. **C1 Spell-statisztika + C8 edzőbábu** — a 2. balansz-kör adatból és mérésből menjen.
3. **B30 Háború-ablakok** (+ **G24 raid-cooldown**) — kis munka, a védők életminőségének
   legnagyobb dobása.
4. **A18 Spell-loadoutok + A22 ranglista-bővítés** — két gyors győzelem a friss A-infrán.
5. **C5 Discord-híd + B25 heti lottó** — közösségi zaj, retention.
6. **I1 Mestermű-esély + F12 kozmetika-árverés** — a szakma-gazdaság első lépcsője
   luxus-sinkkel párban.
7. **J1–J5 új objektíva-típusok** — a quest-rendszer kifejezőereje minden későbbi
   story-tartalom előfeltétele.
8. **A17 HP-rendszer átdolgozás** — a nagy kör, külön ágon, teljes playtesttel (B26 rúnák,
   B3 dungeonök és a G-kategória mély PvP-tételei UTÁNA érdemesek, az új HP-skálára hangolva).
