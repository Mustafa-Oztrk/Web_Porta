package com.ZentrixStudio.webporta;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private final Context context;
    private final List<Device> deviceList;
    private final OnDeviceActionListener listener;

    public interface OnDeviceActionListener {
        void onDeviceClick(Device device);
        void onDeviceLongClick(Device device, int position, View anchor);
    }

    public DeviceAdapter(Context context, List<Device> deviceList, OnDeviceActionListener listener) {
        this.context = context;
        this.deviceList = deviceList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Device device = deviceList.get(position);
        holder.txtName.setText(device.getName());
        holder.txtIp.setText(device.getIp());
        
        holder.imgPin.setVisibility(device.isPinned() ? View.VISIBLE : View.GONE);

        int color = device.getColor();
        if (color == 0) color = ContextCompat.getColor(context, R.color.primary);

        int iconRes;
        if (device.getType().contains("3D Printer")) {
            iconRes = android.R.drawable.ic_menu_preferences;
        } else if (device.getType().contains("ESP32")) {
            iconRes = android.R.drawable.ic_menu_day;
        } else if (device.getType().contains("IoT")) {
            iconRes = android.R.drawable.ic_lock_idle_low_battery;
        } else {
            iconRes = android.R.drawable.ic_menu_info_details;
        }

        holder.imgType.setImageResource(iconRes);
        holder.imgType.setImageTintList(ColorStateList.valueOf(color));
        holder.iconContainer.setBackgroundTintList(ColorStateList.valueOf(color));

        holder.itemView.setOnClickListener(v -> listener.onDeviceClick(device));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onDeviceLongClick(device, holder.getAdapterPosition(), v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(deviceList, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtIp;
        ImageView imgType, imgPin;
        View iconContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtDeviceName);
            txtIp = itemView.findViewById(R.id.txtDeviceIp);
            imgType = itemView.findViewById(R.id.imgDeviceType);
            iconContainer = itemView.findViewById(R.id.iconContainer);
            imgPin = itemView.findViewById(R.id.imgPin);
        }
    }
}
