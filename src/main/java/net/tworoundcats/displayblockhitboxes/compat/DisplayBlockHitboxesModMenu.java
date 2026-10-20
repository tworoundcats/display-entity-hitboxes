package net.tworoundcats.displayblockhitboxes.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

public class DisplayBlockHitboxesModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return ClothConfigScreenProvider.getFactory();
        }

        return parent -> null;
    }

    private static class ClothConfigScreenProvider {
        private static ConfigScreenFactory<?> getFactory() {
            return net.tworoundcats.displayblockhitboxes.gui.DisplayHitboxesConfigScreen::buildScreen;
        }
    }
}