package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * When it looks like you are about to die (low health, a big burst of damage, or a lethal fall) and you carry a
 * Totem of Undying anywhere in your inventory, it is moved into your offhand (or main hand) in the same tick.
 * When the danger is over the item that was in your hand goes back (if the totem was not used up).
 */
public class AutoTotem extends Module {
    public final NumberSetting health = add(new NumberSetting("health", 8, 2, 20));            // HP incl. absorption
    public final NumberSetting burstDamage = add(new NumberSetting("burstDamage", 6, 2, 20));  // HP lost within 1 second
    public final BoolSetting lethalFall = add(new BoolSetting("lethalFall", true));
    public final ChoiceSetting hand = add(new ChoiceSetting("hand", "Offhand", List.of("Offhand", "Mainhand")));
    public final BoolSetting alwaysHold = add(new BoolSetting("alwaysHold", false));
    public final BoolSetting restore = add(new BoolSetting("restore", true));
    public final BoolSetting fromInventory = add(new BoolSetting("fromInventory", true));
    public final BoolSetting message = add(new BoolSetting("message", true));

    private final float[] hist = new float[20];
    private int hi, cooldown, safeTicks, swapMenu = -1, swapButton;

    public AutoTotem() { super("AutoTotem", "Grabs a Totem of Undying when you are about to die", Category.PLAYER); }

    @Override public void onDisable() { swapMenu = -1; safeTicks = 0; }

    private static boolean totem(net.minecraft.world.item.ItemStack s) { return s.is(Items.TOTEM_OF_UNDYING); }

    /** Menu slot index (player inventory container) of inventory index i. */
    private static int menuSlot(int i) { return i < 9 ? 36 + i : i; }

    private int findTotem(Inventory inv) {
        for (int i = 0; i < 9; i++) if (totem(inv.getItem(i))) return i;
        if (fromInventory.get()) for (int i = 9; i < 36; i++) if (totem(inv.getItem(i))) return i;
        return -1;
    }

    private boolean lethalFall(Minecraft mc, LocalPlayer p, float hp) {
        if (p.verticalCollisionBelow || p.isInWater() || p.isFallFlying() || p.getAbilities().flying) return false;
        if (p.getDeltaMovement().y > -0.3 || p.fallDistance < 2f || p.hasEffect(MobEffects.SLOW_FALLING)) return false;
        Vec3 from = new Vec3(p.getX(), p.getY() + 0.01, p.getZ());
        BlockHitResult hit = mc.level.clip(new ClipContext(from, from.add(0, -60, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        if (hit.getType() == HitResult.Type.MISS || !mc.level.getFluidState(hit.getBlockPos()).isEmpty()) return false;
        return Math.ceil(p.fallDistance + (p.getY() - hit.getLocation().y) - 3.0) >= hp;
    }

    @Override public void onTick() {
        Minecraft mc = mc();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) { swapMenu = -1; return; }

        float hp = p.getHealth() + p.getAbsorptionAmount();
        hist[hi] = hp;
        hi = (hi + 1) % hist.length;
        float peak = 0;
        for (float v : hist) peak = Math.max(peak, v);
        if (cooldown > 0) cooldown--;
        if (p.isSpectator() || p.getAbilities().instabuild || p.isDeadOrDying()) return;

        boolean danger = alwaysHold.get()
                || hp <= health.get()
                || (peak - hp >= burstDamage.get() && hp <= 14)
                || (p.isInLava() && hp <= 12)
                || (lethalFall.get() && lethalFall(mc, p, hp));
        boolean inHand = totem(p.getOffhandItem()) || totem(p.getMainHandItem());   // works from either hand
        boolean menuOk = p.containerMenu == p.inventoryMenu;                         // no chest etc. open

        if (danger) {
            safeTicks = 0;
            if (inHand || cooldown > 0 || !menuOk) return;
            Inventory inv = p.getInventory();
            int slot = findTotem(inv);
            if (slot < 0) return;
            boolean off = hand.get().equals("Offhand");
            int button = off ? 40 : inv.selected;                                    // 40 = offhand in a SWAP click
            mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, menuSlot(slot), button, ClickType.SWAP, p);
            swapMenu = menuSlot(slot);
            swapButton = button;
            cooldown = 3;
            if (message.get()) Chat.send("Totem equipped!");
            return;
        }

        // danger is over - put the old item back (only if the totem is still there, i.e. it was not used up)
        if (swapMenu >= 0) {
            if (!inHand) { swapMenu = -1; return; }
            if (++safeTicks >= 60 && restore.get() && menuOk && cooldown == 0) {
                mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, swapMenu, swapButton, ClickType.SWAP, p);
                swapMenu = -1;
                safeTicks = 0;
            }
        }
    }
}
