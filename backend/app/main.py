from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from contextlib import asynccontextmanager

from app.core.config import settings
from app.core.database import AsyncSessionLocal, init_db
from app.services.document_service import recover_orphaned_processing_documents
from app.api.v1.health import router as health_router
from app.api.v1.courses import router as courses_router
from app.api.v1.documents import router as documents_router
from app.api.v1.assistant import router as assistant_router
from app.api.v1.quizzes import router as quizzes_router
from app.api.v1.progress import router as progress_router


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
