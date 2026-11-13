package io.github.qw5ri7.logx.net;

import io.github.qw5ri7.logx.BaseTest;
import io.github.qw5ri7.logx.model.MCVersion;
import io.github.qw5ri7.logx.model.VersionManifest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VersionFetcherTest extends BaseTest {
    @Test
    public void testFetchVersionManifest() throws IOException, InterruptedException {
        VersionManifest manifest = VersionFetcher.fetchVersionManifest().join();
        assertNotNull(manifest);
        for (MCVersion version : manifest.getVersions()) {
            System.out.println(version);
        }
    }
}
