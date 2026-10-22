package net.conduits_prevent_drowned;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConduitsPreventDrowned implements ModInitializer {
    public static final String ID = "conduits_prevent_drowned";
    public static final String PREFIX = "[Conduits Prevent Drowned] ";
    private static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static final ConfigStore CONFIG = new ConfigStore(
            FabricLoader.getInstance().getConfigDir().resolve(ID + ".json"));

    @Override
    public void onInitialize() {
        try { CONFIG.reload(); }
        catch (Exception e) { LOGGER.warn(PREFIX + "Could not load config; using range {}. File preserved.", CONFIG.range(), e); }
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(Commands.literal(ID)
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("reload").executes(context -> {
                            try {
                                CONFIG.reload();
                                context.getSource().sendSuccess(() -> Component.literal(PREFIX
                                        + "Config reloaded. preventDrownedInRange: " + CONFIG.range()), false);
                                return 1;
                            } catch (Exception e) {
                                LOGGER.warn(PREFIX + "Could not reload config; keeping range {}. File preserved.", CONFIG.range(), e);
                                context.getSource().sendFailure(Component.literal(PREFIX
                                        + "Config reload failed. Previous settings kept; check the server log."));
                                return 0;
                            }
                        }))));
        String version = FabricLoader.getInstance().getModContainer(ID).orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
        LOGGER.info(PREFIX + "Mod initialized. Version: {}", version);
    }
}
