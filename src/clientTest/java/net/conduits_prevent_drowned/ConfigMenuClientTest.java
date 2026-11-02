package net.conduits_prevent_drowned;

import java.nio.file.Files;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.IntegerListEntry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.TitleScreen;

public class ConfigMenuClientTest implements FabricClientGameTest {
    private ClothConfigScreen editor;

    private void open(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = new ConduitsModMenu().getModConfigScreenFactory().create(new TitleScreen());
            if (!ConduitsModMenu.hasCompatibleClothConfig()) {
                if (!(screen instanceof AlertScreen)) throw new AssertionError("Old Cloth must show a safe warning");
                client.setScreenAndShow(screen);
                // Prepare and test the future editor without rendering incompatible Cloth widgets.
                editor = (ClothConfigScreen) ConduitsConfigScreen.create(new TitleScreen());
            } else {
                if (!(screen instanceof ClothConfigScreen)) throw new AssertionError("Expected Cloth editor");
                editor = (ClothConfigScreen) screen;
                client.setScreenAndShow(editor);
            }
        });
        context.waitTicks(3);
    }

    private IntegerListEntry entry() {
        return (IntegerListEntry) editor.getCategorizedEntries().values().iterator().next().getFirst();
    }

    private void save(ClientGameTestContext context, int range) {
        open(context);
        context.runOnClient(client -> {
            entry().setValue(Integer.toString(range));
            editor.saveAll(true);
        });
        context.waitTicks(5);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("conduits_prevent_drowned.json");
        final String original;
        try { original = Files.readString(path); }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
        try {
            open(context);
            context.takeScreenshot("conduits-cloth-compatibility-notice");
            context.runOnClient(client -> {
                for (int invalid : new int[] {-1, 401}) {
                    entry().setValue(Integer.toString(invalid));
                    if (entry().getConfigError().isEmpty()) throw new AssertionError("Invalid range must show validation");
                }
                entry().setValue("77");
            });
            context.setScreen(TitleScreen::new);
            context.runOnClient(client -> {
                try {
                    if (!Files.readString(path).equals(original)) throw new AssertionError("Cancelled edits were saved");
                } catch (java.io.IOException e) { throw new RuntimeException(e); }
            });
            save(context, 32);
            context.setScreen(TitleScreen::new);
            try (var world = context.worldBuilder().create()) {
                world.getServer().runOnServer(server -> {
                    if (ConduitsPreventDrowned.CONFIG.range() != 32) throw new AssertionError("Title save must apply to next world");
                });
                save(context, 0);
                world.getServer().runOnServer(server -> {
                    if (ConduitsPreventDrowned.CONFIG.range() != 0) throw new AssertionError("Integrated save must apply immediately");
                });
                save(context, 400);
                world.getServer().runOnServer(server -> {
                    if (ConduitsPreventDrowned.CONFIG.range() != 400) throw new AssertionError("Default range must restore immediately");
                });
                context.setScreen(TitleScreen::new);
            }
        } finally {
            context.runOnClient(client -> {
                try {
                    Files.writeString(path, original);
                    ConduitsPreventDrowned.CONFIG.reload();
                } catch (java.io.IOException e) { throw new RuntimeException(e); }
                client.setScreenAndShow(new TitleScreen());
            });
        }
    }
}
