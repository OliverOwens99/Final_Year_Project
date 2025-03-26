from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

# Load model and tokenizer
model_name = "bucketresearch/politicalBiasBERT"
tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModelForSequenceClassification.from_pretrained(model_name)

# Export to ONNX
dummy_input = tokenizer("This is a test", return_tensors="pt")
torch.onnx.export(
    model, 
    args=(dummy_input.input_ids, dummy_input.attention_mask),
    f="political-bias-model.onnx",
    input_names=["input_ids", "attention_mask"],
    output_names=["logits"],
    dynamic_axes={
        "input_ids": {0: "batch", 1: "sequence"},
        "attention_mask": {0: "batch", 1: "sequence"},
        "logits": {0: "batch"}
    }
)