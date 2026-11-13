package io.github.qw5ri7.logx.model;

import lombok.Data;

import java.util.List;

@Data
public class VersionManifest {
    private List<MCVersion> versions;
    private Latest latest;
    @Data
    public static class Latest {
        private String release;
        private String snapshot;
    }
}
