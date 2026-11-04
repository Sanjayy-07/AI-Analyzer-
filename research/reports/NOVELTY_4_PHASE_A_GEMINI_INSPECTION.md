# Novelty 4 — Phase A: Gemini Integration Inspection Report

**Project:** AI-Powered Dependency & Architecture Refactoring Analyzer  
**Module:** Just-In-Time Software Defect Prediction & Architecture Refactoring  
**Feature:** Novelty 4 — LLM-Based Architecture Explanations with Automatic Claim Verification  
**Date:** 2026-11-04  
**Status:** COMPLETE & VERIFIED  

---

## 1. Executive Summary

This inspection examines the existing Generative AI infrastructure within `com.archanalyzer.ai` to prepare for the implementation of **Novelty 4: LLM-Based Architecture Explanations with Automatic Claim Verification**.

### Core Findings
1. **Zero External LLM SDK Dependencies:** The system utilizes Java 21 standard library `java.net.http.HttpClient` exclusively for API communication with Google Gemini (`https://generativelanguage.googleapis.com/v1beta/models/...:generateContent`). No third-party LLM SDKs (e.g., langchain, spring-ai, or google-genai) are present or required, ensuring tight footprint and dependency stability.
2. **Robust Multi-Directory `.env` Loading:** `AiConfig.java` parses environment files across four parent directories and `user.dir`, securely locating keys without hardcoding or leaking credentials.
3. **Model Selection & Migration Policy:** Legacy Gemini models (`gemini-1.5-*`, `gemini-2.0-*`, `gemini-2.5-*`, `gemini-pro`) are automatically migrated to modern standard endpoints (`gemini-3.5-flash`, `gemini-3.5-flash-lite`, `gemini-3.8-flash`).
4. **Deterministic Offline Testability:** `MockLlmProvider.java` provides a complete test double that intercepts completions and chat sessions, enabling 100% deterministic test coverage of prompt assembly, JSON parsing, and claim verification without live network dependencies or quota consumption.
5. **No Key Exposure:** API keys are never serialized to JSON endpoints, logs, JSP views, or git repositories.

---

## 2. Existing AI Infrastructure Audit

| Component | File Path | Role & Operational Status |
|---|---|---|
| `AiConfig` | `core/.../ai/AiConfig.java` | Environment & `.env` parser; handles timeouts (default 90s), model alias resolution, and credentials. |
| `LlmProvider` | `core/.../ai/LlmProvider.java` | Standard interface for single-turn completion and multi-turn chat (`generateCompletion`, `generateChatCompletion`). |
| `GeminiLlmProvider` | `core/.../ai/GeminiLlmProvider.java` | Direct HTTP implementation communicating with Gemini v1beta REST API; handles JSON schema output and retry failover. |
| `MockLlmProvider` | `core/.../ai/MockLlmProvider.java` | Programmable test double supporting prompt assertions and simulated responses. |
| `AiExplanationService`| `core/.../ai/AiExplanationService.java`| Domain explanation and refactoring recommendation service. |
| `AnalysisApiServlet` | `webapp/.../servlet/AnalysisApiServlet.java`| REST API servlet exposing `/api/projects/current/ai/*` and `/api/projects/chat`. |

---

## 3. Novelty 4 Architectural Integration Plan

The objective of Novelty 4 is to solve the fundamental hallucination problem of LLM-generated software architecture explanations. Rather than presenting unverified LLM text as authoritative, Novelty 4 establishes **Java as the authoritative source of truth**.

```
+-----------------------------------------------------------------------------------+
|                            Java Core (Ground Truth)                               |
|                                                                                   |
|  [DependencyGraph]  [MetricsEngine]  [GraphAlgorithms]  [ArchitectureRiskService] |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
                     +---------------------------------------+
                     |    EvidenceCollector (Authoritative)  |
                     |  Extracts immutable ArchitectureEvidence|
                     +---------------------------------------+
                                         |
                     +-------------------+-------------------+
                     |                                       |
                     v                                       v
         +-----------------------+              +--------------------------+
         | ExplanationPrompt-    |              | ClaimVerificationEngine  |
         | Builder (JSON Schema) |              | - MetricClaimVerifier    |
         +-----------------------+              | - DependencyClaimVerifier|
                     |                          | - CycleClaimVerifier     |
                     v                          | - RiskClaimVerifier      |
         +-----------------------+              +--------------------------+
         | GeminiLlmProvider     |                           ^
         | (or MockLlmProvider)  |                           |
         +-----------------------+                           |
                     |                                       |
                     v                                       |
         +-----------------------+                           |
         | ExplanationResponse-  |---- Extracted Claims -----+
         | Parser (JSON/Claims)  |
         +-----------------------+
                     |
                     v
         +---------------------------------------------------+
         |             VerifiedExplanation                   |
         |  - Overview & Findings                            |
         |  - Verified Claims (Green)                        |
         |  - Contradicted / Unsupported Claims (Red/Amber)  |
         |  - Verification Rate (%)                          |
         +---------------------------------------------------+
```

### Verification Taxonomy
1. `METRIC_FACT`: Asserts numerical metric on a component (LOC, CC, instability, fan-in, fan-out, cyclomatic complexity). Verified within bounded numerical tolerance ($\pm 0.05$ absolute or $1\%$ relative).
2. `DEPENDENCY_FACT`: Asserts directed coupling $A \to B$. Verified against `DependencyGraph.containsEdge(from, to)`. Detects inverted directions.
3. `CYCLE_FACT`: Asserts that component $C$ is part of a circular dependency cycle. Verified against Tarjan SCC decomposition (`GraphAlgorithms.stronglyConnectedComponents`).
4. `RISK_FACT`: Asserts architecture risk level or prediction score. Verified against Novelty 3 `ArchitectureRiskService` outputs.
5. `INTERPRETATION` & `RECOMMENDATION`: Classified and verified against referenced components and valid refactoring operations; flagged if referencing non-existent components.

---

## 4. Preservation & Safety Guarantees

1. `sample-demo-project` is strictly isolated and will never be touched, modified, or accessed.
2. Frozen Phase 3A research artifacts and Phase 4 test datasets remain untouched.
3. No duplicate LLM provider classes or API clients will be created; Novelty 4 reuses `AiConfig` and `LlmProvider`.
4. All existing tests (142 core tests including Novelty 1, 2, and 3 regression tests) will continue to pass.
