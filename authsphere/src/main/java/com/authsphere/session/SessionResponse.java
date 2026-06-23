package com.authsphere.session;

import java.time.LocalDateTime;
import java.util.UUID;

public class SessionResponse {
    private UUID id;
    private String deviceName;
    private String browser;
    private String os;
    private String ipAddress;
    private LocalDateTime loginTime;
    private LocalDateTime lastActivity;
    private boolean current;

    public SessionResponse(UUID id, String deviceName, String browser, String os,
                           String ipAddress, LocalDateTime loginTime,
                           LocalDateTime lastActivity, boolean current) {
        this.id = id;
        this.deviceName = deviceName;
        this.browser = browser;
        this.os = os;
        this.ipAddress = ipAddress;
        this.loginTime = loginTime;
        this.lastActivity = lastActivity;
        this.current = current;
    }

    public UUID getId() { return id; }
    public String getDeviceName() { return deviceName; }
    public String getBrowser() { return browser; }
    public String getOs() { return os; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getLoginTime() { return loginTime; }
    public LocalDateTime getLastActivity() { return lastActivity; }
    public boolean isCurrent() { return current; }
}
