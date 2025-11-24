package com.gameengine.core;

import com.gameengine.components.TransformComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.core.GameObject;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 游戏逻辑类，处理具体的游戏规则
 */
public class GameLogic {
    private Scene scene;
    private InputManager inputManager;
    private ExecutorService physicsExecutor; // 用于并行处理物理计算的线程池
    
    public GameLogic(Scene scene) {
        this.scene = scene;
        this.inputManager = InputManager.getInstance();
        // 根据可用CPU核心数创建线程池，-1是为了留出一些资源给主线程和其他系统进程
        int threadCount = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        this.physicsExecutor = Executors.newFixedThreadPool(threadCount);
    }

    /**
     * 清理资源，关闭线程池
     */
    public void cleanup() {
        if (physicsExecutor != null && !physicsExecutor.isShutdown()) {
            physicsExecutor.shutdown();
            try {
                if (!physicsExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    physicsExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                physicsExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 处理玩家输入
     */
    public void handlePlayerInput() {
        List<GameObject> players = scene.findGameObjectsByComponent(TransformComponent.class);
        if (players.isEmpty()) return;
        
        GameObject player = players.get(0);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);
        
        if (transform == null || physics == null) return;
        
        Vector2 movement = new Vector2();
        
        if (inputManager.isKeyPressed(87) || inputManager.isKeyPressed(38)) { // W或上箭头
            movement.y -= 1;
        }
        if (inputManager.isKeyPressed(83) || inputManager.isKeyPressed(40)) { // S或下箭头
            movement.y += 1;
        }
        if (inputManager.isKeyPressed(65) || inputManager.isKeyPressed(37)) { // A或左箭头
            movement.x -= 1;
        }
        if (inputManager.isKeyPressed(68) || inputManager.isKeyPressed(39)) { // D或右箭头
            movement.x += 1;
        }
        
        if (movement.magnitude() > 0) {
            movement = movement.normalize().multiply(200);
            physics.setVelocity(movement);
        }
        
        // 边界检查
        Vector2 pos = transform.getPosition();
        if (pos.x < 0) pos.x = 0;
        if (pos.y < 0) pos.y = 0;
        if (pos.x > 800 - 20) pos.x = 800 - 20;
        if (pos.y > 600 - 20) pos.y = 600 - 20;
        transform.setPosition(pos);
    }
    
    /**
     * 并行更新物理系统
     */
    public void updatePhysics() {
        List<PhysicsComponent> physicsComponents = scene.getComponents(PhysicsComponent.class);
        if (physicsComponents.isEmpty()) return;

        // 计算每个线程处理的组件数量
        int threadCount = Runtime.getRuntime().availableProcessors() - 1;
        threadCount = Math.max(2, threadCount);
        int batchSize = Math.max(1, physicsComponents.size() / threadCount + 1);

        List<Future<?>> futures = new ArrayList<>();

        // 将物理组件列表分割成多个批次，为每个批次创建一个任务并提交到线程池
        for (int i = 0; i < physicsComponents.size(); i += batchSize) {
            final int start = i;
            final int end = Math.min(i + batchSize, physicsComponents.size());

            // 处理一个批次的物理组件
            Future<?> future = physicsExecutor.submit(() -> {
                for (int j = start; j < end; j++) {
                    PhysicsComponent physics = physicsComponents.get(j);
                    updateSinglePhysics(physics);
                }
            });

            futures.add(future);
        }

        // 等待所有任务完成，以确保物理更新在下一帧渲染前全部结束
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 更新单个物理组件的逻辑，主要处理边界检测和反弹
     * @param physics 要更新的物理组件
     */
    private void updateSinglePhysics(PhysicsComponent physics) {
        // 边界反弹
        TransformComponent transform = physics.getOwner().getComponent(TransformComponent.class);
        if (transform != null && physics.bounces) {
            Vector2 pos = transform.getPosition();
            Vector2 velocity = physics.getVelocity();

            if (pos.x <= 0 || pos.x >= 800 - 15) {
                velocity.x = -velocity.x;
                physics.setVelocity(velocity);
            }
            if (pos.y <= 0 || pos.y >= 600 - 15) {
                velocity.y = -velocity.y;
                physics.setVelocity(velocity);
            }

            // 确保在边界内
            if (pos.x < 0) pos.x = 0;
            if (pos.y < 0) pos.y = 0;
            if (pos.x > 800 - 15) pos.x = 800 - 15;
            if (pos.y > 600 - 15) pos.y = 600 - 15;
            transform.setPosition(pos);
        }
    }
    
    /**
     * 检查碰撞
     */
    public void checkCollisions() {
    }
}
