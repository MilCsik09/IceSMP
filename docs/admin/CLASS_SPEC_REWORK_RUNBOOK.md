# Kaszt- és specializáció-rework — üzemeltetői runbook

Ez a kiegészítő runbook nem helyettesíti a `docs/ADMIN_GUIDE.md` kézikönyvet.

## Bármely rework flag engedélyezése előtt

1. Készüljön visszaállítható mentés a világról, játékosadatokról, pluginadatról és resource packről.
2. Pontosan a `class-spec-dependencies.lock.yml` fájlban rögzített verziók legyenek telepítve.
3. A staging szerver induljon `-Dpaper.disablePluginRemapping=true` kapcsolóval.
4. A `class-spec-rework.enabled` maradjon `false`, amíg a dependency preflight nem teljesen tiszta.
5. Először belső tesztelői cohorton fusson a class/spec flag, csak ezután globálisan.

## Hibaviselkedés

- Kötelező dependency eltérés enforcement mellett: az indulás még a gameplay state betöltése előtt
  leáll.
- Opcionális integráció hiánya: a kapcsolódó vizuális kiegészítés nem érhető el; gameplay-szabály nem
  változhat csendben.
- Későbbi korrupt class-profile: az érintett profil quarantine-ba kerül, a nyers payload megmarad,
  és review előtt nincs item-grant vagy -revoke.

## Rollback

Kapcsold ki a rework flageket, állítsd vissza az előző resource packet, majd indítsd újra az előző
verziózárt pluginbundle-lel. Újabb Minecraft adatverzióra frissített világot tilos régebbi szerverre
visszaengedni. A legacy profilmirrort a zárt béta és a sikeres rollbackpróba végéig meg kell tartani.
