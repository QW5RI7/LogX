package io.github.qw5ri7.logx;

import org.junit.jupiter.api.BeforeAll;

public class BaseTest {
    @BeforeAll
    public static void init() {
        Bootstrap.init();
    }
}
