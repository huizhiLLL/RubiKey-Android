/*
 * Derived from DCTimer-BLE's GanCubeCipher.java (GPL-3.0-or-later).
 */
package com.huizhi.rubikey.cube.gan;

import android.annotation.SuppressLint;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/* AES/ECB is part of the GAN cube wire protocol and is not application data encryption. */
@SuppressLint("GetInstance")
final class GanCubeCipher {
    private byte[] iv;
    private Cipher encryptCipher;
    private Cipher decryptCipher;

    void init(byte[] baseKey, byte[] baseIv, String mac) throws GeneralSecurityException {
        byte[] macBytes = parseMac(mac);
        byte[] key = Arrays.copyOf(baseKey, baseKey.length);
        iv = Arrays.copyOf(baseIv, baseIv.length);
        for (int i = 0; i < 6; i++) {
            key[i] = (byte) (((key[i] & 0xff) + (macBytes[5 - i] & 0xff)) % 255);
            iv[i] = (byte) (((iv[i] & 0xff) + (macBytes[5 - i] & 0xff)) % 255);
        }
        SecretKeySpec spec = new SecretKeySpec(key, "AES");
        encryptCipher = Cipher.getInstance("AES/ECB/NoPadding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, spec);
        decryptCipher = Cipher.getInstance("AES/ECB/NoPadding");
        decryptCipher.init(Cipher.DECRYPT_MODE, spec);
    }

    byte[] encode(byte[] value) throws GeneralSecurityException {
        byte[] result = Arrays.copyOf(value, value.length);
        xor(result, 0);
        write(result, 0, encryptCipher.doFinal(Arrays.copyOf(result, 16)));
        if (result.length > 16) {
            int offset = result.length - 16;
            byte[] block = Arrays.copyOfRange(result, offset, offset + 16);
            xor(block, 0);
            write(result, offset, encryptCipher.doFinal(block));
        }
        return result;
    }

    byte[] decode(byte[] value) throws GeneralSecurityException {
        byte[] result = Arrays.copyOf(value, value.length);
        if (result.length > 16) {
            int offset = result.length - 16;
            byte[] block = decryptCipher.doFinal(Arrays.copyOfRange(result, offset, offset + 16));
            xor(block, 0);
            write(result, offset, block);
        }
        byte[] first = decryptCipher.doFinal(Arrays.copyOf(result, 16));
        xor(first, 0);
        write(result, 0, first);
        return result;
    }

    private static byte[] parseMac(String mac) {
        String[] parts = mac == null ? new String[0] : mac.trim().toUpperCase(Locale.US).split(":");
        if (parts.length != 6) throw new IllegalArgumentException("invalid mac: " + mac);
        byte[] result = new byte[6];
        for (int i = 0; i < 6; i++) result[i] = (byte) Integer.parseInt(parts[i], 16);
        return result;
    }

    private void xor(byte[] target, int offset) {
        for (int i = 0; i < 16; i++) target[offset + i] ^= iv[i];
    }

    private static void write(byte[] target, int offset, byte[] block) {
        System.arraycopy(block, 0, target, offset, 16);
    }
}
