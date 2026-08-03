# PlayerProfile read APIs

## Internal Java API

`IceSMPPlayerProfileApi` is registered through Bukkit `ServicesManager`. It exposes immutable snapshots, section snapshots, name lookup, filtered public DTOs and a listener subscription. It never exposes repository adapters, YAML, file paths or mutable Bukkit objects, and all storage reads are asynchronous.

## HTTP API v1

The adapter is disabled by default and binds to `127.0.0.1` only when explicitly enabled. Public endpoints respect profile visibility. SELF bearer credentials are bound to one player UUID; ADMIN credentials may read health, quarantine and moderation/operations summaries. Tokens are deployment secrets, are digested in memory and never logged. The adapter enforces rate, request/response size and timeout limits, supports ETag/`If-None-Match`, returns sanitized 403/404/409/429/500 responses and drains on shutdown. It has no write endpoints.

The machine contract is [`openapi/player-profile-v1.yaml`](openapi/player-profile-v1.yaml).

## SQL replacement contract

A future `SqlPlayerProfileRepository` and transaction manager must preserve owner binding, per-section CAS, manifest-equivalent snapshot generation, quarantine semantics, operation fingerprints and recovery results. Gameplay managers, DTOs, commands, GUI and HTTP code must not change. A YAML importer performs structured decode, domain validation and repository saves; it never guesses from PDC or invokes gameplay migration.
