package com.example.UPI.controller;

import com.example.UPI.crypto.ServerKeyHolder;
import com.example.UPI.dto.*;
import com.example.UPI.model.MeshPacket;
import com.example.UPI.repository.AccountRepository;
import com.example.UPI.repository.TransactionRepository;
import com.example.UPI.service.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class ApiController {

    private final ServerKeyHolder        serverKeyHolder;
    private final DemoService            demoService;
    private final MeshSimulatorService   meshSimulatorService;
    private final BridgeIngestionService bridgeIngestionService;
    private final AccountRepository      accountRepository;
    private final TransactionRepository  transactionRepository;
    private final IdempotencyService     idempotencyService;

    public ApiController(ServerKeyHolder serverKeyHolder,
                         DemoService demoService,
                         MeshSimulatorService meshSimulatorService,
                         BridgeIngestionService bridgeIngestionService,
                         AccountRepository accountRepository,
                         TransactionRepository transactionRepository,
                         IdempotencyService idempotencyService) {
        this.serverKeyHolder        = serverKeyHolder;
        this.demoService            = demoService;
        this.meshSimulatorService   = meshSimulatorService;
        this.bridgeIngestionService = bridgeIngestionService;
        this.accountRepository      = accountRepository;
        this.transactionRepository  = transactionRepository;
        this.idempotencyService     = idempotencyService;
    }



    @GetMapping("/server-key")
    public ResponseEntity<Map<String, String>> getServerPublicKey() {
        return ResponseEntity.ok(Map.of(
                "publicKey",    serverKeyHolder.getPublicKeyBase64(),
                "algorithm",    "RSA-2048",
                "padding",      "OAEP-SHA256",
                "hybridScheme", "RSA-OAEP wraps AES-256-GCM session key"
        ));
    }



    @PostMapping("/demo/send")
    public ResponseEntity<Map<String, Object>> demoSend(
            @Valid @RequestBody DemoSendRequest request) throws Exception {

        int ttl       = request.getTtl()         != null ? request.getTtl()         : 5;
        String device = request.getStartDevice() != null ? request.getStartDevice() : "phone-alice";

        MeshPacket packet = demoService.createPacket(
                request.getSenderVpa(),
                request.getReceiverVpa(),
                request.getAmount(),
                request.getPin(),
                ttl
        );

        meshSimulatorService.inject(device, packet);

        return ResponseEntity.ok(Map.of(
                "packetId",          packet.getPacketId(),
                "ciphertextPreview", packet.getCiphertext().substring(0, 64) + "...",
                "ttl",               packet.getTtl(),
                "injectedAt",        device
        ));
    }



    @GetMapping("/mesh/state")
    public ResponseEntity<Map<String, Object>> meshState() {
        List<Map<String, Object>> deviceData = meshSimulatorService.getDevices().stream()
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("deviceId",    d.getDeviceId());
                    m.put("hasInternet", d.hasInternet());
                    m.put("packetCount", d.packetCount());
                    m.put("packetIds",   d.getHeldPackets().stream()
                            .map(p -> p.getPacketId().substring(0, 8))
                            .toList());
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "devices",              deviceData,
                "idempotencyCacheSize", idempotencyService.size()
        ));
    }

    @PostMapping("/mesh/gossip")
    public ResponseEntity<Map<String, Object>> meshGossip() {
        MeshSimulatorService.GossipResult result = meshSimulatorService.gossipOnce();
        return ResponseEntity.ok(Map.of(
                "transfers",    result.transfers(),
                "deviceCounts", result.deviceCounts()
        ));
    }

    @PostMapping("/mesh/flush")
    public ResponseEntity<Map<String, Object>> meshFlush() {
        List<MeshSimulatorService.BridgeUpload> uploads =
                meshSimulatorService.collectBridgeUploads();

        List<Map<String, Object>> results =
                Collections.synchronizedList(new ArrayList<>());

        uploads.parallelStream().forEach(upload -> {
            int hopCount = 5 - upload.packet().getTtl();
            IngestResponse response = bridgeIngestionService.ingest(
                    upload.packet(), upload.bridgeNodeId(), hopCount);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("bridgeNode",    upload.bridgeNodeId());
            entry.put("packetId",      upload.packet().getPacketId().substring(0, 8));
            entry.put("outcome",       response.getOutcome());
            entry.put("reason",        response.getReason() != null ? response.getReason() : "");
            entry.put("transactionId", response.getTransactionId() != null
                    ? response.getTransactionId() : -1);
            results.add(entry);
        });

        return ResponseEntity.ok(Map.of(
                "uploadsAttempted", uploads.size(),
                "results",          results
        ));
    }

    @PostMapping("/mesh/reset")
    public ResponseEntity<Map<String, String>> meshReset() {
        meshSimulatorService.resetMesh();
        idempotencyService.clear();
        return ResponseEntity.ok(Map.of("status", "mesh and idempotency cache cleared"));
    }



    @PostMapping("/bridge/ingest")
    public ResponseEntity<IngestResponse> bridgeIngest(
            @Valid @RequestBody MeshPacket packet,
            @RequestHeader(value = "X-Bridge-Node-Id", defaultValue = "unknown") String bridgeNodeId,
            @RequestHeader(value = "X-Hop-Count",      defaultValue = "0")       int hopCount) {

        IngestResponse response = bridgeIngestionService.ingest(packet, bridgeNodeId, hopCount);
        return ResponseEntity.ok(response);
    }



    @GetMapping("/accounts")
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        return ResponseEntity.ok(
                accountRepository.findAll()
                        .stream()
                        .map(AccountResponse::from)
                        .toList()
        );
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> listTransactions() {
        return ResponseEntity.ok(
                transactionRepository.findTop20ByOrderByIdDesc()
                        .stream()
                        .map(TransactionResponse::from)
                        .toList()
        );
    }
}