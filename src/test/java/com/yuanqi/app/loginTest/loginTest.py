import requests
import pytest

# 1. 准备测试数据
test_cases = [
    {"username": "qm", "password": "123456"}
]


def send_login_request(username, password):
    url = "http://localhost:8080/api/users/login"
    userdata= {
        "username": username,
        "password": password
    }

    req = requests.post(url, userdata)
    return req

@pytest.mark.parametrize("case_data", test_cases)
def test_login(case_data):
    username = case_data["username"]
    password = case_data["password"]

    response = send_login_request(username, password)


    #！-打印返回值信息 -
    print("请求结果", response)
