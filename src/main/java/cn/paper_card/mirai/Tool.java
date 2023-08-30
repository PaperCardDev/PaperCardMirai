package cn.paper_card.mirai;


import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

class Tool {

    static byte @NotNull [] decodeHex(@NotNull String hexStr) {
        final HexFormat hexFormat = HexFormat.of().withUpperCase();
        return hexFormat.parseHex(hexStr);
    }

    static @NotNull String encodeHex(byte @NotNull [] bytes) {
        final HexFormat hexFormat = HexFormat.of().withUpperCase();
        return hexFormat.formatHex(bytes);
    }

    static byte @NotNull [] md5Digest(byte @NotNull [] bytes) {
        final MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        md5.update(bytes);

        return md5.digest();
    }

}
