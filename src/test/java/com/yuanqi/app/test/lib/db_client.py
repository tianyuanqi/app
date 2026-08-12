"""
MySQL 数据库校验与清理模块。

这个文件负责直接连接 MySQL，做两件事：
1. 校验接口返回的数据，数据库里是否也存在
2. 测试结束后，删除 AUTO_TEST 测试数据
"""

from __future__ import annotations

from typing import Any

import pymysql


class DbClient:
    """
    数据库操作客户端。

    你可以把它理解成：测试脚本里的“数据库小助手”。
    """

    def __init__(self, db_config: dict[str, Any]) -> None:
        """
        参数 db_config 示例:
        {
            "host": "127.0.0.1",
            "port": 3306,
            "user": "root",
            "password": "xxx",
            "database": "blog"
        }
        """
        self.db_config = db_config
        self.connection: pymysql.connections.Connection | None = None

    def connect(self) -> None:
        """建立 MySQL 连接。"""
        self.connection = pymysql.connect(
            host=self.db_config["host"],
            port=int(self.db_config["port"]),
            user=self.db_config["user"],
            password=self.db_config["password"],
            database=self.db_config["database"],
            charset="utf8mb4",
            cursorclass=pymysql.cursors.DictCursor,  # 查询结果返回字典，更易读
            autocommit=True,  # 每条 SQL 自动提交，不用手动 commit
        )

    def close(self) -> None:
        """关闭数据库连接，释放资源。"""
        if self.connection is not None:
            self.connection.close()
            self.connection = None

    def __enter__(self) -> "DbClient":
        """
        支持 with 语法:
            with DbClient(cfg) as db:
                ...
        """
        self.connect()
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        """with 代码块结束时自动关闭连接。"""
        self.close()

    def _query_one(self, sql: str, params: tuple[Any, ...] = ()) -> dict[str, Any] | None:
        """执行 SQL，返回第一行结果（字典）。"""
        assert self.connection is not None, "数据库尚未连接"
        with self.connection.cursor() as cursor:
            cursor.execute(sql, params)
            return cursor.fetchone()

    def _query_scalar(self, sql: str, params: tuple[Any, ...] = ()) -> Any:
        """执行 SQL，只返回第一行第一列，例如 COUNT(*) 的结果。"""
        row = self._query_one(sql, params)
        if not row:
            return None
        return next(iter(row.values()))

    def get_user_id_by_username(self, username: str) -> int | None:
        """
        根据用户名查询用户 ID。

        对应表: t_user
        """
        sql = "SELECT id FROM t_user WHERE username = %s LIMIT 1"
        value = self._query_scalar(sql, (username,))
        return int(value) if value is not None else None

    def count_auto_test_photos(self, user_id: int, title_prefix: str) -> int:
        """
        统计某个用户名下，标题以 AUTO_TEST_ 开头的照片数量。

        对应表: photo_info
        """
        sql = (
            "SELECT COUNT(*) AS cnt FROM photo_info "
            "WHERE user_id = %s AND title LIKE %s"
        )
        value = self._query_scalar(sql, (user_id, f"{title_prefix}%"))
        return int(value or 0)

    def photo_exists(self, photo_id: int, user_id: int) -> bool:
        """
        检查某张照片是否存在于数据库，且确实属于该用户。
        """
        sql = (
            "SELECT id FROM photo_info "
            "WHERE id = %s AND user_id = %s LIMIT 1"
        )
        return self._query_one(sql, (photo_id, user_id)) is not None

    def delete_photo_cascade(self, photo_id: int, user_id: int) -> None:
        """
        删除一张照片及其标签关联。

        删除顺序:
        1. 先删 t_photo_tag（中间表）
        2. 再删 photo_info（主表）

        这样不会出现“主记录删了，关联记录还在”的脏数据。
        """
        assert self.connection is not None, "数据库尚未连接"
        with self.connection.cursor() as cursor:
            cursor.execute("DELETE FROM t_photo_tag WHERE photo_id = %s", (photo_id,))
            cursor.execute(
                "DELETE FROM photo_info WHERE id = %s AND user_id = %s",
                (photo_id, user_id),
            )

    def cleanup_auto_test_photos(self, user_id: int, title_prefix: str) -> int:
        """
        批量清理某个用户所有 AUTO_TEST_ 开头的照片。

        返回:
            实际删除的照片数量
        """
        assert self.connection is not None, "数据库尚未连接"
        with self.connection.cursor() as cursor:
            cursor.execute(
                "SELECT id FROM photo_info WHERE user_id = %s AND title LIKE %s",
                (user_id, f"{title_prefix}%"),
            )
            rows = cursor.fetchall()
            photo_ids = [row["id"] for row in rows]

            for photo_id in photo_ids:
                cursor.execute("DELETE FROM t_photo_tag WHERE photo_id = %s", (photo_id,))
                cursor.execute(
                    "DELETE FROM photo_info WHERE id = %s AND user_id = %s",
                    (photo_id, user_id),
                )

            return len(photo_ids)
