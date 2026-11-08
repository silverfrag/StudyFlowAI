from pydantic import BaseModel, Field
from uuid import UUID
from datetime import datetime
from typing import Optional, List, Literal

OptionId = Literal["A", "B", "C", "D"]


class OptionSchema(BaseModel):
    id: OptionId
    text: str


class QuizCreateRequest(BaseModel):
    course_id: UUID
    document_id: Optional[UUID] = None
    topic: Optional[str] = Field(None, max_length=200)
    question_count: int = Field(default=5, ge=3, le=10, description="Số câu hỏi từ 3 đến 10")


class QuizQuestionClientResponse(BaseModel):
    id: UUID
    prompt: str
    topic: str
    options: List[OptionSchema]


class QuizClientResponse(BaseModel):
    id: UUID
    course_id: UUID
    title: str
    total_questions: int
    questions: List[QuizQuestionClientResponse]
    created_at: datetime


class AnswerSubmission(BaseModel):
    question_id: UUID
    selected_option_id: Optional[OptionId] = None


class QuizSubmitRequest(BaseModel):
    answers: List[AnswerSubmission]


class TopicStat(BaseModel):
    topic: str
    total_questions: int
    correct_count: int
    accuracy: float


class SourceCitation(BaseModel):
    document_title: str
    page_number: Optional[int] = None


class QuestionResultDetail(BaseModel):
    question_id: UUID
    prompt: str
    selected_option_id: Optional[OptionId] = None
    correct_option_id: OptionId
    is_correct: bool
    explanation: str
    citation: Optional[SourceCitation] = None


class QuizSubmitResponse(BaseModel):
    attempt_id: UUID
    quiz_id: UUID
    score: float
    total_questions: int
    correct_count: int
    incorrect_count: int
    submitted_at: datetime
    topic_stats: List[TopicStat]
    details: List[QuestionResultDetail]

