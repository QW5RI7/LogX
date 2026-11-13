package io.github.qw5ri7.logx.model;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class MCVersion {
    private String id;
    private VersionType type;
    private String url;
    private String sha1;
    private OffsetDateTime releaseTime;
    private int complianceLevel;
}
