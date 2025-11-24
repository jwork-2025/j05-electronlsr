package com.gameengine.recording;

public class RecordingConfig {
    public String outputPath;
    public float keyframeIntervalSec = 0.05f; // Increased frequency for smoother replay
    public int sampleFps = 30;
    public int quantizeDecimals = 2;
    public int queueCapacity = 4096;

    public RecordingConfig(String outputPath) {
        this.outputPath = outputPath;
    }
}