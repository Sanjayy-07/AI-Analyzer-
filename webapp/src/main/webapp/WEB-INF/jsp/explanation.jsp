<%@ page contentType="text/html;charset=UTF-8" language="java" isELIgnored="true" %>
<%@ page import="com.archanalyzer.explanation.verification.VerifiedExplanation" %>
<%@ page import="com.archanalyzer.explanation.verification.ClaimVerificationResult" %>
<%@ page import="com.archanalyzer.explanation.verification.ClaimVerificationStatus" %>
<%@ page import="com.archanalyzer.explanation.evidence.ArchitectureEvidence" %>
<%@ page import="com.archanalyzer.explanation.evidence.EvidenceItem" %>
<%@ page import="com.archanalyzer.explanation.llm.ExplanationResponse.FindingDto" %>
<%@ page import="com.archanalyzer.explanation.llm.ExplanationResponse.RecommendationDto" %>
<%@ page import="com.archanalyzer.pipeline.AnalyzerPipeline.PipelineResult" %>
<%@ page import="java.util.*" %>
<%
    PipelineResult baseline = (PipelineResult) request.getAttribute("baseline");
    if (baseline == null || baseline.model == null || baseline.graph == null) {
        response.sendRedirect(request.getContextPath() + "/projects/upload?msg=noproject");
        return;
    }
    String projectName = baseline.model.getProjectName() != null ? baseline.model.getProjectName() : "Analyzed Project";
    VerifiedExplanation explanation = (VerifiedExplanation) request.getAttribute("explanation");
    ArchitectureEvidence evidence = (ArchitectureEvidence) request.getAttribute("evidence");
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LLM Architecture Explanations with Automatic Claim Verification — <%= projectName %></title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="<%= ctx %>/css/main.css">
    <style>
        :root {
            --exp-bg: #090d16;
            --exp-panel: #111726;
            --exp-panel-2: #172033;
            --exp-border: #1e293b;
            --exp-accent: #3b82f6;
            --exp-accent-glow: rgba(59,130,246,0.3);
            --exp-success: #10b981;
            --exp-warning: #f59e0b;
            --exp-danger: #ef4444;
            --exp-purple: #8b5cf6;
            --exp-cyan: #06b6d4;
        }

        body {
            background: var(--exp-bg);
            color: #e2e8f0;
            font-family: 'Inter', system-ui, -apple-system, sans-serif;
            margin: 0;
            padding: 0;
        }

        .container {
            max-width: 1400px;
            margin: 0 auto;
            padding: 28px 24px 60px;
        }

        .hero-banner {
            background: linear-gradient(135deg, rgba(59,130,246,0.12), rgba(139,92,246,0.08));
            border: 1px solid var(--exp-border);
            border-radius: 14px;
            padding: 24px 28px;
            margin-bottom: 24px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .hero-title {
            font-size: 1.45rem;
            font-weight: 700;
            margin: 0 0 6px 0;
            color: #f8fafc;
        }
        .hero-sub {
            color: #94a3b8;
            font-size: 0.92rem;
            margin: 0;
        }
        .hero-meta {
            text-align: right;
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.82rem;
            color: #64748b;
        }
        .hero-meta span { color: #38bdf8; font-weight: 600; }

        .scorecards-grid {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 16px;
            margin-bottom: 24px;
        }
        .scorecard {
            background: var(--exp-panel);
            border: 1px solid var(--exp-border);
            border-radius: 12px;
            padding: 18px 20px;
            display: flex;
            flex-direction: column;
            gap: 6px;
        }
        .scorecard-label {
            font-size: 0.78rem;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: #64748b;
            font-weight: 600;
        }
        .scorecard-value {
            font-size: 1.85rem;
            font-weight: 700;
            line-height: 1.1;
        }
        .scorecard-desc {
            font-size: 0.82rem;
            color: #94a3b8;
        }
        .val-success { color: var(--exp-success); }
        .val-danger { color: var(--exp-danger); }
        .val-accent { color: var(--exp-accent); }
        .val-purple { color: var(--exp-purple); }

        .tabs-header {
            display: flex;
            gap: 8px;
            border-bottom: 1px solid var(--exp-border);
            margin-bottom: 20px;
        }
        .tab-btn {
            background: none;
            border: none;
            color: #94a3b8;
            padding: 12px 18px;
            font-size: 0.92rem;
            font-weight: 600;
            cursor: pointer;
            border-bottom: 2px solid transparent;
            transition: all 0.2s;
        }
        .tab-btn:hover { color: #f8fafc; }
        .tab-btn.active {
            color: #60a5fa;
            border-bottom-color: #60a5fa;
        }

        .tab-content { display: none; }
        .tab-content.active { display: block; }

        .panel {
            background: var(--exp-panel);
            border: 1px solid var(--exp-border);
            border-radius: 12px;
            padding: 22px;
            margin-bottom: 20px;
        }
        .panel-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 16px;
        }
        .panel-title {
            font-size: 1.05rem;
            font-weight: 600;
            margin: 0;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .overview-box {
            background: var(--exp-panel-2);
            border: 1px solid rgba(59,130,246,0.25);
            border-radius: 10px;
            padding: 18px 22px;
            font-size: 0.98rem;
            line-height: 1.6;
            color: #e2e8f0;
            margin-bottom: 22px;
        }

        .cards-list { display: flex; flex-direction: column; gap: 14px; }
        .finding-card {
            background: var(--exp-panel-2);
            border: 1px solid var(--exp-border);
            border-left: 4px solid var(--exp-accent);
            border-radius: 8px;
            padding: 16px 18px;
        }
        .finding-card.sev-high { border-left-color: var(--exp-danger); }
        .finding-card.sev-medium { border-left-color: var(--exp-warning); }
        .finding-card.sev-low { border-left-color: var(--exp-success); }

        .finding-top {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 8px;
        }
        .finding-title {
            font-weight: 600;
            font-size: 0.98rem;
            color: #f8fafc;
        }
        .badge {
            font-size: 0.72rem;
            padding: 3px 8px;
            border-radius: 4px;
            font-weight: 600;
            text-transform: uppercase;
        }
        .badge-verified { background: rgba(16,185,129,0.18); color: #34d399; border: 1px solid rgba(16,185,129,0.3); }
        .badge-partially { background: rgba(245,158,11,0.18); color: #fbbf24; border: 1px solid rgba(245,158,11,0.3); }
        .badge-contradicted { background: rgba(239,68,68,0.18); color: #f87171; border: 1px solid rgba(239,68,68,0.3); }
        .badge-unsupported { background: rgba(148,163,184,0.18); color: #cbd5e1; border: 1px solid rgba(148,163,184,0.3); }
        .badge-info { background: rgba(59,130,246,0.18); color: #60a5fa; border: 1px solid rgba(59,130,246,0.3); }

        .evidence-pill {
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.75rem;
            background: rgba(255,255,255,0.06);
            padding: 2px 7px;
            border-radius: 4px;
            color: #94a3b8;
            margin-right: 4px;
        }

        .data-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.88rem;
        }
        .data-table th, .data-table td {
            padding: 12px 14px;
            text-align: left;
            border-bottom: 1px solid var(--exp-border);
        }
        .data-table th {
            background: rgba(255,255,255,0.02);
            color: #64748b;
            font-weight: 600;
            text-transform: uppercase;
            font-size: 0.74rem;
            letter-spacing: 0.05em;
        }
        .data-table tbody tr:hover {
            background: rgba(255,255,255,0.025);
        }

        .chat-input-box {
            display: flex;
            gap: 12px;
            margin-bottom: 20px;
        }
        .chat-input {
            flex: 1;
            background: var(--exp-panel-2);
            border: 1px solid var(--exp-border);
            border-radius: 8px;
            padding: 12px 16px;
            color: #f8fafc;
            font-size: 0.95rem;
            outline: none;
        }
        .chat-input:focus { border-color: var(--exp-accent); }
        .chat-btn {
            background: var(--exp-accent);
            border: none;
            color: #fff;
            padding: 12px 24px;
            border-radius: 8px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
        }
        .chat-btn:hover { background: #2563eb; }

        .filter-group {
            display: flex;
            gap: 8px;
            align-items: center;
        }
        .filter-btn {
            background: var(--exp-panel-2);
            border: 1px solid var(--exp-border);
            color: #94a3b8;
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 0.78rem;
            cursor: pointer;
        }
        .filter-btn.active {
            background: rgba(59,130,246,0.15);
            border-color: #3b82f6;
            color: #60a5fa;
            font-weight: 600;
        }
    </style>
</head>
<body>

<header class="topbar">
    <div style="display:flex;align-items:center;">
        <a href="<%= ctx %>/" style="text-decoration:none;"><div class="brand">⚡ Java ArchAnalyzer</div></a>
    </div>
    <nav>
        <a href="<%= ctx %>/">Home</a>
        <a href="<%= ctx %>/projects/upload">Upload</a>
        <a href="<%= ctx %>/dashboard">Dashboard</a>
        <a href="<%= ctx %>/planner">🗺️ Planner</a>
        <a href="<%= ctx %>/risk">Risk Prediction</a>
        <a href="<%= ctx %>/explanation" class="active">AI Explanations</a>
        <a href="<%= ctx %>/simulator">What-If Simulator</a>
        <a href="<%= ctx %>/history">🕒 History</a>
    </nav>
</header>

    <div class="container">
        <div class="hero-banner">
            <div>
                <h1 class="hero-title">LLM Architecture Explanations with Automatic Claim Verification</h1>
                <p class="hero-sub">Deterministic Ground-Truth Auditing &amp; Automated Claim Verification for Project: <strong><%= projectName %></strong></p>
            </div>
            <div class="hero-meta">
                Model: <span><%= explanation != null ? explanation.modelUsed() : "N/A" %></span><br>
                Verification Duration: <span><%= explanation != null ? explanation.executionDurationMs() : 0 %> ms</span>
            </div>
        </div>

        <div class="scorecards-grid">
            <div class="scorecard">
                <span class="scorecard-label">Verification Rate</span>
                <span class="scorecard-value val-success"><%= explanation != null ? explanation.verificationRate() : 0.0 %>%</span>
                <span class="scorecard-desc"><%= explanation != null ? explanation.verifiedCount() : 0 %> verified, <%= explanation != null ? explanation.partiallyVerifiedCount() : 0 %> partial</span>
            </div>
            <div class="scorecard">
                <span class="scorecard-label">Contradiction Rate</span>
                <span class="scorecard-value <%= explanation != null && explanation.contradictionRate() > 0 ? "val-danger" : "val-success" %>"><%= explanation != null ? explanation.contradictionRate() : 0.0 %>%</span>
                <span class="scorecard-desc"><%= explanation != null ? explanation.contradictedCount() : 0 %> contradicted assertions</span>
            </div>
            <div class="scorecard">
                <span class="scorecard-label">Total Claims Evaluated</span>
                <span class="scorecard-value val-accent"><%= explanation != null ? explanation.totalClaims() : 0 %></span>
                <span class="scorecard-desc"><%= explanation != null ? explanation.unsupportedCount() : 0 %> unsupported, <%= explanation != null ? explanation.notVerifiableCount() : 0 %> opinions</span>
            </div>
            <div class="scorecard">
                <span class="scorecard-label">Ground-Truth Evidence</span>
                <span class="scorecard-value val-purple"><%= evidence != null ? evidence.size() : 0 %></span>
                <span class="scorecard-desc">Authoritative AST, graph & metric facts</span>
            </div>
        </div>

        <div class="tabs-header">
            <button class="tab-btn active" onclick="switchTab('tab-findings', this)">Verified Explanation & Findings</button>
            <button class="tab-btn" onclick="switchTab('tab-claims', this)">Claim Verification Audit Matrix</button>
            <button class="tab-btn" onclick="switchTab('tab-evidence', this)">Ground-Truth Register</button>
            <button class="tab-btn" onclick="switchTab('tab-qa', this)">Interactive Architect Q&A</button>
        </div>

        <!-- TAB 1: FINDINGS & RECOMMENDATIONS -->
        <div id="tab-findings" class="tab-content active">
            <div class="panel">
                <div class="panel-header">
                    <h2 class="panel-title">Architectural Overview</h2>
                    <span class="badge badge-verified">Ground-Truth Verified</span>
                </div>
                <div class="overview-box">
                    <%= explanation != null && explanation.overview() != null ? explanation.overview() : "No overview available." %>
                </div>

                <div class="panel-header">
                    <h2 class="panel-title">Key Architectural Findings (<%= explanation != null ? explanation.findings().size() : 0 %>)</h2>
                </div>
                <div class="cards-list">
                    <% if (explanation != null && !explanation.findings().isEmpty()) {
                        for (FindingDto f : explanation.findings()) {
                            String sevClass = "sev-low";
                            if ("HIGH".equalsIgnoreCase(f.severity())) sevClass = "sev-high";
                            else if ("MEDIUM".equalsIgnoreCase(f.severity())) sevClass = "sev-medium";
                    %>
                    <div class="finding-card <%= sevClass %>">
                        <div class="finding-top">
                            <span class="finding-title"><%= f.issue() %></span>
                            <span class="badge <%= sevClass.equals("sev-high") ? "badge-contradicted" : (sevClass.equals("sev-medium") ? "badge-partially" : "badge-verified") %>"><%= f.severity() %></span>
                        </div>
                        <p style="margin: 4px 0 10px; font-size: 0.9rem; color: #cbd5e1;"><%= f.explanation() %></p>
                        <div>
                            <span style="font-size: 0.78rem; color: #64748b; margin-right: 6px;">CITED EVIDENCE:</span>
                            <% for (String evId : f.evidenceIds()) { %>
                                <span class="evidence-pill"><%= evId %></span>
                            <% } %>
                        </div>
                    </div>
                    <%  }
                    } else { %>
                        <p style="color: #64748b;">No architectural findings generated.</p>
                    <% } %>
                </div>

                <div class="panel-header" style="margin-top: 26px;">
                    <h2 class="panel-title">Actionable Refactoring Recommendations (<%= explanation != null ? explanation.recommendations().size() : 0 %>)</h2>
                </div>
                <div class="cards-list">
                    <% if (explanation != null && !explanation.recommendations().isEmpty()) {
                        for (RecommendationDto r : explanation.recommendations()) {
                    %>
                    <div class="finding-card sev-low" style="border-left-color: #38bdf8;">
                        <div class="finding-top">
                            <span class="finding-title"><%= r.action() %> &mdash; Target: <code><%= r.targetComponent() %></code></span>
                            <span class="badge badge-info">RECOMMENDATION</span>
                        </div>
                        <p style="margin: 4px 0 6px; font-size: 0.9rem; color: #cbd5e1;"><%= r.rationale() %></p>
                        <p style="margin: 0 0 10px; font-size: 0.85rem; color: #34d399;"><strong>Projected Benefit:</strong> <%= r.projectedBenefit() %></p>
                        <div>
                            <span style="font-size: 0.78rem; color: #64748b; margin-right: 6px;">EVIDENCE:</span>
                            <% for (String evId : r.evidenceIds()) { %>
                                <span class="evidence-pill"><%= evId %></span>
                            <% } %>
                        </div>
                    </div>
                    <%  }
                    } else { %>
                        <p style="color: #64748b;">No recommendations available.</p>
                    <% } %>
                </div>
            </div>
        </div>

        <!-- TAB 2: CLAIM VERIFICATION MATRIX -->
        <div id="tab-claims" class="tab-content">
            <div class="panel">
                <div class="panel-header">
                    <h2 class="panel-title">Extracted Claims & Ground-Truth Verification Audit</h2>
                    <div class="filter-group">
                        <button class="filter-btn active" onclick="filterClaims('all', this)">All (<%= explanation != null ? explanation.totalClaims() : 0 %>)</button>
                        <button class="filter-btn" onclick="filterClaims('VERIFIED', this)">Verified (<%= explanation != null ? explanation.verifiedCount() : 0 %>)</button>
                        <button class="filter-btn" onclick="filterClaims('PARTIALLY_VERIFIED', this)">Partial (<%= explanation != null ? explanation.partiallyVerifiedCount() : 0 %>)</button>
                        <button class="filter-btn" onclick="filterClaims('CONTRADICTED', this)">Contradicted (<%= explanation != null ? explanation.contradictedCount() : 0 %>)</button>
                        <button class="filter-btn" onclick="filterClaims('UNSUPPORTED', this)">Unsupported (<%= explanation != null ? explanation.unsupportedCount() : 0 %>)</button>
                    </div>
                </div>

                <table class="data-table" id="claims-table">
                    <thead>
                        <tr>
                            <th>Claim ID</th>
                            <th>Type</th>
                            <th>Subject / Target</th>
                            <th>Claimed Assertion</th>
                            <th>Ground-Truth Verdict & Observed Value</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (explanation != null && !explanation.verifiedClaims().isEmpty()) {
                            for (ClaimVerificationResult res : explanation.verifiedClaims()) {
                                String statusBadge = "badge-unsupported";
                                if (res.status() == ClaimVerificationStatus.VERIFIED) statusBadge = "badge-verified";
                                else if (res.status() == ClaimVerificationStatus.PARTIALLY_VERIFIED) statusBadge = "badge-partially";
                                else if (res.status() == ClaimVerificationStatus.CONTRADICTED) statusBadge = "badge-contradicted";
                        %>
                        <tr class="claim-row" data-status="<%= res.status().name() %>">
                            <td><code><%= res.claim().claimId() %></code></td>
                            <td><span class="evidence-pill"><%= res.claim().type() %></span></td>
                            <td>
                                <strong><%= res.claim().subjectId() != null ? res.claim().subjectId() : "N/A" %></strong>
                                <%= res.claim().targetId() != null ? " &rarr; " + res.claim().targetId() : "" %>
                            </td>
                            <td><%= res.claim().text() %></td>
                            <td>
                                <div style="color: #cbd5e1;"><%= res.explanation() %></div>
                                <% if (res.observedTextValue() != null) { %>
                                    <div style="font-family: 'JetBrains Mono', monospace; font-size: 0.78rem; color: #38bdf8; margin-top: 2px;">
                                        Observed: <%= res.observedTextValue() %>
                                    </div>
                                <% } %>
                            </td>
                            <td><span class="badge <%= statusBadge %>"><%= res.status() %></span></td>
                        </tr>
                        <%  }
                        } else { %>
                        <tr><td colspan="6" style="text-align: center; color: #64748b;">No claims extracted or evaluated.</td></tr>
                        <% } %>
                    </tbody>
                </table>
            </div>
        </div>

        <!-- TAB 3: EVIDENCE REGISTER -->
        <div id="tab-evidence" class="tab-content">
            <div class="panel">
                <div class="panel-header">
                    <h2 class="panel-title">Authoritative Ground-Truth Architecture Evidence Register</h2>
                    <span style="font-size: 0.82rem; color: #94a3b8;"><%= evidence != null ? evidence.size() : 0 %> immutable facts registered</span>
                </div>
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>Evidence ID</th>
                            <th>Type</th>
                            <th>Subject</th>
                            <th>Target / Value</th>
                            <th>Source Engine</th>
                            <th>Description</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (evidence != null && !evidence.getAllItems().isEmpty()) {
                            for (EvidenceItem item : evidence.getAllItems()) {
                        %>
                        <tr>
                            <td><code><%= item.evidenceId() %></code></td>
                            <td><span class="evidence-pill"><%= item.type() %></span></td>
                            <td><strong><%= item.subjectId() %></strong></td>
                            <td>
                                <%= item.targetId() != null ? "&rarr; " + item.targetId() : (item.metricName() != null ? item.metricName() + " = " + item.textValue() : item.textValue()) %>
                            </td>
                            <td><span style="color: #a78bfa; font-size: 0.8rem;"><%= item.sourceEngine() %></span></td>
                            <td style="color: #cbd5e1;"><%= item.description() %></td>
                        </tr>
                        <%  }
                        } else { %>
                        <tr><td colspan="6" style="text-align: center; color: #64748b;">No evidence register available.</td></tr>
                        <% } %>
                    </tbody>
                </table>
            </div>
        </div>

        <!-- TAB 4: ARCHITECT Q&A -->
        <div id="tab-qa" class="tab-content">
            <div class="panel">
                <div class="panel-header">
                    <h2 class="panel-title">Interactive Architect Q&A with Automatic Claim Verification</h2>
                    <span style="font-size: 0.82rem; color: #94a3b8;">Ask questions about your architecture; answers are verified against Java ground truth in real time</span>
                </div>

                <div class="chat-input-box">
                    <input type="text" id="user-question-input" class="chat-input" placeholder="e.g. Which components participate in circular dependency cycles and how should we refactor them?">
                    <button class="chat-btn" onclick="submitArchitectQuestion()">Ask Architect</button>
                </div>

                <div id="qa-response-area" style="display: none;">
                    <div class="panel-header">
                        <h3 class="panel-title">Verified Explanation Response</h3>
                        <span id="qa-rate-badge" class="badge badge-verified">Verification Rate: 100%</span>
                    </div>
                    <div id="qa-overview-box" class="overview-box"></div>
                    <h4 style="margin: 16px 0 8px; font-size: 0.95rem;">Extracted & Verified Claims:</h4>
                    <div id="qa-claims-list" class="cards-list"></div>
                </div>
            </div>
        </div>
    </div>

    <script>
        function switchTab(tabId, btn) {
            document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
            document.querySelectorAll('.tab-btn').forEach(el => el.classList.remove('active'));
            document.getElementById(tabId).classList.add('active');
            btn.classList.add('active');
        }

        function filterClaims(status, btn) {
            document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            const rows = document.querySelectorAll('.claim-row');
            rows.forEach(r => {
                if (status === 'all' || r.getAttribute('data-status') === status) {
                    r.style.display = '';
                } else {
                    r.style.display = 'none';
                }
            });
        }

        async function submitArchitectQuestion() {
            const input = document.getElementById('user-question-input');
            const question = input.value.trim();
            if (!question) return;

            const area = document.getElementById('qa-response-area');
            const overviewBox = document.getElementById('qa-overview-box');
            const claimsList = document.getElementById('qa-claims-list');
            const rateBadge = document.getElementById('qa-rate-badge');

            overviewBox.textContent = "Analyzing architectural ground truth and generating verified response...";
            claimsList.innerHTML = "";
            area.style.display = 'block';

            try {
                const resp = await fetch('<%= ctx %>/api/explanation/ask', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams({ question })
                });
                const data = await resp.json();

                overviewBox.textContent = data.overview || "No response received.";
                rateBadge.textContent = 'Verification Rate: ' + (data.verificationRate || 100) + '%';

                if (data.verifiedClaims && data.verifiedClaims.length > 0) {
                    claimsList.innerHTML = data.verifiedClaims.map(c => `
                        <div class="finding-card sev-low" style="border-left-color: ${c.status === 'VERIFIED' ? '#10b981' : (c.status === 'CONTRADICTED' ? '#ef4444' : '#f59e0b')}">
                            <div class="finding-top">
                                <span class="finding-title">${c.claim ? c.claim.text : ''}</span>
                                <span class="badge ${c.status === 'VERIFIED' ? 'badge-verified' : (c.status === 'CONTRADICTED' ? 'badge-contradicted' : 'badge-partially')}">${c.status}</span>
                            </div>
                            <p style="margin: 4px 0; font-size: 0.88rem; color: #cbd5e1;">${c.explanation}</p>
                            ${c.observedTextValue ? `<div style="font-family: 'JetBrains Mono', monospace; font-size: 0.78rem; color: #38bdf8;">Observed: ${c.observedTextValue}</div>` : ''}
                        </div>
                    `).join('');
                } else {
                    claimsList.innerHTML = '<p style="color: #64748b;">No claims extracted.</p>';
                }
            } catch (err) {
                overviewBox.textContent = "Error generating verified answer: " + err.message;
            }
        }
    </script>
</body>
</html>
