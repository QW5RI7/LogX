package io.github.qw5ri7.logx.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略未知字段，防止解析报错
public class AssetIndexJson {
    private Map<String, AssetObject> objects;
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetObject {
        private String hash;
        private Long size;
    }
}