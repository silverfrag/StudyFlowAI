from pydantic import BaseModel
from uuid import UUID
from datetime import datetime
from typing import Optional, List


class RecentActivity(BaseModel):
    course_id: UUID
    course_name: str
    last_quiz_title: str
    last_score: float
    last_active_at: datetime


class ProgressSummaryResponse(BaseModel):
    total_courses: int
    total_quizzes_completed: int
    average_score: float
    recent_activity: Optional[RecentActivity] = None


class SuggestedDocument(BaseModel):
    id: UUID
    title: str


class RecommendationItem(BaseModel):
    topic: str
    course_id: UUID
    course_name: str
    total_answered: int
    correct_count: int
    accuracy: float
    recommended_action: str
    suggested_document: Optional[SuggestedDocument] = None

