package com.sentiment;

import AnalyzerResult;
import dev.langchain4j.model.huggingface.HuggingFaceModel;
import dev.langchain4j.model.output.Response;

public class TransformerAnalyzer {
    private static final HuggingFaceModel model;
    private static final String PROMPT_TEMPLATE = 
        "Analyze the political bias in this text. Rate it on a scale from -1 (left) to 1 (right): %s";
    
    static {
        model = HuggingFaceModel.builder()
            .apiKey(System.getenv("HUGGINGFACE_API_KEY"))
            .modelId("distilbert-base-uncased-finetuned-sst-2-english")
            .build();
    }

    public static AnalyzerResult analyzeText(String text) {
        try {
            // Clean text first
            text = AnalyzerResult.cleanText(text);
            
            // Create prompt
            String prompt = String.format(PROMPT_TEMPLATE, text);
            
            // Get model response
            Response<String> response = model.generate(prompt);
            
            // Parse score from response
            double score = parseScore(response.content());
            
            // Convert to percentages
            double leftPercentage = Math.max(0, Math.min(100, 50 - (score * 50)));
            double rightPercentage = 100 - leftPercentage;
            
            return new AnalyzerResult(
                leftPercentage,
                rightPercentage,
                "Transformer analysis complete"
            );
            
        } catch (Exception e) {
            System.err.println("Transformer analysis error: " + e.getMessage());
            return new AnalyzerResult(50, 50, "Analysis failed");
        }
    }

    private static double parseScore(String response) {
        try {
            // Extract numerical score from response
            String score = response.replaceAll("[^-0-9.]", "");
            return Double.parseDouble(score);
        } catch (Exception e) {
            return 0.0;
        }
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
}
