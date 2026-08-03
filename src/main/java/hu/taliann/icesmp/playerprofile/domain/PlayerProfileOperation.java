package hu.taliann.icesmp.playerprofile.domain;
import java.time.Instant;import java.util.*;
public record PlayerProfileOperation(String operationId,String type,Status status,String fingerprint,Instant createdAt,Instant updatedAt,Map<String,String> metadata){
 public PlayerProfileOperation{operationId=ImmutableValues.id(operationId,"operationId");type=ImmutableValues.id(type,"type");Objects.requireNonNull(status);fingerprint=ImmutableValues.id(fingerprint,"fingerprint");Objects.requireNonNull(createdAt);Objects.requireNonNull(updatedAt);metadata=ImmutableValues.strings(metadata,64);if(updatedAt.isBefore(createdAt))throw new IllegalArgumentException("operation time order invalid");}
 public enum Status{PREPARED,COMMITTED,ROLLED_BACK,FAILED}
}