package com.example.macrorecorder.repository;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.example.macrorecorder.data.Preset;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PresetRepository {
    private static final String PREFS_NAME = "macro_recorder_prefs";
    private static final String PRESETS_KEY = "presets";
    private static final String CURRENT_PRESET_KEY = "current_preset_id";

    private final SharedPreferences prefs;
    private final Gson gson;

    public PresetRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void savePreset(Preset preset) {
        List<Preset> presets = getAllPresets();

        // Если пресет с таким ID уже существует, заменяем его
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).getId().equals(preset.getId())) {
                presets.set(i, preset);
                saveAllPresets(presets);
                return;
            }
        }

        // Иначе добавляем новый
        presets.add(preset);
        saveAllPresets(presets);
    }

    public List<Preset> getAllPresets() {
        String json = prefs.getString(PRESETS_KEY, "[]");
        Type type = new TypeToken<List<Preset>>(){}.getType();
        List<Preset> presets = gson.fromJson(json, type);
        return presets != null ? presets : new ArrayList<>();
    }

    public Preset getPresetById(String id) {
        for (Preset preset : getAllPresets()) {
            if (preset.getId().equals(id)) {
                return preset;
            }
        }
        return null;
    }

    public void deletePreset(String id) {
        List<Preset> presets = getAllPresets();
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).getId().equals(id)) {
                presets.remove(i);
                saveAllPresets(presets);

                // Если удаляем текущий пресет, сбрасываем выбор
                if (id.equals(getCurrentPresetId())) {
                    setCurrentPresetId(null);
                }
                break;
            }
        }
    }

    public String getCurrentPresetId() {
        return prefs.getString(CURRENT_PRESET_KEY, null);
    }

    public void setCurrentPresetId(String id) {
        prefs.edit().putString(CURRENT_PRESET_KEY, id).apply();
    }

    public Preset getCurrentPreset() {
        String id = getCurrentPresetId();
        return id != null ? getPresetById(id) : null;
    }

    private void saveAllPresets(List<Preset> presets) {
        String json = gson.toJson(presets);
        prefs.edit().putString(PRESETS_KEY, json).apply();
    }

    public static String generateId() {
        return UUID.randomUUID().toString();
    }
}