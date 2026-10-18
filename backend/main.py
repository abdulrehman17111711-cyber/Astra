"""
Astra AI - FastAPI Backend Server
Coordinates Gemini AI agent planning, tool schema analysis, and response generation.
"""
import os
import sys
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from models import (
    AgentPlanRequest,
    AgentPlanResponse,
    AgentResultRequest,
    AgentResultResponse,
    ChatRequest,
    ChatResponse
)
from gemini_service import plan_agent_task, evaluate_agent_result, call_gemini_api

app = FastAPI(
    title="Astra Voice Assistant Agent Backend",
    description="Agentic tool-planning and response synthesis server for Astra Voice Assistant",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
def health_check():
    """Health check endpoint to verify backend status and model configuration."""
    return {
        "status": "ok",
        "service": "Astra Voice Assistant Agent Backend",
        "model": os.getenv("GEMINI_MODEL", "gemini-2.5-flash"),
        "api_key_configured": bool(os.getenv("GEMINI_API_KEY"))
    }

@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest):
    """Direct conversational endpoint without tool execution."""
    raw = call_gemini_api(request.message)
    if raw:
        return ChatResponse(response=raw)
    return ChatResponse(response=f"I received your message: \"{request.message}\". Astra Voice Assistant is online.")

@app.post("/agent/plan", response_model=AgentPlanResponse)
def plan_action(request: AgentPlanRequest):
    """
    Core agent planning endpoint:
    Analyzes user speech/text and available tools, returning a structured tool_call,
    confirmation_required, or direct_response.
    """
    try:
        plan = plan_agent_task(request)
        return plan
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/agent/result", response_model=AgentResultResponse)
def evaluate_result(request: AgentResultRequest):
    """
    Post-execution evaluation endpoint:
    Receives local execution output from Android and generates natural conversational response.
    """
    try:
        response = evaluate_agent_result(request)
        return response
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", 8000))
    print(f"Starting Astra AI FastAPI Backend on {host}:{port}...")
    uvicorn.run(app, host=host, port=port)
