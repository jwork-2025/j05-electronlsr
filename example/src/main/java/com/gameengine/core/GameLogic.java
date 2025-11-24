package com.gameengine.core;

import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GameLogic {
    private Scene scene;
    private InputManager inputManager;
    private Random random;
    private boolean gameOver;
    private GameEngine gameEngine;
    private Map<GameObject, Vector2> aiTargetVelocities;
    private Map<GameObject, Float> aiTargetUpdateTimers;
    private ExecutorService avoidanceExecutor;
    
    public GameLogic(Scene scene) {
        this.scene = scene;
        this.inputManager = InputManager.getInstance();
        this.random = new Random();
        this.gameOver = false;
        this.aiTargetVelocities = new HashMap<>();
        this.aiTargetUpdateTimers = new HashMap<>();
        int threadCount = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        this.avoidanceExecutor = Executors.newFixedThreadPool(threadCount);
    }
    
    public void cleanup() {
        if (avoidanceExecutor != null && !avoidanceExecutor.isShutdown()) {
            avoidanceExecutor.shutdown();
            try {
                if (!avoidanceExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    avoidanceExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                avoidanceExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public void setGameEngine(GameEngine engine) {
        this.gameEngine = engine;
    }
    
    public boolean isGameOver() {
        return gameOver;
    }
    
    public GameObject getUserPlayer() {
        for (GameObject obj : scene.getGameObjects()) {
            if (obj.getName().equals("Player") && obj.hasComponent(PhysicsComponent.class)) {
                return obj;
            }
        }
        return null;
    }
    
    public List<GameObject> getAIPlayers() {
        return scene.getGameObjects().stream()
            .filter(obj -> obj.getName().equals("AIPlayer"))
            .filter(obj -> obj.isActive())
            .collect(Collectors.toList());
    }
    
    public void handlePlayerInput(float deltaTime) {
        if (gameOver) return;
        
        GameObject player = getUserPlayer();
        if (player == null) return;
        
        TransformComponent transform = player.getComponent(TransformComponent.class);
        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);
        
        if (transform == null || physics == null) return;
        
        Vector2 movement = new Vector2();
        
        // W / UpArrow (AWT=38, GLFW=265)
        if (inputManager.isKeyPressed(87) || inputManager.isKeyPressed(38) || inputManager.isKeyPressed(265)) {
            movement.y -= 1;
        }
        // S / DownArrow (AWT=40, GLFW=264)
        if (inputManager.isKeyPressed(83) || inputManager.isKeyPressed(40) || inputManager.isKeyPressed(264)) {
            movement.y += 1;
        }
        // A / LeftArrow (AWT=37, GLFW=263)
        if (inputManager.isKeyPressed(65) || inputManager.isKeyPressed(37) || inputManager.isKeyPressed(263)) {
            movement.x -= 1;
        }
        // D / RightArrow (AWT=39, GLFW=262)
        if (inputManager.isKeyPressed(68) || inputManager.isKeyPressed(39) || inputManager.isKeyPressed(262)) {
            movement.x += 1;
        }
        
        if (movement.magnitude() > 0) {
            movement = movement.normalize().multiply(200);
            physics.setVelocity(movement);
        }
        
        Vector2 pos = transform.getPosition();
        int screenW = gameEngine != null && gameEngine.getRenderer() != null ? gameEngine.getRenderer().getWidth() : 1920;
        int screenH = gameEngine != null && gameEngine.getRenderer() != null ? gameEngine.getRenderer().getHeight() : 1080;
        if (pos.x < 0) pos.x = 0;
        if (pos.y < 0) pos.y = 0;
        if (pos.x > screenW - 20) pos.x = screenW - 20;
        if (pos.y > screenH - 20) pos.y = screenH - 20;
        transform.setPosition(pos);
    }
    
    public void handleAIPlayerMovement(float deltaTime) {
        if (gameOver) return;
        
        List<GameObject> aiPlayers = getAIPlayers();
        
        for (GameObject aiPlayer : aiPlayers) {
            PhysicsComponent physics = aiPlayer.getComponent(PhysicsComponent.class);
            if (physics == null) continue;
            
            if (!aiTargetVelocities.containsKey(aiPlayer)) {
                Vector2 initialTarget = new Vector2(
                    (random.nextFloat() - 0.5f) * 150,
                    (random.nextFloat() - 0.5f) * 150
                );
                aiTargetVelocities.put(aiPlayer, initialTarget);
                aiTargetUpdateTimers.put(aiPlayer, 0f);
            }
            
            float timer = aiTargetUpdateTimers.get(aiPlayer) + deltaTime;
            aiTargetUpdateTimers.put(aiPlayer, timer);
            
            if (timer >= (2.0f + random.nextFloat() * 2.0f)) {
                Vector2 newTarget = new Vector2(
                    (random.nextFloat() - 0.5f) * 150,
                    (random.nextFloat() - 0.5f) * 150
                );
                aiTargetVelocities.put(aiPlayer, newTarget);
                aiTargetUpdateTimers.put(aiPlayer, 0f);
            }
            
            Vector2 currentVelocity = physics.getVelocity();
            Vector2 targetVelocity = aiTargetVelocities.get(aiPlayer);
            
            float lerpFactor = 0.1f;
            Vector2 newVelocity = new Vector2(
                currentVelocity.x + (targetVelocity.x - currentVelocity.x) * lerpFactor,
                currentVelocity.y + (targetVelocity.y - currentVelocity.y) * lerpFactor
            );
            
            float maxSpeed = 150f;
            if (newVelocity.magnitude() > maxSpeed) {
                newVelocity = newVelocity.normalize().multiply(maxSpeed);
            }
            
            physics.setVelocity(newVelocity);
        }
    }
    
    
    public void handleAIPlayerAvoidance(float deltaTime) {
        if (gameOver) return;
        
        List<GameObject> aiPlayers = getAIPlayers();
        if (aiPlayers.isEmpty()) return;
        
        if (aiPlayers.size() < 10) {
            handleAIPlayerAvoidanceSerial(aiPlayers, deltaTime);
        } else {
            handleAIPlayerAvoidanceParallel(aiPlayers, deltaTime);
        }
    }
    
    private void handleAIPlayerAvoidanceSerial(List<GameObject> aiPlayers, float deltaTime) {
        for (int i = 0; i < aiPlayers.size(); i++) {
            processAvoidanceForPlayer(aiPlayers, i, deltaTime);
        }
    }
    
    private void handleAIPlayerAvoidanceParallel(List<GameObject> aiPlayers, float deltaTime) {
        int threadCount = Runtime.getRuntime().availableProcessors() - 1;
        threadCount = Math.max(2, threadCount);
        int batchSize = Math.max(1, aiPlayers.size() / threadCount + 1);
        
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < aiPlayers.size(); i += batchSize) {
            final int start = i;
            final int end = Math.min(i + batchSize, aiPlayers.size());
            
            Future<?> future = avoidanceExecutor.submit(() -> {
                for (int j = start; j < end; j++) {
                    processAvoidanceForPlayer(aiPlayers, j, deltaTime);
                }
            });
            
            futures.add(future);
        }
        
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private void processAvoidanceForPlayer(List<GameObject> aiPlayers, int index, float deltaTime) {
        GameObject aiPlayer1 = aiPlayers.get(index);
        TransformComponent transform1 = aiPlayer1.getComponent(TransformComponent.class);
        PhysicsComponent physics1 = aiPlayer1.getComponent(PhysicsComponent.class);
        
        if (transform1 == null || physics1 == null) return;
        
        Vector2 pos1 = transform1.getPosition();
        Vector2 avoidance = new Vector2();
        
        for (int j = index + 1; j < aiPlayers.size(); j++) {
            GameObject aiPlayer2 = aiPlayers.get(j);
            TransformComponent transform2 = aiPlayer2.getComponent(TransformComponent.class);
            
            if (transform2 == null) continue;
            
            Vector2 pos2 = transform2.getPosition();
            float distance = pos1.distance(pos2);
            
            if (distance < 80 && distance > 0) {
                Vector2 direction = pos1.subtract(pos2).normalize();
                float strength = (80 - distance) / 80.0f;
                avoidance = avoidance.add(direction.multiply(strength * 50));
            }
        }
        
        if (avoidance.magnitude() > 0) {
            Vector2 currentVelocity = physics1.getVelocity();
            float lerpFactor = 0.15f;
            Vector2 avoidanceDirection = avoidance.normalize();
            float avoidanceStrength = Math.min(avoidance.magnitude(), 50f);
            
            Vector2 targetVelocity = currentVelocity.add(
                avoidanceDirection.multiply(avoidanceStrength * deltaTime * 10)
            );
            
            Vector2 newVelocity = new Vector2(
                currentVelocity.x + (targetVelocity.x - currentVelocity.x) * lerpFactor,
                currentVelocity.y + (targetVelocity.y - currentVelocity.y) * lerpFactor
            );
            
            float maxSpeed = 150f;
            if (newVelocity.magnitude() > maxSpeed) {
                newVelocity = newVelocity.normalize().multiply(maxSpeed);
            }
            
            physics1.setVelocity(newVelocity);
        }
    }
    
    public void checkCollisions() {
        if (gameOver) return;
        
        GameObject userPlayer = getUserPlayer();
        if (userPlayer == null) return;
        
        TransformComponent playerTransform = userPlayer.getComponent(TransformComponent.class);
        if (playerTransform == null) return;
        
        Vector2 playerPos = playerTransform.getPosition();
        
        List<GameObject> aiPlayers = getAIPlayers();
        for (GameObject aiPlayer : aiPlayers) {
            TransformComponent aiTransform = aiPlayer.getComponent(TransformComponent.class);
            if (aiTransform != null) {
                float distance = playerPos.distance(aiTransform.getPosition());
                if (distance < 30) {
                    gameOver = true;
                    return;
                }
            }
        }
    }
}
