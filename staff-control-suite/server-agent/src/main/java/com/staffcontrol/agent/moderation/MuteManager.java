package com.staffcontrol.agent.moderation;

import java.util.concurrent.ConcurrentHashMap;

public class MuteManager {

    // playerName (lowercase) -> expiresAt millis (0 = permanent)
    private final ConcurrentHashMap<String, Long> mutes = new ConcurrentHashMap<>();

    public void addMute(String playerName, long expiresAt) {
        mutes.put(playerName.toLowerCase(), expiresAt);
    }

    public void removeMute(String playerName) {
        mutes.remove(playerName.toLowerCase());
    }

    public boolean isMuted(String playerName) {
        Long expiresAt = mutes.get(playerName.toLowerCase());
        if (expiresAt == null) return false;
        if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
            mutes.remove(playerName.toLowerCase());
            return false;
        }
        return true;
    }

    public long getMuteExpiry(String playerName) {
        return mutes.getOrDefault(playerName.toLowerCase(), 0L);
    }
}
