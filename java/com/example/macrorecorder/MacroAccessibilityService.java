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
        Log.d("MacroAccessibilityService", "onCreate() вызван");
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        isServiceConnected = true;
        Log.d("MacroAccessibilityService", "Сервис подключен");

        // Настройка сервиса
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED |
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED |
                AccessibilityEvent.TYPE_VIEW_SCROLLED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;

        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isRecording) {
            return;
        }

        int eventType = -1;

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                eventType = EVENT_TYPE_CLICK;
                Log.d("MacroAccessibilityService", "Записан клик");
                break;
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
                eventType = EVENT_TYPE_LONG_CLICK;
                Log.d("MacroAccessibilityService", "Записан долгий клик");
                break;
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                // We don't record these events for macro purposes
                return;
            default:
                return; // Записываем только клики
        }

        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            try {
                // Получаем координаты элемента
                Rect bounds = new Rect();
                source.getBoundsInScreen(bounds);

                if (bounds.isEmpty()) {
                    // Если не можем получить границы, пробуем другие методы
                    Log.w("MacroAccessibilityService", "Bounds пустые, пытаемся получить координаты другим способом");

                    // Пробуем получить информацию о событии
                    List<CharSequence> eventText = event.getText();
                    if (eventText != null && !eventText.isEmpty()) {
                        // Можно записать информацию о тексте
                        Log.d("MacroAccessibilityService", "Текст события: " + eventText.get(0));
                    }

                    // Пытаемся получить координаты через родительский элемент
                    Rect parentBounds = new Rect();
                    source.getBoundsInParent(parentBounds);

                    if (!parentBounds.isEmpty()) {
                        bounds = parentBounds;
                        Log.d("MacroAccessibilityService", "Используем координаты из родителя");
                    } else {
                        // Если все еще пусто, используем дефолтные координаты
                        bounds.set(100, 100, 200, 200); // Дефолтные координаты
                        Log.w("MacroAccessibilityService", "Используем дефолтные координаты");
                    }
                }

                // Используем центр элемента
                float x = bounds.left + bounds.width() / 2f;
                float y = bounds.top + bounds.height() / 2f;

                long timestamp = System.currentTimeMillis() - recordingStartTime;

                // Создаем и добавляем действие
                MacroAction action = new MacroAction(eventType, x, y, timestamp);
                Log.d("MacroAccessibilityService", "Создано действие: тип=" + eventType +
                        ", x=" + x + ", y=" + y + ", время=" + timestamp);

                // Рассчитываем задержку
                if (!recordedActions.isEmpty()) {
                    MacroAction prev = recordedActions.get(recordedActions.size() - 1);
                    long delay = timestamp - prev.getTimestamp();
                    action.setDelay(delay);
                    Log.d("MacroAccessibilityService", "Задержка: " + delay + "ms");
                } else {
                    action.setDelay(0);
                }

                recordedActions.add(action);
                Log.d("MacroRecorder", "Записано действие: " + eventType + " в (" + x + ", " + y + ")");

            } catch (Exception e) {
                Log.e("MacroAccessibilityService", "Ошибка обработки события: " + e.getMessage());
            } finally {
                source.recycle();
            }
        } else {
            Log.w("MacroAccessibilityService", "Источник события null");
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
        Log.d("MacroAccessibilityService", "Вызов startRecording с именем: " + presetName);

        isRecording = true;
        currentPresetName = presetName;
        recordedActions = new ArrayList<>();
        recordingStartTime = System.currentTimeMillis();
        Log.d("MacroAccessibilityService", "Начало записи: " + presetName + ", время начала: " + recordingStartTime);

        // Добавляем первое действие (начало записи)
        MacroAction startAction = new MacroAction(
                EVENT_TYPE_TOUCH_DOWN,
                0, 0,
                0
        );
        recordedActions.add(startAction);
        Log.d("MacroAccessibilityService", "Добавлено начальное действие, всего действий: " + recordedActions.size());
    }

    public void stopRecording() {
        Log.d("MacroAccessibilityService", "Вызов stopRecording, isRecording=" + isRecording);

        if (!isRecording) return;

        isRecording = false;
        Log.d("MacroAccessibilityService", "Окончание записи. Записано действий: " +
                (recordedActions != null ? recordedActions.size() : 0));

        // Добавляем последнее действие (окончание записи)
        if (recordedActions != null && !recordedActions.isEmpty()) {
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
            Log.d("MacroAccessibilityService", "Добавлено конечное действие");
        }

        // Сохраняем пресет
        if (recordedActions != null && !recordedActions.isEmpty()) {
            try {
                Preset preset = new Preset(
                        PresetRepository.generateId(),
                        currentPresetName,
                        System.currentTimeMillis(),
                        recordedActions
                );

                presetRepository.savePreset(preset);
                Log.d("MacroAccessibilityService", "Пресет сохранен: " + preset.getName() +
                        ", ID: " + preset.getId() + ", действий: " + preset.getActions().size());
            } catch (Exception e) {
                Log.e("MacroAccessibilityService", "Ошибка сохранения пресета: " + e.getMessage());
            }
        } else {
            Log.w("MacroAccessibilityService", "Нет действий для сохранения");
        }

        recordedActions = null;
        currentPresetName = null;
        Log.d("MacroAccessibilityService", "Запись завершена и очищена");
    }

    public void playMacro(List<MacroAction> actions) {
        if (actions == null || actions.isEmpty()) {
            Log.e("MacroAccessibilityService", "Нет действий для воспроизведения");
            return;
        }

        Log.d("MacroAccessibilityService", "Начало воспроизведения макроса, действий: " + actions.size());

        new Thread(() -> {
            try {
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
                            // Начало жеста
                            Log.d("MacroAccessibilityService", "Начало жеста");
                            break;
                        case EVENT_TYPE_TOUCH_UP:
                            // Окончание жеста
                            Log.d("MacroAccessibilityService", "Окончание жеста");
                            break;
                    }
                }
                Log.d("MacroAccessibilityService", "Воспроизведение завершено");
            } catch (Exception e) {
                Log.e("MacroAccessibilityService", "Ошибка воспроизведения: " + e.getMessage());
            }
        }).start();
    }

    private void dispatchClick(float x, float y) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
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
                Log.d("MacroAccessibilityService", "Отправлен клик в (" + x + ", " + y + ")");
            } catch (Exception e) {
                Log.e("MacroAccessibilityService", "Ошибка отправки клика: " + e.getMessage());
            }
        } else {
            Log.w("MacroAccessibilityService", "API < 24, dispatchGesture не поддерживается");
        }
    }

    private void dispatchLongClick(float x, float y) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
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
                Log.d("MacroAccessibilityService", "Отправлен долгий клик в (" + x + ", " + y + ")");
            } catch (Exception e) {
                Log.e("MacroAccessibilityService", "Ошибка отправки долгого клика: " + e.getMessage());
            }
        }
    }

    public boolean isRecording() {
        Log.d("MacroAccessibilityService", "isRecording() возвращает: " + isRecording);
        return isRecording;
    }

    public boolean isServiceConnected() {
        Log.d("MacroAccessibilityService", "isServiceConnected() возвращает: " + isServiceConnected);
        return isServiceConnected;
    }

    public int getRecordedActionsCount() {
        int count = recordedActions != null ? recordedActions.size() : 0;
        Log.d("MacroAccessibilityService", "getRecordedActionsCount() возвращает: " + count);
        return count;
    }

    public String getCurrentPresetName() {
        Log.d("MacroAccessibilityService", "getCurrentPresetName() возвращает: " + currentPresetName);
        return currentPresetName;
    }

    public static MacroAccessibilityService getInstance() {
        Log.d("MacroAccessibilityService", "getInstance() вызван, instance=" + instance);
        return instance;
    }

    // Метод для тестирования
    public void testService() {
        Log.d("MacroAccessibilityService", "testService() вызван");
        // Можно добавить уведомление
    }
}