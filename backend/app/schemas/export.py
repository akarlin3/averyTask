from pydantic import BaseModel


class ImportRequest(BaseModel):
    mode: str = "merge"  # "merge" or "replace"


class ImportResponse(BaseModel):
    tasks_imported: int
    projects_imported: int
    tags_imported: int
    habits_imported: int
    mode: str
