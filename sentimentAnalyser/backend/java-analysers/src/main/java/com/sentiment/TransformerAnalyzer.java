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

/**
 * Analyses political bias in text using transformer-based language models. This class provides
 * methods to evaluate political leaning in content through multiple transformer models from Hugging
 * Face. It assigns a political bias score between -1 (extreme left) and 1 (extreme right).
 * 
 * The analyser incorporates retry mechanisms for handling temporary service unavailability and
 * provides detailed explanations of detected bias.
 */
public class TransformerAnalyzer {
    /**
     * Registry that maps model names to supplier functions that create model instances. Allows
     * dynamic selection and instantiation of different language models.
     */
    private static final Map<String, Supplier<ChatLanguageModel>> MODEL_REGISTRY = new HashMap<>();

    /**
     * The currently active language model used for text analysis.
     */
    private static ChatLanguageModel model;

    /**
     * System prompt that instructs the model how to analyse political bias. Provides guidelines on
     * what constitutes left vs. right bias and specifies the expected response format with score
     * and explanation.
     */
    private static final String SYSTEM_PROMPT =
            "You are an AI specialized in political bias analysis. "
                    + "Analyze text for political bias on a scale from -1 (extreme left) to 1 (extreme right). "
                    + "IMPORTANT: Positive scores indicate right-leaning bias, negative scores indicate left-leaning bias."
                    + "LEFT bias indicators: progressive values, social equality, government programs, regulation, wealth redistribution. "
                    + "RIGHT bias indicators: traditional values, individual liberty, free markets, limited government, fiscal conservatism. "
                    + "Format your response as a JSON object with this EXACT structure: "
                    + "{\"score\": X, \"explanation\": \"Y\"} "
                    + "where X is a number between -1 and 1, and Y is your detailed political bias analysis. "
                    + "Your explanation must focus on political bias indicators, not comment on the events themselves. "
                    + "DO NOT include any text before or after the JSON object. "
                    + "DO NOT use placeholders. "
                    + "DO NOT repeat these instructions in your response.";

    static {
        // Initialize model registry - one model per provider

        MODEL_REGISTRY.put("mistral-7b",
                () -> HuggingFaceChatModel.builder().accessToken(System.getenv("HF_API_KEY"))
                        .modelId("mistralai/Mistral-7B-Instruct-v0.2") // This one works
                        .temperature(0.1).timeout(java.time.Duration.ofSeconds(120))
                        .waitForModel(true).build());

        MODEL_REGISTRY.put("gemma-2b-it",
                () -> HuggingFaceChatModel.builder().accessToken(System.getenv("HF_API_KEY"))
                        .modelId("google/gemma-2b-it") // This one works
                        .temperature(0.1).timeout(java.time.Duration.ofSeconds(60))
                        .waitForModel(false).build());

        MODEL_REGISTRY.put("llama-2-7b",
                () -> HuggingFaceChatModel.builder().accessToken(System.getenv("HF_API_KEY"))
                        .modelId("meta-llama/Llama-2-7b-chat-hf") // Replace SOLAR with this
                        .temperature(0.1).timeout(java.time.Duration.ofSeconds(120))
                        .waitForModel(false).build());

        MODEL_REGISTRY.put("deepseek-chat",
                () -> HuggingFaceChatModel.builder().accessToken(System.getenv("HF_API_KEY"))
                        .modelId("deepseek-ai/deepseek-coder-1.3b-instruct") // Much smaller model
                                                                             // (1.3B)
                        .temperature(0.1).timeout(java.time.Duration.ofSeconds(90))
                        .waitForModel(false).build());


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
     * Selects a default model based on available API keys. When Hugging Face API key is available,
     * uses gemma-2b-it as the default model.
     * 
     * @param openaiKey The OpenAI API key (not used but kept for method signature)
     * @param hfKey The Hugging Face API key
     */
    private static void selectDefaultModel(String openaiKey, String hfKey) {
        try {
            // Since we don't want to use OpenAI models at all
            if (hfKey != null) {
                // Change default from phi-2 to gemma-2b-it
                model = MODEL_REGISTRY.get("gemma-2b-it").get();
                System.err.println("Using default model: gemma-2b-it");
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
     * Sets the active model by name. Attempts to initialise the specified model from the registry.
     * 
     * @param modelName The name of the model to use
     * @return true if successfully initialised the model, false if model not found or
     *         initialisation failed
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

    /**
     * Analyses text for political bias using the currently active language model. Implements retry
     * logic with exponential backoff for handling temporary service unavailability.
     * 
     * @param text The text to analyse for political bias
     * @return An AnalyzerResult containing the bias analysis (left/right percentages and
     *         explanation)
     */
    public static AnalyzerResult analyzeText(String text) {
        int maxRetries = 4;
        int retryDelay = 3000; // Start with 3 seconds
        text = text.replaceAll("\\p{C}", "");

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (model == null) {
                    throw new IllegalStateException(
                            "Model not initialized. Check API key environment variables.");
                }

                // Clean text before analysis
                text = AnalyzerResult.cleanText(text);

                // Create prompt with system and user messages
                var response =
                        model.generate(new SystemMessage(SYSTEM_PROMPT), new UserMessage(text));

                // Extract the content from the response
                String content = response.content().text();

                // Parse score from response
                JSONObject result = parseResponse(content);
                double score = result.getDouble("score");
                String explanation = result.getString("explanation");


                // Add this debug line:
                System.err.println(
                        "DEBUG - Model result: score=" + score + ", explanation=" + explanation);



                // Use the factory method for consistency
                return AnalyzerResult.createTransformerResult(score, explanation);

            } catch (Exception e) {
                String errorMsg = e.getMessage();
                boolean is503Error = errorMsg != null
                        && (errorMsg.contains("503") || errorMsg.contains("Service Unavailable"));

                // If it's a 503 and not the last attempt, retry
                if (is503Error && attempt < maxRetries) {
                    // added jitter to try and get some more consistent results
                    int jitter = (int)(Math.random() * 1000);
                    int delay = retryDelay * (1 << attempt) + jitter;
                    System.err.println("Service unavailable (503). Retry attempt " + (attempt + 1)
                            + " of " + maxRetries + " in " + (delay / 1000) + " seconds...");
                    try {
                        Thread.sleep(delay);
                        continue;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                System.err.println("Analysis error: " + e.getMessage());

                if (is503Error) {
                    return new AnalyzerResult(50, 50,
                            "Hugging Face API is currently experiencing high demand (503 error). "
                                    + "Please try again later or use another analyser type.");
                } else {
                    return new AnalyzerResult(50, 50, "Error analysing text: " + e.getMessage());
                }
            }
        }

        return new AnalyzerResult(50, 50,
                "Hugging Face API is unavailable after multiple retry attempts. Please try again later.");
    }

    /**
     * Parses the JSON response from the language model. Uses multiple strategies to extract the
     * score and explanation: 1. Direct JSON parsing of the last JSON object in the response 2.
     * Regular expression extraction as a fallback
     * 
     * @param response The raw text response from the language model
     * @return A JSONObject containing the score and explanation
     */
    private static JSONObject parseResponse(String response) {
        try {
            System.err.println("Raw response: " + response);

            // Look for the last JSON object in the string - this is likely the model's actual
            // answer
            int lastOpenBrace = response.lastIndexOf('{');
            int lastCloseBrace = response.lastIndexOf('}');

            if (lastOpenBrace >= 0 && lastCloseBrace > lastOpenBrace) {
                // Extract just the last JSON object
                String jsonStr = response.substring(lastOpenBrace, lastCloseBrace + 1);
                System.err.println("Extracted final JSON: " + jsonStr);

                try {
                    // Try to parse as direct JSON first
                    JSONObject parsed = new JSONObject(jsonStr);

                    // Check if score is present and valid
                    double score;
                    try {
                        score = parsed.getDouble("score");
                        System.err.println("Successfully parsed score from final JSON: " + score);

                        // Validate score is within range
                        if (score < -1 || score > 1) {
                            double oldScore = score;
                            score = Math.max(-1, Math.min(1, score));
                            parsed.put("score", score);
                            System.err.println(
                                    "Score out of range, clamped " + oldScore + " to " + score);
                        }

                        // Make sure explanation exists
                        if (!parsed.has("explanation")
                                || parsed.getString("explanation").trim().isEmpty()) {
                            String defaultExplanation =
                                    "Score: " + score + (score < 0 ? " (left-leaning)"
                                            : score > 0 ? " (right-leaning)" : " (neutral)");

                            parsed.put("explanation", defaultExplanation);
                        }

                        // This looks like a valid model response JSON, return it
                        System.err.println("Final score: " + score);
                        System.err.println("Final explanation: " + parsed.getString("explanation"));
                        return parsed;
                    } catch (Exception e) {
                        System.err.println(
                                "Last JSON object didn't have a valid score, trying regex extraction");
                        // Fall through to regex extraction
                    }
                } catch (Exception e) {
                    System.err.println("Couldn't parse last JSON object, trying regex extraction");
                    // Fall through to regex extraction
                }
            }

            // If direct JSON parsing failed, try regex
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\\{\"score\":\\s*(-?\\d+(\\.\\d+)?),\\s*\"explanation\":\\s*\"([^\"]*)\"\\}");
            java.util.regex.Matcher matcher = pattern.matcher(response);

            // Find the LAST match, not first
            int lastMatchStart = -1;
            double lastScore = 0.0;
            String lastExplanation = "";

            while (matcher.find()) {
                lastMatchStart = matcher.start();
                lastScore = Double.parseDouble(matcher.group(1));
                lastExplanation = matcher.group(3);
            }

            if (lastMatchStart >= 0) {
                System.err.println("Regex extracted final score: " + lastScore);
                System.err.println("Regex extracted final explanation: " + lastExplanation);

                JSONObject parsed = new JSONObject();
                parsed.put("score", lastScore);
                parsed.put("explanation", lastExplanation);
                return parsed;
            }

            // If all extraction methods failed
            System.err.println("All JSON extraction methods failed");
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

    /**
     * Command-line entry point for the analyser. Accepts an optional model specification via
     * command line arguments. Reads text from standard input, analyses it, and outputs the result
     * as JSON.
     * 
     * @param args Command line arguments --model [model-name] to select a specific model from the
     *        registry
     */
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
