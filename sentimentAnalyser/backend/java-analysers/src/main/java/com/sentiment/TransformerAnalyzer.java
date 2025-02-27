package com.sentiment;

import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

public class TransformerAnalyzer {
    // Model factory - allows easily swapping between different models
    private static final Map<String, Supplier<ChatLanguageModel>> MODEL_REGISTRY = new HashMap<>();
    private static ChatLanguageModel model;
    
    private static final String SYSTEM_PROMPT = 
        "You are an AI specialized in political bias analysis. " +
        "Analyze text for political bias on a scale from -1 (extreme left) to 1 (extreme right). " +
        "Return only the numerical score and a brief explanation.";
    
    static {
        // Register models in the factory
        MODEL_REGISTRY.put("gpt-3.5-turbo", () -> 
            OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-3.5-turbo")
                .temperature(0.1)
                .maxTokens(100)
                .build()
        );
        
        MODEL_REGISTRY.put("gpt-4", () -> 
            OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4")
                .temperature(0.1)
                .maxTokens(100)
                .build()
        );
        
        // Initialize with default model
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.err.println("Warning: OPENAI_API_KEY not set. Transformer analyzer won't work.");
            model = null;
        } else {
            // Use model specified in env var or default to gpt-3.5-turbo
            String modelName = System.getenv("LLM_MODEL_NAME");
            if (modelName != null && MODEL_REGISTRY.containsKey(modelName)) {
                model = MODEL_REGISTRY.get(modelName).get();
                System.out.println("Using model: " + modelName);
            } else {
                model = MODEL_REGISTRY.get("gpt-3.5-turbo").get();
                System.out.println("Using default model: gpt-3.5-turbo");
            }
        }
    }
    
    /**
     * Sets the active model by name
     * @param modelName The name of the model to use
     * @return true if successful, false if model not found
     */
    public static boolean setModel(String modelName) {
        if (MODEL_REGISTRY.containsKey(modelName)) {
            model = MODEL_REGISTRY.get(modelName).get();
            return true;
        }
        return false;
    }

    public static AnalyzerResult analyzeText(String text) {
        try {
            if (model == null) {
                throw new IllegalStateException("Model not initialized. Check OPENAI_API_KEY environment variable.");
            }
            
            // Clean text before analysis
            text = AnalyzerResult.cleanText(text);
            
            // Create prompt with system and user messages
            var response = model.generate(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(text)
            );
            
            // Extract the content from the response
            String content = response.content().text();
            
            // Parse score from response
            double score = parseScore(content);
            
            // Convert to percentages
            double leftPercentage = Math.max(0, Math.min(100, 50 - (score * 50)));
            double rightPercentage = 100 - leftPercentage;
            
            return new AnalyzerResult(
                leftPercentage,
                rightPercentage,
                extractExplanation(content, score)
            );
            
        } catch (Exception e) {
            System.err.println("Analysis error: " + e.getMessage());
            return new AnalyzerResult(
                50, 50, 
                "Error analyzing text: " + e.getMessage()
            );
        }
    }

    private static double parseScore(String response) {
        try {
            // Extract first number from response (should be the bias score)
            String[] words = response.split("\\s+");
            for (String word : words) {
                if (word.matches("-?\\d*\\.?\\d+")) {
                    double score = Double.parseDouble(word);
                    return Math.max(-1, Math.min(1, score)); // Clamp between -1 and 1
                }
            }
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    private static String extractExplanation(String response, double score) {
        // Remove the score from the beginning of the response
        String scoreStr = String.valueOf(score);
        int index = response.indexOf(scoreStr);
        if (index >= 0) {
            return response.substring(index + scoreStr.length()).trim();
        }
        return response.trim();
    }

    public static void main(String[] args) {
        if (System.getenv("OPENAI_API_KEY") == null) {
            System.err.println("Error: OPENAI_API_KEY environment variable not set");
            System.exit(1);
        }
        
        try (Scanner scanner = new Scanner(System.in)) {
            // Check for command line args to set model
            if (args.length > 0 && args[0].equals("--model") && args.length > 1) {
                if (!setModel(args[1])) {
                    System.err.println("Unknown model: " + args[1]);
                    System.err.println("Available models: " + String.join(", ", MODEL_REGISTRY.keySet()));
                    System.exit(1);
                }
            }
            
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
}