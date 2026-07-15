/*
 * Derived from DCTimer-BLE's Moyu32Cipher.java (GPL-3.0-or-later).
 * Copyright retained from the upstream GPLv3 project.
 */
package com.huizhi.rubikey.cube.moyu;

import android.annotation.SuppressLint;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/* AES/ECB is part of the Moyu32 cube wire protocol and is not application data encryption. */
@SuppressLint("GetInstance")
final class Moyu32Cipher {
    private static final byte[] BASE_KEY = {21, 119, 58, 92, 103, 14, 45, 31, 23, 103, 42, 19, -101, 103, 82, 87};
    private static final byte[] BASE_IV = {17, 35, 38, 37, -122, 42, 44, 59, 85, 6, 127, 49, 126, 103, 33, 87};
    private byte[] iv;
    private Cipher encryptCipher;
    private Cipher decryptCipher;

    void init(String mac) throws GeneralSecurityException {
        byte[] macBytes = parseMac(mac);
        byte[] key = Arrays.copyOf(BASE_KEY, BASE_KEY.length);
        iv = Arrays.copyOf(BASE_IV, BASE_IV.length);
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

    byte[] encode(byte[] value) throws GeneralSecurityException { return transform(value, encryptCipher); }

    byte[] decode(byte[] value) throws GeneralSecurityException {
        byte[] result = Arrays.copyOf(value, value.length);
        if (!ready()) return result;
        if (result.length < 16) throw new GeneralSecurityException("MoYu32 packet is shorter than AES block");
        if (result.length > 16) decryptAt(result, result.length - 16);
        decryptAt(result, 0);
        return result;
    }

    private byte[] transform(byte[] value, Cipher cipher) throws GeneralSecurityException {
        byte[] result = Arrays.copyOf(value, value.length);
        if (!ready()) return result;
        if (result.length < 16) throw new GeneralSecurityException("MoYu32 packet is shorter than AES block");
        encryptAt(result, 0, cipher);
        if (result.length > 16) encryptAt(result, result.length - 16, cipher);
        return result;
    }

    private boolean ready() { return encryptCipher != null && decryptCipher != null && iv != null; }
    private void encryptAt(byte[] data, int offset, Cipher cipher) throws GeneralSecurityException {
        byte[] block = Arrays.copyOfRange(data, offset, offset + 16); xor(block); write(data, offset, cipher.doFinal(block));
    }
    private void decryptAt(byte[] data, int offset) throws GeneralSecurityException {
        byte[] block = decryptCipher.doFinal(Arrays.copyOfRange(data, offset, offset + 16)); xor(block); write(data, offset, block);
    }
    private void xor(byte[] block) { for (int i = 0; i < 16; i++) block[i] = (byte) (block[i] ^ iv[i]); }
    private void write(byte[] target, int offset, byte[] block) { System.arraycopy(block, 0, target, offset, 16); }
    private byte[] parseMac(String mac) {
        String[] parts = mac.trim().toUpperCase(Locale.US).split(":");
        if (parts.length != 6) throw new IllegalArgumentException("invalid MAC address");
        byte[] result = new byte[6];
        for (int i = 0; i < 6; i++) result[i] = (byte) Integer.parseInt(parts[i], 16);
        return result;
    }
}
