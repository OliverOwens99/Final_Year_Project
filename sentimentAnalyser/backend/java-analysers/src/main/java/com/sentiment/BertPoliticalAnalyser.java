package com.sentiment;

import ai.onnxruntime.*;
import java.util.*;
import java.nio.LongBuffer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class BertPoliticalAnalyser {
    private static OrtEnvironment env;
    private static OrtSession session;
    private static final int MAX_LENGTH = 512;
    
    static {
        try {
            env = OrtEnvironment.getEnvironment();
            
            // Fix the model loading part 
            try (InputStream modelStream = BertPoliticalAnalyser.class.getResourceAsStream("/political-bias-model.onnx")) {
                if (modelStream == null) {
                    throw new IOException("Model file not found in resources");
                }
                
                // Create a temp file
                File tempModel = File.createTempFile("bert-model-", ".onnx");
                tempModel.deleteOnExit();
                
                // Copy stream to temp file
                try (FileOutputStream fos = new FileOutputStream(tempModel)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = modelStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                
                // Create session using the temp file path
                session = env.createSession(tempModel.getAbsolutePath());
                System.err.println("BERT model loaded successfully");
            }
        } catch (OrtException | IOException e) {
            System.err.println("Error initializing BERT model: " + e.getMessage());
        }
    }

    public static AnalyzerResult analyzeText(String text) {
        try {
            // Clean text before analysis
            text = AnalyzerResult.cleanText(text);
            
            // Create input tensor
            long[] inputIds = tokenizeText(text);
            
            // Create attention mask (all 1s)
            long[] attentionMask = new long[inputIds.length];
            Arrays.fill(attentionMask, 1L);
            
            // Create input map with both required tensors
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(inputIds),
                new long[]{1, inputIds.length}
            ));
            inputs.put("attention_mask", OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(attentionMask),
                new long[]{1, attentionMask.length}
            ));
            
            // Run inference
            OrtSession.Result output = session.run(inputs);
            
            // Process results
            OnnxValue onnxValue = output.get(0);
            float[] logits = ((OnnxTensor) onnxValue).getFloatBuffer().array();
            
            double bias = calculatePoliticalBias(logits);
            double leftScore = Math.max(0, Math.min(100, 50 - (bias * 25)));
            double rightScore = 100 - leftScore;
            
            return new AnalyzerResult(
                leftScore,
                rightScore,
                String.format("Political bias analysis: %.2f (negative=left, positive=right)", bias)
            );
            
        } catch (OrtException e) {
            System.err.println("Analysis error: " + e.getMessage());
            return new AnalyzerResult(50, 50, "BERT analysis failed: " + e.getMessage());
        }
    }

    private static long[] tokenizeText(String text) {
        // Create a list to hold token IDs
        List<Long> tokenIds = new ArrayList<>();
        
        // Add [CLS] token first (ID 101 in BERT)
        tokenIds.add(101L);
        
        // Split text into words
        String[] words = text.split("\\s+");
        int maxWords = Math.min(words.length, MAX_LENGTH - 2); // Reserve space for [CLS] and [SEP]
        
        // Convert words to token IDs (simplified)
        for (int i = 0; i < maxWords; i++) {
            // Use hashCode but with BERT's vocab size (30522)
            long tokenId = Math.abs(words[i].hashCode() % 30522);
            // Avoid 0, 101, and 102 which are special tokens
            if (tokenId == 0 || tokenId == 101 || tokenId == 102) tokenId += 1000;
            tokenIds.add(tokenId);
        }
        
        // Add [SEP] token at the end (ID 102 in BERT)
        tokenIds.add(102L);
        
        // Convert to array
        long[] inputIds = new long[tokenIds.size()];
        for (int i = 0; i < tokenIds.size(); i++) {
            inputIds[i] = tokenIds.get(i);
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