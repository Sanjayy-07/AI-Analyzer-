package com.archanalyzer.explanation.llm;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java parser for LLM explanation JSON payloads.
 * Handles markdown code block stripping, bracket matching, string and array extraction,
 * and graceful fallback if the response contains partial JSON.
 */
public class ExplanationResponseParser {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    public ExplanationResponse parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new ExplanationResponse("", "No explanation response received from model.", List.of(), List.of(), List.of());
        }

        String cleanedJson = cleanRawResponse(rawText);

        String overview = extractStringField(cleanedJson, "overview");
        if (overview == null || overview.isBlank()) {
            overview = extractFirstParagraph(rawText);
        }

        List<ExplanationResponse.FindingDto> findings = parseFindings(cleanedJson);
        List<ExplanationResponse.RecommendationDto> recommendations = parseRecommendations(cleanedJson);
        List<ExplanationResponse.ExtractedClaimDto> claims = parseClaims(cleanedJson);

        // Fallback: If no explicit claims array was parsed, synthesize claims from findings and recommendations
        if (claims.isEmpty()) {
            claims = synthesizeClaimsFromFindingsAndRecs(findings, recommendations);
        }

        return new ExplanationResponse(rawText, overview, findings, recommendations, claims);
    }

    private String cleanRawResponse(String raw) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        int firstBrace = raw.indexOf('{');
        int lastBrace = raw.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return raw.substring(firstBrace, lastBrace + 1).trim();
        }
        return raw.trim();
    }

    private String extractFirstParagraph(String text) {
        String[] lines = text.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("{") || line.startsWith("```") || line.startsWith("[")) continue;
            if (!line.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(line);
                if (sb.length() > 200) break;
            }
        }
        return sb.length() > 0 ? sb.toString() : "Architectural analysis completed.";
    }

    private List<ExplanationResponse.FindingDto> parseFindings(String json) {
        List<ExplanationResponse.FindingDto> list = new ArrayList<>();
        List<String> objects = extractObjectsFromArray(json, "findings");
        int index = 0;
        for (String obj : objects) {
            index++;
            String id = extractStringField(obj, "findingId");
            if (id == null) id = "finding-" + index;
            String comp = extractStringField(obj, "componentId");
            String issue = extractStringField(obj, "issue");
            String sev = extractStringField(obj, "severity");
            String exp = extractStringField(obj, "explanation");
            List<String> evs = extractStringArray(obj, "evidenceIds");
            list.add(new ExplanationResponse.FindingDto(id, comp, issue, sev, exp, evs));
        }
        return list;
    }

    private List<ExplanationResponse.RecommendationDto> parseRecommendations(String json) {
        List<ExplanationResponse.RecommendationDto> list = new ArrayList<>();
        List<String> objects = extractObjectsFromArray(json, "recommendations");
        int index = 0;
        for (String obj : objects) {
            index++;
            String id = extractStringField(obj, "recommendationId");
            if (id == null) id = "rec-" + index;
            String target = extractStringField(obj, "targetComponent");
            String action = extractStringField(obj, "action");
            String rat = extractStringField(obj, "rationale");
            List<String> evs = extractStringArray(obj, "evidenceIds");
            String benefit = extractStringField(obj, "projectedBenefit");
            list.add(new ExplanationResponse.RecommendationDto(id, target, action, rat, evs, benefit));
        }
        return list;
    }

    private List<ExplanationResponse.ExtractedClaimDto> parseClaims(String json) {
        List<ExplanationResponse.ExtractedClaimDto> list = new ArrayList<>();
        List<String> objects = extractObjectsFromArray(json, "claims");
        int index = 0;
        for (String obj : objects) {
            index++;
            String id = extractStringField(obj, "claimId");
            if (id == null) id = "claim-" + index;
            String type = extractStringField(obj, "claimType");
            String subject = extractStringField(obj, "subjectId");
            String target = extractStringField(obj, "targetId");
            String metric = extractStringField(obj, "metricName");
            Double val = extractDoubleField(obj, "claimedNumericValue");
            String textVal = extractStringField(obj, "claimedTextValue");
            String text = extractStringField(obj, "text");
            List<String> evs = extractStringArray(obj, "evidenceIds");

            if (text == null || text.isBlank()) {
                text = (subject != null ? subject : "Component") + (metric != null ? " " + metric + " = " + val : "");
            }

            list.add(new ExplanationResponse.ExtractedClaimDto(
                    id,
                    type != null ? type.toUpperCase() : "INTERPRETATION",
                    subject,
                    target,
                    metric,
                    val,
                    textVal,
                    text,
                    evs
            ));
        }
        return list;
    }

    private List<ExplanationResponse.ExtractedClaimDto> synthesizeClaimsFromFindingsAndRecs(
            List<ExplanationResponse.FindingDto> findings,
            List<ExplanationResponse.RecommendationDto> recs
    ) {
        List<ExplanationResponse.ExtractedClaimDto> list = new ArrayList<>();
        int claimIdx = 0;
        for (ExplanationResponse.FindingDto f : findings) {
            claimIdx++;
            list.add(new ExplanationResponse.ExtractedClaimDto(
                    "synth-claim-" + claimIdx,
                    "INTERPRETATION",
                    f.componentId(),
                    null,
                    null,
                    null,
                    f.severity(),
                    f.issue() != null ? f.issue() : f.explanation(),
                    f.evidenceIds()
            ));
        }
        for (ExplanationResponse.RecommendationDto r : recs) {
            claimIdx++;
            list.add(new ExplanationResponse.ExtractedClaimDto(
                    "synth-claim-" + claimIdx,
                    "RECOMMENDATION",
                    r.targetComponent(),
                    null,
                    null,
                    null,
                    null,
                    r.action() != null ? r.action() + ": " + r.rationale() : r.rationale(),
                    r.evidenceIds()
            ));
        }
        return list;
    }

    private List<String> extractObjectsFromArray(String json, String arrayKey) {
        List<String> result = new ArrayList<>();
        int keyPos = json.indexOf("\"" + arrayKey + "\"");
        if (keyPos < 0) return result;
        int bracketOpen = json.indexOf('[', keyPos);
        if (bracketOpen < 0) return result;
        int bracketClose = findMatchingBracket(json, bracketOpen, '[', ']');
        if (bracketClose <= bracketOpen) return result;

        String body = json.substring(bracketOpen + 1, bracketClose).trim();
        int depth = 0;
        int start = -1;
        boolean inQuote = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            }
            if (!inQuote) {
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        result.add(body.substring(start, i + 1));
                        start = -1;
                    }
                }
            }
        }
        return result;
    }

    private String extractStringField(String obj, String fieldName) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(obj);
        if (m.find()) return unescapeJson(m.group(1));
        return null;
    }

    private Double extractDoubleField(String obj, String fieldName) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*([+-]?[0-9]+(?:\\.[0-9]+)?)");
        Matcher m = p.matcher(obj);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private List<String> extractStringArray(String obj, String fieldName) {
        List<String> list = new ArrayList<>();
        int keyPos = obj.indexOf("\"" + fieldName + "\"");
        if (keyPos < 0) return list;
        int bracketOpen = obj.indexOf('[', keyPos);
        if (bracketOpen < 0) return list;
        int bracketClose = findMatchingBracket(obj, bracketOpen, '[', ']');
        if (bracketClose <= bracketOpen) return list;

        String body = obj.substring(bracketOpen + 1, bracketClose).trim();
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(body);
        while (m.find()) {
            list.add(unescapeJson(m.group(1)));
        }
        return list;
    }

    private int findMatchingBracket(String text, int openPos, char openChar, char closeChar) {
        int depth = 0;
        boolean inQuote = false;
        for (int i = openPos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            }
            if (!inQuote) {
                if (c == openChar) depth++;
                else if (c == closeChar) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\");
    }
}
