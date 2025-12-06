package org.example;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

final class GamePanel extends JPanel implements ActionListener, KeyListener {
    private enum GameState {
        READY,
        RUNNING,
        PAUSED,
        LEVEL_COMPLETE,
        GAME_OVER
    }

    private enum BonusType {
        PADDLE_GROW("1", "Raquette XL", "Allonge la raquette et booste sa vitesse pendant 15s", 5, 15),
        PIERCE_BALL("2", "Balle percante", "Traverse les briques pendant 10s", 6, 10),
        SCORE_BOOST("3", "Score x2", "Double les points pendant 20s", 4, 20);

        private final String keyLabel;
        private final String label;
        private final String description;
        private final int cost;
        private final int durationSeconds;

        BonusType(String keyLabel, String label, String description, int cost, int durationSeconds) {
            this.keyLabel = keyLabel;
            this.label = label;
            this.description = description;
            this.cost = cost;
            this.durationSeconds = durationSeconds;
        }

        int durationFrames() {
            return durationSeconds * FRAMES_PER_SECOND;
        }
    }

    private static final int PANEL_WIDTH = 800;
    private static final int PANEL_HEIGHT = 600;
    private static final int FRAMES_PER_SECOND = 60;
    private static final int TIMER_DELAY = 1000 / FRAMES_PER_SECOND;
    private static final double BASE_BALL_SPEED = 5.0;
    private static final double SPEED_INCREMENT = 0.4;
    private static final double MAX_BALL_SPEED = 11.0;
    private static final int BASE_PADDLE_WIDTH = 120;
    private static final int BASE_PADDLE_HEIGHT = 16;
    private static final double BASE_PADDLE_SPEED = 6.5;
    private static final int CHEAT_PADDLE_WIDTH = (int) Math.round(BASE_PADDLE_WIDTH * 1.7);
    private static final double CHEAT_PADDLE_SPEED = BASE_PADDLE_SPEED * 1.75;
    private static final int EXPLOSION_LIFETIME = 18;
    private static final Color[] BRICK_COLORS = {
        new Color(0xF94144),
        new Color(0xF3722C),
        new Color(0xF8961E),
        new Color(0xF9844A),
        new Color(0xF9C74F),
        new Color(0x90BE6D),
        new Color(0x43AA8B),
        new Color(0x577590),
        new Color(0x9D4EDD)
    };
    private static final int STAR_COUNT = 140;
    private static final int NEBULA_COUNT = 3;
    private static final double MAX_PADDLE_DEFLECTION = Math.toRadians(70);
    private static final double AUTO_PILOT_SPEED_MULTIPLIER = 2.0;
    private static final double[] AUTOPILOT_RATIOS = {
        0.08, 0.16, 0.24, 0.32, 0.40, 0.50, 0.60, 0.68, 0.76, 0.84, 0.92
    };
    private static final int AUTOPILOT_MAX_LOOKAHEAD_EVENTS = 12;
    private static final double AUTOPILOT_MAX_SIMULATION_TIME = 6.8;
    private static final double AUTOPILOT_EPSILON = 1e-6;
    private static final double AUTO_PILOT_GUIDANCE_STRENGTH = 0.65;
    private static final int SCORE_HISTORY_LIMIT = 5;

    private final Timer timer;
    private final Paddle paddle;
    private final Ball ball;
    private final List<Brick> bricks = new ArrayList<>();
    private final List<Explosion> explosions = new ArrayList<>();
    private final List<ActiveBonus> activeBonuses = new ArrayList<>();
    private final List<SpaceStar> stars = new ArrayList<>();
    private final List<Nebula> nebulas = new ArrayList<>();
    private final List<ScoreRecord> scoreRecords = new ArrayList<>();
    private final Random random = new Random();

    private GameState gameState = GameState.READY;
    private boolean leftPressed;
    private boolean rightPressed;
    private boolean helpVisible = true;
    private boolean cheatMode;
    private boolean autoPilotMode;
    private boolean autoPilotAimValid;
    private boolean pauseMenuVisible;
    private double autoPilotAimX = PANEL_WIDTH / 2.0;
    private GameState stateBeforePause = GameState.READY;

    private int score;
    private int lives;
    private int level;
    private int credits;
    private int personalBestScore;
    private int personalBestLevel = 1;
    private int personalBestCredits;
    private boolean scoreRecordedThisRun;

    private boolean shopOpen;
    private String shopMessage = "";
    private int shopMessageTimer;

    GamePanel() {
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        int paddleWidth = BASE_PADDLE_WIDTH;
        int paddleHeight = BASE_PADDLE_HEIGHT;
        double paddleStartX = (PANEL_WIDTH - paddleWidth) / 2.0;
        double paddleY = PANEL_HEIGHT - 70;
        paddle = new Paddle(paddleStartX, paddleY, paddleWidth, paddleHeight, BASE_PADDLE_SPEED);

        int ballDiameter = 18;
        double ballCenterX = paddleStartX + paddleWidth / 2.0;
        double ballCenterY = paddleY - ballDiameter;
        ball = new Ball(ballCenterX, ballCenterY, ballDiameter, new Color(0xFFD966));

        initializeSpaceElements();

        timer = new Timer(TIMER_DELAY, this);
        timer.start();

        startNewGame();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isDisplayable()) {
            timer.stop();
            return;
        }
        updateGame();
    }

    private void updateGame() {
        tickShopMessage();
        updateStars();
        ensureValidPaddlePosition();
        ensureValidBallPosition();
        if (shopOpen) {
            repaint();
            Toolkit.getDefaultToolkit().sync();
            return;
        }

        if (autoPilotMode && gameState == GameState.READY && ball.getSpeed() == 0) {
            launchBall();
        }

        updatePaddle();

        if (gameState == GameState.READY) {
            ball.setCenter(paddle.getX() + paddle.getWidth() / 2.0, paddle.getY() - ball.getDiameter() / 2.0 - 4);
        } else if (gameState == GameState.RUNNING) {
            if (autoPilotMode) {
                applyAutoPilotGuidance();
            }
            ball.move();
            constrainBallToField();
            handlePaddleCollision();
            handleBrickCollisions();

            if (bricks.isEmpty()) {
                addScore(500 + 200 * level);
                gameState = GameState.LEVEL_COMPLETE;
            }
        }

        updateActiveBonuses();
        updateExplosions();
        repaint();
        Toolkit.getDefaultToolkit().sync();
    }

    private void updatePaddle() {
        if (gameState == GameState.GAME_OVER || gameState == GameState.LEVEL_COMPLETE || gameState == GameState.PAUSED) {
            return;
        }
        if (autoPilotMode) {
            runAutoPilot();
            return;
        }
        if (leftPressed && !rightPressed) {
            paddle.move(-1, PANEL_WIDTH);
        } else if (rightPressed && !leftPressed) {
            paddle.move(1, PANEL_WIDTH);
        }
        ensureValidPaddlePosition();
    }

    private void runAutoPilot() {
        double halfWidth = paddle.getWidth() / 2.0;
        double desiredCenter = planAutoPilotCenter();
        if (!Double.isFinite(desiredCenter)) {
            desiredCenter = PANEL_WIDTH / 2.0;
        }
        double clampedCenter = clamp(desiredCenter, halfWidth, PANEL_WIDTH - halfWidth);
        if (!Double.isFinite(clampedCenter)) {
            clampedCenter = PANEL_WIDTH / 2.0;
        }
        double paddleSpeed = paddle.getSpeed();
        if (paddleSpeed <= 1e-3) {
            paddle.setCenter(clampedCenter);
            ensureValidPaddlePosition();
            return;
        }

        double currentCenter = paddle.getCenterX();
        double delta = clampedCenter - currentCenter;
        double tolerance = Math.max(1.2, paddle.getWidth() * 0.015);

        if (Math.abs(delta) <= tolerance) {
            paddle.setCenter(clampedCenter);
            ensureValidPaddlePosition();
            return;
        }

        double directionLimit = autoPilotMode ? Math.min(autoPilotSpeedMultiplier(), 1.6) : 1.0;
        double maxStep = paddleSpeed * directionLimit;
        double distance = Math.abs(delta);
        double targetStep = Math.min(distance, maxStep);
        double normalizedDirection = Math.signum(delta) * (targetStep / paddleSpeed);
        if (!Double.isFinite(normalizedDirection)) {
            normalizedDirection = 0;
        }
        normalizedDirection = clamp(normalizedDirection, -directionLimit, directionLimit);

        paddle.move(normalizedDirection, PANEL_WIDTH);

        double newCenter = paddle.getCenterX();
        if (Math.abs(clampedCenter - newCenter) <= tolerance) {
            paddle.setCenter(clampedCenter);
        }
        ensureValidPaddlePosition();
    }

    private void applyAutoPilotGuidance() {
        if (!autoPilotMode || bricks.isEmpty()) {
            return;
        }
        if (ball.getSpeed() <= AUTOPILOT_EPSILON) {
            return;
        }
        if (ball.getVelocityY() >= 0) {
            return;
        }

        double referenceX = autoPilotAimValid ? autoPilotAimX : ball.getCenterX();
        Brick target = selectPriorityBrick(referenceX);
        if (target == null) {
            return;
        }

        Rectangle2D.Double bounds = target.getBounds();
        double dx = bounds.getCenterX() - ball.getCenterX();
        double dy = bounds.getCenterY() - ball.getCenterY();
        double distance = Math.hypot(dx, dy);
        if (distance < AUTOPILOT_EPSILON) {
            return;
        }

        double minSpeed = minimumBallSpeed() * autoPilotSpeedMultiplier();
        double maxSpeed = effectiveMaxSpeed();
        double desiredSpeed = clamp(ball.getSpeed(), minSpeed, maxSpeed);
        if (desiredSpeed <= AUTOPILOT_EPSILON) {
            desiredSpeed = minSpeed;
        }

        double desiredVelocityX = dx / distance * desiredSpeed;
        double desiredVelocityY = dy / distance * desiredSpeed;
        if (desiredVelocityY >= -AUTOPILOT_EPSILON) {
            desiredVelocityY = -Math.abs(desiredVelocityY) - 0.5;
        }

        double blendedVelocityX = lerp(ball.getVelocityX(), desiredVelocityX, AUTO_PILOT_GUIDANCE_STRENGTH);
        double blendedVelocityY = lerp(ball.getVelocityY(), desiredVelocityY, AUTO_PILOT_GUIDANCE_STRENGTH);
        double blendedSpeed = Math.hypot(blendedVelocityX, blendedVelocityY);
        if (blendedSpeed < AUTOPILOT_EPSILON) {
            return;
        }

        double finalSpeed = clamp(blendedSpeed, minSpeed, maxSpeed);
        double scale = finalSpeed / blendedSpeed;
        ball.setVelocity(blendedVelocityX * scale, blendedVelocityY * scale);
    }

    private double planAutoPilotCenter() {
        double interceptX = predictImpactX();
        double speed = ball.getSpeed();
        if (gameState != GameState.RUNNING || speed <= 0.01) {
            return aimForBrick(ball.getCenterX(), interceptX);
        }

        ShotPlan plan = planBestShot(interceptX);
        if (plan != null) {
            return rememberAutoPilotAim(plan.targetCenter());
        }
        return aimForBrick(ball.getCenterX(), interceptX);
    }

    private double aimForBrick(double referenceX, double fallbackX) {
        Brick priority = selectPriorityBrick(referenceX);
        if (priority != null) {
            double aim = priority.getBounds().getCenterX();
            return rememberAutoPilotAim(aim);
        }
        if (autoPilotAimValid) {
            return autoPilotAimX;
        }
        return fallbackX;
    }

    private double rememberAutoPilotAim(double aim) {
        autoPilotAimX = clamp(aim, paddle.getWidth() / 2.0, PANEL_WIDTH - paddle.getWidth() / 2.0);
        autoPilotAimValid = true;
        return autoPilotAimX;
    }

    private double predictImpactX() {
        double radius = ball.getDiameter() / 2.0;
        double targetY = paddle.getY() - radius;
        double centerX = ball.getCenterX();
        double centerY = ball.getCenterY();
        double velocityX = ball.getVelocityX();
        double velocityY = ball.getVelocityY();

        if (Math.abs(velocityX) < 1e-6 && Math.abs(velocityY) < 1e-6) {
            return centerX;
        }

        if (velocityY > 0.01) {
            double distance = Math.max(0, targetY - centerY);
            double time = distance / velocityY;
            double projected = centerX + velocityX * time;
            return reflectWithinWalls(projected, radius);
        }

        if (velocityY < -0.01) {
            double absVy = Math.abs(velocityY);
            double distanceUp = Math.max(0, centerY - radius);
            double distanceDown = Math.max(0, targetY - radius);
            double totalTime = (distanceUp + distanceDown) / absVy;
            double projected = centerX + velocityX * totalTime;
            return reflectWithinWalls(projected, radius);
        }

        return centerX;
    }

    private Brick selectPriorityBrick(double referenceX) {
        Brick best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Brick brick : bricks) {
            if (brick.isDestroyed()) {
                continue;
            }
            Rectangle2D.Double bounds = brick.getBounds();
            double baseValue = evaluateBrickValue(brick);
            double alignment = 1.0 - clamp(Math.abs(bounds.getCenterX() - referenceX) / (PANEL_WIDTH * 0.5), 0.0, 1.0);
            double score = baseValue * 5.0 + alignment * 2.2;
            if (score > bestScore) {
                bestScore = score;
                best = brick;
            }
        }
        return best;
    }

    private ShotPlan planBestShot(double interceptX) {
        double ballRadius = ball.getDiameter() / 2.0;
        double paddleTop = paddle.getY();
        double strikeY = paddleTop - ballRadius - 0.5;
        double baseSpeed = Math.min(effectiveMaxSpeed(), (minimumBallSpeed() + level * 0.5) * autoPilotSpeedMultiplier());
        double currentCenter = paddle.getCenterX();
        double paddleWidth = paddle.getWidth();
        boolean piercing = hasPiercingBall();

        ShotPlan bestPlan = null;
        for (double candidateRatio : AUTOPILOT_RATIOS) {
            double ratio = clamp(candidateRatio, 0.08, 0.92);
            double angle = (ratio - 0.5) * 2.0 * MAX_PADDLE_DEFLECTION;
            double velocityX = baseSpeed * Math.sin(angle);
            double velocityY = -Math.abs(baseSpeed * Math.cos(angle));
            ShotSimulationResult result = simulateShot(interceptX, strikeY, velocityX, velocityY, ballRadius, piercing);
            double targetCenter = interceptX + (0.5 - ratio) * paddleWidth;
            double score = evaluateShotResult(result, targetCenter, currentCenter);
            if (!Double.isFinite(score)) {
                continue;
            }
            if (bestPlan == null || score > bestPlan.score()) {
                bestPlan = new ShotPlan(targetCenter, ratio, score);
            }
        }
        return bestPlan;
    }

    private ShotSimulationResult simulateShot(double startX, double startY, double velocityX, double velocityY, double radius, boolean piercing) {
        double x = startX;
        double y = startY;
        double vx = velocityX;
        double vy = velocityY;
        double timeElapsed = 0.0;
        int bounceCount = 0;
        List<Brick> hitBricks = new ArrayList<>();
        List<Double> impactTimes = new ArrayList<>();
        Set<Brick> removedBricks = piercing ? new HashSet<>() : null;

        for (int iteration = 0; iteration < AUTOPILOT_MAX_LOOKAHEAD_EVENTS; iteration++) {
            Collision collision = findNextCollision(x, y, vx, vy, radius, removedBricks);
            if (collision == null) {
                break;
            }
            double dt = Math.max(collision.time, AUTOPILOT_EPSILON);
            x += vx * dt;
            y += vy * dt;
            timeElapsed += dt;
            if (timeElapsed > AUTOPILOT_MAX_SIMULATION_TIME) {
                break;
            }

            switch (collision.type) {
                case FLOOR -> {
                    return ShotSimulationResult.miss(timeElapsed, true, hitBricks, impactTimes, bounceCount, vx, vy);
                }
                case CEILING -> {
                    vy = -vy;
                    y = radius;
                    bounceCount++;
                }
                case WALL -> {
                    vx = -vx;
                    x = clamp(x, radius, PANEL_WIDTH - radius);
                    bounceCount++;
                }
                case BRICK -> {
                    Brick brick = collision.brick;
                    hitBricks.add(brick);
                    impactTimes.add(timeElapsed);
                    if (piercing) {
                        if (removedBricks != null) {
                            removedBricks.add(brick);
                        }
                        if (hitBricks.size() >= 6) {
                            return ShotSimulationResult.hit(hitBricks, impactTimes, timeElapsed, bounceCount, vx, vy);
                        }
                    } else {
                        if (collision.normalX != 0) {
                            vx = -vx;
                        }
                        if (collision.normalY != 0) {
                            vy = -vy;
                        }
                        return ShotSimulationResult.hit(hitBricks, impactTimes, timeElapsed, bounceCount, vx, vy);
                    }
                }
            }
        }

        if (!hitBricks.isEmpty()) {
            return ShotSimulationResult.hit(hitBricks, impactTimes, timeElapsed, bounceCount, vx, vy);
        }
        return ShotSimulationResult.miss(timeElapsed, false, hitBricks, impactTimes, bounceCount, vx, vy);
    }

    private Collision findNextCollision(double x, double y, double vx, double vy, double radius, Set<Brick> ignoredBricks) {
        double minTime = Double.POSITIVE_INFINITY;
        Collision best = null;

        if (vy > AUTOPILOT_EPSILON) {
            double time = (PANEL_HEIGHT - radius - y) / vy;
            if (time >= AUTOPILOT_EPSILON && time < minTime) {
                minTime = time;
                best = new Collision(CollisionType.FLOOR, time, 0.0, -1.0, null);
            }
        }

        if (vy < -AUTOPILOT_EPSILON) {
            double time = (radius - y) / vy;
            if (time >= AUTOPILOT_EPSILON && time < minTime) {
                minTime = time;
                best = new Collision(CollisionType.CEILING, time, 0.0, 1.0, null);
            }
        }

        if (vx < -AUTOPILOT_EPSILON) {
            double time = (radius - x) / vx;
            if (time >= AUTOPILOT_EPSILON && time < minTime) {
                minTime = time;
                best = new Collision(CollisionType.WALL, time, 1.0, 0.0, null);
            }
        }

        if (vx > AUTOPILOT_EPSILON) {
            double time = (PANEL_WIDTH - radius - x) / vx;
            if (time >= AUTOPILOT_EPSILON && time < minTime) {
                minTime = time;
                best = new Collision(CollisionType.WALL, time, -1.0, 0.0, null);
            }
        }

        for (Brick brick : bricks) {
            if (brick.isDestroyed()) {
                continue;
            }
            if (ignoredBricks != null && ignoredBricks.contains(brick)) {
                continue;
            }
            Collision collision = findBrickCollision(brick, x, y, vx, vy, radius);
            if (collision != null && collision.time < minTime) {
                minTime = collision.time;
                best = collision;
            }
        }
        return best;
    }

    private Collision findBrickCollision(Brick brick, double startX, double startY, double vx, double vy, double radius) {
        Rectangle2D.Double bounds = brick.getBounds();
        double minX = bounds.x - radius;
        double maxX = bounds.x + bounds.width + radius;
        double minY = bounds.y - radius;
        double maxY = bounds.y + bounds.height + radius;

        double enterX = Double.NEGATIVE_INFINITY;
        double exitX = Double.POSITIVE_INFINITY;
        double enterY = Double.NEGATIVE_INFINITY;
        double exitY = Double.POSITIVE_INFINITY;

        if (Math.abs(vx) < AUTOPILOT_EPSILON) {
            if (startX <= minX || startX >= maxX) {
                return null;
            }
        } else {
            double inv = 1.0 / vx;
            double t1 = (minX - startX) * inv;
            double t2 = (maxX - startX) * inv;
            enterX = Math.min(t1, t2);
            exitX = Math.max(t1, t2);
        }

        if (Math.abs(vy) < AUTOPILOT_EPSILON) {
            if (startY <= minY || startY >= maxY) {
                return null;
            }
        } else {
            double inv = 1.0 / vy;
            double t1 = (minY - startY) * inv;
            double t2 = (maxY - startY) * inv;
            enterY = Math.min(t1, t2);
            exitY = Math.max(t1, t2);
        }

        double entry = Math.max(Math.max(enterX, enterY), 0.0);
        double exit = Math.min(exitX, exitY);
        if (exit < entry || exit < AUTOPILOT_EPSILON) {
            return null;
        }

        double normalX = 0.0;
        double normalY = 0.0;
        if (enterX > enterY) {
            normalX = vx > 0 ? -1.0 : 1.0;
        } else if (enterY > enterX) {
            normalY = vy > 0 ? -1.0 : 1.0;
        } else {
            normalX = vx > 0 ? -1.0 : 1.0;
            normalY = vy > 0 ? -1.0 : 1.0;
        }

        return new Collision(CollisionType.BRICK, entry, normalX, normalY, brick);
    }

    private double evaluateShotResult(ShotSimulationResult result, double targetCenter, double currentCenter) {
        if (!result.hitBrick()) {
            double penaltyBase = result.missedByFloor() ? 260.0 : 120.0;
            double timePenalty = result.totalTime() * 9.0;
            double movementPenalty = Math.abs(targetCenter - currentCenter) * 0.02;
            return -penaltyBase - timePenalty - movementPenalty;
        }

        List<Brick> hitBricks = result.hitBricks();
        List<Double> impactTimes = result.impactTimes();
        if (hitBricks.isEmpty()) {
            return -100.0;
        }

        double valueScore = 0.0;
        for (int i = 0; i < hitBricks.size(); i++) {
            double brickValue = evaluateBrickValue(hitBricks.get(i));
            double timeWeight = 1.0 / (1.0 + impactTimes.get(i) * 0.55);
            valueScore += brickValue * timeWeight;
        }

        Brick primary = hitBricks.get(0);
        Rectangle2D.Double bounds = primary.getBounds();
        double exitHorizontal = Math.abs(result.exitVelocityX());
        double exitVertical = Math.abs(result.exitVelocityY());
        double alignmentPenalty = Math.abs(bounds.getCenterX() - targetCenter) * 0.012;
        double travelPenalty = Math.abs(targetCenter - currentCenter) * 0.016;
        double horizontalBias = exitHorizontal / Math.max(0.4, exitVertical);
        double lowHorizontalDeficit = Math.max(0.0, 1.4 - exitHorizontal);

        double score = valueScore * 8.4;
        score += exitHorizontal * 4.2;
        score += horizontalBias * 6.0;
        score -= lowHorizontalDeficit * 9.0;
        score += Math.max(0.0, exitVertical) * 0.6;
        score -= Math.max(0.0, result.exitVelocityY()) * 3.4;
        score -= result.totalTime() * 1.6;
        score -= result.bounces() * 1.3;
        score -= alignmentPenalty;
        score -= travelPenalty;

        if (hitBricks.size() > 1) {
            score += (hitBricks.size() - 1) * 6.0;
        }
        if (hitBricks.size() >= 3) {
            score += 5.5;
        }
        return score;
    }

    private double evaluateBrickValue(Brick brick) {
        Rectangle2D.Double bounds = brick.getBounds();
        double normalizedHeight = 1.0 - clamp(bounds.y / (PANEL_HEIGHT * 0.9), 0.0, 1.0);
        double centerX = bounds.getCenterX();
        double proximityToCenter = 1.0 - clamp(Math.abs(centerX - PANEL_WIDTH / 2.0) / (PANEL_WIDTH / 2.0), 0.0, 1.0);
        double density = computeLocalBrickDensity(centerX, bounds.getCenterY(), 150.0);
        double laneValue = computeLaneClearValue(centerX, bounds.y);
        double edgeValue = computeEdgeClearValue(bounds);
        double toughness = brick.getRemainingHits() > 1 ? brick.getRemainingHits() * 0.45 : 0.0;
        return normalizedHeight * 4.4 + density * 1.8 + laneValue * 1.3 + proximityToCenter * 0.5 + edgeValue * 1.1 + toughness;
    }

    private double computeLaneClearValue(double centerX, double brickTopY) {
        double laneHalfWidth = Math.max(50.0, paddle.getWidth() * 0.25);
        int blockers = 0;
        for (Brick brick : bricks) {
            if (brick.isDestroyed()) {
                continue;
            }
            Rectangle2D.Double bounds = brick.getBounds();
            double bx = bounds.getCenterX();
            if (bx < centerX - laneHalfWidth || bx > centerX + laneHalfWidth) {
                continue;
            }
            if (bounds.y + bounds.height <= brickTopY - AUTOPILOT_EPSILON) {
                blockers++;
            }
        }
        return Math.max(0.0, 1.5 - blockers * 0.28);
    }

    private double computeLocalBrickDensity(double centerX, double centerY, double radius) {
        double score = 0.0;
        double radiusSquared = radius * radius;
        for (Brick brick : bricks) {
            if (brick.isDestroyed()) {
                continue;
            }
            Rectangle2D.Double bounds = brick.getBounds();
            double dx = bounds.getCenterX() - centerX;
            double dy = bounds.getCenterY() - centerY;
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared > radiusSquared) {
                continue;
            }
            double weight = Math.exp(-distanceSquared / (radiusSquared * 0.6));
            score += weight;
        }
        return score;
    }

    private double computeEdgeClearValue(Rectangle2D.Double bounds) {
        double leftDistance = Math.max(0.0, bounds.x);
        double rightDistance = Math.max(0.0, PANEL_WIDTH - (bounds.x + bounds.width));
        double proximity = Math.min(leftDistance, rightDistance);
        double normalized = 1.0 - clamp(proximity / (PANEL_WIDTH * 0.45), 0.0, 1.0);
        return normalized;
    }

    private static final class ShotPlan {
        private final double targetCenter;
        private final double ratio;
        private final double score;

        ShotPlan(double targetCenter, double ratio, double score) {
            this.targetCenter = targetCenter;
            this.ratio = ratio;
            this.score = score;
        }

        double targetCenter() {
            return targetCenter;
        }

        double ratio() {
            return ratio;
        }

        double score() {
            return score;
        }
    }

    private static final class ShotSimulationResult {
        private final boolean hitBrick;
        private final boolean missedByFloor;
        private final double totalTime;
        private final int bounces;
        private final double exitVelocityX;
        private final double exitVelocityY;
        private final List<Brick> hitBricks;
        private final List<Double> impactTimes;

        private ShotSimulationResult(boolean hitBrick, boolean missedByFloor, double totalTime, int bounces,
                                     double exitVelocityX, double exitVelocityY,
                                     List<Brick> hitBricks, List<Double> impactTimes) {
            this.hitBrick = hitBrick;
            this.missedByFloor = missedByFloor;
            this.totalTime = totalTime;
            this.bounces = bounces;
            this.exitVelocityX = exitVelocityX;
            this.exitVelocityY = exitVelocityY;
            this.hitBricks = hitBricks;
            this.impactTimes = impactTimes;
        }

        static ShotSimulationResult hit(List<Brick> hitBricks, List<Double> impactTimes, double totalTime, int bounces,
                                        double exitVelocityX, double exitVelocityY) {
            return new ShotSimulationResult(true, false, totalTime, bounces, exitVelocityX, exitVelocityY,
                List.copyOf(hitBricks), List.copyOf(impactTimes));
        }

        static ShotSimulationResult miss(double totalTime, boolean fell, List<Brick> hitBricks, List<Double> impactTimes,
                                         int bounces, double exitVelocityX, double exitVelocityY) {
            return new ShotSimulationResult(false, fell, totalTime, bounces, exitVelocityX, exitVelocityY,
                List.copyOf(hitBricks), List.copyOf(impactTimes));
        }

        boolean hitBrick() {
            return hitBrick;
        }

        boolean missedByFloor() {
            return missedByFloor;
        }

        double totalTime() {
            return totalTime;
        }

        int bounces() {
            return bounces;
        }

        double exitVelocityX() {
            return exitVelocityX;
        }

        double exitVelocityY() {
            return exitVelocityY;
        }

        List<Brick> hitBricks() {
            return hitBricks;
        }

        List<Double> impactTimes() {
            return impactTimes;
        }
    }

    private enum CollisionType {
        BRICK,
        WALL,
        CEILING,
        FLOOR
    }

    private static final class Collision {
        private final CollisionType type;
        private final double time;
        private final double normalX;
        private final double normalY;
        private final Brick brick;

        Collision(CollisionType type, double time, double normalX, double normalY, Brick brick) {
            this.type = type;
            this.time = time;
            this.normalX = normalX;
            this.normalY = normalY;
            this.brick = brick;
        }
    }

    private double reflectWithinWalls(double projectedCenter, double radius) {
        double min = radius;
        double max = PANEL_WIDTH - radius;
        double arenaWidth = max - min;
        if (arenaWidth <= 0) {
            return PANEL_WIDTH / 2.0;
        }

        double range = arenaWidth * 2.0;
        double offset = projectedCenter - min;
        double wrapped = offset % range;
        if (wrapped < 0) {
            wrapped += range;
        }
        if (wrapped <= arenaWidth) {
            return min + wrapped;
        }
        return max - (wrapped - arenaWidth);
    }

    private double autoPilotSpeedMultiplier() {
        return autoPilotMode ? AUTO_PILOT_SPEED_MULTIPLIER : 1.0;
    }

    private double effectiveMaxSpeed() {
        return MAX_BALL_SPEED * autoPilotSpeedMultiplier();
    }

    private void applyAutoPilotSpeedBoost() {
        double speed = ball.getSpeed();
        if (speed <= 0.01) {
            return;
        }
        double minSpeed = minimumBallSpeed() * AUTO_PILOT_SPEED_MULTIPLIER;
        double target = Math.min(effectiveMaxSpeed(), speed * AUTO_PILOT_SPEED_MULTIPLIER);
        if (target < minSpeed) {
            target = minSpeed;
        }
        if (target > speed + 0.01) {
            ball.normalizeSpeed(target);
        }
    }

    private void clampBallToNormalSpeed() {
        double speed = ball.getSpeed();
        if (speed > MAX_BALL_SPEED) {
            ball.normalizeSpeed(MAX_BALL_SPEED);
        }
    }

    private void constrainBallToField() {
        double diameter = ball.getDiameter();
        if (ball.getX() <= 0 && ball.getVelocityX() < 0) {
            ball.setX(0);
            ball.bounceHorizontally();
        } else if (ball.getX() + diameter >= PANEL_WIDTH && ball.getVelocityX() > 0) {
            ball.setX(PANEL_WIDTH - diameter);
            ball.bounceHorizontally();
        }

        if (ball.getY() <= 0 && ball.getVelocityY() < 0) {
            ball.setY(0);
            ball.bounceVertically();
        } else if (ball.getY() >= PANEL_HEIGHT) {
            loseLife();
        }
    }

    private void handlePaddleCollision() {
        if (ball.getVelocityY() >= 0 && ball.getBounds().intersects(paddle.getBounds())) {
            ball.setY(paddle.getY() - ball.getDiameter() - 0.5);

            double hitPosition = (ball.getCenterX() - paddle.getX()) / paddle.getWidth();
            hitPosition = Math.max(0.05, Math.min(0.95, hitPosition));

            double maxAngle = MAX_PADDLE_DEFLECTION;
            double angle = (hitPosition - 0.5) * 2 * maxAngle;
            double baseTargetSpeed = (minimumBallSpeed() + level * 0.5) * autoPilotSpeedMultiplier();
            double targetSpeed = Math.min(effectiveMaxSpeed(), baseTargetSpeed);
            double newVelocityX = targetSpeed * Math.sin(angle);
            double newVelocityY = -Math.abs(targetSpeed * Math.cos(angle));
            ball.setVelocity(newVelocityX, newVelocityY);
        }
    }

    private void handleBrickCollisions() {
        Rectangle2D.Double ballBounds = ball.getBounds();
        Iterator<Brick> iterator = bricks.iterator();
        boolean collisionHandled = false;
        boolean piercingBall = hasPiercingBall();
        while (iterator.hasNext()) {
            Brick brick = iterator.next();
            if (brick.isDestroyed()) {
                iterator.remove();
                continue;
            }

            Rectangle2D.Double brickBounds = brick.getBounds();
            if (ballBounds.intersects(brickBounds)) {
                if (piercingBall) {
                    spawnExplosion(brickBounds);
                    iterator.remove();
                    awardCredit();
                    addScore(100);
                    collisionHandled = true;
                    continue;
                } else {
                    resolveBallBrickCollision(brickBounds);
                    boolean destroyed = brick.applyHit();
                    if (destroyed) {
                        spawnExplosion(brickBounds);
                        iterator.remove();
                        awardCredit();
                        addScore(100);
                    } else {
                        addScore(30);
                    }
                }
                collisionHandled = true;
                if (!piercingBall) {
                    break;
                }
            }
        }
        if (collisionHandled) {
            accelerateBall();
        }
    }

    private void spawnExplosion(Rectangle2D.Double brickBounds) {
        explosions.add(new Explosion(brickBounds));
    }

    private void addScore(int basePoints) {
        double multiplier = currentScoreMultiplier();
        int awarded = (int) Math.round(basePoints * multiplier);
        score += Math.max(0, awarded);
        updatePersonalBestProgress();
    }

    private void awardCredit() {
        credits++;
        updatePersonalBestProgress();
    }

    private double currentScoreMultiplier() {
        return isBonusActive(BonusType.SCORE_BOOST) ? 2.0 : 1.0;
    }

    private boolean hasPiercingBall() {
        return cheatMode || isBonusActive(BonusType.PIERCE_BALL);
    }

    private void updateActiveBonuses() {
        if (activeBonuses.isEmpty()) {
            return;
        }
        boolean modified = false;
        if (gameState == GameState.RUNNING || gameState == GameState.READY) {
            Iterator<ActiveBonus> iterator = activeBonuses.iterator();
            while (iterator.hasNext()) {
                ActiveBonus bonus = iterator.next();
                bonus.remainingFrames--;
                if (bonus.remainingFrames <= 0) {
                    iterator.remove();
                    modified = true;
                }
            }
        }
        if (modified) {
            refreshPlayerModifiers();
        }
    }

    private void updateExplosions() {
        Iterator<Explosion> iterator = explosions.iterator();
        while (iterator.hasNext()) {
            Explosion explosion = iterator.next();
            if (explosion.update()) {
                iterator.remove();
            }
        }
    }

    private void resolveBallBrickCollision(Rectangle2D.Double brickBounds) {
        double ballLeft = ball.getX();
        double ballRight = ball.getX() + ball.getDiameter();
        double ballTop = ball.getY();
        double ballBottom = ball.getY() + ball.getDiameter();

        double overlapLeft = ballRight - brickBounds.x;
        double overlapRight = brickBounds.x + brickBounds.width - ballLeft;
        double overlapTop = ballBottom - brickBounds.y;
        double overlapBottom = brickBounds.y + brickBounds.height - ballTop;

        double minHorizontal = Math.min(overlapLeft, overlapRight);
        double minVertical = Math.min(overlapTop, overlapBottom);

        if (minHorizontal < minVertical) {
            if (overlapLeft < overlapRight) {
                ball.setX(brickBounds.x - ball.getDiameter() - 0.5);
            } else {
                ball.setX(brickBounds.x + brickBounds.width + 0.5);
            }
            ball.bounceHorizontally();
        } else {
            if (overlapTop < overlapBottom) {
                ball.setY(brickBounds.y - ball.getDiameter() - 0.5);
            } else {
                ball.setY(brickBounds.y + brickBounds.height + 0.5);
            }
            ball.bounceVertically();
        }
    }

    private void accelerateBall() {
        double currentSpeed = ball.getSpeed();
        double minSpeed = minimumBallSpeed() * autoPilotSpeedMultiplier();
        if (currentSpeed < minSpeed) {
            ball.normalizeSpeed(minSpeed);
        } else {
            double increment = SPEED_INCREMENT * autoPilotSpeedMultiplier();
            double boostedSpeed = Math.min(effectiveMaxSpeed(), currentSpeed + increment);
            ball.normalizeSpeed(boostedSpeed);
        }
    }

    private double minimumBallSpeed() {
        return BASE_BALL_SPEED + (level - 1) * 0.4;
    }

    private void loseLife() {
        lives--;
        if (lives <= 0) {
            recordCurrentRun("Game Over");
            gameState = GameState.GAME_OVER;
        } else {
            resetRound();
        }
    }

    private void resetRound() {
        pauseMenuVisible = false;
        stateBeforePause = GameState.READY;
        paddle.setCenter(PANEL_WIDTH / 2.0);
        refreshPlayerModifiers();
        ball.setCenter(paddle.getX() + paddle.getWidth() / 2.0, paddle.getY() - ball.getDiameter() / 2.0 - 4);
        ball.setVelocity(0, 0);
        gameState = GameState.READY;
    }

    private void recordCurrentRun(String note) {
        if (scoreRecordedThisRun) {
            return;
        }
        if (score <= 0 && level <= 1 && credits <= 0) {
            return;
        }
        updatePersonalBestProgress();
        scoreRecordedThisRun = true;
        ScoreRecord record = new ScoreRecord(Math.max(0, score), Math.max(1, level), note);
        scoreRecords.add(record);
        scoreRecords.sort((a, b) -> Integer.compare(b.score, a.score));
        while (scoreRecords.size() > SCORE_HISTORY_LIMIT) {
            scoreRecords.remove(scoreRecords.size() - 1);
        }
        personalBestScore = Math.max(personalBestScore, record.score);
        personalBestLevel = Math.max(personalBestLevel, record.level);
        personalBestCredits = Math.max(personalBestCredits, credits);
    }

    private void updatePersonalBestProgress() {
        if (score > personalBestScore) {
            personalBestScore = score;
        }
        if (level > personalBestLevel) {
            personalBestLevel = level;
        }
        if (credits > personalBestCredits) {
            personalBestCredits = credits;
        }
    }

    private void startNewGame() {
        if (!scoreRecordedThisRun && (score > 0 || level > 1 || credits > 0)) {
            recordCurrentRun("Abandon");
        }
        score = 0;
        lives = 3;
        level = 1;
        cheatMode = false;
        autoPilotMode = false;
        credits = 0;
        shopOpen = false;
        shopMessage = "";
        shopMessageTimer = 0;
        activeBonuses.clear();
        autoPilotAimValid = false;
        autoPilotAimX = PANEL_WIDTH / 2.0;
        pauseMenuVisible = false;
        stateBeforePause = GameState.READY;
        initializeSpaceElements();
        refreshPlayerModifiers();
        buildLevel();
        resetRound();
        scoreRecordedThisRun = false;
    }

    private void startNextLevel() {
        level++;
        updatePersonalBestProgress();
        buildLevel();
        autoPilotAimValid = false;
        resetRound();
        gameState = GameState.READY;
    }

    private void initializeSpaceElements() {
        stars.clear();
        nebulas.clear();

        for (int i = 0; i < STAR_COUNT; i++) {
            double x = random.nextDouble() * PANEL_WIDTH;
            double y = random.nextDouble() * PANEL_HEIGHT;
            double size = 1.0 + random.nextDouble() * 2.5;
            double halo = size + 2.0 + random.nextDouble() * 4.0;
            int baseAlpha = 140 + random.nextInt(80);
            int alphaRange = 30 + random.nextInt(90);
            double twinkleSpeed = 0.01 + random.nextDouble() * 0.04;
            double phase = random.nextDouble() * Math.PI * 2;
            int tintRed = 200 + random.nextInt(55);
            int tintGreen = 200 + random.nextInt(55);
            stars.add(new SpaceStar(x, y, size, halo, baseAlpha, alphaRange, twinkleSpeed, phase, tintRed, tintGreen));
        }

        Color[] palette = {
            new Color(120, 70, 210, 180),
            new Color(70, 120, 220, 180),
            new Color(190, 80, 180, 180),
            new Color(90, 150, 200, 180)
        };

        for (int i = 0; i < NEBULA_COUNT; i++) {
            double centerX = random.nextDouble() * PANEL_WIDTH;
            double centerY = random.nextDouble() * PANEL_HEIGHT * 0.75;
            double radius = 160 + random.nextDouble() * 220;
            Color base = palette[random.nextInt(palette.length)];
            Color inner = new Color(base.getRed(), base.getGreen(), base.getBlue(), 200);
            Color mid = new Color(
                Math.min(255, base.getRed() + 20),
                Math.min(255, base.getGreen() + 20),
                Math.min(255, base.getBlue() + 20),
                70
            );
            nebulas.add(new Nebula(centerX, centerY, radius, inner, mid));
        }
    }

    private void updateStars() {
        for (SpaceStar star : stars) {
            star.update();
        }
    }

    private void buildLevel() {
        bricks.clear();
        explosions.clear();

        LevelConfig config = determineLevelConfig();
        int horizontalGap = 6;
        int verticalGap = 10;
        int topOffset = 70;
        int sidePadding = 36;

        double availableWidth = PANEL_WIDTH - sidePadding * 2.0 - horizontalGap * (config.columns - 1);
        if (availableWidth <= 0) {
            buildFallbackLevel();
            return;
        }
        double brickWidth = availableWidth / config.columns;
        double brickHeight = 24;

        int totalPlaced = 0;
        for (int row = 0; row < config.rows; row++) {
            if (row > 0 && random.nextDouble() < config.gapChance) {
                continue;
            }
            boolean[] pattern = generateRowPattern(config, row);
            int rowPlaced = 0;
            for (int col = 0; col < config.columns; col++) {
                if (!pattern[col]) {
                    continue;
                }
                double x = sidePadding + col * (brickWidth + horizontalGap);
                double y = topOffset + row * (brickHeight + verticalGap);
                int hits = determineBrickHits(config, row);
                Color color = chooseBrickColor(hits, row);
                bricks.add(new Brick(x, y, brickWidth, brickHeight, color, hits));
                rowPlaced++;
            }
            totalPlaced += rowPlaced;
        }

        if (totalPlaced == 0) {
            buildFallbackLevel();
        }
    }

    private LevelConfig determineLevelConfig() {
        int stage = Math.max(0, level - 1);
        int columns = Math.min(12, 8 + (int) Math.floor(stage / 2.0));
        int rows = Math.min(9, 4 + (int) Math.floor(stage / 1.5));
        double fillRate = clamp(0.55 + stage * 0.045, 0.55, 0.92);
        double rowFillGrowth = clamp(0.02 + stage * 0.004, 0.02, 0.06);
        double clusterBias = clamp(0.18 + stage * 0.015, 0.18, 0.45);
        double toughChance = clamp(0.18 + stage * 0.05, 0.18, 0.65);
        int maxHits = Math.min(4, 1 + (int) Math.floor(stage / 2.0));
        double gapChance = clamp(0.22 - stage * 0.03, 0.04, 0.22);
        double minDensity = clamp(0.18 + stage * 0.02, 0.18, 0.50);
        return new LevelConfig(columns, rows, fillRate, rowFillGrowth, clusterBias, toughChance, Math.max(1, maxHits), gapChance, minDensity);
    }

    private boolean[] generateRowPattern(LevelConfig config, int rowIndex) {
        boolean[] pattern = new boolean[config.columns];
        double rowFill = clamp(config.fillRate + config.rowFillGrowth * rowIndex, 0.20, 0.95);
        int minBricks = minimumBricksForRow(config, rowIndex);
        int bricksPlaced = 0;
        boolean previous = false;

        for (int col = 0; col < config.columns; col++) {
            double chance = rowFill;
            if (previous) {
                chance += config.clusterBias;
            } else if (col > 0 && random.nextDouble() < 0.35) {
                chance += config.clusterBias * 0.5;
            }
            chance += (random.nextDouble() - 0.5) * 0.08;
            chance = clamp(chance, 0.05, 0.98);

            boolean place = random.nextDouble() < chance;
            if (!place && bricksPlaced < minBricks) {
                double remainingSlots = config.columns - col;
                double needed = minBricks - bricksPlaced;
                if (remainingSlots <= needed || random.nextDouble() < needed / Math.max(1.0, remainingSlots)) {
                    place = true;
                }
            }

            pattern[col] = place;
            if (place) {
                bricksPlaced++;
            }
            previous = place;
        }

        if (bricksPlaced == 0) {
            int index = random.nextInt(config.columns);
            pattern[index] = true;
        } else if (bricksPlaced < minBricks) {
            List<Integer> empties = new ArrayList<>();
            for (int col = 0; col < config.columns; col++) {
                if (!pattern[col]) {
                    empties.add(col);
                }
            }
            while (bricksPlaced < minBricks && !empties.isEmpty()) {
                int idx = empties.remove(random.nextInt(empties.size()));
                pattern[idx] = true;
                bricksPlaced++;
            }
        }

        return pattern;
    }

    private int minimumBricksForRow(LevelConfig config, int rowIndex) {
        double density = clamp(config.minDensity + rowIndex * 0.04, config.minDensity, 0.75);
        return Math.max(1, (int) Math.round(config.columns * density));
    }

    private int determineBrickHits(LevelConfig config, int rowIndex) {
        if (config.maxHits <= 1) {
            return 1;
        }
        double progress = config.rows <= 1 ? 1.0 : rowIndex / (double) (config.rows - 1);
        double chance = config.toughBrickChance * (0.6 + progress * 0.6);
        int hits = 1;
        if (random.nextDouble() < chance) {
            hits++;
            while (hits < config.maxHits && random.nextDouble() < 0.45) {
                hits++;
            }
        }
        return Math.min(config.maxHits, hits);
    }

    private Color chooseBrickColor(int hits, int rowIndex) {
        Color base = BRICK_COLORS[(rowIndex + level) % BRICK_COLORS.length];
        double jitter = 0.9 + random.nextDouble() * 0.2;
        Color adjusted = adjustBrightness(base, jitter);
        if (hits >= 4) {
            adjusted = adjustBrightness(adjusted, 0.75);
        } else if (hits == 3) {
            adjusted = adjustBrightness(adjusted, 0.82);
        } else if (hits == 2) {
            adjusted = adjustBrightness(adjusted, 0.9);
        }
        return adjusted;
    }

    private Color adjustBrightness(Color base, double factor) {
        int r = clampColor((int) Math.round(base.getRed() * factor));
        int g = clampColor((int) Math.round(base.getGreen() * factor));
        int b = clampColor((int) Math.round(base.getBlue() * factor));
        return new Color(r, g, b);
    }

    private double lerp(double start, double end, double alpha) {
        double clampedAlpha = clamp(alpha, 0.0, 1.0);
        return start + (end - start) * clampedAlpha;
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return (min + max) / 2.0;
        }
        if (min > max) {
            double temp = min;
            min = max;
            max = temp;
        }
        return Math.max(min, Math.min(max, value));
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void buildFallbackLevel() {
        bricks.clear();
        int columns = 10;
        int rows = Math.min(6, 3 + level);
        int horizontalGap = 8;
        int verticalGap = 8;
        int topOffset = 80;
        int sidePadding = 30;

        double availableWidth = PANEL_WIDTH - sidePadding * 2.0 - horizontalGap * (columns - 1);
        if (availableWidth <= 0) {
            return;
        }
        double brickWidth = availableWidth / columns;
        double brickHeight = 24;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                double x = sidePadding + col * (brickWidth + horizontalGap);
                double y = topOffset + row * (brickHeight + verticalGap);
                int hits = Math.min(1 + level / 3, 1 + row / 2);
                Color color = BRICK_COLORS[(row + col) % BRICK_COLORS.length];
                bricks.add(new Brick(x, y, brickWidth, brickHeight, color, hits));
            }
        }

        if (bricks.isEmpty()) {
            double fallbackWidth = 70;
            double fallbackHeight = 24;
            bricks.add(new Brick((PANEL_WIDTH - fallbackWidth) / 2.0, topOffset, fallbackWidth, fallbackHeight, BRICK_COLORS[0], 1));
        }
    }

    private void launchBall() {
        double baseSpeed = Math.max(minimumBallSpeed(), BASE_BALL_SPEED) * autoPilotSpeedMultiplier();
        baseSpeed = Math.min(baseSpeed, effectiveMaxSpeed());
        double angleDegrees = 40 + random.nextInt(21); // between 40 and 60 degrees
        double angle = Math.toRadians(angleDegrees);
        double direction = random.nextBoolean() ? 1 : -1;
        double velocityX = direction * baseSpeed * Math.sin(angle);
        double velocityY = -baseSpeed * Math.cos(angle);
        ball.setVelocity(velocityX, velocityY);
        gameState = GameState.RUNNING;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBackground(g2);
        drawBricks(g2);
        drawExplosions(g2);
        paddle.draw(g2);
        ball.draw(g2);
        drawHud(g2);

        switch (gameState) {
            case READY -> drawCenteredMessage(g2, "Appuyez sur ESPACE pour lancer la balle");
            case PAUSED -> drawPauseMenu(g2);
            case LEVEL_COMPLETE -> drawCenteredMessage(g2, "Bravo ! Niveau suivant avec ESPACE");
            case GAME_OVER -> drawCenteredMessage(g2, "Partie terminee - ESPACE pour rejouer");
            default -> {
            }
        }

        if (helpVisible && gameState == GameState.READY) {
            drawHelp(g2);
        }

        if (shopOpen) {
            drawShopOverlay(g2);
        }

        g2.dispose();
    }

    private void drawBackground(Graphics2D g2) {
        drawSpaceGradient(g2);
        drawNebulas(g2);
        drawStarfield(g2);
    }

    private void drawSpaceGradient(Graphics2D g2) {
        GradientPaint vertical = new GradientPaint(0, 0, new Color(6, 6, 28), 0, PANEL_HEIGHT, new Color(4, 18, 46));
        g2.setPaint(vertical);
        g2.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        GradientPaint diagonal = new GradientPaint(0, 0, new Color(60, 14, 110, 90), PANEL_WIDTH, PANEL_HEIGHT, new Color(0, 0, 0, 0));
        g2.setPaint(diagonal);
        g2.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        GradientPaint horizon = new GradientPaint(0, PANEL_HEIGHT, new Color(0, 60, 120, 60), PANEL_WIDTH, PANEL_HEIGHT / 2f, new Color(0, 0, 0, 0));
        g2.setPaint(horizon);
        g2.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
    }

    private void drawNebulas(Graphics2D g2) {
        for (Nebula nebula : nebulas) {
            float radius = (float) nebula.radius;
            Point2D center = new Point2D.Double(nebula.centerX, nebula.centerY);
            float[] dist = {0f, 0.55f, 1f};
            Color outer = new Color(nebula.midColor.getRed(), nebula.midColor.getGreen(), nebula.midColor.getBlue(), 0);
            Color[] colors = {nebula.innerColor, nebula.midColor, outer};
            RadialGradientPaint paint = new RadialGradientPaint(center, radius, dist, colors);
            g2.setPaint(paint);
            int diameter = (int) Math.round(radius * 2);
            int drawX = (int) Math.round(nebula.centerX - radius);
            int drawY = (int) Math.round(nebula.centerY - radius);
            g2.fillOval(drawX, drawY, diameter, diameter);
        }
    }

    private void drawStarfield(Graphics2D g2) {
        for (SpaceStar star : stars) {
            int alpha = star.currentAlpha();
            if (alpha <= 0) {
                continue;
            }
            double halo = star.haloSize;
            int haloAlpha = alpha / 3;
            if (haloAlpha > 0 && halo > star.size) {
                g2.setColor(new Color(160, 200, 255, Math.min(255, haloAlpha)));
                int haloDiameter = (int) Math.max(1, Math.round(halo));
                int haloX = (int) Math.round(star.x - halo / 2.0);
                int haloY = (int) Math.round(star.y - halo / 2.0);
                g2.fillOval(haloX, haloY, haloDiameter, haloDiameter);
            }

            int starSize = (int) Math.max(1, Math.round(star.size));
            int drawX = (int) Math.round(star.x - star.size / 2.0);
            int drawY = (int) Math.round(star.y - star.size / 2.0);
            g2.setColor(new Color(star.tintRed, star.tintGreen, 255, Math.min(255, alpha)));
            g2.fillOval(drawX, drawY, starSize, starSize);
        }
    }

    private void drawBricks(Graphics2D g2) {
        for (Brick brick : bricks) {
            brick.draw(g2);
        }
    }

    private void drawExplosions(Graphics2D g2) {
        for (Explosion explosion : explosions) {
            explosion.draw(g2);
        }
    }

    private void drawHud(Graphics2D g2) {
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(10, 10, PANEL_WIDTH - 20, PANEL_HEIGHT - 20);

        g2.setFont(new Font("Roboto", Font.BOLD, 18));
        String data = "Score: " + score + "   Vies: " + lives + "   Niveau: " + level + "   Credits: " + credits;
        g2.drawString(data, 30, 40);
        int infoY = 68;
        if (cheatMode) {
            g2.setColor(new Color(255, 120, 0));
            g2.setFont(new Font("Roboto", Font.BOLD, 20));
            g2.drawString("Mode triche surchauffe !", 30, infoY);
            infoY += 26;
        } else if (isBonusActive(BonusType.PIERCE_BALL)) {
            g2.setColor(new Color(255, 190, 60));
            g2.setFont(new Font("Roboto", Font.BOLD, 18));
            g2.drawString("Balle percante active", 30, infoY);
            infoY += 24;
        }

        if (!shopMessage.isEmpty()) {
            g2.setColor(new Color(200, 230, 255));
            g2.setFont(new Font("Roboto", Font.PLAIN, 16));
            g2.drawString(shopMessage, 30, infoY);
            infoY += 22;
        }

        if (!activeBonuses.isEmpty()) {
            g2.setFont(new Font("Roboto", Font.PLAIN, 14));
            g2.setColor(new Color(220, 220, 255));
            int bonusY = 40;
            int x = PANEL_WIDTH - 240;
            g2.drawString("Bonus actifs :", x, bonusY);
            bonusY += 18;
            for (ActiveBonus bonus : activeBonuses) {
                int seconds = bonus.remainingSeconds();
                String label = bonus.type.label + " (" + seconds + "s)";
                g2.drawString(label, x, bonusY);
                bonusY += 18;
            }
        }
    }

    private void drawHelp(Graphics2D g2) {
        g2.setFont(new Font("Roboto", Font.PLAIN, 16));
        g2.setColor(new Color(255, 255, 255, 180));
        String[] lines = {
            "LEFT / RIGHT pour deplacer la raquette",
            "ESPACE pour lancer ou continuer",
            "P ou ECHAP pour ouvrir le menu pause",
            "H pour afficher ou masquer l'aide"
        };
        int y = PANEL_HEIGHT - 120;
        for (String line : lines) {
            g2.drawString(line, 30, y);
            y += 22;
        }
    }

    private void drawCenteredMessage(Graphics2D g2, String message) {
        g2.setFont(new Font("Roboto", Font.BOLD, 22));
        int textWidth = g2.getFontMetrics().stringWidth(message);
        int x = (PANEL_WIDTH - textWidth) / 2;
        int y = PANEL_HEIGHT / 2;
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRoundRect(x - 30, y - 40, textWidth + 60, 80, 20, 20);
        g2.setColor(Color.WHITE);
        g2.drawString(message, x, y + 6);
    }

    private void drawPauseMenu(Graphics2D g2) {
        if (!pauseMenuVisible) {
            drawCenteredMessage(g2, "Jeu en pause");
            return;
        }

        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        int panelWidth = 520;
        int panelHeight = 380;
        int panelX = (PANEL_WIDTH - panelWidth) / 2;
        int panelY = (PANEL_HEIGHT - panelHeight) / 2;

        g2.setColor(new Color(28, 34, 56, 230));
        g2.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 28, 28);
        g2.setColor(new Color(120, 190, 255, 220));
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 28, 28);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Roboto", Font.BOLD, 34));
        g2.drawString("Pause", panelX + 25, panelY + 55);

        g2.setFont(new Font("Roboto", Font.PLAIN, 18));
        String[] options = {
            "Echap / P   - Reprendre la partie",
            "ESPACE      - Continuer",
            "ENTREE      - Recommencer la manche",
            "Q           - Nouvelle partie"
        };
        int y = panelY + 100;
        for (String option : options) {
            g2.drawString(option, panelX + 25, y);
            y += 32;
        }

        drawRecordsTable(g2, panelX, panelY, panelWidth);
    }

    private void drawRecordsTable(Graphics2D g2, int panelX, int panelY, int panelWidth) {
        int titleY = panelY + 170;
        g2.setColor(new Color(200, 220, 255));
        g2.setFont(new Font("Roboto", Font.BOLD, 19));
        g2.drawString("Tableau des scores / records", panelX + 25, titleY);

        int summaryY = titleY + 24;
        g2.setFont(new Font("Roboto", Font.PLAIN, 15));
        g2.setColor(new Color(215, 225, 245));
        String summary = String.format(
            "Record perso : %s pts | Niveau %d | Credits max %d",
            formatScore(Math.max(personalBestScore, score)),
            Math.max(personalBestLevel, level),
            Math.max(personalBestCredits, credits));
        g2.drawString(summary, panelX + 25, summaryY);

        int boxY = summaryY + 12;
        int boxHeight = 165;
        g2.setColor(new Color(12, 18, 34, 200));
        g2.fillRoundRect(panelX + 15, boxY, panelWidth - 30, boxHeight, 20, 20);
        g2.setColor(new Color(120, 190, 255, 140));
        g2.drawRoundRect(panelX + 15, boxY, panelWidth - 30, boxHeight, 20, 20);

        int colRank = panelX + 30;
        int colScore = panelX + 90;
        int colLevel = panelX + 230;
        int colNote = panelX + 320;
        int headerY = boxY + 28;

        g2.setFont(new Font("Roboto Mono", Font.BOLD, 15));
        g2.setColor(new Color(190, 210, 255));
        g2.drawString("#", colRank, headerY);
        g2.drawString("Score", colScore, headerY);
        g2.drawString("Niveau", colLevel, headerY);
        g2.drawString("Etat", colNote, headerY);

        g2.setFont(new Font("Roboto Mono", Font.PLAIN, 15));
        g2.setColor(new Color(225, 230, 255));
        int rowY = headerY + 24;
        drawScoreRow(g2, "Actuel", score, level, describeCurrentRun(), colRank, colScore, colLevel, colNote, rowY);

        if (scoreRecords.isEmpty()) {
            g2.setColor(new Color(200, 210, 230));
            g2.drawString("Aucun record enregistre pour le moment", colScore, rowY + 26);
            return;
        }

        int rank = 1;
        for (ScoreRecord record : scoreRecords) {
            rowY += 22;
            drawScoreRow(g2, String.valueOf(rank), record.score, record.level, record.note, colRank, colScore, colLevel, colNote, rowY);
            rank++;
            if (rank > SCORE_HISTORY_LIMIT) {
                break;
            }
        }
    }

    private void drawScoreRow(Graphics2D g2, String label, int scoreValue, int levelValue, String note,
                              int colRank, int colScore, int colLevel, int colNote, int y) {
        g2.drawString(label, colRank, y);
        g2.drawString(formatScore(scoreValue), colScore, y);
        g2.drawString(String.valueOf(Math.max(1, levelValue)), colLevel, y);
        g2.drawString(note, colNote, y);
    }

    private String describeCurrentRun() {
        return switch (gameState) {
            case READY -> "Pret";
            case RUNNING -> autoPilotMode ? "En cours (auto)" : "En cours";
            case PAUSED -> "En pause";
            case LEVEL_COMPLETE -> "Niveau termine";
            case GAME_OVER -> "Terminee";
        };
    }

    private String formatScore(int value) {
        int safe = Math.max(0, value);
        String digits = Integer.toString(safe);
        StringBuilder builder = new StringBuilder();
        int counter = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            builder.append(digits.charAt(i));
            counter++;
            if (counter == 3 && i > 0) {
                builder.append(' ');
                counter = 0;
            }
        }
        return builder.reverse().toString();
    }

    private void drawShopOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        int panelWidth = 560;
        int panelHeight = 280;
        int panelX = (PANEL_WIDTH - panelWidth) / 2;
        int panelY = (PANEL_HEIGHT - panelHeight) / 2;

        g2.setColor(new Color(30, 30, 60, 230));
        g2.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 24, 24);
        g2.setColor(new Color(200, 200, 255, 220));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 24, 24);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Roboto", Font.BOLD, 26));
        g2.drawString("Boutique", panelX + 26, panelY + 46);

        g2.setFont(new Font("Roboto", Font.PLAIN, 16));
        g2.drawString("Credits disponibles : " + credits, panelX + 26, panelY + 78);
        g2.drawString("Appuyez sur 1-3 pour acheter, B ou ECHAP pour fermer", panelX + 26, panelY + panelHeight - 32);

        int itemY = panelY + 112;
        for (BonusType bonus : BonusType.values()) {
            boolean affordable = credits >= bonus.cost;
            g2.setFont(new Font("Roboto", Font.BOLD, 18));
            g2.setColor(affordable ? Color.WHITE : new Color(200, 90, 90));
            String title = bonus.keyLabel + ". " + bonus.label + " - " + bonus.cost + " credits (" + bonus.durationSeconds + "s)";
            g2.drawString(title, panelX + 26, itemY);

            g2.setFont(new Font("Roboto", Font.PLAIN, 14));
            g2.setColor(new Color(200, 210, 255));
            g2.drawString(bonus.description, panelX + 26, itemY + 20);

            if (isBonusActive(bonus)) {
                g2.setColor(new Color(120, 220, 140));
                g2.drawString("Actif (" + getRemainingSecondsFor(bonus) + "s restants)", panelX + 26, itemY + 38);
            }

            itemY += 64;
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (shopOpen) {
            if (handleShopInput(keyCode)) {
                return;
            }
            return;
        }
        if (pauseMenuVisible) {
            handlePauseMenuInput(keyCode);
            return;
        }

        switch (keyCode) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_Q, KeyEvent.VK_A -> leftPressed = true;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> rightPressed = true;
            case KeyEvent.VK_SPACE -> onSpacePressed();
            case KeyEvent.VK_P, KeyEvent.VK_ESCAPE -> togglePauseMenu();
            case KeyEvent.VK_H -> helpVisible = !helpVisible;
            case KeyEvent.VK_E -> toggleCheatMode();
            case KeyEvent.VK_R -> toggleAutoPilot();
            case KeyEvent.VK_B -> toggleShop();
            case KeyEvent.VK_1, KeyEvent.VK_NUMPAD1 -> { /* Ignored when boutique ferm??e */ }
            case KeyEvent.VK_2, KeyEvent.VK_NUMPAD2 -> { /* Ignored when boutique ferm??e */ }
            case KeyEvent.VK_3, KeyEvent.VK_NUMPAD3 -> { /* Ignored when boutique ferm??e */ }
            default -> {
            }
        }
    }

    private void onSpacePressed() {
        switch (gameState) {
            case READY -> launchBall();
            case PAUSED -> resumeFromPause();
            case LEVEL_COMPLETE -> startNextLevel();
            case GAME_OVER -> startNewGame();
            default -> {
            }
        }
    }

    private void togglePauseMenu() {
        if (pauseMenuVisible) {
            resumeFromPause();
        } else {
            openPauseMenu();
        }
    }

    private void openPauseMenu() {
        if (gameState != GameState.PAUSED) {
            stateBeforePause = gameState;
        }
        gameState = GameState.PAUSED;
        pauseMenuVisible = true;
    }

    private void resumeFromPause() {
        if (!pauseMenuVisible) {
            return;
        }
        pauseMenuVisible = false;
        if (stateBeforePause == GameState.PAUSED) {
            stateBeforePause = GameState.READY;
        }
        gameState = stateBeforePause;
        ensureValidPaddlePosition();
        ensureValidBallPosition();
    }

    private void restartRoundFromPause() {
        pauseMenuVisible = false;
        stateBeforePause = GameState.READY;
        resetRound();
    }

    private void toggleCheatMode() {
        cheatMode = !cheatMode;
        refreshPlayerModifiers();
        if (gameState == GameState.READY) {
            ball.setCenter(paddle.getX() + paddle.getWidth() / 2.0, paddle.getY() - ball.getDiameter() / 2.0 - 4);
        }
    }

    private void toggleAutoPilot() {
        autoPilotMode = !autoPilotMode;
        leftPressed = false;
        rightPressed = false;
        autoPilotAimValid = false;
        refreshPlayerModifiers();
        ensureValidPaddlePosition();
        ensureValidBallPosition();
        if (autoPilotMode) {
            applyAutoPilotSpeedBoost();
        } else {
            clampBallToNormalSpeed();
        }
        if (autoPilotMode && gameState == GameState.READY && ball.getSpeed() == 0) {
            launchBall();
        }
    }

    private void toggleShop() {
        if (gameState == GameState.GAME_OVER || gameState == GameState.LEVEL_COMPLETE) {
            return;
        }
        shopOpen = !shopOpen;
        showShopMessage(shopOpen ? "Boutique ouverte" : "Boutique fermee");
    }

    private boolean handleShopInput(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_B, KeyEvent.VK_ESCAPE -> {
                shopOpen = false;
                showShopMessage("Boutique fermee");
                return true;
            }
            case KeyEvent.VK_1, KeyEvent.VK_NUMPAD1 -> {
                attemptPurchase(BonusType.PADDLE_GROW);
                return true;
            }
            case KeyEvent.VK_2, KeyEvent.VK_NUMPAD2 -> {
                attemptPurchase(BonusType.PIERCE_BALL);
                return true;
            }
            case KeyEvent.VK_3, KeyEvent.VK_NUMPAD3 -> {
                attemptPurchase(BonusType.SCORE_BOOST);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void handlePauseMenuInput(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_P, KeyEvent.VK_SPACE -> resumeFromPause();
            case KeyEvent.VK_ENTER -> restartRoundFromPause();
            case KeyEvent.VK_Q -> startNewGame();
            default -> {
            }
        }
    }

    private void attemptPurchase(BonusType bonusType) {
        if (credits < bonusType.cost) {
            showShopMessage("Credits insuffisants");
            return;
        }
        credits -= bonusType.cost;
        activateBonus(bonusType);
        showShopMessage(bonusType.label + " achetee");
    }

    private void showShopMessage(String message) {
        shopMessage = message;
        shopMessageTimer = FRAMES_PER_SECOND * 2;
    }

    private void tickShopMessage() {
        if (shopMessageTimer > 0) {
            shopMessageTimer--;
            if (shopMessageTimer == 0) {
                shopMessage = "";
            }
        }
    }

    private void refreshPlayerModifiers() {
        double widthFactor = 1.0;
        double paddleSpeed = BASE_PADDLE_SPEED;

        if (cheatMode) {
            widthFactor = (double) CHEAT_PADDLE_WIDTH / BASE_PADDLE_WIDTH;
            paddleSpeed = CHEAT_PADDLE_SPEED;
        }

        if (isBonusActive(BonusType.PADDLE_GROW)) {
            widthFactor *= 1.35;
            paddleSpeed *= 1.2;
        }

        int targetWidth = (int) Math.round(BASE_PADDLE_WIDTH * widthFactor);
        paddle.setSpeed(paddleSpeed);
        paddle.setWidth(targetWidth, PANEL_WIDTH);

        paddle.setOnFire(cheatMode);
        boolean fireBall = hasPiercingBall();
        ball.setOnFire(fireBall);
        paddle.setRadioactive(autoPilotMode);
        ball.setRadioactive(autoPilotMode);
        ensureValidPaddlePosition();
        ensureValidBallPosition();
    }

    private void ensureValidPaddlePosition() {
        double x = paddle.getX();
        double width = paddle.getWidth();
        if (!Double.isFinite(x) || width <= 0) {
            paddle.setCenter(PANEL_WIDTH / 2.0);
            return;
        }
        double maxX = Math.max(0, PANEL_WIDTH - width);
        if (x < 0) {
            paddle.setCenter(width / 2.0);
        } else if (x > maxX) {
            paddle.setCenter(maxX + width / 2.0);
        }
    }

    private void ensureValidBallPosition() {
        double x = ball.getX();
        double y = ball.getY();
        if (Double.isFinite(x) && Double.isFinite(y)) {
            return;
        }
        double diameter = ball.getDiameter();
        double radius = diameter / 2.0;
        double centerX = PANEL_WIDTH / 2.0;
        if (Double.isFinite(paddle.getX())) {
            centerX = clamp(paddle.getCenterX(), radius, PANEL_WIDTH - radius);
        }
        double centerY = paddle.getY() - radius - 4;
        ball.setCenter(centerX, centerY);
        ball.setVelocity(0, 0);
    }

    private boolean isBonusActive(BonusType type) {
        for (ActiveBonus bonus : activeBonuses) {
            if (bonus.type == type) {
                return true;
            }
        }
        return false;
    }

    private int getRemainingSecondsFor(BonusType type) {
        for (ActiveBonus bonus : activeBonuses) {
            if (bonus.type == type) {
                return Math.max(0, bonus.remainingSeconds());
            }
        }
        return 0;
    }

    private void activateBonus(BonusType type) {
        int duration = type.durationFrames();
        for (ActiveBonus bonus : activeBonuses) {
            if (bonus.type == type) {
                bonus.remainingFrames = duration;
                refreshPlayerModifiers();
                return;
            }
        }
        activeBonuses.add(new ActiveBonus(type, duration));
        refreshPlayerModifiers();
    }

    private static final class ScoreRecord {
        private final int score;
        private final int level;
        private final String note;

        private ScoreRecord(int score, int level, String note) {
            this.score = score;
            this.level = level;
            this.note = note;
        }
    }

    private static final class LevelConfig {
        private final int columns;
        private final int rows;
        private final double fillRate;
        private final double rowFillGrowth;
        private final double clusterBias;
        private final double toughBrickChance;
        private final int maxHits;
        private final double gapChance;
        private final double minDensity;

        LevelConfig(int columns, int rows, double fillRate, double rowFillGrowth, double clusterBias, double toughBrickChance, int maxHits, double gapChance, double minDensity) {
            this.columns = columns;
            this.rows = rows;
            this.fillRate = fillRate;
            this.rowFillGrowth = rowFillGrowth;
            this.clusterBias = clusterBias;
            this.toughBrickChance = toughBrickChance;
            this.maxHits = maxHits;
            this.gapChance = gapChance;
            this.minDensity = minDensity;
        }
    }

    private static final class ActiveBonus {
        private final BonusType type;
        private int remainingFrames;

        ActiveBonus(BonusType type, int remainingFrames) {
            this.type = type;
            this.remainingFrames = remainingFrames;
        }

        int remainingSeconds() {
            return (int) Math.ceil(remainingFrames / (double) FRAMES_PER_SECOND);
        }
    }

    private static final class SpaceStar {
        private final double x;
        private final double y;
        private final double size;
        private final double haloSize;
        private final int baseAlpha;
        private final int alphaRange;
        private final double twinkleSpeed;
        private double phase;
        private final int tintRed;
        private final int tintGreen;

        SpaceStar(double x, double y, double size, double haloSize, int baseAlpha, int alphaRange, double twinkleSpeed, double phase, int tintRed, int tintGreen) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.haloSize = haloSize;
            this.baseAlpha = baseAlpha;
            this.alphaRange = alphaRange;
            this.twinkleSpeed = twinkleSpeed;
            this.phase = phase;
            this.tintRed = tintRed;
            this.tintGreen = tintGreen;
        }

        void update() {
            phase += twinkleSpeed;
            if (phase > Math.PI * 2) {
                phase -= Math.PI * 2;
            }
        }

        int currentAlpha() {
            double oscillation = Math.sin(phase) * alphaRange;
            int value = (int) Math.round(baseAlpha + oscillation);
            if (value < 0) {
                return 0;
            }
            return Math.min(255, value);
        }
    }

    private static final class Nebula {
        private final double centerX;
        private final double centerY;
        private final double radius;
        private final Color innerColor;
        private final Color midColor;

        Nebula(double centerX, double centerY, double radius, Color innerColor, Color midColor) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            this.innerColor = innerColor;
            this.midColor = midColor;
        }
    }

    private static final class Explosion {
        private final double centerX;
        private final double centerY;
        private final double baseSize;
        private int remainingLife = EXPLOSION_LIFETIME;

        Explosion(Rectangle2D.Double bounds) {
            this.centerX = bounds.getCenterX();
            this.centerY = bounds.getCenterY();
            this.baseSize = Math.max(bounds.width, bounds.height);
        }

        boolean update() {
            remainingLife--;
            return remainingLife <= 0;
        }

        void draw(Graphics2D g2) {
            double progress = progress();
            double size = baseSize * (1.2 + progress * 1.6);
            double x = centerX - size / 2.0;
            double y = centerY - size / 2.0;

            int alphaCore = (int) Math.max(0, 210 * (1.0 - progress));
            int alphaRing = (int) Math.max(0, 160 * (1.0 - progress));

            if (alphaCore > 0) {
                g2.setColor(new Color(255, (int) Math.min(255, 150 + 80 * progress), 0, alphaCore));
                g2.fillOval((int) Math.round(x), (int) Math.round(y), (int) Math.round(size), (int) Math.round(size));
            }

            if (alphaRing > 0) {
                double ringSize = size * (1.25 + 0.35 * progress);
                double ringX = centerX - ringSize / 2.0;
                double ringY = centerY - ringSize / 2.0;
                Stroke previousStroke = g2.getStroke();
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(new Color(255, 240, 200, alphaRing));
                g2.drawOval((int) Math.round(ringX), (int) Math.round(ringY), (int) Math.round(ringSize), (int) Math.round(ringSize));
                g2.setStroke(previousStroke);
            }
        }

        private double progress() {
            return 1.0 - (double) remainingLife / EXPLOSION_LIFETIME;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_Q || e.getKeyCode() == KeyEvent.VK_A) {
            leftPressed = false;
        } else if (e.getKeyCode() == KeyEvent.VK_RIGHT || e.getKeyCode() == KeyEvent.VK_D) {
            rightPressed = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not used
    }
}


