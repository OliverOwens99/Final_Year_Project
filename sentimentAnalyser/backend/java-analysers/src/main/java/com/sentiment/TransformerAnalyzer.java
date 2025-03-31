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
    "You are an AI specialized in political bias analysis. " +
    "Analyze text for political bias on a scale from -1 (extreme left) to 1 (extreme right). " +
    "LEFT bias indicators: progressive values, social equality, government programs, regulation, wealth redistribution. " +
    "RIGHT bias indicators: traditional values, individual liberty, free markets, limited government, fiscal conservatism. " +
    "Return ONLY a valid JSON object with this exact structure: " +
    "{\"score\": [number between -1 and 1], \"explanation\": \"[detailed political bias analysis explaining WHY the text leans left or right]\"} " +
    "Your explanation MUST focus on political bias indicators, not comment on the events themselves.";

    static {
        // Initialize model registry - one model per provider
        MODEL_REGISTRY.put("phi-2",
                () -> HuggingFaceChatModel.builder().accessToken(System.getenv("HF_API_KEY"))
                        .modelId("microsoft/phi-2") // Excellent 2.7B parameter model
                        .temperature(0.1).timeout(java.time.Duration.ofSeconds(1000)).waitForModel(true).build());

        MODEL_REGISTRY.put("gemma-2b-it",
                () -> HuggingFaceChatModel.builder().accessToken(System.getenv("HF_API_KEY"))
                        .modelId("google/gemma-2b-it") // Google's 2B instruction-tuned model
                        .temperature(0.1).timeout(java.time.Duration.ofSeconds(1000)).waitForModel(true).build());

        MODEL_REGISTRY.put("qwen-1.8b",
                () -> HuggingFaceChatModel.builder().accessToken(System.getenv("HF_API_KEY"))
                        .modelId("Qwen/Qwen1.5-1.8B-Chat") // Strong 1.8B chat model
                        .temperature(0.1).timeout(java.time.Duration.ofSeconds(1000)).waitForModel(true).build());


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
                model = MODEL_REGISTRY.get("phi-2").get();
                System.err.println("Using default model: phi-2");
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
        int maxRetries = 2;
        int retryDelay = 3000; // Start with 3 seconds

        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
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


                
                // Check if explanation is about political bias
                boolean isPoliticalAnalysis = 
                    explanation.toLowerCase().contains("left") || 
                    explanation.toLowerCase().contains("right") || 
                    explanation.toLowerCase().contains("liberal") || 
                    explanation.toLowerCase().contains("conservative") ||
                    explanation.toLowerCase().contains("bias") ||
                    explanation.toLowerCase().contains("politic");
                    
                if (!isPoliticalAnalysis) {
                    // Replace with better explanation
                    if (score < -0.3) {
                        explanation = "The text leans significantly left (score: " + score + 
                                     "). This indicates presence of left-wing political bias markers.";
                    } else if (score < 0) {
                        explanation = "The text leans slightly left (score: " + score + 
                                     "). Some left-leaning political bias indicators are present.";
                    } else if (score > 0.3) {
                        explanation = "The text leans significantly right (score: " + score + 
                                     "). This indicates presence of right-wing political bias markers.";
                    } else if (score > 0) {
                        explanation = "The text leans slightly right (score: " + score + 
                                     "). Some right-leaning political bias indicators are present.";
                    } else {
                        explanation = "The text appears politically neutral (score: " + score + 
                                     "). No significant political bias detected.";
                    }
                    result.put("explanation", explanation);
                }
                
    
                // Use the factory method for consistency
                return AnalyzerResult.createTransformerResult(score, explanation);
                
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                boolean is503Error = errorMsg != null && 
                    (errorMsg.contains("503") || errorMsg.contains("Service Unavailable"));
                    
                // If it's a 503 and not the last attempt, retry
                if (is503Error && attempt < maxRetries) {
                    System.err.println("Service unavailable (503). Retry attempt " + (attempt+1) + 
                                       " of " + maxRetries + " in " + (retryDelay/1000) + " seconds...");
                    try {
                        Thread.sleep(retryDelay);
                        retryDelay *= 2;  // Exponential backoff
                        continue;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                System.err.println("Analysis error: " + e.getMessage());
                
                if (is503Error) {
                    return new AnalyzerResult(50, 50, 
                        "Hugging Face API is currently experiencing high demand (503 error). " + 
                        "Please try again later or use another analyser type.");
                } else {
                    return new AnalyzerResult(50, 50, "Error analysing text: " + e.getMessage());
                }
            }
        }
        
        return new AnalyzerResult(50, 50, 
            "Hugging Face API is unavailable after multiple retry attempts. Please try again later.");
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
    
                // Check if score is an array or a direct number
                double score;
                try {
                    // Try getting as direct number
                    score = parsed.getDouble("score");
                } catch (Exception e) {
                    // If that fails, try getting as array
                    try {
                        score = parsed.getJSONArray("score").getDouble(0);
                        // Replace the array with a direct value
                        parsed.put("score", score);
                    } catch (Exception e2) {
                        // If both methods fail, default to neutral
                        score = 0.0;
                        parsed.put("score", score);
                    }
                }
    
                // Validate score is within expected range
                if (score < -1 || score > 1) {
                    score = Math.max(-1, Math.min(1, score));
                    parsed.put("score", score);
                }
    
                // Check if explanation exists and has valid content
                if (!parsed.has("explanation") || 
                    parsed.getString("explanation").trim().isEmpty() ||
                    parsed.getString("explanation").contains("[") && parsed.getString("explanation").contains("]")) {
                    
                    // Generate a default explanation based on the score
                    String defaultExplanation;
                    if (score < -0.3) {
                        defaultExplanation = "The text leans significantly left (score: " + score + ")";
                    } else if (score < 0) {
                        defaultExplanation = "The text leans slightly left (score: " + score + ")";
                    } else if (score > 0.3) {
                        defaultExplanation = "The text shows significant right-leaning political bias (score: " + score + ")";
                    } else if (score > 0) {
                        defaultExplanation = "The text shows slight right-leaning political bias (score: " + score + ")";
                    } else {
                        defaultExplanation = "The text appears politically neutral (score: " + score + ")";
                    }
                    parsed.put("explanation", defaultExplanation);
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
