package com.mikeyphw.hooklab.termux;

import java.nio.charset.StandardCharsets;

/** Local, Android-independent validation; never executes or rewrites a command. */
public final class TermuxCommandPolicy {
    public static final String HOME = "/data/data/com.termux/files/home";
    public static final String ZSH = "/data/data/com.termux/files/usr/bin/zsh";
    public static final int MAX_BYTES = 48 * 1024;

    private TermuxCommandPolicy() { }

    public static String checkedCommand(String input) {
        if (input == null || input.trim().isEmpty())
            throw new IllegalArgumentException("Command is empty");
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\t')
                throw new IllegalArgumentException("Unsupported control character in command");
            if (Character.getType(c) == Character.FORMAT)
                throw new IllegalArgumentException("Invisible formatting characters are not supported");
        }
        if (input.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Command exceeds 48 KiB UTF-8 limit");
        return input; // Preserve whitespace, quotes, multiple lines and non-ASCII text exactly.
    }

    public static String checkedWorkingDirectory(String input) {
        if (input == null || input.trim().isEmpty()) return HOME;
        String value = input.trim();
        if (value.equals("~")) return HOME;
        if (value.startsWith("~/")) value = HOME + value.substring(1);
        if (!(value.equals(HOME) || value.startsWith(HOME + "/")))
            throw new IllegalArgumentException("Working directory must be under Termux home");
        if (value.length() > 1024)
            throw new IllegalArgumentException("Working directory is too long");
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i)))
                throw new IllegalArgumentException("Working directory contains control characters");
        }
        String suffix = value.substring(HOME.length());
        if (suffix.startsWith("/")) suffix = suffix.substring(1);
        if (!suffix.isEmpty()) {
            for (String segment : suffix.split("/", -1)) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
                    throw new IllegalArgumentException("Working directory contains an invalid segment");
            }
        }
        return value;
    }
}
