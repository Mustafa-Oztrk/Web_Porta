package com.ZentrixStudio.webporta;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private DeviceAdapter adapter;
    private List<Device> deviceList;
    private List<Device> fullDeviceList;
    private SharedPreferences sharedPreferences;
    private int selectedColor;
    private TextInputEditText etSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_NO) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("WebPortaPrefs", Context.MODE_PRIVATE);
        
        checkDisclaimer();

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        
        fullDeviceList = loadDevices();
        deviceList = new ArrayList<>(fullDeviceList);
        sortDevices();

        adapter = new DeviceAdapter(this, deviceList, new DeviceAdapter.OnDeviceActionListener() {
            @Override
            public void onDeviceClick(Device device) {
                Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
                intent.putExtra("url", device.getIp());
                startActivity(intent);
            }

            @Override
            public void onDeviceLongClick(Device device, int position, View anchor) {
                showModernPopupMenu(device, position, anchor);
            }
        });
        recyclerView.setAdapter(adapter);

        etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterDevices(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();
                adapter.onItemMove(fromPos, toPos);
                saveDevices();
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        });
        touchHelper.attachToRecyclerView(recyclerView);

        ExtendedFloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> showAddDeviceDialog());
        
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) fab.shrink();
                else fab.extend();
            }
        });
    }

    private void filterDevices(String query) {
        deviceList.clear();
        if (query.isEmpty()) {
            deviceList.addAll(fullDeviceList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (Device device : fullDeviceList) {
                if (device.getName().toLowerCase().contains(lowerCaseQuery) || 
                    device.getIp().toLowerCase().contains(lowerCaseQuery)) {
                    deviceList.add(device);
                }
            }
        }
        sortDevices();
        adapter.notifyDataSetChanged();
    }

    private void showModernPopupMenu(Device device, int position, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 0, 0, "Taşı (Sürükle bırak)");
        popup.getMenu().add(0, 1, 1, "Düzenle");
        popup.getMenu().add(0, 2, 2, device.isPinned() ? "Sabitlemeyi Kaldır" : "Sabitle");
        popup.getMenu().add(0, 3, 3, "Sil");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0:
                    Snackbar.make(recyclerView, "Sürükleyerek yerini değiştirebilirsiniz", Snackbar.LENGTH_SHORT).show();
                    break;
                case 1:
                    showEditDeviceDialog(device, position);
                    break;
                case 2:
                    device.setPinned(!device.isPinned());
                    sortDevices();
                    adapter.notifyDataSetChanged();
                    saveDevices();
                    break;
                case 3:
                    fullDeviceList.remove(device);
                    deviceList.remove(position);
                    adapter.notifyItemRemoved(position);
                    saveDevices();
                    break;
            }
            return true;
        });
        popup.show();
    }

    private void showEditDeviceDialog(Device device, int position) {
        View dialogView = setupDialogView(device.getName(), device.getIp(), device.getColor());
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cihazı Düzenle")
                .setView(dialogView)
                .setPositiveButton("Güncelle", (dialog, which) -> {
                    EditText etName = dialogView.findViewById(R.id.etDeviceName);
                    EditText etIp = dialogView.findViewById(R.id.etDeviceIp);
                    Spinner spinnerType = dialogView.findViewById(R.id.spinnerDeviceType);

                    device.setName(etName.getText().toString());
                    device.setIp(etIp.getText().toString());
                    device.setType(spinnerType.getSelectedItem().toString());
                    device.setColor(selectedColor);
                    saveDevices();
                    adapter.notifyItemChanged(position);
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private View setupDialogView(String name, String ip, int color) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_device, null);
        EditText etName = dialogView.findViewById(R.id.etDeviceName);
        EditText etIp = dialogView.findViewById(R.id.etDeviceIp);
        View colorPreview = dialogView.findViewById(R.id.viewColorPreview);
        LinearLayout colorPicker = dialogView.findViewById(R.id.colorPickerContainer);

        if (name != null) etName.setText(name);
        if (ip != null) etIp.setText(ip);
        
        selectedColor = (color == 0) ? getResources().getColor(R.color.primary) : color;
        colorPreview.setBackgroundTintList(ColorStateList.valueOf(selectedColor));

        int[] colors = {
            getResources().getColor(R.color.primary),
            Color.parseColor("#2C2C2C"),
            Color.parseColor("#E74C3C"), Color.parseColor("#9C27B0"),
            Color.parseColor("#3498DB"), Color.parseColor("#00BCD4"),
            Color.parseColor("#2ECC71"), Color.parseColor("#F1C40F")
        };

        for (int c : colors) {
            View colorView = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(80, 80);
            params.setMargins(8, 0, 8, 0);
            colorView.setLayoutParams(params);
            colorView.setBackgroundResource(R.drawable.circle_bg);
            colorView.setBackgroundTintList(ColorStateList.valueOf(c));
            colorView.setOnClickListener(v -> {
                selectedColor = c;
                colorPreview.setBackgroundTintList(ColorStateList.valueOf(c));
            });
            colorPicker.addView(colorView);
        }
        return dialogView;
    }

    private void showAddDeviceDialog() {
        View dialogView = setupDialogView(null, null, 0);

        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Kaydet", (dialog, which) -> {
                    EditText etName = dialogView.findViewById(R.id.etDeviceName);
                    EditText etIp = dialogView.findViewById(R.id.etDeviceIp);
                    Spinner spinnerType = dialogView.findViewById(R.id.spinnerDeviceType);

                    String name = etName.getText().toString();
                    String ip = etIp.getText().toString();
                    String type = spinnerType.getSelectedItem().toString();

                    if (!name.isEmpty() && !ip.isEmpty()) {
                        Device device = new Device(name, ip, type, selectedColor);
                        fullDeviceList.add(device);
                        filterDevices(etSearch.getText().toString());
                        saveDevices();
                        Snackbar.make(recyclerView, "Cihaz eklendi", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void sortDevices() {
        Collections.sort(deviceList, (d1, d2) -> {
            if (d1.isPinned() && !d2.isPinned()) return -1;
            if (!d1.isPinned() && d2.isPinned()) return 1;
            return 0;
        });
    }

    private void checkDisclaimer() {
        boolean accepted = sharedPreferences.getBoolean("disclaimer_accepted", false);
        if (!accepted) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Sorumluluk Reddi")
                    .setMessage("Bu uygulama harici cihazların arayüzlerini gösterir. Oluşabilecek teknik sorunlardan kullanıcı sorumludur.")
                    .setCancelable(false)
                    .setPositiveButton("Kabul Ediyorum", (dialog, which) -> {
                        sharedPreferences.edit().putBoolean("disclaimer_accepted", true).apply();
                    })
                    .setNegativeButton("Kapat", (dialog, which) -> finish())
                    .show();
        }
    }

    private void saveDevices() {
        Set<String> deviceSet = new HashSet<>();
        for (Device d : fullDeviceList) {
            deviceSet.add(d.getName() + "|" + d.getIp() + "|" + d.getType() + "|" + d.isPinned() + "|" + d.getColor());
        }
        sharedPreferences.edit().putStringSet("devices", deviceSet).apply();
    }

    private List<Device> loadDevices() {
        List<Device> list = new ArrayList<>();
        Set<String> deviceSet = sharedPreferences.getStringSet("devices", new HashSet<>());
        for (String s : deviceSet) {
            String[] parts = s.split("\\|");
            if (parts.length >= 3) {
                boolean pinned = parts.length >= 4 && Boolean.parseBoolean(parts[3]);
                int color = (parts.length == 5) ? Integer.parseInt(parts[4]) : 0;
                list.add(new Device(parts[0], parts[1], parts[2], pinned, color));
            }
        }
        return list;
    }
}
