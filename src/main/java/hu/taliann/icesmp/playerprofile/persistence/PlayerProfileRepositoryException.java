package hu.taliann.icesmp.playerprofile.persistence;
public class PlayerProfileRepositoryException extends RuntimeException{
 public PlayerProfileRepositoryException(String message){super(message);} public PlayerProfileRepositoryException(String message,Throwable cause){super(message,cause);}
 public static final class RevisionConflict extends PlayerProfileRepositoryException{private final long expected,actual;public RevisionConflict(long expected,long actual,String message){super(message);this.expected=expected;this.actual=actual;}public long expected(){return expected;}public long actual(){return actual;}}
 public static final class Quarantined extends PlayerProfileRepositoryException{public Quarantined(String message){super(message);}}
}
