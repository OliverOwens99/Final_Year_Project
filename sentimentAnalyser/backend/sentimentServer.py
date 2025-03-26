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
from newspaper import Article
import requests
from dotenv import load_dotenv
import os

load_dotenv()
# Define base paths
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
JAR_PATH = os.path.join(BASE_DIR, "java-analysers", "target", 
                      "sentiment-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar")

# Set up logging
logging.basicConfig(level=logging.INFO)
logging.getLogger('pymongo').setLevel(logging.WARNING)

app = Flask(__name__)
app.secret_key = 'your_secret_key'  # Change this to a random secret key
CORS(app, supports_credentials=True, origins=["http://localhost:5173", "http://127.0.0.1:5173"])

bcrypt = Bcrypt(app)
login_manager = LoginManager(app)
login_manager.login_view = 'login'
@login_manager.unauthorized_handler
def unauthorized():
    return jsonify({"error": "Authentication required"}), 401

# MongoDB setup
try:
    client = MongoClient(uri, serverSelectionTimeoutMS=5000)
    client.admin.command('ping')  # Test connection
    db = client.sentiment_analyzer
    users_collection = db.users
    links_collection = db.links
    logging.info("Connected to MongoDB successfully")
except Exception as e:
    logging.warning(f"MongoDB connection error: {e}. Using in-memory storage.")
    # Simple in-memory storage for testing when MongoDB isn't available
    users_db = {"olly": {"password": "demo"}}  # Keep your hardcoded user
    links_db = []
    
    # Mock collections for testing
    class MemoryCollection:
        def __init__(self, db):
            self.db = db
        def find_one(self, query):
            key = query.get("_id")
            return {"_id": key, "password": self.db.get(key)} if key in self.db else None
        def insert_one(self, doc):
            self.db[doc["_id"]] = doc.get("password")
        def find(self, query=None):
            return [{"_id": k, "password": v} for k, v in self.db.items()]
    
    users_collection = MemoryCollection(users_db)
    links_collection = MemoryCollection([]) 

class User(UserMixin):
    def __init__(self, user_id):
        self.id = user_id

@login_manager.user_loader
def load_user(user_id):
    user = users_collection.find_one({"_id": user_id})
    if user:
        return User(user_id=user["_id"])
    return None
def extract_text_from_url(url):
    """Extract main article text from a URL using newspaper3k"""
    try:
        # Use newspaper3k for better article extraction
        article = Article(url)
        article.download()
        article.parse()
        
        if not article.text or len(article.text) < 100:
            # Fallback to BeautifulSoup if newspaper3k doesn't extract enough text
            response = requests.get(url, headers={'User-Agent': 'Mozilla/5.0'})
            soup = BeautifulSoup(response.text, 'html.parser')
            
            # Remove script and style elements
            for script in soup(["script", "style"]):
                script.extract()
                
            # Get text and clean it
            text = soup.get_text()
            lines = (line.strip() for line in text.splitlines())
            chunks = (phrase.strip() for line in lines for phrase in line.split("  "))
            text = '\n'.join(chunk for chunk in chunks if chunk)
            return text[:5000]  # Limit text size
            
        return article.text[:5000]  # Limit text size for performance
        
    except Exception as e:
        logging.error(f"Error extracting text from {url}: {e}")
        raise Exception(f"Could not extract text from URL: {str(e)}")
    
@app.route('/register', methods=['POST'])
def register():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    
    if not username or not password:
        return jsonify({'error': 'Missing username or password'}), 400
    
    # Check if user already exists
    existing_user = users_collection.find_one({"_id": username})
    if existing_user:
        return jsonify({'error': 'Username already exists'}), 409
    
    hashed_password = bcrypt.generate_password_hash(password).decode('utf-8')
    users_collection.insert_one({"_id": username, "password": hashed_password})
    
    # After registration, log the user in automatically 
    login_user(User(user_id=username))
    
    return jsonify({'message': 'User registered successfully'}), 201

@app.route('/check-auth', methods=['GET'])
def check_auth():
    """Check if the user is authenticated"""
    if current_user.is_authenticated:
        return jsonify({'authenticated': True, 'user': current_user.id})
    return jsonify({'authenticated': False}), 401

@app.route('/login', methods=['POST'])
def login():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    
    # Hardcoded credentials for testing
    if username == 'olly' and password == 'demo':
        login_user(User(user_id=username))
        return jsonify({'message': 'Login successful'}), 200
    
    try:
        user = users_collection.find_one({"_id": username})
        if user and bcrypt.check_password_hash(user['password'], password):
            login_user(User(user_id=username))
            return jsonify({'message': 'Login successful'}), 200
    except Exception as e:
        logging.error(f"Login error: {e}")
    
    return jsonify({'error': 'Invalid username or password'}), 401


@app.route('/analyze', methods=['POST'])
@login_required
def analyze():
    data = request.json
    url = data.get('url')
    analyzer_type = data.get('analyzer_type', 'lexicon')
    model = data.get('model', 'gpt-3.5-turbo')
    
    try:
        # Extract article text from the URL
        article_text = extract_text_from_url(url)
        
        # Analyze the text
        result = run_java_analyzer(analyzer_type, article_text, model)
        
        return jsonify({
            'results': result,
            'console_message': f"Successfully analyzed article from {url}"
        })
    except Exception as e:
        logging.error(f"Analysis error: {str(e)}", exc_info=True)
        return jsonify({'error': f"Analysis error: {str(e)}"})

def run_java_analyzer(analyzer_type, text, model=None):
    """Run Java analyzer with the compiled JAR file"""
    # Check if JAR exists
    if not os.path.exists(JAR_PATH):
        raise Exception(f"JAR file not found: {JAR_PATH}. Make sure to build the project with Maven first.")
    
    # Use the JAVA_PATH from .env if available, otherwise try system java
    java_executable = os.environ.get('JAVA_PATH', 'java')
    
    # If the specified java_executable doesn't exist, try some common locations
    if not os.path.exists(java_executable) and java_executable != 'java':
        logging.warning(f"JAVA_PATH '{java_executable}' not found, trying system Java")
        java_executable = 'java'
    
    # Define the analyzer classes
    analyzer_classes = {
        'llm': 'com.sentiment.GPT2Analyzer',
        'transformer': 'com.sentiment.TransformerAnalyzer',
        'lexicon': 'com.sentiment.LexiconAnalyzer'
    }
    
    if analyzer_type not in analyzer_classes:
        raise ValueError(f"Unknown analyzer type: {analyzer_type}")
    
    # Build the command using the specified java executable
    cmd = [java_executable, "-cp", JAR_PATH, analyzer_classes[analyzer_type]]
    
    # Add model parameter for transformer analyzer
    if analyzer_type == 'transformer' and model:
        cmd.extend(["--model", model])
    
    try:
        # Log the command being executed for debugging
        logging.info(f"Executing: {' '.join(cmd)}")
        
        # Use subprocess.run instead of Popen for simplicity
        result = subprocess.run(
            cmd,
            input=text,
            capture_output=True,
            text=True,
            check=False  # Don't raise exception on non-zero return code
        )
        
        if result.stderr:
            logging.warning(f"Java analyzer stderr: {result.stderr}")
        
        if result.returncode != 0:
            raise Exception(f"Java program error ({result.returncode}): {result.stderr}")
        
        # Try to parse the JSON result
        try:
            return json.loads(result.stdout)
        except json.JSONDecodeError:
            logging.error(f"Invalid JSON: '{result.stdout}'")
            raise Exception("Analyzer returned invalid JSON")
            
    except Exception as e:
        logging.error(f"Error running Java analyzer: {str(e)}")
        raise Exception(f"Error running analyzer: {str(e)}")

@app.route('/history')
@login_required
def history():
    """Get user's analysis history"""
    user_links = list(links_collection.find({"user": current_user.id}))
    for link in user_links:
        link['_id'] = str(link['_id'])  # Convert ObjectId to string for JSON serialization
    return jsonify(user_links)

if __name__ == '__main__':
    from datetime import datetime  # Add this import at the top of the file
    app.run(debug=True)
    print('Server running on http://127.0.0.1:5000/')