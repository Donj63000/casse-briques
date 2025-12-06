package org.example;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

final class Paddle {
    private double x;
    private final double y;
    private int width;
    private final int height;
    private double speed;
    private boolean onFire;
    private boolean radioactive;

    Paddle(double startX, double y, int width, int height, double speed) {
        this.x = startX;
        this.y = y;
        this.width = width;
        this.height = height;
        this.speed = speed;
    }

    void move(double direction, int maxWidth) {
        x += direction * speed;
        if (x < 0) {
            x = 0;
        } else if (x + width > maxWidth) {
            x = maxWidth - width;
        }
    }

    void setCenter(double centerX) {
        x = centerX - width / 2.0;
    }

    void setWidth(int newWidth, int maxWidth) {
        double centerX = getCenterX();
        width = Math.max(40, newWidth);
        setCenter(centerX);
        double maxX = Math.max(0, maxWidth - width);
        if (x < 0) {
            x = 0;
        } else if (x > maxX) {
            x = maxX;
        }
    }

    void setSpeed(double newSpeed) {
        speed = Math.max(0, newSpeed);
    }

    double getSpeed() {
        return speed;
    }

    void setOnFire(boolean onFire) {
        this.onFire = onFire;
    }

    void setRadioactive(boolean radioactive) {
        this.radioactive = radioactive;
    }

    double getX() {
        return x;
    }

    double getY() {
        return y;
    }

    int getWidth() {
        return width;
    }

    double getCenterX() {
        return x + width / 2.0;
    }

    int getHeight() {
        return height;
    }

    Rectangle2D.Double getBounds() {
        return new Rectangle2D.Double(x, y, width, height);
    }

    void draw(Graphics2D g2) {
        int drawX = (int) Math.round(x);
        int drawY = (int) Math.round(y);

        if (radioactive) {
            g2.setColor(new Color(110, 255, 170, 140));
            g2.fillRoundRect(drawX - 6, drawY - 4, width + 12, height + 8, 28, 28);
            g2.setColor(new Color(170, 255, 200, 210));
            g2.fillRoundRect(drawX - 2, drawY - 1, width + 4, height + 2, 24, 24);
        } else if (onFire) {
            g2.setColor(new Color(255, 120, 0, 160));
            g2.fillRoundRect(drawX - 6, drawY - 4, width + 12, height + 8, 28, 28);
            g2.setColor(new Color(255, 200, 60, 200));
            g2.fillRoundRect(drawX - 2, drawY - 1, width + 4, height + 2, 24, 24);
        }

        Color bodyColor;
        if (radioactive) {
            bodyColor = new Color(200, 255, 215);
        } else if (onFire) {
            bodyColor = new Color(255, 245, 220);
        } else {
            bodyColor = new Color(0xF0F0F0);
        }
        g2.setColor(bodyColor);
        g2.fillRoundRect(drawX, drawY, width, height, 20, 20);
        Color borderColor;
        if (radioactive) {
            borderColor = new Color(100, 210, 140);
        } else if (onFire) {
            borderColor = new Color(255, 150, 60);
        } else {
            borderColor = new Color(220, 220, 220);
        }
        g2.setColor(borderColor);
        g2.drawRoundRect(drawX, drawY, width, height, 20, 20);
    }
}
