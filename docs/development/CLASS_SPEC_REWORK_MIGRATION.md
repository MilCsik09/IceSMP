# Greenfield PlayerProfile initialization

There is no legacy player-data migration. Missing profiles and sections initialize as empty revision-0 PlayerProfile state. Old development PDC/YAML is inert and may only be removed by an explicit admin cleanup; it is never imported or used as fallback.
