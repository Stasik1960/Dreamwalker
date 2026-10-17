package dev.dreamwalker.bloodbornerp.client;

import dev.dreamwalker.bloodbornerp.BloodborneRp;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.object.AuthoredObjectMotion;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

/** Static, fitted inventory miniatures. Never creates, ticks, or changes a world entity. */
public final class RpPlacementItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer, GeoRenderer<RpPlacementItemRenderer.Icon> {
    private final Icon icon;
    private final GeoModel<Icon> model = new GeoModel<>() {
        @Override public Identifier getModelResource(Icon value) { return BloodborneRp.id(AssetCatalog.get(value.assetId).model()); }
        @Override public Identifier getTextureResource(Icon value) { return BloodborneRp.id(AssetCatalog.get(value.assetId).texture()); }
        @Override public Identifier getAnimationResource(Icon value) { return BloodborneRp.id(AssetCatalog.get(value.assetId).animation()); }
    };
    private BakedGeoModel sourceModel;
    private List<GeoBone> iconBones;
    private double[] fit;

    private RpPlacementItemRenderer(String assetId) { icon = new Icon(ObjectRegistry.canonicalId(assetId)); }

    public static void initialize() {
        ObjectRegistry.TYPES.keySet().forEach(id -> BuiltinItemRendererRegistry.INSTANCE.register(
                Registries.ITEM.get(BloodborneRp.id(id + "_placer")), new RpPlacementItemRenderer(id)));
    }

    @Override public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                                 VertexConsumerProvider vertices, int light, int overlay) {
        BakedGeoModel baked = model.getBakedModel(model.getModelResource(icon));
        if (sourceModel != baked) {
            // GeckoLib's baked bones are mutable and shared with placed entities. Clone their
            // authored snapshots once per resource reload, never their current animated pose.
            iconBones = baked.topLevelBones().stream().map(bone -> copyBone(bone, null)).toList();
            if (icon.assetId.equals("wood_gate")) applyGateRestPose(iconBones);
            fit = projectedFit(iconBones);
            sourceModel = baked;
        }
        matrices.push();
        try {
            matrices.translate(.5, .5, .5);
            float factor = (float)fit[3];
            matrices.scale(factor, factor, factor);
            matrices.translate(-fit[0], -fit[1], -fit[2]);
            rotateIcon(matrices);
            RenderLayer layer = RenderLayer.getEntityCutoutNoCull(model.getTextureResource(icon));
            var buffer = vertices.getBuffer(layer);
            for (GeoBone bone : iconBones) renderRecursively(matrices, icon, bone, layer, vertices,
                    buffer, true, 0, light, overlay, 1, 1, 1, 1);
        } finally { matrices.pop(); }
    }

    private static void rotateIcon(MatrixStack matrices) {
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45));
    }

    private static GeoBone copyBone(GeoBone source, GeoBone parent) {
        var copy = new GeoBone(parent, source.getName(), source.getMirror(), source.getInflate(),
                source.shouldNeverRender(), source.getReset());
        var initial = source.getInitialSnapshot();
        copy.setPivotX(source.getPivotX()); copy.setPivotY(source.getPivotY()); copy.setPivotZ(source.getPivotZ());
        copy.setRotX(initial.getRotX()); copy.setRotY(initial.getRotY()); copy.setRotZ(initial.getRotZ());
        copy.setPosX(initial.getOffsetX()); copy.setPosY(initial.getOffsetY()); copy.setPosZ(initial.getOffsetZ());
        copy.setScaleX(initial.getScaleX()); copy.setScaleY(initial.getScaleY()); copy.setScaleZ(initial.getScaleZ());
        // Cubes/quads contain immutable baked geometry and UV; only bone transforms are mutable.
        copy.getCubes().addAll(source.getCubes());
        for (GeoBone child : source.getChildBones()) copy.getChildBones().add(copyBone(child, copy));
        copy.saveInitialSnapshot();
        return copy;
    }

    private static void applyGateRestPose(List<GeoBone> roots) {
        Map<String, GeoBone> byName = new HashMap<>();
        roots.forEach(bone -> indexBones(bone, byName));
        for (String name : AuthoredObjectMotion.gateBones()) {
            GeoBone bone = byName.get(name);
            if (bone == null) continue;
            var rotation = AuthoredObjectMotion.gateSample(name, "rotation", 1.6);
            var position = AuthoredObjectMotion.gateSample(name, "position", 1.6);
            var initial = bone.getInitialSnapshot();
            bone.setRotX(initial.getRotX() - (float)Math.toRadians(rotation.x));
            bone.setRotY(initial.getRotY() - (float)Math.toRadians(rotation.y));
            bone.setRotZ(initial.getRotZ() + (float)Math.toRadians(rotation.z));
            bone.setPosX((float)position.x); bone.setPosY((float)position.y); bone.setPosZ((float)position.z);
        }
    }

    private static void indexBones(GeoBone bone, Map<String, GeoBone> result) {
        result.put(bone.getName(), bone);
        bone.getChildBones().forEach(child -> indexBones(child, result));
    }

    /** Fit actual transformed vertices, including cube/bone rotations and off-centre origins. */
    private static double[] projectedFit(List<GeoBone> roots) {
        double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        var matrices = new MatrixStack(); rotateIcon(matrices);
        roots.forEach(bone -> measureBone(matrices, bone, bounds));
        double longest = Math.max(bounds[3] - bounds[0], Math.max(bounds[4] - bounds[1], bounds[5] - bounds[2]));
        if (!Double.isFinite(longest) || longest <= 0) throw new IllegalStateException("Empty RP placement item model");
        return new double[]{(bounds[0]+bounds[3])/2, (bounds[1]+bounds[4])/2,
                (bounds[2]+bounds[5])/2, .82/longest};
    }

    private static void measureBone(MatrixStack matrices, GeoBone bone, double[] bounds) {
        matrices.push();
        RenderUtils.prepMatrixForBone(matrices, bone);
        if (!bone.isHidden()) for (var cube : bone.getCubes()) {
            matrices.push();
            RenderUtils.translateToPivotPoint(matrices, cube);
            RenderUtils.rotateMatrixAroundCube(matrices, cube);
            RenderUtils.translateAwayFromPivotPoint(matrices, cube);
            for (var quad : cube.quads()) if (quad != null) for (var vertex : quad.vertices()) {
                var p = vertex.position();
                var point = matrices.peek().getPositionMatrix().transform(new Vector4f(p.x(), p.y(), p.z(), 1));
                bounds[0]=Math.min(bounds[0],point.x); bounds[1]=Math.min(bounds[1],point.y); bounds[2]=Math.min(bounds[2],point.z);
                bounds[3]=Math.max(bounds[3],point.x); bounds[4]=Math.max(bounds[4],point.y); bounds[5]=Math.max(bounds[5],point.z);
            }
            matrices.pop();
        }
        if (!bone.isHidingChildren()) bone.getChildBones().forEach(child -> measureBone(matrices, child, bounds));
        matrices.pop();
    }

    @Override public GeoModel<Icon> getGeoModel() { return model; }
    @Override public Icon getAnimatable() { return icon; }
    @Override public void fireCompileRenderLayersEvent() {}
    @Override public boolean firePreRenderEvent(MatrixStack matrices, BakedGeoModel baked, VertexConsumerProvider vertices, float delta, int light) { return true; }
    @Override public void firePostRenderEvent(MatrixStack matrices, BakedGeoModel baked, VertexConsumerProvider vertices, float delta, int light) {}
    @Override public void updateAnimatedTextureFrame(Icon value) {}

    public static final class Icon implements GeoAnimatable {
        private final String assetId;
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private Icon(String assetId) { this.assetId = assetId; }
        @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
        @Override public double getTick(Object related) { return 0; }
    }
}
