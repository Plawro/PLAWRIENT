package dev.plawrient.module;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/** SoundEvents jsou v nekterych verzich Holder, v jinych primo SoundEvent - tohle zvladne obojí. */
final class Snd {
    private Snd() {}
    static SoundEvent s(Holder<SoundEvent> h) { return h.value(); }
    static SoundEvent s(SoundEvent e) { return e; }
}
