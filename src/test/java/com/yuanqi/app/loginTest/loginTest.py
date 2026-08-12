import json
from pathlib import Path

import pytest
import requests

# 登录接口地址
LOGIN_URL = "http://localhost:8080/api/users/login"
# 测试数据文件（与当前脚本同目录）
TEST_DATA_FILE = Path(__file__).resolve().parent / "login_test_data.json"


def load_test_cases():
    """从 JSON 文件加载登录测试用例"""
    with open(TEST_DATA_FILE, encoding="utf-8") as f:
        return json.load(f)


TEST_CASES = load_test_cases()


def send_login_request(username, password):
    """调用登录接口"""
    payload = {
        "username": username,
        "password": str(password) if password is not None else "",
    }
    try:
        return requests.post(LOGIN_URL, json=payload, timeout=10)
    except requests.RequestException as exc:
        error_message = str(exc)

        class FailedResponse:
            status_code = 0
            text = error_message

            @staticmethod
            def json():
                return {"code": None, "message": error_message, "data": None}

        return FailedResponse()


def print_login_result(index, case_data, response):
    """在控制台输出单次登录请求结果"""
    desc = case_data.get("desc", "未命名用例")
    expect_code = case_data.get("expect_status")

    try:
        body = response.json()
        business_code = body.get("code")
        message = body.get("message", "")
        data = body.get("data")
    except (ValueError, json.JSONDecodeError):
        body = response.text
        business_code = None
        message = ""
        data = None

    passed = business_code == expect_code
    result_flag = "PASS" if passed else "FAIL"

    print("=" * 60)
    print(f"[用例 {index}] {desc}")
    print(f"  请求参数: username={case_data.get('username')!r}, password={case_data.get('password')!r}")
    print(f"  HTTP 状态码: {response.status_code}")
    print(f"  业务状态码: {business_code} (期望: {expect_code}) -> {result_flag}")
    if message:
        print(f"  提示信息: {message}")
    if data is not None:
        print(f"  返回数据: {data}")
    print(f"  完整响应: {body}")


def run_login_tests():
    """循环执行全部登录测试用例并输出结果"""
    print(f"共加载 {len(TEST_CASES)} 条测试数据，开始调用登录接口...\n")

    pass_count = 0
    for index, case in enumerate(TEST_CASES, start=1):
        response = send_login_request(case["username"], case["password"])
        print_login_result(index, case, response)

        try:
            if response.json().get("code") == case.get("expect_status"):
                pass_count += 1
        except (ValueError, json.JSONDecodeError):
            pass

    print("=" * 60)
    print(f"测试完成: {pass_count}/{len(TEST_CASES)} 通过")


@pytest.mark.parametrize(
    "case_data",
    TEST_CASES,
    ids=[case.get("desc", f"case_{i}") for i, case in enumerate(TEST_CASES)],
)
def test_login(case_data):
    """pytest 参数化登录测试"""
    index = TEST_CASES.index(case_data) + 1
    response = send_login_request(case_data["username"], case_data["password"])
    print_login_result(index, case_data, response)

    body = response.json()
    assert body.get("code") == case_data["expect_status"], (
        f"期望业务状态码 {case_data['expect_status']}，实际 {body.get('code')}"
    )


if __name__ == "__main__":
    run_login_tests()
