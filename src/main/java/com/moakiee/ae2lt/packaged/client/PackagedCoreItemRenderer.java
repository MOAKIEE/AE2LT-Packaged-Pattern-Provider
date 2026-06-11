package com.moakiee.ae2lt.packaged.client;

import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;
import com.moakiee.ae2lt.packaged.registry.PPItems;

@SuppressWarnings("deprecation")
public final class PackagedCoreItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float TARGET_CENTER_X = 0.78f;
    private static final float TARGET_CENTER_Y = 0.22f;
    private static final float TARGET_CENTER_Z = 0.57f;
    private static final float TARGET_SCALE = 0.45f;
    private static final float TARGET_DEPTH_SCALE = 0.02f;

    public PackagedCoreItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext context,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        renderBase(poseStack, bufferSource, packedLight, packedOverlay);
        targetStack(stack).ifPresent(target ->
                renderTarget(target, poseStack, bufferSource, packedLight, packedOverlay));
    }

    private static Optional<ItemStack> targetStack(ItemStack stack) {
        for (var definition : PackagedCoreDefinition.all()) {
            if (!stack.is(definition.runtimeItem().get())) {
                continue;
            }

            var id = definition.targetItemId();
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                return Optional.empty();
            }

            var item = BuiltInRegistries.ITEM.get(id);
            if (item == null || item == Items.AIR) {
                return Optional.empty();
            }
            return Optional.of(new ItemStack(item));
        }
        return Optional.empty();
    }

    private static void renderBase(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(PPItems.BASIC_PACKAGED_CORE.get()),
                ItemDisplayContext.NONE,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                null,
                0);
        poseStack.popPose();
    }

    private static void renderTarget(
            ItemStack target,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(TARGET_CENTER_X, TARGET_CENTER_Y, TARGET_CENTER_Z);
        poseStack.scale(TARGET_SCALE, TARGET_SCALE, TARGET_SCALE * TARGET_DEPTH_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                target,
                ItemDisplayContext.GUI,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                null,
                0);
        poseStack.popPose();
    }
}
