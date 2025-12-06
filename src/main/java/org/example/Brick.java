package org.example;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

final class Brick {
    private final Rectangle2D.Double bounds;
    private final Color color;
    private int hitPoints;

    Brick(double x, double y, double width, double height, Color color, int hitPoints) {
        this.bounds = new Rectangle2D.Double(x, y, width, height);
        this.color = color;
        this.hitPoints = Math.max(1, hitPoints);
    }

    Rectangle2D.Double getBounds() {
        return bounds;
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
        g2.setColor(color);
        g2.fill(bounds);

        g2.setColor(color.brighter());
        g2.setStroke(new BasicStroke(2));
        g2.draw(bounds);

        if (hitPoints > 1) {
            g2.setColor(new Color(255, 255, 255, 120));
            int inset = 6;
            g2.fill(new Rectangle2D.Double(bounds.x + inset, bounds.y + inset, bounds.width - 2 * inset, bounds.height - 2 * inset));
        }
    }
}
