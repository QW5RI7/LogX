package io.github.qw5ri7.logx.util;

import io.github.qw5ri7.logx.BaseTest;
import io.github.qw5ri7.logx.model.LauncherConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConfigUtilTest extends BaseTest {
    @Test
    public void testLoadConfig() throws Exception {
        LauncherConfig config = ConfigUtil.getCurrentConfig();
        assertNotNull(config);
        System.out.println(config);
    }
}
