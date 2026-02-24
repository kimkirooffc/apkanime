package com.aniflow.util;

import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Rectangle;

public final class AppIconGenerator {
    private AppIconGenerator() {
    }

    public static Image createAppIcon() {
        Rectangle base = new Rectangle(512, 512);
        base.setArcWidth(240);
        base.setArcHeight(240);
        base.setFill(new LinearGradient(
            0,
            0,
            1,
            1,
            true,
            CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#4158D0")),
            new Stop(1, Color.web("#C850C0"))
        ));

        DropShadow shadow = new DropShadow();
        shadow.setRadius(46);
        shadow.setOffsetY(16);
        shadow.setColor(Color.color(0, 0, 0, 0.3));
        base.setEffect(shadow);

        Circle glassRing = new Circle(256, 256, 210);
        glassRing.setFill(Color.color(1, 1, 1, 0.08));
        glassRing.setStroke(Color.color(1, 1, 1, 0.28));
        glassRing.setStrokeWidth(8);

        Circle blurRing = new Circle(256, 256, 225);
        blurRing.setFill(Color.color(1, 1, 1, 0.12));
        blurRing.setEffect(new GaussianBlur(24));

        Circle head = new Circle(256, 280, 92, Color.web("#FFE8D5"));

        Path hair = new Path();
        hair.getElements().addAll(
            new MoveTo(165, 260),
            new QuadCurveTo(176, 165, 256, 168),
            new QuadCurveTo(336, 165, 347, 260),
            new QuadCurveTo(333, 218, 304, 220),
            new QuadCurveTo(288, 222, 278, 234),
            new QuadCurveTo(245, 214, 206, 222),
            new QuadCurveTo(180, 226, 165, 260)
        );
        hair.setFill(Color.web("#101014"));

        Ellipse leftEyeOuter = new Ellipse(228, 284, 18, 24);
        leftEyeOuter.setFill(Color.web("#111111"));
        Ellipse rightEyeOuter = new Ellipse(284, 284, 18, 24);
        rightEyeOuter.setFill(Color.web("#111111"));

        Ellipse leftEyeShine = new Ellipse(223, 274, 4, 6);
        leftEyeShine.setFill(Color.WHITE);
        Ellipse rightEyeShine = new Ellipse(279, 274, 4, 6);
        rightEyeShine.setFill(Color.WHITE);

        Group group = new Group(base, blurRing, glassRing, head, hair, leftEyeOuter, rightEyeOuter, leftEyeShine, rightEyeShine);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return group.snapshot(params, null);
    }
}
