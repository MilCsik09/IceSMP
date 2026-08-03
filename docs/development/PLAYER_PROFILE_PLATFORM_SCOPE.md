# PlayerProfile platform scope

This stacked draft branch turns the greenfield class/spec Profile v2 root into the modular IceSMP PlayerProfile platform.

The final branch will contain the storage-independent domain, section registry, structured YAML repository, per-section CAS, cross-section transaction protocol, lifecycle/query services, internal Bukkit service API, read-only HTTP API, authority guards, full player-owned persistence integrations, tests and documentation.

This file intentionally contains no legacy migration plan: the server is greenfield and old player PDC/YAML state is not an authority.
