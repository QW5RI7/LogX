package io.github.qw5ri7.logx.core;

import io.github.qw5ri7.logx.BaseTest;
import io.github.qw5ri7.logx.auth.OfflineAuthenticator;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class GameLauncherTest extends BaseTest {
    @SneakyThrows
    @Test
    public void testLaunchGame() {
        Process process = GameLauncher.launchGame(OfflineAuthenticator.login("Test"), "./.minecraft/versions/1.20.4/");
        new Thread(() -> {
            try (InputStreamReader is = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                int read;
                while ((read = is.read()) != -1) {
                    System.out.print((char) read);
                }
            } catch (Exception ignored) {
            }
        }).start();
        process.waitFor();
        Assertions.assertEquals(0, process.exitValue());
    }
}
