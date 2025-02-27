package com.sentiment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class LexiconAnalyzer {
    private static final Map<String, Double> VADER_LEXICON = new HashMap<>();
    private static final Map<String, Double> POLITICAL_LEXICON = new HashMap<>();
    
    static {
        try {
            // Load resources from classpath
            InputStream vaderStream = LexiconAnalyzer.class.getResourceAsStream("/vader_lexicon.txt");
            if (vaderStream == null) {
                throw new IOException("VADER lexicon not found in resources");
            }
            
            InputStream politicalStream = LexiconAnalyzer.class.getResourceAsStream("/politicalBias.txt");
            if (politicalStream == null) {
                throw new IOException("Political lexicon not found in resources");
            }
            
            // Debug path information
            System.out.println("Working Directory = " + Paths.get("").toAbsolutePath());
            
            loadVaderLexicon(vaderStream);
            loadPoliticalLexicon(politicalStream);
            
        } catch (IOException e) {
            System.err.println("Error loading lexicons: " + e.getMessage());
            initializeFallbackLexicons();
        }
    }

    private static void loadVaderLexicon(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith(";")) continue;
                
                String[] parts = line.split("\t");
                if (parts.length >= 2) {
                    try {
                        VADER_LEXICON.put(
                            parts[0].trim().toLowerCase(),
                            Double.parseDouble(parts[1].trim())
                        );
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid VADER score for: " + parts[0]);
                    }
                }
            }
        }
    }

    private static void loadPoliticalLexicon(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split("\t");
                if (parts.length >= 2) {
                    try {
                        POLITICAL_LEXICON.put(
                            parts[0].trim().toLowerCase(),
                            Double.parseDouble(parts[1].trim())
                        );
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid political score for: " + parts[0]);
                    }
                }
            }
        }
    }

    private static void initializeFallbackLexicons() {
        // Fallback political bias terms
        POLITICAL_LEXICON.put("liberal", -0.8);
        POLITICAL_LEXICON.put("conservative", 0.8);
        POLITICAL_LEXICON.put("democrat", -0.7);
        POLITICAL_LEXICON.put("republican", 0.7);
        POLITICAL_LEXICON.put("left", -0.6);
        POLITICAL_LEXICON.put("right", 0.6);
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            String text = scanner.nextLine();
            AnalyzerResult result = analyzeText(text);
            System.out.println(result.toString());
        } catch (Exception e) {
            System.out.println(String.format(
                "{\"error\": \"%s\", \"left\": 50, \"right\": 50}", 
                e.getMessage()
            ));
        }
    }

    private static AnalyzerResult analyzeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new AnalyzerResult(50, 50, "No text to analyze");
        }

        String[] words = text.toLowerCase().split("\\s+");
        double vaderScore = 0;
        double politicalScore = 0;
        int vaderMatches = 0;
        int politicalMatches = 0;

        for (String word : words) {
            // Check political lexicon first (higher priority)
            if (POLITICAL_LEXICON.containsKey(word)) {
                politicalScore += POLITICAL_LEXICON.get(word);
                politicalMatches++;
            }
            // Check VADER lexicon for general sentiment
            else if (VADER_LEXICON.containsKey(word)) {
                vaderScore += VADER_LEXICON.get(word);
                vaderMatches++;
            }
        }

        // Calculate weighted scores
        double totalScore = 0;
        if (politicalMatches > 0 || vaderMatches > 0) {
            // Weight political terms more heavily
            totalScore = (politicalScore * 2 + vaderScore) / 
                        (politicalMatches * 2 + vaderMatches);
        }

        // Convert to percentages (normalize to 0-100 range)
        double leftPercentage = Math.max(0, Math.min(100, 50 - (totalScore * 25)));
        double rightPercentage = 100 - leftPercentage;

        String message = String.format(
            "Analysis complete: Found %d political terms and %d sentiment terms. Overall bias score: %.2f",
            politicalMatches,
            vaderMatches,
            totalScore
        );

        return new AnalyzerResult(leftPercentage, rightPercentage, message);
    }
}