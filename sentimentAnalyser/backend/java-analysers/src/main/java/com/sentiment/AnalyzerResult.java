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

    // For BertPoliticalAnalyser's bias score approach
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

    // Add to AnalyzerResult.java
    public static AnalyzerResult createTransformerResult(double score, String explanation) {
        // Detect placeholder text in the explanation
        if (explanation.contains("[") && explanation.contains("]") || 
            explanation.trim().isEmpty() ||
            explanation.equals("null")) {
            // Replace with a more useful message based on score
            if (score < -0.3) {
                explanation = "The text shows significant left-leaning political bias (score: " + score + ")";
            } else if (score < 0) {
                explanation = "The text shows slight left-leaning political bias (score: " + score + ")";
            } else if (score > 0.3) {
                explanation = "The text shows significant right-leaning political bias (score: " + score + ")";
            } else if (score > 0) {
                explanation = "The text shows slight right-leaning political bias (score: " + score + ")";
            } else {
                explanation = "The text appears politically neutral (score: " + score + ")";
            }
        }
    
        // Calculate percentages
        double leftPercentage = Math.max(0, Math.min(100, 50 - (score * 50)));
        double rightPercentage = 100 - leftPercentage;
        
        return new AnalyzerResult(leftPercentage, rightPercentage, explanation);
    }



    @Override
    public String toString() {
        return String.format(
            "{\"left\": %.1f, \"right\": %.1f, \"message\": \"%s\", \"explanation\": \"%s\"}", 
            left, right, 
            escapeJsonString(message), 
            escapeJsonString(message)  // Include both fields with same content
        );
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
