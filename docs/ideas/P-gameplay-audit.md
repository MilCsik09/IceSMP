# P — Teljes gameplay-audit (2026-07-20, 4 szempont-mélyvizsgálat)

> Szempontok: játékos-életút • rendszer-szinergiák • frakció-élmény • tartalom-ütem/létszám.
> A számszerű állítások config-ellenőrzöttek. Státusz-jelölés: 🔴 strukturális, 🟡 érdemi,
> 🟢 polish. A javítás tulaj-döntésre vár, kivéve ahol [GYORS] — az kérésre azonnal mehet.

## 🔴 Strukturális (a 4 jelentés egybehangzó fő leletei)

1. **Kollektív célok népesség-vakok.** 8 fős szerveren: WIN_RAID×20 cél ≈ 10+ hét (a
   szezonnál hosszabb); dark_undead_purge 3000 zombi ~2 DARK játékosnak = 15-30 nap;
   szerver-kihívás 500 kill/30 perc 2 online mellett lehetetlen; server_hunt 5000 ≈ 12 nap.
   → [GYORS] célszámok skálázása online/taglétszámmal (a config már ismeri őket), vagy
   kézi újrahangolás kis szerverre. ✅ KÉSZ: szerver-kihívás per-player skálázás
   (server-challenge.per-player-targets) + community/heti célszámok újrahangolva.
2. **A RED↔BLUE hétköznapi háború önsorsrontó.** Nyílt-világi ölés +1 bűn, 4 bűn =
   száműzetés → a hadviselő frakciók aktív játékosait a bűn-rendszer a DARK-ba darálja;
   legális kivezetés csak a király-kapus raid (reálisan ritka) + 2 párbaj/hét.
   → hadi-zóna/háborús ablak, ahol a RED↔BLUE ölés bűn-mentes; VAGY király-nélküli
   mini-raid fallback.
3. **Suttogónak lenni szigorúan rosszabb, mint nem lenni.** Egyetlen kód-oldali előny a
   /suttogas csatorna + az új cult-relief; az ár 6 HP + lelepleződéskor 4 bűn + vérdíj.
   A guide ígérte "sötét-mágiájú tárgyak" nem léteznek. → Suttogó-exkluzív jutalom
   (feketepiac-kedvezmény, cult-loot részesedés, spy-súly bónusz).
4. **Figyelem-orchestráció hiányzik.** 9 független világesemény (együttes "épp aktív"
   valószínűség ~0.7) + ~30 broadcast-forrás (8-12 broadcast/óra) — nincs prioritás-jel,
   nincs throttle. → közös "egy nagy PvE-esemény egyszerre" kapu + a személyes-léptékű
   események (régészet, titkos hely) action-barra/local-chatre.
   ✅ RÉSZBEN: MajorEventGate — a nagy események (world-boss/invasion/wild-hunt/
   escort/cultists) természetes sorsolása kapuzott (orchestration.enabled).
   A broadcast-diéta (személyes-léptékű események action-barra) még nyitott.
5. **A szezon-ív elöl-terhelt.** Minden rejtvény/story/fejezet-tartalom az első ~2 hétben
   elfogy; a 10-53. napnak nincs dátum-kapus tartalma; a 2+ szezonnak NULLA fejezet-questje
   (csak chapter: 1 létezik). 46-50-es kaszt-szint: semmi nem oldódik fel (~236 kill üres
   grind). → fejezet 2-3 megírása, szezon-közepi esemény, level-50 capstone.

## 🟡 Érdemi

6. Onboarding-lánc zsákutca: az onboarding_gather-nek nincs next-je → a kaszt-próbát
   magától kell megtalálni. [GYORS] ✅ KÉSZ: onboarding_utmutatas (Hírnök-quest) beláncolva. • Csak 4/13 kaszt kapott teljes mentor-láncot.
7. A kaszt az egyetlen admin-jegyes, visszafordíthatatlan döntés — és a legkevesebb
   infóval (a szerep-címke csak a spec-oldalon látszik). → self-service kaszt-váltás a
   frakció-minta szerint (díj+cooldown) + szerep-címke a választó GUI-ban.
   [GYORS a címke] ✅ KÉSZ: szerep-címke a JobGUI-ban (a self-service váltás még nyitott).
8. Szezon-bajnok kifizetés csak online tagoknak — az offline-pending minta (profession-
   weekly) létezik, a zászlóshajó-jutalom nem használja. [GYORS] ✅ KÉSZ
   (pending-champion-spoils a season.yml-ben, belépéskor jár).
9. HolidayService override() halott hook (0 hívó) — az ünnepeknek nincs mechanikai
   hatása. [GYORS: vérhold/invázió ünnep-skin bekötése] ✅ KÉSZ: blood-moon-chance-mult /
   invasion-chance-mult ünnep-kulcsok élnek (Rém-éj: ×2 / ×1.5).
10. NEUTRAL: király-rétegből kizárva + adó-mentes = félhalott kassza, mégis oda folyna
    a szezon-jutalom; a raid.neutral súly halott config. → tanács/vén-mechanika.
11. DARK-belépés newbie-trap (ingyenes, prompt nélkül, "örök"); Mortengrad (a scope:
    capital feltételezte főváros) még nem létezik → addig scope: all ajánlott.
    [GYORS] ✅ KÉSZ: scope: all + kétlépcsős DARK-belépés (join-confirm-seconds).
12. Nagy szerveren: kincs/vad hajsza/rontás-mag "első kattintó visz mindent" —
    monopolizálható; raid 10v10 plafon kispadoztat. → personal-loot minta kiterjesztése.
13. Szakma-heti célok: a craft-szakmáknak (armorer 333 smith/hét, cook 1000 étel) 3-5×
    nehezebb egység-áron, mint a gyűjtőknek. [GYORS: célszám-hangolás] ✅ KÉSZ
    (armorer 2500 / cook 1500 / enchanter 1500 / alchemist 1800).
14. Rontás-loot gyengébb, mint a kincsláda, pedig nagyobb munka. [GYORS] ✅ KÉSZ
    (rontás-loot feljavítva). • Csoportos tartalom kasszába fizet (hígul), a szóló-
    események zsebbe — a racionális játék a szóló-farm. → world-boss személyes
    bónusz-tétel. ✅ KÉSZ (world-events.world-boss.killer-loot).

## 🟢 Polish / doc

15. 17 riddle-quest él, a doc 16-ot mond. [GYORS] ✅ KÉSZ (doc javítva 17-re).
    • Dungeon-rendszer kapu-kód létezik, de nincs player-guide oldala.
    • Kalauz-ellentmondás: factions.yml komment vs. kód a DARK-váltás díjáról.
      ✅ KÉSZ (komment a kódhoz igazítva: a Sötétbe lépés mindig ingyenes).
    • Napi questek sosem rotálódnak (a rotation-group mechanizmus kész). [GYORS]
      ✅ KÉSZ ("napi-npc" csoport, naponta 3 a frakció-független ismételhetőkből).
    • Karaván: unique-anyag slot-garancia a rotációba. [GYORS] ✅ KÉSZ
      (caravan.rotation.guarantee-unique).

## Ami kifejezetten jól áll (a jelentések szerint)

- Mid-game horgok: rejtvények + napi/heti + bestiárium + közösségi célok — valódi
  havi-szintű húzás; a finálé-hét (B33+G16+F15+szezonboss) jól megkomponált csúcspont.
- Spec-respec (100) és frakció-váltás (500+72h+kapu) gazdasága mintaszerű.
- Valuta-folyamok tiszták: Csontveret/Emlékszilánk/Rúnapor forrás-nyelő párjai rendben.
- A DARK "törvényenkívüli karrier" a legjobban megtervezett frakció-út — csak a fővárosa
  hiányzik még.
- Az "elszigetelt sziget" gyanú többnyire alaptalan: bestiárium/régészet/pet/parkour mind
  bekötött; tisztán kozmetikai csak a városi őrség (szándékos) és a holiday-hook (halott).
