package net.conduits_prevent_drowned;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ConduitsConfigScreen {
    private ConduitsConfigScreen() {}

    static Screen create(Screen parent) {
        var minecraft = Minecraft.getInstance();
        var openedServer = minecraft.getSingleplayerServer();
        if (minecraft.getConnection() != null && openedServer == null) {
            return ConduitsModMenu.notice(parent, "Multiplayer uses the server configuration. "
                    + "Ask the server administrator to edit its config and use /conduits_prevent_drowned reload.");
        }
        final int initial;
        try { initial = ConduitsPreventDrowned.CONFIG.readForEditing(); }
        catch (java.io.IOException | RuntimeException e) {
            return ConduitsModMenu.notice(parent, "Cannot open the configuration. Existing file preserved. " + e.getMessage());
        }
        int[] draft = {initial};
        var builder = ConfigBuilder.create().setParentScreen(parent)
                .setTitle(Component.literal("Conduits Prevent Drowned Config"));
        builder.getOrCreateCategory(Component.literal("General")).addEntry(builder.entryBuilder()
                .startIntField(Component.literal("Prevention range (blocks)"), initial)
                .setDefaultValue(400).setMin(0).setMax(400)
                .setTooltip(Component.literal("Horizontal radius around players with Conduit Power. Height is ignored."),
                        Component.literal("0 disables prevention. Default: 400. Applies immediately in singleplayer."),
                        Component.literal("Saved globally for this Minecraft instance, across singleplayer worlds."))
                .setSaveConsumer(value -> draft[0] = value).build());
        builder.setSavingRunnable(() -> {
            if (minecraft.getSingleplayerServer() != openedServer
                    || (minecraft.getConnection() != null && openedServer == null)) {
                minecraft.schedule(() -> minecraft.setScreenAndShow(ConduitsModMenu.notice(parent,
                        "The world or server changed while editing. Settings were not saved. Reopen the menu.")));
                return;
            }
            int requestedRange = draft[0];
            Runnable save = () -> {
                String message;
                try {
                    ConduitsPreventDrowned.CONFIG.saveRange(requestedRange);
                    message = "Config saved. Prevention range: " + requestedRange + " blocks.";
                } catch (java.io.IOException | RuntimeException e) {
                    message = "Could not save the configuration. Previous settings kept. " + e.getMessage();
                }
                String feedback = message;
                // Delay feedback until Cloth has finished closing its screen.
                minecraft.schedule(() -> minecraft.setScreenAndShow(ConduitsModMenu.notice(parent, feedback)));
            };
            if (openedServer != null) openedServer.execute(save);
            else save.run();
        });
        return builder.build();
    }
}
