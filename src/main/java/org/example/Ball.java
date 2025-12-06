package org.example;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

final class Ball {
    private double x;
    private double y;
    private double velocityX;
    private double velocityY;
    private final int diameter;
    private final Color defaultColor;
    private Color color;
    private boolean onFire;
    private boolean radioactive;
    private static final int TRAIL_CAPACITY = 18;
    private final double[] trailX = new double[TRAIL_CAPACITY];
    private final double[] trailY = new double[TRAIL_CAPACITY];
    private int trailSize;
    private int trailHead = -1;

    Ball(double centerX, double centerY, int diameter, Color color) {
        this.diameter = diameter;
        this.defaultColor = color;
        this.color = color;
        setCenter(centerX, centerY);
    }

    void move() {
        x += velocityX;
        y += velocityY;
        if (Math.abs(velocityX) > 1e-6 || Math.abs(velocityY) > 1e-6) {
            recordTrailPosition(getCenterX(), getCenterY());
        }
    }

    void setVelocity(double newVelocityX, double newVelocityY) {
        this.velocityX = newVelocityX;
        this.velocityY = newVelocityY;
    }

    void bounceHorizontally() {
        velocityX = -velocityX;
    }

    void bounceVertically() {
        velocityY = -velocityY;
    }

    void setCenter(double centerX, double centerY) {
        this.x = centerX - diameter / 2.0;
        this.y = centerY - diameter / 2.0;
        resetTrail(centerX, centerY);
    }

    double getX() {
        return x;
    }

    void setX(double x) {
        this.x = x;
    }

    double getY() {
        return y;
    }

    void setY(double y) {
        this.y = y;
    }

    double getCenterX() {
        return x + diameter / 2.0;
    }

    double getCenterY() {
        return y + diameter / 2.0;
    }

    double getVelocityX() {
        return velocityX;
    }

    double getVelocityY() {
        return velocityY;
    }

    double getSpeed() {
        return Math.hypot(velocityX, velocityY);
    }

    int getDiameter() {
        return diameter;
    }

    Rectangle2D.Double getBounds() {
        return new Rectangle2D.Double(x, y, diameter, diameter);
    }

    void setColor(Color color) {
        this.color = color;
    }

    void setOnFire(boolean onFire) {
        this.onFire = onFire;
        if (!onFire) {
            this.color = defaultColor;
        } else {
            this.color = new Color(0xFF6F00);
        }
    }

    void setRadioactive(boolean radioactive) {
        if (this.radioactive == radioactive) {
            if (radioactive && trailSize == 0) {
                recordTrailPosition(getCenterX(), getCenterY());
            }
            return;
        }
        this.radioactive = radioactive;
        if (radioactive) {
            resetTrail(getCenterX(), getCenterY());
        } else {
            clearTrail();
        }
    }

    void normalizeSpeed(double targetSpeed) {
        double currentSpeed = getSpeed();
        if (currentSpeed == 0) {
            return;
        }
        double scale = targetSpeed / currentSpeed;
        velocityX *= scale;
        velocityY *= scale;
    }

    void ensureMinimumSpeed(double minSpeed) {
        double currentSpeed = getSpeed();
        if (currentSpeed < minSpeed) {
            normalizeSpeed(minSpeed);
        }
    }

    void clearTrail() {
        trailSize = 0;
        trailHead = -1;
    }

    void draw(Graphics2D g2) {
        if (radioactive && trailSize > 1) {
            drawRadioactiveTrail(g2);
        }

        if (radioactive) {
            g2.setColor(new Color(120, 255, 150, 120));
            int glowSize = Math.max(4, diameter + 18);
            g2.fillOval((int) Math.round(x) - 9, (int) Math.round(y) - 9, glowSize, glowSize);
            g2.setColor(new Color(180, 255, 200, 200));
            g2.fillOval((int) Math.round(x) - 3, (int) Math.round(y) - 3, diameter + 6, diameter + 6);
        } else if (onFire) {
            g2.setColor(new Color(255, 120, 0, 160));
            int glowSize = Math.max(4, diameter + 12);
            g2.fillOval((int) Math.round(x) - 6, (int) Math.round(y) - 6, glowSize, glowSize);
            g2.setColor(new Color(255, 200, 40, 180));
            g2.fillOval((int) Math.round(x) - 2, (int) Math.round(y) - 2, diameter + 4, diameter + 4);
        }

        g2.setColor(radioactive ? new Color(90, 255, 130) : color);
        g2.fillOval((int) Math.round(x), (int) Math.round(y), diameter, diameter);
        g2.setColor(radioactive ? new Color(235, 255, 235) : Color.WHITE);
        int highlightSize = Math.max(2, diameter / 4);
        g2.fillOval((int) Math.round(x + diameter / 3.0), (int) Math.round(y + diameter / 3.0), highlightSize, highlightSize);
    }

    private void drawRadioactiveTrail(Graphics2D g2) {
        double baseSize = diameter;
        for (int i = 0; i < trailSize; i++) {
            int index = (trailHead - i + TRAIL_CAPACITY) % TRAIL_CAPACITY;
            double centerX = trailX[index];
            double centerY = trailY[index];
            double progress = (double) (trailSize - i) / (trailSize + 1);
            int alpha = (int) Math.round(120 * progress);
            int size = (int) Math.round(baseSize * (0.6 + 0.5 * progress));
            int drawX = (int) Math.round(centerX - size / 2.0);
            int drawY = (int) Math.round(centerY - size / 2.0);
            g2.setColor(new Color(80, 255, 160, Math.max(10, Math.min(240, alpha))));
            g2.fillOval(drawX, drawY, size, size);
        }
    }

    private void recordTrailPosition(double centerX, double centerY) {
        trailHead = (trailHead + 1) % TRAIL_CAPACITY;
        trailX[trailHead] = centerX;
        trailY[trailHead] = centerY;
        if (trailSize < TRAIL_CAPACITY) {
            trailSize++;
        }
    }

    private void resetTrail(double centerX, double centerY) {
        trailSize = 0;
        trailHead = -1;
        recordTrailPosition(centerX, centerY);
    }
}
