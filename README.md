# Heavymatica

## What it is

Heavymatica is a client-side Fabric addon for Litematica and Litematica Array. A **heavymatic** is a named preset of one or more boxes, with a role: dig, clear, flood, fill or protect. You place it in the world, it shows as wireframes, and you can send its areas to the Manhattan script mod as saved areas.

Heavymatics appear in Litematica's placement list, marked `[H]`.

## Requirements

- Minecraft 26.1.1 with Java 25
- Fabric Loader 0.19.3 or newer
- Fabric API
- Litematica and malilib (built against Litematica 0.27.14 and malilib 0.28.12)
- Litematica Array 1.5.0 or newer (required)
- Manhattan (optional, only needed for "Send to Manhattan")

Sodium: the terrain cutaway works with and without Sodium (tested target: Sodium 0.8.9).

## Using it

### The H menu

Press **H** in the world to open the Heavymatica menu. It lists your presets with two buttons:

- **Place**: places the preset with its corner at the block you stand on.
- **Delete**: click to delete the preset file (a dialog asks first; Shift + click skips it). Placements already in the world stay.

The menu also has two buttons to make a new preset:

- **New from Manhattan selection**: uses the boxes selected in Manhattan. Enabled only when Manhattan is installed and has a selection.
- **New from Litematica selection**: uses all boxes of the current Litematica area selection.

Either button opens a small dialog with a name and a role. The role button cycles through DIG, CLEAR, FLOOD, FILL, PROTECT and CUSTOM. **Save** stores the preset and places it on the selected boxes. If a preset with the same name exists, it is replaced.

### The placement list rows

Each heavymatic has a row in Litematica's placement list. The row shows its name and a status line with the region and box counts, plus "sent" (green) or "out of sync" (orange) when Manhattan is involved. Its buttons, from right to left:

- **Configure**: opens the configuration screen (see below).
- **Send**: sends the areas to Manhattan. Shown only when Manhattan is installed.
- **Export**: scans the terrain and copies a text for Manhattan or an LLM. The button shows the progress while it scans.
- **Cut: ON / WIREFRAME / OFF**: sets the cutaway mode (see below).
- **Enabled / Disabled**: shows or hides the heavymatic in the world.
- **Remove**: click to remove the placement (a dialog asks first; Shift + click skips it). Areas already sent to Manhattan stay there.

Heavymatic rows can go into folders, take a color, and be selected and dragged like placement rows.

### The configuration screen

**Configure** opens the settings of one placement:

- **Origin** X, Y and Z: the position of the preset's corner. Type a number or use the +- button (left click +1, right click -1, Shift x8, Alt x4). **Move to player** sets the origin to the block you stand on.
- **Rotate**: cycles clockwise through 0, 90, 180 and 270 degrees.
- **Mirror**: cycles through NONE, FRONT_BACK (flips X) and LEFT_RIGHT (flips Z).
- **Copies** X, Y and Z: how many copies of the heavymatic to place along each axis (1 = no copies).
- **Gap** X, Y and Z: the space between the copies. The screen shows the size, and the copy step is the size plus the gap, at least 1.
- **Name**: the name of the area in Manhattan.
- **Cutaway**: the cutaway mode button.
- **Send**, **Export** and **Close**. Send is shown only when Manhattan is installed.
- **Regions**: each region with its name, role, box count and a **Visible / Hidden** button.

The screen shows the total box count. It turns orange above 64 boxes and red above 4096.

### Shape Generator

In Litematica Array's **Shape Generator**, the **Save as Heavymatic** button turns the shape into a heavymatic. The shape is split into exact boxes and saved as a preset, placed where the preview is. A dialog asks for the name and role first.

### Roles and colors

| Role | Color | Notes |
|---|---|---|
| DIG | dark orange | Cuts terrain in the cutaway |
| CLEAR | gold | Cuts terrain in the cutaway |
| FLOOD | blue | |
| FILL | brown | |
| PROTECT | green | Becomes a safe area in Manhattan |
| CUSTOM | white | Only selects the area in Manhattan, no action |

A region can have its own color. Otherwise its role color is used. Water and lava inside the boxes of visible DIG regions are marked with small blue and red cubes, so you can see them before you dig.

### Cutaway

The cutaway hides the terrain inside DIG and CLEAR boxes, so you can see the work area. Each placement has one of three modes:

- **ON**: wireframes and the terrain cutaway.
- **WIREFRAME**: wireframes only.
- **OFF**: nothing is drawn for this placement.

The cutaway is only visual. The real blocks stay where they are. Bots, Manhattan and collision are not affected.

The **Cycle cutaway** key cycles the mode (ON, WIREFRAME, OFF). It is not bound by default: set it in Minecraft's Controls settings. It works on the placements selected in the list. If none is selected, it works on the nearest placement within 16 blocks.

Wireframes are drawn only for enabled placements, and only for boxes within 160 blocks of you.

## Manhattan

Manhattan is optional. Without it, the Send buttons are hidden and everything else works.

### Area names

Each region becomes one Manhattan area:

- A preset with one region uses the placement's name (by default the preset name).
- A preset with several regions uses `name_region` for each region, for example `canal_trench`.

All array copies of a region are in the same area. Send always sends every region, including hidden ones.

PROTECT regions become safe areas in Midtown.

### Out of sync and sent

After you send, the row shows "sent". A change to the boxes, the role or the Manhattan name marks the row "out of sync" until you send again. Changes to Enabled, Cutaway or Visible do not.

The row is also marked "out of sync" when a script changes or deletes one of its areas in Manhattan.

### Dialogs and limits

- **More than 64 boxes**: Send asks for confirmation first.
- **An area a script wrote**: Send asks whether to replace it. If you say no, that area is skipped, the other areas are still sent, and the placement is not marked as sent.
- **An area in use**: if a running script has the area selected, Manhattan refuses it. Send stops there and tells you to stop the script first.
- **More than 4096 boxes**: Manhattan accepts at most 4096 boxes per area. Heavymatica refuses to send such a region, and nothing is sent. The Save dialog refuses to save it too.

### Export for Manhattan / LLM

**Export** scans the terrain in the background. It only reads loaded chunks, so unloaded cells are counted as unscanned. The text contains:

- A header with the name, world bounds and volume.
- For each region: its role, box count, area name, and a terrain summary. The summary has the water, lava and air counts, the five most common blocks, and the percentage unscanned.
- Warnings for falling blocks at the edges, lava next to the region, and overlap with a Manhattan protected area.
- The `area_box` lines for the boxes, and the role call (for example `clear_area()` for DIG).

Lines that start with `//` are comments, so the whole text can be pasted into a Manhattan script.

The text is copied to the clipboard and saved to `config/heavymatica/exports/<name>.txt`. A new export with the same name replaces the file.

## Files

- `config/heavymatica/presets/*.heavymatic`: the presets. Each file is JSON:

  ```json
  { "version": 1, "name": "canal_segment", "author": "", "description": "",
    "regions": [ { "name": "trench", "role": "DIG", "color": -23296, "boxes": [[0,0,0,20,14,20]] } ] }
  ```

  Boxes are `[minX, minY, minZ, maxX, maxY, maxZ]`, relative to the preset's corner.

- `config/heavymatica/placements/<world>.json`: the placements of one world. The world is `sp-<save folder>` in singleplayer, or `mp-<server address>` on a server. Placements are saved about a second after a change.
- `config/heavymatica/exports/`: the export texts.

## Building

1. Install JDK 25. `build.bat` uses a portable `jdk-25*` folder in `tools\` (or `..\Manhattan\tools\`) if there is one. Otherwise it uses `JAVA_HOME`.
2. Put the Litematica Array 1.5.0 jar into `libs\`. Litematica Array is not on a Maven repository, so build it from the `heavymatica-api` branch of Litematica Array and copy the `litematica-array-*.jar` here. The `libs\` folder is not in git.
3. Run `build.bat`. The mod jar is written to `build\libs\`. The build also runs the unit tests.

From PowerShell you can run `cmd /c "build.bat --console=plain -q"`.

## License

MIT. See [LICENSE](LICENSE).
