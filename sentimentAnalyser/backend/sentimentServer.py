from flask import Flask, request, jsonify
from flask_cors import CORS
import requests
from bs4 import BeautifulSoup
import re

app = Flask(__name__)
CORS(app)  # Enable CORS for all routes

def extract_text_from_url(url):
    try:
        response = requests.get(url)
        soup = BeautifulSoup(response.text, 'html.parser')
        
        # Remove script and style elements
        for script in soup(["script", "style"]):
            script.decompose()
            
        # Get text and clean it
        text = soup.get_text()
        # Replace multiple newlines with single newline
        text = re.sub(r'\n+', '\n', text.strip())
        return text
    except Exception as e:
        return str(e)

def analyze_political_bias(text):
    # Placeholder for your bias analysis logic
    # You'll need to implement your own algorithm or use an existing model
    # This is just a dummy implementation
    return {
        "left": 60,
        "right": 40
    }

@app.route('/analyze', methods=['POST'])
def analyze():
    data = request.get_json()
    url = data.get('url')
    
    if not url:
        return jsonify({"error": "No URL provided"}), 400
    
    # Extract text from URL
    article_text = extract_text_from_url(url)
    
    # Analyze the text
    results = analyze_political_bias(article_text)
    
    return jsonify(results)

if __name__ == '__main__':
    app.run(debug=True)