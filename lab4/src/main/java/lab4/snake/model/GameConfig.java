package lab4.snake.model;

public record GameConfig(int width, int height, int foodStatic, int stateDelayMs) {
    public static final int MIN_WIDTH = 10;
    public static final int MAX_WIDTH = 100;
    public static final int MIN_HEIGHT = 10;
    public static final int MAX_HEIGHT = 100;
    public static final int MIN_FOOD_STATIC = 0;
    public static final int MAX_FOOD_STATIC = 100;
    public static final int MIN_STATE_DELAY_MS = 100;
    public static final int MAX_STATE_DELAY_MS = 3000;

    public GameConfig(int width, int height, int foodStatic, int stateDelayMs) {
        this.width = clamp(width, MIN_WIDTH, MAX_WIDTH);
        this.height = clamp(height, MIN_HEIGHT, MAX_HEIGHT);
        this.foodStatic = clamp(foodStatic, MIN_FOOD_STATIC, MAX_FOOD_STATIC);
        this.stateDelayMs = clamp(stateDelayMs, MIN_STATE_DELAY_MS, MAX_STATE_DELAY_MS);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public int getPingDelayMs() {
        return stateDelayMs / 10;
    }

    public int getNodeTimeoutMs() {
        return (int) (stateDelayMs * 0.8);
    }
}