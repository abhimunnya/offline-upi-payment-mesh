package com.example.UPI.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.UPI.model.PaymentInstruction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

@Slf4j
@Service
public class HybridCryptoService {

    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int    AES_KEY_BITS       = 256;
    private static final int    GCM_IV_BYTES       = 12;
    private static final int    GCM_TAG_BITS       = 128;
    private static final int    RSA_KEY_BYTES      = 256;

    private final SecureRandom    rng;
    private final ObjectMapper    objectMapper;
    private final ServerKeyHolder serverKeyHolder;

    public HybridCryptoService(ObjectMapper objectMapper, ServerKeyHolder serverKeyHolder) {
        this.objectMapper    = objectMapper;
        this.serverKeyHolder = serverKeyHolder;
        this.rng             = new SecureRandom();
    }

    public String encrypt(PaymentInstruction instruction,
                          PublicKey serverPublicKey) throws Exception {

        byte[] plaintext = objectMapper.writeValueAsBytes(instruction);

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(AES_KEY_BITS, rng);
        SecretKey aesKey = kg.generateKey();

        byte[] iv = new byte[GCM_IV_BYTES];
        rng.nextBytes(iv);
        Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] aesCiphertext = aesCipher.doFinal(plaintext);

        Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION);
        OAEPParameterSpec oaepSpec = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        rsaCipher.init(Cipher.ENCRYPT_MODE, serverPublicKey, oaepSpec);
        byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());

        ByteBuffer buffer = ByteBuffer.allocate(
                encryptedAesKey.length + iv.length + aesCiphertext.length);
        buffer.put(encryptedAesKey);
        buffer.put(iv);
        buffer.put(aesCiphertext);

        return Base64.getEncoder().encodeToString(buffer.array());
    }

    public PaymentInstruction decrypt(String base64Ciphertext) throws Exception {

        byte[] all = Base64.getDecoder().decode(base64Ciphertext);

        int minLength = RSA_KEY_BYTES + GCM_IV_BYTES + (GCM_TAG_BITS / 8);
        if (all.length < minLength) {
            throw new IllegalArgumentException("Ciphertext too short: " + all.length);
        }

        ByteBuffer buffer      = ByteBuffer.wrap(all);
        byte[] encryptedAesKey = new byte[RSA_KEY_BYTES];
        byte[] iv              = new byte[GCM_IV_BYTES];
        byte[] aesCiphertext   = new byte[all.length - RSA_KEY_BYTES - GCM_IV_BYTES];

        buffer.get(encryptedAesKey);
        buffer.get(iv);
        buffer.get(aesCiphertext);

        Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION);
        OAEPParameterSpec oaepSpec = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        rsaCipher.init(Cipher.DECRYPT_MODE, serverKeyHolder.getPrivateKey(), oaepSpec);
        byte[] aesKeyBytes = rsaCipher.doFinal(encryptedAesKey);
        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

        Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plaintext = aesCipher.doFinal(aesCiphertext);

        return objectMapper.readValue(plaintext, PaymentInstruction.class);
    }

    public String hashCiphertext(String base64Ciphertext) throws NoSuchAlgorithmException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = sha256.digest(base64Ciphertext.getBytes());
        StringBuilder hex = new StringBuilder(64);
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
