"""
MinerU FastAPI 解析服务
供 EduMind Spring Boot 后端调用，接收 PDF 路径，返回 Markdown 文本。

模型自动管理：首次启动从 ModelScope 下载（约 3GB），通过 Docker volume 持久化。
"""
import logging
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("mineru-api")

SUPPORTED_SUFFIXES = {".pdf", ".png", ".jpg", ".jpeg", ".docx", ".pptx", ".xlsx"}

app = FastAPI(title="MinerU Document Parser", version="2.0")

# 全局就绪状态 — 模型校验完成后才接受解析请求
_ready = False


@app.on_event("startup")
async def startup_check():
    """启动时探测 mineru 命令，触发模型下载（如需要）。"""
    global _ready
    try:
        # 简单调用 --version，确认 CLI 可用
        proc = await subprocess.create_subprocess_exec(
            "mineru", "--version",
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        stdout, stderr = await proc.communicate()
        if proc.returncode == 0:
            _ready = True
            log.info("MinerU CLI 就绪: %s", stdout.decode(errors="replace").strip()[:200])
        else:
            log.warning("MinerU CLI 返回非零: %s", stderr.decode(errors="replace")[:500])
    except FileNotFoundError:
        log.error("mineru 命令未找到！请检查 pip install 'mineru[core]' 是否成功")
    except Exception as e:
        log.warning("MinerU 启动探测失败（模型可能未下载，将在首次请求时自动下载）: %s", e)
        _ready = True  # 允许按需加载


class ParseRequest(BaseModel):
    file_path: str  # 绝对路径（通过 Docker volume 共享）


class ParseResponse(BaseModel):
    status: str          # "ok" | "error"
    markdown: str        # 解析后的 Markdown 全文
    char_count: int      # 字符数
    page_count: int      # PDF 页数
    error: str | None = None


async def _run_mineru(pdf_path: Path, output_dir: Path) -> tuple[Path, int]:
    """调用 MinerU CLI，返回 (md_file_path, page_count)"""
    import subprocess
    import re

    cmd = [
        "mineru",
        "-p", str(pdf_path),
        "-o", str(output_dir),
        "-m", "auto",          # 自动检测最佳模式（OCR/文本）
    ]

    log.info("执行: %s", " ".join(cmd))
    proc = await subprocess.create_subprocess_exec(
        *cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    stdout, stderr = await proc.communicate()

    if proc.returncode != 0:
        err = stderr.decode("utf-8", errors="replace")[:2000]
        raise RuntimeError(f"MinerU 退出码 {proc.returncode}: {err}")

    # MinerU 输出目录结构: output_dir/<basename>/<basename>.md
    basename = pdf_path.stem
    md_file = output_dir / basename / f"{basename}.md"
    if not md_file.exists():
        # 尝试其他可能的输出位置
        candidates = list(output_dir.rglob("*.md"))
        if candidates:
            md_file = candidates[0]
        else:
            raise RuntimeError(f"找不到 MinerU 输出文件，目录: {output_dir}")

    # 统计页数
    page_count = 0
    for f in output_dir.rglob("*"):
        if f.suffix.lower() in (".png", ".jpg"):
            page_count += 1
    if page_count == 0:
        page_count = 1  # 至少 1 页

    return md_file, page_count


@app.post("/parse", response_model=ParseResponse)
async def parse_pdf(req: ParseRequest):
    """
    解析 PDF 文件，返回 Markdown 全文。
    调用方传入文件在宿主机/共享卷上的绝对路径。
    """
    file_path = Path(req.file_path)
    if not file_path.exists():
        raise HTTPException(status_code=404, detail=f"文件不存在: {req.file_path}")
    if file_path.suffix.lower() not in SUPPORTED_SUFFIXES:
        raise HTTPException(status_code=400,
            detail=f"不支持的文件格式: {file_path.suffix}，仅支持 {', '.join(sorted(SUPPORTED_SUFFIXES))}")

    log.info("开始解析: %s (%.1f MB)", file_path.name, file_path.stat().st_size / 1024 / 1024)

    output_dir = Path(tempfile.mkdtemp(prefix="mineru_"))

    try:
        md_file, page_count = await _run_mineru(file_path, output_dir)
        markdown = md_file.read_text(encoding="utf-8")

        log.info(
            "解析完成: %s → %d 字符, %d 页",
            file_path.name, len(markdown), page_count,
        )

        return ParseResponse(
            status="ok",
            markdown=markdown,
            char_count=len(markdown),
            page_count=page_count,
        )

    except Exception as e:
        log.exception("解析失败: %s", file_path.name)
        return ParseResponse(
            status="error",
            markdown="",
            char_count=0,
            page_count=0,
            error=str(e),
        )
    finally:
        shutil.rmtree(output_dir, ignore_errors=True)


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/ready")
async def ready():
    """就绪探针：模型已加载，可以接收解析请求。"""
    if _ready:
        return {"status": "ready"}
    raise HTTPException(status_code=503, detail="MinerU 模型未就绪，请稍后重试")
