package com.gameengine.example;

import com.gameengine.core.GameEngine;
import com.gameengine.graphics.Renderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

public class MenuScene extends Scene {
    private GameEngine engine;
    private Renderer renderer;
    private InputManager input;
    
    private int selectedIndex = 0;
    private final String[] options = {"START GAME", "REPLAY", "EXIT"};

    public MenuScene(GameEngine engine) {
        super("MainMenu");
        this.engine = engine;
        this.renderer = engine.getRenderer();
        this.input = engine.getInputManager();
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        
        if (input.isKeyJustPressed(38)) { // UP
            selectedIndex = (selectedIndex - 1 + options.length) % options.length;
        } else if (input.isKeyJustPressed(40)) { // DOWN
            selectedIndex = (selectedIndex + 1) % options.length;
        } else if (input.isKeyJustPressed(10) || input.isKeyJustPressed(32)) { // ENTER/SPACE
            select();
        }
        
        // Mouse Hover Support
        Vector2 m = input.getMousePosition();
        float cx = 400;
        float cy = 300;
        for (int i=0; i<options.length; i++) {
            float y = cy + i * 60;
            if (m.x > cx - 100 && m.x < cx + 100 && m.y > y && m.y < y + 40) {
                selectedIndex = i;
                if (input.isMouseButtonJustPressed(0)) select();
            }
        }
    }
    
    private void select() {
        if (selectedIndex == 0) {
            engine.setScene(new GameScene(engine));
        } else if (selectedIndex == 1) {
            engine.setScene(new ReplayScene(engine, null));
        } else {
            System.exit(0);
        }
    }

    @Override
    public void render() {
        renderer.drawRect(0, 0, 800, 600, 0.15f, 0.15f, 0.2f, 1f);
        
        // Title
        renderer.drawString("HULUWA VS MONSTERS", 200, 100, 1f, 0.8f, 0.2f, 1f, 40);

        float cx = 400;
        float cy = 300;

        for (int i = 0; i < options.length; i++) {
            float y = cy + i * 60;
            if (i == selectedIndex) {
                renderer.drawRect(cx - 100, y, 200, 40, 0.8f, 0.4f, 0.2f, 1f);
                renderer.drawString(options[i], cx - 60, y + 10, 1f, 1f, 1f, 1f, 24);
            } else {
                renderer.drawRect(cx - 100, y, 200, 40, 0.3f, 0.3f, 0.35f, 1f);
                renderer.drawString(options[i], cx - 60, y + 10, 0.8f, 0.8f, 0.8f, 1f, 24);
            }
        }
        
        renderer.drawString("Controls: WASD Move, RightClick Fireball, MiddleClick Bomb", 150, 550, 0.5f, 0.5f, 0.5f, 1f, 16);
    }
}
