package com.gameengine.components;

import com.gameengine.core.Component;

public class HealthComponent extends Component<HealthComponent> {
    public float currentHealth;
    public float maxHealth;
    public boolean isInvincible;
    public float invincibilityTimer;

    public HealthComponent(float maxHealth) {
        this.maxHealth = maxHealth;
        this.currentHealth = maxHealth;
        this.isInvincible = false;
        this.invincibilityTimer = 0;
    }

    @Override
    public void initialize() {}

    @Override
    public void update(float deltaTime) {
        if (isInvincible) {
            invincibilityTimer -= deltaTime;
            if (invincibilityTimer <= 0) {
                isInvincible = false;
            }
        }
    }

    @Override
    public void render() {}

    public void takeDamage(float damage) {
        if (!isInvincible) {
            this.currentHealth -= damage;
            if (this.currentHealth < 0) {
                this.currentHealth = 0;
            }
        }
    }

    public void setInvincible(float duration) {
        this.isInvincible = true;
        this.invincibilityTimer = duration;
    }
}
