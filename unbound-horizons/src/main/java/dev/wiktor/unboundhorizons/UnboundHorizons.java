package dev.wiktor.unboundhorizons;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UnboundHorizons implements ModInitializer {
    public static final String MOD_ID = "unboundhorizons";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOG.info("Unbound Horizons initialised. Dimension Y range: -512 to 1535. LOD caches previously loaded terrain.");
    }
}
