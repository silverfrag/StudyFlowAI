import asyncio
import json
import logging
import re
from typing import Any, Dict, List, Optional, Type, TypeVar

from google import genai
from google.genai import types
from pydantic import BaseModel, ValidationError

from app.core.config import settings

logger = logging.getLogger(__name__)

T = TypeVar("T", bound=BaseModel)

_LEGACY_MODEL_MAP = {
    "gemini-1.5-flash": "gemini-3.5-flash-lite",
    "gemini-1.5-flash-latest": "gemini-3.5-flash-lite",
    "gemini-1.5-pro": "gemini-3.5-flash-lite",
    "gemini-2.0-flash": "gemini-3.5-flash-lite",
    "gemini-2.0-flash-lite": "gemini-3.5-flash-lite",
    "gemini-2.5-flash": "gemini-3.5-flash-lite",
}

_DEFAULT_GENERATION_MODEL = "gemini-3.5-flash-lite"

_LEGACY_EMBEDDING_MAP = {
    "text-embedding-004": "gemini-embedding-001",
    "models/text-embedding-004": "gemini-embedding-001",
}

_UNSUPPORTED_SCHEMA_KEYS = {
    "title",
    "default",
    "minLength",
    "maxLength",
    "minProperties",
    "maxProperties",
    "uniqueItems",
    "$defs",
}


class AIServiceError(Exception):
    """Lỗi khi gọi Gemini Developer API hoặc xử lý phản hồi cấu trúc."""


def is_gemini_configured() -> bool:
    key = (settings.GEMINI_API_KEY or "").strip()
    return bool(key) and key != "your_gemini_api_key_here"


def resolve_generation_model() -> str:
    configured = (settings.GEMINI_MODEL or "").strip()
    mapped = _LEGACY_MODEL_MAP.get(configured, configured or _DEFAULT_GENERATION_MODEL)
    if mapped != configured and configured:
        logger.warning("Model '%s' đã cũ/không còn hỗ trợ; tự động chuyển sang '%s'.", configured, mapped)
    return mapped


def resolve_generation_candidates() -> List[str]:
    primary = resolve_generation_model()
    raw_fallbacks = getattr(settings, "GEMINI_FALLBACK_MODELS", "") or ""
    candidates: List[str] = [primary]
    for item in raw_fallbacks.split(","):
        cleaned = _LEGACY_MODEL_MAP.get(item.strip(), item.strip())
        if cleaned and cleaned not in candidates:
            candidates.append(cleaned)
    return candidates


def resolve_embedding_model() -> str:
    configured = (settings.GEMINI_EMBEDDING_MODEL or "").strip()
    mapped = _LEGACY_EMBEDDING_MAP.get(configured, configured or "gemini-embedding-001")
    if mapped != configured and configured:
        logger.warning("Embedding model '%s' đã ngừng hoạt động; tự động chuyển sang '%s'.", configured, mapped)
    return mapped


def get_genai_client() -> genai.Client:
    if not is_gemini_configured():
        raise AIServiceError(
            "Backend chưa cấu hình GEMINI_API_KEY hợp lệ trong file .env."
        )
    return genai.Client(api_key=settings.GEMINI_API_KEY.strip())


def inline_json_schema(schema: Dict[str, Any]) -> Dict[str, Any]:
    """Đệ quy thay thế $ref/$defs và loại bỏ từ khóa không cần thiết cho Gemini structured output."""
    defs = schema.get("$defs", {})

    def _resolve(node: Any) -> Any:
        if isinstance(node, list):
            return [_resolve(item) for item in node]
        if not isinstance(node, dict):
            return node

        if "$ref" in node:
            ref_path = str(node["$ref"])
            ref_name = ref_path.rsplit("/", 1)[-1]
            target = defs.get(ref_name, {})
            merged = dict(target)
            for k, v in node.items():
                if k != "$ref":
                    merged[k] = v
            return _resolve(merged)

        # Xử lý Optional (anyOf: [T, {"type": "null"}]) thành JSON Schema union type: [T, "null"]
        if "anyOf" in node and isinstance(node["anyOf"], list):
            non_null = [item for item in node["anyOf"] if not (isinstance(item, dict) and item.get("type") == "null")]
            has_null = len(non_null) < len(node["anyOf"])
            if len(non_null) == 1:
                resolved = _resolve(non_null[0])
                if isinstance(resolved, dict) and has_null and isinstance(resolved.get("type"), str):
                    resolved = dict(resolved)
                    resolved["type"] = [resolved["type"], "null"]
                    return resolved
                if not has_null:
                    return resolved

        cleaned: Dict[str, Any] = {}
        for k, v in node.items():
            if k in _UNSUPPORTED_SCHEMA_KEYS:
                continue
            cleaned[k] = _resolve(v)
        return cleaned

    return _resolve(schema)


def _strip_json_fences(raw: str) -> str:
    text = raw.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text, flags=re.IGNORECASE)
        text = re.sub(r"\s*```$", "", text)
    return text.strip()


def extract_interaction_text(interaction: Any) -> str:
    text = getattr(interaction, "output_text", None)
    if isinstance(text, str) and text.strip():
        return text

    steps = getattr(interaction, "steps", None) or []
    parts: List[str] = []
    for step in steps:
        if getattr(step, "type", None) == "model_output":
            for item in getattr(step, "content", None) or []:
                item_text = getattr(item, "text", None)
                if isinstance(item_text, str):
                    parts.append(item_text)
    return "".join(parts)


async def embed_texts(
    texts: List[str],
    task_type: str = "RETRIEVAL_DOCUMENT",
    dimensions: int = 768,
    batch_size: int = 32,
) -> List[List[float]]:
    """Tạo vector embedding 768 chiều bằng Gemini Embedding API."""
    if not texts:
        return []

    client = get_genai_client()
    model = resolve_embedding_model()
    vectors: List[List[float]] = []

    for start in range(0, len(texts), batch_size):
        batch = texts[start : start + batch_size]
        try:
            response = await asyncio.wait_for(
                client.aio.models.embed_content(
                    model=model,
                    contents=batch,
                    config=types.EmbedContentConfig(
                        output_dimensionality=dimensions,
                        task_type=task_type,
                    ),
                ),
                timeout=settings.GEMINI_TIMEOUT_SECONDS,
            )
        except asyncio.TimeoutError as exc:
            logger.warning("Gemini embed_content timeout sau %.1fs", settings.GEMINI_TIMEOUT_SECONDS)
            raise AIServiceError("Dịch vụ AI xử lý embedding quá thời gian phản hồi.") from exc
        except AIServiceError:
            raise
        except Exception as exc:
            logger.warning("Lỗi gọi Gemini embed_content: %s", type(exc).__name__)
            raise AIServiceError("Không thể tạo vector embedding từ dịch vụ Gemini AI.") from exc

        embeddings = getattr(response, "embeddings", None) or []
        if len(embeddings) != len(batch):
            raise AIServiceError("Số lượng vector embedding trả về từ AI không khớp dữ liệu gửi lên.")

        for emb in embeddings:
            vals = list(getattr(emb, "values", None) or [])
            if len(vals) != dimensions:
                raise AIServiceError(
                    f"Kích thước vector trả về ({len(vals)}) không khớp cấu hình pgvector ({dimensions})."
                )
            vectors.append(vals)

    return vectors


async def generate_structured_json(
    prompt: str,
    system_instruction: str,
    schema_cls: Type[T],
) -> T:
    """Gọi Gemini Interactions API ép đầu ra JSON theo schema và validate bằng Pydantic (kèm fallback khi chậm)."""
    client = get_genai_client()
    candidates = resolve_generation_candidates()
    json_schema = inline_json_schema(schema_cls.model_json_schema())

    last_error_message = "Dịch vụ Gemini AI hiện không khả dụng hoặc hết hạn mức."
    for idx, model in enumerate(candidates):
        has_fallback = idx < len(candidates) - 1
        per_attempt_timeout = (
            min(float(settings.GEMINI_TIMEOUT_SECONDS), 18.0)
            if has_fallback
            else float(settings.GEMINI_TIMEOUT_SECONDS)
        )
        try:
            interaction = await asyncio.wait_for(
                client.aio.interactions.create(
                    model=model,
                    system_instruction=system_instruction,
                    input=prompt,
                    store=False,
                    response_format={
                        "type": "text",
                        "mime_type": "application/json",
                        "schema": json_schema,
                    },
                ),
                timeout=per_attempt_timeout,
            )
        except asyncio.TimeoutError as exc:
            logger.warning(
                "Gemini interactions.create (%s) timeout sau %.1fs",
                model,
                per_attempt_timeout,
            )
            last_error_message = "Dịch vụ AI phản hồi quá chậm. Vui lòng thử lại sau."
            if has_fallback:
                continue
            raise AIServiceError(last_error_message) from exc
        except AIServiceError:
            raise
        except Exception as exc:
            logger.warning("Lỗi gọi Gemini interactions.create (%s): %s", model, type(exc).__name__)
            last_error_message = "Dịch vụ Gemini AI hiện không khả dụng hoặc hết hạn mức."
            if has_fallback:
                continue
            raise AIServiceError(last_error_message) from exc

        interaction_status = getattr(interaction, "status", None)
        if interaction_status in ("failed", "cancelled"):
            logger.warning("Gemini interaction (%s) kết thúc với status=%s", model, interaction_status)
            last_error_message = "Dịch vụ AI xử lý yêu cầu không thành công. Vui lòng thử lại."
            if has_fallback:
                continue
            raise AIServiceError(last_error_message)

        raw_text = extract_interaction_text(interaction)
        if not raw_text.strip():
            last_error_message = "Dịch vụ AI không trả về nội dung hợp lệ."
            if has_fallback:
                continue
            raise AIServiceError(last_error_message)

        cleaned_json = _strip_json_fences(raw_text)
        try:
            return schema_cls.model_validate_json(cleaned_json)
        except (ValidationError, json.JSONDecodeError) as exc:
            logger.warning("Gemini JSON output (%s) không khớp schema %s: %s", model, schema_cls.__name__, type(exc).__name__)
            last_error_message = "Dữ liệu AI trả về không đúng định dạng cấu trúc yêu cầu."
            if has_fallback:
                continue
            raise AIServiceError(last_error_message) from exc

    raise AIServiceError(last_error_message)
