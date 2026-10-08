package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Tools: while you mine, picks the fastest tool (hotbar, optionally swaps one in from the inventory) and
 * switches back afterwards. Weapons: when you aim at a hostile, shows (and optionally equips) the best weapon.
 */
public class AutoTool extends Module {
    public final BoolSetting tools = add(new BoolSetting("tools", true));
    public final BoolSetting fromInventory = add(new BoolSetting("fromInventory", true));
    public final BoolSetting restore = add(new BoolSetting("restore", true));
    public final BoolSetting weaponHint = add(new BoolSetting("weaponHint", true));
    public final BoolSetting autoWeapon = add(new BoolSetting("autoWeapon", false));
    public final BoolSetting ranged = add(new BoolSetting("ranged", true));                    // suggest bow / crossbow for far targets
    public final NumberSetting rangedFrom = add(new NumberSetting("rangedFrom", 8, 4, 30));    // blocks
    public final NumberSetting hintSeconds = add(new NumberSetting("hintSeconds", 6, 1, 30));  // how long the hint stays up

    private int prevSlot = -1, swapInv = -1, swapHot = -1, idle, cooldown;
    private ItemStack hintStack = ItemStack.EMPTY;
    private int hintSlot = -1;
    private double hintDmg, hintDps, hintDist;
    private boolean hintRanged;
    private long hintUntil;
    private int lastSel = -1, manualUntil;

    public AutoTool() { super("AutoTool", "Best tool while mining, best weapon vs. mobs", Category.PLAYER); }

    @Override public void onDisable() { hintStack = ItemStack.EMPTY; }

    // ---------- tools ----------
    private static float speed(ItemStack s, BlockState st) {
        if (s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= 1) return 0f;
        float v = s.getDestroySpeed(st);
        if (v > 1f) {
            int eff = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, s);
            if (eff > 0) v += eff * eff + 1;
        }
        if (st.requiresCorrectToolForDrops() && !s.isCorrectToolForDrops(st)) v = Math.min(v, 0.99f);
        return v;
    }

    private void pickTool(Minecraft mc, LocalPlayer p, Inventory inv, BlockState st) {
        int cur = inv.selected;
        float bestS = speed(inv.getItem(cur), st);
        int bestHot = cur, bestInv = -1;
        for (int i = 0; i < 9; i++) {
            float s = speed(inv.getItem(i), st);
            if (s > bestS + 0.01f) { bestS = s; bestHot = i; bestInv = -1; }
        }
        if (fromInventory.get()) {
            for (int i = 9; i < 36; i++) {
                float s = speed(inv.getItem(i), st);
                if (s > bestS + 0.01f) { bestS = s; bestInv = i; bestHot = cur; }
            }
        }
        if (bestInv >= 0) {
            mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, bestInv, cur, ClickType.SWAP, p);
            swapInv = bestInv; swapHot = cur; cooldown = 8;
        } else if (bestHot != cur) {
            if (prevSlot < 0) prevSlot = cur;
            inv.selected = bestHot;
            cooldown = 2;
        }
    }

    private void restoreTool(Minecraft mc, LocalPlayer p, Inventory inv) {
        if (swapInv >= 0) {
            mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, swapInv, swapHot, ClickType.SWAP, p);
            swapInv = -1; swapHot = -1;
        }
        if (prevSlot >= 0) { inv.selected = prevSlot; prevSlot = -1; }
    }

    // ---------- weapons ----------
    private static double damage(ItemStack s, LivingEntity t) {
        double dmg = 1.0;
        if (s.isEmpty()) return dmg;
        var dm = s.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE);
        if (dm.isEmpty()) return dmg;
        for (AttributeModifier m : dm) if (m.getOperation() == AttributeModifier.Operation.ADDITION) dmg += m.getAmount();
        return dmg + EnchantmentHelper.getDamageBonus(s, t.getMobType());
    }

    private static double score(ItemStack s, LivingEntity t) {
        double dmg = damage(s, t), spd = 4.0;
        if (!s.isEmpty()) {
            var sm = s.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED);
            for (AttributeModifier m : sm) if (m.getOperation() == AttributeModifier.Operation.ADDITION) spd += m.getAmount();
        }
        return dmg * Math.max(0.2, spd);
    }

    private static boolean isRanged(ItemStack s) { return s.is(Items.BOW) || s.is(Items.CROSSBOW); }

    private static boolean usable(LocalPlayer p, ItemStack s) {
        if (s.is(Items.BOW)) return p.getAbilities().instabuild || !p.getProjectile(s).isEmpty();
        if (s.is(Items.CROSSBOW)) return CrossbowItem.isCharged(s) || p.getAbilities().instabuild || !p.getProjectile(s).isEmpty();
        return false;
    }

    private void clearHint() { hintStack = ItemStack.EMPTY; hintSlot = -1; }

    private void updateWeapon(Minecraft mc, LocalPlayer p, Inventory inv) {
        if (!weaponHint.get() && !autoWeapon.get()) { clearHint(); return; }
        long now = System.currentTimeMillis();
        LivingEntity t = Targeting.held(30);            // your pinged target if you set one (TargetPing), else what you aim at
        int found = -1;
        boolean foundRanged = false;
        double foundScore = 0, foundDist = 0;

        if (t != null && !EntityFilter.isFake(t)) {
            boolean pinned = t == Targeting.pinned();
            boolean hostile = pinned || t instanceof Enemy || t instanceof Player || (t instanceof NeutralMob nm && nm.isAngry());
            if (hostile) {
                ItemStack cur = inv.getItem(inv.selected);
                double dist = Math.sqrt(p.distanceToSqr(t));
                boolean far = ranged.get() && dist >= rangedFrom.get();
                boolean holdingRanged = isRanged(cur);
                if (far) {
                    // far target: the right thing is a bow / crossbow - never push a sword on you here
                    if (!(holdingRanged && usable(p, cur))) {
                        int bi = -1, bs = 0;
                        for (int i = 0; i < 36; i++) {
                            ItemStack s = inv.getItem(i);
                            if (i == inv.selected || !usable(p, s)) continue;
                            int sc = s.is(Items.BOW) ? 2 : 1;
                            if (sc > bs) { bs = sc; bi = i; }
                        }
                        if (bi >= 0) { found = bi; foundRanged = true; foundDist = dist; }
                    }
                } else if (!(holdingRanged && dist >= 3.5)) {   // holding a bow while he is not on top of you = your choice
                    double best = score(cur, t);
                    for (int i = 0; i < 36; i++) {
                        if (i == inv.selected) continue;
                        double sc = score(inv.getItem(i), t);
                        if (sc > best + 0.5) { best = sc; found = i; }
                    }
                    foundScore = best;
                    foundDist = dist;
                }
            }
        }

        if (found >= 0) {
            hintStack = inv.getItem(found);
            hintSlot = found;
            hintRanged = foundRanged;
            hintDist = foundDist;
            hintDps = foundScore;
            hintDmg = foundRanged ? 0 : damage(hintStack, t);
            hintUntil = now + (long) (hintSeconds.get() * 1000);     // stays up a while after you look away
        } else if (!hintStack.isEmpty()) {
            boolean equipped = ItemStack.isSameItemSameTags(inv.getItem(inv.selected), hintStack);
            if (equipped || now > hintUntil) clearHint();
        }

        // auto-equip only when you did not just pick a slot yourself, and never while drawing / eating / blocking
        if (autoWeapon.get() && found >= 0 && cooldown == 0 && mc.screen == null
                && p.tickCount >= manualUntil && !p.isUsingItem()) {
            if (found < 9) { inv.selected = found; cooldown = 4; }
            else if (fromInventory.get()) {
                mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, found, inv.selected, ClickType.SWAP, p);
                cooldown = 8;
            }
        }
    }

    @Override public void onTick() {
        Minecraft mc = mc();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) { hintStack = ItemStack.EMPTY; return; }
        if (cooldown > 0) cooldown--;
        Inventory inv = p.getInventory();
        if (lastSel >= 0 && inv.selected != lastSel) manualUntil = p.tickCount + 100;   // you (or another module) changed slot: hands off for 5 s
        if (mc.screen != null) { lastSel = inv.selected; return; }

        BlockHitResult bhr = (mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) ? b : null;
        boolean mining = tools.get() && bhr != null && mc.options.keyAttack.isDown() && !p.getAbilities().instabuild;
        if (mining) {
            idle = 0;
            if (cooldown == 0) pickTool(mc, p, inv, mc.level.getBlockState(bhr.getBlockPos()));
        } else if ((prevSlot >= 0 || swapInv >= 0) && restore.get()) {
            if (++idle > 10) { restoreTool(mc, p, inv); idle = 0; }
        }
        updateWeapon(mc, p, inv);
        lastSel = inv.selected;
    }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        if (hintStack.isEmpty() || mc.screen != null || mc.options.hideGui || !weaponHint.get()) return;
        String where = hintSlot < 9 ? "hotbar slot " + (hintSlot + 1) : "inventory (swap it in)";
        String l1 = "Switch to: " + hintStack.getHoverName().getString() + (hintRanged ? "  (ranged)" : "");
        String l2 = hintRanged
                ? String.format(java.util.Locale.ROOT, "target %.0fm away  -  %s", hintDist, where)
                : String.format(java.util.Locale.ROOT, "%.1f dmg  (%.1f dps)  -  %s", hintDmg, hintDps, where);
        int w = Math.max(Draw.width(l1), Draw.width(l2)) + 34;
        int x = (g.guiWidth() - w) / 2, y = g.guiHeight() - 92;
        Draw.rrect(g, x, y, w, 26, 6, Draw.bg(0.8));
        Draw.rrect(g, x + 4, y + 23, w - 8, 1, 0, 0xFFFF9F1A);
        g.renderItem(hintStack, x + 6, y + 5);
        Draw.text(g, l1, x + 28, y + 4, Draw.TEXT);
        Draw.text(g, l2, x + 28, y + 14, Draw.TEXT_DIM);
    }
}
