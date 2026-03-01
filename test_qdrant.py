import requests
import json

# 测试Qdrant中是否存在方彪的简历信息
def test_qdrant_search():
    url = "http://localhost:6333/collections/default/points/search"
    
    # 这里我们使用一个简单的向量，实际应该使用真实的embedding
    payload = {
        "vector": [0.1] * 384,  # 假设使用all-MiniLM-L6-v2模型，维度为384
        "limit": 10,
        "filter": {
            "must": [
                {
                    "key": "text",
                    "match": {
                        "text": "方彪"
                    }
                }
            ]
        }
    }
    
    headers = {
        "Content-Type": "application/json"
    }
    
    try:
        response = requests.post(url, data=json.dumps(payload), headers=headers)
        response.raise_for_status()
        result = response.json()
        print("Qdrant搜索结果:")
        print(json.dumps(result, indent=2, ensure_ascii=False))
        
        if result.get('result'):
            print(f"找到{len(result['result'])}条匹配结果")
        else:
            print("未找到匹配结果")
    except Exception as e:
        print(f"请求失败: {e}")

if __name__ == "__main__":
    test_qdrant_search()
