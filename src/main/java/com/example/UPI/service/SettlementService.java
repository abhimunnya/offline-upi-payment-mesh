package com.example.UPI.service;

import com.example.UPI.exception.AccountNotFoundException;
import com.example.UPI.model.Account;
import com.example.UPI.model.PaymentInstruction;
import com.example.UPI.model.Transaction;
import com.example.UPI.repository.AccountRepository;
import com.example.UPI.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
public class SettlementService {

    private final AccountRepository     accountRepository;
    private final TransactionRepository transactionRepository;

    public SettlementService(AccountRepository accountRepository,
                             TransactionRepository transactionRepository) {
        this.accountRepository     = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction settle(PaymentInstruction instruction,
                              String packetHash,
                              String bridgeNodeId,
                              int hopCount) {

        Account sender = accountRepository.findById(instruction.getSenderVpa())
                .orElseThrow(() -> new AccountNotFoundException(instruction.getSenderVpa()));

        Account receiver = accountRepository.findById(instruction.getReceiverVpa())
                .orElseThrow(() -> new AccountNotFoundException(instruction.getReceiverVpa()));

        BigDecimal amount = instruction.getAmount();

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive, got: " + amount);
        }

        if (sender.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient balance: {} has ₹{}, tried to send ₹{}",
                    sender.getVpa(), sender.getBalance(), amount);
            return recordTransaction(instruction, packetHash, bridgeNodeId,
                    hopCount, Transaction.Status.REJECTED);
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accountRepository.save(sender);
        accountRepository.save(receiver);

        Transaction tx = recordTransaction(instruction, packetHash, bridgeNodeId,
                hopCount, Transaction.Status.SETTLED);

        log.info("SETTLED ₹{} | {} → {} | bridge={} hops={} hash={}...",
                amount, sender.getVpa(), receiver.getVpa(),
                bridgeNodeId, hopCount, packetHash.substring(0, 12));

        return tx;
    }

    private Transaction recordTransaction(PaymentInstruction instruction,
                                          String packetHash,
                                          String bridgeNodeId,
                                          int hopCount,
                                          Transaction.Status status) {
        Transaction tx = Transaction.builder()
                .packetHash(packetHash)
                .senderVpa(instruction.getSenderVpa())
                .receiverVpa(instruction.getReceiverVpa())
                .amount(instruction.getAmount())
                .signedAt(Instant.ofEpochMilli(instruction.getSignedAt()))
                .settledAt(Instant.now())
                .bridgeNodeId(bridgeNodeId)
                .hopCount(hopCount)
                .status(status)
                .build();

        return transactionRepository.save(tx);
    }
}