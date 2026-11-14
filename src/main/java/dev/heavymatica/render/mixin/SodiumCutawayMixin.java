package dev.heavymatica.render.mixin;

import dev.heavymatica.render.cutaway.CutawayMask;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium's counterpart of {@link CutawayRegionMixin}. LevelSlice is the per-task snapshot Sodium's chunk mesher reads
 * on its worker threads. In Sodium 0.8.x and 0.9.x (checked 0.9.2 on MC 26.2) every read goes through these two methods: getBlockState(BlockPos) and
 * getFluidState(BlockPos) delegate to getBlockState(int, int, int), and getBlockEntity(BlockPos) to
 * getBlockEntity(int, int, int). Inside cut boxes a block becomes its fluid alone (air, water or lava), so the terrain
 * disappears but water and lava stay visible. Only the mesh changes; ClientLevel is never touched.
 * Applied only when Sodium is loaded (see HeavymaticaMixinPlugin).
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumCutawayMixin {
    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), cancellable = true, remap = false)
    private void heavymatica$cutBlock(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (CutawayMask.cut(x, y, z)) cir.setReturnValue(CutawayMask.fluidOnly(cir.getReturnValue()));
    }

    @Inject(method = "getBlockEntity(III)Lnet/minecraft/world/level/block/entity/BlockEntity;", at = @At("HEAD"), cancellable = true, remap = false)
    private void heavymatica$cutBlockEntity(int x, int y, int z, CallbackInfoReturnable<BlockEntity> cir) {
        if (CutawayMask.cut(x, y, z)) cir.setReturnValue(null);
    }
}
