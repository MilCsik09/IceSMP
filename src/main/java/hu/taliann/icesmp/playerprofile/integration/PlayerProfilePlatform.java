package hu.taliann.icesmp.playerprofile.integration;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.playerprofile.api.IceSMPPlayerProfileApi;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileService;
import hu.taliann.icesmp.playerprofile.http.PlayerProfileHttpServer;
import hu.taliann.icesmp.playerprofile.persistence.*;
import hu.taliann.icesmp.playerprofile.transaction.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Path;import java.time.Duration;import java.util.*;import java.util.concurrent.*;

/** Owns the PlayerProfile repository, read APIs and bounded lifecycle. */
public final class PlayerProfilePlatform {
 private final JavaPlugin plugin;private final ConfigManager config;private final YamlPlayerProfileRepository repository;private final YamlPlayerProfileTransactionManager transactions;private final PlayerProfileService service;private PlayerProfileHttpServer http;private boolean started;
 public PlayerProfilePlatform(JavaPlugin plugin,ConfigManager config){this.plugin=Objects.requireNonNull(plugin);this.config=Objects.requireNonNull(config);Path root=plugin.getDataFolder().toPath().resolve("player-profiles");repository=new YamlPlayerProfileRepository(root);transactions=new YamlPlayerProfileTransactionManager(repository);service=new PlayerProfileService(repository,transactions);}
 public PlayerProfileRepository repository(){return repository;}public PlayerProfileService service(){return service;}public PlayerProfileTransactionManager transactions(){return transactions;}
 public synchronized void start(){if(started)return;Bukkit.getServicesManager().register(IceSMPPlayerProfileApi.class,service,plugin,ServicePriority.Normal);PlayerProfileHttpServer.Config httpConfig=httpConfig();http=new PlayerProfileHttpServer(service,httpConfig);try{if(http.start())plugin.getLogger().info("PlayerProfile read API bound to "+httpConfig.bindAddress()+":"+http.boundPort());}catch(Exception failure){Bukkit.getServicesManager().unregister(IceSMPPlayerProfileApi.class,service);throw new IllegalStateException("PlayerProfile HTTP API startup failed",failure);}started=true;}
 public synchronized CompletionStage<PlayerProfileRepository.ShutdownResult> shutdown(Duration timeout){if(!started)return repository.shutdown(timeout);started=false;Bukkit.getServicesManager().unregister(IceSMPPlayerProfileApi.class,service);CompletionStage<Void> httpStop=http==null?CompletableFuture.completedFuture(null):http.stop(timeout.dividedBy(3));return httpStop.handle((v,x)->null).thenCompose(v->service.shutdown(timeout));}
 private PlayerProfileHttpServer.Config httpConfig(){boolean enabled=config.getBoolean("player-profile.http.enabled",false);String bind=config.getString("player-profile.http.bind","127.0.0.1");int port=config.getInt("player-profile.http.port",8765);Map<String,PlayerProfileHttpServer.TokenGrant> tokens=new LinkedHashMap<>();for(String entry:config.getStringList("player-profile.http.self-tokens")){if(entry==null||entry.isBlank())continue;int separator=entry.indexOf(':');if(separator<=0||separator==entry.length()-1)throw new IllegalStateException("self token entries must be <uuid>:<token>");UUID playerId=UUID.fromString(entry.substring(0,separator).trim());String token=entry.substring(separator+1).trim();tokens.put(token,PlayerProfileHttpServer.TokenGrant.self(playerId));}for(String token:config.getStringList("player-profile.http.admin-tokens"))if(token!=null&&!token.isBlank())tokens.put(token.trim(),PlayerProfileHttpServer.TokenGrant.admin());return new PlayerProfileHttpServer.Config(enabled,bind,port,tokens,config.getInt("player-profile.http.requests-per-minute",60),config.getInt("player-profile.http.max-request-bytes",1024),config.getInt("player-profile.http.max-response-bytes",1_048_576),Duration.ofMillis(config.getLong("player-profile.http.timeout-ms",3000)));}
}
