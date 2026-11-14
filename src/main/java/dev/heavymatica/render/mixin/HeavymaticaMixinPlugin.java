package dev.heavymatica.render.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Picks the cutaway mixin for the chunk mesher in use (vanilla or Sodium). */
public final class HeavymaticaMixinPlugin implements IMixinConfigPlugin {
    private final boolean sodium = FabricLoader.getInstance().isModLoaded("sodium");

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Exactly one mesher hook: Sodium's LevelSlice with Sodium, vanilla's RenderSectionRegion without it.
        if (mixinClassName.endsWith(".SodiumCutawayMixin")) return sodium;
        if (mixinClassName.endsWith(".CutawayRegionMixin")) return !sodium;
        return true;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
