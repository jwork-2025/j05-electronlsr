package com.gameengine.example;

import com.gameengine.components.*;
import com.gameengine.core.GameObject;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameLogic;
import com.gameengine.graphics.Renderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 游戏主程序
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("启动游戏引擎...");

        try {
            // 创建游戏引擎
            GameEngine engine = new GameEngine(800, 600, "游戏引擎");
            InputManager inputManager = engine.getInputManager();

            // 创建游戏场景
            Scene gameScene = new Scene("GameScene") {
                private Renderer renderer;
                private Random random;
                private float time;
                private GameLogic gameLogic;
                private int score;
                private float fireballCooldown;
                private float bombCooldown;
                private boolean gameOver = false;

                @Override
                public void initialize() {
                    super.initialize();
                    this.renderer = engine.getRenderer();
                    this.random = new Random();
                    this.time = 0;
                    this.score = 0;
                    this.gameLogic = new GameLogic(this);
                    this.fireballCooldown = 0;
                    this.bombCooldown = 0;

                    // 创建游戏对象
                    createPlayer();
                    createEnemies();
                    createDecorations();
                }

                @Override
                public void update(float deltaTime) {
                    super.update(deltaTime);

                    if (gameOver) {
                        if (inputManager.isKeyPressed(32)) {
                            restartGame();
                        }
                        return;
                    }

                    time += deltaTime;

                    if (fireballCooldown > 0) {
                        fireballCooldown -= deltaTime;
                    }
                    if (bombCooldown > 0) {
                        bombCooldown -= deltaTime;
                    }

                    // 使用游戏逻辑类处理游戏规则
                    gameLogic.handlePlayerInput();
                    handleShooting();
                    handleBombShooting();
                    updateEnemies(deltaTime);
                    updateBombs(deltaTime);
                    gameLogic.updatePhysics();
                    checkCollisionsAndScore();

                    // 生成新敌人
                    if (time > 2.0f) {
                        int enemyCount = 0;
                        for (GameObject obj : getGameObjects()) {
                            if (obj.getName().equals("Enemy") && obj.isActive()) {
                                enemyCount++;
                            }
                        }
                        if (enemyCount < 50) {
                            createEnemy();
                        }
                        time = 0;
                    }
                    
                    removeOffscreenObjects();
                }

                private void updateEnemies(float deltaTime) {
                    GameObject player = null;
                    for (GameObject obj : getGameObjects()) {
                        if (obj.getName().equals("Player")) {
                            player = obj;
                            break;
                        }
                    }
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
                                float speed = 50; // 敌人移动速度
                                enemyPhysics.setVelocity(direction.multiply(speed));
                            }
                        }
                    }
                }

                @Override
                public void render() {
                    // 绘制背景
                    renderer.drawRect(0, 0, 800, 600, 0.1f, 0.1f, 0.2f, 1.0f);

                    // 渲染所有对象
                    super.render();

                    // 渲染血条和炸弹特效
                    renderHealthBars();
                    renderBombEffects();
                    
                    // 绘制分数
                    renderer.drawString("Score: " + score, 10, 30, 1, 1, 1, 1, 24);

                    // 绘制冷却时间
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
                        GameObject player = null;
                        for (GameObject obj : getGameObjects()) {
                            if (obj.getName().equals("Player")) {
                                player = obj;
                                break;
                            }
                        }

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

                    // 添加变换组件
                    fireball.addComponent(new TransformComponent(startPosition));

                    // 添加渲染组件
                    RenderComponent render = fireball.addComponent(new RenderComponent(
                        RenderComponent.RenderType.CIRCLE,
                        new Vector2(10, 10),
                        new RenderComponent.Color(1.0f, 0.2f, 0.0f, 1.0f)
                    ));
                    render.setRenderer(renderer);

                    // 添加物理组件
                    PhysicsComponent physics = fireball.addComponent(new PhysicsComponent(0.1f));
                    Vector2 direction = targetPosition.subtract(startPosition).normalize();
                    physics.setVelocity(direction.multiply(500)); // Set fireball speed
                    physics.setFriction(1.0f);
                    physics.bounces = false;

                    addGameObject(fireball);
                }

                private void handleBombShooting() {
                    if (inputManager.isMouseButtonJustPressed(3) && bombCooldown <= 0) {
                        bombCooldown = 5.0f;
                        GameObject player = null;
                        for (GameObject obj : getGameObjects()) {
                            if (obj.getName().equals("Player")) {
                                player = obj;
                                break;
                            }
                        }

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

                    RenderComponent render = bomb.addComponent(new RenderComponent(
                        RenderComponent.RenderType.CIRCLE,
                        new Vector2(16, 16),
                        new RenderComponent.Color(0.5f, 0.0f, 1.0f, 1.0f)
                    ));
                    render.setRenderer(renderer);

                    PhysicsComponent physics = bomb.addComponent(new PhysicsComponent(0.2f));
                    Vector2 direction = targetPosition.subtract(startPosition).normalize();
                    physics.setVelocity(direction.multiply(300));
                    physics.setFriction(1.0f);
                    physics.bounces = false;

                    addGameObject(bomb);
                }

                private void updateBombs(float deltaTime) {
                    GameObject player = null;
                    for (GameObject obj : getGameObjects()) {
                        if (obj.getName().equals("Player")) {
                            player = obj;
                            break;
                        }
                    }

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

                                                if (enemyHealth.currentHealth <= 0) {
                                                    if (enemy.isActive()) {
                                                        enemy.destroy();
                                                        score += 10;
                                                        if (player != null) {
                                                            HealthComponent playerHealth = player.getComponent(HealthComponent.class);
                                                            if (playerHealth != null) {
                                                                playerHealth.currentHealth = Math.min(playerHealth.maxHealth, playerHealth.currentHealth + 10);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (bombComp.currentState == BombComponent.State.EXPLODING) {
                                bombComp.explosionVfxTimer -= deltaTime;
                                if (bombComp.explosionVfxTimer <= 0) {
                                    bomb.destroy();
                                }
                            }
                        }
                    }
                }
                
                private void checkCollisionsAndScore() {
                    GameObject player = null;
                    List<GameObject> enemies = new ArrayList<>();
                    List<GameObject> fireballs = new ArrayList<>();

                    for (GameObject obj : getGameObjects()) {
                        if (obj.getName().equals("Player")) {
                            player = obj;
                        } else if (obj.getName().equals("Enemy") && obj.isActive()) {
                            enemies.add(obj);
                        } else if (obj.getName().equals("Fireball") && obj.isActive()) {
                            fireballs.add(obj);
                        }
                    }

                    for (GameObject fireball : fireballs) {
                        TransformComponent fireballTransform = fireball.getComponent(TransformComponent.class);
                        RenderComponent fireballRender = fireball.getComponent(RenderComponent.class);
                        if (fireballTransform == null || fireballRender == null) continue;

                        for (GameObject enemy : enemies) {
                            if (!enemy.isActive()) continue;

                            TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
                            RenderComponent enemyRender = enemy.getComponent(RenderComponent.class);
                            if (enemyTransform == null || enemyRender == null) continue;

                            Vector2 enemyPos = enemyTransform.getPosition();
                            Vector2 enemySize = enemyRender.getSize();
                            Vector2 fireballPos = fireballTransform.getPosition();
                            Vector2 fireballSize = fireballRender.getSize();

                            if (fireballPos.x < enemyPos.x + enemySize.x &&
                                fireballPos.x + fireballSize.x > enemyPos.x &&
                                fireballPos.y < enemyPos.y + enemySize.y &&
                                fireballPos.y + fireballSize.y > enemyPos.y) {

                                HealthComponent enemyHealth = enemy.getComponent(HealthComponent.class);
                                if (enemyHealth != null) {
                                    
                                    enemyHealth.takeDamage(30);
                                    if (enemyHealth.currentHealth <= 0) {
                                        enemy.destroy();
                                        score += 10;

                                        if (player != null) {
                                            HealthComponent playerHealth = player.getComponent(HealthComponent.class);
                                            if (playerHealth != null) {
                                                playerHealth.currentHealth = Math.min(playerHealth.maxHealth, playerHealth.currentHealth + 10);
                                            }
                                        }
                                    }
                                }
                                fireball.destroy();
                                break;
                            }
                        }
                    }

                    if (player != null) {
                        HealthComponent playerHealth = player.getComponent(HealthComponent.class);
                        TransformComponent playerTransform = player.getComponent(TransformComponent.class);

                        if (playerHealth != null && !playerHealth.isInvincible && playerTransform != null) {
                            float playerLeft = playerTransform.getPosition().x - 13;
                            float playerTop = playerTransform.getPosition().y - 22;
                            float playerWidth = 26;
                            float playerHeight = 32;

                            for (GameObject enemy : enemies) {
                                if (!enemy.isActive()) continue;

                                TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
                                RenderComponent enemyRender = enemy.getComponent(RenderComponent.class);
                                if (enemyTransform == null || enemyRender == null) continue;

                                Vector2 enemyPos = enemyTransform.getPosition();
                                Vector2 enemySize = enemyRender.getSize();

                                if (playerLeft < enemyPos.x + enemySize.x &&
                                    playerLeft + playerWidth > enemyPos.x &&
                                    playerTop < enemyPos.y + enemySize.y &&
                                    playerTop + playerHeight > enemyPos.y) {
                                    
                                    
                                    playerHealth.takeDamage(50);
                                    playerHealth.setInvincible(2.0f);

                                    if (playerHealth.currentHealth <= 0) {
                                        gameOver = true;
                                    }
                                }
                            }
                        }
                    }
                }
                
                private void removeOffscreenObjects() {
                    List<GameObject> objects = getGameObjects();
                    for (GameObject obj : objects) {
                        if (obj.getName().equals("Fireball")) {
                            TransformComponent transform = obj.getComponent(TransformComponent.class);
                            if (transform != null) {
                                Vector2 pos = transform.getPosition();
                                if (pos.x < 0 || pos.x > 800 || pos.y < 0 || pos.y > 600) {
                                    obj.destroy();
                                }
                            }
                        }
                    }
                }

                private void restartGame() {
                    gameOver = false;
                    score = 0;

                    // Remove all enemies, fireballs, and bombs
                    List<GameObject> objectsToRemove = new ArrayList<>();
                    for (GameObject obj : getGameObjects()) {
                        if (obj.getName().equals("Enemy") || obj.getName().equals("Fireball") || obj.getName().equals("Bomb")) {
                            objectsToRemove.add(obj);
                        }
                    }
                    for (GameObject obj : objectsToRemove) {
                        obj.destroy();
                    }

                    // Reset player
                    GameObject player = null;
                    for (GameObject obj : getGameObjects()) {
                        if (obj.getName().equals("Player")) {
                            player = obj;
                            break;
                        }
                    }
                    if (player != null) {
                        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
                        if (playerTransform != null) {
                            playerTransform.setPosition(new Vector2(400, 300));
                        }
                        HealthComponent playerHealth = player.getComponent(HealthComponent.class);
                        if (playerHealth != null) {
                            playerHealth.currentHealth = playerHealth.maxHealth;
                            playerHealth.isInvincible = false;
                            playerHealth.invincibilityTimer = 0;
                        }
                    }

                    // Create initial enemies
                    createEnemies();
                }

                private void createPlayer() {
                    // 创建葫芦娃 - 所有部位都在一个GameObject中
                    GameObject player = new GameObject("Player") {
                        private Vector2 basePosition;
                        
                        @Override
                        public void update(float deltaTime) {
                            super.update(deltaTime);
                            updateComponents(deltaTime);
                            
                            // 更新所有部位的位置
                            updateBodyParts();
                        }
                        
                        @Override
                        public void render() {
                            // 渲染所有部位
                            renderBodyParts();
                        }
                        
                        private void updateBodyParts() {
                            TransformComponent transform = getComponent(TransformComponent.class);
                            if (transform != null) {
                                basePosition = transform.getPosition();
                            }
                        }
                        
                        private void renderBodyParts() {
                            if (basePosition == null) return;

                            HealthComponent health = getComponent(HealthComponent.class);
                            boolean isInvincible = health != null && health.isInvincible;

                            RenderComponent.Color bodyColor = isInvincible ? new RenderComponent.Color(1,1,1) : new RenderComponent.Color(1.0f, 0.0f, 0.0f, 1.0f);
                            RenderComponent.Color headColor = isInvincible ? new RenderComponent.Color(1,1,1) : new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f);
                            RenderComponent.Color leftArmColor = isInvincible ? new RenderComponent.Color(1,1,1) : new RenderComponent.Color(1.0f, 0.8f, 0.0f, 1.0f);
                            RenderComponent.Color rightArmColor = isInvincible ? new RenderComponent.Color(1,1,1) : new RenderComponent.Color(0.0f, 1.0f, 0.0f, 1.0f);

                            // 渲染身体
                            renderer.drawRect(
                                basePosition.x - 8, basePosition.y - 10, 16, 20,
                                bodyColor.r, bodyColor.g, bodyColor.b, bodyColor.a
                            );
                            
                            // 渲染头部
                            renderer.drawRect(
                                basePosition.x - 6, basePosition.y - 22, 12, 12,
                                headColor.r, headColor.g, headColor.b, headColor.a
                            );
                            
                            // 渲染左臂
                            renderer.drawRect(
                                basePosition.x - 13, basePosition.y - 5, 6, 12,
                                leftArmColor.r, leftArmColor.g, leftArmColor.b, leftArmColor.a
                            );
                            
                            // 渲染右臂
                            renderer.drawRect(
                                basePosition.x + 7, basePosition.y - 5, 6, 12,
                                rightArmColor.r, rightArmColor.g, rightArmColor.b, rightArmColor.a
                            );
                        }
                    };
                    
                    // 添加变换组件
                    player.addComponent(new TransformComponent(new Vector2(400, 300)));
                    
                    // 添加物理组件
                    PhysicsComponent physics = player.addComponent(new PhysicsComponent(1.0f));
                    physics.setFriction(0.95f);

                    player.addComponent(new HealthComponent(100));
                    
                    addGameObject(player);
                }
                
                private void createEnemies() {
                    for (int i = 0; i < 3; i++) {
                        createEnemy();
                    }
                }
                
                private void createEnemy() {
                    GameObject enemy = new GameObject("Enemy");
                    
                    // 随机位置
                    Vector2 position = new Vector2(
                        random.nextFloat() * 800,
                        random.nextFloat() * 600
                    );
                    
                    // 添加变换组件
                    enemy.addComponent(new TransformComponent(position));
                    
                    // 添加渲染组件 - 改为矩形，使用橙色
                    RenderComponent render = enemy.addComponent(new RenderComponent(
                        RenderComponent.RenderType.RECTANGLE,
                        new Vector2(20, 20),
                        new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f)  // 橙色
                    ));
                    render.setRenderer(renderer);
                    
                    // 添加物理组件
                    PhysicsComponent physics = enemy.addComponent(new PhysicsComponent(0.5f));
                    physics.setVelocity(new Vector2(
                        (random.nextFloat() - 0.5f) * 100,
                        (random.nextFloat() - 0.5f) * 100
                    ));
                    physics.setFriction(0.98f);

                    enemy.addComponent(new HealthComponent(50));
                    
                    addGameObject(enemy);
                }
                
                private void createDecorations() {
                    for (int i = 0; i < 5; i++) {
                        createDecoration();
                    }
                }
                
                private void createDecoration() {
                    GameObject decoration = new GameObject("Decoration");
                    
                    // 随机位置
                    Vector2 position = new Vector2(
                        random.nextFloat() * 800,
                        random.nextFloat() * 600
                    );
                    
                    // 添加变换组件
                    decoration.addComponent(new TransformComponent(position));
                    
                    // 添加渲染组件
                    RenderComponent render = decoration.addComponent(new RenderComponent(
                        RenderComponent.RenderType.CIRCLE,
                        new Vector2(5, 5),
                        new RenderComponent.Color(0.5f, 0.5f, 1.0f, 0.8f)
                    ));
                    render.setRenderer(renderer);
                    
                    addGameObject(decoration);
                }
            };
            
            // 设置场景
            engine.setScene(gameScene);
            
            // 运行游戏
            engine.run();
            
        } catch (Exception e) {
            System.err.println("游戏运行出错: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("游戏结束");
    }
}
