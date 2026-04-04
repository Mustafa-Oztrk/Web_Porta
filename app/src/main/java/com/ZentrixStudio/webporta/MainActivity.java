package com.ZentrixStudio.webporta;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView, rvDiscover;
    private DeviceAdapter adapter, discoverAdapter;
    private List<Device> deviceList, discoverList;
    private List<Device> fullDeviceList;
    private SharedPreferences sharedPreferences;
    private int selectedColor;
    private TextInputEditText etSearch;
    private TextView txtTotalCount, txtOnlineCount;
    private View layoutEmpty, layoutHome, layoutDiscover, layoutSettings;
    private ProgressBar scanProgress;
    private String currentCategory = "Hepsi";
    private final ExecutorService executorService = Executors.newFixedThreadPool(12);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_NO) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("WebPortaPrefs", Context.MODE_PRIVATE);
        
        layoutHome = findViewById(R.id.layout_home);
        layoutDiscover = findViewById(R.id.layout_discover);
        layoutSettings = findViewById(R.id.layout_settings);
        scanProgress = findViewById(R.id.scanProgress);
        
        txtTotalCount = findViewById(R.id.txtTotalCount);
        txtOnlineCount = findViewById(R.id.txtOnlineCount);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        
        checkDisclaimer();

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        fullDeviceList = loadDevices();
        deviceList = new ArrayList<>(fullDeviceList);
        adapter = new DeviceAdapter(this, deviceList, new DeviceAdapter.OnDeviceActionListener() {
            @Override
            public void onDeviceClick(Device device) {
                device.setLastAccessed(System.currentTimeMillis());
                saveDevices();
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

        rvDiscover = findViewById(R.id.rvDiscover);
        rvDiscover.setLayoutManager(new LinearLayoutManager(this));
        discoverList = new ArrayList<>();
        discoverAdapter = new DeviceAdapter(this, discoverList, new DeviceAdapter.OnDeviceActionListener() {
            @Override
            public void onDeviceClick(Device device) {
                showAddDeviceDialogFromDiscover(device.getIp());
            }
            @Override
            public void onDeviceLongClick(Device device, int position, View anchor) {}
        });
        rvDiscover.setAdapter(discoverAdapter);

        findViewById(R.id.btnScan).setOnClickListener(v -> checkPermissionAndScan());
        findViewById(R.id.btnClearAll).setOnClickListener(v -> clearAllData());

        setupBottomNav();
        setupSearchAndFilters();
        setupItemTouchHelper();

        ExtendedFloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> showAddDeviceDialog());
        
        refreshUI();
        startOnlineCheck();
    }

    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_discover) {
                if (!hasLocationPermission()) {
                    showScanPermissionDialog();
                    return false;
                }
            }
            
            layoutHome.setVisibility(View.GONE);
            layoutDiscover.setVisibility(View.GONE);
            layoutSettings.setVisibility(View.GONE);

            if (itemId == R.id.nav_home) {
                layoutHome.setVisibility(View.VISIBLE);
                refreshUI();
            } else if (itemId == R.id.nav_discover) {
                layoutDiscover.setVisibility(View.VISIBLE);
            } else if (itemId == R.id.nav_settings) {
                layoutSettings.setVisibility(View.VISIBLE);
            }
            return true;
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void showScanPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Tarama İzni")
                .setMessage("Ağdaki cihazları keşfetmek için konum izni gereklidir. Android 10 ve üzeri sürümlerde ağ detaylarını görmek için bu izin zorunludur.")
                .setPositiveButton("İzin Ver", (dialog, which) -> {
                    ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, 100);
                })
                .setNegativeButton("İptal", (dialog, which) -> {
                    bottomNav.setSelectedItemId(R.id.nav_home);
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            if (granted) {
                bottomNav.setSelectedItemId(R.id.nav_discover);
            } else {
                bottomNav.setSelectedItemId(R.id.nav_home);
                Snackbar.make(recyclerView, "Tarama özelliği için izin verilmedi.", Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private void checkPermissionAndScan() {
        if (hasLocationPermission()) {
            scanNetwork();
        } else {
            showScanPermissionDialog();
        }
    }

    private void scanNetwork() {
        discoverList.clear();
        discoverAdapter.notifyDataSetChanged();
        scanProgress.setVisibility(View.VISIBLE);
        
        executorService.execute(() -> {
            try {
                String myIp = getLocalIpAddress();
                
                if (myIp == null || myIp.isEmpty() || myIp.equals("0.0.0.0")) {
                    mainHandler.post(() -> {
                        scanProgress.setVisibility(View.GONE);
                        Snackbar.make(recyclerView, "Geçerli bir ağ bağlantısı bulunamadı!", Snackbar.LENGTH_LONG).show();
                    });
                    return;
                }

                String subnet = myIp.substring(0, myIp.lastIndexOf(".") + 1);
                
                for (int i = 1; i < 255; i++) {
                    final String testIp = subnet + i;
                    if (testIp.equals(myIp)) continue;
                    
                    executorService.execute(() -> {
                        try {
                            InetAddress address = InetAddress.getByName(testIp);
                            if (address.isReachable(500)) {
                                mainHandler.post(() -> {
                                    Device d = new Device("Keşfedilen Cihaz", "http://" + testIp, "Bilinmiyor", 0);
                                    d.setOnline(true);
                                    if (!discoverList.contains(d)) {
                                        discoverList.add(d);
                                        discoverAdapter.notifyItemInserted(discoverList.size() - 1);
                                    }
                                });
                            }
                        } catch (IOException ignored) {}
                    });
                }
                
                mainHandler.postDelayed(() -> scanProgress.setVisibility(View.GONE), 5000);
                
            } catch (Exception e) {
                mainHandler.post(() -> scanProgress.setVisibility(View.GONE));
            }
        });
    }

    private String getLocalIpAddress() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            Network network = cm.getActiveNetwork();
            if (network != null) {
                LinkProperties lp = cm.getLinkProperties(network);
                if (lp != null) {
                    for (LinkAddress la : lp.getLinkAddresses()) {
                        InetAddress addr = la.getAddress();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        }
        return null;
    }

    private void clearAllData() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Verileri Sıfırla")
                .setMessage("Tüm kayıtlı cihazlar silinecek. Emin misiniz?")
                .setPositiveButton("Evet, Sil", (dialog, which) -> {
                    fullDeviceList.clear();
                    deviceList.clear();
                    saveDevices();
                    refreshUI();
                    Snackbar.make(recyclerView, "Tüm veriler temizlendi", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void showAddDeviceDialogFromDiscover(String ip) {
        View dialogView = setupDialogView(null, ip, 0);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cihazı Kaydet")
                .setView(dialogView)
                .setPositiveButton("Kaydet", (dialog, which) -> {
                    EditText etName = dialogView.findViewById(R.id.etDeviceName);
                    Spinner spinnerType = dialogView.findViewById(R.id.spinnerDeviceType);
                    String name = etName.getText().toString();
                    if (!name.isEmpty()) {
                        Device device = new Device(name, ip, spinnerType.getSelectedItem().toString(), selectedColor);
                        fullDeviceList.add(device);
                        filterDevices();
                        saveDevices();
                        refreshUI();
                        Snackbar.make(recyclerView, "Cihaz eklendi", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void setupSearchAndFilters() {
        etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterDevices();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        ChipGroup chipGroup = findViewById(R.id.chipGroupFilters);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip chip = findViewById(checkedIds.get(0));
                currentCategory = chip.getText().toString();
                filterDevices();
            }
        });
    }

    private void filterDevices() {
        String query = etSearch.getText() != null ? etSearch.getText().toString().toLowerCase() : "";
        deviceList.clear();
        for (Device device : fullDeviceList) {
            boolean matchesQuery = device.getName().toLowerCase().contains(query) || 
                                 device.getIp().toLowerCase().contains(query);
            boolean matchesCategory = currentCategory.equals("Hepsi") || 
                                    device.getType().equals(currentCategory);
            if (matchesQuery && matchesCategory) deviceList.add(device);
        }
        sortDevices();
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void startOnlineCheck() {
        executorService.execute(() -> {
            while (!isFinishing()) {
                for (Device device : new ArrayList<>(fullDeviceList)) {
                    try {
                        String cleanIp = device.getIp().replace("http://", "").replace("https://", "").split(":")[0].split("/")[0];
                        boolean reachable = InetAddress.getByName(cleanIp).isReachable(1000);
                        device.setOnline(reachable);
                    } catch (IOException e) {
                        device.setOnline(false);
                    }
                }
                mainHandler.post(this::refreshUI);
                try { Thread.sleep(30000); } catch (InterruptedException e) { break; }
            }
        });
    }

    private void refreshUI() {
        txtTotalCount.setText(String.valueOf(fullDeviceList.size()));
        long onlineCount = 0;
        for(Device d : fullDeviceList) if(d.isOnline()) onlineCount++;
        txtOnlineCount.setText(String.valueOf(onlineCount));
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        layoutEmpty.setVisibility(deviceList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupItemTouchHelper() {
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                    Collections.swap(deviceList, fromPos, toPos);
                    adapter.notifyItemMoved(fromPos, toPos);
                    if (deviceList.size() == fullDeviceList.size() && currentCategory.equals("Hepsi")) {
                        fullDeviceList = new ArrayList<>(deviceList);
                    }
                    saveDevices();
                    return true;
                }
                return false;
            }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        });
        touchHelper.attachToRecyclerView(recyclerView);
    }

    private void showModernPopupMenu(Device device, int position, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 0, 0, "Düzenle");
        popup.getMenu().add(0, 1, 1, device.isPinned() ? "Sabitlemeyi Kaldır" : "Sabitle");
        popup.getMenu().add(0, 2, 2, "Sil");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: showEditDeviceDialog(device, position); break;
                case 1:
                    device.setPinned(!device.isPinned());
                    filterDevices();
                    saveDevices();
                    break;
                case 2:
                    fullDeviceList.remove(device);
                    filterDevices();
                    saveDevices();
                    refreshUI();
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
                    filterDevices();
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
        
        selectedColor = (color == 0) ? ContextCompat.getColor(this, R.color.primary) : color;
        colorPreview.setBackgroundTintList(ColorStateList.valueOf(selectedColor));

        int[] colors = {
            ContextCompat.getColor(this, R.color.primary),
            Color.parseColor("#2C2C2C"), Color.parseColor("#E74C3C"), 
            Color.parseColor("#9C27B0"), Color.parseColor("#3498DB"), 
            Color.parseColor("#00BCD4"), Color.parseColor("#2ECC71"), 
            Color.parseColor("#F1C40F")
        };

        for (int c : colors) {
            View colorView = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(80, 80);
            params.setMargins(12, 0, 12, 0);
            colorView.setLayoutParams(params);
            colorView.setBackgroundResource(R.drawable.circle_bg);
            colorView.setBackgroundTintList(ColorStateList.valueOf(c));
            colorView.setElevation(4f);
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
                        filterDevices();
                        saveDevices();
                        refreshUI();
                        Snackbar.make(recyclerView, "Cihaz başarıyla eklendi", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void sortDevices() {
        Collections.sort(deviceList, (d1, d2) -> {
            if (d1.isPinned() && !d2.isPinned()) return -1;
            if (!d1.isPinned() && d2.isPinned()) return 1;
            return Long.compare(d2.getLastAccessed(), d1.getLastAccessed());
        });
    }

    private void checkDisclaimer() {
        boolean accepted = sharedPreferences.getBoolean("disclaimer_accepted", false);
        if (!accepted) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Güvenlik Bildirimi")
                    .setMessage("Web Porta, cihazlarınıza erişim sağlayan bir köprüdür. Veri güvenliğiniz için cihazlarınızın şifreleme protokollerini kontrol etmeyi unutmayın.")
                    .setCancelable(false)
                    .setPositiveButton("Anladım", (dialog, which) -> {
                        sharedPreferences.edit().putBoolean("disclaimer_accepted", true).apply();
                    })
                    .setNegativeButton("Çıkış", (dialog, which) -> finish())
                    .show();
        }
    }

    private void saveDevices() {
        Set<String> deviceSet = new HashSet<>();
        for (Device d : fullDeviceList) {
            deviceSet.add(d.getName() + "|" + d.getIp() + "|" + d.getType() + "|" + d.isPinned() + "|" + d.getColor() + "|" + d.getLastAccessed());
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
                int color = (parts.length >= 5) ? Integer.parseInt(parts[4]) : 0;
                long lastAccessed = (parts.length >= 6) ? Long.parseLong(parts[5]) : 0;
                Device d = new Device(parts[0], parts[1], parts[2], pinned, color);
                d.setLastAccessed(lastAccessed);
                list.add(d);
            }
        }
        return list;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
    }
}
