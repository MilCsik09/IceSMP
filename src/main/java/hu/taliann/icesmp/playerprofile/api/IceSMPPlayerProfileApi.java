package hu.taliann.icesmp.playerprofile.api;
import hu.taliann.icesmp.playerprofile.domain.*;import java.util.*;import java.util.concurrent.*;
public interface IceSMPPlayerProfileApi {
 CompletionStage<Optional<PlayerProfileSnapshot>> find(UUID playerId);
 CompletionStage<Optional<PlayerProfileSnapshot>> findByName(String playerName);
 CompletionStage<Optional<ProfileSectionSnapshot<?>>> section(UUID playerId,ProfileSectionId section);
 CompletionStage<PublicPlayerProfileDto> publicProfile(UUID playerId);
 CompletionStage<SelfPlayerProfileDto> selfProfile(UUID playerId);
 CompletionStage<AdminPlayerProfileDto> adminProfile(UUID playerId);
 AutoCloseable subscribe(PlayerProfileListener listener);
 @FunctionalInterface interface PlayerProfileListener{void changed(UUID playerId,long profileRevision,Set<ProfileSectionId> changedSections);}
}
