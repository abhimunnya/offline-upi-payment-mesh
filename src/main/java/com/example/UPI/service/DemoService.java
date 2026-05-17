package com.example.UPI.service;

import com.example.UPI.crypto.HybridCryptoService;
import com.example.UPI.crypto.ServerKeyHolder;
import com.example.UPI.model.Account;
import com.example.UPI.model.MeshPacket;
import com.example.UPI.model.PaymentInstruction;
import com.example.UPI.repository.AccountRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class DemoService {

    private final AccountRepository   accountRepository;
    private final HybridCryptoService cryptoService;
    private final ServerKeyHolder     serverKeyHolder;

    public DemoService(AccountRepository accountRepository,
                       HybridCryptoService cryptoService,
                       ServerKeyHolder serverKeyHolder) {
        this.accountRepository = accountRepository;
        this.cryptoService     = cryptoService;
        this.serverKeyHolder   = serverKeyHolder;
    }

    @PostConstruct
    public void seedAccounts() {
        if (accountRepository.count() > 0) return;

        List<Account> demoAccounts = List.of(
                Account.builder().vpa("alice@demo").holderName("Alice").balance(new BigDecimal("5000.00")).build(),
                Account.builder().vpa("bob@demo").holderName("Bob").balance(new BigDecimal("1000.00")).build(),
                Account.builder().vpa("carol@demo").holderName("Carol").balance(new BigDecimal("2500.00")).build(),
                Account.builder().vpa("dave@demo").holderName("Dave").balance(new BigDecimal("500.00")).build()
        );

        accountRepository.saveAll(demoAccounts);
        log.info("Seeded {} demo accounts", demoAccounts.size());
    }

    public MeshPacket createPacket(String senderVpa,
                                   String receiverVpa,
                                   BigDecimal amount,
                                   String pin,
                                   int ttl) throws Exception {

        PaymentInstruction instruction = PaymentInstruction.builder()
                .senderVpa(senderVpa)
                .receiverVpa(receiverVpa)
                .amount(amount)
                .pinHash(sha256Hex(pin))
                .nonce(UUID.randomUUID().toString())
                .signedAt(Instant.now().toEpochMilli())
                .build();

        String ciphertext = cryptoService.encrypt(instruction, serverKeyHolder.getPublicKey());

        return MeshPacket.builder()
                .packetId(UUID.randomUUID().toString())
                .ttl(ttl)
                .createdAt(Instant.now().toEpochMilli())
                .ciphertext(ciphertext)
                .build();
    }

    private String sha256Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest(input.getBytes());
        StringBuilder hex = new StringBuilder(64);
        for (byte b : hashBytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}