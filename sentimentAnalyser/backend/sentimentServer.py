from flask import Flask, request, jsonify
import subprocess
import json
import logging

app = Flask(__name__)

logging.basicConfig(level=logging.DEBUG)

def run_java_analyzer(analyzer_type, text):
    java_programs = {
        'llm': ['java', '-cp', '.:json.jar', 'LLMAnalyzer'],
        'transformer': ['java', '-cp', '.:json.jar', 'TransformerAnalyzer'],
        'lexicon': ['java', '-cp', '.:json.jar', 'LexiconAnalyzer']
    }
    
    try:
        process = subprocess.Popen(
            java_programs[analyzer_type],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        
        stdout, stderr = process.communicate(input=text)
        
        if process.returncode != 0:
            raise Exception(f"Java program error: {stderr}")
            
        #return json.loads(stdout)
        return {
        'left': 50,
        'right': 50,
        'message': f'Placeholder analysis for {analyzer_type}'
    }
        
    except Exception as e:
        raise Exception(f"Error running analyzer: {str(e)}")

@app.route('/analyze', methods=['POST'])
def analyze():
    data = request.get_json()
    logging.debug(f"Received data: {data}")
    
    if not data or 'url' not in data or 'analyzer_type' not in data:
        return jsonify({'error': 'Missing url or analyzer type'}), 400
        
    try:
        text = extract_text_from_url(data['url'])
        logging.debug(f"Extracted text: {text}")
        
        results = run_java_analyzer(data['analyzer_type'], text)
        logging.debug(f"Analysis results: {results}")
        
        return jsonify(results)
        
    except Exception as e:
        logging.error(f"Error: {str(e)}")
        return jsonify({'error': str(e)}), 500
    
def extract_text_from_url(url):
    return 'This is the extracted text from the URL'

if __name__ == '__main__':
    app.run(debug=True)
    print('Server running on http://127.0.0.1:5000/')