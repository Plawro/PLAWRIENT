package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "MLG" water bucket clutch. When you fall far enough to take real damage it takes a water bucket (hotbar or
 * inventory), looks straight down, places the water at the last moment (as soon as the ground is within reach),
 * and after landing picks the water up again and puts the bucket back where it came from.
 */
public class WaterClutch extends Module {
    private static WaterClutch INSTANCE;

    public final NumberSetting minDamage = add(new NumberSetting("minDamage", 3, 1, 20));        // half hearts
    public final NumberSetting placeHeight = add(new NumberSetting("placeHeight", 2.8, 1.5, 2.88)); // blocks above ground
    public final BoolSetting fromInventory = add(new BoolSetting("fromInventory", true));
    public final BoolSetting pickUp = add(new BoolSetting("pickUp", true));
    public final BoolSetting message = add(new BoolSetting("message", true));

    private static final double SCAN = 40, ARM_DIST = 28;

    private enum S { IDLE, ARMED, PLACED, PICKUP }

    private record Ground(double dist, double damage, boolean safe) {}

    private S state = S.IDLE;
    private int prevSlot, swapInv = -1, swapHot = -1, ticks, tries, cooldown;
    private float prevPitch;
    private boolean announced;

    public WaterClutch() {
        super("WaterClutch", "Places a water bucket when you fall, picks it up after", Category.PLAYER);
        INSTANCE = this;
    }

    /** True while a clutch is in progress (AutoWater stays out of the way). */
    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.state != S.IDLE; }

    @Override public void onDisable() { abort(); }

    // ---------- helpers ----------

    private boolean falling(LocalPlayer p) {
        if (p.verticalCollisionBelow || p.isInWater() || p.isInLava() || p.isFallFlying() || p.isPassenger() || p.onClimbable()
                || p.isSpectator() || p.isSleeping() || p.getAbilities().flying || p.getAbilities().instabuild) return false;
        if (p.hasEffect(MobEffects.SLOW_FALLING) || p.hasEffect(MobEffects.LEVITATION)) return false;
        return p.getDeltaMovement().y < -0.4 && p.fallDistance > 1.5f;
    }

    /** What is below us: distance to the ground, predicted fall damage and whether the landing is already safe. */
    private Ground scan(Minecraft mc, LocalPlayer p) {
        var level = mc.level;
        Vec3 from = new Vec3(p.getX(), p.getY() + 0.01, p.getZ());
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(0, -SCAN, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        if (hit.getType() == HitResult.Type.MISS) return null;

        double d = Math.max(0, p.getY() - hit.getLocation().y);
        BlockPos pos = hit.getBlockPos();
        BlockState st = level.getBlockState(pos);
        // water / lava / slime / hay / honey below: nothing to clutch (lava is not handled)
        boolean safe = !level.getFluidState(pos).isEmpty() || st.is(Blocks.SLIME_BLOCK) || st.is(Blocks.HAY_BLOCK)
                || st.is(Blocks.HONEY_BLOCK) || st.is(Blocks.POWDER_SNOW);
        MobEffectInstance jb = p.getEffect(MobEffects.JUMP);
        int jump = jb != null ? jb.getAmplifier() + 1 : 0;
        double dmg = Math.ceil(p.fallDistance + d - 3.0 - jump);
        return new Ground(d, dmg, safe);
    }

    private int find(Inventory inv) {
        if (inv.getItem(inv.selected).is(Items.WATER_BUCKET)) return inv.selected;
        for (int i = 0; i < 9; i++) if (inv.getItem(i).is(Items.WATER_BUCKET)) return i;
        if (fromInventory.get())
            for (int i = 9; i < 36; i++) if (inv.getItem(i).is(Items.WATER_BUCKET)) return i;
        return -1;
    }

    private void aimDown(LocalPlayer p) { p.setXRot(90f); p.xRotO = 90f; }

    private void restore(Minecraft mc, LocalPlayer p) {
        p.setXRot(prevPitch);
        p.xRotO = prevPitch;
        if (swapInv >= 0 && mc.gameMode != null)
            mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, swapInv, swapHot, ClickType.SWAP, p);
        p.getInventory().selected = prevSlot;
    }

    private void reset() { state = S.IDLE; swapInv = -1; swapHot = -1; ticks = 0; tries = 0; announced = false; }

    private void abort() {
        Minecraft mc = mc();
        if (state != S.IDLE && mc.player != null) restore(mc, mc.player);
        reset();
    }

    private void finish(Minecraft mc, LocalPlayer p) {
        restore(mc, p);
        reset();
        cooldown = 20;
    }

    private void use(Minecraft mc, LocalPlayer p) { mc.gameMode.useItem(p, InteractionHand.MAIN_HAND); }

    // ---------- main loop ----------

    @Override public void onTick() {
        Minecraft mc = mc();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) { reset(); return; }
        if (cooldown > 0) cooldown--;
        Inventory inv = p.getInventory();

        if (state == S.IDLE) {
            if (cooldown > 0 || mc.screen != null || Freecam.active()) return;
            if (AutoWater.INSTANCE != null && AutoWater.INSTANCE.busy()) return;
            if (mc.level.dimensionType().ultraWarm() || !falling(p)) return;
            Ground g = scan(mc, p);
            if (g == null || g.safe() || g.damage() < minDamage.get() || g.dist() > ARM_DIST) return;
            int slot = find(inv);
            if (slot < 0) return;

            prevSlot = inv.selected;
            prevPitch = p.getXRot();
            ticks = 0; tries = 0; announced = false;
            if (slot < 9) inv.selected = slot;
            else {
                swapHot = inv.selected; swapInv = slot;
                mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, slot, inv.selected, ClickType.SWAP, p);
            }
            aimDown(p);            // the server learns the new pitch with the next movement packet
            state = S.ARMED;
            return;
        }

        if (mc.screen != null || p.isDeadOrDying()) { abort(); return; }
        aimDown(p);
        ticks++;

        switch (state) {
            case ARMED -> {
                if (p.verticalCollisionBelow || p.isInWater() || p.isInLava()) { finish(mc, p); return; }
                Ground g = scan(mc, p);
                if (g == null) { if (ticks > 200) abort(); return; }
                if (g.safe() || !p.getMainHandItem().is(Items.WATER_BUCKET)) { abort(); return; }
                if (ticks >= 1 && g.dist() <= placeHeight.get()) {
                    use(mc, p);
                    state = S.PLACED; ticks = 0; tries = 1;
                }
            }
            case PLACED -> {
                boolean emptied = p.getMainHandItem().is(Items.BUCKET);
                if (emptied && !announced) { announced = true; if (message.get()) Chat.send("Water clutch!"); }
                boolean landed = p.verticalCollisionBelow || p.isInWater();
                if (landed || ticks > 40) {
                    if (emptied && pickUp.get()) { state = S.PICKUP; ticks = 0; }
                    else {
                        if (!emptied && message.get()) Chat.send("Water clutch failed - could not place the water.");
                        finish(mc, p);
                    }
                    return;
                }
                if (!emptied && ticks % 2 == 0 && tries < 4) {      // placement did not register - try again
                    Ground g = scan(mc, p);
                    if (g != null && g.dist() <= placeHeight.get() + 0.05) { use(mc, p); tries++; }
                }
            }
            case PICKUP -> {
                if (p.getMainHandItem().is(Items.WATER_BUCKET)) { if (ticks >= 2) finish(mc, p); return; }
                if (!p.getMainHandItem().is(Items.BUCKET)) { finish(mc, p); return; }
                if (ticks > 40) {
                    if (message.get()) Chat.send("Could not pick the water back up.");
                    finish(mc, p);
                    return;
                }
                if (ticks >= 3 && ticks % 3 == 0) use(mc, p);       // empty bucket on the water source below you
            }
            default -> {}
        }
    }
}
