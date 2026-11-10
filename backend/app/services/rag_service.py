from datetime import datetime, timedelta
import logging
import re
import unicodedata
from typing import Dict, List, Optional, Sequence, Set, Tuple
from uuid import UUID

from fastapi import HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy import and_, func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.assistant import AssistantMessage, AssistantSession
from app.models.course import Course
from app.models.document import Document, DocumentChunk
from app.schemas.assistant import (
    AskResponse,
    AssistantMessageResponse,
    AssistantSessionResponse,
    ChatHistoryMessage,
    Citation,
)
from app.services.document_service import EMBEDDING_DIMENSIONS
from app.services.gemini_service import embed_texts, generate_structured_json

logger = logging.getLogger(__name__)

RAG_TOP_K = 14
RAG_VECTOR_POOL = 24
RAG_LEXICAL_POOL = 12
MAX_CONTEXT_CHARS = 12000
MAX_SNIPPET_CHARS = 360

_STOPWORDS_FOLDED = {
    "la", "gi", "cua", "va", "voi", "trong", "cho", "nhung", "cac", "nay", "kia",
    "nhu", "the", "nao", "sao", "tai", "ve", "tu", "den", "mot", "hai", "ba",
    "duoc", "khong", "co", "hay", "hoac", "thi", "ma", "khi", "noi", "dung",
    "chinh", "tai", "lieu", "mon", "hoc", "phan", "bai", "giai", "thich",
    "tom", "tat", "tong", "hop", "liet", "ke", "neu", "vi", "du", "cu", "chi",
    "tiet", "them", "hon", "nhat", "quan", "trong", "khai", "niem", "pdf", "txt",
}

_OVERVIEW_CUES = (
    "tom tat",
    "noi dung chinh",
    "tong hop",
    "tong quan",
    "khai quat",
    "toan bo mon",
    "toan bo tai lieu",
    "on tap toan bo",
    "cac chuong",
    "gom nhung phan",
    "bao gom nhung gi",
    "co nhung gi",
    "khai niem quan trong",
    "khai niem cot loi",
    "trong tam mon",
    "muc tieu bai hoc",
    "cac chu de chinh",
)

_FOLLOWUP_CUES = (
    "chi tiet hon",
    "ky hon",
    "ro hon",
    "vi du",
    "cu the",
    "phan tren",
    "y tren",
    "y do",
    "vua neu",
    "noi tren",
    "tiep theo",
    "con gi",
    "tai sao",
    "so sanh",
    "ngan gon",
    "gach dau dong",
)

RAG_SYSTEM_INSTRUCTION = (
    "Bạn là Trợ lý học tập AI chuyên sâu của StudyFlow, hỗ trợ sinh viên ôn tập bậc đại học. "
    "Nhiệm vụ của bạn là trả lời câu hỏi SÂU SẮC, MẠCH LẠC và HỮU ÍCH bằng tiếng Việt, "
    "DỰA HOÀN TOÀN vào các đoạn tài liệu học tập [ĐOẠN i] được cung cấp.\n"
    "Quy tắc bắt buộc:\n"
    "1. Trung thực tuyệt đối với ngữ cảnh: chỉ dùng thông tin, định nghĩa, số liệu, quy trình, đoạn code hoặc ví dụ có thật trong các [ĐOẠN i]. Không suy diễn hay bịa thêm kiến thức ngoài tài liệu.\n"
    "2. Trả lời sâu sắc, có cấu trúc rõ ràng (KHÔNG trả lời hời hợt 1–2 câu khi tài liệu có nhiều chi tiết):\n"
    "   - Với câu hỏi TỔNG HỢP / TÓM TẮT: hệ thống hóa theo từng tài liệu hoặc từng nhóm chủ đề lớn, nêu rõ các khái niệm cốt lõi, mục tiêu, quy trình/kỹ thuật và điểm trọng tâm (không chỉ chép lại tên mục lục).\n"
    "   - Với câu hỏi KHÁI NIỆM / KỸ THUẬT / QUY TRÌNH / SO SÁNH: trình bày đầy đủ các lớp ý: định nghĩa & bản chất, các thành phần hoặc từng bước thực hiện, ví dụ/code minh họa (nếu có trong đoạn trích) và lưu ý/ưu nhược điểm.\n"
    "   - Định dạng thân thiện trên màn hình di động: dùng tiêu đề (## hoặc **...**), gạch đầu dòng (-) hoặc đánh số (1., 2.), block code ``` nếu có code/câu lệnh. "
    "TUYỆT ĐỐI KHÔNG viết ký hiệu `$...$`, `$$...$$` hoặc lệnh LaTeX (`\\text{...}`, `\\times`, `\\frac...`); hãy viết công thức trực tiếp bằng ký tự Unicode dễ đọc (ví dụ: `CC = E - N + 2P`, `×`, `≤`, `≥`, `→`). "
    "TUYỆT ĐỐI KHÔNG in nhãn `[ĐOẠN 0]`, `[ĐOẠN 1]` hoặc dòng kẻ `---` vào trong nội dung `answer`.\n"
    "3. Xử lý khi ngữ cảnh không đủ:\n"
    "   - Nếu các [ĐOẠN i] không có căn cứ cho câu hỏi, đặt found_in_context = false, cited_indices = [], và viết answer: 'Nội dung này chưa được tìm thấy trong tài liệu đã nạp của môn học.'\n"
    "   - Nếu tài liệu mới trả lời được một phần, hãy phân tích thật sâu phần có căn cứ và nêu rõ phần còn lại chưa thấy trong tài liệu đã nạp.\n"
    "4. Trích dẫn đầy đủ: khi found_in_context = true, liệt kê chính xác TẤT CẢ chỉ số [i] bạn đã dùng làm căn cứ vào cited_indices (ưu tiên sắp xếp theo mức độ liên quan; thường từ 2–6 đoạn nếu câu hỏi tổng hợp nhiều phần)."
)


class _RagLLMOutput(BaseModel):
    found_in_context: bool = Field(
        ...,
        description="True chỉ khi các đoạn tài liệu được cung cấp chứa đủ căn cứ để trả lời câu hỏi.",
    )
    answer: str = Field(
        ...,
        description=(
            "Câu trả lời chuyên sâu, có cấu trúc bằng tiếng Việt dựa hoàn toàn trên tài liệu; "
            "không chứa nhãn [ĐOẠN i], dòng '---' hay cú pháp LaTeX ($...$, \\text{...})."
        ),
    )
    cited_indices: List[int] = Field(
        default_factory=list,
        description="Danh sách chỉ số nguyên của các đoạn [i] được dùng làm căn cứ trả lời.",
    )


def _clean_rag_answer(raw_answer: str) -> str:
    """Làm sạch nhẹ các mảnh kỹ thuật (nhãn [ĐOẠN i], đường kẻ ---, lệnh LaTeX đơn giản) nếu model lỡ sinh ra."""
    text = (raw_answer or "").strip()
    if not text:
        return ""
    # Gỡ nhãn prompt nội bộ như [ĐOẠN 0], [ĐOẠN 0, ĐOẠN 1], ([ĐOẠN 2])
    text = re.sub(
        r"\(\s*\[?\s*(?:theo\s+)?(?:ĐOẠN|đoạn)\s*\d+(?:\s*[,;]\s*(?:ĐOẠN|đoạn)?\s*\d+)*\s*\]?\s*\)",
        "",
        text,
    )
    text = re.sub(
        r"\[(?:ĐOẠN|đoạn)\s*\d+(?:\s*[,;]\s*(?:ĐOẠN|đoạn)?\s*\d+)*\]",
        "",
        text,
    )
    # Chuẩn hóa nhẹ công thức LaTeX phổ biến thành Unicode đọc được trên Compose Text (giữ nguyên $0, $('...'))
    text = re.sub(r"\$\$([^$]+?)\$\$", lambda m: m.group(1).strip(), text)
    text = re.sub(
        r"\$([^$\n]{2,140}?(?:=|\\(?:text|times|leq?|geq?|rightarrow|neq|cdot|frac))[^$\n]{0,140}?)\$",
        lambda m: m.group(1).strip(),
        text,
    )
    text = re.sub(r"\\text\{([^}]*)\}", r"\1", text)
    text = (
        text.replace("\\times", "×")
        .replace("\\leq", "≤")
        .replace("\\le", "≤")
        .replace("\\geq", "≥")
        .replace("\\ge", "≥")
        .replace("\\rightarrow", "→")
        .replace("\\neq", "≠")
        .replace("\\cdot", "·")
    )
    # Loại bỏ dòng chỉ có '---' hoặc '***'
    text = re.sub(r"(?m)^[ \t]*[-*_]{3,}[ \t]*\r?\n?", "", text)
    # Gọn khoảng trắng dư trước dấu câu (giữ nguyên thụt lề đầu dòng cho code block)
    text = re.sub(r"[ \t]+([,.;:!?])", r"\1", text)
    text = re.sub(r"(?<=\S) {2,}", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def _fold_vietnamese(text: str) -> str:
    replaced = (text or "").replace("đ", "d").replace("Đ", "D")
    nfd = unicodedata.normalize("NFD", replaced)
    stripped = "".join(ch for ch in nfd if unicodedata.category(ch) != "Mn")
    return re.sub(r"\s+", " ", stripped.lower()).strip()


def _compact_alnum(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", _fold_vietnamese(text))


def _natural_sort_key(text: str) -> List[object]:
    parts = re.split(r"(\d+)", (text or "").lower())
    return [int(p) if p.isdigit() else p for p in parts]


def _extract_keywords(text: str) -> List[str]:
    folded = _fold_vietnamese(text)
    raw_tokens = re.findall(r"[a-z0-9_$\-\.]+", folded)
    keywords: List[str] = []
    seen: Set[str] = set()
    for tok in raw_tokens:
        clean = tok.strip(".-_")
        if len(clean) < 2:
            continue
        if clean in _STOPWORDS_FOLDED and not any(ch.isdigit() for ch in clean):
            continue
        if clean not in seen:
            seen.add(clean)
            keywords.append(clean)
    return keywords[:12]


def _extract_raw_search_terms(text: str) -> List[str]:
    tokens = re.findall(r"[0-9A-Za-zÀ-ỹ_$\-]+", text or "")
    terms: List[str] = []
    seen: Set[str] = set()
    for tok in tokens:
        folded = _fold_vietnamese(tok)
        if len(folded) < 3:
            continue
        if folded in _STOPWORDS_FOLDED and not any(ch.isdigit() for ch in folded):
            continue
        lower_tok = tok.lower()
        if lower_tok not in seen:
            seen.add(lower_tok)
            terms.append(lower_tok)
    return terms[:6]


def _is_overview_query(question: str) -> bool:
    folded = _fold_vietnamese(question)
    return any(cue in folded for cue in _OVERVIEW_CUES)


def _is_followup_question(question: str) -> bool:
    folded = _fold_vietnamese(question)
    words = folded.split()
    if len(words) <= 5:
        return True
    return any(cue in folded for cue in _FOLLOWUP_CUES)


def _build_retrieval_query(question: str, history: Optional[Sequence[ChatHistoryMessage]]) -> str:
    if not history:
        return question
    user_turns = [
        re.sub(r"\s+", " ", turn.content.strip())
        for turn in history
        if turn.role == "user" and turn.content.strip()
    ]
    if not user_turns:
        return question
    if _is_followup_question(question):
        prev_topic = user_turns[-1][:240]
        if prev_topic.lower() != question.lower():
            return f"{prev_topic} — {question}"
    return question


def _find_mentioned_doc_ids(
    search_text: str,
    ready_docs: Sequence[Document],
) -> Set[UUID]:
    folded_query = _fold_vietnamese(search_text)
    query_keywords = set(_extract_keywords(search_text))
    matched: Set[UUID] = set()

    # Nhận diện số chương rõ ràng như "chuong 3", "chuong 4-2", "chuong 4.2", "4-3"
    chapter_nums = re.findall(r"(?:chuong|bai|phan)\s*(\d+(?:[\-._]\d+)?)", folded_query)
    standalone_subnums = re.findall(r"\b(\d+[\-._]\d+)\b", folded_query)
    explicit_refs = [ref.replace(".", "-") for ref in (chapter_nums + standalone_subnums)]

    for doc in ready_docs:
        folded_title = _fold_vietnamese(doc.title)
        norm_title = folded_title.replace(".", "-")
        hit = False
        for ref in explicit_refs:
            if re.search(rf"\bchuong\s*{re.escape(ref)}(?![0-9])", norm_title) or re.search(
                rf"(?<![0-9]){re.escape(ref)}(?![0-9])", norm_title
            ):
                hit = True
                break
        if not hit:
            title_keywords = [
                kw for kw in _extract_keywords(doc.title)
                if len(kw) >= 4 and kw not in {"chuong", "tong", "quan", "kiem", "phan", "mem", "thiet", "phuong", "phap"}
            ]
            overlap = query_keywords.intersection(title_keywords)
            if len(overlap) >= 2 or any(kw in folded_query and len(kw) >= 6 for kw in title_keywords):
                hit = True
        if hit:
            matched.add(doc.id)
    return matched


def _is_filler_slide(text: str) -> bool:
    stripped = (text or "").strip()
    if len(stripped) < 45:
        return True
    folded = _fold_vietnamese(stripped)
    if len(stripped) < 110 and any(marker in folded for marker in ("cam on", "thank you", "q&a", "hoi dap")):
        return True
    return False


def _build_snippet(text: str, max_chars: int = MAX_SNIPPET_CHARS) -> str:
    cleaned = " ".join(text.split())
    if len(cleaned) <= max_chars:
        return cleaned
    truncated = cleaned[:max_chars].rsplit(" ", 1)[0].strip()
    return f"{truncated or cleaned[:max_chars]}..."


def _build_plain_preview(text: str, max_chars: int = 120) -> str:
    """Chuyển markdown câu trả lời thành đoạn preview văn bản thuần gọn gàng cho danh sách phiên chat."""
    raw = (text or "").strip()
    if not raw:
        return ""
    cleaned_lines: List[str] = []
    for line in raw.splitlines():
        s = line.strip()
        if not s or re.fullmatch(r"[-*_`]{3,}", s):
            continue
        is_heading = bool(re.match(r"^#{1,6}\s+", s))
        s = re.sub(r"^#{1,6}\s+", "", s)
        s = re.sub(r"^[-*+]\s+", "", s)
        s = re.sub(r"\*\*([^*]+?)\*\*", r"\1", s)
        s = re.sub(r"__([^_]+?)__", r"\1", s)
        s = s.replace("`", "")
        s = re.sub(r"\s+", " ", s).strip()
        if not s:
            continue
        if is_heading and s[-1] not in ".!?:;…":
            s = f"{s}:"
        cleaned_lines.append(s)
    plain = " ".join(cleaned_lines)
    return _build_snippet(plain or raw, max_chars)


def _pack_chunks_within_budget(
    ordered_chunks: Sequence[Tuple[DocumentChunk, str]],
    max_chunks: int = RAG_TOP_K,
    max_chars: int = MAX_CONTEXT_CHARS,
) -> List[Tuple[DocumentChunk, str]]:
    packed: List[Tuple[DocumentChunk, str]] = []
    seen_ids: Set[UUID] = set()
    total_chars = 0

    for chunk, doc_title in ordered_chunks:
        if chunk.id in seen_ids:
            continue
        chunk_len = len(chunk.text or "")
        if packed and (len(packed) >= max_chunks or total_chars + chunk_len > max_chars):
            break
        seen_ids.add(chunk.id)
        packed.append((chunk, doc_title))
        total_chars += chunk_len
        if len(packed) >= max_chunks:
            break
    return packed


async def _retrieve_overview_chunks(
    course_id: UUID,
    ready_docs: Sequence[Document],
    mentioned_doc_ids: Set[UUID],
    query_vec: List[float],
    db: AsyncSession,
) -> List[Tuple[DocumentChunk, str]]:
    target_docs = [d for d in ready_docs if d.id in mentioned_doc_ids] if mentioned_doc_ids else list(ready_docs)
    if not target_docs:
        target_docs = list(ready_docs)
    target_doc_ids = [d.id for d in target_docs]
    doc_title_map = {d.id: d.title for d in target_docs}

    # 1. Lấy các slide đầu/mục tiêu/tóm tắt của từng tài liệu (ordinal <= 5)
    early_stmt = (
        select(DocumentChunk)
        .where(
            DocumentChunk.course_id == course_id,
            DocumentChunk.document_id.in_(target_doc_ids),
            DocumentChunk.ordinal <= 5,
        )
        .order_by(DocumentChunk.document_id, DocumentChunk.ordinal.asc())
    )
    early_chunks = list((await db.execute(early_stmt)).scalars().all())
    by_doc_early: Dict[UUID, List[DocumentChunk]] = {doc_id: [] for doc_id in target_doc_ids}
    for ch in early_chunks:
        by_doc_early.setdefault(ch.document_id, []).append(ch)

    # 2. Lấy pool vector gần nhất để bổ sung nội dung cốt lõi bám theo câu hỏi
    vec_stmt = (
        select(DocumentChunk)
        .where(
            DocumentChunk.course_id == course_id,
            DocumentChunk.document_id.in_(target_doc_ids),
            DocumentChunk.embedding.is_not(None),
        )
        .order_by(DocumentChunk.embedding.cosine_distance(query_vec))
        .limit(RAG_VECTOR_POOL)
    )
    vec_chunks = list((await db.execute(vec_stmt)).scalars().all())
    by_doc_vec: Dict[UUID, List[DocumentChunk]] = {doc_id: [] for doc_id in target_doc_ids}
    for ch in vec_chunks:
        by_doc_vec.setdefault(ch.document_id, []).append(ch)

    # 3. Nếu phạm vi ít tài liệu hoặc một số tài liệu thiếu mẫu trong vec pool, lấy thêm mẫu rải đều
    by_doc_spread: Dict[UUID, List[DocumentChunk]] = {}
    docs_needing_spread = (
        target_doc_ids
        if len(target_docs) <= 2
        else [d_id for d_id in target_doc_ids if len(by_doc_vec.get(d_id, [])) < 2]
    )
    if docs_needing_spread:
        all_doc_chunks_res = await db.execute(
            select(DocumentChunk)
            .where(DocumentChunk.document_id.in_(docs_needing_spread))
            .order_by(DocumentChunk.document_id, DocumentChunk.ordinal.asc())
        )
        grouped_all: Dict[UUID, List[DocumentChunk]] = {}
        for ch in all_doc_chunks_res.scalars().all():
            grouped_all.setdefault(ch.document_id, []).append(ch)
        per_doc_sample = 8 if len(target_docs) == 1 else (4 if len(target_docs) == 2 else 3)
        for d_id, rows in grouped_all.items():
            substantive = [c for c in rows if c.ordinal >= 4 and not _is_filler_slide(c.text)] or [
                c for c in rows if not _is_filler_slide(c.text)
            ] or rows
            if len(substantive) <= per_doc_sample:
                by_doc_spread[d_id] = substantive
            else:
                step = len(substantive) / float(per_doc_sample)
                by_doc_spread[d_id] = [substantive[int(i * step)] for i in range(per_doc_sample)]

    # 4. Chọn cân bằng theo từng tài liệu (outline + substantive/vec + spread) theo thứ tự chương
    per_doc_selected: Dict[UUID, List[DocumentChunk]] = {doc_id: [] for doc_id in target_doc_ids}
    seen_chunk_ids: Set[UUID] = set()
    slots_per_doc = max(2, RAG_TOP_K // max(1, min(len(target_docs), 7)))

    for doc in target_docs:
        d_id = doc.id
        picked: List[DocumentChunk] = []

        # Ưu tiên slide tổng quan/mục tiêu (thường ordinal 1..3 chứa nhiều thông tin hơn trang bìa)
        early_substantive = [c for c in by_doc_early.get(d_id, []) if not _is_filler_slide(c.text)]
        early_pool = early_substantive or by_doc_early.get(d_id, [])
        for ch in early_pool:
            if ch.id not in seen_chunk_ids and len(picked) < 1:
                picked.append(ch)
                seen_chunk_ids.add(ch.id)

        # Bổ sung chunk khớp vector hoặc mẫu rải đều của chính tài liệu đó
        for ch in by_doc_vec.get(d_id, []):
            if len(picked) >= slots_per_doc:
                break
            if ch.id not in seen_chunk_ids and not _is_filler_slide(ch.text):
                picked.append(ch)
                seen_chunk_ids.add(ch.id)

        for ch in by_doc_spread.get(d_id, []):
            if len(picked) >= slots_per_doc + 1:
                break
            if ch.id not in seen_chunk_ids:
                picked.append(ch)
                seen_chunk_ids.add(ch.id)

        if not picked:
            for ch in by_doc_early.get(d_id, []) + by_doc_vec.get(d_id, []):
                if ch.id not in seen_chunk_ids:
                    picked.append(ch)
                    seen_chunk_ids.add(ch.id)
                    break

        picked.sort(key=lambda c: c.ordinal)
        per_doc_selected[d_id] = picked

    # Vòng 1: đảm bảo mỗi tài liệu đều có đại diện; Vòng 2: thêm phần còn lại
    ordered: List[Tuple[DocumentChunk, str]] = []
    max_rounds = max((len(v) for v in per_doc_selected.values()), default=0)
    for rnd in range(max_rounds):
        for doc in target_docs:
            items = per_doc_selected.get(doc.id, [])
            if rnd < len(items):
                ordered.append((items[rnd], doc.title))

    # Bổ sung từ vector pool toàn cục nếu ngân sách còn chỗ
    for ch in vec_chunks:
        if ch.id not in seen_chunk_ids and ch.document_id in doc_title_map:
            seen_chunk_ids.add(ch.id)
            ordered.append((ch, doc_title_map[ch.document_id]))

    return _pack_chunks_within_budget(ordered)


async def _retrieve_hybrid_focused_chunks(
    course_id: UUID,
    document_id: Optional[UUID],
    ready_docs: Sequence[Document],
    mentioned_doc_ids: Set[UUID],
    retrieval_query: str,
    query_vec: List[float],
    db: AsyncSession,
) -> List[Tuple[DocumentChunk, str]]:
    doc_title_map = {d.id: d.title for d in ready_docs}
    doc_rank_map = {d.id: idx for idx, d in enumerate(ready_docs)}
    distance_col = DocumentChunk.embedding.cosine_distance(query_vec).label("distance")

    base_filter = [
        DocumentChunk.course_id == course_id,
        Document.status == "ready",
        DocumentChunk.embedding.is_not(None),
    ]
    if document_id is not None:
        base_filter.append(DocumentChunk.document_id == document_id)

    vec_stmt = (
        select(DocumentChunk, Document.title, distance_col)
        .join(Document, DocumentChunk.document_id == Document.id)
        .where(*base_filter)
        .order_by(distance_col)
        .limit(RAG_VECTOR_POOL)
    )
    candidate_map: Dict[UUID, Tuple[DocumentChunk, str, float]] = {}
    for chunk, title, dist in (await db.execute(vec_stmt)).all():
        candidate_map[chunk.id] = (chunk, title, float(dist) if dist is not None else 0.5)

    # Nếu người học nhắc đích danh chương/tài liệu, lấy thêm vector top riêng trong tài liệu đó
    if document_id is None and mentioned_doc_ids:
        doc_vec_stmt = (
            select(DocumentChunk, Document.title, distance_col)
            .join(Document, DocumentChunk.document_id == Document.id)
            .where(
                DocumentChunk.course_id == course_id,
                DocumentChunk.document_id.in_(list(mentioned_doc_ids)),
                DocumentChunk.embedding.is_not(None),
            )
            .order_by(distance_col)
            .limit(10)
        )
        for chunk, title, dist in (await db.execute(doc_vec_stmt)).all():
            if chunk.id not in candidate_map:
                candidate_map[chunk.id] = (chunk, title, float(dist) if dist is not None else 0.5)

    # Truy vấn từ khóa lexical để bổ trợ khi thuật ngữ/tiếng Việt dính liền nhau trong PDF
    raw_terms = _extract_raw_search_terms(retrieval_query)
    if raw_terms:
        lex_filters = [DocumentChunk.text.ilike(f"%{term}%") for term in raw_terms]
        lex_stmt = (
            select(DocumentChunk, Document.title)
            .join(Document, DocumentChunk.document_id == Document.id)
            .where(
                DocumentChunk.course_id == course_id,
                Document.status == "ready",
                or_(*lex_filters),
            )
        )
        if document_id is not None:
            lex_stmt = lex_stmt.where(DocumentChunk.document_id == document_id)
        lex_stmt = lex_stmt.limit(RAG_LEXICAL_POOL)
        for chunk, title in (await db.execute(lex_stmt)).all():
            if chunk.id not in candidate_map:
                candidate_map[chunk.id] = (chunk, title, 0.45)

    if not candidate_map:
        return []

    query_keywords = _extract_keywords(retrieval_query)
    compact_keywords = [_compact_alnum(kw) for kw in query_keywords if len(_compact_alnum(kw)) >= 3]

    scored: List[Tuple[float, DocumentChunk, str]] = []
    for chunk, title, dist in candidate_map.values():
        vec_sim = max(0.0, 1.0 - dist)
        folded_text = _fold_vietnamese(chunk.text)
        compact_text = _compact_alnum(chunk.text)

        lex_hits = 0
        if query_keywords:
            for kw in query_keywords:
                c_kw = _compact_alnum(kw)
                if kw in folded_text or (len(c_kw) >= 3 and c_kw in compact_text):
                    lex_hits += 1
            lex_score = float(lex_hits) / float(len(query_keywords))
        else:
            lex_score = 0.0

        doc_boost = 0.22 if chunk.document_id in mentioned_doc_ids else 0.0
        text_len = len(chunk.text or "")
        substance_bonus = 0.08 if text_len >= 160 else (-0.12 if text_len < 70 else 0.0)
        compact_bonus = 0.05 if any(ck in compact_text for ck in compact_keywords[:3]) else 0.0

        total_score = vec_sim + (0.40 * lex_score) + doc_boost + substance_bonus + compact_bonus
        scored.append((total_score, chunk, title))

    scored.sort(key=lambda item: (-item[0], doc_rank_map.get(item[1].document_id, 99), item[1].ordinal))

    # Chọn các anchor đa dạng tài liệu trước (tối đa 4 anchor mỗi doc ở vòng đầu)
    anchors: List[Tuple[DocumentChunk, str]] = []
    seen_anchor_ids: Set[UUID] = set()
    doc_counts: Dict[UUID, int] = {}
    max_anchors = 8

    for _, chunk, title in scored:
        if len(anchors) >= max_anchors:
            break
        if document_id is None and len(ready_docs) > 1 and doc_counts.get(chunk.document_id, 0) >= 4:
            continue
        anchors.append((chunk, title))
        seen_anchor_ids.add(chunk.id)
        doc_counts[chunk.document_id] = doc_counts.get(chunk.document_id, 0) + 1

    if len(anchors) < max_anchors:
        for _, chunk, title in scored:
            if len(anchors) >= max_anchors:
                break
            if chunk.id not in seen_anchor_ids:
                anchors.append((chunk, title))
                seen_anchor_ids.add(chunk.id)

    # Mở rộng slide liền kề (ordinal - 1, ordinal + 1) cho các anchor ngắn dạng slide
    neighbor_conditions = []
    wanted_pairs: Set[Tuple[UUID, int]] = set()
    for chunk, _ in anchors[:4]:
        if len(chunk.text or "") < 600:
            for nb_ord in (chunk.ordinal - 1, chunk.ordinal + 1):
                if nb_ord >= 0:
                    pair = (chunk.document_id, nb_ord)
                    if pair not in wanted_pairs:
                        wanted_pairs.add(pair)
                        neighbor_conditions.append(
                            and_(
                                DocumentChunk.document_id == chunk.document_id,
                                DocumentChunk.ordinal == nb_ord,
                            )
                        )

    neighbor_lookup: Dict[Tuple[UUID, int], Tuple[DocumentChunk, str]] = {}
    if neighbor_conditions:
        nb_stmt = select(DocumentChunk).where(or_(*neighbor_conditions))
        for nb in (await db.execute(nb_stmt)).scalars().all():
            title = doc_title_map.get(nb.document_id)
            if title and not _is_filler_slide(nb.text):
                neighbor_lookup[(nb.document_id, nb.ordinal)] = (nb, title)

    # Ghép anchor + neighbor liền kề theo đúng thứ tự liên quan
    assembled: List[Tuple[DocumentChunk, str]] = []
    seen_final: Set[UUID] = set()

    for chunk, title in anchors:
        prev_nb = neighbor_lookup.get((chunk.document_id, chunk.ordinal - 1))
        next_nb = neighbor_lookup.get((chunk.document_id, chunk.ordinal + 1))
        local_group: List[Tuple[DocumentChunk, str]] = []
        if prev_nb:
            local_group.append(prev_nb)
        local_group.append((chunk, title))
        if next_nb:
            local_group.append(next_nb)
        for ch, t in local_group:
            if ch.id not in seen_final:
                seen_final.add(ch.id)
                assembled.append((ch, t))

    # Thêm các phần còn lại của candidate pool nếu ngân sách vẫn còn chỗ
    for _, chunk, title in scored:
        if chunk.id not in seen_final:
            seen_final.add(chunk.id)
            assembled.append((chunk, title))

    return _pack_chunks_within_budget(assembled)


def _build_session_title(question: str, max_len: int = 72) -> str:
    cleaned = re.sub(r"\s+", " ", (question or "").strip())
    if len(cleaned) <= max_len:
        return cleaned or "Đoạn chat mới"
    truncated = cleaned[:max_len].rsplit(" ", 1)[0].strip()
    return f"{truncated or cleaned[:max_len]}..."


async def _load_recent_session_history(
    session_id: UUID,
    expected_document_id: Optional[UUID],
    db: AsyncSession,
    max_messages: int = 4,
) -> List[ChatHistoryMessage]:
    stmt = (
        select(AssistantMessage)
        .where(AssistantMessage.session_id == session_id)
        .order_by(AssistantMessage.created_at.desc(), AssistantMessage.id.desc())
        .limit(12)
    )
    rows = list((await db.execute(stmt)).scalars().all())
    contiguous_same_scope: List[ChatHistoryMessage] = []
    for msg in rows:
        if msg.document_id != expected_document_id:
            break
        role = "user" if msg.role == "user" else "assistant"
        content = (msg.content or "").strip()
        if not content:
            continue
        contiguous_same_scope.append(ChatHistoryMessage(role=role, content=content[:2000]))
        if len(contiguous_same_scope) >= max_messages:
            break
    contiguous_same_scope.reverse()
    return contiguous_same_scope


async def _persist_assistant_turn(
    db: AsyncSession,
    course_id: UUID,
    document_id: Optional[UUID],
    question: str,
    response: AskResponse,
    existing_session: Optional[AssistantSession] = None,
) -> AskResponse:
    user_ts = datetime.utcnow()
    ai_ts = user_ts + timedelta(microseconds=10)

    if existing_session is None:
        session_obj = AssistantSession(
            course_id=course_id,
            document_id=document_id,
            title=_build_session_title(question),
            created_at=user_ts,
            updated_at=ai_ts,
        )
        db.add(session_obj)
        await db.flush()
    else:
        session_obj = existing_session
        session_obj.document_id = document_id
        session_obj.updated_at = ai_ts

    user_msg = AssistantMessage(
        session_id=session_obj.id,
        course_id=course_id,
        document_id=document_id,
        role="user",
        content=question,
        found_in_context=True,
        citations=[],
        created_at=user_ts,
    )
    ai_msg = AssistantMessage(
        session_id=session_obj.id,
        course_id=course_id,
        document_id=document_id,
        role="assistant",
        content=response.answer,
        found_in_context=response.found_in_context,
        citations=[c.model_dump(mode="json") for c in response.citations],
        created_at=ai_ts,
    )
    db.add(user_msg)
    db.add(ai_msg)
    await db.commit()

    response.session_id = session_obj.id
    response.session_title = session_obj.title
    return response


async def list_course_assistant_sessions(
    course_id: UUID,
    db: AsyncSession,
    limit: int = 50,
) -> List[AssistantSessionResponse]:
    course = await db.get(Course, course_id)
    if not course:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy môn học."},
        )

    sess_stmt = (
        select(AssistantSession)
        .where(AssistantSession.course_id == course_id)
        .order_by(AssistantSession.updated_at.desc(), AssistantSession.created_at.desc())
        .limit(limit)
    )
    sessions = list((await db.execute(sess_stmt)).scalars().all())
    if not sessions:
        return []

    session_ids = [s.id for s in sessions]
    count_rows = (
        await db.execute(
            select(AssistantMessage.session_id, func.count(AssistantMessage.id))
            .where(AssistantMessage.session_id.in_(session_ids))
            .group_by(AssistantMessage.session_id)
        )
    ).all()
    count_map: Dict[UUID, int] = {sid: int(cnt) for sid, cnt in count_rows}

    last_msg_rows = (
        await db.execute(
            select(AssistantMessage.session_id, AssistantMessage.content)
            .where(AssistantMessage.session_id.in_(session_ids))
            .distinct(AssistantMessage.session_id)
            .order_by(
                AssistantMessage.session_id,
                AssistantMessage.created_at.desc(),
                AssistantMessage.id.desc(),
            )
        )
    ).all()
    preview_map: Dict[UUID, str] = {sid: _build_plain_preview(content, 120) for sid, content in last_msg_rows if content}

    doc_ids = list({s.document_id for s in sessions if s.document_id is not None})
    doc_title_map: Dict[UUID, str] = {}
    if doc_ids:
        doc_rows = (await db.execute(select(Document.id, Document.title).where(Document.id.in_(doc_ids)))).all()
        doc_title_map = {did: dtitle for did, dtitle in doc_rows}

    return [
        AssistantSessionResponse(
            id=s.id,
            course_id=s.course_id,
            document_id=s.document_id,
            document_title=doc_title_map.get(s.document_id) if s.document_id else None,
            title=s.title,
            message_count=count_map.get(s.id, 0),
            last_message_preview=preview_map.get(s.id),
            created_at=s.created_at,
            updated_at=s.updated_at,
        )
        for s in sessions
    ]


async def list_session_messages(
    session_id: UUID,
    db: AsyncSession,
    limit: int = 100,
) -> List[AssistantMessageResponse]:
    session_obj = await db.get(AssistantSession, session_id)
    if not session_obj:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy phiên chat."},
        )

    stmt = (
        select(AssistantMessage)
        .where(AssistantMessage.session_id == session_id)
        .order_by(AssistantMessage.created_at.desc(), AssistantMessage.id.desc())
        .limit(limit)
    )
    rows = list((await db.execute(stmt)).scalars().all())
    rows.reverse()

    items: List[AssistantMessageResponse] = []
    for msg in rows:
        parsed_citations: List[Citation] = []
        for raw_cite in msg.citations or []:
            if isinstance(raw_cite, dict):
                try:
                    parsed_citations.append(Citation.model_validate(raw_cite))
                except Exception:
                    continue
        items.append(
            AssistantMessageResponse(
                id=msg.id,
                session_id=msg.session_id,
                course_id=msg.course_id,
                document_id=msg.document_id,
                role="user" if msg.role == "user" else "assistant",
                content=msg.content,
                found_in_context=msg.found_in_context,
                citations=parsed_citations,
                created_at=msg.created_at,
            )
        )
    return items


async def delete_assistant_session(session_id: UUID, db: AsyncSession) -> None:
    session_obj = await db.get(AssistantSession, session_id)
    if not session_obj:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy phiên chat."},
        )
    await db.delete(session_obj)
    await db.commit()


async def ask_question_rag(
    course_id: UUID,
    document_id: Optional[UUID],
    question: str,
    db: AsyncSession,
    history: Optional[Sequence[ChatHistoryMessage]] = None,
    session_id: Optional[UUID] = None,
) -> AskResponse:
    """Truy vấn lai (pgvector + lexical + mở rộng ngữ cảnh slide), chống trôi ngữ cảnh và lưu phiên chat theo môn."""
    cleaned_question = question.strip()
    if len(cleaned_question) < 2:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"code": "VALIDATION_ERROR", "message": "Câu hỏi cần có ít nhất 2 ký tự.", "field": "question"},
        )

    course = await db.get(Course, course_id)
    if not course:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy môn học."},
        )

    existing_session: Optional[AssistantSession] = None
    if session_id is not None:
        existing_session = await db.get(AssistantSession, session_id)
        if not existing_session:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy phiên chat."},
            )
        if existing_session.course_id != course_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={
                    "code": "VALIDATION_ERROR",
                    "message": "Phiên chat không thuộc môn học đã chọn.",
                    "field": "session_id",
                },
            )

    if document_id is not None:
        doc = await db.get(Document, document_id)
        if not doc:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy tài liệu được chọn."},
            )
        if doc.course_id != course_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={
                    "code": "VALIDATION_ERROR",
                    "message": "Tài liệu được chọn không thuộc môn học hiện tại.",
                    "field": "document_id",
                },
            )
        if doc.status != "ready":
            status_vi = "đang xử lý" if doc.status == "processing" else "bị lỗi"
            return AskResponse(
                session_id=existing_session.id if existing_session else None,
                session_title=existing_session.title if existing_session else None,
                answer=f"Tài liệu '{doc.title}' hiện {status_vi}, chưa sẵn sàng để tra cứu.",
                found_in_context=False,
                citations=[],
            )

    # Kiểm tra trước xem phạm vi truy vấn đã có chunk embedding sẵn sàng hay chưa
    has_chunk_stmt = (
        select(DocumentChunk.id)
        .join(Document, DocumentChunk.document_id == Document.id)
        .where(
            DocumentChunk.course_id == course_id,
            Document.status == "ready",
            DocumentChunk.embedding.is_not(None),
        )
    )
    if document_id is not None:
        has_chunk_stmt = has_chunk_stmt.where(DocumentChunk.document_id == document_id)
    has_chunk_res = await db.execute(has_chunk_stmt.limit(1))
    if has_chunk_res.scalar_one_or_none() is None:
        return AskResponse(
            session_id=existing_session.id if existing_session else None,
            session_title=existing_session.title if existing_session else None,
            answer="Môn học này chưa có tài liệu nào ở trạng thái sẵn sàng để tra cứu. Hãy tải tệp PDF hoặc TXT lên và chờ xử lý xong.",
            found_in_context=False,
            citations=[],
        )

    doc_scope_stmt = select(Document).where(Document.course_id == course_id, Document.status == "ready")
    if document_id is not None:
        doc_scope_stmt = doc_scope_stmt.where(Document.id == document_id)
    ready_docs = sorted(
        list((await db.execute(doc_scope_stmt)).scalars().all()),
        key=lambda d: _natural_sort_key(d.title),
    )

    # Chống trôi ngữ cảnh:
    # - Nếu tiếp tục phiên cũ nhưng đổi phạm vi document_id so với lượt trước của phiên, ngắt nối ngữ cảnh cũ.
    # - Nếu tiếp tục cùng phạm vi trong phiên đã lưu, ưu tiên tối đa 4 tin nhắn liên tiếp cùng phạm vi từ DB.
    if existing_session is not None:
        doc_scope_switched = existing_session.document_id != document_id
        if doc_scope_switched:
            effective_history: List[ChatHistoryMessage] = []
        else:
            effective_history = await _load_recent_session_history(
                session_id=existing_session.id,
                expected_document_id=document_id,
                db=db,
                max_messages=4,
            )
            if not effective_history and history:
                effective_history = list(history)[-4:]
    else:
        effective_history = list(history)[-4:] if history else []

    retrieval_query = _build_retrieval_query(cleaned_question, effective_history)
    # Luôn nhúng câu hỏi thành vector 768 chiều để hỗ trợ truy vấn ngữ nghĩa & kiểm soát lỗi AI nhất quán
    query_vectors = await embed_texts(
        [retrieval_query],
        task_type="RETRIEVAL_QUERY",
        dimensions=EMBEDDING_DIMENSIONS,
    )
    query_vec = query_vectors[0]

    mentioned_doc_ids = (
        set()
        if document_id is not None
        else _find_mentioned_doc_ids(f"{cleaned_question} {retrieval_query}", ready_docs)
    )

    if _is_overview_query(cleaned_question):
        retrieved_rows = await _retrieve_overview_chunks(
            course_id=course_id,
            ready_docs=ready_docs,
            mentioned_doc_ids=mentioned_doc_ids,
            query_vec=query_vec,
            db=db,
        )
    else:
        retrieved_rows = await _retrieve_hybrid_focused_chunks(
            course_id=course_id,
            document_id=document_id,
            ready_docs=ready_docs,
            mentioned_doc_ids=mentioned_doc_ids,
            retrieval_query=retrieval_query,
            query_vec=query_vec,
            db=db,
        )

    if not retrieved_rows:
        return await _persist_assistant_turn(
            db=db,
            course_id=course_id,
            document_id=document_id,
            question=cleaned_question,
            response=AskResponse(
                answer="Nội dung này chưa được tìm thấy trong tài liệu đã nạp của môn học.",
                found_in_context=False,
                citations=[],
            ),
            existing_session=existing_session,
        )

    context_blocks = []
    for idx, (chunk, doc_title) in enumerate(retrieved_rows):
        page_label = f"Trang {chunk.page_number}" if chunk.page_number is not None else "Không phân trang"
        context_blocks.append(f"[ĐOẠN {idx}] Tài liệu: {doc_title} | {page_label}\n{chunk.text}")

    doc_names_summary = ", ".join(d.title for d in ready_docs[:10])
    history_block = ""
    if effective_history:
        recent_turns = []
        for turn in effective_history[-4:]:
            role_vi = "Sinh viên" if turn.role == "user" else "Trợ lý AI"
            snippet = re.sub(r"\s+", " ", turn.content.strip())[:360]
            recent_turns.append(f"- {role_vi}: {snippet}")
        if recent_turns:
            history_block = (
                "Ngữ cảnh hội thoại gần đây (chỉ dùng để hiểu mạch câu hỏi tiếp nối; "
                "mọi dữ kiện và citation vẫn PHẢI trích xuất từ các [ĐOẠN i] bên dưới):\n"
                + "\n".join(recent_turns)
                + "\n\n"
            )

    user_prompt = (
        f"Môn học: {course.name}\n"
        f"Tài liệu sẵn sàng trong phạm vi ({len(ready_docs)}): {doc_names_summary}\n\n"
        f"{history_block}"
        f"Các đoạn tài liệu truy xuất được:\n"
        + "\n\n---\n\n".join(context_blocks)
        + f"\n\nCâu hỏi của sinh viên: {cleaned_question}"
    )

    llm_output = await generate_structured_json(
        prompt=user_prompt,
        system_instruction=RAG_SYSTEM_INSTRUCTION,
        schema_cls=_RagLLMOutput,
    )

    if not llm_output.found_in_context:
        fallback_msg = "Nội dung này chưa được tìm thấy trong tài liệu đã nạp của môn học."
        return await _persist_assistant_turn(
            db=db,
            course_id=course_id,
            document_id=document_id,
            question=cleaned_question,
            response=AskResponse(
                answer=llm_output.answer.strip() or fallback_msg,
                found_in_context=False,
                citations=[],
            ),
            existing_session=existing_session,
        )

    # Xác thực chặt chẽ các chỉ số trích dẫn để không bao giờ bịa citation
    seen_chunk_ids: Set[UUID] = set()
    verified_citations: List[Citation] = []
    for idx in llm_output.cited_indices:
        if isinstance(idx, int) and 0 <= idx < len(retrieved_rows):
            chunk, doc_title = retrieved_rows[idx]
            if chunk.id in seen_chunk_ids:
                continue
            seen_chunk_ids.add(chunk.id)
            verified_citations.append(
                Citation(
                    document_id=chunk.document_id,
                    document_title=doc_title,
                    page_number=chunk.page_number,
                    chunk_id=chunk.id,
                    snippet=_build_snippet(chunk.text),
                )
            )

    if not verified_citations:
        return await _persist_assistant_turn(
            db=db,
            course_id=course_id,
            document_id=document_id,
            question=cleaned_question,
            response=AskResponse(
                answer="Nội dung này chưa được tìm thấy căn cứ rõ ràng trong tài liệu đã nạp của môn học.",
                found_in_context=False,
                citations=[],
            ),
            existing_session=existing_session,
        )

    cleaned_answer = _clean_rag_answer(llm_output.answer) or llm_output.answer.strip()
    return await _persist_assistant_turn(
        db=db,
        course_id=course_id,
        document_id=document_id,
        question=cleaned_question,
        response=AskResponse(
            answer=cleaned_answer,
            found_in_context=True,
            citations=verified_citations,
        ),
        existing_session=existing_session,
    )
