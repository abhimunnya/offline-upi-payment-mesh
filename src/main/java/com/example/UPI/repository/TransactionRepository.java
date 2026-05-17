package com.example.UPI.repository;

import com.example.UPI.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findTop20ByOrderByIdDesc();

    boolean existsByPacketHash(String packetHash);
}
