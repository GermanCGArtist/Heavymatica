package dev.heavymatica.render.mixin;

import dev.heavymatica.render.cutaway.CutawayMask;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RenderSectionRegion is the snapshot (copy of 3x3x3 sections) that SectionCompiler#compile reads while meshing on
 * worker threads. Inside cut boxes a block is replaced by its fluid alone (air, water or lava), so the terrain
 * disappears but water and lava stay visible as they really are. getFluidState is left alone. Only the mesh changes;
 * ClientLevel is never touched.
 */
@Mixin(RenderSectionRegion.class)
public abstract class CutawayRegionMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void heavymatica$cutBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (CutawayMask.cut(pos.getX(), pos.getY(), pos.getZ())) cir.setReturnValue(CutawayMask.fluidOnly(cir.getReturnValue()));
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void heavymatica$cutBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (CutawayMask.cut(pos.getX(), pos.getY(), pos.getZ())) cir.setReturnValue(null);
    }
}
