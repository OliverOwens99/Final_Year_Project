

public class AnalyzerResult {
    private final double left;
    private final double right;
    private final String message;

    public static String cleanText(String text) {
        if (text == null) return "";
        
        // Convert to lowercase
        text = text.toLowerCase();
        
        // Remove URLs
        text = text.replaceAll("https?://\\S+|www\\.\\S+", "");
        
        // Remove special characters but keep sentence structure
        text = text.replaceAll("[^a-z\\s.,!?]", " ");
        
        // Normalize whitespace
        text = text.replaceAll("\\s+", " ").trim();
        
        return text;
    }

    public AnalyzerResult(double left, double right, String message) {
        this.left = left;
        this.right = right;
        this.message = message;
    }

    @Override
    public String toString() {
        return String.format("{\"left\": %.2f, \"right\": %.2f, \"message\": \"%s\"}", 
            left, right, message);
    }
}