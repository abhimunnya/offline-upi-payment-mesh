package com.example.UPI.model;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeshPacket {

    @NotBlank
    private String packetId;

    @Min(0)
    private int ttl;

    @NotNull
    private Long createdAt;

    @NotBlank
    private String ciphertext;
}