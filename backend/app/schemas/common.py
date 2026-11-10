from datetime import datetime, timezone
from typing import Annotated

from pydantic import PlainSerializer


def _serialize_utc_datetime(dt: datetime) -> str:
    if dt.tzinfo is None:
        utc_dt = dt.replace(tzinfo=timezone.utc)
    else:
        utc_dt = dt.astimezone(timezone.utc)
    return utc_dt.strftime("%Y-%m-%dT%H:%M:%SZ")


UtcDatetime = Annotated[
    datetime,
    PlainSerializer(_serialize_utc_datetime, return_type=str, when_used="json"),
]
