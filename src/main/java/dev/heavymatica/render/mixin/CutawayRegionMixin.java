package dev.heavymatica.render.mixin;

import dev.heavymatica.render.cutaway.CutawayMask;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RenderSectionRegion is the snapshot (copy of 3x3x3 sections) that SectionCompiler#compile reads while meshing on
 * worker threads. Returning air here only changes the mesh; ClientLevel is never touched.
 */
@Mixin(RenderSectionRegion.class)
public abstract class CutawayRegionMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void heavymatica$cutBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (CutawayMask.cut(pos.getX(), pos.getY(), pos.getZ())) cir.setReturnValue(Blocks.AIR.defaultBlockState());
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void heavymatica$cutFluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (CutawayMask.cut(pos.getX(), pos.getY(), pos.getZ())) cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void heavymatica$cutBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (CutawayMask.cut(pos.getX(), pos.getY(), pos.getZ())) cir.setReturnValue(null);
    }
}
