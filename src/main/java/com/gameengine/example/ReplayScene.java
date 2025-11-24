package com.gameengine.example;

import com.gameengine.components.HealthComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.Renderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import com.gameengine.recording.RecordingJson;
import com.gameengine.recording.FileRecordingStorage;

import java.io.File;
import java.util.*;

public class ReplayScene extends Scene {
    private final GameEngine engine;
    private String recordingPath;
    private Renderer renderer;
    private InputManager input;
    private float time;
    
    // Keyframe Structure
    private static class Keyframe {
        static class EntityInfo {
            String id;
            Vector2 pos;
            String rt; 
            float w, h;
            float r=1,g=1,b=1,a=1;
            float hp = -1;
            float maxHp = -1;
        }
        double t;
        Map<String, EntityInfo> entities = new HashMap<>();
    }

    private final List<Keyframe> keyframes = new ArrayList<>();
    private final Map<String, GameObject> activeObjects = new HashMap<>();

    // File Selection Mode
    private List<File> recordingFiles;
    private int selectedIndex = 0;

    public ReplayScene(GameEngine engine, String path) {
        super("Replay");
        this.engine = engine;
        this.recordingPath = path;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.input = engine.getInputManager();
        this.time = 0f;
        this.keyframes.clear();
        this.activeObjects.clear();
        
        if (recordingPath != null) {
            loadRecording(recordingPath);
        } else {
            FileRecordingStorage storage = new FileRecordingStorage();
            recordingFiles = storage.listRecordings();
        }
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        
        if (input.isKeyJustPressed(27)) { // ESC
            engine.setScene(new MenuScene(engine));
            return;
        }

        if (recordingPath == null) {
            handleFileSelection();
            return;
        }

        if (keyframes.isEmpty()) return;

        time += deltaTime;
        double lastT = keyframes.get(keyframes.size() - 1).t;
        if (time > lastT) {
            time = 0; // Loop
            // Reset scene for looping
            clear();
            activeObjects.clear();
        }

        // Interpolation Logic
        Keyframe a = keyframes.get(0);
        Keyframe b = keyframes.get(keyframes.size() - 1);
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe k1 = keyframes.get(i);
            Keyframe k2 = keyframes.get(i + 1);
            if (time >= k1.t && time <= k2.t) { a = k1; b = k2; break; }
        }
        
        double span = Math.max(1e-6, b.t - a.t);
        double u = Math.min(1.0, Math.max(0.0, (time - a.t) / span));
        
        syncObjects(a, b, (float)u);
    }

    @Override
    public void render() {
        renderer.drawRect(0, 0, 800, 600, 0.05f, 0.05f, 0.1f, 1.0f);

        if (recordingPath == null) {
            renderFileList();
            return;
        }

        super.render(); 
        renderHealthBars();

        renderer.drawString("REPLAY MODE", 350, 30, 0.5f, 1f, 0.5f, 1f, 24);
        renderer.drawString("Press ESC to Return", 330, 570, 0.8f, 0.8f, 0.8f, 1f, 20);
    }
    
    private void renderHealthBars() {
        for (GameObject obj : activeObjects.values()) {
            HealthComponent hc = obj.getComponent(HealthComponent.class);
            TransformComponent tc = obj.getComponent(TransformComponent.class);
            if (hc != null && tc != null && hc.maxHealth > 0) {
                 Vector2 pos = tc.getPosition();
                float healthPercentage = hc.currentHealth / hc.maxHealth;
                float barWidth = 30;
                float barHeight = 5;
                float yOffset = -30;
                float x = pos.x - (barWidth / 2);
                
                // Simple heuristic to guess offset based on name
                if (obj.getName().contains("Enemy")) {
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

    private void handleFileSelection() {
        if (recordingFiles == null || recordingFiles.isEmpty()) return;

        if (input.isKeyJustPressed(38)) { // UP
            selectedIndex = (selectedIndex - 1 + recordingFiles.size()) % recordingFiles.size();
        } else if (input.isKeyJustPressed(40)) { // DOWN
            selectedIndex = (selectedIndex + 1) % recordingFiles.size();
        } else if (input.isKeyJustPressed(10) || input.isKeyJustPressed(32)) { // ENTER/SPACE
            recordingPath = recordingFiles.get(selectedIndex).getAbsolutePath();
            initialize(); 
        }
    }

    private void renderFileList() {
        renderer.drawString("SELECT RECORDING", 300, 50, 1, 1, 1, 1, 30);
        if (recordingFiles == null || recordingFiles.isEmpty()) {
            renderer.drawString("No recordings found.", 300, 300, 1, 0.5f, 0.5f, 1, 24);
            return;
        }
        float startY = 100;
        for (int i = 0; i < recordingFiles.size(); i++) {
            String name = recordingFiles.get(i).getName();
            float y = startY + i * 30;
            if (i == selectedIndex) {
                renderer.drawRect(100, y, 600, 25, 0.3f, 0.3f, 0.4f, 0.8f);
                renderer.drawString("> " + name, 110, y+5, 1, 1, 0, 1, 20);
            } else {
                renderer.drawString(name, 120, y+5, 0.8f, 0.8f, 0.8f, 1, 20);
            }
        }
    }

    private void loadRecording(String path) {
        keyframes.clear();
        FileRecordingStorage storage = new FileRecordingStorage();
        try {
            for (String line : storage.readLines(path)) {
                if (line.contains("\"type\":\"keyframe\"")) {
                    Keyframe kf = new Keyframe();
                    kf.t = RecordingJson.parseDouble(RecordingJson.field(line, "t"));
                    int idx = line.indexOf("\"entities\":[");
                    if (idx >= 0) {
                        int bracket = line.indexOf('[', idx);
                        String arr = bracket >= 0 ? RecordingJson.extractArray(line, bracket) : "";
                        String[] parts = RecordingJson.splitTopLevel(arr);
                        for (String p : parts) {
                            Keyframe.EntityInfo ei = new Keyframe.EntityInfo();
                            ei.id = RecordingJson.stripQuotes(RecordingJson.field(p, "id"));
                            double x = RecordingJson.parseDouble(RecordingJson.field(p, "x"));
                            double y = RecordingJson.parseDouble(RecordingJson.field(p, "y"));
                            ei.pos = new Vector2((float)x, (float)y);
                            ei.rt = RecordingJson.stripQuotes(RecordingJson.field(p, "rt"));
                            ei.w = (float)RecordingJson.parseDouble(RecordingJson.field(p, "w"));
                            ei.h = (float)RecordingJson.parseDouble(RecordingJson.field(p, "h"));
                            
                            // Health Parsing
                            String hpStr = RecordingJson.field(p, "hp");
                            if (hpStr != null) {
                                ei.hp = (float)RecordingJson.parseDouble(hpStr);
                                ei.maxHp = (float)RecordingJson.parseDouble(RecordingJson.field(p, "maxHp"));
                            }

                            String colorArr = RecordingJson.field(p, "color");
                            if (colorArr != null && colorArr.startsWith("[")) {
                                String c = colorArr.substring(1, Math.max(1, colorArr.indexOf(']', 1)));
                                String[] cs = c.split(",");
                                if (cs.length >= 3) {
                                    try {
                                        ei.r = Float.parseFloat(cs[0].trim());
                                        ei.g = Float.parseFloat(cs[1].trim());
                                        ei.b = Float.parseFloat(cs[2].trim());
                                        if (cs.length >= 4) ei.a = Float.parseFloat(cs[3].trim());
                                    } catch (Exception ignored) {}
                                }
                            }
                            kf.entities.put(ei.id, ei);
                        }
                    }
                    keyframes.add(kf);
                }
            }
            keyframes.sort(Comparator.comparingDouble(k -> k.t));
        } catch (Exception ignored) {}
    }

    private GameObject createVisualFor(Keyframe.EntityInfo ei) {
        GameObject obj;
        String rawId = ei.id.contains("_") ? ei.id.substring(0, ei.id.lastIndexOf('_')) : ei.id;
        
        if ("Player".equals(rawId)) {
            obj = EntityFactory.createPlayerVisual(renderer);
        } else {
            obj = EntityFactory.createRenderableVisual(renderer, rawId, ei.rt, ei.w, ei.h, ei.r, ei.g, ei.b, ei.a);
        }
        
        // IMPORTANT: Set the exact ID from recording so we can map it
        obj.setName(ei.id); 
        
        TransformComponent tc = obj.getComponent(TransformComponent.class);
        if (tc == null) obj.addComponent(new TransformComponent(ei.pos));
        else tc.setPosition(ei.pos);
        
        if (ei.hp >= 0) {
            obj.addComponent(new HealthComponent(ei.maxHp));
        }
        
        return obj;
    }

    private void syncObjects(Keyframe a, Keyframe b, float u) {
        Set<String> presentIds = new HashSet<>();
        
        // We mainly sync based on Frame A (or B? usually A for existence, interpolate to B)
        // Ideally we check all entities in A and B.
        // Simplification: Exist if in A. Lerp to B if in B, else stay at A or lerp to last known?
        // Better: Exist if in A OR B?
        // Let's assume exist if in A.
        
        for (String id : a.entities.keySet()) {
            presentIds.add(id);
            Keyframe.EntityInfo infoA = a.entities.get(id);
            Keyframe.EntityInfo infoB = b.entities.get(id); // Might be null if destroyed
            
            GameObject obj = activeObjects.get(id);
            if (obj == null) {
                obj = createVisualFor(infoA);
                activeObjects.put(id, obj);
                addGameObject(obj);
            }
            
            // Interpolate
            Vector2 posA = infoA.pos;
            Vector2 posB = (infoB != null) ? infoB.pos : infoA.pos;
            
            float x = (float)((1.0 - u) * posA.x + u * posB.x);
            float y = (float)((1.0 - u) * posA.y + u * posB.y);
            
            TransformComponent tc = obj.getComponent(TransformComponent.class);
            if (tc != null) tc.setPosition(new Vector2(x, y));
            
            // Sync Health
            HealthComponent hc = obj.getComponent(HealthComponent.class);
            if (hc != null) {
                // Linear interp health for smoothness? or just snap? Snap is safer for discrete events.
                hc.currentHealth = infoA.hp;
                hc.maxHealth = infoA.maxHp;
            }
        }
        
        // Destroy objects not in current frame
        // (Using Iterator to remove safely)
        Iterator<Map.Entry<String, GameObject>> it = activeObjects.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, GameObject> entry = it.next();
            if (!presentIds.contains(entry.getKey())) {
                // Object disappeared
                removeGameObject(entry.getValue());
                it.remove();
            }
        }
    }
}