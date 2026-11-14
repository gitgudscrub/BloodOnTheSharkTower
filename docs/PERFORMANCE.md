# Performance and shaders

The pack is set up to run on PCs with 8 GB of RAM. Shaders are optional,
for players with stronger PCs.

## What the pack already does

- Sodium, Lithium, FerriteCore, Entity Culling, ImmediatelyFast and
  BadOptimizations make the game render faster and use less memory.
- Dynamic FPS lowers the frame rate when the game is in the background
  (for example while you're in Discord), so it uses less of your PC.
- The Prism instance gives the game 3 GB of memory. That is plenty for
  this pack and leaves room for Windows and Discord on an 8 GB PC.
- A new install starts with a render distance of 8, a simulation distance
  of 6 and fewer particles. Your own settings are never overwritten after that.

## 8 GB PCs

- Keep the memory at 3 GB. Giving Minecraft more memory on an 8 GB PC makes
  it slower, because Windows starts swapping to disk.
- If the game stutters, lower Render Distance to 6 in
  Options > Video Settings and set Graphics to Fast.
- Close browser tabs and other games while you play.

## Stronger PCs (16 GB RAM or more)

- You can raise the memory to 4–6 GB. In Prism: right-click the instance >
  Edit > Settings > Java > Maximum memory allocation.
- Turn the render distance up as high as you like (the server sets the limit).

## Shaders

Shaders need the **Iris Shaders** mod plus a shader pack. They are not
installed by default.

1. When you first install the pack, a window lists the optional files.
   Tick **Iris Shaders** and one of the shader packs:
   - **Complementary Reimagined**: good-looking, for strong PCs with a
     gaming graphics card.
   - **MakeUp Ultra Fast**: lighter, for mid-range PCs.
2. In the game, go to Options > Video Settings > Shader Packs (or press
   **F7**), pick a pack and click Apply. Press **F6** to turn shaders on or off.

Iris normally uses R, K and I, which are Sharktower keys (Grimoire, catalog,
Storyteller tools). The first time you start the game with Iris, Sharktower
moves Iris to F6 (shaders on/off) and F7 (shader packs) and unbinds its
reload-shaders key. You can change them in Options > Controls > Key Binds.

To change your choice later (Prism instance): open the instance's folder
(right-click the instance > Folder), delete `minecraft/packwiz.json`, and
launch. The window asks again. Nothing else is lost.

If you installed the `.mrpack` in the Modrinth App or Prism, the optional
files are offered when you import the pack.

Shaders run on your graphics card, so they cost frame rate rather than
RAM. If the game gets slow with shaders, switch to MakeUp Ultra Fast or
press F6 to turn them off.

## Server admin

- A view distance of 6–8 is enough for the town. It sets the most chunks any
  player can load, so a lower view distance also saves memory on players' PCs.
  The setting is `view-distance` in `server.properties`, next to
  `simulation-distance` (4–6).
- Lithium and FerriteCore can also run on the server (they are optional there).
