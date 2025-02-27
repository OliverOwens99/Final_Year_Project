from flask import Flask, request, jsonify, redirect, url_for, session
from flask_bcrypt import Bcrypt
from flask_login import LoginManager, UserMixin, login_user, login_required, logout_user, current_user
from pymongo import MongoClient
from flask_cors import CORS
import subprocess
import json
import logging
import requests
from bs4 import BeautifulSoup


logging.basicConfig(level=logging.INFO)

logging.getLogger('pymongo').setLevel(logging.WARNING)

app = Flask(__name__)
app.secret_key = 'your_secret_key'  # Change this to a random secret key
CORS(app)

bcrypt = Bcrypt(app)
login_manager = LoginManager(app)
login_manager.login_view = 'login'

# MongoDB setup
uri = "mongodb+srv://admin:admin@cluster0.8c7ngnf.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0"
client = MongoClient(uri)

# Send a ping to confirm a successful connection
# try:
#     client.admin.command('ping')
#     print("Pinged your deployment. You successfully connected to MongoDB!")
# except Exception as e:
#     print(e)

db = client.sentiment_analyzer
users_collection = db.users
links_collection = db.links

logging.basicConfig(level=logging.DEBUG)

class User(UserMixin):
    def __init__(self, user_id):
        self.id = user_id

@login_manager.user_loader
def load_user(user_id):
    user = users_collection.find_one({"_id": user_id})
    if user:
        return User(user_id=user["_id"])
    return None

@app.route('/register', methods=['POST'])
def register():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    
    if not username or not password:
        return jsonify({'error': 'Missing username or password'}), 400
    
    hashed_password = bcrypt.generate_password_hash(password).decode('utf-8')
    users_collection.insert_one({"_id": username, "password": hashed_password})
    
    return jsonify({'message': 'User registered successfully'}), 201

@app.route('/login', methods=['POST'])
def login():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    
    # Hardcoded credentials for demo purposes
    hardcoded_username = 'olly'
    hardcoded_password = 'demo'
    
    if username == hardcoded_username and password == hardcoded_password:
        login_user(User(user_id=username))
        return jsonify({'message': 'Login successful'}), 200
    
    user = users_collection.find_one({"_id": username})
    if user and bcrypt.check_password_hash(user['password'], password):
        login_user(User(user_id=username))
        return jsonify({'message': 'Login successful'}), 200
    
    return jsonify({'error': 'Invalid username or password'}), 401

@app.route('/logout')
@login_required
def logout():
    logout_user()
    return jsonify({'message': 'Logged out successfully'}), 200

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
            
        return json.loads(stdout)
        
    except Exception as e:
        raise Exception(f"Error running analyzer: {str(e)}")
# house keeping to ensure the java programme works as expected
def run_java_analyzer_lexicon(analyzer_type, text):
    java_programs = {
        'lexicon': ['java', '-cp', './java-analysers', 'LexiconAnalyzer']
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
            
        return json.loads(stdout)
        
    except Exception as e:
        raise Exception(f"Error running analyzer: {str(e)}")

@app.route('/analyze', methods=['POST'])
@login_required
def analyze():
    data = request.json
    url = data.get('url')
    analyzer_type = data.get('analyzer_type', 'lexicon')
    model = data.get('model', 'gpt-3.5-turbo')  # Get the model parameter
    
    # Extract article text
    try:
        article_text = extract_text_from_url(url)
    except Exception as e:
        return jsonify({'error': f"Error extracting text: {str(e)}"})
    
    # Analyze the text
    try:
        result = None
        console_message = ""
        
        if analyzer_type == 'transformer':
            # Pass model parameter to transformer analyzer
            result = run_java_analyzer(analyzer_type, article_text, model)
        else:
            # Use existing analyzers without model parameter
            result = run_java_analyzer(analyzer_type, article_text)
            
        return jsonify({
            'results': result,
            'console_message': console_message
        })
    except Exception as e:
        return jsonify({'error': f"Analysis error: {str(e)}"})

def run_java_analyzer(analyzer_type, text, model=None):
    java_programs = {
        'llm': ['java', '-cp', './java-analysers/target/sentiment-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar', 'com.sentiment.GPT2Analyzer'],
        'transformer': ['java', '-cp', './java-analysers/target/sentiment-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar', 'com.sentiment.TransformerAnalyzer'],
        'lexicon': ['java', '-cp', './java-analysers/target/sentiment-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar', 'com.sentiment.LexiconAnalyzer']
    }
    
    cmd = java_programs[analyzer_type]
    
    # Add model parameter for transformer analyzer
    if analyzer_type == 'transformer' and model:
        cmd.extend(['--model', model])
    
    try:
        process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        
        stdout, stderr = process.communicate(input=text)
        
        if process.returncode != 0:
            raise Exception(f"Java program error: {stderr}")
            
        return json.loads(stdout)
        
    except Exception as e:
        raise Exception(f"Error running analyzer: {str(e)}")

if __name__ == '__main__':
    app.run(debug=True)
    print('Server running on http://127.0.0.1:5000/')