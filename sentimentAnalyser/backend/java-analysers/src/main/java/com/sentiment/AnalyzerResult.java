package com.sentiment;

import java.util.regex.Pattern;

/**
 * A container class for political bias analysis results.
 * This class represents the output of various political bias analysers
 * and provides a standardised format for communicating results.
 * <p>
 * The result includes left/right political leaning percentages, an overall
 * message about the analysis, and a detailed explanation of the bias detected.
 * It also provides static factory methods for creating consistent results
 * from different analyser types.
 */
public class AnalyzerResult {
    /** Percentage representing left-leaning political bias (0-100) */
    private final double left;
    
    /** Percentage representing right-leaning political bias (0-100) */
    private final double right;
    
    /** Brief summary of the analysis result */
    private final String message;
    
    /** Detailed explanation of the political bias analysis */
    private final String explanation;

    /** Pattern for detecting and removing URLs and file references during text cleaning */
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://|www\\.)\\S+|\\S+\\.(pdf|zip|exe|doc|docx|xls|xlsx|ppt|pptx)\\S*");
    
    /** Pattern for detecting special characters to be removed during text cleaning */
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^a-z\\s.,!?]");

    /**
     * Constructs an analysis result with the message also used as the explanation.
     * 
     * @param left Percentage representing left-leaning bias (0-100)
     * @param right Percentage representing right-leaning bias (0-100)
     * @param message Brief summary of the analysis result
     */
    public AnalyzerResult(double left, double right, String message) {
        this.left = left;
        this.right = right;
        this.message = message;
        this.explanation = message; // Use message as default explanation
    }

    /**
     * Constructs an analysis result with separate message and detailed explanation.
     * 
     * @param left Percentage representing left-leaning bias (0-100)
     * @param right Percentage representing right-leaning bias (0-100)
     * @param message Brief summary of the analysis result
     * @param explanation Detailed explanation of the political bias analysis
     */
    public AnalyzerResult(double left, double right, String message, String explanation) {
        this.left = left;
        this.right = right;
        this.message = message;
        this.explanation = explanation;
    }

    /**
     * Cleans and normalises input text for consistent analysis.
     * Performs several transformations:
     * - Converts text to lowercase
     * - Removes URLs and file references
     * - Removes special characters while preserving sentence structure
     * - Normalises whitespace
     * 
     * @param text The text to clean
     * @return Cleaned and normalised text
     */
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
    
    /**
     * Factory method for creating results from Lexicon-based analysis.
     * Calculates weighted scores based on political and sentiment matches.
     * 
     * @param politicalScore Sum of political term scores
     * @param sentimentScore Sum of sentiment term scores
     * @param politicalMatches Number of political terms matched
     * @param sentimentMatches Number of sentiment terms matched
     * @return An AnalyzerResult with appropriate percentages and message
     */
    public static AnalyzerResult createLexiconResult(double politicalScore, double sentimentScore,
            int politicalMatches, int sentimentMatches) {
        double score = calculateWeightedScore(politicalScore, sentimentScore, politicalMatches,
                sentimentMatches);

        double leftPercentage = Math.max(0, Math.min(100, 50 - (score * 50)));
        double rightPercentage = 100 - leftPercentage;

        String message = String.format(
                "Analysis complete: Found %d political terms and %d sentiment terms. Overall bias score: %.2f",
                politicalMatches, sentimentMatches, score);

        return new AnalyzerResult(leftPercentage, rightPercentage, message);
    }

    /**
     * Calculates a weighted political bias score from political and sentiment scores.
     * Political terms are weighted 3x more heavily than sentiment terms.
     * 
     * @param politicalScore Sum of political term scores
     * @param sentimentScore Sum of sentiment term scores
     * @param politicalMatches Number of political terms matched
     * @param sentimentMatches Number of sentiment terms matched
     * @return The weighted score representing overall bias
     */
    private static double calculateWeightedScore(double politicalScore, double sentimentScore, 
                                              int politicalMatches, int sentimentMatches) {
        if (politicalMatches > 0 || sentimentMatches > 0) {
            // Weight political terms 3x more than sentiment terms
            return (politicalScore * 3 + sentimentScore) / (politicalMatches * 3 + sentimentMatches);
        }
        return 0;
    }

    /**
     * Factory method for creating results from the BERT analyser.
     * Converts a raw bias score to left/right percentages.
     * 
     * @param bias The raw bias score from BERT analysis (-1 to 1)
     * @return An AnalyzerResult with appropriate percentages and message
     */
    public static AnalyzerResult createBertResult(double bias) {
        // Convert bias score to percentages (consistent with your current implementation)
        double leftPercentage = Math.max(0, Math.min(100, 50 - (bias * 50)));
        double rightPercentage = 100 - leftPercentage;
        
        String message = String.format(
            "Political bias analysis: %.2f (negative=left, positive=right)", 
            bias);
        
        return new AnalyzerResult(leftPercentage, rightPercentage, message);
    }

    /**
     * Factory method for creating results from Transformer-based analysis.
     * Converts a raw score and explanation into standardised result format.
     * 
     * @param score The raw bias score from transformer analysis (-1 to 1)
     * @param explanation The detailed explanation from the language model
     * @return An AnalyzerResult with appropriate percentages and explanation
     */
    public static AnalyzerResult createTransformerResult(double score, String explanation) {
        System.err.println("Creating result with score: " + score);
        
        // Check for placeholder text
        if (explanation == null || 
            explanation.trim().isEmpty() ||
            explanation.equals("[detailed political bias analysis explaining WHY the text leans left or right]")) {
            
            explanation = "The model did not provide a detailed analysis. Score: " + score;
        }
    
        // Calculate percentages based on score
        double leftPercentage = 50 - (score * 50); // Convert [-1,1] to [100,0]
        double rightPercentage = 100 - leftPercentage;
        
        // Ensure percentages are within valid range
        leftPercentage = Math.max(0, Math.min(100, leftPercentage));
        rightPercentage = Math.max(0, Math.min(100, rightPercentage));
        
        String message = String.format("Political bias analysis score: %.2f (negative=left, positive=right)", score);
        
        System.err.println("Calculated left: " + leftPercentage + ", right: " + rightPercentage);
        
        return new AnalyzerResult(leftPercentage, rightPercentage, message, explanation);
    }

    /**
     * Converts the result to a JSON string representation.
     * Includes left/right percentages, message, and explanation.
     * 
     * @return JSON string representing the analysis result
     */
    @Override
    public String toString() {
        return String.format(
            "{\"left\": %.1f, \"right\": %.1f, \"message\": \"%s\", \"explanation\": \"%s\"}", 
            left, right, 
            escapeJsonString(message),
            escapeJsonString(explanation)
        );
    }

    /**
     * Escapes special characters in a string for JSON encoding.
     * 
     * @param input The string to escape for JSON
     * @return Properly escaped JSON string
     */
    private String escapeJsonString(String input) {
        if (input == null)
            return "";
        // IMPORTANT: The backslash replacement must come first!
        return input.replace("\\", "\\\\") // This must be first or you'll double-escape
                .replace("\"", "\\\"").replace("\b", "\\b").replace("\f", "\\f")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /**
     * Returns the brief message summarising the analysis result.
     * 
     * @return The analysis message
     */
    public String getMessage() {
        return this.message;
    }
    
    /**
     * Returns the detailed explanation of the political bias analysis.
     * 
     * @return The detailed analysis explanation
     */
    public String getExplanation() {
        return this.explanation;
    }
}