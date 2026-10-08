# PLAWRIENT

**A client-side utility and quality-of-life mod for Minecraft 1.20.1 (Forge).**
One in-game menu, 36 modules: ESP and HUD tools, smart inventory helpers, survival safety nets, a built-in music
player and a few horror-flavoured fun modules. Everything runs on your client only. Nothing is installed on or
sent to the server by the mod itself.

> **Status:** young and actively developed. Some modules were written without extensive in-game testing, so expect
> rough edges - please open an issue if something misbehaves.

---

## Contents

- [Features](#features)
- [Quick start](#quick-start)
- [The menu](#the-menu)
- [Chat commands](#chat-commands)
- [Music player and custom songs](#music-player-and-custom-songs)
- [Configuration and files](#configuration-and-files)
- [Building from source](#building-from-source)
- [Responsible use](#responsible-use)
- [Credits](#credits)
- [License](#license)

---

## Features

### Render

| Module | What it does |
|---|---|
| **EntityESP** | Lock-on brackets around players, hostiles, animals and items, with an HP bar (vertical or horizontal) and a pulsing highlight on your target. |
| **Nametags** | Replaces vanilla nametags: name, distance, armor, **HP as a number and a horizontal bar**, and gear for players. |
| **StorageESP** | Chests, barrels and shulkers through walls (boxes + chams). |
| **BlockESP** | Highlights blocks of your choice through walls. |
| **Xray** | Shows only the blocks you select; everything else turns invisible. |
| **Fullbright** | Always full brightness. |
| **Zoom** | Smooth, adjustable zoom (hold or toggle, scroll wheel changes the amount). |
| **Trajectory** | Predicts where arrows, tridents, potions, pearls and other projectiles land - red if it will hit you, green for your own. |
| **Fuse** | Countdown over primed TNT, TNT minecarts and hissing creepers, with a danger marker when you are inside the blast radius. |
| **Vision** | Tesla-style detection overlay: boxes with distance, speed and threat level, points of interest, a safe-path planner and a telemetry block. |
| **SwordBlock** | The 1.8 sword-blocking animation (visual only). |

### HUD

| Module | What it does |
|---|---|
| **Hud** | Custom logo text (any system font) and a module list. |
| **TargetHUD** | Panel for your target: 3D model, name, distance, armor, gear, effects and a horizontal HP bar. |
| **TargetPing** | Press **G** (rebindable) to mark your target. All target-aware modules then follow it; it gets an on-screen marker and an edge-of-screen arrow when it is behind you. |
| **ItemInfo** | Window for the item in your hand: durability bar with low-durability warning, **arrows left with a low-ammo warning** for bows and crossbows, item count, and the normal inventory tooltip. |
| **VitalsWarning** | Pulsing red edges + heartbeat at low health, chip and chime at low hunger. |
| **Awareness** | "Behind you" warning with a sound, direction arrows, and an eye icon over enemies that chase you or see you. |
| **DamageNumbers** | Floating damage / heal numbers. |
| **BlockInfo** | Info about the block you look at. |
| **LowFire** | A small fire overlay that does not cover the screen. |

### Player

| Module | What it does |
|---|---|
| **WaterClutch** | MLG water bucket: grabs a water bucket (hotbar or inventory) when a fall would hurt, looks down, places water at the last moment, then picks it up and puts the bucket back. |
| **AutoTotem** | When you are about to die (low health, a burst of damage, a lethal fall, lava) a Totem of Undying is moved into your offhand instantly; the old item is restored afterwards. |
| **AutoRefill** | When the item in your hand runs out or a tool breaks, the same item is refilled from your inventory (main hand and offhand). |
| **AutoTool** | Best tool while mining; best weapon against mobs - including **bow / crossbow for distant targets** - with a hint window (and optional auto-equip that never overrides your own slot choice). |
| **AutoArmor** | Replaces broken armor from your inventory. |
| **AutoWater** | Extinguishes you when on fire (water bucket, powder snow, fire resistance in the Nether). |
| **DeathInfo** | Death coordinates in chat plus a Death waypoint. |
| **Route** + **AddWaypoint** | Long-range path *display* (does not walk) to where you look or through your waypoints. |
| **Freecam** | Detached camera. |

### Movement

| Module | What it does |
|---|---|
| **Speed** | Bhop / strafe. |
| **InventoryMove** | Walk, jump and sprint while a screen is open. |

### Fun

| Module | What it does |
|---|---|
| **MusicSwitcher** | Pick exactly which music track plays. Powers the **Music** tab (see below). |
| **BloodMoon** | Red sky, blood-red fog, a big red moon and a music box on random nights. Plays your own recording if you supply one. |
| **Haunting** | Random creepy sounds: footsteps behind you, distant doors, cave noises, whispers. |
| **Herobrine** | A client-side "fake player" with white eyes that now and then appears in the distance and vanishes when you look at it. Exists only on your screen. |


<img width="2560" height="1377" alt="2026-10-08_13 06 22" src="https://github.com/user-attachments/assets/45dd2ac3-b3d8-4d96-8466-f37990e129f7" />
<img width="2560" height="1377" alt="2026-10-08_13 05 06" src="https://github.com/user-attachments/assets/006c7a2c-da34-417b-820d-f941ab9a4e6a" />
<img width="2560" height="1377" alt="2026-10-08_13 04 15" src="https://github.com/user-attachments/assets/a1fe39ed-b3e9-4008-8c26-c08667bc64da" />
<img width="2560" height="1377" alt="2026-10-08_13 04 05" src="https://github.com/user-attachments/assets/aab84f9c-62b0-4486-95d8-fec4d0bdcac0" />
<img width="2560" height="1377" alt="2026-10-08_13 03 54" src="https://github.com/user-attachments/assets/4e15cc5a-9168-4262-a588-bb1296f770ff" />
<img width="2560" height="1377" alt="2026-10-08_13 02 34" src="https://github.com/user-attachments/assets/d2266351-1545-4d8f-a1c8-45c4fa05a963" />


---

## Quick start

1. Install **Minecraft 1.20.1** with **Forge 47.x**.
2. Drop the built jar into `.minecraft/mods/` (see [Building from source](#building-from-source)).
3. Start the game and press **Right Shift** to open the menu.

The mod is client-only: it does nothing on a dedicated server and does not need to be installed there.

## The menu

Press **Right Shift** (again to close). The menu has three tabs:

- **Modules** - one panel per category. *Left click* toggles a module, *right click* opens its settings,
  *middle click* sets a keybind (Delete clears, Esc cancels). Drag panel headers to move them, click a header to
  collapse it, type anywhere to search.
- **Settings** - interface options (logo text, font, module list).
- **Music** - the built-in music player.

## Chat commands

Commands start with `&` and are handled entirely on the client.

| Command | Description |
|---|---|
| `&help` | List commands. |
| `&list` | All modules with their state and key. |
| `&<module>` / `&toggle <module>` | Toggle a module. |
| `&bind <module> <key\|none>` | Bind a key, e.g. `&bind TargetPing g`. |
| `&settings <module>` | Show a module's settings. |
| `&set <module> <setting> <value>` | Change a setting, e.g. `&set xray blocks add diamond_ore`. |
| `&ping` | Mark / unmark your target (same as the TargetPing key). |
| `&wp add [name]` \| `here` \| `list` \| `remove <n>` \| `clear` \| `go <n\|off>` \| `reset` | Waypoints. |

## Music player and custom songs

The **Music** tab is a small Spotify-like player for Minecraft's music:

- now-playing card with a disc icon, elapsed time and an equalizer
- play / stop, previous / next, and a playlist mode (*Repeat one*, *Next track*, *Shuffle*, *Play once*)
- volume slider (the game's Music volume)
- a searchable song list with category chips (Game, Creative, Menu, Nether, End, Discs, Custom)
- quick buttons for *Vanilla*, *Random* and *Silence*

**Your own songs.** Click **Open folder** (or go to
`.minecraft/resourcepacks/PLAWRIENT-fonts/assets/plawrient/sounds/custom/`), drop `.ogg` files (OGG Vorbis) in
there, then click **Reload songs**. They show up under *Custom*. Convert other formats with, for example:

```bash
ffmpeg -i song.mp3 -c:a libvorbis song.ogg
```

**BloodMoon music box.** The mod ships **no copyrighted audio**. BloodMoon uses a built-in note-block lullaby by
default; to use a recording of your own, save it as
`.minecraft/resourcepacks/PLAWRIENT-fonts/assets/plawrient/sounds/puppet_music_box.ogg` and toggle BloodMoon.

> Minecraft has no real pause: *Play* restarts a song from the beginning, and only the elapsed time (not the
> total length) can be shown.

## Configuration and files

| What | Where |
|---|---|
| Module states, keys, settings, menu layout, waypoints | `.minecraft/config/plawrient.json` |
| Installed fonts, music sound definitions, custom songs | `.minecraft/resourcepacks/PLAWRIENT-fonts/` (a resource pack the mod creates and enables automatically) |

## Building from source

Requirements: **JDK 17**. Clone the repository and use the bundled Gradle wrapper:

```bash
./gradlew runClient   # start a development client with the mod loaded
./gradlew build       # build the jar -> build/libs/
```

On Windows use `.\gradlew` instead of `./gradlew`. The built jar goes into `.minecraft/mods/` of a Forge 1.20.1
(47.x) client.

Project layout:

```
src/main/java/dev/plawrient/
  core/    module system, settings, commands, config, fonts, chat helpers
  module/  all modules
  gui/     menu (ClickGuiScreen), music player (MusicPanel), drawing helpers
  forge/   Forge entry point and event bridge
```

Adding a module: extend `Module`, add settings with `add(new BoolSetting(...))` and friends, override the
`on...` hooks, and register it in `ModuleManager`.

## Responsible use

Several modules (ESP, Xray, Freecam, Speed, InventoryMove, auto-equip helpers, ...) give an advantage and are
**not allowed on many multiplayer servers**. Using them there can get you banned. Use PLAWRIENT in single-player,
on your own server, or where the rules explicitly allow client mods - you are responsible for how you use it.

## Credits

- **Concept, feature ideas, direction and testing:** **[Plawro](https://plawro.net)**
- **Implementation:** written together with **[Claude](https://claude.ai)**, Anthropic's Sonnet 5.5 Medium AI assistant, through
  Claude Code - a lot of design and engineering went into it. The mod was made in less than a week.

Minecraft is a trademark of Mojang AB / Microsoft. This project is not affiliated with or endorsed by Mojang,
Microsoft, Forge or Anthropic.

## License

GNU GPLv3 (as declared in `mods.toml`).
