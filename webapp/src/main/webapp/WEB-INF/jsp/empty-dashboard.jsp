<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Dashboard</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/main.css">
</head>
<body>
<header class="topbar">
    <div style="display:flex;align-items:center;">
        <a href="${pageContext.request.contextPath}/" style="text-decoration:none;"><div class="brand">⚡ Java ArchAnalyzer</div></a>
    </div>
    <nav>
        <a href="${pageContext.request.contextPath}/">Home</a>
        <a href="${pageContext.request.contextPath}/projects/upload">Upload</a>
        <a href="${pageContext.request.contextPath}/dashboard" class="active">Dashboard</a>
        <a href="${pageContext.request.contextPath}/planner">🗺️ Planner</a>
        <a href="${pageContext.request.contextPath}/risk">Risk Prediction</a>
        <a href="${pageContext.request.contextPath}/explanation">AI Explanations</a>
        <a href="${pageContext.request.contextPath}/simulator">What-If Simulator</a>
        <a href="${pageContext.request.contextPath}/history">🕒 History</a>
    </nav>
</header>
<main class="dashboard">
    <div class="empty-state">
        <h2>No analysis yet</h2>
        <p>Upload a project archive to see its dependency graph, architecture health, and ML risk predictions.</p>
        <a class="cta" href="${pageContext.request.contextPath}/projects/upload">Upload a project</a>
    </div>
</main>
</body>
</html>
