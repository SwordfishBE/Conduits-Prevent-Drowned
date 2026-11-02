package net.conduits_prevent_drowned;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Must remain loadable without any Cloth Config classes. */
public final class ConduitsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> notice(parent, "Install Cloth Config for Minecraft 26.4 to edit the configuration here. "
                    + "You can also edit config/conduits_prevent_drowned.json and use /conduits_prevent_drowned reload.");
        }
        if (!hasCompatibleClothConfig()) {
            return parent -> notice(parent, "This configuration menu requires Cloth Config for Minecraft 26.4. "
                    + "The installed older or incompatible version cannot display it safely. "
                    + "Until a compatible release is available, edit config/conduits_prevent_drowned.json "
                    + "and use /conduits_prevent_drowned reload.");
        }
        return ConduitsConfigScreen::create;
    }

    static boolean hasCompatibleClothConfig() {
        var loader = FabricLoader.getInstance();
        var cloth = loader.getModContainer("cloth-config");
        if (cloth.isEmpty()) return false;
        try {
            if (!VersionPredicate.parse(">=26.4-").test(cloth.get().getMetadata().getVersion())) return false;
            Version minecraft = loader.getModContainer("minecraft").orElseThrow().getMetadata().getVersion();
            return cloth.get().getMetadata().getDependencies().stream()
                    .filter(dependency -> dependency.getModId().equals("minecraft"))
                    .allMatch(dependency -> dependency.matches(minecraft));
        } catch (net.fabricmc.loader.api.VersionParsingException exception) { return false; }
    }

    static Screen notice(Screen parent, String message) {
        return new AlertScreen(() -> Minecraft.getInstance().setScreenAndShow(parent),
                Component.literal("Conduits Prevent Drowned"),
                Component.literal(ConduitsPreventDrowned.PREFIX + message));
    }
}
