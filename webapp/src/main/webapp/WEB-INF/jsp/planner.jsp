<%@ page contentType="text/html;charset=UTF-8" language="java" isELIgnored="true" %>
<%@ page import="com.archanalyzer.pipeline.AnalyzerPipeline" %>
<%@ page import="com.archanalyzer.model.ProjectModel" %>
<%@ page import="java.util.*" %>
<%
    AnalyzerPipeline.PipelineResult result = (AnalyzerPipeline.PipelineResult) request.getAttribute("result");
    if (result == null) {
        response.sendRedirect(request.getContextPath() + "/projects/upload?msg=noproject");
        return;
    }
    ProjectModel model = result.model;
    String planId = (String) request.getAttribute("planId");
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Simulation-Guided Refactoring Planner — <%= model.getProjectName() %></title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="<%= ctx %>/css/main.css">
    <style>
        :root {
            --sim-bg: #0a0d14;
            --sim-panel: #11151f;
            --sim-panel-2: #161c2a;
            --sim-border: #1e2840;
            --sim-accent: #6366f1;
            --sim-accent-glow: rgba(99,102,241,0.35);
            --sim-success: #10b981;
            --sim-warning: #f59e0b;
            --sim-danger: #ef4444;
            --sim-teal: #0ea5e9;
            --sim-purple: #a855f7;
        }

        body {
            background: var(--sim-bg);
            color: #e2e8f0;
            font-family: 'Inter', system-ui, sans-serif;
            margin: 0;
            padding: 0;
        }

        .container {
            max-width: 1280px;
            margin: 0 auto;
            padding: 28px 24px 60px 24px;
        }

        .hero-banner {
            background: linear-gradient(135deg, rgba(99,102,241,0.12), rgba(168,85,247,0.08));
            border: 1px solid var(--sim-border);
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
            margin: 0 0 6px 0;
            background: linear-gradient(135deg, #ffffff, #c7d2fe);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .hero-subtitle {
            font-size: 0.85rem;
            color: #94a3b8;
            margin: 0;
            line-height: 1.5;
        }

        .baseline-strip {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 14px;
            margin-bottom: 28px;
        }
        .baseline-card {
            background: var(--sim-panel);
            border: 1px solid var(--sim-border);
            border-radius: 10px;
            padding: 14px 18px;
        }
        .baseline-card .val {
            font-size: 1.4rem;
            font-weight: 700;
            color: #f8fafc;
            font-family: 'JetBrains Mono', monospace;
        }
        .baseline-card .lbl {
            font-size: 0.76rem;
            color: #94a3b8;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            margin-top: 4px;
        }

        /* 2-column layout */
        .planner-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 24px;
            margin-bottom: 30px;
        }
        @media (max-width: 960px) {
            .planner-grid { grid-template-columns: 1fr; }
        }

        .panel-box {
            background: var(--sim-panel);
            border: 1px solid var(--sim-border);
            border-radius: 12px;
            padding: 20px;
            display: flex;
            flex-direction: column;
            gap: 16px;
        }
        .panel-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid var(--sim-border);
            padding-bottom: 12px;
        }
        .panel-header h2 {
            font-size: 1.05rem;
            font-weight: 600;
            margin: 0;
            color: #f1f5f9;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .candidate-list, .plan-list {
            display: flex;
            flex-direction: column;
            gap: 10px;
            max-height: 440px;
            overflow-y: auto;
            padding-right: 4px;
        }
        .candidate-item, .plan-item {
            background: var(--sim-panel-2);
            border: 1px solid var(--sim-border);
            border-radius: 8px;
            padding: 12px 14px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            transition: border-color 0.15s;
        }
        .candidate-item:hover, .plan-item:hover {
            border-color: #3b82f6;
        }

        .op-badge {
            font-size: 0.7rem;
            font-weight: 600;
            text-transform: uppercase;
            padding: 3px 8px;
            border-radius: 4px;
            display: inline-block;
            margin-bottom: 4px;
        }
        .badge-cycle { background: rgba(239,68,68,0.2); color: #fca5a5; border: 1px solid rgba(239,68,68,0.3); }
        .badge-extract { background: rgba(59,130,246,0.2); color: #93c5fd; border: 1px solid rgba(59,130,246,0.3); }
        .badge-remove { background: rgba(245,158,11,0.2); color: #fde68a; border: 1px solid rgba(245,158,11,0.3); }
        .badge-redirect { background: rgba(16,185,129,0.2); color: #6ee7b7; border: 1px solid rgba(16,185,129,0.3); }

        .op-info { flex: 1; min-width: 0; }
        .op-title { font-size: 0.85rem; font-weight: 600; color: #f1f5f9; }
        .op-desc { font-size: 0.76rem; color: #94a3b8; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

        .btn-add {
            background: linear-gradient(135deg, #3b82f6, #2563eb);
            color: #fff;
            border: none;
            padding: 6px 12px;
            border-radius: 6px;
            font-size: 0.78rem;
            font-weight: 600;
            cursor: pointer;
            white-space: nowrap;
        }
        .btn-remove {
            background: transparent;
            color: #ef4444;
            border: 1px solid rgba(239,68,68,0.3);
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 0.75rem;
            cursor: pointer;
        }
        .btn-remove:hover { background: rgba(239,68,68,0.15); }

        .btn-simulate-action {
            background: linear-gradient(135deg, #6366f1, #8b5cf6);
            color: #fff;
            border: none;
            padding: 13px 26px;
            border-radius: 10px;
            font-size: 0.95rem;
            font-weight: 700;
            cursor: pointer;
            box-shadow: 0 4px 18px rgba(99,102,241,0.35);
            display: inline-flex;
            align-items: center;
            gap: 8px;
            transition: all 0.2s;
        }
        .btn-simulate-action:hover:not(:disabled) {
            transform: translateY(-1px);
            box-shadow: 0 6px 26px rgba(99,102,241,0.5);
        }
        .btn-simulate-action:disabled { opacity: 0.5; cursor: not-allowed; }

        .simulation-section {
            background: var(--sim-panel);
            border: 1px solid var(--sim-border);
            border-radius: 14px;
            padding: 26px;
            margin-bottom: 30px;
            display: none;
        }
        .simulation-section.visible { display: block; }

        .delta-table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 16px;
            font-size: 0.85rem;
        }
        .delta-table th {
            text-align: left;
            padding: 10px 14px;
            background: var(--sim-panel-2);
            color: #94a3b8;
            font-weight: 600;
            text-transform: uppercase;
            font-size: 0.72rem;
            letter-spacing: 0.5px;
            border-bottom: 1px solid var(--sim-border);
        }
        .delta-table td {
            padding: 12px 14px;
            border-bottom: 1px solid var(--sim-border);
            color: #cbd5e1;
        }
        .delta-improved { color: #34d399; font-weight: 600; }
        .delta-degraded { color: #f87171; font-weight: 600; }
        .delta-neutral { color: #94a3b8; }

        .stale-banner {
            background: rgba(245,158,11,0.12);
            border: 1px solid rgba(245,158,11,0.35);
            border-radius: 8px;
            padding: 12px 18px;
            color: #fbbf24;
            font-size: 0.84rem;
            margin-bottom: 16px;
            display: none;
            align-items: center;
            gap: 10px;
        }

        .tradeoffs-box {
            background: var(--sim-panel-2);
            border: 1px solid var(--sim-border);
            border-radius: 8px;
            padding: 16px;
            margin-top: 18px;
        }
        .tradeoffs-box h4 {
            margin: 0 0 10px 0;
            font-size: 0.88rem;
            color: #c7d2fe;
        }
        .tradeoffs-list {
            margin: 0 0 0 18px;
            padding: 0;
            font-size: 0.82rem;
            color: #94a3b8;
            line-height: 1.6;
        }

        .approve-bar {
            margin-top: 24px;
            padding-top: 20px;
            border-top: 1px solid var(--sim-border);
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 16px;
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
        <a href="<%= ctx %>/planner" class="active">🗺️ Planner</a>
        <a href="<%= ctx %>/risk">Risk Prediction</a>
        <a href="<%= ctx %>/explanation">AI Explanations</a>
        <a href="<%= ctx %>/simulator">What-If Simulator</a>
        <a href="<%= ctx %>/history">🕒 History</a>
    </nav>
</header>

<div class="container">

    <!-- Hero / Context Banner -->
    <div class="hero-banner">
        <div>
            <h1 class="hero-title">Simulation-Guided Refactoring Planner</h1>
            <p class="hero-subtitle">
                Assemble multi-operation refactoring plans, simulate their combined architectural effect on an isolated graph snapshot,
                detect structural conflicts, and verify actual post-application improvements.
            </p>
        </div>
        <div>
            <button type="button" id="simulatePlanBtn" onclick="runCombinedSimulation()" class="btn-simulate-action" disabled>
                ⚡ Simulate Combined Plan
            </button>
        </div>
    </div>

    <!-- Baseline Metrics Strip -->
    <div class="baseline-strip">
        <div class="baseline-card">
            <div class="val"><%= result.metricsBefore.architectureHealthScore %></div>
            <div class="lbl">Architecture Health</div>
        </div>
        <div class="baseline-card">
            <div class="val"><%= result.metricsBefore.dependencyScore %></div>
            <div class="lbl">Dependency Pressure</div>
        </div>
        <div class="baseline-card">
            <div class="val"><%= result.metricsBefore.cycleCount %></div>
            <div class="lbl">Circular Dependencies</div>
        </div>
        <div class="baseline-card">
            <div class="val"><%= result.metricsBefore.totalDependencies %></div>
            <div class="lbl">Total Dependencies</div>
        </div>
        <div class="baseline-card">
            <div class="val"><%= result.metricsBefore.totalComponents %></div>
            <div class="lbl">Total Components</div>
        </div>
    </div>

    <!-- Stale Plan Alert -->
    <div id="staleAlert" class="stale-banner">
        <span>⚠️</span>
        <div><strong>Plan Modified:</strong> Selected operations have changed since the last simulation. Re-simulation is required before approving or applying.</div>
    </div>

    <!-- Planner Grid: Candidate Discovery & Plan Builder -->
    <div class="planner-grid">

        <!-- Left: Candidate Operations -->
        <div class="panel-box">
            <div class="panel-header">
                <h2><span>🔍</span> Candidate Operations Browser</h2>
                <button type="button" onclick="loadCandidates()" style="background:var(--sim-panel-2);border:1px solid var(--sim-border);color:#94a3b8;padding:4px 10px;border-radius:6px;font-size:0.75rem;cursor:pointer;">
                    🔄 Refresh
                </button>
            </div>
            <div style="font-size:0.78rem;color:#94a3b8;">
                High-value refactoring opportunities discovered automatically from cycles and heavy coupling:
            </div>
            <div id="candidateList" class="candidate-list">
                <div style="text-align:center;padding:30px;color:#64748b;">Loading candidate operations...</div>
            </div>
        </div>

        <!-- Right: Active Refactoring Plan -->
        <div class="panel-box">
            <div class="panel-header">
                <h2><span>📋</span> Active Refactoring Plan (<span id="planOpCount">0</span>)</h2>
                <div style="display:flex;gap:8px;">
                    <button type="button" onclick="clearPlan()" style="background:transparent;border:1px solid rgba(239,68,68,0.3);color:#fca5a5;padding:4px 10px;border-radius:6px;font-size:0.75rem;cursor:pointer;">
                        Clear All
                    </button>
                </div>
            </div>
            <div style="font-size:0.78rem;color:#94a3b8;">
                Operations in this plan will be applied together in the specified sequence during simulation:
            </div>
            <div id="planList" class="plan-list">
                <div style="text-align:center;padding:40px;color:#64748b;">
                    No operations in plan.<br>Add operations from the candidates browser on the left.
                </div>
            </div>
        </div>
    </div>

    <!-- Simulation Results Dashboard -->
    <div id="simulationSection" class="simulation-section">
        <div style="display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid var(--sim-border);padding-bottom:14px;margin-bottom:18px;">
            <div>
                <h3 style="margin:0 0 4px 0;font-size:1.15rem;color:#f8fafc;">Combined Architecture Simulation Results</h3>
                <div style="font-size:0.78rem;color:#94a3b8;">
                    Calculated using complete dependency graph topology recalculation. <strong style="color:#a5b4fc;">(Non-additive)</strong>
                </div>
            </div>
            <div id="simStatusBadge" style="padding:4px 12px;border-radius:6px;font-size:0.75rem;font-weight:600;">
                SIMULATED
            </div>
        </div>

        <!-- Delta Comparison Table -->
        <table class="delta-table">
            <thead>
                <tr>
                    <th>Architecture Metric</th>
                    <th>Baseline</th>
                    <th>Simulated</th>
                    <th>Delta (Δ)</th>
                    <th>% Change</th>
                    <th>Evaluation</th>
                </tr>
            </thead>
            <tbody id="deltaTableBody">
            </tbody>
        </table>

        <!-- Trade-offs Panel -->
        <div class="tradeoffs-box">
            <h4>Architectural Trade-offs & Findings:</h4>
            <ul id="tradeoffsList" class="tradeoffs-list"></ul>
        </div>

        <!-- Graph Transformations Summary -->
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:16px;">
            <div style="background:var(--sim-panel-2);border:1px solid var(--sim-border);border-radius:8px;padding:12px;">
                <div style="font-size:0.76rem;font-weight:600;color:#93c5fd;margin-bottom:6px;">Added Nodes & Abstractions:</div>
                <div id="addedNodesView" style="font-size:0.78rem;color:#cbd5e1;font-family:'JetBrains Mono',monospace;">None</div>
            </div>
            <div style="background:var(--sim-panel-2);border:1px solid var(--sim-border);border-radius:8px;padding:12px;">
                <div style="font-size:0.76rem;font-weight:600;color:#fca5a5;margin-bottom:6px;">Decoupled / Removed Edges:</div>
                <div id="removedEdgesView" style="font-size:0.78rem;color:#cbd5e1;font-family:'JetBrains Mono',monospace;">None</div>
            </div>
        </div>

        <!-- Approval & Apply Controls -->
        <div class="approve-bar">
            <div>
                <label style="display:inline-flex;align-items:center;gap:8px;font-size:0.82rem;color:#cbd5e1;cursor:pointer;">
                    <input type="checkbox" id="runMavenTestsPlanner" checked>
                    Run Maven test suite during verification
                </label>
            </div>
            <div style="display:flex;gap:12px;">
                <button type="button" id="approveBtn" onclick="approveCurrentPlan()" style="background:linear-gradient(135deg,#3b82f6,#2563eb);color:#fff;border:none;padding:10px 20px;border-radius:8px;font-size:0.85rem;font-weight:600;cursor:pointer;">
                    ✓ Approve Plan
                </button>
                <button type="button" id="applyPlanBtn" onclick="applyApprovedPlan()" style="background:linear-gradient(135deg,#10b981,#059669);color:#fff;border:none;padding:10px 22px;border-radius:8px;font-size:0.85rem;font-weight:600;cursor:pointer;" disabled>
                    ⚡ Apply Approved Plan to Codebase
                </button>
            </div>
        </div>
    </div>

    <!-- Post-Application Verification Section -->
    <div id="verificationSection" class="simulation-section">
        <div style="text-align:center;margin-bottom:20px;">
            <div style="font-size:3rem;margin-bottom:8px;">🎉</div>
            <h3 style="color:#34d399;margin:0 0 4px 0;font-size:1.2rem;">Plan Applied & Verified Successfully!</h3>
            <p style="color:#94a3b8;font-size:0.82rem;margin:0;">The actual codebase has been re-analyzed. Compare the actual post-refactoring architecture against the simulated prediction below.</p>
        </div>

        <table class="delta-table">
            <thead>
                <tr>
                    <th>Metric</th>
                    <th>Simulated Prediction</th>
                    <th>Actual Post-Refactoring</th>
                    <th>Discrepancy</th>
                    <th>Concordance</th>
                </tr>
            </thead>
            <tbody id="verificationTableBody"></tbody>
        </table>

        <div style="display:flex;justify-content:space-between;align-items:center;margin-top:24px;flex-wrap:wrap;gap:12px;">
            <a id="downloadZipLink" href="<%= ctx %>/projects/download/zip" style="background:linear-gradient(135deg,#3b82f6,#2563eb);color:#fff;text-decoration:none;padding:10px 22px;border-radius:8px;font-size:0.86rem;font-weight:600;display:inline-flex;align-items:center;gap:8px;">
                📦 Download Refactored Project ZIP
            </a>
            <button type="button" onclick="location.reload()" style="background:var(--sim-panel-2);border:1px solid var(--sim-border);color:#cbd5e1;padding:10px 20px;border-radius:8px;font-size:0.85rem;cursor:pointer;">
                🔄 Reload Planner
            </button>
        </div>
    </div>

</div>

<script>
const CTX = '<%= ctx %>';
let currentPlanHash = '';
let isStale = false;

document.addEventListener('DOMContentLoaded', () => {
    loadCandidates();
    loadActivePlan();
});

function esc(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

async function loadCandidates() {
    const list = document.getElementById('candidateList');
    try {
        const res = await fetch(`${CTX}/api/refactor/simulation/candidates`);
        const json = await res.json();
        if (!json.candidates || json.candidates.length === 0) {
            list.innerHTML = '<div style="text-align:center;padding:30px;color:#64748b;">No candidate operations found.</div>';
            return;
        }

        window.currentCandidates = json.candidates || [];
        list.innerHTML = json.candidates.map((c, idx) => `
            <div class="candidate-item">
                <div class="op-info">
                    <span class="op-badge ${getBadgeClass(c.type)}">${esc(c.type)}</span>
                    <div class="op-title">${esc(c.description)}</div>
                    <div class="op-desc">Target: ${esc(c.targetComponent || '')} ${c.secondaryComponent ? '⇄ ' + esc(c.secondaryComponent) : ''}</div>
                </div>
                <button type="button" class="btn-add" onclick="addCandidateByIndex(${idx})">
                    + Add
                </button>
            </div>
        `).join('');
    } catch (e) {
        list.innerHTML = '<div style="text-align:center;padding:30px;color:#fca5a5;">Failed to load candidates.</div>';
    }
}

function getBadgeClass(type) {
    if (type === 'BREAK_CYCLE') return 'badge-cycle';
    if (type === 'EXTRACT_INTERFACE') return 'badge-extract';
    if (type === 'REMOVE_DEPENDENCY') return 'badge-remove';
    return 'badge-redirect';
}

async function loadActivePlan() {
    const list = document.getElementById('planList');
    try {
        const res = await fetch(`${CTX}/api/refactor/simulation/plan`);
        const json = await res.json();
        document.getElementById('planOpCount').innerText = json.operationsCount || 0;
        currentPlanHash = json.planHash || '';
        isStale = json.isStale;

        document.getElementById('staleAlert').style.display = isStale ? 'flex' : 'none';
        document.getElementById('simulatePlanBtn').disabled = (json.operationsCount === 0);

        if (!json.operations || json.operations.length === 0) {
            list.innerHTML = '<div style="text-align:center;padding:40px;color:#64748b;">No operations in plan.<br>Add operations from the candidates browser on the left.</div>';
            return;
        }

        window.currentPlanOperations = json.operations || [];
        list.innerHTML = json.operations.map((op, idx) => `
            <div class="plan-item">
                <div style="font-family:'JetBrains Mono',monospace;color:#64748b;font-size:0.75rem;width:24px;">#${idx + 1}</div>
                <div class="op-info">
                    <span class="op-badge ${getBadgeClass(op.type)}">${esc(op.type)}</span>
                    <div class="op-title">${esc(op.description)}</div>
                    <div class="op-desc">${esc(op.id)}</div>
                </div>
                <div style="display:flex;gap:4px;align-items:center;">
                    ${idx > 0 ? `<button type="button" class="btn-remove" style="color:#93c5fd;border-color:rgba(59,130,246,0.3);" onclick="moveOperation(${idx}, -1)" title="Move Up">▲</button>` : ''}
                    ${idx < json.operations.length - 1 ? `<button type="button" class="btn-remove" style="color:#93c5fd;border-color:rgba(59,130,246,0.3);" onclick="moveOperation(${idx}, 1)" title="Move Down">▼</button>` : ''}
                    <button type="button" class="btn-remove" onclick="removeOperation('${esc(op.id)}')">Remove</button>
                </div>
            </div>
        `).join('');
    } catch (e) {
        list.innerHTML = '<div style="text-align:center;padding:30px;color:#fca5a5;">Failed to load active plan.</div>';
    }
}

function addCandidateByIndex(idx) {
    const c = window.currentCandidates && window.currentCandidates[idx];
    if (!c || !c.type || c.type === 'undefined') return;
    addCandidateToPlan(c.type, c.targetComponent, c.secondaryComponent || '', c.newComponent || '', c.description || '');
}

async function moveOperation(idx, direction) {
    const ops = window.currentPlanOperations || [];
    const targetIdx = idx + direction;
    if (targetIdx < 0 || targetIdx >= ops.length) return;
    const ids = ops.map(o => o.id);
    const temp = ids[idx];
    ids[idx] = ids[targetIdx];
    ids[targetIdx] = temp;
    const params = new URLSearchParams({ order: ids.join(',') });
    await fetch(`${CTX}/api/refactor/simulation/plan/reorder`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params
    });
    loadActivePlan();
}

async function addCandidateToPlan(type, target, secondary, newComponent, description) {
    if (!type || type === 'undefined') return;
    const params = new URLSearchParams({
        type: type,
        target: target || '',
        secondary: secondary || '',
        newComponent: newComponent || '',
        description: description || ''
    });
    await fetch(`${CTX}/api/refactor/simulation/plan/operation`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params
    });
    loadActivePlan();
}

async function removeOperation(opId) {
    const params = new URLSearchParams({ id: opId });
    await fetch(`${CTX}/api/refactor/simulation/plan/remove`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params
    });
    loadActivePlan();
}

async function clearPlan() {
    await fetch(`${CTX}/api/refactor/simulation/plan/clear`, { method: 'POST' });
    loadActivePlan();
    document.getElementById('simulationSection').classList.remove('visible');
}

async function runCombinedSimulation() {
    const btn = document.getElementById('simulatePlanBtn');
    btn.disabled = true;
    btn.innerText = 'Simulating...';

    try {
        const res = await fetch(`${CTX}/api/refactor/simulation/simulate`, { method: 'POST' });
        const json = await res.json();

        if (json.status !== 'ok') {
            alert('Simulation could not proceed: ' + (json.simulation?.conflicts?.map(c => c.message).join('\n') || json.error || 'Conflict detected.'));
            return;
        }

        renderSimulationResults(json.simulation);
        loadActivePlan();
    } catch (e) {
        alert('Simulation failed: ' + e);
    } finally {
        btn.disabled = false;
        btn.innerText = '⚡ Simulate Combined Plan';
    }
}

function renderSimulationResults(sim) {
    const sec = document.getElementById('simulationSection');
    sec.classList.add('visible');

    // Table rows
    const tbody = document.getElementById('deltaTableBody');
    const deltas = sim.metricDeltas || {};
    let html = '';

    for (const [name, d] of Object.entries(deltas)) {
        let cls = 'delta-neutral';
        let evalTxt = 'Unchanged';
        if (d.isImprovement) {
            cls = 'delta-improved';
            evalTxt = '✓ Improved';
        } else if (d.absoluteDelta !== 0 && d.preference !== 'NEUTRAL') {
            cls = 'delta-degraded';
            evalTxt = 'Noticeable Trade-off';
        }

        html += `
            <tr>
                <td style="font-weight:600;color:#f1f5f9;">${esc(name)}</td>
                <td style="font-family:'JetBrains Mono',monospace;">${d.baselineValue}</td>
                <td style="font-family:'JetBrains Mono',monospace;">${d.simulatedValue}</td>
                <td class="${cls}" style="font-family:'JetBrains Mono',monospace;">${d.absoluteDelta > 0 ? '+' : ''}${d.absoluteDelta}</td>
                <td class="${cls}" style="font-family:'JetBrains Mono',monospace;">${d.percentageChange > 0 ? '+' : ''}${d.percentageChange}%</td>
                <td class="${cls}">${evalTxt}</td>
            </tr>
        `;
    }
    tbody.innerHTML = html;

    // Tradeoffs
    const tradeoffs = sim.tradeoffs || [];
    document.getElementById('tradeoffsList').innerHTML = tradeoffs.length > 0
        ? tradeoffs.map(t => `<li>${esc(t)}</li>`).join('')
        : '<li>Combined architecture preserved metric equilibrium without adverse trade-offs.</li>';

    // Transformations
    const addedNodes = sim.addedNodes || [];
    document.getElementById('addedNodesView').innerText = addedNodes.length > 0 ? addedNodes.join('\n') : 'None';

    const removedEdges = sim.removedEdges || [];
    document.getElementById('removedEdgesView').innerText = removedEdges.length > 0 ? removedEdges.join('\n') : 'None';

    // Enable approve
    document.getElementById('approveBtn').disabled = false;
    document.getElementById('applyPlanBtn').disabled = true;
    sec.scrollIntoView({ behavior: 'smooth' });
}

async function approveCurrentPlan() {
    const btn = document.getElementById('approveBtn');
    btn.disabled = true;

    try {
        const params = new URLSearchParams({ simulationHash: currentPlanHash });
        const res = await fetch(`${CTX}/api/refactor/simulation/approve`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params
        });
        const json = await res.json();
        if (json.status === 'ok') {
            alert('Plan successfully approved for execution!');
            document.getElementById('applyPlanBtn').disabled = false;
        } else {
            alert('Approval rejected: ' + json.error);
        }
    } catch (e) {
        alert('Approval failed: ' + e);
    } finally {
        btn.disabled = false;
    }
}

async function applyApprovedPlan() {
    if (!confirm('Are you sure you want to apply this verified refactoring plan to the actual project code?')) return;

    const btn = document.getElementById('applyPlanBtn');
    btn.disabled = true;
    btn.innerText = 'Applying & Verifying...';

    const runMaven = document.getElementById('runMavenTestsPlanner').checked;
    const params = new URLSearchParams({ runMavenTests: runMaven ? 'true' : 'false' });

    try {
        const res = await fetch(`${CTX}/api/refactor/simulation/apply`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params
        });
        const json = await res.json();

        if (json.status !== 'ok') {
            alert('Application failed and was safely rolled back:\n' + (json.report?.discrepancies?.join('\n') || json.error));
            return;
        }

        renderVerification(json.report);
    } catch (e) {
        alert('Application failed with exception: ' + e);
    } finally {
        btn.disabled = false;
        btn.innerText = '⚡ Apply Approved Plan to Codebase';
    }
}

function renderVerification(report) {
    const sec = document.getElementById('verificationSection');
    sec.classList.add('visible');

    const tbody = document.getElementById('verificationTableBody');
    tbody.innerHTML = (report.comparisons || []).map(c => `
        <tr>
            <td style="font-weight:600;color:#f1f5f9;">${esc(c.metricName)}</td>
            <td style="font-family:'JetBrains Mono',monospace;">${c.simulatedValue}</td>
            <td style="font-family:'JetBrains Mono',monospace;font-weight:700;color:#34d399;">${c.actualValue}</td>
            <td style="font-family:'JetBrains Mono',monospace;">${c.discrepancy}</td>
            <td style="color:${c.isConcordant ? '#34d399' : '#f87171'};font-weight:600;">
                ${c.isConcordant ? '✓ Concordant' : '⚠ Discrepancy'}
            </td>
        </tr>
    `).join('');

    sec.scrollIntoView({ behavior: 'smooth' });
}
</script>

</body>
</html>
