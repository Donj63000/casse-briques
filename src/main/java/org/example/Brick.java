package org.example;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

final class Brick {
    private final Rectangle2D.Double bounds;
    private final Color color;
    private final int maxHitPoints;
    private int hitPoints;

    Brick(double x, double y, double width, double height, Color color, int hitPoints) {
        this.bounds = new Rectangle2D.Double(x, y, width, height);
        this.color = color;
        this.hitPoints = Math.max(1, hitPoints);
        this.maxHitPoints = this.hitPoints;
    }

    Rectangle2D.Double getBounds() {
        return bounds;
    }

    Color getColor() {
        return color;
    }

    boolean isDestroyed() {
        return hitPoints <= 0;
    }

    boolean applyHit() {
        if (hitPoints > 0) {
            hitPoints--;
        }
        return hitPoints <= 0;
    }

    int getRemainingHits() {
        return hitPoints;
    }

    void draw(Graphics2D g2) {
        if (isDestroyed()) {
            return;
        }
        double shadowOffset = 3.0;
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fill(new Rectangle2D.Double(bounds.x + shadowOffset, bounds.y + shadowOffset, bounds.width, bounds.height));

        GradientPaint gradient = new GradientPaint(
            (float) bounds.x,
            (float) bounds.y,
            shade(color, 1.15),
            (float) bounds.x,
            (float) (bounds.y + bounds.height),
            shade(color, 0.85)
        );
        g2.setPaint(gradient);
        g2.fill(bounds);

        g2.setColor(shade(color, 1.2f));
        g2.setStroke(new BasicStroke(2f));
        g2.draw(bounds);

        g2.setColor(new Color(255, 255, 255, 120));
        g2.setStroke(new BasicStroke(3f));
        g2.drawLine((int) bounds.x + 2, (int) bounds.y + 2, (int) (bounds.x + bounds.width - 2), (int) bounds.y + 2);
        g2.setStroke(new BasicStroke(1f));

        drawHealthMarker(g2);
    }

    private void drawHealthMarker(Graphics2D g2) {
        int inset = 6;
        double markerHeight = 6;
        double markerWidth = bounds.width - inset * 2;
        double markerX = bounds.x + inset;
        double markerY = bounds.y + bounds.height - markerHeight - 4;

        g2.setColor(new Color(0, 0, 0, 100));
        g2.fill(new Rectangle2D.Double(markerX, markerY, markerWidth, markerHeight));

        double ratio = (double) hitPoints / maxHitPoints;
        double filledWidth = Math.max(0, markerWidth * ratio);
        g2.setColor(shade(color, 1.1));
        g2.fill(new Rectangle2D.Double(markerX, markerY, filledWidth, markerHeight));

        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(255, 255, 255, 90));
        for (int i = 1; i < maxHitPoints; i++) {
            double tickX = markerX + (markerWidth / maxHitPoints) * i;
            g2.drawLine((int) Math.round(tickX), (int) Math.round(markerY), (int) Math.round(tickX), (int) Math.round(markerY + markerHeight));
        }
    }

    private Color shade(Color base, double factor) {
        int r = clampColor((int) Math.round(base.getRed() * factor));
        int g = clampColor((int) Math.round(base.getGreen() * factor));
        int b = clampColor((int) Math.round(base.getBlue() * factor));
        return new Color(r, g, b, base.getAlpha());
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
