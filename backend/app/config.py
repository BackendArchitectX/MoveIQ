from functools import lru_cache
from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="MOVEIQ_", env_file="../.env", extra="ignore")
    env: str = "development"
    data_root: Path = Path("../data/moveinsync/raw")
    normalized_root: Path = Path("../data/normalized")
    database_url: str = "postgresql://moveiq:moveiq@localhost:5432/moveiq"
    replay_speed: int = 60
    llm_provider: str = "mock"

@lru_cache
def get_settings() -> Settings:
    return Settings()
