package com.example.UPI.dto;

import com.example.UPI.model.Account;
import lombok.*;
import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {

    private String vpa;
    private String holderName;
    private BigDecimal balance;

    public static AccountResponse from(Account account) {
        return AccountResponse.builder()
                .vpa(account.getVpa())
                .holderName(account.getHolderName())
                .balance(account.getBalance())
                .build();
    }
}