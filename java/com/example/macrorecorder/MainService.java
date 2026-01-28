package com.example.macrorecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import com.example.macrorecorder.data.Preset;
import com.example.macrorecorder.repository.PresetRepository;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainService extends Service {
    private WindowManager windowManager;
    private View floatingButtonView;
    private Button stopButton;
    private TextView countdownTextView;
    private WindowManager.LayoutParams floatingButtonParams;
    private WindowManager.LayoutParams stopButtonParams;
    private WindowManager.LayoutParams countdownParams;
    private PresetRepository presetRepository;
    private boolean isRecording = false;
    private CountDownTimer countDownTimer;

    private static final String CHANNEL_ID = "MacroRecorderChannel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        presetRepository = new PresetRepository(this);
        createNotificationChannel();
        showFloatingButton();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeFloatingButton();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Macro Recorder Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Macro Recorder")
                .setContentText("Сервис активен. Нажмите для управления.")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void showFloatingButton() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Параметры для плавающей кнопки
        floatingButtonParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        floatingButtonParams.gravity = Gravity.TOP | Gravity.START;
        floatingButtonParams.x = 100;
        floatingButtonParams.y = 100;

        // Создаем view для кнопки
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        floatingButtonView = inflater.inflate(R.layout.floating_button, null);

        Button mainButton = floatingButtonView.findViewById(R.id.floating_main_button);

        // Создаем собственный обработчик касаний для drag-and-drop
        mainButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long touchStartTime;
            private boolean isDragging = false;
            private static final int CLICK_THRESHOLD = 200; // 200ms
            private static final int DRAG_THRESHOLD = 10; // 10px

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = floatingButtonParams.x;
                        initialY = floatingButtonParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;

                        // Проверяем, началось ли перетаскивание
                        if (!isDragging &&
                                (Math.abs(deltaX) > DRAG_THRESHOLD || Math.abs(deltaY) > DRAG_THRESHOLD)) {
                            isDragging = true;
                        }

                        // Обновляем позицию только если началось перетаскивание
                        if (isDragging) {
                            floatingButtonParams.x = initialX + (int) deltaX;
                            floatingButtonParams.y = initialY + (int) deltaY;
                            windowManager.updateViewLayout(floatingButtonView, floatingButtonParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        long touchDuration = System.currentTimeMillis() - touchStartTime;

                        // Если не было перетаскивания и было короткое нажатие - это клик
                        if (!isDragging && touchDuration < CLICK_THRESHOLD) {
                            showPopupMenu(mainButton);
                        }
                        return true;
                }
                return false;
            }
        });

        // Добавляем кнопку на экран
        windowManager.addView(floatingButtonView, floatingButtonParams);
    }

    private void showPopupMenu(View anchor) {
        try {
            PopupMenu popupMenu = new PopupMenu(this, anchor);
            popupMenu.getMenuInflater().inflate(R.menu.main_menu, popupMenu.getMenu());

            // Временно добавьте тестовый пункт меню
            popupMenu.getMenu().add("Тест записи");

            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();

                if (id == R.id.menu_play) {
                    playMacro();
                    return true;
                } else if (id == R.id.menu_select_preset) {
                    selectPreset();
                    return true;
                } else if (id == R.id.menu_record) {
                    startRecording();
                    return true;
                } else if (id == R.id.menu_close) {
                    stopSelf();
                    return true;
                } else if (item.getTitle().equals("Тест записи")) {
                    testRecording();
                    return true;
                }

                return false;
            });

            popupMenu.show();
        } catch (Exception e) {
            Log.e("MainService", "Ошибка показа меню: " + e.getMessage());
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void testRecording() {
        showToast("Тест записи...");
        showDebugInfo();

        MacroAccessibilityService service = MacroAccessibilityService.getInstance();
        if (service == null) {
            showToast("Сервис = null");
        } else if (!service.isServiceConnected()) {
            showToast("Сервис не подключен");
        } else {
            showToast("Сервис готов!");

            // Тестовая запись
            service.startRecording("Тестовый макрос");
            showToast("Запись начата");

            // Остановить через 3 секунды
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (service.isRecording()) {
                    service.stopRecording();
                    showToast("Запись сохранена");
                }
            }, 3000);
        }
    }

    private void playMacro() {
        Preset currentPreset = presetRepository.getCurrentPreset();
        if (currentPreset == null) {
            showToast("Сначала выберите пресет!");
            return;
        }

        showCountdown("Воспроизведение начнется через", () -> {
            MacroAccessibilityService service = MacroAccessibilityService.getInstance();
            if (service != null) {
                service.playMacro(currentPreset.getActions());
            } else {
                showToast("Сервис недоступен. Проверьте разрешения.");
            }
        });
    }

    private void selectPreset() {
        Intent intent = new Intent(this, PresetListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startRecording() {
        // Проверяем, включен ли Accessibility Service
        if (!isAccessibilityServiceEnabled()) {
            showToast("Включите сервис специальных возможностей в настройках");
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }

        // Проверяем состояние сервиса
        MacroAccessibilityService service = MacroAccessibilityService.getInstance();
        if (service == null) {
            showToast("Сервис не инициализирован. Подождите...");

            // Пытаемся инициализировать
            ensureAccessibilityService();

            // Даем время на инициализацию
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (MacroAccessibilityService.getInstance() != null) {
                    showNameInputDialog();
                } else {
                    showToast("Не удалось инициализировать сервис. Перезапустите приложение.");
                }
            }, 1000);
        } else {
            // Сервис доступен, показываем диалог
            showNameInputDialog();
        }
    }

    private void showDebugInfo() {
        StringBuilder info = new StringBuilder();

        // Проверка разрешений
        boolean hasOverlayPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(this);
        info.append("Overlay permission: ").append(hasOverlayPermission).append("\n");

        // Проверка Accessibility Service
        boolean isAccessibilityEnabled = isAccessibilityServiceEnabled();
        info.append("Accessibility enabled: ").append(isAccessibilityEnabled).append("\n");

        // Проверка экземпляра сервиса
        MacroAccessibilityService service = MacroAccessibilityService.getInstance();
        info.append("Service instance: ").append(service != null ? "exists" : "null").append("\n");

        if (service != null) {
            info.append("Service connected: ").append(service.isServiceConnected()).append("\n");
            info.append("Service recording: ").append(service.isRecording()).append("\n");
        }

        Log.d("MainService", "Debug info: " + info.toString());
        // Можно показать Toast для отладки
        // showToast(info.toString());
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + MacroAccessibilityService.class.getCanonicalName();
        try {
            int accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );

            if (accessibilityEnabled == 1) {
                String settingValue = Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );
                if (settingValue != null) {
                    TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                    splitter.setString(settingValue);
                    while (splitter.hasNext()) {
                        String serviceName = splitter.next();
                        if (serviceName.equalsIgnoreCase(service)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.e("MainService", "Ошибка проверки Accessibility: " + e.getMessage());
        }
        return false;
    }

    private void showNameInputDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_MacroRecorder_Dialog);
        builder.setTitle("Введите имя макроса");

        final androidx.appcompat.widget.AppCompatEditText input =
                new androidx.appcompat.widget.AppCompatEditText(this);
        input.setHint("Мой макрос");
        builder.setView(input);

        builder.setPositiveButton("Начать запись", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                name = "Макрос " + new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        .format(new Date());
            }
            startRecordingWithName(name);
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void startRecordingWithName(String presetName) {
        showCountdown("Запись начнется через", new Runnable() {
            @Override
            public void run() {
                isRecording = true;
                showStopButton();

                // Запускаем запись в Accessibility Service
                MacroAccessibilityService service = MacroAccessibilityService.getInstance();

                if (service != null) {
                    // Проверяем, готов ли сервис к записи
                    if (service.isServiceConnected()) {
                        service.startRecording(presetName);
                        showToast("Запись началась!");
                    } else {
                        showToast("Сервис не готов. Подождите...");
                        // Ждем подключения
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (service.isServiceConnected()) {
                                service.startRecording(presetName);
                                showToast("Запись началась!");
                            } else {
                                showToast("Не удалось подключить сервис");
                                hideStopButton();
                                isRecording = false;
                            }
                        }, 2000);
                    }
                } else {
                    showToast("Сервис записи недоступен. Перезапустите приложение.");
                    hideStopButton();
                    isRecording = false;
                }
            }
        });
    }

    private void testRecordingFeature() {
        showToast("Тестирование записи...");

        // 1. Проверка разрешения на overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                showToast("Нет разрешения на overlay");
                return;
            }
        }

        // 2. Проверка Accessibility Service
        if (!isAccessibilityServiceEnabled()) {
            showToast("Accessibility Service не включен");
            return;
        }

        // 3. Проверка экземпляра сервиса
        MacroAccessibilityService service = MacroAccessibilityService.getInstance();
        if (service == null) {
            showToast("Сервис не инициализирован (null)");
        } else {
            showToast("Сервис найден");
            if (service.isServiceConnected()) {
                showToast("Сервис подключен");
                // Запускаем тестовую запись
                service.startRecording("Тестовый макрос");
                showToast("Запись начата");

                // Останавливаем через 3 секунды
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (service.isRecording()) {
                        service.stopRecording();
                        showToast("Запись сохранена!");
                    }
                }, 3000);
            } else {
                showToast("Сервис не подключен");
            }
        }
    }

    private void ensureAccessibilityService() {
        MacroAccessibilityService service = MacroAccessibilityService.getInstance();

        if (service == null || !service.isServiceConnected()) {
            showToast("Переподключение сервиса...");

            // Пытаемся перезапустить сервис
            Intent intent = new Intent(this, MacroAccessibilityService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            // Ждем подключения
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (MacroAccessibilityService.getInstance() != null) {
                    showToast("Сервис готов к записи");
                } else {
                    showToast("Не удалось подключить сервис");
                }
            }, 2000);
        }
    }

    private void hideStopButton() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (stopButton != null) {
                try {
                    windowManager.removeView(stopButton);
                } catch (IllegalArgumentException e) {
                    Log.e("MainService", "Кнопка уже удалена");
                }
                stopButton = null;
            }
        });
    }

    private void showCountdown(String message, Runnable onFinish) {
        // Создаем TextView для обратного отсчета
        countdownParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT
        );
        countdownParams.dimAmount = 0.5f;
        countdownParams.gravity = Gravity.CENTER;

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        countdownTextView = (TextView) inflater.inflate(R.layout.countdown_overlay, null);
        countdownTextView.setText(message + "\n3");

        windowManager.addView(countdownTextView, countdownParams);

        countDownTimer = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                countdownTextView.setText(message + "\n" + seconds);
            }

            @Override
            public void onFinish() {
                windowManager.removeView(countdownTextView);
                countdownTextView = null;
                onFinish.run();
            }
        }.start();
    }

    private void showStopButton() {
        // Убедимся, что мы в главном потоке
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (stopButton != null && stopButton.isAttachedToWindow()) {
                    windowManager.removeView(stopButton);
                }

                stopButtonParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );
                stopButtonParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                stopButtonParams.y = 200;

                stopButton = new Button(MainService.this);
                stopButton.setText("СТОП");
                stopButton.setTextColor(Color.WHITE);
                stopButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                stopButton.setPadding(40, 20, 40, 20);
                stopButton.setBackgroundColor(Color.RED);

                // Скругленные углы для кнопки
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.RECTANGLE);
                shape.setCornerRadius(30);
                shape.setColor(Color.RED);
                stopButton.setBackground(shape);

                stopButton.setOnClickListener(v -> {
                    stopRecording();
                    showToast("Запись остановлена");
                });

                windowManager.addView(stopButton, stopButtonParams);
            } catch (Exception e) {
                Log.e("MainService", "Ошибка создания кнопки СТОП: " + e.getMessage());
                showToast("Ошибка: " + e.getMessage());
            }
        });
    }

    private void stopRecording() {
        if (!isRecording) {
            return;
        }

        isRecording = false;

        // Убираем кнопку СТОП
        new Handler(Looper.getMainLooper()).post(() -> {
            if (stopButton != null) {
                try {
                    windowManager.removeView(stopButton);
                } catch (IllegalArgumentException e) {
                    Log.e("MainService", "Кнопка уже удалена");
                }
                stopButton = null;
            }
        });

        // Останавливаем запись в Accessibility Service
        MacroAccessibilityService service = MacroAccessibilityService.getInstance();
        if (service != null && service.isRecording()) {
            service.stopRecording();
            showToast("Запись сохранена!");

            // Обновляем список пресетов (если нужно)
            selectPreset();
        } else {
            showToast("Сервис записи не активен");
        }
    }

    private void removeFloatingButton() {
        if (floatingButtonView != null) {
            windowManager.removeView(floatingButtonView);
            floatingButtonView = null;
        }
        if (stopButton != null) {
            windowManager.removeView(stopButton);
            stopButton = null;
        }
        if (countdownTextView != null) {
            windowManager.removeView(countdownTextView);
            countdownTextView = null;
        }
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(MainService.this, message, Toast.LENGTH_SHORT).show()
        );
    }
}