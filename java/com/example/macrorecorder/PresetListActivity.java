package com.example.macrorecorder;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.macrorecorder.data.Preset;
import com.example.macrorecorder.repository.PresetRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PresetListActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private PresetAdapter adapter;
    private PresetRepository presetRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preset_list);

        presetRepository = new PresetRepository(this);

        recyclerView = findViewById(R.id.preset_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        FloatingActionButton fab = findViewById(R.id.fab_new_preset);
        fab.setOnClickListener(v -> {
            // Здесь можно добавить создание нового пресета
            finish(); // Возвращаемся к плавающей кнопке
        });

        loadPresets();
    }

    private void loadPresets() {
        List<Preset> presets = presetRepository.getAllPresets();
        adapter = new PresetAdapter(presets);
        recyclerView.setAdapter(adapter);
    }

    private class PresetAdapter extends RecyclerView.Adapter<PresetAdapter.ViewHolder> {
        private List<Preset> presets;

        public PresetAdapter(List<Preset> presets) {
            this.presets = presets;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_preset, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Preset preset = presets.get(position);

            holder.nameText.setText(preset.getName());

            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            holder.dateText.setText(sdf.format(new Date(preset.getDateCreated())));

            long duration = preset.getDuration();
            holder.durationText.setText(String.format(Locale.getDefault(),
                    "%d.%03d сек", duration / 1000, duration % 1000));

            holder.itemView.setOnClickListener(v -> {
                presetRepository.setCurrentPresetId(preset.getId());
                finish();
            });

            holder.itemView.setOnLongClickListener(v -> {
                // Диалог удаления/переименования
                showPresetOptionsDialog(preset);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return presets.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView dateText;
            TextView durationText;

            ViewHolder(View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.preset_name);
                dateText = itemView.findViewById(R.id.preset_date);
                durationText = itemView.findViewById(R.id.preset_duration);
            }
        }
    }

    private void showPresetOptionsDialog(Preset preset) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(preset.getName());

        String[] options = {"Удалить", "Переименовать", "Отмена"};
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // Удаление
                presetRepository.deletePreset(preset.getId());
                loadPresets();
            } else if (which == 1) {
                // Переименование
                renamePreset(preset);
            }
        });

        builder.show();
    }

    private void renamePreset(Preset preset) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Переименовать");

        final androidx.appcompat.widget.AppCompatEditText input =
                new androidx.appcompat.widget.AppCompatEditText(this);
        input.setText(preset.getName());
        builder.setView(input);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                preset.setName(newName);
                presetRepository.savePreset(preset);
                loadPresets();
            }
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }
}