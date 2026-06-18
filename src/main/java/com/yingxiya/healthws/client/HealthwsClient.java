package com.yingxiya.healthws.client;

import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class HealthwsClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("healthws");
    private static final int UDP_PORT = 9988;
    private static final int TICK_THROTTLE = 20;

    private DatagramSocket udpSocket;
    private InetAddress targetAddr;

    private float lastHealth = 20.0F;
    private int tickCounter = 0;

    private static DamageSource pendingDamageSource = null;
    private static float pendingDamageAmount = 0.0F;

    public static void recordDamage(DamageSource source, float amount) {
        pendingDamageSource = source;
        pendingDamageAmount = amount;
    }

    @Override
    public void onInitializeClient() {
        try {
            udpSocket = new DatagramSocket();
            targetAddr = InetAddress.getByName("127.0.0.1");
            LOG.info("UDP 初始化成功");
        } catch (Exception e) {
            LOG.error("UDP 初始化失败", e);
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PlayerEntity player = client.player;
            if (player == null) return;

            float nowHp = clamp(player.getHealth(), 0F, player.getMaxHealth());
            float maxHp = player.getMaxHealth();
            float realDamage = Math.max(0.0F, lastHealth - nowHp);
            boolean isDead = player.isDead();

            tickCounter++;
            boolean needSend = realDamage > 0 || tickCounter >= TICK_THROTTLE;

            if (needSend) {
                JsonObject json = new JsonObject();
                json.addProperty("name", player.getName().getString());
                json.addProperty("health", nowHp);
                json.addProperty("maxHealth", maxHp);
                json.addProperty("Damage", realDamage);
                json.addProperty("isDead", isDead);

                if (pendingDamageSource != null && realDamage > 0) {
                    Entity attacker = pendingDamageSource.getAttacker();
                    if (attacker != null) {
                        json.addProperty("attackerName", attacker.getName().getString());
                        json.addProperty("attackerType", attacker.getType().toString());

                        double distance = attacker.squaredDistanceTo(player);
                        json.addProperty("distance", Math.round(distance * 100.0) / 100.0);
                    }

                    Entity sourceEntity = pendingDamageSource.getSource();
                    if (sourceEntity != null && sourceEntity != attacker) {
                        json.addProperty("sourceName", sourceEntity.getName().getString());
                        json.addProperty("sourceType", sourceEntity.getType().toString());
                    }

                    json.addProperty("damageTypeName", pendingDamageSource.getName());
                    json.addProperty("damageAmount", pendingDamageAmount);

                    pendingDamageSource = null;
                    pendingDamageAmount = 0.0F;
                }

                sendUdp(json.toString());

                tickCounter = 0;
                lastHealth = nowHp;
            }
        });
    }

    private float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private void sendUdp(String data) {
        if (udpSocket == null || udpSocket.isClosed()) return;
        try {
            byte[] bytes = data.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, targetAddr, UDP_PORT);
            udpSocket.send(packet);
        } catch (Exception ignored) {}
    }
}
