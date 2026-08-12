"""
测试配置加载模块。

这个文件负责做一件事：读取 config/test_config.yaml，并检查配置是否完整。
你可以把它理解成“测试启动前的准备工作”。
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

# 当前 test 目录的绝对路径（lib 的上一级就是 test 目录）
BASE_DIR = Path(__file__).resolve().parent.parent

# 默认读取的真实配置文件（包含数据库密码，不应提交到 Git）
DEFAULT_CONFIG_PATH = BASE_DIR / "config" / "test_config.yaml"

# 示例配置文件（可以提交到 Git，给其他人复制使用）
EXAMPLE_CONFIG_PATH = BASE_DIR / "config" / "test_config.example.yaml"

# 下面这些常量定义：配置文件里“必须有”的字段
REQUIRED_TOP_LEVEL = ("base_url", "account", "database", "test_photo", "report")
REQUIRED_ACCOUNT = ("username", "password")
REQUIRED_DATABASE = ("host", "port", "user", "password", "database")
REQUIRED_TEST_PHOTO = ("title_prefix", "description", "location", "category", "tags")
REQUIRED_REPORT = ("output_dir",)


def _ensure_section(config: dict[str, Any], key: str, required_fields: tuple[str, ...]) -> None:
    """
    检查配置中的某个区块（例如 account、database）是否填写完整。

    参数:
        config: 整个配置字典
        key: 要检查的区块名，比如 "account"
        required_fields: 这个区块里必须存在的字段名
    """
    section = config.get(key)
    if not isinstance(section, dict):
        raise ValueError(f"配置项 '{key}' 缺失或格式不正确，应为对象")

    # 找出哪些必填字段是空的
    missing = [field for field in required_fields if section.get(field) in (None, "")]
    if missing:
        raise ValueError(f"配置项 '{key}' 缺少必填字段: {', '.join(missing)}")


def load_config(config_path: Path | None = None) -> dict[str, Any]:
    """
    加载并校验测试配置文件。

    用法示例:
        config = load_config()
        print(config["base_url"])

    返回:
        一个字典，里面包含接口地址、账号、数据库信息等。
    """
    path = config_path or DEFAULT_CONFIG_PATH

    # 如果配置文件不存在，给出清晰提示，告诉用户怎么创建
    if not path.exists():
        hint = (
            f"未找到配置文件: {path}\n"
            f"请复制 {EXAMPLE_CONFIG_PATH} 为 {DEFAULT_CONFIG_PATH} 并填入本地数据库信息。"
        )
        raise FileNotFoundError(hint)

    # 读取 YAML 文件
    with open(path, encoding="utf-8") as file:
        config = yaml.safe_load(file) or {}

    # 检查顶层字段是否齐全
    for key in REQUIRED_TOP_LEVEL:
        if key not in config:
            raise ValueError(f"配置文件缺少顶层字段: {key}")

    # 检查每个子区块里的必填字段
    _ensure_section(config, "account", REQUIRED_ACCOUNT)
    _ensure_section(config, "database", REQUIRED_DATABASE)
    _ensure_section(config, "test_photo", REQUIRED_TEST_PHOTO)
    _ensure_section(config, "report", REQUIRED_REPORT)

    # 额外补充两个运行时路径，后面生成报告时会用到
    config["_base_dir"] = str(BASE_DIR)
    config["_report_dir"] = str((BASE_DIR / config["report"]["output_dir"]).resolve())
    return config
