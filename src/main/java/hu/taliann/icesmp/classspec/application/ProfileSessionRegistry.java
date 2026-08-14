package hu.taliann.icesmp.classspec.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared generation fence across join, durable mutations and scheduler callbacks. */
public final class ProfileSessionRegistry {
    private final Map<UUID,Session> sessions=new ConcurrentHashMap<>();
    private final AtomicBoolean accepting=new AtomicBoolean(true);

    public UUID begin(UUID playerId){Objects.requireNonNull(playerId);if(!accepting.get())throw new IllegalStateException("Profile sessions stopped");UUID token=UUID.randomUUID();sessions.put(playerId,new Session(token,State.ACTIVATING,""));return token;}
    public void markReady(UUID playerId,UUID token){replace(playerId,token,State.READY,"");}
    public void markReconciliationRequired(UUID playerId,UUID token,String detail){replace(playerId,token,State.RECONCILIATION_REQUIRED,clean(detail));}
    public void markClosing(UUID playerId,UUID token){replace(playerId,token,State.CLOSING,"");}
    public boolean close(UUID playerId,UUID token){return sessions.computeIfPresent(playerId,(id,current)->current.token().equals(token)?null:current)==null&&!sessions.containsKey(playerId);}
    public boolean isCurrent(UUID playerId,UUID token){Session current=sessions.get(playerId);return accepting.get()&&current!=null&&current.token().equals(token)&&current.state()!=State.CLOSING;}
    public boolean runIfCurrent(UUID playerId,UUID token,Runnable action){Objects.requireNonNull(playerId);Objects.requireNonNull(token);Objects.requireNonNull(action);AtomicBoolean ran=new AtomicBoolean();sessions.computeIfPresent(playerId,(id,current)->{if(!accepting.get()||!current.token().equals(token)||current.state()==State.CLOSING)return current;action.run();ran.set(true);return current;});return ran.get();}
    public boolean isReady(UUID playerId){Session current=sessions.get(playerId);return accepting.get()&&current!=null&&current.state()==State.READY;}
    public Optional<UUID> currentToken(UUID playerId){Session current=sessions.get(playerId);return current==null?Optional.empty():Optional.of(current.token());}
    public Optional<Session> session(UUID playerId){return Optional.ofNullable(sessions.get(playerId));}
    public void stop(){accepting.set(false);sessions.replaceAll((id,s)->new Session(s.token(),State.STOPPED,"plugin disable"));}
    public boolean accepting(){return accepting.get();}
    private void replace(UUID playerId,UUID token,State state,String detail){sessions.compute(playerId,(id,current)->{if(current==null||!current.token().equals(token))throw new StaleSessionException(playerId,token);return new Session(token,state,detail);});}
    private static String clean(String detail){String value=detail==null?"":detail.trim();return value.length()<=512?value:value.substring(0,512);}
    public record Session(UUID token,State state,String detail){public Session{Objects.requireNonNull(token);Objects.requireNonNull(state);detail=clean(detail);}}
    public enum State{ACTIVATING,READY,RECONCILIATION_REQUIRED,CLOSING,STOPPED}
    public static final class StaleSessionException extends RuntimeException{private static final long serialVersionUID=1L;public StaleSessionException(UUID playerId,UUID token){super("Stale Profile v2 session callback for "+playerId+" token="+token);}}
}
