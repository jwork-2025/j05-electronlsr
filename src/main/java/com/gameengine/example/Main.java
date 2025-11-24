package com.gameengine.example;

import com.gameengine.core.GameEngine;

/**
 * 游戏主程序
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("启动游戏引擎...");

        try {
            // 创建游戏引擎
            GameEngine engine = new GameEngine(800, 600, "Huluwa VS Monsters");
            
            // 设置初始场景为菜单
            engine.setScene(new MenuScene(engine));
            
            // 运行游戏
            engine.run();
            
        } catch (Exception e) {
            System.err.println("游戏运行出错: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("游戏结束");
    }
}