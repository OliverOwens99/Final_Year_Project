from flask import Flask, request, jsonify
import subprocess
import json

app = Flask(__name__)

def run_java_analyzer(analyzer_type, text):
    java_programs = {
        ## names of programmes will change this is placeholder
        'llm': ['java', '-cp', '.:json.jar', 'LLMAnalyzer'],
        'transformer': ['java', '-cp', '.:json.jar', 'TransformerAnalyzer'],
        'lexicon': ['java', '-cp', '.:json.jar', 'LexiconAnalyzer']
    }
    
    try:
        # Run the Java program and pipe the text to it
        process = subprocess.Popen(
            java_programs[analyzer_type],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        
        # Send text to Java program and get results
        stdout, stderr = process.communicate(input=text)
        
        if process.returncode != 0:
            raise Exception(f"Java program error: {stderr}")
            
        # Parse the JSON output from Java
        return json.loads(stdout)
        
    except Exception as e:
        raise Exception(f"Error running analyzer: {str(e)}")

@app.route('/analyze', methods=['POST'])
def analyze():
    data = request.get_json()
    
    if not data or 'url' not in data or 'analyzer_type' not in data:
        return jsonify({'error': 'Missing url or analyzer type'}), 400
        
    try:
        # Your existing text extraction code here
        text = extract_text_from_url(data['url'])
        
        # Run the selected Java analyzer
        results = run_java_analyzer(data['analyzer_type'], text)
        
        return jsonify(results)
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    
def extract_text_from_url(url):
    # Placeholder for text extraction
    return 'This is the extracted text from the URL'

if __name__ == '__main__':
    app.run(debug=True)