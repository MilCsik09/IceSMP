package hu.taliann.icesmp.dev.weaver.api;

public record AreaLimits(int maxBlocks, int maxEntities, int maxChunks, int maxRegions, int concurrency) {
    public AreaLimits {
        if (maxBlocks < 0 || maxBlocks > 4096 || maxEntities < 0 || maxEntities > 128 || maxChunks < 1 || maxChunks > 9
                || maxRegions < 1 || maxRegions > 9 || concurrency < 1 || concurrency > 16) throw new IllegalArgumentException("AREA limits exceed release caps");
    }
}
