package dev.plawrient.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("plawrient")
public class Plawrient {
    public Plawrient() {
        // client-only: na dedicated serveru se nic nenacte
        if (FMLEnvironment.dist == Dist.CLIENT) ClientInit.init();
    }
}
