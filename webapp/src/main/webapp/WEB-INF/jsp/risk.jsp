<%@ page contentType="text/html;charset=UTF-8" language="java" isELIgnored="true" %>
<%@ page import="com.archanalyzer.riskprediction.service.ArchitectureRiskService.ProjectRiskOverview" %>
<%@ page import="com.archanalyzer.riskprediction.service.ArchitectureRiskService.ComponentRiskResult" %>
<%@ page import="com.archanalyzer.riskprediction.service.ArchitectureRiskService.CoChangeNeighborDto" %>
<%@ page import="com.archanalyzer.riskprediction.evaluation.AblationStudy.AblationResult" %>
<%@ page import="java.util.*" %>
<%
    Boolean hasProject = (Boolean) request.getAttribute("hasProject");
    if (hasProject == null || !hasProject) {
        response.sendRedirect(request.getContextPath() + "/projects/upload?msg=noproject");
        return;
    }
    String projectName = (String) request.getAttribute("projectName");
    ProjectRiskOverview overview = (ProjectRiskOverview) request.getAttribute("overview");
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI-Based Architecture Risk Prediction — <%= projectName %></title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="<%= ctx %>/css/main.css">
    <style>
        :root {
            --risk-bg: #090d16;
            --risk-panel: #111726;
            --risk-panel-2: #172033;
            --risk-border: #1e293b;
            --risk-accent: #3b82f6;
            --risk-accent-glow: rgba(59,130,246,0.3);
            --risk-success: #10b981;
            --risk-warning: #f59e0b;
            --risk-danger: #ef4444;
            --risk-purple: #8b5cf6;
            --risk-cyan: #06b6d4;
        }

        body {
            background: var(--risk-bg);
            color: #e2e8f0;
            font-family: 'Inter', system-ui, -apple-system, sans-serif;
            margin: 0;
            padding: 0;
        }

        .container {
            max-width: 1320px;
            margin: 0 auto;
            padding: 28px 24px 60px 24px;
        }

        .hero-banner {
            background: linear-gradient(135deg, rgba(59,130,246,0.12), rgba(139,92,246,0.08));
            border: 1px solid var(--risk-border);
            border-radius: 14px;
            padding: 24px 28px;
            margin-bottom: 24px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 16px;
        }
        .hero-title {
            font-size: 1.35rem;
            font-weight: 700;
            color: #f8fafc;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .hero-badge {
            background: rgba(59,130,246,0.2);
            color: #60a5fa;
            border: 1px solid rgba(59,130,246,0.4);
            font-size: 0.72rem;
            font-weight: 600;
            padding: 3px 10px;
            border-radius: 12px;
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }
        .hero-sub {
            color: #94a3b8;
            font-size: 0.88rem;
            margin-top: 6px;
            max-width: 820px;
            line-height: 1.5;
        }

        .stat-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
            gap: 16px;
            margin-bottom: 24px;
        }
        .stat-card {
            background: var(--risk-panel);
            border: 1px solid var(--risk-border);
            border-radius: 12px;
            padding: 18px 20px;
            position: relative;
            overflow: hidden;
        }
        .stat-label {
            font-size: 0.78rem;
            color: #64748b;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            font-weight: 600;
            margin-bottom: 8px;
        }
        .stat-value {
            font-size: 1.8rem;
            font-weight: 700;
            color: #f8fafc;
            display: flex;
            align-items: baseline;
            gap: 6px;
        }
        .stat-sub {
            font-size: 0.75rem;
            color: #94a3b8;
            margin-top: 4px;
        }

        .card {
            background: var(--risk-panel);
            border: 1px solid var(--risk-border);
            border-radius: 14px;
            padding: 22px 26px;
            margin-bottom: 24px;
        }
        .card-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 18px;
            flex-wrap: wrap;
            gap: 12px;
        }
        .card-title {
            font-size: 1.1rem;
            font-weight: 600;
            color: #f1f5f9;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        /* Risk table */
        .risk-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.85rem;
        }
        .risk-table th {
            text-align: left;
            padding: 12px 14px;
            background: var(--risk-panel-2);
            color: #94a3b8;
            font-weight: 600;
            border-bottom: 1px solid var(--risk-border);
            font-size: 0.75rem;
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }
        .risk-table td {
            padding: 12px 14px;
            border-bottom: 1px solid rgba(30,41,59,0.7);
            color: #cbd5e1;
            vertical-align: middle;
        }
        .risk-table tr:hover td {
            background: rgba(30,41,59,0.4);
        }

        .badge {
            display: inline-block;
            padding: 3px 10px;
            border-radius: 12px;
            font-size: 0.72rem;
            font-weight: 600;
            text-transform: uppercase;
        }
        .badge-high {
            background: rgba(239,68,68,0.2);
            color: #f87171;
            border: 1px solid rgba(239,68,68,0.4);
        }
        .badge-med {
            background: rgba(245,158,11,0.2);
            color: #fbbf24;
            border: 1px solid rgba(245,158,11,0.4);
        }
        .badge-low {
            background: rgba(16,185,129,0.2);
            color: #34d399;
            border: 1px solid rgba(16,185,129,0.4);
        }
        .badge-cycle {
            background: rgba(139,92,246,0.2);
            color: #c084fc;
            border: 1px solid rgba(139,92,246,0.4);
        }

        .progress-bar-wrap {
            width: 100px;
            height: 7px;
            background: #1e293b;
            border-radius: 4px;
            overflow: hidden;
            display: inline-block;
            vertical-align: middle;
            margin-right: 8px;
        }
        .progress-bar-fill {
            height: 100%;
            border-radius: 4px;
        }
        .fill-high { background: linear-gradient(90deg, #f59e0b, #ef4444); }
        .fill-med { background: linear-gradient(90deg, #3b82f6, #f59e0b); }
        .fill-low { background: linear-gradient(90deg, #10b981, #06b6d4); }

        .factor-tag {
            display: inline-block;
            background: rgba(30,41,59,0.7);
            border: 1px solid var(--risk-border);
            color: #94a3b8;
            font-size: 0.72rem;
            padding: 2px 7px;
            border-radius: 4px;
            margin: 2px 4px 2px 0;
        }
        .factor-tag.severe {
            background: rgba(239,68,68,0.15);
            border-color: rgba(239,68,68,0.3);
            color: #fca5a5;
        }

        .code-cell {
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.8rem;
            color: #e2e8f0;
        }

        /* Ablation card */
        .ablation-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
            gap: 16px;
            margin-top: 16px;
        }
        .model-card {
            background: var(--risk-panel-2);
            border: 1px solid var(--risk-border);
            border-radius: 10px;
            padding: 16px;
        }
        .model-card.featured {
            border-color: rgba(59,130,246,0.6);
            background: linear-gradient(135deg, rgba(59,130,246,0.1), rgba(139,92,246,0.05));
        }
        .model-name {
            font-size: 0.95rem;
            font-weight: 600;
            color: #f8fafc;
            margin-bottom: 8px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .metric-row {
            display: flex;
            justify-content: space-between;
            font-size: 0.8rem;
            padding: 4px 0;
            border-bottom: 1px dashed rgba(255,255,255,0.06);
        }
        .metric-name { color: #94a3b8; }
        .metric-val { font-family: 'JetBrains Mono', monospace; font-weight: 500; color: #f1f5f9; }

        .btn {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 8px 16px;
            border-radius: 8px;
            font-size: 0.82rem;
            font-weight: 600;
            text-decoration: none;
            cursor: pointer;
            border: none;
            transition: all 0.15s;
        }
        .btn-primary {
            background: linear-gradient(135deg, #3b82f6, #2563eb);
            color: white;
            box-shadow: 0 4px 14px rgba(59,130,246,0.3);
        }
        .btn-primary:hover { background: linear-gradient(135deg, #2563eb, #1d4ed8); }
        .btn-secondary {
            background: var(--risk-panel-2);
            color: #cbd5e1;
            border: 1px solid var(--risk-border);
        }
        .btn-secondary:hover { background: #1e293b; color: white; }
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
        <a href="<%= ctx %>/risk" class="active">Risk Prediction</a>
        <a href="<%= ctx %>/explanation">AI Explanations</a>
        <a href="<%= ctx %>/simulator">What-If Simulator</a>
        <a href="<%= ctx %>/history">🕒 History</a>
    </nav>
</header>

<div class="container">

    <!-- Hero Banner -->
    <div class="hero-banner">
        <div>
            <div class="hero-title">
                AI-Based Architecture Risk Prediction
                <span class="hero-badge">Risk Prediction</span>
            </div>
            <div class="hero-sub">
                Predicts component-level architectural defect and change risk by combining <strong>static dependency graph topology</strong> (fan-in/out, cyclomatic complexity, dependency depth, cyclic SCCs) with <strong>evolutionary Git history</strong> (commit frequency, code churn, burstiness, co-change graphs).
            </div>
        </div>
        <div style="display:flex; gap:10px;">
            <a href="<%= ctx %>/planner" class="btn btn-secondary">Open Planner</a>
            <button onclick="reassessRisk()" class="btn btn-primary" id="btnReassess">Re-Assess Risk</button>
        </div>
    </div>

    <!-- Stat Grid -->
    <div class="stat-grid">
        <div class="stat-card">
            <div class="stat-label">Total Components</div>
            <div class="stat-value"><%= overview.totalComponents() %></div>
            <div class="stat-sub">Analyzed in <%= projectName %></div>
        </div>

        <div class="stat-card" style="border-left: 3px solid var(--risk-danger);">
            <div class="stat-label">High Risk Components</div>
            <div class="stat-value" style="color: #f87171;">
                <%= overview.highRiskCount() %>
                <span style="font-size: 0.9rem; font-weight: 500; color: #94a3b8;">
                    (<%= overview.totalComponents() > 0 ? Math.round((double) overview.highRiskCount() / overview.totalComponents() * 100) : 0 %>%)
                </span>
            </div>
            <div class="stat-sub">Risk probability &ge; 60%</div>
        </div>

        <div class="stat-card">
            <div class="stat-label">Medium / Low Risk</div>
            <div class="stat-value" style="color: #fbbf24;">
                <%= overview.mediumRiskCount() %>
                <span style="font-size: 1rem; color: #34d399; font-weight: normal;"> / <%= overview.lowRiskCount() %></span>
            </div>
            <div class="stat-sub">Med: 35-59% | Low: &lt;35%</div>
        </div>

        <div class="stat-card">
            <div class="stat-label">Git History Status</div>
            <div class="stat-value" style="font-size: 1.4rem;">
                <% if (overview.gitHistoryAvailable()) { %>
                    <span style="color: #34d399;">Connected</span>
                <% } else { %>
                    <span style="color: #94a3b8; font-size: 1.1rem;">Static Fallback</span>
                <% } %>
            </div>
            <div class="stat-sub">
                <% if (overview.gitHistoryAvailable()) { %>
                    <%= overview.totalCommits() %> commits mined
                <% } else { %>
                    No Git repository metadata
                <% } %>
            </div>
        </div>

        <div class="stat-card" style="border-left: 3px solid var(--risk-purple);">
            <div class="stat-label">Cyclic &amp; High Risk</div>
            <div class="stat-value" style="color: #c084fc;">
                <%= overview.cycleComponentsWithHighRisk().size() %>
            </div>
            <div class="stat-sub">In cycle + high churn/risk</div>
        </div>
    </div>

    <!-- Architectural Integration Alert if cyclic high-risk components exist -->
    <% if (!overview.cycleComponentsWithHighRisk().isEmpty()) { %>
    <div style="background: rgba(139,92,246,0.12); border: 1px solid rgba(139,92,246,0.3); border-radius: 12px; padding: 16px 20px; margin-bottom: 24px; display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap;">
        <div>
            <div style="font-weight: 600; color: #e9d5ff; font-size: 0.95rem; margin-bottom: 4px;">
                Direct Action Required: <%= overview.cycleComponentsWithHighRisk().size() %> Components are both Trapped in Dependency Cycles and High Risk
            </div>
            <div style="font-size: 0.82rem; color: #c4b5fd;">
                These components represent the primary architectural defect vectors:
                <strong><%= String.join(", ", overview.cycleComponentsWithHighRisk()) %></strong>.
                Use Automated Cycle Breaking or the Simulation Planner to decouple them.
            </div>
        </div>
        <a href="<%= ctx %>/planner" class="btn btn-primary" style="background: linear-gradient(135deg, #8b5cf6, #7c3aed); box-shadow: 0 4px 14px rgba(139,92,246,0.35);">
            Plan Cycle-Break Refactoring
        </a>
    </div>
    <% } %>

    <!-- Component Risk Ranking Card -->
    <div class="card">
        <div class="card-header">
            <div>
                <div class="card-title">Component Risk Rankings</div>
                <div style="font-size: 0.8rem; color: #94a3b8; margin-top: 2px;">
                    Ranked by predicted defect and change probability using <%= overview.modelDescription() %>
                </div>
            </div>
            <div style="display: flex; gap: 8px;">
                <input type="text" id="filterInput" placeholder="Filter component..." onkeyup="filterTable()"
                       style="background: var(--risk-panel-2); border: 1px solid var(--risk-border); color: #f1f5f9; padding: 6px 12px; border-radius: 6px; font-size: 0.82rem; width: 220px;">
            </div>
        </div>

        <div style="overflow-x: auto;">
            <table class="risk-table" id="componentTable">
                <thead>
                    <tr>
                        <th style="width: 50px;">Rank</th>
                        <th>Component</th>
                        <th style="width: 170px;">Risk Probability</th>
                        <th style="width: 100px;">Category</th>
                        <th style="width: 90px;">Cycle?</th>
                        <th>Key Risk Factors</th>
                        <th>Co-Change Partners</th>
                    </tr>
                </thead>
                <tbody>
                <%
                    int rank = 1;
                    for (ComponentRiskResult comp : overview.components()) {
                        String fillClass = comp.riskProbability() >= 0.60 ? "fill-high" : (comp.riskProbability() >= 0.35 ? "fill-med" : "fill-low");
                        String badgeClass = comp.riskProbability() >= 0.60 ? "badge-high" : (comp.riskProbability() >= 0.35 ? "badge-med" : "badge-low");
                %>
                    <tr>
                        <td style="color: #64748b; font-weight: 600;"><%= rank++ %></td>
                        <td class="code-cell">
                            <strong><%= comp.componentName() %></strong>
                        </td>
                        <td>
                            <div class="progress-bar-wrap">
                                <div class="progress-bar-fill <%= fillClass %>" style="width: <%= comp.riskPercentage() %>%;"></div>
                            </div>
                            <span style="font-family: 'JetBrains Mono', monospace; font-weight: 600; font-size: 0.85rem;">
                                <%= comp.riskPercentage() %>%
                            </span>
                        </td>
                        <td>
                            <span class="badge <%= badgeClass %>"><%= comp.riskCategory().name() %></span>
                        </td>
                        <td>
                            <% if (comp.inCycle()) { %>
                                <span class="badge badge-cycle">CYCLE</span>
                            <% } else { %>
                                <span style="color: #64748b; font-size: 0.78rem;">No</span>
                            <% } %>
                        </td>
                        <td>
                            <% for (String f : comp.topStaticFactors()) { %>
                                <span class="factor-tag <%= f.contains("Cycle") || f.contains("High") ? "severe" : "" %>"><%= f %></span>
                            <% } %>
                            <% for (String f : comp.topEvolutionaryFactors()) { %>
                                <% if (!f.contains("zero historical") && !f.contains("No evolutionary")) { %>
                                    <span class="factor-tag <%= f.contains("Frequent") || f.contains("High") ? "severe" : "" %>"><%= f %></span>
                                <% } %>
                            <% } %>
                        </td>
                        <td>
                            <% if (comp.coChangeNeighbors().isEmpty()) { %>
                                <span style="color: #64748b; font-size: 0.75rem;">None</span>
                            <% } else { %>
                                <% for (CoChangeNeighborDto n : comp.coChangeNeighbors()) { %>
                                    <span class="factor-tag" title="Co-changed <%= n.coChangeCount() %> times, Jaccard: <%= n.jaccardStrength() %>">
                                        <%= n.neighborComponent() %> (<%= n.coChangeCount() %>)
                                    </span>
                                <% } %>
                            <% } %>
                        </td>
                    </tr>
                <% } %>
                </tbody>
            </table>
        </div>
    </div>

    <!-- Empirical Ablation Study Card -->
    <div class="card">
        <div class="card-header">
            <div>
                <div class="card-title">
                    Empirical Ablation Study &amp; Hypothesis Verification
                    <% if (overview.ablationResult() != null && overview.ablationResult().hypothesisSupported()) { %>
                        <span class="badge badge-low" style="background: rgba(16,185,129,0.25); color: #34d399; font-size: 0.75rem;">
                            Hypothesis Supported
                        </span>
                    <% } %>
                </div>
                <div style="font-size: 0.8rem; color: #94a3b8; margin-top: 2px;">
                    Validating: <em>"Does combining static architecture metrics with historical Git churn and co-change mining improve defect and change risk prediction over static structural metrics alone?"</em>
                </div>
            </div>
        </div>

        <% if (overview.ablationResult() != null) {
            AblationResult abl = overview.ablationResult();
        %>
            <div style="background: rgba(30,41,59,0.5); border: 1px solid var(--risk-border); border-radius: 10px; padding: 14px 18px; margin-bottom: 18px; font-size: 0.86rem; color: #cbd5e1; line-height: 1.5;">
                <%= abl.summary() %>
                <div style="margin-top: 8px; display: flex; gap: 20px; font-size: 0.8rem; color: #94a3b8;">
                    <span>&Delta; ROC-AUC: <strong style="color: <%= abl.deltaRocAucOverStatic() >= 0 ? "#34d399" : "#f87171" %>;"><%= String.format(Locale.US, "%+.4f", abl.deltaRocAucOverStatic()) %></strong></span>
                    <span>&Delta; PR-AUC: <strong style="color: <%= abl.deltaPrAucOverStatic() >= 0 ? "#34d399" : "#f87171" %>;"><%= String.format(Locale.US, "%+.4f", abl.deltaPrAucOverStatic()) %></strong></span>
                    <span>&Delta; F1: <strong style="color: <%= abl.deltaF1OverStatic() >= 0 ? "#34d399" : "#f87171" %>;"><%= String.format(Locale.US, "%+.4f", abl.deltaF1OverStatic()) %></strong></span>
                    <span>&Delta; Brier Error: <strong style="color: <%= abl.deltaBrierOverStatic() <= 0 ? "#34d399" : "#f87171" %>;"><%= String.format(Locale.US, "%+.4f", abl.deltaBrierOverStatic()) %></strong></span>
                </div>
            </div>

            <div class="ablation-grid">
                <!-- 1. Baseline -->
                <div class="model-card">
                    <div class="model-name">
                        1. Marginal Prior
                        <span style="font-size: 0.7rem; color: #64748b;">Baseline</span>
                    </div>
                    <div class="metric-row"><span class="metric-name">Precision</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.baselineMetrics().precision()) %></span></div>
                    <div class="metric-row"><span class="metric-name">Recall</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.baselineMetrics().recall()) %></span></div>
                    <div class="metric-row"><span class="metric-name">F1-Score</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.baselineMetrics().f1()) %></span></div>
                    <div class="metric-row"><span class="metric-name">ROC-AUC</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.baselineMetrics().rocAuc()) %></span></div>
                    <div class="metric-row"><span class="metric-name">PR-AUC</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.baselineMetrics().prAuc()) %></span></div>
                    <div class="metric-row"><span class="metric-name">Brier Score</span><span class="metric-val"><%= String.format(Locale.US, "%.4f", abl.baselineMetrics().brierScore()) %></span></div>
                </div>

                <!-- 2. Static Only -->
                <div class="model-card">
                    <div class="model-name">
                        2. Static Architecture
                        <span style="font-size: 0.7rem; color: #94a3b8;">11 Features</span>
                    </div>
                    <div class="metric-row"><span class="metric-name">Precision</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.staticMetrics().precision()) %></span></div>
                    <div class="metric-row"><span class="metric-name">Recall</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.staticMetrics().recall()) %></span></div>
                    <div class="metric-row"><span class="metric-name">F1-Score</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.staticMetrics().f1()) %></span></div>
                    <div class="metric-row"><span class="metric-name">ROC-AUC</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.staticMetrics().rocAuc()) %></span></div>
                    <div class="metric-row"><span class="metric-name">PR-AUC</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.staticMetrics().prAuc()) %></span></div>
                    <div class="metric-row"><span class="metric-name">Brier Score</span><span class="metric-val"><%= String.format(Locale.US, "%.4f", abl.staticMetrics().brierScore()) %></span></div>
                </div>

                <!-- 3. Evolutionary Only -->
                <div class="model-card">
                    <div class="model-name">
                        3. Evolutionary Git Churn
                        <span style="font-size: 0.7rem; color: #94a3b8;">13 Features</span>
                    </div>
                    <div class="metric-row"><span class="metric-name">Precision</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.evolutionaryMetrics().precision()) %></span></div>
                    <div class="metric-row"><span class="metric-name">Recall</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.evolutionaryMetrics().recall()) %></span></div>
                    <div class="metric-row"><span class="metric-name">F1-Score</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.evolutionaryMetrics().f1()) %></span></div>
                    <div class="metric-row"><span class="metric-name">ROC-AUC</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.evolutionaryMetrics().rocAuc()) %></span></div>
                    <div class="metric-row"><span class="metric-name">PR-AUC</span><span class="metric-val"><%= String.format(Locale.US, "%.3f", abl.evolutionaryMetrics().prAuc()) %></span></div>
                    <div class="metric-row"><span class="metric-name">Brier Score</span><span class="metric-val"><%= String.format(Locale.US, "%.4f", abl.evolutionaryMetrics().brierScore()) %></span></div>
                </div>

                <!-- 4. Combined Multi-Modal -->
                <div class="model-card featured">
                    <div class="model-name" style="color: #60a5fa;">
                        4. Multi-Modal Ensemble
                        <span class="badge badge-low" style="font-size: 0.68rem;">ML Scoring</span>
                    </div>
                    <div class="metric-row"><span class="metric-name">Precision</span><span class="metric-val" style="color: #60a5fa;"><%= String.format(Locale.US, "%.3f", abl.combinedMetrics().precision()) %></span></div>
                    <div class="metric-row"><span class="metric-name">Recall</span><span class="metric-val" style="color: #60a5fa;"><%= String.format(Locale.US, "%.3f", abl.combinedMetrics().recall()) %></span></div>
                    <div class="metric-row"><span class="metric-name">F1-Score</span><span class="metric-val" style="color: #60a5fa;"><%= String.format(Locale.US, "%.3f", abl.combinedMetrics().f1()) %></span></div>
                    <div class="metric-row"><span class="metric-name">ROC-AUC</span><span class="metric-val" style="color: #60a5fa;"><%= String.format(Locale.US, "%.3f", abl.combinedMetrics().rocAuc()) %></span></div>
                    <div class="metric-row"><span class="metric-name">PR-AUC</span><span class="metric-val" style="color: #60a5fa;"><%= String.format(Locale.US, "%.3f", abl.combinedMetrics().prAuc()) %></span></div>
                    <div class="metric-row"><span class="metric-name">Brier Score</span><span class="metric-val" style="color: #60a5fa;"><%= String.format(Locale.US, "%.4f", abl.combinedMetrics().brierScore()) %></span></div>
                </div>
            </div>
        <% } else { %>
            <div style="background: rgba(30,41,59,0.4); border: 1px dashed var(--risk-border); border-radius: 10px; padding: 24px; text-align: center; color: #94a3b8; font-size: 0.88rem;">
                <% if (!overview.gitHistoryAvailable()) { %>
                    Ablation evaluation requires an active Git commit history to evaluate temporal splits. This project is currently running in <strong>Static Structural Mode</strong>.
                <% } else { %>
                    Commit history in this project is currently insufficient to form isolated chronological train/test temporal partitions. Predictions are generated via calibrated multi-modal scoring.
                <% } %>
            </div>
        <% } %>
    </div>

</div>

<script>
    function filterTable() {
        const input = document.getElementById("filterInput");
        const filter = input.value.toLowerCase();
        const table = document.getElementById("componentTable");
        const trs = table.getElementsByTagName("tr");

        for (let i = 1; i < trs.length; i++) {
            const tdComp = trs[i].getElementsByTagName("td")[1];
            if (tdComp) {
                const text = tdComp.textContent || tdComp.innerText;
                trs[i].style.display = text.toLowerCase().indexOf(filter) > -1 ? "" : "none";
            }
        }
    }

    function reassessRisk() {
        const btn = document.getElementById("btnReassess");
        btn.innerText = "Re-Assessing...";
        btn.disabled = true;

        fetch("<%= ctx %>/api/risk/assess", { method: "POST" })
            .then(res => res.json())
            .then(data => {
                location.reload();
            })
            .catch(err => {
                alert("Error re-assessing risk: " + err);
                btn.innerText = "Re-Assess Risk";
                btn.disabled = false;
            });
    }
</script>

</body>
</html>
