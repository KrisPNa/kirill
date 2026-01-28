package com.example.macrorecorder.data;

import com.google.gson.annotations.SerializedName;

public class MacroAction {
    @SerializedName("event_type")
    private int eventType; // MotionEvent.ACTION_DOWN, ACTION_UP и т.д.

    @SerializedName("x")
    private float x;

    @SerializedName("y")
    private float y;

    @SerializedName("timestamp")
    private long timestamp;

    @SerializedName("delay")
    private long delay; // Задержка после предыдущего события (мс)

    public MacroAction(int eventType, float x, float y, long timestamp) {
        this.eventType = eventType;
        this.x = x;
        this.y = y;
        this.timestamp = timestamp;
    }

    // Геттеры и сеттеры
    public int getEventType() { return eventType; }
    public void setEventType(int eventType) { this.eventType = eventType; }

    public float getX() { return x; }
    public void setX(float x) { this.x = x; }

    public float getY() { return y; }
    public void setY(float y) { this.y = y; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getDelay() { return delay; }
    public void setDelay(long delay) { this.delay = delay; }
}