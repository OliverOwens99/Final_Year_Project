package com.sentiment;

import java.util.regex.Pattern;

public class AnalyzerResult {
    private final double left;
    private final double right;
    private final String message;

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://|www\\.)\\S+|\\S+\\.(pdf|zip|exe|doc|docx|xls|xlsx|ppt|pptx)\\S*");
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^a-z\\s.,!?]");

    public static String cleanText(String text) {
        if (text == null)
            return "";

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
    
    //static factory methods for unified result creation
    public static AnalyzerResult createLexiconResult(double politicalScore, double sentimentScore,
            int politicalMatches, int sentimentMatches) {
        double score = calculateWeightedScore(politicalScore, sentimentScore, politicalMatches,
                sentimentMatches);

        double leftPercentage = Math.max(0, Math.min(100, 50 - (score * 25)));
        double rightPercentage = 100 - leftPercentage;

        String message = String.format(
                "Analysis complete: Found %d political terms and %d sentiment terms. Overall bias score: %.2f",
                politicalMatches, sentimentMatches, score);

        return new AnalyzerResult(leftPercentage, rightPercentage, message);
    }

    // For TransformerAnalyzer's direct score approach
    private static double calculateWeightedScore(double politicalScore, double sentimentScore, 
                                              int politicalMatches, int sentimentMatches) {
        if (politicalMatches > 0 || sentimentMatches > 0) {
            // Weight political terms 3x more than sentiment terms
            return (politicalScore * 3 + sentimentScore) / (politicalMatches * 3 + sentimentMatches);
        }
        return 0;
    }

    // Add this method to AnalyzerResult.java
    public static AnalyzerResult createBertResult(double bias) {
        // Convert bias score to percentages (consistent with your current implementation)
        double leftPercentage = Math.max(0, Math.min(100, 50 - (bias * 25)));
        double rightPercentage = 100 - leftPercentage;
        
        String message = String.format(
            "Political bias analysis: %.2f (negative=left, positive=right)", 
            bias);
        
        return new AnalyzerResult(leftPercentage, rightPercentage, message);
    }
        
    public AnalyzerResult(double left, double right, String message) {
        this.left = left;
        this.right = right;
        this.message = message;
    }




    @Override
    public String toString() {
        // Use the escapeJsonString method that's already defined but not used
        return String.format("{\"left\": %.1f, \"right\": %.1f, \"message\": \"%s\"}", left, right,
                escapeJsonString(message));
    }

    


    private String escapeJsonString(String input) {
        if (input == null)
            return "";
        // IMPORTANT: The backslash replacement must come first!
        return input.replace("\\", "\\\\") // This must be first or you'll double-escape
                .replace("\"", "\\\"").replace("\b", "\\b").replace("\f", "\\f")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    public String getMessage() {
        return this.message;
    }
}
