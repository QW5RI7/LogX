package io.github.qw5ri7.logx.util;

import io.github.qw5ri7.logx.model.HashType;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 哈希工具类，用于检验文件哈希值
 */
@Slf4j
public class HashUtil {
    /**
     * 检验文件哈希值是否匹配
     * @param filePath 文件路径
     * @param expected 预期的哈希值，十六进制字符串
     * @param hashType 哈希算法类型，如 SHA-256
     * @return 如果匹配则返回 true，否则返回 false
     */
    public static boolean verifyFileHash(String filePath, String expected, HashType hashType) {
        if (hashType == null || expected == null || expected.isEmpty()) return true;// 如果没有哈希类型或哈希值为空，认为检验通过
        try (InputStream is = new FileInputStream(filePath)) {
            String algorithm = hashType.getAlgorithm();
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b)); // 转换为十六进制字符串
            }
            String actualHash = sb.toString();

            return actualHash.equalsIgnoreCase(expected);
        } catch (Exception e) {
            log.error("检验文件哈希值失败可能文件不存在", e);
            return false;
        }
    }

    public static boolean verifyBytesHash(byte[] byteArray, String expected, HashType hashType) {
        if (hashType == null || expected == null || expected.isEmpty()) return true;
        String algorithm = hashType.getAlgorithm();
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            digest.update(byteArray);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b)); // 转换为十六进制字符串
            }
            String actualHash = sb.toString();
            return actualHash.equalsIgnoreCase(expected);
        } catch (Exception e) {
            log.error("检验字节数组哈希值失败可能算法不存在", e);
            return false;
        }
    }
}
