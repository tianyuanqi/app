"""
pytest 公共 fixture 文件。

fixture 可以理解成“测试开始前，pytest 自动帮你准备好的工具”。
这样 test_photo_flow.py 里就不用重复写加载配置、创建客户端的代码了。
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

# 当前 test 目录
TEST_DIR = Path(__file__).resolve().parent

# 把 test 目录加入 Python 搜索路径，这样才能 import lib.xxx
if str(TEST_DIR) not in sys.path:
    sys.path.insert(0, str(TEST_DIR))

from lib.api_client import ApiClient
from lib.config_loader import load_config
from lib.db_client import DbClient
from lib.report_builder import ReportCollector


@pytest.fixture(scope="session")
def config() -> dict:
    """
    整个测试会话只加载一次配置。

    scope="session" 的意思：
    不管有多少测试用例，这个 fixture 只执行一次，然后大家共用。
    """
    return load_config()


@pytest.fixture(scope="session")
def api_client(config: dict) -> ApiClient:
    """提供一个已经知道 base_url 的接口客户端。"""
    return ApiClient(config["base_url"])


@pytest.fixture(scope="session")
def report_collector(config: dict) -> ReportCollector:
    """提供一个报告收集器，用来记录每一步 PASS/FAIL。"""
    return ReportCollector(config["_report_dir"])
