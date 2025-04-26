# Political Bias Analysis System

## Overview

This project is a web application that focuses on sentiment analysis using various forms of machine learning and AI to analyse and process any article and look for spin and/or bias in said article. The goal is to state and display on the webpage whether the content of the link provided is right-leaning or left-leaning on the political spectrum.

The system offers three distinct methods for detecting political bias:

1. **Lexicon-Based Analysis**: Uses a dictionary of political terms and sentiment analysis to identify bias through terminology
2. **Transformer-Based Analysis**: Leverages large language models to analyze content context and framing
3. **BERT-Based Analysis**: Employs a fine-tuned BERT model specifically trained for political bias detection

## Features

- **Multi-method bias detection**: Choose between lexicon, transformer, or BERT-based analysis
- **Model selection**: Select from multiple transformer models (Gemma, Mistral, Llama, etc.)
- **Visual results**: Pie charts showing left/right bias percentages
- **Detailed explanations**: Textual descriptions of detected bias and reasoning
- **History tracking**: Save and revisit previous analyses
- **User authentication**: Secure access to personal analysis history

## Technology Stack

### Frontend
- **React**: Component-based UI library
- **Vite**: Fast build tool and development server
- **Chart.js**: For visualization of bias percentages
- **CSS**: Responsive design for multiple devices

### Backend
- **Flask**: Python web framework for API endpoints
- **Newspaper3k**: Primary article content extraction
- **BeautifulSoup**: Secondary content extraction
- **MongoDB**: Storing user history and analysis results

### Analysis Engines
- **Java 21**: Core analysis implementation with virtual threads
- **LangChain4j**: Integration with transformer models
- **ONNX Runtime**: Efficient local model inference
- **Hugging Face models**: Pre-trained language models

## System Architecture

The system follows a three-tier architecture:

1. **Presentation Layer**: React frontend for user interaction and visualization
2. **Application Layer**: Flask backend for coordination and text extraction
3. **Analysis Layer**: Java-based engines implementing different bias detection approaches

Communication between layers uses REST APIs and standardized JSON formats.

## Requirements

- Node.js 18+
- Python 3.10+
- Java 21+
- MongoDB
- Hugging Face API key (for transformer-based analysis)

## Installation and Setup

### 1. Clone the repository

git clone https://github.com/yourusername/Final_Year_Project-1.git
cd Final_Year_Project-1

### 2.Set up the frontend

cd sentimentAnalyser/frontend
npm install
npm run dev

### 3.Set up the backend

cd ../backend
pip install -r requirements.txt

### 4.Configure environment variables

Create a .env file in the backend directory with:

MONGODB_URI=mongodb://localhost:27017/biasanalyzer
SECRET_KEY=your_secret_key
HF_API_KEY=your_huggingface_api_key


### 5. Setup jars

#### Create the lib directory (from the backend directory)
mkdir -p lib

#### Download JAR files from Google Drive
#### https://drive.google.com/drive/folders/1DyQ_pQqyh6O42njOs4xSTV2ZYJl4Locb?usp=sharing

#### Move downloaded JARs to the lib folder
#### On Windows:
move C:\path\to\downloaded\sentiment-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar lib\

#### On macOS/Linux:
mv ~/Downloads/sentiment-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar lib/



### 7. Start the backend server


# From the backend directory
# Activate the virtual environment
# On Windows:
venv\Scripts\activate

# On macOS/Linux:
source venv/bin/activate

# Run the server
python sentimentServer.py



### Link to screenCast

https://youtu.be/Pr69VvWj92k