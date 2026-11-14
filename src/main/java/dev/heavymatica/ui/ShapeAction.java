package dev.heavymatica.ui;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.VoxelGrid;
import dev.heavymatica.core.VoxelToBoxDecomposer;
import dev.schemarray.api.ShapeActionContext;
import dev.schemarray.shape.VoxelResult;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** The "Save as Heavymatic" button of the Shape Generator: the shape becomes exact boxes, a preset and a placement. */
public final class ShapeAction {
    private ShapeAction() {}

    public static void run(ShapeActionContext ctx) {
        VoxelResult v = ctx.voxels();
        if (v == null || v.blockCount() == 0) {
            CreateFromSelection.say("The shape is empty.");
            return;
        }
        // a cell is filled when its palette entry is not air (index 0 = air; guard against an explicit air block too)
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
        List<Box3i> local = VoxelToBoxDecomposer.decompose(grid);
        if (local.isEmpty()) {
            CreateFromSelection.say("The shape has no solid blocks.");
            return;
        }
        var o = ctx.suggestedOrigin();
        List<Box3i> world = new ArrayList<>(local.size());
        for (Box3i b : local) world.add(b.offset(o.getX(), o.getY(), o.getZ()));
        String name = ctx.suggestedName() == null || ctx.suggestedName().isBlank() ? "shape" : ctx.suggestedName().trim();
        // too many boxes: the dialog says so and its Save button stays off
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new NameRoleDialog(mc.gui.screen(), "Save shape as heavymatic", name, world.size(), true,
                (n, role) -> CreateFromSelection.saveAndPlace(n, role, world)));
    }
}
