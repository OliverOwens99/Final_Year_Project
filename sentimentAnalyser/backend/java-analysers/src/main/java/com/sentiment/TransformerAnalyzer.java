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
        MODEL_REGISTRY.put("phi-2",
                () -> HuggingFaceChatModel.builder().accessToken(System.getenv("HF_API_KEY"))
                        .modelId("microsoft/phi-2") // Excellent 2.7B parameter model
                        .temperature(0.1).timeout(java.time.Duration.ofSeconds(120)).build());

        MODEL_REGISTRY.put("gemma-2b-it",
                () -> HuggingFaceChatModel.builder().accessToken(System.getenv("HF_API_KEY"))
                        .modelId("google/gemma-2b-it") // Google's 2B instruction-tuned model
                        .temperature(0.1).timeout(java.time.Duration.ofSeconds(120)).build());

        MODEL_REGISTRY.put("qwen-1.8b",
                () -> HuggingFaceChatModel.builder().accessToken(System.getenv("HF_API_KEY"))
                        .modelId("Qwen/Qwen1.5-1.8B-Chat") // Strong 1.8B chat model
                        .temperature(0.1).timeout(java.time.Duration.ofSeconds(120)).build());
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
                // Remove the check for OpenAI models since we don't want to use them
                if (hfKey == null) {
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
            // Since we don't want to use OpenAI models at all
            if (hfKey != null) {
                // Use one of your defined models
                model = MODEL_REGISTRY.get("falcon-small").get();
                System.err.println("Using default model: falcon-small");
            } else {
                model = null;
                System.err.println("No HF_API_KEY available, model will not function");
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
            System.err.println("Raw response: " + response);
            
            // Find the first { and last } to extract just the JSON part
            int startIdx = response.indexOf('{');
            int endIdx = response.lastIndexOf('}') + 1;
    
            if (startIdx >= 0 && endIdx > startIdx) {
                String jsonStr = response.substring(startIdx, endIdx);
                JSONObject parsed = new JSONObject(jsonStr);
                
                // Handle case where score is an array
                if (parsed.get("score") instanceof org.json.JSONArray) {
                    org.json.JSONArray scoreArray = parsed.getJSONArray("score");
                    if (scoreArray.length() > 0) {
                        Object value = scoreArray.get(0);
                        if (value instanceof String) {
                            // Handle case where the model just repeats the instructions
                            String strValue = (String)value;
                            if (strValue.contains("number between")) {
                                parsed.put("score", 0.0);
                            } else {
                                try {
                                    double numValue = Double.parseDouble(strValue);
                                    parsed.put("score", numValue);
                                } catch (NumberFormatException e) {
                                    parsed.put("score", 0.0);
                                }
                            }
                        } else {
                            parsed.put("score", scoreArray.getDouble(0));
                        }
                    } else {
                        parsed.put("score", 0.0);
                    }
                }
                
                // Validate score is within expected range
                double score = parsed.getDouble("score");
                if (score < -1 || score > 1) {
                    score = Math.max(-1, Math.min(1, score));
                    parsed.put("score", score);
                }
                
                return parsed;
            }
            
            // No JSON found, create default
            JSONObject defaultJson = new JSONObject();
            defaultJson.put("score", 0.0);
            defaultJson.put("explanation", "Model did not return valid JSON format");
            return defaultJson;
        } catch (Exception e) {
            System.err.println("Error parsing JSON response: " + e.getMessage());
            JSONObject defaultJson = new JSONObject();
            defaultJson.put("score", 0.0);
            defaultJson.put("explanation", "Failed to parse model response: " + e.getMessage());
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
