package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Random client-side scares: footsteps approaching from behind, distant doors, cave noises and whispers. */
public class Haunting extends Module {
    public final BoolSetting footsteps = add(new BoolSetting("footsteps", true));
    public final BoolSetting doors = add(new BoolSetting("doors", true));
    public final BoolSetting ambience = add(new BoolSetting("ambience", true));
    public final BoolSetting whispers = add(new BoolSetting("whispers", true));
    public final NumberSetting frequency = add(new NumberSetting("frequency", 75, 10, 600));
    public final NumberSetting volume = add(new NumberSetting("volume", 0.8, 0.1, 1.0));

    private final Random rnd = new Random();
    private int cooldown, stepsLeft, stepDelay;
    private double stepDist;
    private int closeDoorIn = -1;
    private double doorX, doorY, doorZ;

    public Haunting() { super("Haunting", "Random creepy sounds", Category.FUN); }

    @Override public void onEnable() { cooldown = 20 * 15; stepsLeft = 0; closeDoorIn = -1; }

    private void play(net.minecraft.sounds.SoundEvent ev, double x, double y, double z, SoundSource src, float vol, float pitch) {
        var level = mc().level;
        if (level != null) level.playLocalSound(x, y, z, ev, src, vol, pitch, false);
    }

    @Override public void onTick() {
        Minecraft mc = mc();
        var level = mc.level;
        var p = mc.player;
        if (level == null || p == null) return;
        float vol = volume.get().floatValue();

        if (closeDoorIn >= 0 && --closeDoorIn < 0)
            play(Snd.s(SoundEvents.WOODEN_DOOR_CLOSE), doorX, doorY, doorZ, SoundSource.BLOCKS, vol, 0.9f);

        if (stepsLeft > 0) {
            if (--stepDelay <= 0) {
                Vec3 look = p.getLookAngle();
                double bx = -look.x, bz = -look.z, len = Math.max(1e-4, Math.hypot(bx, bz));
                bx /= len; bz /= len;
                double x = p.getX() + bx * stepDist + (rnd.nextDouble() - 0.5) * 0.6;
                double z = p.getZ() + bz * stepDist + (rnd.nextDouble() - 0.5) * 0.6;
                BlockState under = level.getBlockState(BlockPos.containing(x, p.getY() - 0.5, z));
                SoundType st = under.isAir() ? SoundType.GRASS : under.getSoundType();
                play(st.getStepSound(), x, p.getY(), z, SoundSource.PLAYERS, st.getVolume() * 0.7f * vol, st.getPitch() * 0.85f);
                stepsLeft--;
                stepDist = Math.max(2.5, stepDist - 0.8);
                stepDelay = 7 + rnd.nextInt(3);
            }
            return;
        }

        if (--cooldown > 0) return;
        cooldown = (int) (frequency.get() * 20 * (0.5 + rnd.nextDouble()));

        List<Integer> options = new ArrayList<>();
        if (footsteps.get()) options.add(0);
        if (doors.get()) options.add(1);
        if (ambience.get()) options.add(2);
        if (whispers.get()) options.add(3);
        if (options.isEmpty()) return;

        double ang = rnd.nextDouble() * Math.PI * 2;
        switch (options.get(rnd.nextInt(options.size()))) {
            case 0 -> { stepsLeft = 7 + rnd.nextInt(3); stepDist = 8; stepDelay = 1; }
            case 1 -> {
                double d = 12 + rnd.nextDouble() * 12;
                doorX = p.getX() + Math.cos(ang) * d; doorY = p.getY(); doorZ = p.getZ() + Math.sin(ang) * d;
                play(Snd.s(SoundEvents.WOODEN_DOOR_OPEN), doorX, doorY, doorZ, SoundSource.BLOCKS, vol, 0.9f);
                closeDoorIn = 10 + rnd.nextInt(25);
            }
            case 2 -> play(Snd.s(SoundEvents.AMBIENT_CAVE), p.getX() + Math.cos(ang) * 6, p.getY(), p.getZ() + Math.sin(ang) * 6,
                    SoundSource.AMBIENT, vol, 0.7f + rnd.nextFloat() * 0.3f);
            default -> play(Snd.s(SoundEvents.ENDERMAN_AMBIENT), p.getX() + Math.cos(ang) * 8, p.getY() + 1, p.getZ() + Math.sin(ang) * 8,
                    SoundSource.AMBIENT, 0.35f * vol, 0.5f);
        }
    }
}
