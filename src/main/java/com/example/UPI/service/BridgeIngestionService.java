package com.example.UPI.service;

import com.example.UPI.crypto.HybridCryptoService;
import com.example.UPI.dto.IngestResponse;
import com.example.UPI.model.MeshPacket;
import com.example.UPI.model.PaymentInstruction;
import com.example.UPI.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class BridgeIngestionService {

    private final HybridCryptoService  cryptoService;
    private final IdempotencyService   idempotencyService;
    private final SettlementService    settlementService;

    @Value("${upi.mesh.packet-max-age-seconds:86400}")
    private long maxAgeSeconds;

    @Value("${upi.mesh.clock-skew-tolerance-seconds:300}")
    private long clockSkewToleranceSeconds;

    public BridgeIngestionService(HybridCryptoService cryptoService,
                                  IdempotencyService idempotencyService,
                                  SettlementService settlementService) {
        this.cryptoService      = cryptoService;
        this.idempotencyService = idempotencyService;
        this.settlementService  = settlementService;
    }

    public IngestResponse ingest(MeshPacket packet, String bridgeNodeId, int hopCount) {
        try {
            String packetHash = cryptoService.hashCiphertext(packet.getCiphertext());

            if (!idempotencyService.claim(packetHash)) {
                log.info("DUPLICATE dropped | hash={}...", packetHash.substring(0, 12));
                return IngestResponse.builder()
                        .outcome("DUPLICATE_DROPPED")
                        .packetHash(packetHash)
                        .build();
            }

            PaymentInstruction instruction;
            try {
                instruction = cryptoService.decrypt(packet.getCiphertext());
            } catch (Exception e) {
                log.warn("Decryption failed | hash={}... | reason={}",
                        packetHash.substring(0, 12), e.getMessage());
                return IngestResponse.builder()
                        .outcome("INVALID")
                        .packetHash(packetHash)
                        .reason("decryption_failed")
                        .build();
            }

            long ageSeconds = (Instant.now().toEpochMilli() - instruction.getSignedAt()) / 1000;

            if (ageSeconds > maxAgeSeconds) {
                log.warn("Stale packet | hash={}... | age={}s",
                        packetHash.substring(0, 12), ageSeconds);
                return IngestResponse.builder()
                        .outcome("INVALID")
                        .packetHash(packetHash)
                        .reason("stale_packet: " + ageSeconds + "s old")
                        .build();
            }

            if (ageSeconds < -clockSkewToleranceSeconds) {
                log.warn("Future-dated packet | hash={}... | age={}s",
                        packetHash.substring(0, 12), ageSeconds);
                return IngestResponse.builder()
                        .outcome("INVALID")
                        .packetHash(packetHash)
                        .reason("future_dated")
                        .build();
            }

            Transaction tx = settlementService.settle(
                    instruction, packetHash, bridgeNodeId, hopCount);

            return IngestResponse.builder()
                    .outcome(tx.getStatus().name())
                    .packetHash(packetHash)
                    .transactionId(tx.getId())
                    .build();

        } catch (Exception e) {
            log.error("Ingestion pipeline error: {}", e.getMessage(), e);
            return IngestResponse.builder()
                    .outcome("INVALID")
                    .packetHash("unknown")
                    .reason("internal_error: " + e.getMessage())
                    .build();
        }
    }
}