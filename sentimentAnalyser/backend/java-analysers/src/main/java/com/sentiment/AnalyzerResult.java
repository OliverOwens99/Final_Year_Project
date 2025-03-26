package com.sentiment;

import java.util.regex.Pattern;

public class AnalyzerResult {
    private final double left;
    private final double right;
    private final String message;
    
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+|www\\.\\S+");
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^a-z\\s.,!?]");

    public static String cleanText(String text) {
        if (text == null) return "";
        
        // Convert to lowercase
        text = text.toLowerCase();
        
        // Remove URLs
        text = URL_PATTERN.matcher(text).replaceAll("");
        
        // Remove special characters but keep sentence structure
        text = SPECIAL_CHARS.matcher(text).replaceAll(" ");
        
        // Normalize whitespace
        text = text.replaceAll("\\s+", " ").trim();
        
        return text;
    }

    public AnalyzerResult(double left, double right, String message) {
        this.left = left;
        this.right = right;
        this.message = message;
    }

    @Override
    public String toString() {
        // Use the escapeJsonString method that's already defined but not used
        return String.format("{\"left\": %.1f, \"right\": %.1f, \"message\": \"%s\"}",
                left, right, escapeJsonString(message));
    }
    
    private String escapeJsonString(String input) {
        if (input == null) return "";
        // IMPORTANT: The backslash replacement must come first!
        return input.replace("\\", "\\\\")  // This must be first or you'll double-escape
                   .replace("\"", "\\\"")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}