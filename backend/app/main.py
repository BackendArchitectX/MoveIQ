from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="MoveIQ", description="Evidence-governed mobility operations intelligence", version="0.2.0")
app.add_middleware(CORSMiddleware, allow_origins=["http://localhost:5173"], allow_methods=["*"], allow_headers=["*"])

@app.get("/api/v1/health")
def health():
    return {"status": "ok", "service": "moveiq"}
