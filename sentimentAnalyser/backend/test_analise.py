import requests

url = 'http://127.0.0.1:5000/analyze'
data = {
    'url': 'http://example.com/article',
    'analyzer_type': 'llm'
}

response = requests.post(url, json=data)
print(response.json())