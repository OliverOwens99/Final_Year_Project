import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
            // Load VADER lexicon
            Path vaderPath = Paths.get("resources", "vader_lexicon.txt");
            loadVaderLexicon(vaderPath);
            
            // Load political bias lexicon
            Path politicalPath = Paths.get("resources", "politicalBias.txt");
            loadPoliticalLexicon(politicalPath);
            
            System.out.println("Loaded " + VADER_LEXICON.size() + " VADER terms");
            System.out.println("Loaded " + POLITICAL_LEXICON.size() + " political terms");
            
        } catch (IOException e) {
            System.err.println("Error loading lexicons: " + e.getMessage());
            
        }
    }

    private static void loadVaderLexicon(Path path) throws IOException {
        Files.lines(path)
            .filter(line -> !line.trim().isEmpty() && !line.startsWith(";"))
            .map(line -> line.split("\t"))
            .filter(parts -> parts.length >= 2)
            .forEach(parts -> {
                try {
                    VADER_LEXICON.put(
                        parts[0].trim().toLowerCase(),
                        Double.parseDouble(parts[1].trim())
                    );
                } catch (NumberFormatException e) {
                    System.err.println("Invalid VADER score for: " + parts[0]);
                }
            });
    }

    private static void loadPoliticalLexicon(Path path) throws IOException {
        Files.lines(path)
            .filter(line -> !line.trim().isEmpty())
            .map(line -> line.split("\t"))
            .filter(parts -> parts.length >= 2)
            .forEach(parts -> {
                try {
                    POLITICAL_LEXICON.put(
                        parts[0].trim().toLowerCase(),
                        Double.parseDouble(parts[1].trim())
                    );
                } catch (NumberFormatException e) {
                    System.err.println("Invalid political score for: " + parts[0]);
                }
            });
    }

    private static AnalyzerResult analyzeText(String text) {
        String[] words = text.toLowerCase().split("\\s+");
        double vaderScore = 0, politicalScore = 0;
        int vaderMatches = 0, politicalMatches = 0;

        for (String word : words) {
            if (VADER_LEXICON.containsKey(word)) {
                vaderScore += VADER_LEXICON.get(word);
                vaderMatches++;
            }
            if (POLITICAL_LEXICON.containsKey(word)) {
                politicalScore += POLITICAL_LEXICON.get(word);
                politicalMatches++;
            }
        }

        double weightedScore = calculateWeightedScore(
            vaderScore, vaderMatches,
            politicalScore, politicalMatches
        );

        double leftPercentage = Math.max(0, Math.min(100, 50 - (weightedScore * 25)));
        
        return new AnalyzerResult(
            leftPercentage,
            100 - leftPercentage,
            String.format("Found %d VADER terms, %d political terms", 
                vaderMatches, politicalMatches)
        );
    }

    private static double calculateWeightedScore(
            double vaderScore, int vaderMatches,
            double politicalScore, int politicalMatches) {
        if (vaderMatches + politicalMatches == 0) return 0;

        double normalizedVader = vaderMatches > 0 ? vaderScore / vaderMatches : 0;
        double normalizedPolitical = politicalMatches > 0 ? politicalScore / politicalMatches : 0;
        
        // Weight political terms more heavily (70-30 split)
        return (normalizedPolitical * 0.7) + (normalizedVader * 0.3);
    }
}