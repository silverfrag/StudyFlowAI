from contextlib import asynccontextmanager
import logging
import socket

from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.core.config import settings
from app.core.database import AsyncSessionLocal, init_db
from app.services.document_service import recover_orphaned_processing_documents
from app.api.v1.health import router as health_router
from app.api.v1.courses import router as courses_router
from app.api.v1.documents import router as documents_router
from app.api.v1.assistant import router as assistant_router
from app.api.v1.quizzes import router as quizzes_router
from app.api.v1.progress import router as progress_router

logger = logging.getLogger(__name__)


def _is_db_connection_error(exc: BaseException) -> bool:
    visited: set[int] = set()
    stack: list[BaseException] = [exc]
    while stack:
        cur = stack.pop()
        if id(cur) in visited:
            continue
        visited.add(id(cur))
        if isinstance(cur, (ConnectionRefusedError, socket.gaierror)):
            return True
        if isinstance(cur, OSError) and getattr(cur, "errno", None) in (10061, 10060, 111, 110, 61):
            return True
        if cur.__class__.__name__ in {
            "PostgresConnectionError",
            "CannotConnectNowError",
            "ConnectionDoesNotExistError",
            "InterfaceError",
        }:
            return True
        msg = str(cur).lower()
        if "connect call failed" in msg or "connection refused" in msg:
            return True
        for nxt in (cur.__cause__, cur.__context__):
            if nxt is not None:
                stack.append(nxt)
        sub_excs = getattr(cur, "exceptions", None)
        if sub_excs:
            stack.extend(sub_excs)
    return False


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Khởi động database, pgvector extension và phục hồi các tài liệu bị gián đoạn trước đó
    try:
        await init_db()
        async with AsyncSessionLocal() as db:
            recovered = await recover_orphaned_processing_documents(db)
            if recovered:
                print(f"Recovered {recovered} orphaned processing document(s) to error state.")
        print("Database & vector extension initialized successfully.")
    except Exception as e:
        print(f"Warning: Could not connect to DB on startup: {e}")
    yield


app = FastAPI(
    title=settings.APP_NAME,
    version="1.0.0",
    description="Backend API cho StudyFlow AI - Hỗ trợ học tập cá nhân hóa tích hợp RAG & Gemini.",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

# Cấu hình CORS cho Android client
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Custom Exception Handler chuẩn hóa lỗi Validation của FastAPI về Envelope chung
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    errors = []
    primary_field = None
    for err in exc.errors():
        field_path = ".".join(str(loc) for loc in err.get("loc", []))
        if not primary_field:
            primary_field = field_path
        errors.append({
            "field": field_path,
            "message": err.get("msg", "Invalid input"),
        })

    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={
            "detail": {
                "code": "VALIDATION_ERROR",
                "message": "Dữ liệu gửi lên không hợp lệ.",
                "field": primary_field,
                "errors": errors,
            }
        },
    )


# Custom Exception Handler cho HTTPException chuẩn
@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(request: Request, exc: StarletteHTTPException):
    detail = exc.detail
    if isinstance(detail, dict) and "code" in detail:
        return JSONResponse(
            status_code=exc.status_code,
            content={"detail": detail},
        )
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "detail": {
                "code": "HTTP_ERROR",
                "message": str(detail),
                "field": None,
            }
        },
    )


# Chuẩn hóa mọi lỗi chưa bắt (bao gồm mất kết nối PostgreSQL) về envelope JSON chuẩn
@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    logger.exception("Unhandled error on %s %s", request.method, request.url.path)
    if _is_db_connection_error(exc):
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={
                "detail": {
                    "code": "INTERNAL_SERVER_ERROR",
                    "message": "Không thể kết nối cơ sở dữ liệu PostgreSQL (cổng 5432). Hãy mở Docker Desktop và chạy container studyflow-postgres.",
                    "field": None,
                }
            },
        )
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "detail": {
                "code": "INTERNAL_SERVER_ERROR",
                "message": "Lỗi hệ thống nội bộ backend. Vui lòng thử lại sau.",
                "field": None,
            }
        },
    )


# Đăng ký các Router API V1
api_v1_prefix = "/api/v1"
app.include_router(health_router, prefix=api_v1_prefix)
app.include_router(courses_router, prefix=api_v1_prefix)
app.include_router(documents_router, prefix=api_v1_prefix)
app.include_router(assistant_router, prefix=api_v1_prefix)
app.include_router(quizzes_router, prefix=api_v1_prefix)
app.include_router(progress_router, prefix=api_v1_prefix)


@app.get("/")
async def root():
    return {
        "app": settings.APP_NAME,
        "docs": "/docs",
        "health": "/api/v1/health",
        "api_v1": "/api/v1",
    }
