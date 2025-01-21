

public class AnalyzerResult {
    private final double left;
    private final double right;
    private final String message;

    public AnalyzerResult(double left, double right, String message) {
        this.left = left;
        this.right = right;
        this.message = message;
    }

    @Override
    public String toString() {
        return String.format("{\"left\": %.2f, \"right\": %.2f, \"message\": \"%s\"}", left, right, message);
    }
}