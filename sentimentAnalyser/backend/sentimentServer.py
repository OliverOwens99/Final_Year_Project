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
#this was added to help scrub fox news and other anti scrapping sites 
USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36'
HEADERS = {'User-Agent': USER_AGENT, 'Accept': 'text/html,application/xhtml+xml,application/xml'}



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
    # Get the URI from environment variables
    uri = os.environ.get("uri")
    
    
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
    # Check if it's an obvious download link before making any requests
    download_extensions = ['.pdf', '.zip', '.exe', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx']
    if any(url.lower().endswith(ext) for ext in download_extensions):
        return "This appears to be a download link, not an article."
    
    try:
        # Try newspaper3k first
        article = Article(url)
        article.download()
        article.parse()
        
        # If we got reasonable text content, use it
        if article.text and len(article.text) > 100:
            extracted_text = article.text
            logging.info(f"Extracted {len(extracted_text)} chars with newspaper3k. Preview: {extracted_text[:150]}...")
            return extracted_text
    except Exception as primary_error:
        logging.warning(f"Primary extraction failed: {str(primary_error)}")
        # Continue to fallback methods
        
    # Fallback 1: Direct requests with browser-like headers
    try:
        logging.info(f"newspaper3k failed, trying direct requests")
        session = requests.Session()
        session.headers.update(HEADERS)
        
        # Some sites check referer or require cookies
        session.headers.update({'Referer': 'https://www.google.com/'})
        
        response = session.get(url, timeout=15)
        response.raise_for_status()  # Raise exception for 4XX/5XX errors
        
        # Check if we got HTML content
        if 'text/html' in response.headers.get('Content-Type', ''):
            soup = BeautifulSoup(response.text, 'html.parser')
            
            # Remove non-content elements
            for element in soup(["script", "style", "nav", "header", "footer", "meta"]):
                element.extract()
                
            # Try to find main content - common content containers
            main_content = None
            for selector in ['article', 'main', '[role="main"]', '.content', '#content', '.article-body', '.story-body']:
                main_content = soup.select_one(selector)
                if main_content and len(main_content.get_text(strip=True)) > 200:
                    logging.info(f"Found main content using selector: {selector}")
                    break
                    
            # Extract text from main content if found, otherwise from the whole page
            if main_content:
                text = main_content.get_text(separator=' ')
                logging.info(f"Extracted text from main content element")
            else:
                text = soup.get_text(separator=' ')
                logging.info(f"Extracted text from entire page (no main content found)")
                
            # Clean up text
            lines = [line.strip() for line in text.splitlines() if line.strip()]
            text = " ".join(lines)
            
            if len(text) > 100:
                extracted_text = text
                logging.info(f"Extracted {len(extracted_text)} chars with BeautifulSoup. Preview: {extracted_text[:150]}...")
                return extracted_text
    except requests.exceptions.HTTPError as http_err:
        if http_err.response.status_code == 404:
            return "The article could not be found (404 error). The URL might be incorrect or the content may have been removed."
        elif http_err.response.status_code == 403:
            return "Access to this article is forbidden (403 error). The website may be blocking automated access."
        else:
            logging.error(f"HTTP error: {http_err}")
            
    except Exception as e:
        logging.error(f"Fallback extraction error: {str(e)}")
    
    # If we get here, all extraction methods failed
    return "Could not extract text from this URL. The article might be behind a paywall, or the website may block automated access."
    
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
    model = data.get('model')
    
    try:
        # Extract article text from the URL
        article_text = extract_text_from_url(url)
        
        # Analyze the text
        result = run_java_analyzer(analyzer_type, article_text, model)
        
        # Save analysis to history
        history_record = {
            "user": current_user.id,
            "url": url,
            "analyzer_type": analyzer_type,
            "model": model if analyzer_type == "transformer" else None,
            "left": result["left"],
            "right": result["right"],
            "message": result["message"],
            "explanation": result.get("explanation", ""),
            "date": datetime.now()
        }
        # Store in MongoDB
        links_collection.insert_one(history_record)
        
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
        'llm': 'com.sentiment.BertPoliticalAnalyser',
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
        
        # Log the raw stdout for debugging
        logging.info(f"Raw Java output: {result.stdout}")
        
        try:
            # Parse the JSON response
            parsed_result = json.loads(result.stdout)
            
            # Ensure all required fields are present
            if "left" not in parsed_result:
                logging.warning("Missing 'left' field in analyzer output")
                parsed_result["left"] = 50.0
                
            if "right" not in parsed_result:
                logging.warning("Missing 'right' field in analyzer output")
                parsed_result["right"] = 50.0
                
            # Ensure values are proper floats
            parsed_result["left"] = float(parsed_result["left"])
            parsed_result["right"] = float(parsed_result["right"])
            
            # Make sure both message and explanation exist
            if "message" not in parsed_result:
                parsed_result["message"] = "Analysis complete"
                
            if "explanation" not in parsed_result:
                if "message" in parsed_result:
                    parsed_result["explanation"] = parsed_result["message"]
                else:
                    parsed_result["explanation"] = "No explanation provided"
            
            # Log the processed result for debugging
            logging.info(f"Processed JSON result: {json.dumps(parsed_result)}")
            
            return parsed_result
            
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