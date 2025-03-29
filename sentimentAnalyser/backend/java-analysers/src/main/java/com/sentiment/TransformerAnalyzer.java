package com.sentiment;

import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.huggingface.HuggingFaceChatModel;
import org.json.JSONObject;

public class TransformerAnalyzer {
    // Model factory - allows easily swapping between different models
    private static final Map<String, Supplier<ChatLanguageModel>> MODEL_REGISTRY = new HashMap<>();
    private static ChatLanguageModel model;

    private static final String SYSTEM_PROMPT =
            "You are an AI specialized in political bias analysis. "
                    + "Analyze text for political bias on a scale from -1 (extreme left) to 1 (extreme right). "
                    + "Return ONLY a valid JSON object with this exact structure: "
                    + "{\"score\": [number between -1 and 1], \"explanation\": \"[your explanation]\"}";

    static {
        // Initialize model registry - one model per provider
        MODEL_REGISTRY.put("gpt-3.5-turbo",
                () -> OpenAiChatModel.builder().apiKey(System.getenv("OPENAI_API_KEY"))
                        .modelName("gpt-3.5-turbo").temperature(0.0).build());

        // Small Hugging Face model (well under 10GB)
        MODEL_REGISTRY.put("tiny-llama",
                () -> HuggingFaceChatModel.builder().accessToken(System.getenv("HF_API_KEY"))
                        .modelId("TinyLlama/TinyLlama-1.1B-Chat-v1.0").temperature(0.1).build());
        MODEL_REGISTRY.put("deepseek-1.3b",
                () -> HuggingFaceChatModel.builder().accessToken(System.getenv("HF_API_KEY"))
                        .modelId("deepseek-ai/deepseek-coder-1.3b-base").temperature(0.1).build());

        // Initialize the model based on available API keys
        String openaiKey = System.getenv("OPENAI_API_KEY");
        String hfKey = System.getenv("HF_API_KEY");

        if (openaiKey == null && hfKey == null) {
            System.err.println(
                    "Warning: Neither OPENAI_API_KEY nor HF_API_KEY are set. Transformer analyzer won't work.");
            model = null;
        } else {
            // Use model specified in env var or select based on available keys
            String modelName = System.getenv("LLM_MODEL_NAME");
            if (modelName != null && MODEL_REGISTRY.containsKey(modelName)) {
                // Check if we have the right API key for requested model
                if ((modelName.startsWith("gpt") && openaiKey == null)) {
                    System.err
                            .println("Warning: Requested OpenAI model but OPENAI_API_KEY not set.");
                    selectDefaultModel(openaiKey, hfKey);
                } else if ((!modelName.startsWith("gpt")) && hfKey == null) {
                    System.err.println(
                            "Warning: Requested Hugging Face model but HF_API_KEY not set.");
                    selectDefaultModel(openaiKey, hfKey);
                } else {
                    try {
                        model = MODEL_REGISTRY.get(modelName).get();
                        System.err.println("Using model: " + modelName);
                    } catch (Exception e) {
                        System.err.println("Error initializing model: " + e.getMessage());
                        selectDefaultModel(openaiKey, hfKey);
                    }
                }
            } else {
                selectDefaultModel(openaiKey, hfKey);
            }
        }
    }

    /**
     * Selects a default model based on available API keys
     */
    private static void selectDefaultModel(String openaiKey, String hfKey) {
        try {
            if (openaiKey != null) {
                model = MODEL_REGISTRY.get("gpt-3.5-turbo").get();
                System.err.println("Using default model: gpt-3.5-turbo"); // Fixed: changed to
                                                                          // stderr
            } else if (hfKey != null) {
                model = MODEL_REGISTRY.get("tiny-llama").get();
                System.err.println("Using default model: tiny-llama");
            } else {
                model = null;
                System.err.println("No API keys available, model will not function");
            }
        } catch (Exception e) {
            model = null;
            System.err.println("Error initializing default model: " + e.getMessage());
        }
    }

    /**
     * Sets the active model by name
     * 
     * @param modelName The name of the model to use
     * @return true if successful, false if model not found
     */
    public static boolean setModel(String modelName) {
        if (MODEL_REGISTRY.containsKey(modelName)) {
            try {
                model = MODEL_REGISTRY.get(modelName).get();
                return true;
            } catch (Exception e) {
                System.err.println("Error initializing model: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    public static AnalyzerResult analyzeText(String text) {
        try {
            if (model == null) {
                throw new IllegalStateException(
                        "Model not initialized. Check API key environment variables.");
            }
    
            // Clean text before analysis
            text = AnalyzerResult.cleanText(text);
    
            // Create prompt with system and user messages
            var response = model.generate(new SystemMessage(SYSTEM_PROMPT), new UserMessage(text));
    
            // Extract the content from the response
            String content = response.content().text();
    
            // Parse score from response
            JSONObject result = parseResponse(content);
            double score = result.getDouble("score");
            String explanation = result.getString("explanation");
    
            // Use the factory method for consistency
            return AnalyzerResult.createTransformerResult(score, explanation);
        } catch (Exception e) {
            System.err.println("Analysis error: " + e.getMessage());
            return new AnalyzerResult(50, 50, "Error analyzing text: " + e.getMessage());
        }
    }

    private static JSONObject parseResponse(String response) {
        try {
            // Find the first { and last } to extract just the JSON part
            int startIdx = response.indexOf('{');
            int endIdx = response.lastIndexOf('}') + 1;

            if (startIdx >= 0 && endIdx > startIdx) {
                String jsonStr = response.substring(startIdx, endIdx);
                return new JSONObject(jsonStr);
            }
            throw new Exception("No valid JSON found in response");
        } catch (Exception e) {
            System.err.println("Error parsing JSON response: " + e.getMessage());
            // Create default JSON with neutral values
            JSONObject defaultJson = new JSONObject();
            defaultJson.put("score", 0.0);
            defaultJson.put("explanation", "Failed to parse model response");
            return defaultJson;
        }
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            // Check for command line args to set model
            if (args.length > 0 && args[0].equals("--model") && args.length > 1) {
                if (!setModel(args[1])) {
                    System.err.println("Unknown model: " + args[1]);
                    System.err.println(
                            "Available models: " + String.join(", ", MODEL_REGISTRY.keySet()));
                    System.exit(1);
                }
            }

            String text = scanner.nextLine();
            AnalyzerResult result = analyzeText(text);
            System.out.println(result.toString());
        } catch (Exception e) {
            System.out.println(String.format(
                    "{\"left\": 50, \"right\": 50, \"message\": \"Analysis failed: %s\"}",
                    e.getMessage().replace("\"", "\\\"")));
        }
    }
}
