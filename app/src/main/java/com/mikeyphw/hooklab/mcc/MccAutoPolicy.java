package com.mikeyphw.hooklab.mcc;

/** Pure value checks for P10: never refill until a persisted exact one-byte zero is observed. */
public final class MccAutoPolicy {
    private MccAutoPolicy() { }
    public static boolean allowedName(String name) {
        if (name == null || !name.endsWith("-DiamondBalance")) return false;
        String prefix = name.substring(0, name.length()-"-DiamondBalance".length());
        return prefix.length() >= 8 && prefix.length() <= 128 && prefix.matches("[A-Za-z0-9_-]+");
    }
    public static boolean exactZero(byte[] data) {
        return data != null && data.length == 1 && data[0] == (byte)'0';
    }
}
