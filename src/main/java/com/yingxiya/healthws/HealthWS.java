package com.yingxiya.healthws;

import com.yingxiya.healthws.client.HealthWSClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(HealthWS.MOD_ID)
public class HealthWS {
    public static final String MOD_ID = "healthws";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @SuppressWarnings("deprecation") // 仅抑制IDE废弃警告，1.20.1无运行问题
    public HealthWS() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::clientInit);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void clientInit(FMLClientSetupEvent event) {
        event.enqueueWork(HealthWSClient::registerTick);
    }
}