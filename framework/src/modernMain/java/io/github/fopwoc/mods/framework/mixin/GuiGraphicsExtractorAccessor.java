package io.github.fopwoc.mods.framework.mixin;

import org.spongepowered.asm.mixin.Mixin;

/*? if >=26 {*/
/**
 * Reaches the GUI render state a 26.x extractor submits to, for custom GUI elements such as GPU
 * canvas meshes. NeoForge exposes a submit method and Fabric API widens the field, but neither
 * works on the other loader.
 */
@Mixin(net.minecraft.client.gui.GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {
    @org.spongepowered.asm.mixin.gen.Accessor("guiRenderState")
    net.minecraft.client.renderer.state.gui.GuiRenderState knhcore$guiRenderState();
}
/*?} else {*/
/*// 1.21.1 draws GPU canvas meshes immediately and needs no access; the mixin config is shared.
@Mixin(net.minecraft.client.gui.GuiGraphics.class)
public interface GuiGraphicsExtractorAccessor {}
*/
/*?}*/
