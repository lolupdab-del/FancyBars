package dev.fancybars.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.Display;

/** Lets the mod throw away a text display's cached lines so they're rebuilt (with custom names swapped in). */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayCacheAccessor {
	@Accessor("clientDisplayCache")
	void fancybars$setClientDisplayCache(Display.TextDisplay.@Nullable CachedInfo cache);
}
