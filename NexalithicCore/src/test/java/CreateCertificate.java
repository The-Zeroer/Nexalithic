import com.thezeroer.nexalithic.core.security.DefaultCertificate;
import com.thezeroer.nexalithic.core.util.BinaryStorageUtils;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;

/**
 * 创建证书
 *
 * @author tbrtz647@outlook.com
 * @since 2026/04/12
 * @version 1.0.0
 */
public class CreateCertificate {
    private static final String SIGNATURE_ALGORITHM = "Ed25519";
    public static void main(String[] args) throws Exception {
        Path path = getSourceDirectory(CreateCertificate.class).getParent().resolve("resources");

        System.out.println("Path: " + getSourceDirectory(CreateCertificate.class).getParent().resolve("resources"));

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(SIGNATURE_ALGORITHM);
        KeyPair rootKeyPair = keyPairGenerator.generateKeyPair();
        KeyPair issuingKeyPair = keyPairGenerator.generateKeyPair();
        KeyPair leafPair = keyPairGenerator.generateKeyPair();
        DefaultCertificate issuingCert = sign(issuingKeyPair.getPublic(), rootKeyPair.getPrivate(), 1);
        DefaultCertificate leafCert = sign(leafPair.getPublic(), issuingKeyPair.getPrivate(), 1);
        ByteBuffer issuingCertBuffer = ByteBuffer.allocate(issuingCert.totalSize());
        issuingCert.toBuffer(issuingCertBuffer);
        ByteBuffer leafCertBuffer = ByteBuffer.allocate(leafCert.totalSize());
        leafCert.toBuffer(leafCertBuffer);

        BinaryStorageUtils.saveBytes(issuingCertBuffer.array(), path.resolve("issuingCertificate").toString());
        BinaryStorageUtils.saveBytes(leafCertBuffer.array(), path.resolve("leafCertificate").toString());
        BinaryStorageUtils.saveBytes(rootKeyPair.getPublic().getEncoded(), path.resolve("rootPublicKey").toString());
        BinaryStorageUtils.saveBytes(leafPair.getPrivate().getEncoded(), path.resolve("leafPrivateKey").toString());
        System.out.println("--- 生成完成 ---");
    }

    public static DefaultCertificate sign(PublicKey targetPublic, PrivateKey signerPrivate, int version) throws Exception {
        long now = 0;
        long expiry = Long.MAX_VALUE;
        byte[] pubKeyBytes = targetPublic.getEncoded();
        ByteBuffer dataToSign = ByteBuffer.allocate(Integer.BYTES + Long.BYTES * 2 + pubKeyBytes.length);
        dataToSign.putInt(version).putLong(now).putLong(expiry).put(pubKeyBytes);
        dataToSign.flip();
        Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
        sig.initSign(signerPrivate);
        sig.update(dataToSign);
        return new DefaultCertificate(version, now, expiry, pubKeyBytes, sig.sign());
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    public static Path getSourceDirectory(Class<?> clazz) throws Exception {
        // 获取编译后的 .class 文件所在的绝对路径
        // 例如: /D:/Project/target/test-classes/com/thezeroer/nexalithic/client/security/
        String classPath = clazz.getResource(".").getPath();

        // 移除路径开头的 "/" (Windows 环境下)
        if (System.getProperty("os.name").toLowerCase().contains("win") && classPath.startsWith("/")) {
            classPath = classPath.substring(1);
        }

        // 核心逻辑：从编译输出目录回溯到源码目录
        // 假设 Maven/Gradle 结构: 寻找 "target" 或 "out" 并替换为 "src/test/java"
        String sourcePath;
        if (classPath.contains("/target/test-classes/")) {
            sourcePath = classPath.replace("/target/test-classes/", "/src/test/java/");
        } else if (classPath.contains("/out/test/classes/")) { // IntelliJ 默认输出
            sourcePath = classPath.replace("/out/test/classes/", "/src/test/java/");
        } else {
            // 如果不在标准输出目录下，直接返回 class 目录
            sourcePath = classPath;
        }

        return Paths.get(sourcePath);
    }
}
