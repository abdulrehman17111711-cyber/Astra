"""
Gemini Service for Astra Voice Assistant Backend
Handles structured JSON planning with Gemini API.
Uses GEMINI_API_KEY and GEMINI_MODEL (default: gemini-3.5-flash) from environment variables.
"""
import json
import os
import re
import urllib.request
import urllib.error
from typing import Any, Dict, List, Optional
from models import (
    AgentPlanRequest,
    AgentPlanResponse,
    AgentResultRequest,
    AgentResultResponse,
    StructuredIntentSchema
)

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3.5-flash")

SYSTEM_INSTRUCTION = """
You are the AI reasoning engine behind Astra Voice Assistant, an intelligent, voice-first Android assistant created by Abdul Rehman.
Your role is to understand natural conversational English, Roman Urdu, Hindi, and mixed-language instructions (e.g. "YouTube pe search karo CID", "TikTok pe funny videos dhoondo", "Ammi ko call karo", "WhatsApp pe Bhai ko message bhejo main aa raha hoon", "Subah 7 baje ka alarm lagao").

You must NEVER require rigid command templates. Instead, understand the user's natural intent and produce a structured JSON intent with validated tool arguments.

CRITICAL EXTRACTION RULES:
1. Identify the user's actual goal.
2. Identify the target app or system feature (e.g. YouTube, TikTok, Instagram, WhatsApp, Spotify, Chrome, Camera, Clock, Phone, Contacts, Settings).
3. Extract clean parameters (query, recipient, message, time, sim):
   - CRITICAL: Never include conversational filler verbs or particles like "karo", "kar do", "karna", "search", "dhoondo", "pe", "par", "mein", "chalao", "kholo", "please" in the search query or parameters unless explicitly intended by the user!
   - Example: "YouTube pe search karo CID" -> Target app: "YouTube", Action: "SEARCH", Query: "CID".
   - Example: "TikTok pe search karo cooking recipes" -> Target app: "TikTok", Action: "SEARCH", Query: "cooking recipes".
   - Example: "Instagram pe search karo tech news" -> Target app: "Instagram", Action: "SEARCH", Query: "tech news".
   - Example: "Ammi ko call karo" -> Target app: "Phone", Action: "CALL", Recipient: "Ammi".
   - Example: "WhatsApp pe Ali ko bolo meeting cancel ho gayi" -> Target app: "WhatsApp", Action: "MESSAGE", Recipient: "Ali", Message: "meeting cancel ho gayi".
4. Determine whether clarification is genuinely necessary. If information is missing and cannot be inferred, set needs_clarification=true with a clarification question.
5. Define how the result can be verified on Android (verification_strategy).
6. Map to the best registered tool from the available tools list:
   - YouTube search: "youtube_search" or "search_app"
   - Other app searches (TikTok, Instagram, Spotify, Play Store, etc.): "search_app"
   - General web search: "browser_tool" or "web_search"
   - Opening any app: "open_app" (with appName or packageName)
   - Calling: "call_contact" (or "call_phone") -> ALWAYS requires confirmation!
   - Messaging: "send_message" or "whatsapp_message" -> ALWAYS requires confirmation!
   - Setting alarm: "alarm_tool" (hour, minute, amPm)
   - Storing fact: "quick_note" (content, category)
   - Checking device/battery: "device_status" or "device_info"
   - Checking time: "world_clock"
   - General dialogue/questions: direct_response

OUTPUT STRICT JSON MATCHING THIS SCHEMA:
{
  "type": "tool_call" | "confirmation_required" | "direct_response" | "clarification_needed",
  "tool": "<tool_name_or_null>",
  "arguments": { <tool_arguments_or_null> },
  "message": "<spoken_response_or_clarification_question>",
  "prompt": "<confirmation_question_in_user_language_if_confirmation_required>",
  "rationale": "<brief_reasoning>",
  "structured_intent": {
    "action": "SEARCH" | "LAUNCH" | "CALL" | "MESSAGE" | "ALARM" | "NOTE" | "DEVICE_STATUS" | "CLOCK" | "CONVERSATION",
    "target_app": "<app_name_or_null>",
    "goal": "<user_actual_goal>",
    "parameters": { <cleaned_parameters> },
    "execution_steps": [ "<step_1>", "<step_2>" ],
    "needs_clarification": false,
    "clarification_question": null,
    "verification_strategy": "<how_result_is_verified>"
  }
}
"""

def call_gemini_api(prompt: str, system_prompt: str = SYSTEM_INSTRUCTION) -> Optional[str]:
    api_key = os.getenv("GEMINI_API_KEY", "")
    model = os.getenv("GEMINI_MODEL", "gemini-3.5-flash")
    if not api_key:
        return None

    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
    payload = {
        "contents": [
            {"parts": [{"text": prompt}]}
        ],
        "systemInstruction": {
            "parts": [{"text": system_prompt}]
        },
        "generationConfig": {
            "responseMimeType": "application/json",
            "temperature": 0.2
        }
    }

    try:
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            candidates = data.get("candidates", [])
            if candidates:
                parts = candidates[0].get("content", {}).get("parts", [])
                if parts:
                    return parts[0].get("text", "")
    except Exception as e:
        print(f"[GeminiService] Error calling Gemini API: {e}")
    return None

def plan_agent_task(request: AgentPlanRequest) -> AgentPlanResponse:
    query = request.query.strip()
    tools_desc = "\n".join([
        f"- {t.name}: {t.description} (params: {[p.name for p in t.parameters]}) (requires_confirmation={t.requires_confirmation})"
        for t in request.available_tools
    ])

    history_desc = ""
    if request.history:
        history_desc = "\nRecent Conversation History:\n" + "\n".join([
            f"- User: {h.get('query', '')} -> Assistant: {h.get('response', '')}"
            for h in request.history[-5:]
        ])

    context_desc = ""
    if request.context:
        context_desc = f"\nContext/Memory: {json.dumps(request.context)}"

    prompt = f"""
User Query: "{query}"

Available Tools:
{tools_desc}
{history_desc}
{context_desc}

Analyze intent, extract clean parameters, identify actual goal and target app, and return strictly valid JSON matching the schema.
"""
    raw_response = call_gemini_api(prompt)
    if raw_response:
        try:
            parsed = json.loads(raw_response)
            structured_data = parsed.get("structured_intent")
            structured_obj = None
            if isinstance(structured_data, dict):
                structured_obj = StructuredIntentSchema(
                    action=structured_data.get("action"),
                    target_app=structured_data.get("target_app"),
                    goal=structured_data.get("goal"),
                    parameters=structured_data.get("parameters", {}),
                    execution_steps=structured_data.get("execution_steps", []),
                    needs_clarification=structured_data.get("needs_clarification", False),
                    clarification_question=structured_data.get("clarification_question"),
                    verification_strategy=structured_data.get("verification_strategy")
                )

            return AgentPlanResponse(
                type=parsed.get("type", "direct_response"),
                tool=parsed.get("tool"),
                arguments=parsed.get("arguments"),
                message=parsed.get("message"),
                prompt=parsed.get("prompt"),
                rationale=parsed.get("rationale"),
                structured_intent=structured_obj
            )
        except Exception as e:
            print(f"[GeminiService] JSON parse error: {e}")

    # Resilient fallback semantic plan
    return fallback_semantic_plan(request)

def evaluate_agent_result(request: AgentResultRequest) -> AgentResultResponse:
    if not request.success:
        err_msg = request.error or "Unknown failure"
        return AgentResultResponse(
            type="direct_response",
            message=f"I couldn't execute {request.tool}: {err_msg}"
        )

    prompt = f"""
The user asked: "{request.query}"
The tool '{request.tool}' was executed locally on Android with arguments {json.dumps(request.arguments)}.
The local execution output is: "{request.result}".

Provide a natural, concise spoken answer confirming the action to the user in their language (English or Roman Urdu).
Respond with JSON: {{ "message": "<concise response>" }}
"""
    raw_response = call_gemini_api(prompt)
    if raw_response:
        try:
            parsed = json.loads(raw_response)
            return AgentResultResponse(
                type="direct_response",
                message=parsed.get("message", request.result or "Done.")
            )
        except Exception:
            pass

    return AgentResultResponse(
        type="direct_response",
        message=request.result or f"Action {request.tool} completed successfully."
    )

def clean_query_text(query: str, app_name: str) -> str:
    cleaned = query
    if app_name:
        cleaned = re.sub(rf"(?i)\b{re.escape(app_name)}\b", " ", cleaned)
    noise = r"(?i)\b(pe|par|mein|me|in|on|at|for|to|aur|and|kholo|chalao|start|launch|open|search karo|search kar do|search karna|search for|search|dhoondo|talash karo|dekho|karo|kar do|karna|kardo|please|batao|dikhao|lagao|lagana|bhejo|bhejna|milao)\b"
    cleaned = re.sub(noise, " ", cleaned)
    cleaned = re.sub(r"[\"\'`:;?,.]", " ", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned

def fallback_semantic_plan(request: AgentPlanRequest) -> AgentPlanResponse:
    query = request.query.strip()
    lower = query.lower()

    # Creator Attribution
    if any(k in lower for k in ["who made you", "who created you", "creator", "developer", "kisne banaya"]):
        return AgentPlanResponse(
            type="direct_response",
            message="I am Astra Voice Assistant, engineered by Abdul Rehman as a voice-first Android assistant.",
            structured_intent=StructuredIntentSchema(
                action="CONVERSATION",
                target_app="System",
                goal="Identify creator of Astra Voice Assistant",
                needs_clarification=False,
                verification_strategy="Attribution delivered"
            )
        )

    # Calling with confirmation
    if any(k in lower for k in ["call karo", "call kar do", "call", "dial", "phone milao"]):
        contact = query
        for k in ["call karo", "call kar do", "ko call", "call", "sim 1 se", "sim 2 se", "phone milao", "please"]:
            contact = re.sub(rf"(?i)\b{re.escape(k)}\b", " ", contact)
        contact = re.sub(r"\s+", " ", contact).strip() or "Contact"
        sim = "SIM 1" if "sim 1" in lower else ("SIM 2" if "sim 2" in lower else "Default SIM")
        tool = "call_phone" if any(c.isdigit() for c in contact) and len([c for c in contact if c.isdigit()]) >= 5 else "call_contact"
        args = {"phoneNumber": contact, "sim": sim} if tool == "call_phone" else {"contactName": contact, "sim": sim}

        return AgentPlanResponse(
            type="confirmation_required",
            tool=tool,
            prompt=f"{contact} ko call karun via {sim}?",
            arguments=args,
            rationale="Telephony action requires user confirmation",
            structured_intent=StructuredIntentSchema(
                action="CALL",
                target_app="Phone",
                goal=f"Call {contact} on {sim}",
                parameters=args,
                execution_steps=["Lookup contact", "Request user confirmation", "Place call"],
                needs_clarification=False,
                verification_strategy="Verify outgoing call intent dispatched"
            )
        )

    # Messaging (WhatsApp / SMS)
    if any(k in lower for k in ["send message", "message bhejo", "text", "whatsapp", "sms", "bolo", "kaho"]):
        is_wa = "whatsapp" in lower
        contact = "Contact"
        msg = "Hello"
        if " ke " in query or " that " in query:
            parts = re.split(r"(?i)\b(ke|that)\b", query, maxsplit=1)
            contact = clean_query_text(parts[0], "WhatsApp")
            msg = parts[-1].strip()
        else:
            contact = clean_query_text(query, "WhatsApp")

        tool = "whatsapp_message" if is_wa else "send_message"
        args = {"contactName": contact, "message": msg} if is_wa else {"contactName": contact, "message": msg, "application": "SMS"}

        return AgentPlanResponse(
            type="confirmation_required",
            tool=tool,
            prompt=f"Send message to {contact}: \"{msg}\"?",
            arguments=args,
            rationale="Sending messages requires user confirmation",
            structured_intent=StructuredIntentSchema(
                action="MESSAGE",
                target_app="WhatsApp" if is_wa else "SMS",
                goal=f"Send message to {contact}",
                parameters=args,
                execution_steps=["Lookup recipient", "Request confirmation", "Send message"],
                needs_clarification=False,
                verification_strategy="Verify message intent dispatched"
            )
        )

    # Alarms
    if any(k in lower for k in ["alarm", "wake me up", "jaga dena"]):
        hour_match = re.search(r"(\d{1,2})", query)
        hour = int(hour_match.group(1)) if hour_match else 7
        am_pm = "PM" if any(p in lower for p in ["pm", "shaam", "raat"]) else "AM"
        args = {"hour": hour, "minute": 0, "amPm": am_pm, "message": "Astra Alarm"}
        return AgentPlanResponse(
            type="tool_call",
            tool="alarm_tool",
            arguments=args,
            rationale="User requested setting an alarm",
            structured_intent=StructuredIntentSchema(
                action="ALARM",
                target_app="Clock",
                goal=f"Set alarm for {hour}:00 {am_pm}",
                parameters=args,
                execution_steps=["Validate time parameters", "Schedule alarm intent"],
                needs_clarification=False,
                verification_strategy="Verify system alarm set"
            )
        )

    # In-App Search (YouTube, TikTok, Instagram, Spotify, Browser, etc.)
    apps_map = {
        "youtube": "YouTube",
        "tiktok": "TikTok",
        "instagram": "Instagram",
        "insta": "Instagram",
        "spotify": "Spotify",
        "browser": "Browser",
        "chrome": "Chrome",
        "google": "Google"
    }

    detected_app = None
    for k, v in apps_map.items():
        if re.search(rf"\b{k}\b", lower):
            detected_app = v
            break

    is_search = any(k in lower for k in ["search", "dhoondo", "talash", "dekho", "find"])
    is_launch = any(k in lower for k in ["kholo", "open", "chalao", "start", "launch"])

    if detected_app and (is_search or (is_launch and "search" in lower)):
        clean_q = clean_query_text(query, detected_app)
        if not clean_q:
            # If no query specified, open app
            return AgentPlanResponse(
                type="tool_call",
                tool="open_app",
                arguments={"appName": detected_app},
                rationale=f"User asked to open {detected_app}",
                structured_intent=StructuredIntentSchema(
                    action="LAUNCH",
                    target_app=detected_app,
                    goal=f"Open {detected_app}",
                    parameters={"appName": detected_app},
                    execution_steps=[f"Resolve {detected_app} package", "Launch activity"],
                    needs_clarification=False,
                    verification_strategy=f"Verify {detected_app} launched"
                )
            )

        tool_to_use = "youtube_search" if detected_app == "YouTube" else "search_app"
        tool_args = {"query": clean_q} if detected_app == "YouTube" else {"application": detected_app, "query": clean_q}

        return AgentPlanResponse(
            type="tool_call",
            tool=tool_to_use,
            arguments=tool_args,
            rationale=f"User requested in-app search inside {detected_app}",
            structured_intent=StructuredIntentSchema(
                action="SEARCH",
                target_app=detected_app,
                goal=f"Search {detected_app} for \"{clean_q}\"",
                parameters={"target_app": detected_app, "query": clean_q},
                execution_steps=[f"Resolve {detected_app} search intent", f"Dispatch query: {clean_q}"],
                needs_clarification=False,
                verification_strategy=f"Verify search intent launched in {detected_app}"
            )
        )

    if detected_app and is_launch:
        return AgentPlanResponse(
            type="tool_call",
            tool="open_app",
            arguments={"appName": detected_app},
            rationale=f"User asked to open {detected_app}",
            structured_intent=StructuredIntentSchema(
                action="LAUNCH",
                target_app=detected_app,
                goal=f"Open {detected_app}",
                parameters={"appName": detected_app},
                execution_steps=[f"Resolve {detected_app} package", "Launch activity"],
                needs_clarification=False,
                verification_strategy=f"Verify {detected_app} launched"
            )
        )

    # General Web Search
    if is_search:
        clean_q = clean_query_text(query, "")
        if clean_q:
            return AgentPlanResponse(
                type="tool_call",
                tool="browser_tool",
                arguments={"queryOrUrl": clean_q},
                rationale="User requested web search",
                structured_intent=StructuredIntentSchema(
                    action="SEARCH",
                    target_app="Browser",
                    goal=f"Search web for \"{clean_q}\"",
                    parameters={"query": clean_q},
                    execution_steps=["Construct ACTION_WEB_SEARCH intent", "Launch default browser"],
                    needs_clarification=False,
                    verification_strategy="Verify browser launched"
                )
            )

    # Memory / Notes
    if any(k in lower for k in ["remember", "yaad rakho", "save note"]):
        content = re.sub(r"(?i)^(remember that|remember|yaad rakho ke|yaad rakho|save note:?)\s*", "", query).strip()
        return AgentPlanResponse(
            type="tool_call",
            tool="quick_note",
            arguments={"content": content, "category": "personal"},
            rationale="Memory storage",
            structured_intent=StructuredIntentSchema(
                action="NOTE",
                target_app="Memory",
                goal=f"Save note: \"{content}\"",
                parameters={"content": content},
                execution_steps=["Persist to Room DB", "Confirm storage"],
                needs_clarification=not bool(content),
                verification_strategy="Verify Room entity saved"
            )
        )

    # Device Status
    if any(k in lower for k in ["battery", "device status", "specs", "phone info"]):
        return AgentPlanResponse(
            type="tool_call",
            tool="device_status",
            arguments={},
            rationale="Hardware state inspect",
            structured_intent=StructuredIntentSchema(
                action="DEVICE_STATUS",
                target_app="SystemTelemetry",
                goal="Inspect battery and device state",
                parameters={},
                execution_steps=["Query BatteryManager", "Format telemetry"],
                needs_clarification=False,
                verification_strategy="Verify battery level returned"
            )
        )

    # Clock
    if any(k in lower for k in ["time", "clock", "waqt", "date"]):
        return AgentPlanResponse(
            type="tool_call",
            tool="world_clock",
            arguments={"timezone": "LOCAL"},
            rationale="World clock inspect",
            structured_intent=StructuredIntentSchema(
                action="CLOCK",
                target_app="WorldClock",
                goal="Check current time",
                parameters={"timezone": "LOCAL"},
                execution_steps=["Format localized time"],
                needs_clarification=False,
                verification_strategy="Verify formatted time returned"
            )
        )

    # Default Conversation
    return AgentPlanResponse(
        type="direct_response",
        message=f"I heard \"{query}\". How can I assist you with your apps or device?",
        structured_intent=StructuredIntentSchema(
            action="CONVERSATION",
            target_app=None,
            goal="Answer user conversationally",
            parameters={"query": query},
            execution_steps=["Generate spoken answer"],
            needs_clarification=False,
            verification_strategy="Conversational answer delivered"
        )
    )
