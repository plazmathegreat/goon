package com.example.maceswap;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaceSwapMod implements ModInitializer {
    public static final String MOD_ID = "maceswap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Mace Elytra Swap initialized.");
    }
}
