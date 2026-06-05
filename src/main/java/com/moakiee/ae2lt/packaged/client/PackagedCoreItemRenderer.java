package com.moakiee.ae2lt.packaged.client;

import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;

public final class PackagedCoreItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation BASE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LTPackagedProvider.MODID,
            "textures/item/provider_core_base.png");

    private static final float TARGET_CENTER_X = 0.78f;
    private static final float TARGET_CENTER_Y = 0.22f;
    private static final float TARGET_SCALE = 0.45f;

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
        var consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(BASE_TEXTURE));
        var pose = poseStack.last();
        vertex(consumer, pose, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, packedLight, packedOverlay);
        vertex(consumer, pose, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, packedLight, packedOverlay);
        vertex(consumer, pose, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, packedLight, packedOverlay);
        vertex(consumer, pose, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, packedLight, packedOverlay);
    }

    private static void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            int packedLight,
            int packedOverlay) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0.0f, 0.0f, 1.0f);
    }

    private static void renderTarget(
            ItemStack target,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(TARGET_CENTER_X, TARGET_CENTER_Y, 0.08f);
        poseStack.scale(TARGET_SCALE, TARGET_SCALE, TARGET_SCALE);
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
