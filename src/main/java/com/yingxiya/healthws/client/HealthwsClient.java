package com.yingxiya.healthws.client;

import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class HealthwsClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("healthws");
    private static final int UDP_PORT = 9988;
    private static final int TICK_THROTTLE = 20; // 20tick=1s节流
    private DatagramSocket udpSocket;
    private InetAddress targetAddr;

    private float lastHealth = 20.0F;
    private int tickCounter = 0;

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
            // 两种情况发包：1.产生伤害  2.每秒一次定时同步
            boolean needSend = realDamage > 0 || tickCounter >= TICK_THROTTLE;

            if (needSend) {
                JsonObject json = new JsonObject();
                json.addProperty("name", player.getName().getString());
                json.addProperty("health", nowHp);
                json.addProperty("maxHealth", maxHp);
                json.addProperty("Damage", realDamage);
                json.addProperty("isDead", isDead);
                sendUdp(json.toString());

                tickCounter = 0;
                // 更新血量缓存
                lastHealth = nowHp;
            }
        });
    }

    // 数值钳位，限制0~max
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