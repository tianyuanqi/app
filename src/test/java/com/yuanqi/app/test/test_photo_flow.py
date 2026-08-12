"""
照片接口自动化 Demo 主程序。

完整流程（共 8 步）：
1. 读取配置
2. 登录获取 Token
3. 上传测试照片
4. 查询我的照片并提取字段
5. 做接口断言
6. 查询数据库做二次校验
7. 清理测试数据
8. 生成测试报告

两种运行方式：
    pytest test_photo_flow.py -v
    python test_photo_flow.py
"""

from __future__ import annotations

import sys
import time
from datetime import datetime
from pathlib import Path

import pytest

# 当前文件所在目录，也就是 test 目录
TEST_DIR = Path(__file__).resolve().parent

# 让 Python 能找到 lib 目录下的模块
if str(TEST_DIR) not in sys.path:
    sys.path.insert(0, str(TEST_DIR))

from lib.api_client import ApiClient
from lib.config_loader import load_config
from lib.db_client import DbClient
from lib.report_builder import ReportCollector

# 测试图片所在目录
FIXTURES_DIR = TEST_DIR / "fixtures"

# 上传时使用的样例图片
SAMPLE_IMAGE = FIXTURES_DIR / "sample.jpg"


class PhotoFlowRunner:
    """
    照片接口自动化测试的执行器。

    你可以把它理解成“总导演”：
    - 它按顺序调用每一步
    - 记录每一步成功还是失败
    - 最后输出报告
    """

    def __init__(self, config: dict, report: ReportCollector) -> None:
        """
        初始化测试运行器。

        参数:
            config: 从 test_config.yaml 读取的配置
            report: 报告收集器，用来记录每一步结果
        """
        self.config = config
        self.report = report

        # 接口客户端：负责发 HTTP 请求
        self.api = ApiClient(config["base_url"])

        # 数据库客户端：负责查库和删测试数据
        self.db = DbClient(config["database"])

        # context 用来保存“步骤之间要传递的数据”
        # 例如登录后的 token、userId，后面步骤会用到
        self.context: dict = {}

        # 本次上传成功后，对应照片的 ID
        self.photo_id: int | None = None

        # 本次上传使用的标题，后面要根据标题去列表里找这张照片
        self.test_title = ""

    def _record_step(self, name: str, started: float, status: str, details: dict, error: str | None = None) -> None:
        """
        记录某一步的执行结果。

        参数:
            name: 步骤名称
            started: 开始时间（time.perf_counter() 的返回值）
            status: PASS 或 FAIL
            details: 这一步的关键信息
            error: 失败原因（可选）
        """
        duration_ms = (time.perf_counter() - started) * 1000
        self.report.add_step(name, status, duration_ms, details, error)

    def step_load_config(self) -> None:
        """第 1 步：确认配置已经读取成功。"""
        started = time.perf_counter()
        try:
            details = {
                "base_url": self.config["base_url"],
                "username": self.config["account"]["username"],
                "database": self.config["database"]["database"],
            }
            self._record_step("1. 读取配置", started, "PASS", details)
        except Exception as exc:
            self._record_step("1. 读取配置", started, "FAIL", {}, str(exc))
            raise

    def step_login(self) -> None:
        """第 2 步：登录并拿到 Token。"""
        started = time.perf_counter()
        account = self.config["account"]
        result: dict = {}

        try:
            # 调用登录接口
            result = self.api.login(account["username"], account["password"])
            body = result["body"]

            # 断言：业务状态码必须是 200
            assert body.get("code") == 200, f"登录失败: {body}"

            data = body.get("data") or {}

            # token 可能在 token 字段，也可能在 accessToken 字段
            token = data.get("token") or data.get("accessToken")
            assert token, "登录响应缺少 token"
            assert data.get("username") == account["username"], "用户名不匹配"

            # 保存登录结果，供后续步骤使用
            self.context.update(
                {
                    "token": token,
                    "userId": data.get("userId"),
                    "username": data.get("username"),
                }
            )

            self._record_step(
                "2. 登录获取 Token",
                started,
                "PASS",
                {
                    "userId": data.get("userId"),
                    "username": data.get("username"),
                    "token_preview": f"{token[:16]}...",
                },
            )
        except Exception as exc:
            self._record_step("2. 登录获取 Token", started, "FAIL", result.get("body", {}), str(exc))
            raise

    def step_upload_photo(self) -> None:
        """第 3 步：上传一张带 AUTO_TEST_ 前缀的测试照片。"""
        started = time.perf_counter()
        photo_cfg = self.config["test_photo"]

        # 标题加上时间戳，避免和历史测试数据重名
        self.test_title = f"{photo_cfg['title_prefix']}{datetime.now().strftime('%Y%m%d%H%M%S')}"

        try:
            assert SAMPLE_IMAGE.exists(), f"测试图片不存在: {SAMPLE_IMAGE}"

            result = self.api.upload_photo(
                file_path=SAMPLE_IMAGE,
                title=self.test_title,
                description=photo_cfg["description"],
                location=photo_cfg["location"],
                category=int(photo_cfg["category"]),
                tags=list(photo_cfg["tags"]),
            )
            body = result["body"]
            assert body.get("code") == 200, f"上传失败: {body}"

            self._record_step(
                "3. 上传测试照片",
                started,
                "PASS",
                {"title": self.test_title, "response": body.get("data")},
            )
        except Exception as exc:
            self._record_step("3. 上传测试照片", started, "FAIL", {"title": self.test_title}, str(exc))
            raise

    def step_query_my_photos(self) -> None:
        """第 4 步：查询当前用户的照片列表，并找到刚上传的那张。"""
        started = time.perf_counter()

        try:
            result = self.api.get_my_photos(current=1, page_size=20)
            body = result["body"]
            assert body.get("code") == 200, f"查询失败: {body}"

            data = body.get("data") or {}
            records = data.get("records") or []

            # 在返回列表里，按标题找到刚上传的照片
            matched = next((item for item in records if item.get("title") == self.test_title), None)
            assert matched is not None, f"列表中未找到上传照片: {self.test_title}"

            # 保存 photo_id，后面查库和清理都要用
            self.photo_id = int(matched["id"])

            extracted = {
                "total": data.get("total"),
                "photo_id": self.photo_id,
                "title": matched.get("title"),
                "author_username": (matched.get("author") or {}).get("username"),
                "record_count": len(records),
            }
            self.context["list_data"] = extracted

            self._record_step("4. 查询我的照片并提取字段", started, "PASS", extracted)
        except Exception as exc:
            self._record_step("4. 查询我的照片并提取字段", started, "FAIL", {}, str(exc))
            raise

    def step_api_assertions(self) -> None:
        """第 5 步：对接口返回的数据做断言（判断是否符合预期）。"""
        started = time.perf_counter()

        try:
            list_data = self.context["list_data"]
            username = self.config["account"]["username"]

            # 断言 1：总数至少 1 条
            assert list_data["total"] >= 1, "照片总数应大于等于 1"

            # 断言 2：作者用户名必须等于当前登录账号
            assert list_data["author_username"] == username, "作者用户名与登录账号不一致"

            # 断言 3：必须已经解析出 photo_id
            assert self.photo_id is not None, "未解析到 photo_id"

            self._record_step(
                "5. 接口断言",
                started,
                "PASS",
                {
                    "total": list_data["total"],
                    "photo_id": self.photo_id,
                    "author_username": list_data["author_username"],
                },
            )
        except Exception as exc:
            self._record_step("5. 接口断言", started, "FAIL", self.context.get("list_data", {}), str(exc))
            raise

    def step_db_validation(self) -> None:
        """第 6 步：直接查数据库，验证接口结果和数据库是否一致。"""
        started = time.perf_counter()
        username = self.config["account"]["username"]
        user_id = int(self.context["userId"])
        title_prefix = self.config["test_photo"]["title_prefix"]

        try:
            self.db.connect()

            # 校验 1：数据库里的用户 ID 和登录返回的一致
            db_user_id = self.db.get_user_id_by_username(username)
            assert db_user_id == user_id, f"数据库 userId={db_user_id} 与登录 userId={user_id} 不一致"

            # 校验 2：刚上传的照片在数据库里确实存在
            assert self.photo_id is not None
            assert self.db.photo_exists(self.photo_id, user_id), "数据库中不存在上传的照片"

            # 校验 3：AUTO_TEST_ 开头的照片至少 1 条
            auto_count = self.db.count_auto_test_photos(user_id, title_prefix)
            assert auto_count >= 1, "数据库中 AUTO_TEST 照片数量应 >= 1"

            self._record_step(
                "6. 数据库校验",
                started,
                "PASS",
                {
                    "db_user_id": db_user_id,
                    "photo_id": self.photo_id,
                    "auto_test_count": auto_count,
                },
            )
        except Exception as exc:
            self._record_step("6. 数据库校验", started, "FAIL", {}, str(exc))
            raise

    def step_cleanup(self) -> None:
        """
        第 7 步：清理本次测试产生的数据。

        优先尝试调用 DELETE 接口；
        如果接口因为鉴权问题失败，就改用 SQL 删除。
        """
        started = time.perf_counter()
        user_id = int(self.context["userId"])
        cleanup_method = "unknown"

        try:
            assert self.photo_id is not None, "缺少 photo_id，无法清理"

            delete_result = self.api.delete_photo(self.photo_id)
            body = delete_result["body"]

            # 如果 DELETE 接口没删成功，就走 SQL 兜底
            if delete_result["status_code"] in (401, 403) or body.get("code") not in (200, None):
                cleanup_method = "sql_fallback"
                if self.db.connection is None:
                    self.db.connect()
                self.db.delete_photo_cascade(self.photo_id, user_id)
            else:
                cleanup_method = "api"

            # 再查一次数据库，确认真的删干净了
            if self.db.connection is None:
                self.db.connect()
            still_exists = self.db.photo_exists(self.photo_id, user_id)
            assert not still_exists, f"清理后照片仍存在: photo_id={self.photo_id}"

            self._record_step(
                "7. 清理测试数据",
                started,
                "PASS",
                {"photo_id": self.photo_id, "cleanup_method": cleanup_method},
            )
        except Exception as exc:
            self._record_step(
                "7. 清理测试数据",
                started,
                "FAIL",
                {"photo_id": self.photo_id, "cleanup_method": cleanup_method},
                str(exc),
            )
            raise
        finally:
            # 不管成功失败，都关闭数据库连接
            self.db.close()

    def step_generate_report(self) -> dict[str, str]:
        """第 8 步：生成 JSON 和 HTML 测试报告。"""
        started = time.perf_counter()
        try:
            paths = self.report.build_paths()
            self._record_step("8. 生成测试报告", started, "PASS", paths)
            return self.report.write_reports(paths=paths)
        except Exception as exc:
            self._record_step("8. 生成测试报告", started, "FAIL", {}, str(exc))
            raise

    def run(self) -> dict[str, str]:
        """
        按顺序执行全部 8 个步骤。

        返回:
            报告文件路径，例如 {"json": "...", "html": "..."}
        """
        self.step_load_config()

        try:
            self.step_login()
            self.step_upload_photo()
            self.step_query_my_photos()
            self.step_api_assertions()
            self.step_db_validation()
            self.step_cleanup()
            return self.step_generate_report()
        except Exception:
            # 如果中间某一步失败了，也尽量把测试数据删掉，并生成报告
            if self.photo_id is not None:
                try:
                    if self.db.connection is None:
                        self.db.connect()
                    self.db.delete_photo_cascade(self.photo_id, int(self.context.get("userId", 0)))
                except Exception:
                    pass
                finally:
                    self.db.close()

            self.step_generate_report()
            raise


def run_demo() -> None:
    """
    直接运行脚本时的入口函数。

    用法:
        python test_photo_flow.py
    """
    config = load_config()
    report = ReportCollector(config["_report_dir"])
    runner = PhotoFlowRunner(config, report)
    paths = runner.run()

    print("=" * 60)
    print("照片接口自动化 Demo 执行完成")
    print(f"JSON 报告: {paths['json']}")
    print(f"HTML 报告: {paths['html']}")
    print("=" * 60)


def test_photo_flow(config: dict, report_collector: ReportCollector) -> None:
    """
    pytest 测试入口。

    用法:
        pytest test_photo_flow.py -v
    """
    runner = PhotoFlowRunner(config, report_collector)
    paths = runner.run()

    # 确认报告文件路径已经生成
    assert paths["json"]
    assert paths["html"]


# 只有当“直接运行这个文件”时，才执行 run_demo()
# 如果被 pytest 导入，则不会走这里
if __name__ == "__main__":
    run_demo()
