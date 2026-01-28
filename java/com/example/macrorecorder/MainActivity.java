package com.example.macrorecorder;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.Manifest;

public class MainActivity extends AppCompatActivity {
    private static final int OVERLAY_PERMISSION_REQUEST = 1001;
    private static final int ACCESSIBILITY_PERMISSION_REQUEST = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Проверяем и запрашиваем разрешения
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        // 1. Проверка разрешения на отображение поверх других окон
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
            } else {
                checkAccessibilityPermission();
            }
        } else {
            checkAccessibilityPermission();
        }
    }

    private void checkAccessibilityPermission() {
        // 2. Проверка включенного Accessibility Service
        // (Пользователь должен включить его вручную в настройках)
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivityForResult(intent, ACCESSIBILITY_PERMISSION_REQUEST);

        // Запускаем сервис
        startService(new Intent(this, MainService.class));

        // Закрываем Activity, оставляя сервис работать
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    checkAccessibilityPermission();
                } else {
                    Toast.makeText(this,
                            "Разрешение необходимо для работы плавающей кнопки",
                            Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        }
    }
}