package com.sentiment;

import ai.onnxruntime.*;
import java.util.*;
import java.nio.LongBuffer;
import java.io.IOException;

public class GPT2Analyzer {
    private static OrtEnvironment env;
    private static OrtSession session;
    private static final int MAX_LENGTH = 512;
    
    static {
        try {
            env = OrtEnvironment.getEnvironment();
            
            // Load model from resources
            String modelPath = GPT2Analyzer.class
                .getResource("/models/gpt2-political.onnx")
                .getPath();
                
            if (modelPath == null) {
                throw new IOException("Model file not found in resources");
            }
            
            session = env.createSession(modelPath);
        } catch (OrtException | IOException e) {
            System.err.println("Error initializing GPT-2: " + e.getMessage());
        }
    }

    public static AnalyzerResult analyzeText(String text) {
        try {
            // Clean text before analysis
            text = AnalyzerResult.cleanText(text);
            
            // Create input tensor
            long[] inputIds = tokenizeText(text);
            LongBuffer longBuffer = LongBuffer.wrap(inputIds);
            OnnxTensor inputTensor = OnnxTensor.createTensor(
                env,
                longBuffer,
                new long[]{1, inputIds.length}
            );
            
            // Run inference
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputTensor);
            OrtSession.Result output = session.run(inputs);
            
            // Process results - properly cast to OnnxTensor
            OnnxValue onnxValue = output.get(0);
            float[] logits;
            
            if (onnxValue instanceof OnnxTensor) {
                logits = ((OnnxTensor) onnxValue).getFloatBuffer().array();
            } else {
                // Handle unexpected type
                throw new OrtException("Expected OnnxTensor but got " + onnxValue.getClass().getName());
            }
            
            double bias = calculatePoliticalBias(logits);
            
            double leftScore = Math.max(0, Math.min(100, 50 - (bias * 25)));
            double rightScore = 100 - leftScore;
            
            return new AnalyzerResult(
                leftScore,
                rightScore,
                String.format("GPT-2 Analysis complete (bias: %.2f)", bias)
            );
            
        } catch (OrtException e) {
            System.err.println("Analysis error: " + e.getMessage());
            return new AnalyzerResult(50, 50, "GPT-2 analysis failed");
        }
    }

    private static long[] tokenizeText(String text) {
        // Simple space-based tokenization for now
        String[] tokens = text.split("\\s+");
        long[] inputIds = new long[Math.min(tokens.length, MAX_LENGTH)];
        
        // Convert tokens to IDs (simplified)
        for (int i = 0; i < inputIds.length; i++) {
            inputIds[i] = tokens[i].hashCode() % 50257; // GPT-2 vocab size
        }
        
        return inputIds;
    }

    private static double calculatePoliticalBias(float[] logits) {
        // Simplified bias calculation
        double sum = 0;
        for (float logit : logits) {
            sum += Math.tanh(logit); // Normalize to [-1, 1]
        }
        return sum / logits.length;
    }
    
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            String text = scanner.nextLine();
            AnalyzerResult result = analyzeText(text);
            System.out.println(result.toString());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.out.println("{\"left\": 50, \"right\": 50, \"message\": \"Analysis failed\"}");
        }
    }
}