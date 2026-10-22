package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A siren: a lattice mast with a rotating horn cluster on top of it.
 *
 * <p>What was there before was a fence post with a box on it, one block tall, which is what a siren
 * looks like if you have never seen one. A real one is a tower — the whole point is height, because
 * the sound has to clear the buildings — with a drum of horns that turns so the wail sweeps across
 * the city instead of pointing one way.
 *
 * <p>So: three and a half blocks of open steelwork, four legs braced at four levels with diagonals
 * on alternate faces, a deck, four horns radiating off a hub that turns, and a lamp above it. None
 * of that could be a block model, which is the reason the block grew a block entity.
 *
 * <p>Same coordinate warning as the wind turbine: geometry is authored with <strong>Y pointing
 * down</strong> and the renderer flips it, so a more negative Y is higher in the world. The origin
 * is the middle of the bottom face of the block the mast stands in.
 */
public final class SirenModel {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(CitiesInLife.id("siren"), "main");

    /** The part that turns, and the part that glows — both named so the renderer can find them. */
    public static final String HEAD = "head";
    public static final String LAMP = "lamp";

    private static final int TEXTURE_WIDTH = 128;
    private static final int TEXTURE_HEIGHT = 128;

    /** Where the legs stand, measured from the middle of the block. */
    private static final float LEG = 4.0F;

    /** Top of the mast, and the underside of the deck the head sits on. */
    private static final float DECK = -40.0F;

    /** The heights the mast is braced at. */
    private static final float[] RINGS = {-10.0F, -20.0F, -30.0F};

    /** How many horns the cluster carries. Four, so the wail sweeps rather than pulses. */
    private static final int HORNS = 4;

    /** Flat colour bands in the texture sheet, so a cube's unwrap lands on the right one. */
    private static final int STEEL_V = 0;
    private static final int HORN_V = 64;
    private static final int LAMP_V = 96;
    private static final int DARK_V = 112;

    private SirenModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // A plate on the ground, so the mast is bolted to something rather than growing out of it.
        root.addOrReplaceChild("base", CubeListBuilder.create()
                        .texOffs(0, DARK_V)
                        .addBox(-6.0F, -2.0F, -6.0F, 12, 2, 12),
                PartPose.ZERO);

        legs(root);
        rings(root);
        diagonals(root);

        // The deck the head turns on.
        root.addOrReplaceChild("deck", CubeListBuilder.create()
                        .texOffs(0, DARK_V)
                        .addBox(-5.0F, DECK - 2.0F, -5.0F, 10, 2, 10),
                PartPose.ZERO);

        head(root);

        // The lamp is a child of the root rather than of the head, so it stays put while the horns
        // sweep past underneath it. A beacon that spun with the drum would read as a fifth horn.
        root.addOrReplaceChild(LAMP, CubeListBuilder.create()
                        .texOffs(0, LAMP_V)
                        .addBox(-2.0F, -4.0F, -2.0F, 4, 4, 4),
                PartPose.offset(0.0F, DECK - 12.0F, 0.0F));

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /** Four uprights, corner to corner, from the base plate to the deck. */
    private static void legs(PartDefinition root) {
        int height = (int) (-DECK) - 2;
        for (int corner = 0; corner < 4; corner++) {
            float x = (corner & 1) == 0 ? LEG : -LEG;
            float z = (corner & 2) == 0 ? LEG : -LEG;
            root.addOrReplaceChild("leg" + corner, CubeListBuilder.create()
                            .texOffs(0, STEEL_V)
                            .addBox(-0.5F, -height, -0.5F, 1, height, 1),
                    PartPose.offset(x, -2.0F, z));
        }
    }

    /** Horizontal collars tying the four legs together. */
    private static void rings(PartDefinition root) {
        int span = (int) (LEG * 2.0F);
        for (int i = 0; i < RINGS.length; i++) {
            float y = RINGS[i];
            root.addOrReplaceChild("ring" + i, CubeListBuilder.create()
                            .texOffs(0, STEEL_V)
                            .addBox(-LEG, y, -LEG - 0.5F, span, 1, 1)
                            .texOffs(0, STEEL_V)
                            .addBox(-LEG, y, LEG - 0.5F, span, 1, 1)
                            .texOffs(0, STEEL_V)
                            .addBox(-LEG - 0.5F, y, -LEG, 1, 1, span)
                            .texOffs(0, STEEL_V)
                            .addBox(LEG - 0.5F, y, -LEG, 1, 1, span),
                    PartPose.ZERO);
        }
    }

    /**
     * The cross-bracing, one bar per face per bay, alternating which way it leans.
     *
     * <p>A rotated child rather than a rotated cube, because a bar leaning one way on one face and
     * the other way on the next is what stops a lattice reading as a ladder.
     */
    private static void diagonals(PartDefinition root) {
        int bay = 14;
        for (int level = 0; level < 2; level++) {
            float y = level == 0 ? -12.0F : -28.0F;
            float lean = (float) Math.toRadians(level == 0 ? 38.0D : -38.0D);
            root.addOrReplaceChild("braceN" + level, CubeListBuilder.create()
                            .texOffs(0, STEEL_V)
                            .addBox(-0.5F, -bay / 2.0F, -0.5F, 1, bay, 1),
                    PartPose.offsetAndRotation(0.0F, y, -LEG, 0.0F, 0.0F, lean));
            root.addOrReplaceChild("braceS" + level, CubeListBuilder.create()
                            .texOffs(0, STEEL_V)
                            .addBox(-0.5F, -bay / 2.0F, -0.5F, 1, bay, 1),
                    PartPose.offsetAndRotation(0.0F, y, LEG, 0.0F, 0.0F, -lean));
            root.addOrReplaceChild("braceW" + level, CubeListBuilder.create()
                            .texOffs(0, STEEL_V)
                            .addBox(-0.5F, -bay / 2.0F, -0.5F, 1, bay, 1),
                    PartPose.offsetAndRotation(-LEG, y, 0.0F, lean, 0.0F, 0.0F));
            root.addOrReplaceChild("braceE" + level, CubeListBuilder.create()
                            .texOffs(0, STEEL_V)
                            .addBox(-0.5F, -bay / 2.0F, -0.5F, 1, bay, 1),
                    PartPose.offsetAndRotation(LEG, y, 0.0F, -lean, 0.0F, 0.0F));
        }
    }

    /** The drum, and the horns coming off it. */
    private static void head(PartDefinition root) {
        PartDefinition head = root.addOrReplaceChild(HEAD, CubeListBuilder.create()
                        .texOffs(0, DARK_V)
                        .addBox(-3.0F, -9.0F, -3.0F, 6, 9, 6),
                PartPose.offset(0.0F, DECK - 2.0F, 0.0F));

        for (int horn = 0; horn < HORNS; horn++) {
            float angle = horn * (float) (Math.PI * 2.0D / HORNS);
            head.addOrReplaceChild("horn" + horn, CubeListBuilder.create()
                            // the throat, where it bolts to the drum
                            .texOffs(0, HORN_V)
                            .addBox(-1.5F, -6.5F, 2.0F, 3, 3, 4)
                            // the bell, flaring out
                            .texOffs(32, HORN_V)
                            .addBox(-3.0F, -8.0F, 6.0F, 6, 6, 5),
                    PartPose.rotation(0.0F, angle, 0.0F));
        }
    }
}
