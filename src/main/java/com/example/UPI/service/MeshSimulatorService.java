package com.example.UPI.service;

import com.example.UPI.model.MeshPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MeshSimulatorService {

    private final Map<String, VirtualDevice> devices = new ConcurrentHashMap<>();

    public MeshSimulatorService() {
        seedDefaultDevices();
    }

    private void seedDefaultDevices() {
        devices.put("phone-alice",     new VirtualDevice("phone-alice",     false));
        devices.put("phone-stranger1", new VirtualDevice("phone-stranger1", false));
        devices.put("phone-stranger2", new VirtualDevice("phone-stranger2", false));
        devices.put("phone-stranger3", new VirtualDevice("phone-stranger3", false));
        devices.put("phone-bridge",    new VirtualDevice("phone-bridge",    true));
    }

    public Collection<VirtualDevice> getDevices() {
        return devices.values();
    }

    public VirtualDevice getDevice(String id) {
        return devices.get(id);
    }

    public void inject(String senderDeviceId, MeshPacket packet) {
        VirtualDevice sender = devices.get(senderDeviceId);
        if (sender == null) {
            throw new IllegalArgumentException("Unknown device: " + senderDeviceId);
        }
        sender.hold(packet);
        log.info("Packet {} injected at {} (TTL={})",
                packet.getPacketId().substring(0, 8), senderDeviceId, packet.getTtl());
    }

    public GossipResult gossipOnce() {
        List<VirtualDevice> deviceList = new ArrayList<>(devices.values());

        Map<String, List<MeshPacket>> snapshot = new HashMap<>();
        for (VirtualDevice device : deviceList) {
            snapshot.put(device.getDeviceId(), new ArrayList<>(device.getHeldPackets()));
        }

        int transfers = 0;

        for (VirtualDevice source : deviceList) {
            for (MeshPacket packet : snapshot.get(source.getDeviceId())) {
                if (packet.getTtl() <= 0) continue;

                for (VirtualDevice destination : deviceList) {
                    if (destination == source) continue;
                    if (destination.holds(packet.getPacketId())) continue;

                    MeshPacket copy = MeshPacket.builder()
                            .packetId(packet.getPacketId())
                            .ttl(packet.getTtl() - 1)
                            .createdAt(packet.getCreatedAt())
                            .ciphertext(packet.getCiphertext())
                            .build();

                    destination.hold(copy);
                    transfers++;
                }
            }
        }

        log.info("Gossip round complete: {} transfer(s)", transfers);
        return new GossipResult(transfers, snapshotMap());
    }

    public Map<String, Integer> snapshotMap() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (VirtualDevice device : devices.values()) {
            result.put(device.getDeviceId(), device.packetCount());
        }
        return result;
    }

    public List<BridgeUpload> collectBridgeUploads() {
        List<BridgeUpload> uploads = new ArrayList<>();
        for (VirtualDevice device : devices.values()) {
            if (!device.hasInternet()) continue;
            for (MeshPacket packet : device.getHeldPackets()) {
                uploads.add(new BridgeUpload(device.getDeviceId(), packet));
            }
        }
        return uploads;
    }

    public void resetMesh() {
        devices.values().forEach(VirtualDevice::clear);
        log.info("Mesh reset — all devices cleared");
    }

    public record GossipResult(int transfers, Map<String, Integer> deviceCounts) {}
    public record BridgeUpload(String bridgeNodeId, MeshPacket packet) {}
}