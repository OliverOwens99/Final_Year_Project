# convert_gpt2.py
from transformers import GPT2Tokenizer, GPT2Model
import torch

def convert_model():
    tokenizer = GPT2Tokenizer.from_pretrained('gpt2')
    model = GPT2Model.from_pretrained('gpt2')
    
    dummy_input = tokenizer.encode("test", return_tensors="pt")
    
    torch.onnx.export(
        model,
        dummy_input,
        "src/main/resources/models/gpt2-political.onnx",
        input_names=['input_ids'],
        output_names=['output'],
        dynamic_axes={
            'input_ids': {0: 'batch', 1: 'sequence'}
        },
        opset_version=12
    )

if __name__ == '__main__':
    convert_model()