package com.gameengine.components;

import com.gameengine.core.Component;
import com.gameengine.math.Vector2;

public class BombComponent extends Component<BombComponent> {

    public enum State {
        TRAVELING,
        ARMING,
        EXPLODING,
        DONE
    }

    public State currentState;
    public Vector2 targetPosition;
    public float armingTimer = 1.5f;
    public float explosionVfxTimer = 0.5f;

    public BombComponent(Vector2 targetPosition) {
        this.targetPosition = targetPosition;
        this.currentState = State.TRAVELING;
    }

    @Override
    public void initialize() {}

    @Override
    public void update(float deltaTime) {

    }

    @Override
    public void render() {}
}
