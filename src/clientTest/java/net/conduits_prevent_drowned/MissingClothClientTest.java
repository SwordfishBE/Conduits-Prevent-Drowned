package net.conduits_prevent_drowned;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/** This entrypoint must never reference optional Cloth classes. */
public class MissingClothClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cloth-config"))
                throw new AssertionError("This run requires Cloth Config to be absent");
            var parent = new TitleScreen();
            var screen = new ConduitsModMenu().getModConfigScreenFactory().create(parent);
            if (!(screen instanceof AlertScreen)) throw new AssertionError("Missing Cloth must show a notice");
            client.setScreenAndShow(screen);
        });
        context.waitTicks(3);
        context.takeScreenshot("conduits-missing-cloth");
        context.setScreen(TitleScreen::new);
    }
}
