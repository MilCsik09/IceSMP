> Current scope authority: [WorldWeaver 1.0](WORLD-WEAVER-1.0-SCOPE.md). Universal completeness and deferred-domain blockers below are historical, superseded requirements.

# IceSMP — Világszövő
## V2 — Teljes, normatív és implementációra kész műszaki design
### Developer-Only Universal Runtime Manipulation Artifact / WorldWeaver Foundation

**Projekt:** IceSMP
**Célplatform:** Folia / Paper API 1.21.11, Java 21
**Implementációs alap:** PR #152 — `feature/trash-production-hardening`
**Ellenőrzött referencia head:** `a335b3b5acaea66772534e51527a1c9233a85d1a`
**Dokumentum státusza:** **NORMATÍV / IMPLEMENTÁCIÓS SOURCE OF TRUTH**
**Verzió:** v2
**Előzmény:** az eredeti teljes Világszövő implementációs terv + az azt követően lezárt univerzális manipulációs/product követelmények összeolvasztott változata.

---

# 0. A kész rendszer egy mondatban

A **Világszövő** egy rejtett, kizárólag a `HiddenDevAuthority.PRIMARY_DEVELOPER` által használható in-game developer artifact, amely a teljes IceSMP **játék szempontjából értelmes, publikált runtime- és canonical felületét** typed providereken keresztül képes inspectelni, manipulálni, létrehozni, eltávolítani, ideiglenesen rávetíteni, tartósan módosítani és — ahol a domain authority ezt biztonságosan támogatja — más rendszer capabilityjeivel komponálni.

A Világszövő:

- **nem** fix képességlistás admin item;
- **nem** command wrapper;
- **nem** raw NBT/PDC editor;
- **nem** reflection debugger;
- **nem** általános scriptnyelv;
- **nem** második gameplay authority;
- **nem** új combat/event/faction/itemization/quest framework.

A Világszövő valódi ereje:

> **nem a tárgyban van, hanem a WorldWeaver Protocolban.**

Az artifact csak egy in-game frontend.

Az aktuális IceSMP build domainjei saját `WorldWeaverProvider` adaptereken keresztül publikálják, hogy az adott buildben mi manipulálható.

---

# 0.1 A „BÁRMIT” normatív definíciója

A projekt product requirementje:

> **A primary developer a Világszövőn keresztül minden gameplay-releváns IceSMP állapotot vagy műveletet elérhessen, amelyhez biztonságos és determinisztikus domain route definiálható.**

Ez öt capability-osztályt jelent:

1. **INSPECT** — mi ez, mi az aktuális canonical/effective state;
2. **MANIPULATE** — meglévő runtime/canonical state módosítása;
3. **CREATE** — domain object, runtime instance, projection, prototype vagy event létrehozása;
4. **REMOVE / SEVER** — létrehozott/megjelölt state eltávolítása vagy projection elvágása;
5. **COMPOSE** — typed export/import, Imprint, bounded Binding és provider-owned Fork segítségével különböző rendszerek capabilityjeinek kombinálása.

A „bármit” **nem** jelenti azt, hogy arbitrary Java private field, heap object vagy method reflectionnel elérhető.

A végleges technikai definíció:

> **Minden gameplay-releváns capability elérhető, amelyet Minecraft vagy egy IceSMP subsystem explicit WorldWeaver contracton keresztül publikál.**

---

# 0.2 Capability completeness — kötelező lefedettségi szabály

A WorldWeaver nem tekinthető „univerzálisnak” pusztán attól, hogy plugin architecture-je van.

Release előtt **coverage closure** szükséges.

Minden jelenlegi nagy IceSMP domainhez az alábbi egyik állapot kötelező:

- `FULL_PROVIDER` — inspect + minden értelmes developer manipulation;
- `INSPECT_ONLY_BY_DESIGN` — kizárólag akkor elfogadott, ha konkrét, dokumentált domain invariant miatt mutation nem implementálható biztonságosan;
- `NO_RUNTIME_SURFACE` — a domainnek nincs értelmes runtime developer surface-e;
- `DEFERRED_BLOCKER` — release blocker; production enable tilos.

A provider contract része:

```java
public record ProviderCoverage(
        String domainId,
        CoverageLevel level,
        Set<String> coveredAuthorities,
        Set<String> intentionallyExcludedSurfaces,
        String exclusionReason
) { }

public enum CoverageLevel {
    FULL_PROVIDER,
    INSPECT_ONLY_BY_DESIGN,
    NO_RUNTIME_SURFACE,
    DEFERRED_BLOCKER
}
```

A `WorldWeaverCoverageRegressionSuite` fail-closed megbukik, ha:

- kötelező current domainhez nincs provider/coverage rekord;
- `DEFERRED_BLOCKER` marad;
- `INSPECT_ONLY_BY_DESIGN` üres indokkal szerepel;
- új canonical subsystem kerül a bootstrapba WorldWeaver coverage-döntés nélkül.

Ez **nem artifact-code coupling**.

Új subsystem esetén a subsystem saját provider/coverage adaptert ad.

Az artifact, kernel és generikus GUI ettől változatlan marad.

---

# 0.3 Dinamikus fejlődés

## Új content

Ha a provider már létezik és canonical registryből dolgozik:

> **0 WorldWeaver core/artifact módosítás.**

Példák:

- új mob ability;
- új mob template;
- új rune;
- új Trash identity;
- új event adapter content;
- későbbi biome identity;
- későbbi Doom manifestation.

A catalog a canonical registryt enumerálja, ezért az új elem automatikusan megjelenik.

## Új subsystem

Egy új subsystem egyszer csatlakozik:

```text
NewSystem
  -> NewSystemWeaverProvider
  -> WorldWeaverProviderRegistry
```

Ezután a Világszövő automatikusan rendereli a faceteket, actionöket, catalogokat, export/import capabilityket.

Hard requirement:

> **új subsystem miatt a WorldWeaver artifact listeneréhez, item behaviorjához vagy generikus GUI rendereréhez tilos domain-specifikus kódot hozzáadni.**

---

# 1. Normatív nyelv és lezárt döntések

A **kell**, **tilos**, **csak**, **mindig** implementációs követelmény.

| Kérdés | Lezárt döntés |
|---|---|
| Ki használhatja? | Csak `HiddenDevAuthority.PRIMARY_DEVELOPER`. |
| Admin/OP használhatja? | Nem. OP és permission önmagában nem authority. |
| Console használhatja? | Csak recovery/diagnostics API-t; artifact GUI-t és provider mutationt nem. |
| Új DEV-item framework készül? | Nem. A meglévő DEV item lifecycle lesz generikus. |
| Mi a default integritási mód? | `SANDBOX`. |
| Lehet valós GM/canonical módosítás? | Igen, `LIVE_GM` + megfelelő arming mellett. |
| A `CANON` scope? | Nem. Risk, lifetime és integrity mode külön tengely. |
| Projection módosít canonical PDC-t? | Nem; explicit effective-state consumer kell. |
| Force undo van? | Általános force undo nincs. Konfliktusnál fail closed. |
| Thread Java-object? | Nem. Typed, schema-versioned, YAML-safe value. |
| Cross-region live read? | Tilos. Snapshot owner threaden készül. |
| Persistent entity startupkor hiányzik? | Pending marad loadig; nem orphanolható automatikusan. |
| Permanent HUD? | Nincs. |
| Public docs? | Nincs WorldWeaver/Trash developer detail. |
| Új contenthez artifact update kell? | Nem. |
| Új subsystemhez artifact update kell? | Nem; provider kell. |
| Domain authority megkerülhető raw PDC/YAML editként? | Nem. |
| Minden gameplay domainnek coverage döntés kell? | Igen, release gate. |

---

# 2. Szállítási scope

## 2.1 Kötelező végállapot

A production-ready rendszer képes:

- `PLAYER`, `ENTITY`, `ITEM_SLOT`, `BLOCK`, `LOCATION`, `AREA`, `WORLD` subjectet kezelni;
- provider-driven inspectiont és action discoveryt végezni;
- canonical registrykből dinamikus typed catalogokat publikálni;
- typed Thread export/importot kezelni;
- one-shot, session és restart-túlélő projectiont kezelni;
- `SANDBOX` developer influence-t jutalom/progresszió quarantine-nal kezelni;
- `LIVE_GM` műveletet külön arminggal kezelni;
- journalizált mutationt, crash recoveryt és conditional Undo-t kezelni;
- bounded AREA actiont kezelni;
- bounded Trigger Bindingot kezelni;
- provider-owned Runtime Forkot kezelni;
- Imprint/Impress capabilityt kezelni;
- provider circuit breakert és rate limitet kezelni;
- full coverage auditot teljesíteni.

Kötelező elsődleges providerek:

1. `MinecraftWeaverProvider`
2. `PvEWeaverProvider`
3. `FactionWeaverProvider`
4. `TrashWeaverProvider`
5. `TerritoryWeaverProvider`
6. `EventWeaverProvider`
7. `ItemizationWeaverProvider`
8. `DeveloperSelfWeaverProvider`
9. `KnowledgeWeaverProvider`
10. a coverage audit által feltárt további current-domain provider/adapters.

## 2.2 Kifejezetten nem készül

- reflection field editor;
- arbitrary method caller;
- raw NBT/PDC editor;
- JavaScript/Lua;
- általános expression VM;
- unrestricted loop;
- unrestricted recursion;
- offline inventory hacking;
- rollback nélküli canonical tömegművelet;
- második domain authority;
- rejtett subsystem publikus dokumentálása.

---

# 3. Feature flag és release epoch

A teljes rendszer production acceptance előtt:

```java
private static final boolean WORLD_WEAVER_ENABLED = false;
```

Nem operator config.

Az első production `true` boot:

```text
release-epoch = 1
```

A state store-ban atomikusan mentendő.

Ha későbbi build:

```text
WORLD_WEAVER_ENABLED == false
release-epoch > 0
```

akkor fail closed:

```text
WORLD_WEAVER_ROLLBACK_UNSAFE
```

Indok:

a monotonic developer influence / reward quarantine state miatt olyan rollback nem engedhető, amely a gate-et eltávolítja, miközben tainted entity/state még létezhet.

---

# 4. Authority és támadási felület

## 4.1 Authority source

Az egyetlen player authority:

```java
HiddenDevAuthority.isDeveloper(player.getUniqueId())
```

A WorldWeaver saját UUID konstansot nem definiál.

## 4.2 Console

Csak:

```java
CompletionStage<RecoveryResult> recoverArtifact(CommandSender console);
WorldWeaverDiagnostics diagnostics(CommandSender console);
```

Console nem:

- nyit GUI-t;
- hoz létre Threadet;
- futtat provider mutationt;
- armol LIVE_GM-et.

## 4.3 Defense in depth

Minden mutation előtt:

1. actor online `Player`;
2. developer authority igaz;
3. main handben authoritative WorldWeaver;
4. artifact ID/owner/instance megegyezik durable state-tel;
5. GUI/session nonce aktuális;
6. subject snapshot revision aktuális;
7. action descriptor + parameter validation;
8. integrity/risk arming valid.

Forged item:

- interaction cancel;
- forged instance cleanup;
- `AUTHORITY_REJECTED` audit.

## 4.4 Authority token

```java
public final class WeaverAuthorityToken {
    WeaverAuthorityToken() {}
}
```

Példányt csak `WorldWeaverKernel` hoz létre.

Provider mutation publikus authority nélküli helperrel tilos.

---

# 5. Generikus DEV artifact lifecycle

A meglévő `DevItemManager` / `DevItemFactory` bővítendő.

Nem készül WorldWeaver-specifikus második singleton framework.

## 5.1 Contractok

```java
public record DevArtifactDefinition(
        String id,
        DevArtifactOwnerPolicy ownerPolicy,
        DevArtifactPresentationSource presentationSource,
        DevArtifactPolicySource policySource
) {}

public record DevArtifactRegistration(
        DevArtifactDefinition definition,
        DevArtifactBehavior behavior
) {}

public sealed interface DevArtifactOwnerPolicy
        permits FixedArtifactOwner, ConfiguredArtifactOwner {}

public record FixedArtifactOwner(UUID owner)
        implements DevArtifactOwnerPolicy {}

public record ConfiguredArtifactOwner(
        String configPath,
        UUID fallback
) implements DevArtifactOwnerPolicy {}

public interface DevArtifactBehavior {
    String artifactId();
    void onIssued(DevArtifactContext context);
    void onRecovered(DevArtifactContext context);
    ArtifactInteractionResult onInteract(DevArtifactInteraction interaction);
    void tick(DevArtifactContext context, long nowMillis);
    Map<String, Object> saveBehaviorState();
    void loadBehaviorState(Map<String, Object> state);
    void shutdown();
}
```

Implementációk:

- `BingulusRewardBehavior`
- `WorldWeaverArtifactBehavior`

## 5.2 Artifact definíció

```text
id: dev_world_weaver
owner: HiddenDevAuthority.PRIMARY_DEVELOPER
material: ECHO_SHARD
autoRestore: true
model states:
  IDLE
  SUBJECT_LOCKED
  THREAD_HELD
  CANON_ARMED
```

A WorldWeaver definition code-defined, nem operator config.

## 5.3 `dev-items-state.yml` schema 2

```yaml
schema-version: 2
artifacts:
  csodalatos_bingulus:
    issued: true
    owner: "..."
    instance: "..."
    behavior-state: {}
  dev_world_weaver:
    issued: true
    owner: "<PRIMARY_DEVELOPER>"
    instance: "..."
    behavior-state: {}
```

A Bingulus meglévő pending/pity/progress state-je migration során veszteség nélkül marad.

---

# 6. Artifact UX és vizuál

## 6.1 Art direction

Nem klasszikus varázspálca.

Javasolt:

- sötét echo/amethyst mag;
- 2–3 lebegő/törött gyűrű;
- obsidian/echo/amethyst nyelv;
- aszimmetrikus silhouette;
- finom emissive repedések.

## 6.2 Model state

1. `IDLE`
2. `SUBJECT_LOCKED`
3. `THREAD_HELD`
4. `CANON_ARMED`

Prioritás:

```text
CANON_ARMED > THREAD_HELD > SUBJECT_LOCKED > IDLE
```

## 6.3 Input mapping

| Input | Eredmény |
|---|---|
| Jobb klikk entityn | direct entity subject + Subject GUI |
| Jobb klikk blockon | block subject + Subject GUI |
| Jobb klikk levegőbe, offhand itemmel | `ITEM_SLOT` subject |
| Jobb klikk levegőbe, üres offhanddel | saját `PLAYER` subject |
| Shift + jobb klikk | Quick Apply aktív Threadből |
| F / swap | Thread Case / Recent |
| Shift + F | last subject újranyitása |

Az event mindig cancel-elt, artifact nem kerül offhandbe.

## 6.4 Feedback

Nincs permanent HUD.

Használható:

- inventory GUI;
- ActionBar;
- rövid title;
- sound;
- particle.

---

# 7. GUI session safety

```java
public record WorldWeaverHolder(
        UUID sessionId,
        long viewRevision,
        WeaverViewKind viewKind
) implements InventoryHolder {}
```

Minden click:

- cancel;
- authority check;
- artifact check;
- session ID check;
- view revision check;
- stale view → refresh, nincs action.

MUTATING+ action friss snapshotot kér confirmation előtt.

---

# 8. Subject modell

## 8.1 Kindok

```java
public enum WeaverSubjectKind {
    PLAYER,
    ENTITY,
    ITEM_SLOT,
    BLOCK,
    LOCATION,
    AREA,
    WORLD
}
```

## 8.2 Stabil refek

```java
public sealed interface SubjectRef permits
        PlayerRef, EntityRef, ItemSlotRef,
        BlockRef, LocationRef, AreaRef, WorldRef {
    WeaverSubjectKind kind();
    String stableKey();
}
```

Példák:

```java
public record PlayerRef(UUID playerId) implements SubjectRef {}
public record EntityRef(UUID entityId) implements SubjectRef {}
public record WorldRef(UUID worldId) implements SubjectRef {}
public record BlockRef(UUID worldId, int x, int y, int z) implements SubjectRef {}
public record LocationRef(
        UUID worldId,
        double x, double y, double z,
        float yaw, float pitch
) implements SubjectRef {}
```

## 8.3 `ITEM_SLOT`

Mutable `ItemStack` nem stabil subject.

```java
public record ItemSlotRef(
        UUID holderId,
        WeaverSlot slot,
        Optional<String> logicalId,
        Optional<UUID> instanceId,
        OptionalLong revision,
        String fingerprint
) implements SubjectRef {}
```

Managed item mutation előtt:

- slot;
- logical ID;
- instance UUID;
- revision;
- fingerprint

újraellenőrzendő.

## 8.4 Snapshot

```java
public record SubjectSnapshot(
        SubjectRef ref,
        long capturedAt,
        String revisionFingerprint,
        Map<String, WeaverValue> facts
) {}
```

Snapshot:

- owner threaden készül;
- immutable;
- nem tartalmaz live Bukkit objectet.

A GUI/provider discovery snapshoton dolgozik.

---

# 9. AREA modell

Supported:

```java
RadiusArea
CuboidArea
TerritoryArea
CylinderArea
PolygonPrismArea
```

Hard limits:

- radius max 16;
- cuboid max 32×32×32;
- max 4096 block;
- max 128 entity;
- max 9 chunk;
- max 9 region fanout;
- territory polygon max 256 vertex.

Unloaded chunkot bulk action nem force-loadol.

AREA collection:

1. pure chunk calculation;
2. per-chunk region task;
3. immutable refs;
4. entity mutation saját scheduleren;
5. max 16 concurrent entity subtask;
6. partial failure → child compensation.

Canonical/destructive AREA action nincs.

---

# 10. Typed value rendszer

## 10.1 Type ID

```java
public record WeaverTypeId(
        String namespace,
        String path,
        int schemaVersion
) {
    public String canonical() {
        return namespace + ":" + path + "@" + schemaVersion;
    }
}
```

Példák:

```text
weaver:boolean@1
weaver:int@1
weaver:double@1
weaver:duration_ticks@1
weaver:uuid@1
weaver:location@1
weaver:area@1
weaver:subject_ref@1
weaver:projection_ref@1

minecraft:material@1
minecraft:entity_type@1
minecraft:gamemode@1

icesmp:pve_ability_ref@1
icesmp:pve_rank@1
icesmp:pve_archetype_ref@1
icesmp:mob_template_ref@1

icesmp:faction@1
icesmp:faction_context@1

icesmp:trash_identity_ref@1
icesmp:trash_rule_ref@1
icesmp:trash_rule_field_ref@1

icesmp:territory_ref@1
icesmp:territory_type@1
icesmp:territory_rule_set@1
icesmp:event_ref@1

icesmp:item_template_ref@1
icesmp:item_roll_profile_ref@1
icesmp:rune_ref@1
```

## 10.2 Value

```java
public record WeaverValue(
        WeaverTypeId type,
        Map<String, Object> payload,
        String sourceProvider,
        String sourceFacet,
        Set<String> sourceCapabilities,
        long capturedAt
) {}
```

Payload:

- YAML-safe primitive/list/map;
- nincs Bukkit object;
- nincs Java serialization;
- nincs arbitrary class name.

## 10.3 Type codec

```java
public interface WeaverTypeCodec {
    WeaverTypeId type();
    ValidationResult validate(Map<String, Object> payload);
    byte[] canonicalBytes(Map<String, Object> payload);
}
```

Unknown schema/type → reject.

---

# 11. Catalog — a dinamikus fejlődés alapja

```java
public interface WeaverValueCatalog {
    WeaverTypeId type();
    CatalogPage page(CatalogQuery query);
    Optional<WeaverValue> resolve(String stableId);
}
```

Catalog:

- max 45 entry/page;
- deterministic stable ID order;
- registry-backed;
- runtime discovery.

Kötelező példák:

| Catalog | Source |
|---|---|
| `pve.ability` | `MobAbilityRegistry.all()` |
| `pve.template` | MobTemplate registry |
| `pve.rank` | canonical `MobRank` |
| `faction.faction` | canonical `FactionType` |
| `trash.identity` | hidden Trash catalog |
| `trash.rule` | rule vocabulary |
| `event.event` | event adapter registry |
| `itemization.template` | ItemTemplate registry |
| `itemization.rune` | rune registry |

Új content esetén a catalog automatikusan bővül.

---

# 12. Thread

## 12.1 Export

Provider explicit `ExportDescriptor`-t publikál.

```java
public record ExportDescriptor(
        String id,
        String facetId,
        WeaverTypeId outputType,
        Set<String> capabilities
) {}
```

## 12.2 Import

```java
public record ImportDescriptor(
        String id,
        String actionId,
        WeaverTypeId acceptedType,
        Set<String> requiredCapabilities,
        String parameterId
) {}
```

Kompatibilis, ha:

1. type namespace/path/schema pontosan egyezik;
2. export capabilities tartalmazza az importer required capability halmazát;
3. provider domain validation sikeres.

## 12.3 Thread Case

Sessionben maximum **12 Thread**.

Thread restartot nem él túl.

Quick Apply:

- 0 importer → incompatibility;
- 1 complete importer → preview/confirmation;
- 1 incomplete → parameter GUI;
- több → choice GUI;
- DESTRUCTIVE/CANONICAL soha nem auto-execute.

---

# 13. Provider contract

```java
public interface WorldWeaverProvider {
    String id();
    int contractVersion();
    Set<WeaverSubjectKind> supportedKinds();
    ProviderContribution contribution();
    ProviderCoverage coverage();

    ProviderDiscovery discover(SubjectSnapshot snapshot);

    InspectionResult inspect(
            ProviderContext context,
            SubjectSnapshot snapshot,
            String facetId);

    PreparedAction prepare(
            ProviderContext context,
            SubjectSnapshot snapshot,
            ActionRequest request);

    PreparedAction prepareUndo(
            ProviderContext context,
            SubjectSnapshot snapshot,
            WeaverReceipt receipt);

    Optional<WeaverValueCatalog> catalog(
            ProviderContext context,
            SubjectSnapshot snapshot,
            String catalogId);

    ValueExportResult exportValue(
            ProviderContext context,
            SubjectSnapshot snapshot,
            String exportId);

    ImportValidation validateImport(
            ProviderContext context,
            SubjectSnapshot snapshot,
            String importId,
            WeaverValue value);

    RecoveryAssessment assessRecovery(
            ProviderContext context,
            SubjectSnapshot snapshot,
            WeaverOperationRecord operation);
}
```

## 13.1 Pure discovery

`discover()`:

- live Bukkit API-t nem hív;
- disk IO-t nem hív;
- mutációt nem végez;
- snapshot + immutable registry view alapján működik.

## 13.2 Contribution

```java
public record ProviderContribution(
        List<FacetDescriptor> facets,
        List<ActionDescriptor> actions,
        List<CatalogDescriptor> catalogs,
        List<ExportDescriptor> exports,
        List<ImportDescriptor> imports,
        Map<String, String> recoveryCapabilitiesByAction
) {}
```

ID:

```text
provider.action_name
```

Regex:

```text
[a-z0-9_.-]{3,96}
```

---

# 14. Action descriptor

```java
public record ActionDescriptor(
        String id,
        String facetId,
        Component label,
        RiskLevel risk,
        Set<Lifetime> lifetimes,
        Set<IntegrityMode> integrityModes,
        Set<IntegrityImpact> integrityImpacts,
        Set<WeaverSubjectKind> subjects,
        List<ActionParameter> parameters,
        AreaSupport areaSupport,
        Optional<AreaLimits> areaLimits,
        boolean undoable,
        Optional<String> irreversibleReason,
        int rateCost
) {}
```

```java
public enum RiskLevel {
    READ_ONLY,
    SAFE,
    MUTATING,
    DESTRUCTIVE,
    CANONICAL
}

public enum Lifetime {
    ONE_SHOT,
    SESSION,
    PERSISTENT
}

public enum IntegrityMode {
    SANDBOX,
    LIVE_GM
}

public enum IntegrityImpact {
    NONE,
    TAINT_SUBJECT,
    TAINT_CREATED,
    EVENT_ORIGIN
}
```

**CANONICAL nem lifetime.**

Canonical action:

```text
risk = CANONICAL
lifetime = ONE_SHOT
integrityModes = { LIVE_GM }
```

---

# 15. Registry validation

`freezeAndValidate()` fail closed, ha:

- duplikált provider/facet/action/catalog/import/export ID;
- unknown type;
- invalid cross-reference;
- CANONICAL action SANDBOX-kompatibilisnek van jelölve;
- PERSISTENT action nem undoable;
- DESTRUCTIVE/CANONICAL action irreversible reason nélkül nem undoable;
- AREA limit hiányzik;
- parameter bounds/text constraint hiányzik;
- importer parameter type mismatch;
- journal action recovery capability nélkül;
- provider contract version nem támogatott;
- coverage level `DEFERRED_BLOCKER`.

Runtime manifest violation circuit-breaker hibának számít.

---

# 16. Folia execution modell

## 16.1 Owner route

```java
public sealed interface ExecutionOwner permits
        ActorOwner,
        EntityOwner,
        RegionOwner,
        GlobalOwner,
        AsyncIoOwner,
        ProfileOwner {}
```

Routing:

| Művelet | Owner |
|---|---|
| actor GUI/inventory/actionbar | actor scheduler |
| player/entity live state | entity scheduler |
| block/location | region scheduler |
| world global state | global region scheduler |
| YAML IO | async serialized IO |
| PlayerProfile transaction | profile async authority |

`Bukkit.getScheduler()` tilos.

`.join()` / `.get()` region threaden tilos.

## 16.2 Execution plan

```java
public record PreparedAction(
        UUID operationId,
        ActionDescriptor descriptor,
        SubjectRef subject,
        String expectedBeforeFingerprint,
        List<ExecutionStage> stages,
        OperationRecoveryPayload recoveryPayload,
        ReceiptFactory receiptFactory
) {}
```

Max 8 stage.

Stage payload immutable.

Live Bukkit object stage-ek között nem vihető át.

Timeout:

- default 5 s;
- IO/Profile 10 s.

Compensation fordított sorrendben.

---

# 17. Mutation flow

1. authority;
2. artifact/session;
3. fresh owner-thread snapshot;
4. action/type/bounds/domain validation;
5. preview;
6. confirmation;
7. arming;
8. `PreparedAction`;
9. durable `PREPARED`, ha journal-köteles;
10. owner-routed stage execution;
11. `APPLIED`;
12. receipt/projection/influence/audit commit;
13. `COMMITTED`;
14. developer feedback.

Before fingerprint drift:

```text
STALE_SUBJECT
```

side effect nélkül.

---

# 18. Risk, confirmation és arming

| Risk | Preview | Confirmation | Arming |
|---|---|---|---|
| READ_ONLY | nem kell | nem kell | nem |
| SAFE | inline | 1 click | nem |
| MUTATING | before/after | confirm | LIVE_GM esetén igen |
| DESTRUCTIVE | before/after + veszteség | kétlépcsős | DESTRUCTIVE (+ LIVE_GM ha kell) |
| CANONICAL | canonical revision + before/after | kétlépcsős nonce | LIVE_GM + CANONICAL |

```java
public enum ArmingCapability {
    DESTRUCTIVE,
    LIVE_GM,
    CANONICAL
}
```

Arming:

- max 30 s;
- one execution attempt;
- success/failure után törlődik;
- logout/death/gui close/artifact recovery/session clear törli.

---

# 19. SANDBOX és LIVE_GM

## 19.1 SANDBOX

Default.

Cél:

> a developer szabadon kísérletezhessen anélkül, hogy a játékosgazdaság vagy progresszió hamis jutalmat kapna.

SANDBOX influence:

- taintolja a releváns subject/created object/eventet;
- reward/progression gate blokkol;
- auditálódik.

## 19.2 LIVE_GM

Explicit, armolt mód.

- reward/progression aktív maradhat;
- preview ezt egyértelműen jelzi;
- canonical mutation csak LIVE_GM;
- audit `LIVE_GM`.

Egy SANDBOX-origin effectet LIVE_GM action nem „moshat tisztára”.

---

# 20. DeveloperInfluence

```java
public record DeveloperInfluence(
        UUID operationId,
        IntegrityMode mode,
        String actionId,
        UUID actorId,
        long appliedAt
) {}
```

Scopes:

```java
public enum InfluenceScope {
    ENTITY,
    PLAYER,
    ITEM,
    EVENT,
    SPATIAL,
    WORLD
}
```

SANDBOX entity marker monotonic.

Player quarantine:

- active projection alatt;
- utána minimum 5 perc.

World one-shot:

- minimum 5 perc quarantine.

Persistent influence durable.

---

# 21. Reward/progression quarantine

Semleges domain contract:

```java
public interface RewardEligibilityPolicy {
    RewardDecision evaluate(RewardContext context);
}
```

Channels minimum:

```text
KILL_REWARD
TRACKING_PROGRESS
QUEST_PROGRESS
QUEST_REWARD
PROFESSION_XP
RARE_GATHERING
CLASS_XP
PET_XP
COMMUNITY_GOAL
WEEKLY_GOAL
SERVER_CHALLENGE
BESTIARY
ACHIEVEMENT
DISCOVERY
CURRENCY_FAUCET
ITEM_ACQUISITION
PERSONAL_LOOT
CRATE_REWARD
DEV_ITEM_REWARD
PARKOUR_CREDIT
RAID_CREDIT
WAR_CREDIT
EVENT_REWARD
```

Bármely `DENY` nyer.

Kötelező gate:

- kill flow;
- Quest non-kill progress;
- quest turn-in/reward;
- profession XP;
- gathering;
- class/pet XP;
- community/weekly/challenge;
- Bestiary/Achievement/discovery;
- currency faucet;
- item acquisition;
- personal loot;
- crates;
- event rewards;
- raid/war/parkour credit.

Sandbox-tainted mob:

- vanilla drops clear;
- exp 0;
- custom rewards deny.

---

# 22. DEV prototype item quarantine

`dev_prototype`:

- csak primary developer inventory;
- nem drop;
- nem trade;
- nem market;
- nem salvage;
- nem craft/anvil/smith input;
- nem smelt/brew/fuel;
- nem place;
- nem consume;
- nem equipment stat source;
- nem rune/set/signature source;
- nem progression source.

Más player/container esetén fail-closed quarantine cleanup.

Artifact maga:

```text
dev_item
```

nem `dev_prototype`.

---

# 23. Projection modell

```java
public record WeaverProjection(
        UUID projectionId,
        long sequence,
        String providerId,
        String actionId,
        SubjectRef subject,
        Lifetime lifetime,
        DeveloperInfluence influence,
        Map<String, WeaverValue> values,
        String canonicalFingerprintAtApply,
        long createdAt,
        OptionalLong expiresAt
) {}
```

Precedence:

- scalar → highest sequence;
- add/remove → capability-specific latest sequence;
- remove projection → recompute effective state;
- canonical state soha nem íródik vissza projection store-ból.

## 23.1 Explicit consumer contract

Projection csak akkor regisztrálható, ha meg van nevezve, mely runtime consumer olvassa.

Példa:

| Projection | Effective consumer | Canonical-only |
|---|---|---|
| PvE rank/ability | ability runtime/combat attribute layer | loot, Bestiary, authored reward |
| Faction context | passive/targeting resolver | tax, quest, treasury, history |
| Territory overlay | protection decision | Territory storage/index |
| Trash rule | rule field service | Trash identity/history |

„Store-ban van” önmagában nem implementáció.

---

# 24. Persistence és operation journal

Store-ok:

1. `world-weaver-state.yml`
2. `world-weaver-audit.yml`

`YamlStore.saveAtomic`.

IO egyetlen serialized coordinator queue.

## 24.1 Operation state

```java
public enum OperationStatus {
    PREPARED,
    APPLIED,
    COMMITTED,
    ABORTED,
    COMPENSATED,
    NEEDS_REVIEW
}
```

State machine:

```text
PREPARED -> APPLIED -> COMMITTED
PREPARED -> ABORTED
PREPARED -> NEEDS_REVIEW
APPLIED -> COMPENSATED
APPLIED -> NEEDS_REVIEW
```

Journal-köteles:

- minden MUTATING;
- DESTRUCTIVE;
- CANONICAL;
- PERSISTENT;
- bármely integrity impact != NONE.

## 24.2 Commit sorrend

1. durable APPLIED + receipt/projection/influence + pending audit;
2. audit idempotens write;
3. COMMITTED + pending audit clear.

Cross-file fake atomicity tilos.

---

# 25. Startup reconciliation

`load()` csak parse/validate.

`start()`:

1. registry freeze;
2. operation reconciliation;
3. world/location projection reapply;
4. player projection joinkor;
5. entity projection `EntitiesLoadEvent`-kor;
6. binding/fork runtime.

Unloaded entity:

```text
PENDING_ENTITY_LOAD
```

Nem force-load.

30 nap unresolved után:

```text
STALE_UNRESOLVED
```

de nincs auto-delete.

World missing:

```text
UNRESOLVED_WORLD
```

manual explicit cleanup szükséges.

---

# 26. Crash recovery

Assessment:

```java
public enum ObservedOperationState {
    BEFORE,
    APPLIED,
    PARTIAL_OR_CONFLICT
}
```

Rules:

- PREPARED + BEFORE → ABORTED;
- PREPARED + APPLIED → materialize receipt → COMMITTED;
- APPLIED + APPLIED → COMMITTED;
- APPLIED + exact compensated BEFORE → COMPENSATED;
- partial/unknown → NEEDS_REVIEW.

Restart reconciliation **nem ismétli meg vakon** a mutationt.

---

# 27. Receipt és conditional Undo

```java
public record WeaverReceipt(
        UUID receiptId,
        UUID operationId,
        String providerId,
        String actionId,
        SubjectRef subject,
        RiskLevel risk,
        Lifetime lifetime,
        IntegrityMode integrityMode,
        String beforeFingerprint,
        String afterFingerprint,
        Map<String, WeaverValue> before,
        Map<String, WeaverValue> after,
        Optional<UndoSpec> undo,
        long createdAt,
        ReceiptStatus status
) {}
```

Undo algoritmus:

1. receipt committed;
2. fresh subject snapshot;
3. current fingerprint == expected current fingerprint;
4. provider `prepareUndo`;
5. normal journal/execution;
6. receipt UNDONE.

Eltérés:

```text
CONFLICT
```

Nincs általános force undo.

Canonical historyt az Undo nem törli; logical compensation új domain history/revision event lehet.

---

# 28. Audit

```java
public enum AuditOutcome {
    COMMITTED,
    ABORTED,
    COMPENSATED,
    NEEDS_REVIEW,
    UNDONE,
    CONFLICT,
    AUTHORITY_REJECTED,
    VALIDATION_REJECTED,
    PROVIDER_ERROR,
    ACKNOWLEDGED
}
```

Max 10 000 entry, majd oldest-first rotáció.

Nem auditál:

- full item bytes;
- raw chat;
- secret config;
- teljes player profile.

---

# 29. Rate limit és failure isolation

Token bucket:

- capacity 20;
- refill 1 / 500 ms;
- action cost 1..10;
- destructive/canonical/event start cost 10;
- AREA cost minimum `1 + ceil(targetCount / 16)`.

Global:

- max 256 session projection;
- max 1024 persistent projection;
- max 2048 durable receipt;
- max 8 parallel operation;
- max 32 projection/subject;
- max 64 binding;
- max 32 fork.

Provider circuit breaker:

3 non-domain runtime exception / 60 s:

```text
QUARANTINED
```

Inspect/mutation tiltott, recovery/cleanup tovább próbálható.

Restart oldja fel.

---

# 30. `MinecraftWeaverProvider`

## Inspect

PLAYER:

- location;
- gamemode;
- health/max;
- food/saturation;
- effects;
- velocity;
- flight;
- equipment.

ENTITY:

- UUID/type/location;
- health;
- AI;
- gravity;
- invulnerable;
- target;
- equipment.

BLOCK/LOCATION:

- material;
- BlockData;
- biome;
- light;
- protection summary.

WORLD:

- time;
- weather;
- difficulty;
- aggregate counts.

## Actions

Minimum:

```text
minecraft.heal
minecraft.damage
minecraft.kill
minecraft.clear_effects
minecraft.set_freeze_ticks
minecraft.set_fire_ticks
minecraft.set_velocity
minecraft.teleport
minecraft.set_ai
minecraft.set_invulnerable
minecraft.set_gamemode
minecraft.set_flight
minecraft.set_mob_target
minecraft.clear_mob_target
minecraft.remove_entity
minecraft.set_block_data
minecraft.lightning_effect
minecraft.play_effect
minecraft.set_world_time
minecraft.set_weather
minecraft.give_sandbox_stack
```

Teleport mindig `teleportAsync`.

`minecraft.damage` non-lethal; lethal csak explicit `kill`.

Protected IceSMP durable entity `remove_entity`-vel nem törölhető.

---

# 31. `PvEWeaverProvider`

Canonical seams:

- MobTemplate registry;
- MobAbilityRegistry;
- MobAbilityRuntime;
- behavior/profile authorities.

Új effective projection port:

```java
public interface MobRuntimeProjectionSource {
    EffectiveMobProjection resolve(
            UUID entityId,
            CanonicalMobProfile canonical);
}
```

## Inspect

- canonical/effective template;
- rank;
- archetype;
- level;
- abilities;
- resistances;
- weaknesses;
- behavior;
- affixes;
- active cast;
- faction semantic context;
- origin/influence.

## Actions

```text
pve.force_ability
pve.add_ability
pve.remove_ability
pve.override_rank
pve.override_archetype
pve.apply_template_projection
pve.clear_projection
pve.refresh_runtime
```

Projection nem emel canonical loot/reward bandet.

`force_ability` ugyanazt a cast lifecycle-t használja; cooldown/conditions/target validáció nem kerülhető meg.

## Export

```text
pve.export_ability
pve.export_rank
pve.export_archetype
pve.export_template
```

Új ability/template registryből automatikusan megjelenik.

---

# 32. `FactionWeaverProvider`

Projection source-ok:

```java
FactionMembershipProjectionSource
FactionContextProjectionSource
```

## Player projection

Hat:

- passive;
- damage/environment;
- mob targeting.

Nem hat:

- membership history;
- tax;
- treasury;
- season;
- quest;
- territory ownership;
- canonical HUD identity.

## Entity semantic context

Engedett:

```text
CORRUPTION
DUNGEON
INVASION
WORLD_BOSS
EVENT_MOB
QUEST_MOB
```

`CROWN_CURSE` projection tiltott, mert saját canonical lifecycle authorityja van.

## Actions

```text
faction.project_player_faction
faction.clear_player_projection
faction.add_entity_context
faction.remove_entity_context
faction.clear_entity_contexts
faction.set_membership_canonical
```

Canonical membership conditional profile transaction.

---

# 33. `TrashWeaverProvider`

Hidden provider.

## Inspect

- base identity;
- kind;
- behavior;
- primitive;
- lifecycle;
- instance UUID;
- revision;
- provenance;
- history;
- archaeology-derived facts;
- anomaly memory;
- rule fields.

## Domain seam

Normál runtime és provider ugyanazt használja:

```text
TrashAnomalyActivationService
TrashRelicActivationService
TrashRuleFieldService
```

Szintetikus Bukkit event tilos.

## Actions

```text
trash.individualize_unit
trash.activate_behavior
trash.transition_success
trash.repair
trash.give_sandbox_copy
trash.create_rule_field
trash.remove_rule_field
```

History:

`DEV_PROTOTYPED` külön event lehet, amelyet Archaeology természetes tényként nem kezel.

Meglévő történelmi eventet default provider action nem hamisít.

## Rule vocabulary

```text
PROJECTILE_WALL
ACOUSTIC_NULL
CEASEFIRE
SPATIAL_ANCHOR
```

---

# 34. `TerritoryWeaverProvider`

## Decision trace

A `TerritoryProtectionService` ugyanabból a pure evaluation core-ból ad inspection trace-et és runtime decisiont.

## Overlay

Rules:

```text
build
interact
pvp
explosions
fire
```

Values:

```text
INHERIT
ALLOW
DENY
```

Sandbox overlay:

- `DENY` allowed;
- `ALLOW` LIVE_GM required.

Hard lifecycle invariants overlay fölött maradnak:

- DOOM grace;
- combat tag;
- raid participant overrides;
- canonical hard bypasses.

## Canonical actions

```text
territory.rename_canonical
territory.set_type_canonical
territory.set_owner_canonical
territory.set_y_bounds_canonical
```

Fingerprint-based conditional transaction.

Define/remove/reshape territory existing canonical workflow marad; ha később erre biztonságos domain API készül, provider actionként hozzáadható — artifact core módosítása nélkül.

---

# 35. `EventWeaverProvider`

Nem új event engine.

Minden event:

```java
WeaverEventAdapter
```

Async start/stop porttal és stable instance UUID-val.

Minimum adapterek:

```text
blood_moon
world_boss
invasion
caravan
ambient
gathering
treasure
wild_hunt
abundance
server_challenge
escort
meteor
stranger
corruption
archeology
cultists
```

Actions:

```text
event.start
event.stop
```

Sandbox start:

- `DeveloperInfluence` origin;
- instance ID durable az első side effect előtt;
- spawnolt entityk ugyanazt az origin ID-t kapják;
- reward/progression tiltott;
- lifecycle/AI/VFX rendesen fut.

Stop csak expected instance-t állítja le.

---

# 36. `ItemizationWeaverProvider`

## Inspect

- item UUID;
- template/version;
- level;
- rarity;
- rolls;
- runes;
- ascension;
- signature;
- source/origin;
- revision;
- history;
- suppression/prototype marker.

## Mutation route

Új domain API:

```java
CompletionStage<DevItemMutationResult> executeFromWorldWeaver(
        Player actor,
        WeaverSlot targetSlot,
        String expectedFingerprint,
        long expectedItemRevision,
        DevItemMutation mutation,
        DeveloperInfluence influence);
```

Ugyanazt a mutation service/journal/render/recovery útvonalat használja.

## Actions

Sandbox:

```text
item.clone_prototype
item.reroll_prototype
item.ascend_prototype
item.add_rune_prototype
item.remove_rune_prototype
```

Canonical LIVE_GM:

```text
item.reroll_canonical
item.ascend_canonical
item.add_rune_canonical
item.remove_rune_canonical
```

Plusz:

```text
item.refresh_presentation
```

Clone új item UUID-t kap.

---

# 37. `DeveloperSelfWeaverProvider`

Csak actor == primary developer.

Minimum:

```text
self.recover_artifact
self.clear_session
self.clear_ephemeral_projections
self.set_vanish
self.arm_destructive
self.arm_live_gm
self.arm_canonical
self.disarm
self.capture_imprint
self.clear_imprints
self.create_binding
self.set_binding_status
self.remove_binding
self.start_fork
self.stop_fork
self.retry_operation_assessment
self.acknowledge_operation_resolved
self.forget_unresolved_projection
self.show_diagnostics
self.replay_intro
```

Vanish existing moderation/VanishManager route.

Nincs második vanish ledger.

---

# 38. `KnowledgeWeaverProvider` és univerzális current-domain coverage

Az eredeti design Quest/Bestiary esetén inspect-only modellt használt.

A v2 ezt két szintre bontja:

## 38.1 Release-safe alap

Inspect:

- Quest active/completed/tracked;
- progress;
- source policy trace;
- visibility/accept blocker;
- Bestiary known state;
- kill/tier/known entry metadata;
- achievement/discovery state, ahol canonical read API van.

## 38.2 Teljes „bármit” cél

Ha a current domain rendelkezik vagy kap **biztonságos conditional canonical mutation API-t**, a provider LIVE_GM/CANONICAL actiont publikálhat.

Példák:

```text
knowledge.quest_accept_canonical
knowledge.quest_reset_canonical
knowledge.quest_complete_without_reward_canonical
knowledge.bestiary_discover_canonical
knowledge.bestiary_reset_entry_canonical
knowledge.achievement_set_canonical
```

Feltétel:

- explicit domain mutation API;
- expected profile revision;
- reward side effect külön és explicit;
- audit;
- journal;
- undo logical compensation, ahol értelmes.

Raw PlayerProfile edit tilos.

Ha ilyen API nincs, az adott surface `INSPECT_ONLY_BY_DESIGN`, indokkal.

A coverage audit dönti el, melyik current domain maradhat így.

---

# 39. További current-system providerek

A production release előtti coverage audit köteles felmérni legalább:

- class/spec/talent runtime;
- profession state;
- class resources/cooldownok;
- currencies/economy faucets;
- pet progression/state;
- moderation/player runtime state;
- achievements/discovery;
- server challenge/community goal state;
- Doom/biome rendszerek, ha addig elkészülnek;
- minden új canonical gameplay manager.

Ha developer-manipuláció értelmes és domain API-val megvalósítható:

> **provider/action kötelező a production enable előtt.**

A konkrét provider lehet például:

```text
ProgressionWeaverProvider
ClassWeaverProvider
EconomyWeaverProvider
PetWeaverProvider
```

de ezek nevét a tényleges current repo authority felmérése zárja le.

Fontos:

> **ez a bővítés nem a Világszövő artifactot bővíti; csak új providereket regisztrál ugyanabba a protokollba.**

---

# 40. Imprint / Impress

Session max 8.

```java
public record WeaverImprint(
        UUID imprintId,
        String providerId,
        String facetId,
        int schemaVersion,
        Map<String, WeaverValue> values,
        String sourceFingerprint,
        long createdAt
) {}
```

Whitelistelt mezők.

Soha nem imprintelhető automatikusan:

- UUID;
- owner;
- history;
- reward receipt;
- player profile revision;
- event instance identity;
- economy receipt.

Példák:

PvE:

- rank;
- archetype;
- ability decisions.

Faction entity:

- semantic contexts.

Territory:

- rule overlay.

Itemization:

- template/rune csak sandbox prototype-ra.

---

# 41. Trigger Binding

Nem általános scriptnyelv.

Csak:

```text
SIGNAL -> ACTION
```

Regisztrált semantic signalok:

```text
weaver.entity_spawned
weaver.entity_damaged
weaver.entity_death
weaver.player_entered_area
weaver.player_left_area
weaver.event_started
weaver.event_stopped
weaver.timer
```

Binding:

- max 4 predicate;
- max 64 binding;
- max executions 1..1000;
- cooldown min 250 ms;
- timer min 20 tick;
- expiry max 7 nap;
- lineage depth max 4;
- csak SANDBOX;
- DESTRUCTIVE/CANONICAL tiltott;
- binding nem hozhat létre bindingot/forkot.

Primary developer online + authoritative artifact kell a trigger executionhöz; különben paused.

---

# 42. Runtime Fork

Csak provider `ForkableFacet` támogatással.

Release use case:

- PvE effective mob profile → sandbox spawn;
- Trash rule field set;
- Territory overlay set.

Event nem forkable, mert saját manager lifecycle-ja van.

PvE fork:

- max 64 entity;
- max 1 óra;
- SANDBOX;
- tainted;
- cleanup own ledgerből;
- persistent csak provider reconciliation támogatással.

Fork nem clone-ol canonical identity/history/receiptet.

---

# 43. Canonical mutation közös szabály

Canonical action csak akkor regisztrálható, ha:

1. egyetlen domain authority API;
2. expected revision/fingerprint;
3. durable fail closed;
4. observed state verifikálható;
5. conditional undo vagy explicit irreversible reason;
6. LIVE_GM + CANONICAL arming;
7. raw PDC/map/YAML edit nincs.

Canonical:

- nem AREA;
- nem Binding;
- nem Fork;
- nem Quick Apply auto-execute;
- nem offline inventory hack.

---

# 44. Dynamic provider UX

A GUI **nem tud domain neveket előre**.

Subject megnyitás:

```text
SubjectSnapshot
  -> providerRegistry.discover(snapshot)
  -> facet list
  -> generic renderer
```

Példa mob:

```text
Minecraft
PvE
Faction
Bestiary/Knowledge
Trash
Quest
<future provider>
```

A lista runtime buildből jön.

Provider nem hozhat saját GUI-t.

---

# 45. GUI layout

6 sor.

Javaslat:

- 0–8: subject header, integrity, refresh;
- 9–35: action/inspection entries;
- 36–44: facet tabs;
- 45: prev;
- 46: Location;
- 47: World;
- 48: Thread export;
- 49: active Thread;
- 50: Recent/Undo;
- 51: projections;
- 52: next;
- 53: back/close.

Catalog max 45/page.

---

# 46. Resource pack

Assetek:

```text
dev_world_weaver_idle
dev_world_weaver_subject
dev_world_weaver_thread
dev_world_weaver_canon
```

Modern 1.21.11 item model pipeline.

No legacy CustomModelData-only implementation.

Resource-pack regression + consistency gate kötelező.

---

# 47. Hidden documentation policy

Nem kerül:

- player guide;
- admin guide;
- builder guide;
- FEATURES;
- public changelog;
- public architecture/roadmap.

Új permission node nincs.

Internal implementation evidence a private DEV task/PR scope-ban marad.

Ha generikus refaktor publikus behavioron is változtat, csak a publikus behavior dokumentálandó, a WorldWeaver entrypoint nélkül.

---

# 48. Package layout

```text
hu.taliann.icesmp.dev.artifact
  DevArtifactDefinition
  DevArtifactRegistration
  DevArtifactBehavior
  DevArtifactOwnerPolicy
  FixedArtifactOwner
  ConfiguredArtifactOwner
  DevArtifactPresentation
  DevArtifactPolicy
  BingulusRewardBehavior
  WorldWeaverArtifactBehavior

hu.taliann.icesmp.dev.integrity
  DeveloperInfluence
  InfluenceScope
  DeveloperInfluenceService
  DeveloperInfluenceIndex
  DeveloperRewardGate
  DeveloperInfluenceListener
  DeveloperPrototypeProtectionListener

hu.taliann.icesmp.dev.weaver
  WorldWeaverKernel
  WorldWeaverProviderRegistry
  WorldWeaverCoverageRegistry
  WorldWeaverCoverageRegressionSuite
  WeaverAuthorityToken
  WorldWeaverSessionManager
  WeaverArming
  WeaverSelectionStore
  WeaverThreadCase
  WeaverImprintStore
  WeaverBindingRuntime
  WeaverForkRuntime

hu.taliann.icesmp.dev.weaver.subject
  SubjectRef
  WeaverSubjectKind
  SubjectKeyCodec
  PlayerRef
  EntityRef
  ItemSlotRef
  BlockRef
  LocationRef
  AreaRef
  WorldRef
  WeaverSlot
  AreaShape
  SubjectSnapshot
  SubjectSnapshotFactory
  SubjectAccessRouter
  ItemFingerprint

hu.taliann.icesmp.dev.weaver.api
  WorldWeaverProvider
  ProviderCoverage
  CoverageLevel
  ProviderContext
  ProviderContribution
  ProviderDiscovery
  FacetDescriptor
  ActionDescriptor
  ActionParameter
  RiskLevel
  Lifetime
  IntegrityMode
  IntegrityImpact
  AreaSupport
  WeaverTypeId
  WeaverTypeCodec
  WeaverTypeRegistry
  WeaverValue
  WeaverValueCatalog
  ExportDescriptor
  ImportDescriptor
  InspectionResult
  ActionRequest
  WeaverReceipt
  WeaverProjection
  WeaverImprint

hu.taliann.icesmp.dev.weaver.execution
  WeaverExecutionCoordinator
  PreparedAction
  ExecutionStage
  ExecutionOwner
  ActorOwner
  EntityOwner
  RegionOwner
  GlobalOwner
  AsyncIoOwner
  ProfileOwner
  WeaverOperationJournal
  WeaverConfirmationService
  WeaverRateLimiter
  ProviderCircuitBreaker
  ProtectedEntityPolicy

hu.taliann.icesmp.dev.weaver.persistence
  WorldWeaverStateStore
  WorldWeaverAuditStore
  WorldWeaverPersistenceCoordinator
  WorldWeaverReconciler
  WeaverOperationRecord
  OperationStatus
  WeaverAuditEntry

hu.taliann.icesmp.dev.weaver.gui
  WorldWeaverHolder
  WorldWeaverGuiController
  WeaverScalarInputService
  SubjectGui
  FacetGui
  ActionParameterGui
  ConfirmationGui
  ThreadCaseGui
  RecentReceiptGui
  ProjectionGui
  DiagnosticsGui

hu.taliann.icesmp.dev.weaver.provider
  MinecraftWeaverProvider
  PvEWeaverProvider
  FactionWeaverProvider
  TrashWeaverProvider
  TerritoryWeaverProvider
  EventWeaverProvider
  ItemizationWeaverProvider
  DeveloperSelfWeaverProvider
  KnowledgeWeaverProvider
  <coverage-audit alapján további providerek>
```

Domain seam-ek a saját domain package-ükben maradnak.

---

# 49. Bootstrap

Construction:

1. canonical registries/managers;
2. DeveloperInfluence + reward gate;
3. empty projection sources;
4. projection-aware domain runtimes;
5. state/audit stores;
6. provider instances;
7. coverage registry;
8. provider registry freeze/validate;
9. kernel/session/gui;
10. artifact behavior;
11. persistent stores.

Provider UI sorrend lehet stabil:

```text
minecraft
pve
faction
trash
territory
event
itemization
knowledge
self
<additional providers>
```

Semmilyen precedence nem függhet a sorrendtől.

---

# 50. Shutdown

1. new action tiltás;
2. binding/fork stop;
3. session projection cleanup;
4. persistent transient attachment rendezés;
5. artifact behavior shutdown;
6. domain runtime shutdown;
7. persistent save;
8. session map clear.

Max 10 s controlled drain.

Retired owner → deferred cleanup audit.

---

# 51. Implementation stack

## WW-00 — Capability coverage audit / source inventory

Mielőtt core implementation indul:

- current cumulative head összes gameplay manager/registry/authority áttekintése;
- provider coverage matrix;
- minden domain `FULL_PROVIDER`, `INSPECT_ONLY_BY_DESIGN` vagy `NO_RUNTIME_SURFACE`;
- `DEFERRED_BLOCKER` listázása;
- planned provider/action catalog;
- current canonical mutation seams azonosítása.

**WW-00 nem implementál feature-t, de normatív implementation input.**

DoD:

- nincs ismeretlen major gameplay authority;
- az artifact „bármit” célja konkrét coverage mátrixra fordítva.

## WW-01 — Generikus DEV artifact lifecycle

- generic DevArtifact contracts;
- DevItemManager schema 2;
- Bingulus behavior extraction behaviorváltozás nélkül;
- WorldWeaver artifact shell;
- resource pack states.

## WW-02 — Kernel vertical slice

- subject refs;
- snapshots;
- type/value/catalog;
- provider registry;
- authority;
- GUI;
- execution;
- reward influence basics;
- Minecraft provider initial.

## WW-03 — Durable state / projection / undo

- journal;
- persistence;
- reconciliation;
- receipt;
- conditional undo;
- AREA engine.

## WW-04 — PvE + Faction + integrity closure

- effective PvE projection;
- ability force/add/remove;
- rank/archetype;
- faction semantic projection;
- canonical faction transaction;
- reward gate generic closure.

## WW-05 — Trash + Territory

- activation service extraction;
- rule fields;
- history integrity;
- protection decision trace;
- territory overlay;
- canonical territory transaction.

## WW-06 — Itemization + Event + Self + Knowledge

- prototype quarantine;
- item mutation;
- event adapters;
- dedicated event reward gates;
- self arming/diagnostics;
- knowledge inspection.

## WW-07 — Thread / Imprint / AREA UX

- Thread Case;
- typed compatibility;
- Quick Apply;
- Imprint/Impress;
- area selection UX.

## WW-08 — Binding / Fork / hardening

- semantic signal registry;
- bounded bindings;
- runtime forks;
- circuit breaker;
- crash/Folia tests.

## WW-09 — Universal coverage closure

WW-00 matrix minden `DEFERRED_BLOCKER` sorának lezárása.

Szükség szerint:

- Progression provider;
- Class provider;
- Economy provider;
- Pet provider;
- additional knowledge canonical ports;
- addig elkészült Biome/Doom provider.

DoD:

- nincs `DEFERRED_BLOCKER`;
- minden current domain coverage rationale zárt;
- új content dynamic catalog regressions zöldek.

## WW-10 — Production enable

- complete acceptance evidence;
- feature flag true;
- release epoch 1;
- final Folia playtest;
- rollback safety verified.

---

# 52. Tesztstratégia

Új suite-ok:

```text
DevArtifactLifecycleRegressionSuite
DevArtifactMigrationRegressionSuite
WorldWeaverAuthorityRegressionSuite
WorldWeaverCoverageRegressionSuite

WeaverContractRegressionSuite
WeaverTypeCompatibilityRegressionSuite
WeaverExecutionRegressionSuite
WeaverProjectionRegressionSuite
WeaverPersistenceRegressionSuite
WeaverUndoRegressionSuite
WeaverIntegrityRegressionSuite
WeaverAreaRegressionSuite
WeaverBindingRegressionSuite
WeaverForkRegressionSuite
WeaverProviderRegressionSuite
WeaverDynamicCatalogRegressionSuite
```

## Contract tests

- type codec round-trip;
- provider ID uniqueness;
- manifest cross-reference;
- catalog/export/import;
- capability match;
- parameter bounds;
- projection precedence;
- fingerprint determinism;
- journal transitions;
- recovery assessment;
- conflict undo;
- rate/circuit;
- binding depth;
- fork whitelist;
- coverage closure.

## Dynamic growth tests

Minimum:

1. test registrybe új fake PvE ability;
2. WorldWeaver core változtatása nélkül catalogban megjelenik;
3. typed Thread export/import működik;
4. új fake provider regisztráció után GUI facet automatikusan megjelenik;
5. artifact behaviorhez egyetlen domain-specifikus módosítás sem szükséges.

Ez a v2 egyik legfontosabb acceptance bizonyítása.

---

# 53. Folia integration acceptance

Kötelező:

- actor és target külön régió;
- actor és mob külön régió;
- 9-region AREA;
- entity despawn prepare/apply között;
- logout confirmation közben;
- chunk unload/reload persistent projection;
- teleport retired callback;
- shutdown active operation alatt;
- idegen owneren nincs entity/inventory/PDC read;
- nincs blocking `.join()` / `.get()`;
- nincs legacy `Bukkit.getScheduler()` execution.

---

# 54. Crash/failure injection

Fault points:

- PREPARED előtt/után;
- first side effect előtt/után;
- APPLIED előtt/után;
- receipt/audit commit;
- compensation;
- final save;
- corrupt YAML;
- provider exception;
- scheduler retirement.

Elvárt:

```text
ABORTED
COMMITTED
COMPENSATED
NEEDS_REVIEW
```

Néma partial success nincs.

---

# 55. Production acceptance checklist

## Authority

- [ ] csak primary developer;
- [ ] OP/admin elutasítva;
- [ ] forged item nem mutál;
- [ ] duplicate recovery;
- [ ] artifact nem drop/container/trade.

## Universality

- [ ] WW-00 coverage matrix teljes;
- [ ] `DEFERRED_BLOCKER` = 0;
- [ ] minden current gameplay domain coverage döntéssel rendelkezik;
- [ ] minden értelmes developer surface providerből elérhető vagy explicit domain-invariant miatt kizárt;
- [ ] új subsystem providerrel bővíthető artifact/core módosítás nélkül;
- [ ] új registry-content automatikusan megjelenik catalogban;
- [ ] nincs domain-specific branch a generic artifact behaviorban/GUI rendererben.

## Folia

- [ ] minden live state owner thread;
- [ ] teleport async;
- [ ] no blocking;
- [ ] unloaded target pending;
- [ ] shutdown clean.

## Integrity

- [ ] sandbox reward/progress leak nincs;
- [ ] child taint propagál;
- [ ] prototype karantén;
- [ ] LIVE_GM explicit arming.

## Projection

- [ ] canonical PDC-t projection nem ír;
- [ ] explicit effective consumer;
- [ ] reward identity projectiontől nem változik;
- [ ] canonical mutation domain API.

## Persistence

- [ ] PREPARED journal;
- [ ] crash reconcile;
- [ ] conflict-safe undo;
- [ ] corrupt store fail closed;
- [ ] release epoch safe.

## UX

- [ ] no permanent HUD;
- [ ] stale GUI safe;
- [ ] Thread compatibility typed;
- [ ] Quick Apply bounded;
- [ ] CANONICAL explicit warning;
- [ ] model states valid.

---

# 56. Kézi end-to-end acceptance

## A — Artifact recovery

- authoritative issuance;
- forged duplicate;
- death/full inventory;
- one authoritative instance.

## B — Sandbox PvE

- mob focus;
- rank projection;
- ability projection;
- canonical loot identity változatlan;
- death reward/progress zero.

## C — Persistent unload/restart

- persistent ability;
- chunk unload;
- restart;
- pending until entity load;
- reapply;
- undo conflict-safe.

## D — Faction boundary

- player projected faction;
- passive/targeting változik;
- tax/treasury/quest/territory/history nem;
- session clear.

## E — Trash integrity

- prototype;
- actual activation service;
- rule field;
- DEV history excluded from natural archaeology;
- no market leak.

## F — Territory

- decision trace;
- overlay;
- same protection evaluation;
- external canonical change;
- old undo conflict.

## G — Sandbox event

- world boss start;
- origin/instance propagated;
- gameplay runs;
- rewards zero;
- undo exact instance only.

## H — Item prototype

- clone new UUID;
- cost-free prototype mutation;
- quarantine;
- canonical mutation only armed LIVE_GM.

## I — Thread

- export ability;
- mob importer complete;
- player incompatible;
- missing params → GUI, no auto execute.

## J — Crash

- APPLIED crash;
- reconcile;
- no silent partial state.

## K — Dynamic evolution

1. A test build új mob abilityt ad a canonical registryhez.
2. A Világszövő artifact/core/GUI Java forrása változatlan.
3. Az új ability a catalogban megjelenik.
4. Threadként exportálható/importálható, ha a provider capability ezt támogatja.
5. A test build egy új dummy subsystem providert regisztrál.
6. Új facet és action automatikusan megjelenik.
7. Generic renderer változatlan.

Ez a **„nem kell mindig hozzányúlni a tárgyhoz”** acceptance.

---

# 57. Migration és kompatibilitás

Kötelező migration:

- `dev-items-state.yml` schema 2.

Nem migrálandó:

- player profiles;
- canonical mob PDC;
- itemization instance;
- Trash history;
- territories;
- event state;
- Quest/Bestiary.

Backward compatibility:

- existing DEV item helper API-k wrapperrel megmaradnak az átállásig;
- Bingulus ID/PDC unchanged;
- existing hidden give route tovább működik;
- WorldWeaver type/action ID schema után alias/migration nélkül nem nevezhető át.

---

# 58. Definition of Done minden WorldWeaver változáshoz

1. provider/action descriptor friss;
2. coverage metadata friss;
3. consumer matrix friss;
4. nincs raw authority shortcut;
5. Folia owner tesztelt;
6. SANDBOX reward/progress auditált;
7. journal/undo tesztelt, ha mutáció;
8. magyar feedback key + inline default;
9. resource pack gate, ha vizuál változik;
10. targeted regressions;
11. full build;
12. consistency gate;
13. shutdown/restart acceptance;
14. generic artifact/core nem kap domain-specifikus if/else branch-et;
15. új content esetén dynamic catalog regression bizonyítja a zero-core-change viselkedést.

---

# 59. Végső architekturális állítás

A Világszövő nem attól univerzális, hogy tetszőleges Java memóriát képes átírni.

Attól univerzális, hogy:

> **az IceSMP minden gameplay-releváns domainje egy typed, versioned, auditalt és Folia-safe developer capability contracton keresztül csatlakozhat hozzá.**

A végleges adatfolyam:

```text
authoritative DEV artifact
  -> stable SubjectRef
  -> owner-thread SubjectSnapshot
  -> pure provider discovery
  -> generic facet/action GUI
  -> typed validation / Thread / catalog
  -> preview + confirmation + arming
  -> journaled owner-routed execution
  -> canonical domain authority OR explicit projection consumer
  -> DeveloperInfluence integrity gate
  -> receipt + conditional undo
  -> durable audit / reconciliation
```

Új content:

```text
canonical registry
  -> existing provider catalog
  -> automatikusan látható
```

Új subsystem:

```text
new subsystem
  -> new WeaverProvider
  -> registry
  -> automatikusan látható
```

Amit emiatt **nem kell módosítani**:

- WorldWeaver artifact behavior;
- generic input mapping;
- generic Subject GUI;
- generic Facet GUI;
- Thread Case;
- generic execution coordinator;
- generic persistence protocol.

---

# 60. Product statement

> **A játékos a világ szabályai szerint játszik. Az admin a világ állapotát kezeli. A Világszövő használója az IceSMP publikált szabályait, állapotait és capabilityjeit közvetlenül manipulálhatja és komponálhatja.**

A végleges product goal:

> **A primary developer számára a teljes IceSMP manipulálható runtime-ja egyetlen fizikai in-game artifacton keresztül legyen elérhető.**

És a hosszú távú architectural rule:

> **A Világszövőt egyszer írjuk meg. Utána az IceSMP bővíti a Világszövőt — nem a Világszövőt kell minden új feature után újraírni.**
