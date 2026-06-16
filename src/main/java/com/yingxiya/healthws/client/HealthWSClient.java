package com.yingxiya.healthws.client;

import com.google.gson.JsonObject;
import com.yingxiya.healthws.HealthWS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class HealthWSClient {
    private static final int UDP_PORT = 9988;
    private static final int TICK_THROTTLE = 20;
    private static DatagramSocket udpSocket;
    private static InetAddress targetAddr;

    private static float lastHealth = 20.0F;
    private static int tickCounter = 0;

    public static void registerTick() {
        try {
            udpSocket = new DatagramSocket();
            targetAddr = InetAddress.getByName("127.0.0.1");
        } catch (Exception e) {
            HealthWS.LOGGER.error("UDP 初始化失败", e);
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(HealthWSClient::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        // 仅Tick结束执行，对应Fabric END_CLIENT_TICK
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        float nowHp = clamp(player.getHealth(), 0F, player.getMaxHealth());
        float maxHp = player.getMaxHealth();
        float realDamage = Math.max(0.0F, lastHealth - nowHp);
        boolean isDead = player.isDeadOrDying();

        tickCounter++;
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
            lastHealth = nowHp;
        }
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private static void sendUdp(String data) {
        if (udpSocket == null || udpSocket.isClosed()) return;
        try {
            byte[] bytes = data.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, targetAddr, UDP_PORT);
            udpSocket.send(packet);
        } catch (Exception ignored) {}
    }
}