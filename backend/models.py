"""
Astra Voice Assistant FastAPI Backend Models
"""
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field

class ToolParameterSchema(BaseModel):
    name: str
    type: str = "string"
    description: str
    required: bool = True

class ToolDeclaration(BaseModel):
    name: str
    description: str
    parameters: List[ToolParameterSchema] = Field(default_factory=list)
    requires_confirmation: bool = False

class StructuredIntentSchema(BaseModel):
    action: Optional[str] = None # SEARCH, LAUNCH, CALL, MESSAGE, ALARM, NOTE, DEVICE_STATUS, CONVERSATION
    target_app: Optional[str] = None # YouTube, TikTok, Instagram, WhatsApp, Phone, Clock, Chrome, etc.
    goal: Optional[str] = None # User's actual goal
    parameters: Optional[Dict[str, Any]] = Field(default_factory=dict) # Cleaned parameters
    execution_steps: Optional[List[str]] = Field(default_factory=list)
    needs_clarification: bool = False
    clarification_question: Optional[str] = None
    verification_strategy: Optional[str] = None

class AgentPlanRequest(BaseModel):
    query: str
    available_tools: List[ToolDeclaration] = Field(default_factory=list)
    context: Optional[Dict[str, Any]] = None
    history: Optional[List[Dict[str, Any]]] = None

class AgentPlanResponse(BaseModel):
    type: str = Field(description="'tool_call', 'direct_response', 'confirmation_required', or 'clarification_needed'")
    tool: Optional[str] = None
    arguments: Optional[Dict[str, Any]] = None
    message: Optional[str] = None
    prompt: Optional[str] = None
    rationale: Optional[str] = None
    structured_intent: Optional[StructuredIntentSchema] = None

class AgentResultRequest(BaseModel):
    query: str
    tool: str
    arguments: Dict[str, Any] = Field(default_factory=dict)
    success: bool
    result: Optional[str] = None
    error: Optional[str] = None

class AgentResultResponse(BaseModel):
    type: str = "direct_response"
    message: str
    tool: Optional[str] = None
    arguments: Optional[Dict[str, Any]] = None

class ChatRequest(BaseModel):
    message: str
    context: Optional[Dict[str, Any]] = None

class ChatResponse(BaseModel):
    response: str
