package com.sentiment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LexiconAnalyzer {
    private static final Map<String, Double> VADER_LEXICON = new HashMap<>();
    private static final Map<String, Double> POLITICAL_LEXICON = new HashMap<>();
    private static final int CHUNK_SIZE = 5000; // Process text in chunks of this many characters
    
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
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith(";")) continue;
                
                String[] parts = line.split("\t");
                if (parts.length >= 2) {
                    try {
                        String term = parts[0].trim().toLowerCase();
                        double score = Double.parseDouble(parts[1].trim());
                        VADER_LEXICON.put(term, score);
                        count++;
                        // Print every 1000th entry for verification
                        if (count % 1000 == 0) {
                            System.err.println("Loaded " + count + " VADER terms. Sample: " + term + " = " + score);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid VADER score for: " + parts[0]);
                    }
                }
            }
            System.err.println("Total VADER lexicon entries: " + VADER_LEXICON.size());
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
                e.getMessage().replace("\"", "\\\"")
            ));
        }
    }

    private static AnalyzerResult analyzeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new AnalyzerResult(50, 50, "No text to analyze");
        }
    
        // Clean text
        text = AnalyzerResult.cleanText(text);
        
        // For short texts, don't use threading
        if (text.length() < CHUNK_SIZE) {
            return analyzeChunk(text);
        }
        
        try {
            // Split text into manageable chunks
            List<String> chunks = new ArrayList<>();
            for (int i = 0; i < text.length(); i += CHUNK_SIZE) {
                chunks.add(text.substring(i, Math.min(i + CHUNK_SIZE, text.length())));
            }
            
            // Process chunks in parallel using virtual threads
            double totalPoliticalScore = 0;
            double totalVaderScore = 0;
            int totalPoliticalMatches = 0;
            int totalVaderMatches = 0;
            
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                // Submit all tasks and collect results
                var results = chunks.stream()
                    .map(chunk -> executor.submit(() -> analyzeChunk(chunk)))
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            System.err.println("Error processing chunk: " + e.getMessage());
                            return new AnalyzerResult(50, 50, "Error");
                        }
                    })
                    .collect(Collectors.toList());
                    
                // Combine results
                for (AnalyzerResult result : results) {
                    // Extract scores from result
                    double[] scores = extractScores(result);
                    totalPoliticalScore += scores[0];
                    totalVaderScore += scores[1];
                    totalPoliticalMatches += (int)scores[2];
                    totalVaderMatches += (int)scores[3];
                }
            }
            
            // Calculate final weighted score
            double finalScore = calculateWeightedScore(
                totalPoliticalScore, totalVaderScore, 
                totalPoliticalMatches, totalVaderMatches
            );
            
            return createResult(finalScore, totalPoliticalMatches, totalVaderMatches);
        } catch (Exception e) {
            System.err.println("Error in virtual thread processing: " + e.getMessage());
            return analyzeChunk(text);
        }
    }
    
    // Extract score values from an AnalyzerResult for aggregation
    private static double[] extractScores(AnalyzerResult result) {
        // The message format: "Analysis complete: Found X political terms and Y sentiment terms. Overall bias score: Z"
        String msg = result.getMessage();
        
        // Parse message for scores (simplified approach)
        int politicalCount = 0;
        int vaderCount = 0;
        double politicalScore = 0;
        double vaderScore = 0;
        
        // This is a hack, but works for our format
        try {
            String[] parts = msg.split("Found ")[1].split(" political terms and ");
            politicalCount = Integer.parseInt(parts[0]);
            
            parts = parts[1].split(" sentiment terms");
            vaderCount = Integer.parseInt(parts[0]);
            
            // Extract bias score
            double bias = Double.parseDouble(msg.split("Overall bias score: ")[1]);
            
            // Work backward from the bias score to calculate individual contributions
            // (This is an approximation)
            double divisor = politicalCount * 3 + vaderCount;
            if (divisor > 0) {
                politicalScore = bias * (politicalCount * 3) / divisor;
                vaderScore = bias * vaderCount / divisor;
            }
        } catch (Exception e) {
            // If parsing fails, return zeros
            System.err.println("Error parsing scores: " + e.getMessage());
        }
        
        return new double[] { politicalScore, vaderScore, politicalCount, vaderCount };
    }
    
    // Unified analyze method for single chunks
    private static AnalyzerResult analyzeChunk(String chunk) {
        // Better tokenization
        String[] words = chunk.replaceAll("[\\.,;:!?\\(\\)\\[\\]{}'\"]", " ")
                             .toLowerCase()
                             .split("\\s+");
        
        double vaderScore = 0;
        double politicalScore = 0;
        int vaderMatches = 0;
        int politicalMatches = 0;
        
        // Process words (including hyphenated words and bigrams)
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) continue;
            
            // Check single words
            if (POLITICAL_LEXICON.containsKey(word)) {
                politicalScore += POLITICAL_LEXICON.get(word);
                politicalMatches++;
            }
            else if (VADER_LEXICON.containsKey(word)) {
                vaderScore += VADER_LEXICON.get(word);
                vaderMatches++;
            }
            
            // Check hyphenated words
            if (word.contains("-")) {
                String unhyphenated = word.replace("-", " ");
                if (POLITICAL_LEXICON.containsKey(unhyphenated)) {
                    politicalScore += POLITICAL_LEXICON.get(unhyphenated);
                    politicalMatches++;
                }
            }
            
            // Check bigrams
            if (i < words.length - 1) {
                String bigram = word + " " + words[i+1];
                if (POLITICAL_LEXICON.containsKey(bigram)) {
                    politicalScore += POLITICAL_LEXICON.get(bigram);
                    politicalMatches++;
                }
            }
        }
        
        // Calculate weighted score
        return AnalyzerResult.createLexiconResult(politicalScore, vaderScore, politicalMatches, vaderMatches);
    }
    
    // Calculate weighted score
    private static double calculateWeightedScore(double politicalScore, double vaderScore, 
                                               int politicalMatches, int vaderMatches) {
        if (politicalMatches > 0 || vaderMatches > 0) {
            // Weight political terms 3x more than sentiment terms
            return (politicalScore * 3 + vaderScore) / (politicalMatches * 3 + vaderMatches);
        }
        return 0;
    }
    
    // Create result with proper percentages
    private static AnalyzerResult createResult(double score, int politicalMatches, int vaderMatches) {
        // Convert to percentages
        double leftPercentage = Math.max(0, Math.min(100, 50 - (score * 25)));
        double rightPercentage = 100 - leftPercentage;
        
        String message = String.format(
            "Analysis complete: Found %d political terms and %d sentiment terms. Overall bias score: %.2f",
            politicalMatches,
            vaderMatches,
            score
        );
        
        return new AnalyzerResult(leftPercentage, rightPercentage, message);
    }
}