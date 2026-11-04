# Novelty 4 — Research Methodology: Automatic Claim Verification for LLM-Based Architecture Explanations

**Project:** AI-Powered Dependency & Architecture Refactoring Analyzer  
**Subsystem:** LLM Explanation Verification Gateway  
**Document:** Research Evaluation Methodology & Formal Verification Protocol  
**Date:** 2026-11-04  
**Status:** PUBLICATION-GRADE RESEARCH PROTOCOL  

---

## 1. Problem Formulation: The Hallucination Dilemma in Architecture AI

Large Language Models (LLMs) frequently generate persuasive, coherent explanations of software architecture that contain subtle, critical hallucinations:
1. **Fabricated Metrics:** Asserting arbitrary cyclomatic complexities, lines of code, or coupling fan-outs.
2. **Topological Direction Inversion:** Claiming component $A$ calls component $B$ when in reality $B$ depends on $A$ (violating the Dependency Inversion Principle).
3. **Cycle Hallucination:** Claiming circular dependency cycles exist where the architecture is acyclic, or overlooking real Strongly Connected Components (SCCs).
4. **Phantom Components:** Recommending refactorings for packages or classes that do not exist in the codebase.
5. **Defect Risk Misrepresentation:** Recommending high-risk modifications to fragile, high-churn classes without empirical grounding.

In mission-critical software refactoring, an unverified LLM explanation is unacceptable. **Novelty 4 establishes the paradigm: Java Code is Ground Truth.** The LLM serves solely as a language synthesizer, while a deterministic Java Claim Verification Engine audits every factual assertion against immutable AST, graph, and ML evidence.

---

## 2. Formal Mathematical Formulation

Let the software system under analysis be represented as a tuple:
$$\mathcal{S} = \langle \mathcal{M}, \mathcal{G}, \mathcal{P}, \mathcal{R} \rangle$$
where:
- $\mathcal{M}$ is the set of parsed AST components $C = \{c_1, c_2, \dots, c_n\}$;
- $\mathcal{G} = (V, E)$ is the directed dependency graph with vertices $V = C$ and directed couplings $E \subseteq V \times V$;
- $\mathcal{P} = \{\mu(c) \mid c \in C\}$ is the set of deterministic AST and graph metrics computed by `MetricsEngine`;
- $\mathcal{R} = \{\rho(c) \mid c \in C\}$ is the set of defect and change risk probabilities predicted by `ArchitectureRiskService` (Novelty 3).

### Authoritative Architecture Evidence Register
The deterministic engine compiles $\mathcal{S}$ into an immutable register of evidence items:
$$\mathcal{E} = \{e_1, e_2, \dots, e_M\}$$
where each evidence item $e_k$ is an immutable tuple:
$$e_k = \langle \text{id}_k, \text{type}_k, \text{subject}_k, \text{target}_k, \text{metric}_k, v_k^*, \text{engine}_k \rangle$$

### Claim Extraction & Representation
An LLM response $R$ is parsed into structured findings, recommendations, and explicit claims:
$$\mathcal{C} = \{\kappa_1, \kappa_2, \dots, \kappa_N\}$$
where each claim $\kappa_j$ is defined by:
$$\kappa_j = \langle \text{id}_j, \tau_j, s_j, t_j, m_j, \hat{v}_j, \text{text}_j, \mathcal{E}_{\text{cited}, j} \rangle$$
with claim taxonomy $\tau_j \in \{\text{METRIC\_FACT}, \text{DEPENDENCY\_FACT}, \text{CYCLE\_FACT}, \text{RISK\_FACT}, \text{INTERPRETATION}, \text{RECOMMENDATION}\}$.

---

## 3. Deterministic Verification Decision Rules

Each claim $\kappa_j$ is evaluated against $\mathcal{E}$ by specialized deterministic verifiers according to strict decision rules:

### 3.1 Metric Fact Verifier ($\tau = \text{METRIC\_FACT}$)
Given claimed value $\hat{v}$ and ground truth value $v^* = \mu(s, m)$:
$$\text{Status}(\kappa) = \begin{cases}
\text{UNSUPPORTED} & \text{if } s \notin V \lor v^* \text{ is undefined} \\
\text{VERIFIED} & \text{if } |\hat{v} - v^*| \le 0.05 \lor \frac{|\hat{v} - v^*|}{\max(1.0, |v^*|)} \le 0.05 \\
\text{PARTIALLY\_VERIFIED} & \text{if } 0.05 < \frac{|\hat{v} - v^*|}{\max(1.0, |v^*|)} \le 0.20 \lor |\hat{v} - v^*| \le 1.0 \\
\text{CONTRADICTED} & \text{otherwise}
\end{cases}$$

### 3.2 Dependency Fact Verifier ($\tau = \text{DEPENDENCY\_FACT}$)
Given claimed coupling $(s \to t)$:
$$\text{Status}(\kappa) = \begin{cases}
\text{UNSUPPORTED} & \text{if } s \notin V \lor t \notin V \\
\text{VERIFIED} & \text{if } (s, t) \in E \\
\text{CONTRADICTED (Inverted)} & \text{if } (s, t) \notin E \land (t, s) \in E \\
\text{CONTRADICTED (Fabricated)} & \text{if } (s, t) \notin E \land (t, s) \notin E
\end{cases}$$

### 3.3 Cycle Fact Verifier ($\tau = \text{CYCLE\_FACT}$)
Let $\text{SCC}(\mathcal{G}) = \{S_1, S_2, \dots, S_K\}$ be the Tarjan strongly connected components of $\mathcal{G}$. A component $s$ is topologically in a cycle if $\exists S_k$ such that $s \in S_k$ and $|S_k| > 1$.
$$\text{Status}(\kappa) = \begin{cases}
\text{UNSUPPORTED} & \text{if } s \notin V \\
\text{VERIFIED} & \text{if claimed in cycle} \land s \in \text{Cycle} \\
\text{VERIFIED} & \text{if claimed acyclic} \land s \notin \text{Cycle} \\
\text{CONTRADICTED} & \text{otherwise}
\end{cases}$$

### 3.4 Risk Fact Verifier ($\tau = \text{RISK\_FACT}$)
Given claimed risk score $\hat{r}$ or category $\hat{\text{cat}}$ and observed Random Forest prediction $r^*, \text{cat}^*$:
$$\text{Status}(\kappa) = \begin{cases}
\text{UNSUPPORTED} & \text{if } s \notin V \lor r^* \text{ is undefined} \\
\text{VERIFIED} & \text{if } \hat{\text{cat}} = \text{cat}^* \lor |\hat{r} - r^*| \le 0.15 \\
\text{PARTIALLY\_VERIFIED} & \text{if } \hat{\text{cat}} \text{ is adjacent to } \text{cat}^* \\
\text{CONTRADICTED} & \text{if } (\hat{\text{cat}} = \text{HIGH} \land \text{cat}^* = \text{LOW}) \lor (\hat{\text{cat}} = \text{LOW} \land \text{cat}^* = \text{HIGH})
\end{cases}$$

---

## 4. Evaluation Metrics for Architectural Explanations

1. **Verification Rate ($VR$):**
   $$VR = \frac{N_{\text{verified}} + 0.5 \times N_{\text{partially}}}{N_{\text{verifiable}}} \times 100\%$$
   Measures the factual fidelity of verifiable statements made in the explanation.

2. **Contradiction Rate ($CR$):**
   $$CR = \frac{N_{\text{contradicted}}}{N_{\text{total\_claims}}} \times 100\%$$
   Measures the prevalence of critical hallucinations and topological distortions.

3. **Unsupported Rate ($UR$):**
   $$UR = \frac{N_{\text{unsupported}}}{N_{\text{total\_claims}}} \times 100\%$$
   Measures references to unobserved classes, phantom components, or unrecorded metrics.

4. **Hallucination Detection Sensitivity ($HDS$):**
   $$HDS = \frac{\text{Detected Injected Hallucinations}}{\text{Total Injected Adversarial Hallucinations}} \times 100\%$$
   Must achieve **100.0%** in adversarial benchmark suites.

5. **Publication-Readiness Invariant:**
   $$\text{Publication Ready} \iff CR = 0.0\% \land VR \ge 80.0\%$$
