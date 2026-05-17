package com.example.UPI.dto;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestResponse {

    private String outcome;
    private String packetHash;
    private String reason;
    private Long transactionId;
}
