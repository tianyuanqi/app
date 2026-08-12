"""
HTTP 接口客户端封装。

这个文件专门负责“发 HTTP 请求”。
你可以把它理解成：一个会自动帮你拼 URL、带 Token 的小助手。
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import requests


class ApiClient:
    """
    后端接口调用客户端。

    目前封装了 4 个常用接口：
    1. login        登录
    2. upload_photo 上传照片
    3. get_my_photos 查询我的照片
    4. delete_photo 删除照片
    """

    def __init__(self, base_url: str, timeout: int = 15) -> None:
        """
        初始化客户端。

        参数:
            base_url: 后端地址，例如 http://localhost:8080
            timeout: 单次请求最长等待秒数，超时就会报错
        """
        # 去掉末尾多余的 /，避免出现 http://xxx//api/... 这种错误地址
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

        # 登录成功后会把 token 存在这里，后续请求会自动带上
        self.token: str | None = None

    def _headers(self) -> dict[str, str]:
        """
        构造请求头。

        如果已经登录，就自动加上:
        Authorization: Bearer xxx
        """
        headers: dict[str, str] = {}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        return headers

    def login(self, username: str, password: str) -> dict[str, Any]:
        """
        调用登录接口。

        接口: POST /api/users/login
        请求体: {"username": "...", "password": "..."}

        返回:
            {
              "status_code": HTTP 状态码,
              "body": 后端返回的 JSON
            }
        """
        url = f"{self.base_url}/api/users/login"
        payload = {"username": username, "password": str(password)}

        response = requests.post(url, json=payload, timeout=self.timeout)
        body = self._parse_json(response)

        # 登录成功时，保存 token，供后续接口使用
        if body.get("code") == 200:
            data = body.get("data") or {}
            self.token = data.get("token") or data.get("accessToken")

        return {
            "status_code": response.status_code,
            "body": body,
        }

    def upload_photo(
        self,
        file_path: Path,
        title: str,
        description: str,
        location: str,
        category: int,
        tags: list[str],
    ) -> dict[str, Any]:
        """
        上传一张照片。

        接口: POST /api/photos/upload
        请求类型: multipart/form-data（文件上传格式）

        注意:
            - 必须先 login，否则没有 token
            - category 必须是数据库里真实存在的分类 ID
        """
        url = f"{self.base_url}/api/photos/upload"

        # 普通表单字段
        form_data = [
            ("title", title),
            ("description", description),
            ("location", location),
            ("category", str(category)),
        ]

        # tag 字段可以传多个，所以这里逐个追加
        form_data.extend(("tag", tag) for tag in tags)

        with open(file_path, "rb") as file:
            response = requests.post(
                url,
                headers=self._headers(),
                files={"file": (file_path.name, file, "image/jpeg")},
                data=form_data,
                timeout=self.timeout,
            )

        return {
            "status_code": response.status_code,
            "body": self._parse_json(response),
        }

    def get_my_photos(self, current: int = 1, page_size: int = 10) -> dict[str, Any]:
        """
        查询当前登录用户上传的照片列表。

        接口: GET /api/photos/my-list
        参数:
            current: 第几页，从 1 开始
            page_size: 每页多少条
        """
        url = f"{self.base_url}/api/photos/my-list"
        params = {"current": current, "pageSize": page_size}

        response = requests.get(
            url,
            headers=self._headers(),
            params=params,
            timeout=self.timeout,
        )

        return {
            "status_code": response.status_code,
            "body": self._parse_json(response),
        }

    def delete_photo(self, photo_id: int) -> dict[str, Any]:
        """
        删除指定照片。

        接口: DELETE /api/photos/{id}

        说明:
            当前后端 DELETE 接口可能还不能完全走 JWT，
            所以测试脚本里会“API 失败时再用 SQL 删除”。
        """
        url = f"{self.base_url}/api/photos/{photo_id}"
        response = requests.delete(url, headers=self._headers(), timeout=self.timeout)

        return {
            "status_code": response.status_code,
            "body": self._parse_json(response),
        }

    @staticmethod
    def _parse_json(response: requests.Response) -> dict[str, Any]:
        """
        把响应体解析成 JSON 字典。

        如果后端返回的不是 JSON（比如纯文本报错），
        也会包装成一个统一格式，避免程序直接崩溃。
        """
        try:
            return response.json()
        except ValueError:
            return {
                "code": response.status_code,
                "message": response.text,
                "data": None,
            }
