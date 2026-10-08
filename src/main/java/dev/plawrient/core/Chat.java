package dev.plawrient.core;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public final class Chat {
    private Chat() {}

    private static MutableComponent prefix() {
        return Component.literal("[PLAWRIENT] ").withStyle(ChatFormatting.AQUA);
    }

    public static void send(String msg) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        p.displayClientMessage(prefix().append(Component.literal(msg).withStyle(ChatFormatting.WHITE)), false);
    }

    /** Message that copies `clipboard` when clicked. */
    public static void copyable(String msg, String clipboard) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        Component body = Component.literal(msg).withStyle(s -> s.withColor(ChatFormatting.WHITE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, clipboard))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy"))));
        p.displayClientMessage(prefix().append(body), false);
    }
}
