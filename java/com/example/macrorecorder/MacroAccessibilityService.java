package com.example.macrorecorder;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Handler;
import android.os.Looper;
import com.example.macrorecorder.data.MacroAction;
import com.example.macrorecorder.data.Preset;
import com.example.macrorecorder.repository.PresetRepository;
import java.util.ArrayList;
import java.util.List;

public class MacroAccessibilityService extends AccessibilityService {
    private static MacroAccessibilityService instance;

    private boolean isRecording = false;
    private boolean isServiceConnected = false;
    private String currentPresetName;
    private List<MacroAction> recordedActions;
    private long recordingStartTime;
    private PresetRepository presetRepository;

    // Константы для типов событий
    private static final int EVENT_TYPE_CLICK = 0;
    private static final int EVENT_TYPE_LONG_CLICK = 1;
    private static final int EVENT_TYPE_TOUCH_DOWN = 2;
    private static final int EVENT_TYPE_TOUCH_UP = 3;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        presetRepository = new PresetRepository(this);
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        isServiceConnected = true;
        Log.d("MacroAccessibilityService", "Сервис подключен");

        // Настройка сервиса
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED |
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;

        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isRecording) return;

        int eventType = -1;

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                eventType = EVENT_TYPE_CLICK;
                break;
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
                eventType = EVENT_TYPE_LONG_CLICK;
                break;
            default:
                return; // Записываем только клики
        }

        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            // Получаем координаты элемента
            Rect bounds = new Rect();
            source.getBoundsInScreen(bounds);

            if (bounds.isEmpty()) {
                source.recycle();
                return;
            }

            // Используем центр элемента
            float x = bounds.left + bounds.width() / 2f;
            float y = bounds.top + bounds.height() / 2f;

            long timestamp = System.currentTimeMillis() - recordingStartTime;

            // Создаем и добавляем действие
            MacroAction action = new MacroAction(eventType, x, y, timestamp);

            // Рассчитываем задержку
            if (!recordedActions.isEmpty()) {
                MacroAction prev = recordedActions.get(recordedActions.size() - 1);
                action.setDelay(timestamp - prev.getTimestamp());
            } else {
                action.setDelay(0);
            }

            recordedActions.add(action);
            Log.d("MacroRecorder", "Записано действие: " + eventType + " в (" + x + ", " + y + ")");
            source.recycle();
        }
    }

    @Override
    public void onInterrupt() {
        Log.d("MacroAccessibilityService", "Сервис был прерван");
        isServiceConnected = false;
        stopRecording();
    }

    @Override
    public void onDestroy() {
        isServiceConnected = false;
        instance = null;
        super.onDestroy();
        Log.d("MacroAccessibilityService", "Сервис уничтожен");
    }

    public void startRecording(String presetName) {
        isRecording = true;
        currentPresetName = presetName;
        recordedActions = new ArrayList<>();
        recordingStartTime = System.currentTimeMillis();
        Log.d("MacroAccessibilityService", "Начало записи: " + presetName);

        // Добавляем первое действие (начало записи)
        MacroAction startAction = new MacroAction(
                EVENT_TYPE_TOUCH_DOWN,
                0, 0,
                0
        );
        recordedActions.add(startAction);
    }

    public void stopRecording() {
        if (!isRecording) return;

        isRecording = false;
        Log.d("MacroAccessibilityService", "Окончание записи. Записано действий: " + recordedActions.size());

        // Добавляем последнее действие (окончание записи)
        if (!recordedActions.isEmpty()) {
            long endTime = System.currentTimeMillis() - recordingStartTime;
            MacroAction endAction = new MacroAction(
                    EVENT_TYPE_TOUCH_UP,
                    0, 0,
                    endTime
            );
            if (recordedActions.size() > 1) {
                MacroAction lastAction = recordedActions.get(recordedActions.size() - 1);
                endAction.setDelay(endTime - lastAction.getTimestamp());
            }
            recordedActions.add(endAction);
        }

        // Сохраняем пресет
        if (!recordedActions.isEmpty()) {
            Preset preset = new Preset(
                    PresetRepository.generateId(),
                    currentPresetName,
                    System.currentTimeMillis(),
                    recordedActions
            );

            presetRepository.savePreset(preset);
            Log.d("MacroAccessibilityService", "Пресет сохранен: " + preset.getName());
        }

        recordedActions = null;
        currentPresetName = null;
    }

    public void playMacro(List<MacroAction> actions) {
        if (actions == null || actions.isEmpty()) {
            Log.e("MacroAccessibilityService", "Нет действий для воспроизведения");
            return;
        }

        Log.d("MacroAccessibilityService", "Начало воспроизведения макроса");

        new Thread(() -> {
            for (int i = 0; i < actions.size(); i++) {
                MacroAction action = actions.get(i);

                // Задержка перед выполнением действия
                if (action.getDelay() > 0) {
                    try {
                        Thread.sleep(action.getDelay());
                    } catch (InterruptedException e) {
                        Log.e("MacroAccessibilityService", "Воспроизведение прервано: " + e.getMessage());
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                // Эмуляция жеста в зависимости от типа события
                switch (action.getEventType()) {
                    case EVENT_TYPE_CLICK:
                        dispatchClick(action.getX(), action.getY());
                        break;
                    case EVENT_TYPE_LONG_CLICK:
                        dispatchLongClick(action.getX(), action.getY());
                        break;
                    case EVENT_TYPE_TOUCH_DOWN:
                        // Начало жеста (можно пропустить, так как клик включает DOWN и UP)
                        break;
                    case EVENT_TYPE_TOUCH_UP:
                        // Окончание жеста
                        break;
                }
            }
            Log.d("MacroAccessibilityService", "Воспроизведение завершено");
        }).start();
    }

    private void dispatchClick(float x, float y) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);

            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(
                    clickPath, 0, 100 // Нажатие на 100 мс для обычного клика
            );
            gestureBuilder.addStroke(stroke);

            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    Log.d("MacroAccessibilityService", "Клик выполнен в (" + x + ", " + y + ")");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Log.w("MacroAccessibilityService", "Клик отменен в (" + x + ", " + y + ")");
                }
            }, null);
        } else {
            Log.w("MacroAccessibilityService", "API < 24, dispatchGesture не поддерживается");
        }
    }

    private void dispatchLongClick(float x, float y) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path longClickPath = new Path();
            longClickPath.moveTo(x, y);

            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(
                    longClickPath, 0, 500 // Нажатие на 500 мс для долгого клика
            );
            gestureBuilder.addStroke(stroke);

            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    Log.d("MacroAccessibilityService", "Долгий клик выполнен в (" + x + ", " + y + ")");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Log.w("MacroAccessibilityService", "Долгий клик отменен в (" + x + ", " + y + ")");
                }
            }, null);
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public boolean isServiceConnected() {
        return isServiceConnected;
    }

    public int getRecordedActionsCount() {
        return recordedActions != null ? recordedActions.size() : 0;
    }

    public String getCurrentPresetName() {
        return currentPresetName;
    }

    public static MacroAccessibilityService getInstance() {
        return instance;
    }
}