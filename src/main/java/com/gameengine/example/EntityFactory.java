package com.gameengine.example;

import com.gameengine.components.RenderComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.Renderer;
import com.gameengine.math.Vector2;

public final class EntityFactory {
    private EntityFactory() {}

    public static GameObject createPlayerVisual(Renderer renderer) {
        return new GameObject("Player") {
            private Vector2 basePosition;
            @Override
            public void update(float dt) {
                super.update(dt);
                TransformComponent tc = getComponent(TransformComponent.class);
                if (tc != null) basePosition = tc.getPosition();
            }
            @Override
            public void render() {
                if (basePosition == null) return;
                
                // Colors (Default state)
                RenderComponent.Color bodyColor = new RenderComponent.Color(1.0f, 0.0f, 0.0f, 1.0f);
                RenderComponent.Color headColor = new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f);
                RenderComponent.Color leftArmColor = new RenderComponent.Color(1.0f, 0.8f, 0.0f, 1.0f);
                RenderComponent.Color rightArmColor = new RenderComponent.Color(0.0f, 1.0f, 0.0f, 1.0f);

                renderer.drawRect(basePosition.x - 8, basePosition.y - 10, 16, 20, bodyColor.r, bodyColor.g, bodyColor.b, bodyColor.a);
                renderer.drawRect(basePosition.x - 6, basePosition.y - 22, 12, 12, headColor.r, headColor.g, headColor.b, headColor.a);
                renderer.drawRect(basePosition.x - 13, basePosition.y - 5, 6, 12, leftArmColor.r, leftArmColor.g, leftArmColor.b, leftArmColor.a);
                renderer.drawRect(basePosition.x + 7, basePosition.y - 5, 6, 12, rightArmColor.r, rightArmColor.g, rightArmColor.b, rightArmColor.a);
            }
        };
    }
    
    // Simple helper for recreating renderable objects
    public static GameObject createRenderableVisual(Renderer renderer, String name, String rt, float w, float h, float r, float g, float b, float a) {
        GameObject obj = new GameObject(name);
        obj.addComponent(new TransformComponent(new Vector2(0,0)));
        
        RenderComponent.RenderType type = RenderComponent.RenderType.RECTANGLE;
        try { type = RenderComponent.RenderType.valueOf(rt); } catch(Exception ignored) {}
        
        RenderComponent rc = obj.addComponent(new RenderComponent(
            type,
            new Vector2(Math.max(1, w), Math.max(1, h)),
            new RenderComponent.Color(r, g, b, a)
        ));
        rc.setRenderer(renderer);
        return obj;
    }
}
