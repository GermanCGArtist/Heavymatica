package dev.heavymatica.ui;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.core.VoxelGrid;
import dev.heavymatica.core.VoxelToBoxDecomposer;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import dev.heavymatica.place.PresetLibrary;
import dev.vitruvius.api.ShapeActionContext;
import dev.vitruvius.api.ShapeOutput;
import dev.vitruvius.shape.VoxelResult;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Heavymatic as an output of Vitruvius's Shape generator window: the shape becomes exact boxes of one region, shown
 * live as an unsaved preview placement (wireframes, and the terrain cutaway for DIG and CLEAR), and Create saves the
 * preset and a real placement at the same spot without leaving the editor.
 */
public final class ShapeOutputImpl implements ShapeOutput {
    private VoxelResult cachedFor;
    private List<Box3i> cachedLocal = List.of();

    @Override
    public String label() {
        return "Heavymatic";
    }

    @Override
    public String hover() {
        return "Saves the shape as a heavymatic preset with the chosen role and places it where the preview is.";
    }

    @Override
    public List<String> options() {
        return Arrays.stream(RegionRole.values()).map(Enum::name).toList();
    }

    @Override
    public String preview(ShapeActionContext ctx, int option) {
        if (ctx == null || ctx.voxels() == null) {
            PlacementStore.setPreview(null);
            cachedFor = null;
            return null;
        }
        List<Box3i> local = boxes(ctx.voxels());
        if (local.isEmpty()) {
            PlacementStore.setPreview(null);
            return "The shape has no solid blocks.";
        }
        RegionRole role = role(option);
        HeavymaticPlacement p = placement(ctx, role, local);
        PlacementStore.setPreview(p);
        String text = local.size() + " boxes, " + role.name();
        return local.size() > NameRoleDialog.MAX_BOXES
                ? "Too many boxes (" + local.size() + ", at most " + NameRoleDialog.MAX_BOXES + "): make the shape simpler."
                : text;
    }

    @Override
    public boolean usesListFolders() {
        return true;
    }

    @Override
    public String create(ShapeActionContext ctx, int option) {
        return create(ctx, option, null);
    }

    @Override
    public String create(ShapeActionContext ctx, int option, String folderId) {
        if (ctx == null || ctx.voxels() == null) return "The shape is empty.";
        List<Box3i> local = boxes(ctx.voxels());
        if (local.isEmpty()) return "The shape has no solid blocks.";
        if (local.size() > NameRoleDialog.MAX_BOXES) {
            return local.size() + " boxes, at most " + NameRoleDialog.MAX_BOXES + " are allowed.";
        }
        RegionRole role = role(option);
        String name = name(ctx);
        PlacementStore.setPreview(null);
        BlockPos o = ctx.suggestedOrigin();
        HeavymaticSpec spec = new HeavymaticSpec(name, "", "", List.of(new HeavymaticRegion(name, role, role.defaultColor(), local)));
        PresetLibrary.save(spec);
        HeavymaticPlacement placed = HeavymaticPlacement.create(spec, name, o.getX(), o.getY(), o.getZ());
        PlacementStore.add(placed);
        if (folderId != null) dev.vitruvius.api.VitruviusApi.moveEntryToFolder(placed.entryId(), folderId);
        return null;
    }

    private static RegionRole role(int option) {
        RegionRole[] all = RegionRole.values();
        return all[Math.max(0, Math.min(all.length - 1, option))];
    }

    private static String name(ShapeActionContext ctx) {
        String n = ctx.suggestedName();
        return n == null || n.isBlank() ? "shape" : n.trim();
    }

    /** The preview placement: the cached local boxes at the shape's origin. */
    private static HeavymaticPlacement placement(ShapeActionContext ctx, RegionRole role, List<Box3i> local) {
        String name = name(ctx);
        HeavymaticSpec spec = new HeavymaticSpec(name, "", "", List.of(new HeavymaticRegion(name, role, role.defaultColor(), local)));
        BlockPos o = ctx.suggestedOrigin();
        return HeavymaticPlacement.create(spec, name, o.getX(), o.getY(), o.getZ());
    }

    /** The exact boxes of the filled cells, in the shape's own coordinates (cached per result). */
    private List<Box3i> boxes(VoxelResult v) {
        if (v == cachedFor) return cachedLocal;
        boolean[] solid = new boolean[v.palette().size() + 1];
        for (int i = 0; i < v.palette().size(); i++) {
            String s = v.palette().get(i);
            solid[i + 1] = !(s.startsWith("air") || s.startsWith("cave_air") || s.startsWith("void_air")
                    || s.startsWith("minecraft:air") || s.startsWith("minecraft:cave_air") || s.startsWith("minecraft:void_air"));
        }
        VoxelGrid grid = new VoxelGrid() {
            @Override public int sizeX() { return v.sizeX(); }
            @Override public int sizeY() { return v.sizeY(); }
            @Override public int sizeZ() { return v.sizeZ(); }
            @Override public boolean filled(int x, int y, int z) {
                int k = v.get(x, y, z);
                return k > 0 && k < solid.length && solid[k];
            }
        };
        cachedLocal = new ArrayList<>(VoxelToBoxDecomposer.decompose(grid));
        cachedFor = v;
        return cachedLocal;
    }
}
