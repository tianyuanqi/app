"""
测试报告生成模块。

这个文件负责把每一步测试结果收集起来，
最后输出两份报告：
1. JSON 报告（给程序看）
2. HTML 报告（给人看，直接用浏览器打开）
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from datetime import datetime
from html import escape
from pathlib import Path
from typing import Any


@dataclass
class StepResult:
    """
    单个测试步骤的执行结果。

    字段说明:
        name: 步骤名称，例如“2. 登录获取 Token”
        status: PASS 或 FAIL
        duration_ms: 这一步耗时（毫秒）
        details: 这一步的关键信息，例如 userId、photo_id
        error: 如果失败，这里会记录错误原因
    """

    name: str
    status: str
    duration_ms: float
    details: dict[str, Any] = field(default_factory=dict)
    error: str | None = None


class ReportCollector:
    """
    测试报告收集器。

    用法:
        report = ReportCollector("reports")
        report.add_step("登录", "PASS", 120, {"username": "qm"})
        report.write_reports()
    """

    def __init__(self, output_dir: str | Path) -> None:
        # 报告输出目录，不存在就自动创建
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)

        # 存放每一步的结果
        self.steps: list[StepResult] = []

        # 记录整个测试开始时间
        self.started_at = datetime.now()

    def add_step(
        self,
        name: str,
        status: str,
        duration_ms: float,
        details: dict[str, Any] | None = None,
        error: str | None = None,
    ) -> None:
        """追加一步测试结果。"""
        self.steps.append(
            StepResult(
                name=name,
                status=status,
                duration_ms=duration_ms,
                details=details or {},
                error=error,
            )
        )

    def summary(self) -> dict[str, Any]:
        """
        汇总所有步骤，生成总结果。

        返回示例:
        {
            "total_steps": 8,
            "passed": 8,
            "failed": 0,
            "overall_status": "PASS",
            "steps": [...]
        }
        """
        passed = sum(1 for step in self.steps if step.status == "PASS")
        failed = sum(1 for step in self.steps if step.status == "FAIL")

        return {
            "started_at": self.started_at.isoformat(timespec="seconds"),
            "finished_at": datetime.now().isoformat(timespec="seconds"),
            "total_steps": len(self.steps),
            "passed": passed,
            "failed": failed,
            "overall_status": "PASS" if failed == 0 else "FAIL",
            "steps": [asdict(step) for step in self.steps],
        }

    def build_paths(self, prefix: str = "photo_flow") -> dict[str, str]:
        """
        预先生成报告文件路径。

        文件名会带时间戳，避免覆盖历史报告。
        例如:
            photo_flow_20260730_105907.json
            photo_flow_20260730_105907.html
        """
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        json_path = self.output_dir / f"{prefix}_{timestamp}.json"
        html_path = self.output_dir / f"{prefix}_{timestamp}.html"
        return {"json": str(json_path), "html": str(html_path)}

    def write_reports(self, prefix: str = "photo_flow", paths: dict[str, str] | None = None) -> dict[str, str]:
        """
        写出 JSON 和 HTML 报告文件。

        返回:
            实际生成的文件路径
        """
        output_paths = paths or self.build_paths(prefix)
        summary = self.summary()

        # 写 JSON 报告
        with open(output_paths["json"], "w", encoding="utf-8") as file:
            json.dump(summary, file, ensure_ascii=False, indent=2)

        # 写 HTML 报告
        html_content = self._build_html(summary)
        with open(output_paths["html"], "w", encoding="utf-8") as file:
            file.write(html_content)

        return output_paths

    def _build_html(self, summary: dict[str, Any]) -> str:
        """把汇总结果渲染成 HTML 表格页面。"""
        rows = []
        for step in summary["steps"]:
            # escape 的作用：防止特殊字符破坏 HTML 结构
            details_text = escape(json.dumps(step["details"], ensure_ascii=False, indent=2))
            error_text = escape(step["error"] or "-")
            rows.append(
                "<tr>"
                f"<td>{escape(step['name'])}</td>"
                f"<td>{escape(step['status'])}</td>"
                f"<td>{step['duration_ms']:.2f}</td>"
                f"<td><pre>{details_text}</pre></td>"
                f"<td>{error_text}</td>"
                "</tr>"
            )

        return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <title>照片接口自动化 Demo 报告</title>
  <style>
    body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 24px; }}
    h1 {{ margin-bottom: 8px; }}
    .meta {{ color: #555; margin-bottom: 20px; }}
    table {{ border-collapse: collapse; width: 100%; }}
    th, td {{ border: 1px solid #ddd; padding: 8px; vertical-align: top; }}
    th {{ background: #f5f5f5; text-align: left; }}
    pre {{ margin: 0; white-space: pre-wrap; word-break: break-word; }}
    .pass {{ color: #137333; font-weight: 600; }}
    .fail {{ color: #b3261e; font-weight: 600; }}
  </style>
</head>
<body>
  <h1>照片接口自动化 Demo 报告</h1>
  <div class="meta">
    <div>开始时间: {escape(summary["started_at"])}</div>
    <div>结束时间: {escape(summary["finished_at"])}</div>
    <div>总步骤: {summary["total_steps"]}，通过: {summary["passed"]}，失败: {summary["failed"]}</div>
    <div>总体结果:
      <span class="{'pass' if summary['overall_status'] == 'PASS' else 'fail'}">
        {escape(summary["overall_status"])}
      </span>
    </div>
  </div>
  <table>
    <thead>
      <tr>
        <th>步骤</th>
        <th>状态</th>
        <th>耗时(ms)</th>
        <th>关键信息</th>
        <th>错误</th>
      </tr>
    </thead>
    <tbody>
      {''.join(rows)}
    </tbody>
  </table>
</body>
</html>
"""
