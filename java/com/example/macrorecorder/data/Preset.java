package com.example.macrorecorder.data;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class Preset {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("date_created")
    private long dateCreated;

    @SerializedName("actions")
    private List<MacroAction> actions;

    public Preset(String id, String name, long dateCreated, List<MacroAction> actions) {
        this.id = id;
        this.name = name;
        this.dateCreated = dateCreated;
        this.actions = actions;
    }

    // Геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getDateCreated() { return dateCreated; }
    public void setDateCreated(long dateCreated) { this.dateCreated = dateCreated; }

    public List<MacroAction> getActions() { return actions; }
    public void setActions(List<MacroAction> actions) { this.actions = actions; }

    // Вспомогательный метод для получения длительности
    public long getDuration() {
        if (actions == null || actions.isEmpty()) return 0;
        long first = actions.get(0).getTimestamp();
        long last = actions.get(actions.size() - 1).getTimestamp();
        return last - first;
    }
}