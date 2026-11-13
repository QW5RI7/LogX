package io.github.qw5ri7.logx.java;

import io.github.qw5ri7.logx.BaseTest;
import io.github.qw5ri7.logx.model.JavaInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class JavaScannerTest extends BaseTest {
    @Test
    public void testJavaScanner() {
        List<JavaInfo> javaInfos = JavaScanner.scan();
        Assertions.assertFalse(javaInfos.isEmpty());
        for (JavaInfo info : javaInfos) {
            System.out.println(info);
        }
    }
}
