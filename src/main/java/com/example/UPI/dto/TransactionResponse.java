package com.example.UPI.dto;

import com.example.UPI.model.Transaction;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {

    private Long id;
    private String packetHash;
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private Instant signedAt;
    private Instant settledAt;
    private String bridgeNodeId;
    private int hopCount;
    private String status;

    public static TransactionResponse from(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .packetHash(tx.getPacketHash())
                .senderVpa(tx.getSenderVpa())
                .receiverVpa(tx.getReceiverVpa())
                .amount(tx.getAmount())
                .signedAt(tx.getSignedAt())
                .settledAt(tx.getSettledAt())
                .bridgeNodeId(tx.getBridgeNodeId())
                .hopCount(tx.getHopCount())
                .status(tx.getStatus().name())
                .build();
    }
}
