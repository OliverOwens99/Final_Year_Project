import java.util.concurrent.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;

public class LexiconAnalyzer {
    private static final Map<String, Double> LEXICON = new ConcurrentHashMap<>();
    private static final int CHUNK_SIZE = 1000;

    static {
        try {
            Files.lines(Paths.get("vader_lexicon.txt"))
                .map(line -> line.split("\t"))
                .filter(parts -> parts.length >= 2)
                .forEach(parts -> {
                    try {
                        LEXICON.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
                    } catch (NumberFormatException e) {
                        System.err.println("Error parsing score for: " + parts[0]);
                    }
                });
        } catch (IOException e) {
            System.err.println("Error loading lexicon: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in);
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            String text = scanner.nextLine();
            List<String> words = Arrays.asList(text.toLowerCase().split("\\s+"));
            List<List<String>> chunks = splitIntoChunks(words, CHUNK_SIZE);
            
            List<Future<ChunkResult>> futures = new ArrayList<>();
            for (List<String> chunk : chunks) {
                futures.add(executor.submit(() -> processChunk(chunk)));
            }
            
            double totalScore = 0;
            int totalMatches = 0;
            
            for (Future<ChunkResult> future : futures) {
                ChunkResult result = future.get(5, TimeUnit.SECONDS);
                totalScore += result.score;
                totalMatches += result.matches;
            }

            double normalizedScore = totalMatches > 0 ? totalScore / totalMatches : 0;
            double leftPercentage = Math.max(0, Math.min(100, 50 - (normalizedScore * 50)));
            
            AnalyzerResult result = new AnalyzerResult(
                leftPercentage,
                100 - leftPercentage,
                String.format("Analyzed %d terms", totalMatches)
            );
            
            System.out.println(result.toString());
            
        } catch (Exception e) {
            System.out.println(String.format("{\"error\": \"%s\"}", e.getMessage()));
        }
    }

    private static List<List<String>> splitIntoChunks(List<String> words, int chunkSize) {
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < words.size(); i += chunkSize) {
            chunks.add(words.subList(i, Math.min(words.size(), i + chunkSize)));
        }
        return chunks;
    }

    private static ChunkResult processChunk(List<String> words) {
        double score = 0;
        int matches = 0;
        for (String word : words) {
            if (LEXICON.containsKey(word)) {
                score += LEXICON.get(word);
                matches++;
            }
        }
        return new ChunkResult(score, matches);
    }

    private static class ChunkResult {
        final double score;
        final int matches;
        
        ChunkResult(double score, int matches) {
            this.score = score;
            this.matches = matches;
        }
    }
}