# Political Bias Analysis System

This project analyses political bias in news articles using three methods: lexicon-based analysis, transformer models, and a fine-tuned BERT model, providing visual and textual explanations of detected bias.

## Screencast Demo

**Watch the demo**: [https://youtu.be/Pr69VvWj92k](https://youtu.be/Pr69VvWj92k)

## Requirements

- Node.js 18+
- Python 3.10+
- Java 21+
- Maven 3.8+
- MongoDB
- Hugging Face API key

## Setup Instructions

### 1. Clone the repository
```bash
git clone https://github.com/OliverOwens99/Final_Year_Project.git
cd Final_Year_Project
```

### 2. Backend Setup

- install python dependencies 

```bash
cd sentimentAnalyser/backend
pip install -r requirements.txt
```
MONGODB_URI=mongodb://localhost:27017/biasanalyzer
SECRET_KEY=your_secret_key_here
HF_API_KEY=your_huggingface_api_key


### 3. Build Java Analysers
```bash
cd java-analysers
mvn clean package
cd ..
mkdir -p lib
cp java-analysers/target/sentiment-analyzer-1.0-SNAPSHOT-jar-with-dependencies.jar lib/
```




### 4. Set up the ONNX model
* **Option 1:** Download from [Google Drive](https://drive.google.com/drive/folders/1DyQ_pQqyh6O42njOs4xSTV2ZYJl4Locb?usp=sharing)
  ```bash
  mkdir -p java-analysers/src/main/resources
  # On Windows:
  move C:\path\to\downloads\political-bias-model.onnx java-analysers\src\main\resources\
  # On macOS/Linux:
  mv ~/Downloads/political-bias-model.onnx java-analysers/src/main/resources/
  ```

* **Option 2:** Generate it yourself
  ```bash
  python trainer.py
  mkdir -p java-analysers/src/main/resources
  cp political-bias-model.onnx java-analysers/src/main/resources/
  ```

### 5. Start the backend server
```bash
python sentimentServer.py
```

### 6. Set up and run the frontend
```bash
cd ../frontend
# Create .env file for the API URL
echo "VITE_API_URL=http://localhost:5000" > .env
npm install
npm run dev
```

### 7. Access the application
* Open your browser to: `http://localhost:5173`
* Register a new account or use test account:
  * Username: `olly`
  * Password: `demo`

