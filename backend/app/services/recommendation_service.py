import asyncio
import logging
import re
from collections import Counter
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple
from uuid import UUID

from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.course import Course
from app.models.document import Document, DocumentChunk
from app.models.quiz import Answer, Question, Quiz, QuizAttempt
from app.schemas.progress import RecommendationItem, SuggestedDocument
from app.services.gemini_service import (
    AIServiceError,
    generate_structured_json,
    is_gemini_configured,
)

logger = logging.getLogger(__name__)

MIN_ANSWERS_FOR_WEAK_TOPIC = 2
WEAK_ACCURACY_THRESHOLD = 0.60
MAX_RECOMMENDATION_CACHE_ITEMS = 256

_RECOMMENDATION_CACHE: Dict[Tuple, str] = {}
_QUIZ_ADVICE_CACHE: Dict[Tuple, str] = {}

RECOMMENDATION_SYSTEM_INSTRUCTION = (
    "Bạn là cố vấn học tập AI của StudyFlow. Nhiệm vụ của bạn là viết lời khuyên ôn tập ngắn gọn, "
    "cụ thể và tự nhiên bằng tiếng Việt cho từng chủ đề mà sinh viên còn yếu dựa trên kết quả làm quiz.\n"
    "Quy tắc bắt buộc:\n"
    "1. Chỉ bám sát dữ liệu câu hỏi làm sai, lời giải thích, tên tài liệu và số trang được cung cấp; "
    "tuyệt đối không tự bịa thêm điểm yếu hay số trang không có trong dữ liệu.\n"
    "2. Phải chỉ rõ khái niệm/lỗi cụ thể cần xem lại (nếu có) và gợi ý đọc đúng phần tài liệu/trang tương ứng.\n"
    "3. Không dùng các câu sáo rỗng, rập khuôn lặp lại giữa các mục kiểu "
    "'Xem lại tài liệu liên quan đến chủ đề ... và làm bài quiz luyện tập củng cố'.\n"
    "4. Mỗi mục dài 1–2 câu súc tích (tối đa 220 ký tự), diễn đạt tự nhiên và đa dạng giữa các chủ đề."
)

QUIZ_RESULT_ADVICE_SYSTEM_INSTRUCTION = (
    "Bạn là cố vấn học tập AI của StudyFlow. Hãy viết 1 đoạn gợi ý ôn tập ngắn (2–3 câu, tối đa 280 ký tự) "
    "bằng tiếng Việt ngay sau khi sinh viên vừa nộp một bài quiz.\n"
    "Quy tắc bắt buộc:\n"
    "1. Tóm tắt trúng trọng tâm kiến thức sinh viên vừa làm sai/bỏ trống dựa đúng trên danh sách câu sai và nguồn tài liệu được cung cấp.\n"
    "2. Nhắc cụ thể tên tài liệu và số trang cần đọc lại nếu có; tuyệt đối không bịa thêm nội dung ngoài dữ liệu.\n"
    "3. Văn phong khích lệ, tự nhiên, tránh khuôn mẫu lặp lại."
)


class _TopicAdviceLLM(BaseModel):
    topic_index: int = Field(..., description="Chỉ số nguyên của chủ đề cần tư vấn.")
    advice: str = Field(..., description="Lời khuyên ôn tập cụ thể 1-2 câu bằng tiếng Việt.")


class _RecommendationAdviceBatchOutput(BaseModel):
    items: List[_TopicAdviceLLM]


class _QuizStudyAdviceLLM(BaseModel):
    advice: str = Field(..., description="Gợi ý ôn tập súc tích 2-3 câu dựa trên các câu làm sai.")


@dataclass
class _WrongEvidence:
    question_id: UUID
    prompt: str
    explanation: str
    document_id: Optional[UUID]
    document_title: Optional[str]
    document_status: Optional[str]
    page_number: Optional[int]


@dataclass
class _WeakTopicCandidate:
    course_id: UUID
    course_name: str
    topic: str
    total_answered: int
    correct_count: int
    accuracy: float
    wrong_evidences: List[_WrongEvidence]
    suggested_doc: Optional[SuggestedDocument]
    pages: List[int]
    cache_key: Tuple


def canonicalize_topic_display(topic: str) -> str:
    return re.sub(r"\s+", " ", (topic or "").strip())


def normalize_topic_group_key(topic: str) -> str:
    cleaned = canonicalize_topic_display(topic).lower()
    collapsed = re.sub(r"[\s()\[\],.;:_\-/]+", " ", cleaned).strip()
    return collapsed or cleaned


def _trim_phrase(text: str, max_len: int = 90) -> str:
    cleaned = re.sub(r"\s+", " ", (text or "").strip())
    if len(cleaned) <= max_len:
        return cleaned
    window = cleaned[:max_len]
    # Ưu tiên cắt tại dấu kết thúc câu hoàn chỉnh nếu nằm ở nửa sau đoạn
    last_punct = max(window.rfind(". "), window.rfind("! "), window.rfind("? "))
    if last_punct >= max_len // 2:
        return window[: last_punct + 1].strip()
    cut = window.rsplit(" ", 1)[0].strip()
    return f"{cut or window}..."


def _extract_focus_clue(evidences: List[_WrongEvidence]) -> str:
    for ev in evidences:
        exp = re.sub(r"\s+", " ", (ev.explanation or "").strip())
        if exp:
            lowered = exp.lower()
            is_bare_generic = lowered.startswith("đáp án đúng là") and "theo nội dung tài liệu môn học" in lowered
            if not is_bare_generic:
                first_sentence = re.split(r"(?<=[.!?])\s+", exp, maxsplit=1)[0].strip()
                if len(first_sentence) >= 12:
                    return _trim_phrase(first_sentence.rstrip(".!"), 110)
        prompt = re.sub(r"\s+", " ", (ev.prompt or "").strip())
        if prompt:
            return _trim_phrase(prompt.rstrip("?"), 100)
    return ""


def _format_doc_reference(doc_title: Optional[str], pages: List[int]) -> str:
    if not doc_title:
        return "tài liệu của môn học"
    if pages:
        page_str = ", ".join(str(p) for p in pages[:3])
        return f"'{doc_title}' (Trang {page_str})"
    return f"'{doc_title}'"


def _build_contextual_fallback_action(cand: _WeakTopicCandidate, variant_idx: int = 0) -> str:
    focus_clue = _extract_focus_clue(cand.wrong_evidences)
    doc_ref = _format_doc_reference(
        cand.suggested_doc.title if cand.suggested_doc else None,
        cand.pages,
    )
    wrong_count = cand.total_answered - cand.correct_count

    if cand.correct_count == 0 and focus_clue:
        patterns = [
            f"Bạn chưa đúng câu nào ({wrong_count}/{cand.total_answered} câu sai) ở '{cand.topic}', đặc biệt: {focus_clue}. Xem lại {doc_ref} trước khi luyện tiếp.",
            f"Chủ đề '{cand.topic}' hiện 0/{cand.total_answered} đúng; điểm cần nắm lại là: {focus_clue}. Mở {doc_ref} để đối chiếu lại kiến thức.",
            f"Cả {cand.total_answered} câu thuộc '{cand.topic}' đều chưa đạt (trọng tâm: {focus_clue}). Hãy đọc kỹ {doc_ref} rồi làm quiz củng cố.",
        ]
        return _trim_phrase(patterns[variant_idx % len(patterns)], 260)
    if focus_clue:
        patterns = [
            f"Mới đúng {cand.correct_count}/{cand.total_answered} câu ở '{cand.topic}', trọng tâm cần củng cố: {focus_clue}. Xem lại {doc_ref} rồi thử làm lại.",
            f"Kết quả ở '{cand.topic}' đạt {cand.correct_count}/{cand.total_answered}; nên rà soát lại phần: {focus_clue} trong {doc_ref}.",
        ]
        return _trim_phrase(patterns[variant_idx % len(patterns)], 260)
    if cand.suggested_doc:
        return _trim_phrase(
            f"Tỷ lệ đúng hiện tại là {cand.correct_count}/{cand.total_answered} ở '{cand.topic}'. "
            f"Nên rà soát nội dung liên quan trong {doc_ref} và làm quiz luyện tập ngắn.",
            260,
        )
    return _trim_phrase(
        f"Bạn mới đạt {cand.correct_count}/{cand.total_answered} câu ở '{cand.topic}'; hãy ôn lại kiến thức nền và thử bài quiz 5 câu.",
        260,
    )


def _store_cache(cache: Dict[Tuple, str], key: Tuple, value: str) -> None:
    if len(cache) >= MAX_RECOMMENDATION_CACHE_ITEMS:
        oldest_key = next(iter(cache))
        cache.pop(oldest_key, None)
    cache[key] = value


async def _enrich_actions_with_llm(candidates: List[_WeakTopicCandidate]) -> Dict[int, str]:
    missing_indices = [
        idx for idx, cand in enumerate(candidates)
        if cand.cache_key not in _RECOMMENDATION_CACHE
    ]
    if not missing_indices or not is_gemini_configured():
        return {}

    prompt_blocks: List[str] = []
    for idx in missing_indices:
        cand = candidates[idx]
        pct = int(round(cand.accuracy * 100))
        doc_ref = _format_doc_reference(
            cand.suggested_doc.title if cand.suggested_doc else None,
            cand.pages,
        )
        clue_lines = []
        for ev_idx, ev in enumerate(cand.wrong_evidences[:2], start=1):
            p_short = _trim_phrase(ev.prompt, 110)
            e_short = _trim_phrase(ev.explanation, 120)
            clue_lines.append(f"  - Câu sai {ev_idx}: {p_short} | Điểm đúng: {e_short}")
        clues_text = "\n".join(clue_lines) if clue_lines else "  - Chưa có mô tả câu sai chi tiết."
        prompt_blocks.append(
            f"[CHỦ ĐỀ {idx}] Môn: {cand.course_name} | Topic: {cand.topic} | "
            f"Kết quả: {cand.correct_count}/{cand.total_answered} ({pct}%) | Tài liệu gợi ý: {doc_ref}\n"
            f"{clues_text}"
        )

    user_prompt = (
        "Hãy viết gợi ý ôn tập cá nhân hóa cho các chủ đề yếu sau:\n\n"
        + "\n\n".join(prompt_blocks)
    )

    generated: Dict[int, str] = {}
    try:
        llm_res = await asyncio.wait_for(
            generate_structured_json(
                prompt=user_prompt,
                system_instruction=RECOMMENDATION_SYSTEM_INSTRUCTION,
                schema_cls=_RecommendationAdviceBatchOutput,
                timeout_seconds=7.0,
            ),
            timeout=12.0,
        )
        valid_set = set(missing_indices)
        for item in llm_res.items:
            if item.topic_index in valid_set:
                cleaned = _trim_phrase(item.advice, 260)
                if len(cleaned) >= 15:
                    generated[item.topic_index] = cleaned
                    _store_cache(_RECOMMENDATION_CACHE, candidates[item.topic_index].cache_key, cleaned)
    except (AIServiceError, asyncio.TimeoutError, Exception) as exc:
        logger.warning("Không thể sinh lời khuyên ôn tập cá nhân hóa bằng Gemini: %s", type(exc).__name__)
    return generated


async def build_recommendations(db: AsyncSession) -> List[RecommendationItem]:
    """Tổng hợp topic yếu xác định từ lịch sử làm quiz và tạo gợi ý ôn tập sát dữ liệu."""
    stmt = (
        select(
            Quiz.course_id,
            Course.name.label("course_name"),
            Question.id.label("question_id"),
            Question.topic,
            Question.prompt,
            Question.explanation,
            Answer.is_correct,
            QuizAttempt.submitted_at,
            Document.id.label("doc_id"),
            Document.title.label("doc_title"),
            Document.status.label("doc_status"),
            DocumentChunk.page_number,
        )
        .join(Question, Answer.question_id == Question.id)
        .join(Quiz, Question.quiz_id == Quiz.id)
        .join(Course, Quiz.course_id == Course.id)
        .join(QuizAttempt, Answer.attempt_id == QuizAttempt.id)
        .outerjoin(DocumentChunk, Question.source_chunk_id == DocumentChunk.id)
        .outerjoin(Document, DocumentChunk.document_id == Document.id)
        .order_by(QuizAttempt.submitted_at.desc())
    )
    rows = (await db.execute(stmt)).all()
    if not rows:
        return []

    grouped: Dict[Tuple[UUID, str], Dict] = {}
    for row in rows:
        display_topic = canonicalize_topic_display(row.topic)
        if not display_topic:
            continue
        group_key = (row.course_id, normalize_topic_group_key(display_topic))
        bucket = grouped.get(group_key)
        if bucket is None:
            bucket = {
                "course_id": row.course_id,
                "course_name": row.course_name or "Môn học",
                "topic_counts": Counter(),
                "total_answered": 0,
                "correct_count": 0,
                "wrong_evidences": [],
                "seen_wrong_q_ids": set(),
                "all_doc_hits": Counter(),
                "doc_titles": {},
            }
            grouped[group_key] = bucket

        bucket["topic_counts"][display_topic] += 1
        bucket["total_answered"] += 1
        if row.is_correct:
            bucket["correct_count"] += 1
        else:
            if row.question_id not in bucket["seen_wrong_q_ids"]:
                bucket["seen_wrong_q_ids"].add(row.question_id)
                bucket["wrong_evidences"].append(
                    _WrongEvidence(
                        question_id=row.question_id,
                        prompt=row.prompt or "",
                        explanation=row.explanation or "",
                        document_id=row.doc_id,
                        document_title=row.doc_title,
                        document_status=row.doc_status,
                        page_number=row.page_number,
                    )
                )

        if row.doc_id is not None and row.doc_status == "ready" and row.doc_title:
            weight = 3 if not row.is_correct else 1
            bucket["all_doc_hits"][row.doc_id] += weight
            bucket["doc_titles"][row.doc_id] = row.doc_title

    latest_ready_doc_cache: Dict[UUID, Optional[SuggestedDocument]] = {}
    candidates: List[_WeakTopicCandidate] = []

    for bucket in grouped.values():
        total_answered = bucket["total_answered"]
        correct_count = bucket["correct_count"]
        if total_answered < MIN_ANSWERS_FOR_WEAK_TOPIC:
            continue

        accuracy = round(float(correct_count) / float(total_answered), 2)
        if accuracy >= WEAK_ACCURACY_THRESHOLD:
            continue

        canonical_topic = bucket["topic_counts"].most_common(1)[0][0]
        course_id = bucket["course_id"]

        suggested_doc: Optional[SuggestedDocument] = None
        if bucket["all_doc_hits"]:
            best_doc_id = bucket["all_doc_hits"].most_common(1)[0][0]
            suggested_doc = SuggestedDocument(
                id=best_doc_id,
                title=bucket["doc_titles"][best_doc_id],
            )
        else:
            if course_id not in latest_ready_doc_cache:
                doc_res = await db.execute(
                    select(Document)
                    .where(Document.course_id == course_id, Document.status == "ready")
                    .order_by(Document.created_at.desc())
                    .limit(1)
                )
                fallback_doc = doc_res.scalar_one_or_none()
                latest_ready_doc_cache[course_id] = (
                    SuggestedDocument(id=fallback_doc.id, title=fallback_doc.title)
                    if fallback_doc
                    else None
                )
            suggested_doc = latest_ready_doc_cache[course_id]

        pages_set = sorted(
            {
                ev.page_number
                for ev in bucket["wrong_evidences"]
                if ev.page_number is not None
                and (suggested_doc is None or ev.document_id == suggested_doc.id)
            }
        )
        wrong_sig = tuple(sorted(str(ev.question_id) for ev in bucket["wrong_evidences"][:5]))
        cache_key = (
            str(course_id),
            canonical_topic,
            total_answered,
            correct_count,
            wrong_sig,
            suggested_doc.title if suggested_doc else "",
            tuple(pages_set[:4]),
        )

        candidates.append(
            _WeakTopicCandidate(
                course_id=course_id,
                course_name=bucket["course_name"],
                topic=canonical_topic,
                total_answered=total_answered,
                correct_count=correct_count,
                accuracy=accuracy,
                wrong_evidences=bucket["wrong_evidences"],
                suggested_doc=suggested_doc,
                pages=pages_set,
                cache_key=cache_key,
            )
        )

    candidates.sort(
        key=lambda c: (
            c.accuracy,
            -(c.total_answered - c.correct_count),
            -c.total_answered,
            c.topic.lower(),
        )
    )

    if not candidates:
        return []

    llm_actions = await _enrich_actions_with_llm(candidates)
    results: List[RecommendationItem] = []
    for idx, cand in enumerate(candidates):
        action_text = (
            _RECOMMENDATION_CACHE.get(cand.cache_key)
            or llm_actions.get(idx)
            or _build_contextual_fallback_action(cand, variant_idx=idx)
        )
        results.append(
            RecommendationItem(
                topic=cand.topic,
                course_id=cand.course_id,
                course_name=cand.course_name,
                total_answered=cand.total_answered,
                correct_count=cand.correct_count,
                accuracy=cand.accuracy,
                recommended_action=action_text,
                suggested_document=cand.suggested_doc,
            )
        )

    return results


async def build_quiz_submit_study_advice(
    quiz_title: str,
    score: float,
    total_questions: int,
    wrong_items: List[Dict],
) -> Optional[str]:
    """Tạo lời khuyên ôn tập sát câu sai ngay trên màn Kết quả học tập (có fallback an toàn)."""
    if not wrong_items:
        return (
            f"Xuất sắc! Bạn đã hoàn thành đúng toàn bộ {total_questions}/{total_questions} câu "
            f"của '{quiz_title}'. Hãy thử hỏi AI chuyên sâu hoặc tạo quiz ở chủ đề tiếp theo."
        )

    sig = (
        quiz_title,
        round(score, 1),
        total_questions,
        tuple((item.get("topic", ""), item.get("prompt", "")[:40]) for item in wrong_items[:4]),
    )
    cached = _QUIZ_ADVICE_CACHE.get(sig)
    if cached:
        return cached

    if is_gemini_configured():
        summary_lines = []
        for i, item in enumerate(wrong_items[:4], start=1):
            src = item.get("doc_title")
            pg = item.get("page_number")
            src_label = (
                f"{src} (Trang {pg})" if src and pg is not None
                else (src or "Tài liệu môn học")
            )
            summary_lines.append(
                f"{i}. Chủ đề: {item.get('topic', '')} | Nguồn: {src_label}\n"
                f"   Câu hỏi: {_trim_phrase(item.get('prompt', ''), 110)}\n"
                f"   Giải thích đúng: {_trim_phrase(item.get('explanation', ''), 120)}"
            )
        prompt = (
            f"Bài quiz: {quiz_title} | Điểm: {score}/10 (sai/bỏ trống {len(wrong_items)}/{total_questions} câu).\n"
            f"Các câu chưa đạt:\n" + "\n".join(summary_lines)
        )
        try:
            llm_out = await asyncio.wait_for(
                generate_structured_json(
                    prompt=prompt,
                    system_instruction=QUIZ_RESULT_ADVICE_SYSTEM_INSTRUCTION,
                    schema_cls=_QuizStudyAdviceLLM,
                    timeout_seconds=7.0,
                ),
                timeout=11.0,
            )
            cleaned = _trim_phrase(llm_out.advice, 340)
            if len(cleaned) >= 15:
                _store_cache(_QUIZ_ADVICE_CACHE, sig, cleaned)
                return cleaned
        except (AIServiceError, asyncio.TimeoutError, Exception) as exc:
            logger.warning("Fallback study advice cho quiz submit vì lỗi Gemini: %s", type(exc).__name__)

    # Fallback sát dữ liệu thực tế (nêu rõ nội dung câu sai + tài liệu/trang cụ thể)
    first = wrong_items[0]
    topics_ordered = []
    for item in wrong_items:
        t = (item.get("topic") or "").strip()
        if t and t not in topics_ordered:
            topics_ordered.append(t)

    doc_refs: List[str] = []
    for item in wrong_items:
        title = item.get("doc_title")
        page = item.get("page_number")
        if title:
            label = f"'{title}' (Trang {page})" if page is not None else f"'{title}'"
            if label not in doc_refs:
                doc_refs.append(label)

    focus = _trim_phrase(first.get("explanation") or first.get("prompt") or "", 95).rstrip(".")
    topic_summary = ", ".join(topics_ordered[:2]) if topics_ordered else "các câu vừa làm sai"
    doc_summary = "; ".join(doc_refs[:2])

    if doc_summary and focus:
        return _trim_phrase(
            f"Bạn vừa nhầm ở {len(wrong_items)} câu thuộc {topic_summary} (đáng chú ý: {focus}). "
            f"Hãy xem lại {doc_summary} rồi vào Tiến độ nếu muốn luyện củng cố.",
            300,
        )
    if focus:
        return _trim_phrase(
            f"Cần chú ý lại {len(wrong_items)} câu chưa đạt ở {topic_summary}, đặc biệt là: {focus}.",
            300,
        )
    return _trim_phrase(
        f"Bạn còn {len(wrong_items)} câu chưa đạt ở {topic_summary}; hãy rà soát phần giải thích chi tiết bên dưới.",
        300,
    )
