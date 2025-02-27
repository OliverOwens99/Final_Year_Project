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
        return String.format("{\"left\": %.2f, \"right\": %.2f, \"message\": \"%s\"}", 
            left, right, escapeJsonString(message));
    }
    
    private String escapeJsonString(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"")
                   .replace("\\", "\\\\")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}