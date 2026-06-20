package com.yingxiya.healthws.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Collection;

public class HealthwsClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("healthws");
    private static final int UDP_PORT = 9988;
    private static final int TICK_THROTTLE = 20;

    private DatagramSocket udpSocket;
    private InetAddress targetAddr;

    public static boolean debug = false;

    private float lastHealth = 20.0F;
    private int tickCounter = 0;

    private static DamageSource pendingDamageSource = null;
    private static float pendingDamageAmount = 0.0F;

    public static void recordDamage(DamageSource source, float amount) {
        pendingDamageSource = source;
        pendingDamageAmount = amount;
        if (debug) {
            LOG.info("记录伤害: source={}, amount={}, type={}",
                source != null ? source.getType().msgId() : "null",
                amount,
                source != null && source.getSource() != null ? source.getSource().getType().toString() : "no_source");
        }
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

                if (debug && realDamage > 0) {
                    LOG.info("检测到伤害: realDamage={}, pendingDamageSource={}, pendingDamageAmount={}",
                        realDamage,
                        pendingDamageSource != null ? "not_null" : "null",
                        pendingDamageAmount);
                }

                Collection<StatusEffectInstance> effects = player.getStatusEffects();
                if (!effects.isEmpty()) {
                    JsonArray potionArray = new JsonArray();
                    for (StatusEffectInstance effect : effects) {
                        JsonObject potionObj = new JsonObject();
                        String translationKey = effect.getTranslationKey();
                        String englishName = getEnglishTranslation(translationKey);
                        potionObj.addProperty("name", englishName);
                        potionObj.addProperty("amplifier", (effect.getAmplifier() + 1));
                        potionObj.addProperty("duration", effect.getDuration());
                        potionObj.addProperty("isAmbient", effect.isAmbient());
                        potionObj.addProperty("visible", effect.shouldShowParticles());
                        potionArray.add(potionObj);
                    }
                    json.add("potions", potionArray);
                }

                if (pendingDamageSource != null && realDamage > 0) {
                    Entity source = pendingDamageSource.getSource();
                    if (debug) {
                        LOG.info("正在处理伤害来源: source_entity={}",
                            source != null ? source.getType().toString() : "null");
                    }
                    if (source instanceof LivingEntity livingAttacker) {
                        json.addProperty("attackerName", livingAttacker.getName().getString());
                        json.addProperty("attackerType", livingAttacker.getType().toString());

                        double distance = livingAttacker.squaredDistanceTo(player);
                        json.addProperty("distance", Math.round(distance * 100.0) / 100.0);
                    } else if (source != null) {
                        json.addProperty("attackerType", source.getType().toString());
                    }

                    json.addProperty("damageTypeName", pendingDamageSource.getType().msgId());
                    json.addProperty("pendingDamageAmount", pendingDamageAmount);

                    pendingDamageSource = null;
                    pendingDamageAmount = 0.0F;
                } else if (debug && realDamage > 0) {
                    LOG.warn("伤害来源丢失: pendingDamageSource={}, realDamage={}",
                        pendingDamageSource, realDamage);
                }

                sendMsgToChat(json.toString());
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
        } catch (Exception ignored) {
        }
    }

    private String getEnglishTranslation(String key) {
        try {
            Language englishLang = Language.getInstance();
            return englishLang.get(key, key);
        } catch (Exception e) {
            return key;
        }
    }


    private static void sendMsgToChat(String msg) {
        if (!debug) return;
        if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().inGameHud != null) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.of(msg));
        }
    }
}
