package com.example.UPI.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemoSendRequest {

    @NotBlank(message = "senderVpa is required")
    private String senderVpa;

    @NotBlank(message = "receiverVpa is required")
    private String receiverVpa;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "pin is required")
    @Size(min = 4, max = 6, message = "PIN must be 4-6 digits")
    private String pin;

    private Integer ttl;

    private String startDevice;
}
