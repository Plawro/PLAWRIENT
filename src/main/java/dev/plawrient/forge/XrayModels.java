package dev.plawrient.forge;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelEvent;

final class XrayModels {
    private XrayModels() {}

    /** Mod bus: po nacteni modelu obali modely bloku (ne itemu) do XrayModel. */
    static void onModify(ModelEvent.ModifyBakingResult e) {
        e.getModels().replaceAll((key, model) -> {
            if (key instanceof ModelResourceLocation && !key.toString().endsWith("#inventory")
                    && !(model instanceof XrayModel)) {
                return new XrayModel(model);
            }
            return model;
        });
    }
}
