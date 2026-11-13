package io.github.qw5ri7.logx.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

import static io.github.qw5ri7.logx.Launcher.APP_DIR;

/**
 * 加密工具类，用于加密和解密字符串
 */
public class CryptoUtil {
    private static final String ALGORITHM = "AES";
    private static final String KEY_FILE_NAME = "mylauncher_secret.key";
    private static SecretKey secretKey;

    // 静态代码块，在类加载时自动初始化密钥
    static {
        initKey();
    }

    private static void initKey() {
        try {
            File keyFile = new File(APP_DIR, KEY_FILE_NAME);

            if (keyFile.exists()) {
                // 如果密钥文件存在，读取它
                byte[] keyBytes = Files.readAllBytes(keyFile.toPath());
                secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            } else {
                // 如果不存在，生成一个随机的 256 位密钥
                KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
                keyGen.init(256); // AES-256
                secretKey = keyGen.generateKey();

                // 保存到文件
                Files.write(keyFile.toPath(), secretKey.getEncoded());
                System.out.println("首次启动，已生成新的加密密钥并保存。");
            }
        } catch (Exception e) {
            throw new RuntimeException("初始化加密密钥失败", e);
        }
    }

    /**
     * 加密字符串
     * @param data 待加密的字符串
     * @return 加密后的字符串，使用 Base64 编码存储
     */
    public static String encrypt(String data) {
        if (data == null || data.isEmpty()) return data;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            // 转成 Base64 方便存入数据库
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密字符串
     * @param encryptedData 待解密的字符串，使用 Base64 编码存储
     * @return 解密后的字符串
     */
    public static String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) return encryptedData;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("解密失败，可能是密钥不匹配或数据损坏");
            return null;
        }
    }
}