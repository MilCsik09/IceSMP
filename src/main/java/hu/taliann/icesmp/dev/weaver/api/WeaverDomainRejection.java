package hu.taliann.icesmp.dev.weaver.api;

/** Expected domain refusal is a bounded result, not a provider circuit-breaker failure. */
public final class WeaverDomainRejection extends RuntimeException {
    private final String code;
    public WeaverDomainRejection(final String code) {
        super(code, null, false, false);
        if (code == null || !code.matches("[A-Z0-9_]{1,64}")) throw new IllegalArgumentException("Invalid domain result code");
        this.code = code;
    }
    public String code() { return code; }
}
