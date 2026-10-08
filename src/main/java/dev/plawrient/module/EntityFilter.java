package dev.plawrient.module;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

public final class EntityFilter {
    private EntityFilter() {}

    public static final int NONE = 0, PLAYER = 1, HOSTILE = 2, ANIMAL = 3, ITEM = 4;

    public static boolean isFake(Entity e) { return e instanceof Herobrine.Fake; }

    public static int kind(Entity e) {
        if (isFake(e)) return NONE;
        if (e instanceof Player) return PLAYER;
        if (e instanceof Enemy) return HOSTILE;
        if (e instanceof Animal || e instanceof WaterAnimal || e instanceof AmbientCreature) return ANIMAL;
        if (e instanceof ItemEntity) return ITEM;
        return NONE;
    }

    public static int color(int kind) {
        return switch (kind) {
            case PLAYER -> 0xFFFF4D4D;
            case HOSTILE -> 0xFFFF9F1A;
            case ANIMAL -> 0xFF55FF7A;
            case ITEM -> 0xFFFFE14D;
            default -> 0xFFFFFFFF;
        };
    }
}
