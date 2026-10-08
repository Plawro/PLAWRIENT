package dev.plawrient.forge;

import dev.plawrient.core.ModuleManager;
import dev.plawrient.core.Platform;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

final class ClientInit {
    private ClientInit() {}

    static void init() {
        Platform.modName = ns -> ModList.get().getModContainerById(ns)
                .map(c -> c.getModInfo().getDisplayName()).orElse(ns);
        ModuleManager.INSTANCE.init(FMLPaths.CONFIGDIR.get());
        MinecraftForge.EVENT_BUS.register(new ForgeEvents());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(XrayModels::onModify);
    }
}
