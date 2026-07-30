# IceSMP dokumentáció

## Aktuális integrált release

Az aktuális, bizonyítékokra épülő és továbbítható release-csomag kiindulópontja:

- [Release- és csapatkézikönyv](releases/ICESMP_RELEASE_AND_TEAM_GUIDE.md)
- [A futó szerverbuildhez képesti changelog](releases/DEPLOYED_BUILD_TO_RELEASE_CHANGELOG.md)
- [Teljes Git-fejlesztési changelog](releases/ICESMP_FULL_DEVELOPMENT_CHANGELOG.md)
- [Rövid csapatközlemény](releases/TEAM_RELEASE_SUMMARY.md)
- [Nyomtatható release pack](releases/ICESMP_RELEASE_PACK.md)
- [Külső pluginok státusza](releases/EXTERNAL_PLUGIN_STATUS.md)
- [Release acceptance checklist](releases/RELEASE_ACCEPTANCE_CHECKLIST.md)
- [Dokumentációs lefedettség](releases/RELEASE_DOCUMENTATION_COVERAGE.md)
- [Bizonyítékmátrix](releases/RELEASE_EVIDENCE_MATRIX.md)

## Szerepkör szerinti kézikönyvek

- [Admin- és moderátori kézikönyv](guides/ADMIN_AND_MODERATOR_GUIDE.md)
- [Builder- és world designer kézikönyv](guides/BUILDER_AND_WORLD_DESIGNER_GUIDE.md)
- [Játékoskézikönyv](player-guide/README.md)
- [Playtest kézikönyv](../PLAYTEST.md)

## Teljes referenciák

- [Funkciókatalógus](reference/FEATURE_CATALOGUE.md)
- [Parancsreferencia](reference/COMMAND_REFERENCE.md)
- [Permissionreferencia](reference/PERMISSION_REFERENCE.md)
- [Konfigurációs referencia](reference/CONFIGURATION_REFERENCE.md)
- [Üzenetkulcs-referencia](reference/MESSAGE_REFERENCE.md)
- [GUI-referencia](reference/GUI_REFERENCE.md)
- [Adatvezérelt tartalomkatalógus](reference/DATA_CONTENT_CATALOGUE.md)

## További technikai és világépítési dokumentumok

- [Architektúra](ARCHITECTURE.md)
- [Builder háttéranyag](EPITESZ_UTMUTATO.md)
- [Moderációs technikai háttér](MODERATION.md)
- [Crate technikai háttér](CRATES.md)
- [AFK scope-döntés](AFK_SCOPE_DECISION.md)
- [Sit-only review boundary](SIT_ONLY_REVIEW.md)
- [Szerverintegráció](SERVER_INTEGRATION.md)
- [Resource-pack referencia](RESOURCE_PACK_CMD.md)
- [Lore](LORE.md) és [lore–runtime megfeleltetés](LORE_REFERENCE.md)

## Autoritás és elavulás

A release állapotáról a `docs/releases/`, `docs/reference/` és `docs/guides/`
aktuális csomagja a mérvadó. A régebbi bemutató-, audit- és ötletdokumentumok
értékes háttéranyagot tartalmaznak, de bennük maradhat történeti darabszám,
branch-SHA vagy rollout-státusz. Eltérés esetén a rögzített release HEAD
forrásinventoryja, a deployed JAR binárisa és az új release-csomag az
autoritatív.
