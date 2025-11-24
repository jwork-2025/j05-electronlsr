package com.gameengine.example;

import com.gameengine.components.*;
import com.gameengine.core.GameObject;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameLogic;
import com.gameengine.graphics.Renderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import com.gameengine.recording.RecordingConfig;
import com.gameengine.recording.RecordingService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameScene extends Scene {
    private GameEngine engine;
    private Renderer renderer;
    private InputManager inputManager;
    private Random random;
    private float time;
    private GameLogic gameLogic;
    private int score;
    private float fireballCooldown;
    private float bombCooldown;
    private boolean gameOver = false;

    public GameScene(GameEngine engine) {
        super("GameScene");
        this.engine = engine;
        this.renderer = engine.getRenderer();
        this.inputManager = engine.getInputManager();
    }

    @Override
    public void initialize() {
        super.initialize();
        this.random = new Random();
        this.time = 0;
        this.score = 0;
        this.gameLogic = new GameLogic(this);
        this.fireballCooldown = 0;
        this.bombCooldown = 0;
        this.gameOver = false;

        createPlayer();
        createEnemies();
        createDecorations();
        
        // Start Recording
        try {
            new File("recordings").mkdirs();
            String path = "recordings/session_" + System.currentTimeMillis() + ".jsonl";
            RecordingConfig cfg = new RecordingConfig(path);
            RecordingService svc = new RecordingService(cfg);
            engine.enableRecording(svc);
        } catch (Exception e) {
            System.err.println("Failed to start recording: " + e.getMessage());
        }
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);

        if (gameOver) {
            if (inputManager.isKeyJustPressed(32)) { // Space
                restartGame();
            } else if (inputManager.isKeyJustPressed(27)) { // ESC
                engine.disableRecording();
                engine.setScene(new MenuScene(engine));
            }
            return;
        }
        
        if (inputManager.isKeyJustPressed(27)) { // ESC to Pause/Exit
            engine.disableRecording();
            engine.setScene(new MenuScene(engine));
            return;
        }

        time += deltaTime;

        if (fireballCooldown > 0) fireballCooldown -= deltaTime;
        if (bombCooldown > 0) bombCooldown -= deltaTime;

        gameLogic.handlePlayerInput();
        handleShooting();
        handleBombShooting();
        updateEnemies(deltaTime);
        updateBombs(deltaTime);
        gameLogic.updatePhysics();
        checkCollisionsAndScore();

        if (time > 2.0f) {
            int enemyCount = 0;
            for (GameObject obj : getGameObjects()) {
                if (obj.getName().equals("Enemy") && obj.isActive()) enemyCount++;
            }
            if (enemyCount < 50) createEnemy();
            time = 0;
        }
        
        removeOffscreenObjects();
    }

    @Override
    public void render() {
        renderer.drawRect(0, 0, 800, 600, 0.1f, 0.1f, 0.2f, 1.0f);
        super.render();
        renderHealthBars();
        renderBombEffects();
        
        renderer.drawString("Score: " + score, 10, 30, 1, 1, 1, 1, 24);

        if (fireballCooldown > 0) {
            String fireballCD = String.format("Fireball CD: %.1f", fireballCooldown);
            renderer.drawString(fireballCD, 650, 30, 1, 1, 1, 1, 20);
        }
        if (bombCooldown > 0) {
            String bombCD = String.format("Bomb CD: %.1f", bombCooldown);
            renderer.drawString(bombCD, 650, 60, 1, 1, 1, 1, 20);
        }

        if (gameOver) {
            renderer.drawString("Game Over", 280, 250, 1, 0, 0, 1, 48);
            renderer.drawString("Press Space to Restart", 277, 300, 1, 1, 1, 1, 24);
            renderer.drawString("Press ESC to Menu", 300, 340, 0.8f, 0.8f, 0.8f, 1, 20);
        }
    }
    
    // ============ LOGIC COPIED FROM MAIN.JAVA ============
    
    private void updateEnemies(float deltaTime) {
        GameObject player = findObjectByName("Player");
        if (player == null) return;
        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
        if (playerTransform == null) return;
        Vector2 playerPos = playerTransform.getPosition();

        for (GameObject enemy : getGameObjects()) {
            if (enemy.getName().equals("Enemy") && enemy.isActive()) {
                TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
                PhysicsComponent enemyPhysics = enemy.getComponent(PhysicsComponent.class);
                if (enemyTransform != null && enemyPhysics != null) {
                    Vector2 enemyPos = enemyTransform.getPosition();
                    Vector2 direction = playerPos.subtract(enemyPos).normalize();
                    enemyPhysics.setVelocity(direction.multiply(50));
                }
            }
        }
    }
    
    private void renderBombEffects() {
        for (GameObject bomb : getGameObjects()) {
            if (bomb.getName().equals("Bomb")) {
                BombComponent bombComp = bomb.getComponent(BombComponent.class);
                if (bombComp == null) continue;
                RenderComponent renderComp = bomb.getComponent(RenderComponent.class);
                if (renderComp == null) continue;

                if (bombComp.currentState == BombComponent.State.ARMING) {
                    boolean isVisible = (int)(bombComp.armingTimer * 10) % 2 == 0;
                    renderComp.setVisible(isVisible);
                } else {
                    renderComp.setVisible(true);
                }

                if (bombComp.currentState == BombComponent.State.EXPLODING) {
                    TransformComponent transform = bomb.getComponent(TransformComponent.class);
                    if (transform == null) continue;
                    float maxRadius = 150;
                    float progress = 1.0f - (bombComp.explosionVfxTimer / 0.5f);
                    float currentRadius = maxRadius * progress;
                    float alpha = 1.0f - progress;
                    renderer.drawCircle(transform.getPosition().x, transform.getPosition().y, currentRadius, 32, 1, 1, 0, alpha);
                    renderComp.setVisible(false);
                }
            }
        }
    }

    private void renderHealthBars() {
        for (GameObject obj : getGameObjects()) {
            if (obj.hasComponent(HealthComponent.class)) {
                HealthComponent health = obj.getComponent(HealthComponent.class);
                TransformComponent transform = obj.getComponent(TransformComponent.class);
                if (transform == null) continue;
                Vector2 pos = transform.getPosition();
                float healthPercentage = health.currentHealth / health.maxHealth;
                float barWidth = 30;
                float barHeight = 5;
                float yOffset = -30;
                float x = pos.x - (barWidth / 2);
                if (obj.getName().equals("Enemy")) {
                    barWidth = 25;
                    yOffset = -10;
                    float enemyWidth = 20;
                    x = pos.x + (enemyWidth / 2) - (barWidth / 2);
                }
                float y = pos.y + yOffset;
                renderer.drawRect(x, y, barWidth, barHeight, 0.2f, 0.2f, 0.2f, 1.0f);
                renderer.drawRect(x, y, barWidth * healthPercentage, barHeight, 1.0f, 0.0f, 0.0f, 1.0f);
            }
        }
    }

    private void handleShooting() {
        if (inputManager.isMouseButtonJustPressed(1) && fireballCooldown <= 0) {
            fireballCooldown = 0.6f;
            GameObject player = findObjectByName("Player");
            if (player != null) {
                TransformComponent playerTransform = player.getComponent(TransformComponent.class);
                if (playerTransform != null) {
                    createFireball(playerTransform.getPosition(), inputManager.getMousePosition());
                }
            }
        }
    }

    private void createFireball(Vector2 startPosition, Vector2 targetPosition) {
        GameObject fireball = new GameObject("Fireball");
        fireball.addComponent(new TransformComponent(startPosition));
        RenderComponent render = fireball.addComponent(new RenderComponent(RenderComponent.RenderType.CIRCLE, new Vector2(10, 10), new RenderComponent.Color(1.0f, 0.2f, 0.0f, 1.0f)));
        render.setRenderer(renderer);
        PhysicsComponent physics = fireball.addComponent(new PhysicsComponent(0.1f));
        Vector2 direction = targetPosition.subtract(startPosition).normalize();
        physics.setVelocity(direction.multiply(500));
        physics.setFriction(1.0f);
        physics.bounces = false;
        addGameObject(fireball);
    }

    private void handleBombShooting() {
        if (inputManager.isMouseButtonJustPressed(3) && bombCooldown <= 0) {
            bombCooldown = 5.0f;
            GameObject player = findObjectByName("Player");
            if (player != null) {
                TransformComponent playerTransform = player.getComponent(TransformComponent.class);
                if (playerTransform != null) {
                    createBomb(playerTransform.getPosition(), inputManager.getMousePosition());
                }
            }
        }
    }

    private void createBomb(Vector2 startPosition, Vector2 targetPosition) {
        GameObject bomb = new GameObject("Bomb");
        bomb.addComponent(new TransformComponent(startPosition));
        bomb.addComponent(new BombComponent(targetPosition));
        RenderComponent render = bomb.addComponent(new RenderComponent(RenderComponent.RenderType.CIRCLE, new Vector2(16, 16), new RenderComponent.Color(0.5f, 0.0f, 1.0f, 1.0f)));
        render.setRenderer(renderer);
        PhysicsComponent physics = bomb.addComponent(new PhysicsComponent(0.2f));
        Vector2 direction = targetPosition.subtract(startPosition).normalize();
        physics.setVelocity(direction.multiply(300));
        physics.setFriction(1.0f);
        physics.bounces = false;
        addGameObject(bomb);
    }

    private void updateBombs(float deltaTime) {
        GameObject player = findObjectByName("Player");
        for (GameObject bomb : getGameObjects()) {
            if (bomb.getName().equals("Bomb")) {
                BombComponent bombComp = bomb.getComponent(BombComponent.class);
                if (bombComp == null) continue;
                TransformComponent bombTransform = bomb.getComponent(TransformComponent.class);
                if (bombTransform == null) continue;

                if (bombComp.currentState == BombComponent.State.TRAVELING) {
                    if (bombTransform.getPosition().distance(bombComp.targetPosition) < 10) {
                        bomb.getComponent(PhysicsComponent.class).setVelocity(new Vector2(0, 0));
                        bombComp.currentState = BombComponent.State.ARMING;
                    }
                } else if (bombComp.currentState == BombComponent.State.ARMING) {
                    bombComp.armingTimer -= deltaTime;
                    if (bombComp.armingTimer <= 0) {
                        bombComp.currentState = BombComponent.State.EXPLODING;
                        float innerRadius = 75;
                        float outerRadius = 150;
                        Vector2 bombPos = bombTransform.getPosition();
                        for (GameObject enemy : getGameObjects()) {
                            if (enemy.getName().equals("Enemy") && enemy.isActive()) {
                                TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
                                HealthComponent enemyHealth = enemy.getComponent(HealthComponent.class);
                                if (enemyTransform != null && enemyHealth != null) {
                                    float distance = bombPos.distance(enemyTransform.getPosition());
                                    if (distance < innerRadius) {
                                        enemyHealth.takeDamage(50);
                                    } else if (distance < outerRadius) {
                                        enemyHealth.takeDamage(20);
                                    }
                                    if (enemyHealth.currentHealth <= 0 && enemy.isActive()) {
                                        enemy.destroy();
                                        score += 10;
                                        if (player != null) {
                                            HealthComponent ph = player.getComponent(HealthComponent.class);
                                            if (ph != null) ph.currentHealth = Math.min(ph.maxHealth, ph.currentHealth + 10);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (bombComp.currentState == BombComponent.State.EXPLODING) {
                    bombComp.explosionVfxTimer -= deltaTime;
                    if (bombComp.explosionVfxTimer <= 0) bomb.destroy();
                }
            }
        }
    }

    private void checkCollisionsAndScore() {
        GameObject player = findObjectByName("Player");
        List<GameObject> enemies = new ArrayList<>();
        List<GameObject> fireballs = new ArrayList<>();
        for (GameObject obj : getGameObjects()) {
            if (obj.getName().equals("Enemy") && obj.isActive()) enemies.add(obj);
            else if (obj.getName().equals("Fireball") && obj.isActive()) fireballs.add(obj);
        }

        for (GameObject fireball : fireballs) {
            TransformComponent ft = fireball.getComponent(TransformComponent.class);
            RenderComponent fr = fireball.getComponent(RenderComponent.class);
            if (ft == null || fr == null) continue;
            for (GameObject enemy : enemies) {
                if (!enemy.isActive()) continue;
                TransformComponent et = enemy.getComponent(TransformComponent.class);
                RenderComponent er = enemy.getComponent(RenderComponent.class);
                if (et == null || er == null) continue;
                
                Vector2 fp = ft.getPosition(); Vector2 fsz = fr.getSize();
                Vector2 ep = et.getPosition(); Vector2 esz = er.getSize();
                
                if (fp.x < ep.x + esz.x && fp.x + fsz.x > ep.x && fp.y < ep.y + esz.y && fp.y + fsz.y > ep.y) {
                    HealthComponent eh = enemy.getComponent(HealthComponent.class);
                    if (eh != null) {
                        eh.takeDamage(30);
                        if (eh.currentHealth <= 0) {
                            enemy.destroy();
                            score += 10;
                            if (player != null) {
                                HealthComponent ph = player.getComponent(HealthComponent.class);
                                if (ph != null) ph.currentHealth = Math.min(ph.maxHealth, ph.currentHealth + 10);
                            }
                        }
                    }
                    fireball.destroy();
                    break;
                }
            }
        }

        if (player != null) {
            HealthComponent ph = player.getComponent(HealthComponent.class);
            TransformComponent pt = player.getComponent(TransformComponent.class);
            if (ph != null && !ph.isInvincible && pt != null) {
                float pl = pt.getPosition().x - 13;
                float ptop = pt.getPosition().y - 22;
                float pw = 26;
                float ph_h = 32;
                
                for (GameObject enemy : enemies) {
                    if (!enemy.isActive()) continue;
                    TransformComponent et = enemy.getComponent(TransformComponent.class);
                    RenderComponent er = enemy.getComponent(RenderComponent.class);
                    if (et == null || er == null) continue;
                    Vector2 ep = et.getPosition(); Vector2 esz = er.getSize();
                    if (pl < ep.x + esz.x && pl + pw > ep.x && ptop < ep.y + esz.y && ptop + ph_h > ep.y) {
                        ph.takeDamage(50);
                        ph.setInvincible(2.0f);
                        if (ph.currentHealth <= 0) gameOver = true;
                    }
                }
            }
        }
    }

    private void removeOffscreenObjects() {
        for (GameObject obj : getGameObjects()) {
            if (obj.getName().equals("Fireball")) {
                TransformComponent t = obj.getComponent(TransformComponent.class);
                if (t != null) {
                    Vector2 p = t.getPosition();
                    if (p.x < 0 || p.x > 800 || p.y < 0 || p.y > 600) obj.destroy();
                }
            }
        }
    }

    private void restartGame() {
        engine.disableRecording(); // stop old
        initialize(); // reset everything
    }

    private GameObject findObjectByName(String name) {
        for (GameObject obj : getGameObjects()) {
            if (obj.getName().equals(name)) return obj;
        }
        return null;
    }

    private void createPlayer() {
        // Using the Factory for Visuals is possible but here we need the full logic object
        // So we keep the original logic but ensure it matches the factory visual
        GameObject player = new GameObject("Player") {
            private Vector2 basePosition;
            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                updateComponents(deltaTime);
                TransformComponent transform = getComponent(TransformComponent.class);
                if (transform != null) basePosition = transform.getPosition();
            }
            @Override
            public void render() {
                if (basePosition == null) return;
                HealthComponent health = getComponent(HealthComponent.class);
                boolean isInvincible = health != null && health.isInvincible;
                // Logic copied from Main.java
                RenderComponent.Color bodyColor = isInvincible ? new RenderComponent.Color(1,1,1) : new RenderComponent.Color(1.0f, 0.0f, 0.0f, 1.0f);
                RenderComponent.Color headColor = isInvincible ? new RenderComponent.Color(1,1,1) : new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f);
                RenderComponent.Color leftArmColor = isInvincible ? new RenderComponent.Color(1,1,1) : new RenderComponent.Color(1.0f, 0.8f, 0.0f, 1.0f);
                RenderComponent.Color rightArmColor = isInvincible ? new RenderComponent.Color(1,1,1) : new RenderComponent.Color(0.0f, 1.0f, 0.0f, 1.0f);

                renderer.drawRect(basePosition.x - 8, basePosition.y - 10, 16, 20, bodyColor.r, bodyColor.g, bodyColor.b, bodyColor.a);
                renderer.drawRect(basePosition.x - 6, basePosition.y - 22, 12, 12, headColor.r, headColor.g, headColor.b, headColor.a);
                renderer.drawRect(basePosition.x - 13, basePosition.y - 5, 6, 12, leftArmColor.r, leftArmColor.g, leftArmColor.b, leftArmColor.a);
                renderer.drawRect(basePosition.x + 7, basePosition.y - 5, 6, 12, rightArmColor.r, rightArmColor.g, rightArmColor.b, rightArmColor.a);
            }
        };
        player.addComponent(new TransformComponent(new Vector2(400, 300)));
        PhysicsComponent physics = player.addComponent(new PhysicsComponent(1.0f));
        physics.setFriction(0.95f);
        player.addComponent(new HealthComponent(100));
        addGameObject(player);
    }

    private void createEnemies() {
        for (int i = 0; i < 3; i++) createEnemy();
    }

    private void createEnemy() {
        GameObject enemy = new GameObject("Enemy");
        Vector2 position = new Vector2(random.nextFloat() * 800, random.nextFloat() * 600);
        enemy.addComponent(new TransformComponent(position));
        RenderComponent render = enemy.addComponent(new RenderComponent(RenderComponent.RenderType.RECTANGLE, new Vector2(20, 20), new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f)));
        render.setRenderer(renderer);
        PhysicsComponent physics = enemy.addComponent(new PhysicsComponent(0.5f));
        physics.setVelocity(new Vector2((random.nextFloat() - 0.5f) * 100, (random.nextFloat() - 0.5f) * 100));
        physics.setFriction(0.98f);
        enemy.addComponent(new HealthComponent(50));
        addGameObject(enemy);
    }

    private void createDecorations() {
        for (int i = 0; i < 5; i++) createDecoration();
    }

    private void createDecoration() {
        GameObject decoration = new GameObject("Decoration");
        Vector2 position = new Vector2(random.nextFloat() * 800, random.nextFloat() * 600);
        decoration.addComponent(new TransformComponent(position));
        RenderComponent render = decoration.addComponent(new RenderComponent(RenderComponent.RenderType.CIRCLE, new Vector2(5, 5), new RenderComponent.Color(0.5f, 0.5f, 1.0f, 0.8f)));
        render.setRenderer(renderer);
        addGameObject(decoration);
    }
}
