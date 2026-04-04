package com.ZentrixStudio.webporta;

import java.io.Serializable;
import java.util.Date;

public class Device implements Serializable {
    private String id;
    private String name;
    private String ip;
    private String type;
    private boolean pinned;
    private int color;
    private long lastAccessed;
    private String note;

    public Device(String name, String ip, String type, int color) {
        this(name, ip, type, false, color);
    }

    public Device(String name, String ip, String type, boolean pinned, int color) {
        this.id = String.valueOf(System.currentTimeMillis());
        this.name = name;
        this.ip = ip;
        this.type = type;
        this.pinned = pinned;
        this.color = color;
        this.lastAccessed = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public long getLastAccessed() { return lastAccessed; }
    public void setLastAccessed(long lastAccessed) { this.lastAccessed = lastAccessed; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
