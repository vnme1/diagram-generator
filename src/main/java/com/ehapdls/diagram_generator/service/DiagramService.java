package com.ehapdls.diagram_generator.service;

import com.ehapdls.diagram_generator.client.GeminiClient;
import com.ehapdls.diagram_generator.exception.DiagramGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagramService {

    private final GeminiClient geminiClient;

    private static final Set<String> VALID_DIAGRAM_PREFIXES = Set.of(
            "graph ", "graph\n",
            "flowchart ", "flowchart\n",
            "sequenceDiagram", "classDiagram", "stateDiagram",
            "erDiagram", "gantt", "pie ", "pie\n",
            "gitgraph", "mindmap", "timeline",
            "C4Context", "C4Container", "C4Component", "C4Deployment"
    );

    private static final Pattern MARKDOWN_FENCE = Pattern.compile(
            "^```(?:mermaid)?\\s*\\n?(.*?)\\n?```\\s*$", Pattern.DOTALL
    );

    /**
     * Matches an unquoted label inside Mermaid bracket/brace node shapes, e.g.:
     *   id[some text]   id{some text}   id[(some text)]
     * but NOT already-quoted labels like id["text"] or id['text'].
     *
     * Capture group 1 = opening bracket(s), group 2 = inner text, group 3 = closing bracket(s).
     */
    private static final Pattern UNQUOTED_LABEL = Pattern.compile(
            "(\\[{1,2}|\\{{1,2})(?![\"'/])([^\\[\\]{}'\"\\n]+?)(?<![\"'/])(\\}{1,2}|\\]{1,2})"
    );

    /**
     * Matches flowchart-only inline styling lines. Intentionally excludes
     * classDiagram class bodies (which use "class Name {") by requiring the
     * line NOT to end with "{" for the "class" keyword.
     *
     *  style nodeId fill:...          → removed
     *  classDef myStyle fill:...      → removed
     *  class nodeId myStyle           → removed  (flowchart class application)
     *  class UserClass {              → kept      (classDiagram class definition)
     */
    private static final Pattern STYLE_COMMAND_LINE = Pattern.compile(
            "^[ \\t]*(?:style[ \\t]+\\S[^\\n]*" +
            "|classDef[ \\t]+\\S[^\\n]*" +
            "|class[ \\t]+\\S[^\\n{]*(?<!\\{))$",
            Pattern.MULTILINE
    );

    /**
     * Matches a colon-annotation that AI sometimes appends after a node definition,
     * e.g.  id["label"]: "some description"
     * This is sequence-diagram syntax and is illegal in flowchart context.
     * We strip everything from the colon to the end of that line segment.
     */
    private static final Pattern COLON_ANNOTATION = Pattern.compile(
            "(?<=[\\]})>])[ \\t]*:[ \\t]+\"[^\"\\n]*\""
    );

    /**
     * Generates a Mermaid.js diagram from the user prompt via Gemini API.
     *
     * @param prompt   the user's architecture description
     * @param language "ko" for Korean, "en" for English
     * @return valid Mermaid.js diagram code
     */
    public String generate(String prompt, String language) {
        String langName = "ko".equals(language) ? "한국어(Korean)" : "English";
        String systemInstruction = buildSystemInstruction(langName);

        log.info("Generating diagram: language={}, promptLength={}", language, prompt.length());

        String rawResponse = geminiClient.generateContent(systemInstruction, prompt);
        String cleaned = sanitizeResponse(rawResponse);

        validateMermaidSyntax(cleaned);

        return cleaned;
    }

    private String buildSystemInstruction(String langName) {
        return """
                You are a senior software architect with 20 years of experience.
                Analyze the user's requirements and return ONLY valid Mermaid.js diagram code.

                STRICT RULES:
                1. Output ONLY the raw Mermaid.js code. No markdown fences (```), no greetings, no explanations, no commentary.
                2. The diagram must start with a valid Mermaid diagram type (graph TD, flowchart TD, sequenceDiagram, classDiagram, erDiagram, etc.).
                3. All node labels, descriptions, and edge labels inside the diagram MUST be written in %s.
                4. Use descriptive and meaningful node IDs.
                5. Apply appropriate styling with colors to make the diagram visually clear.
                6. Keep the diagram well-structured and readable — avoid overly complex layouts.
                7. If the user's description is vague, infer a reasonable architecture and produce the best possible diagram.
                8. CRITICAL — QUOTING: Every node label MUST be wrapped in double quotes, without exception.
                   Correct:   id["My Label (detail)"]   id{"Choice?"}   id[/"Step A"/]
                   Incorrect: id[My Label (detail)]     id{Choice?}     id[/Step A/]
                   This prevents Mermaid parse errors caused by parentheses, colons, slashes, and non-ASCII characters.
                9. CRITICAL — NODE IDs: Node IDs must be short alphanumeric English identifiers only (letters, digits, underscores).
                   NEVER use Korean, CJK, spaces, or special characters as a node ID.
                   Korean/non-ASCII text belongs ONLY inside the quoted label string.
                   Correct:   userNode["사용자"]   dbPrimary["주 데이터베이스"]
                   Incorrect: 사용자["사용자"]     주DB["주 데이터베이스"]
                   This rule also applies to subgraph IDs and any other identifiers.
                10. CRITICAL — NO COLON ANNOTATIONS: In flowchart diagrams, never append `: "text"` after a node definition.
                    That syntax belongs only to sequenceDiagram. Use edge labels instead: A -->|"label"| B
                    Incorrect: userNode["사용자"]: "설명"
                    Correct:   userNode["사용자"] -->|"설명"| nextNode
                11. CRITICAL — NO STYLING COMMANDS: Do NOT output any `style`, `classDef`, or `class` lines.
                    These commands cause frequent and hard-to-diagnose Mermaid parse errors.
                    Convey visual structure through node shapes (rectangles, diamonds, cylinders, stadiums)
                    and subgraph grouping only. Never add color or style overrides.
                """.formatted(langName);
    }

    /**
     * Strips markdown fences, trims whitespace, and sanitizes node labels.
     */
    private String sanitizeResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DiagramGenerationException("AI returned an empty response");
        }

        String trimmed = raw.strip();

        // Strip markdown code fences if present
        var matcher = MARKDOWN_FENCE.matcher(trimmed);
        if (matcher.matches()) {
            trimmed = matcher.group(1).strip();
        }

        trimmed = stripStyleCommands(trimmed);
        trimmed = stripColonAnnotations(trimmed);
        return quoteUnescapedLabels(trimmed);
    }

    /**
     * Removes all inline Mermaid styling commands (style, classDef, class) from the
     * generated code. These lines are cosmetic-only and are the primary source of
     * Mermaid parse errors — they reference node IDs in ways the parser frequently
     * rejects when the surrounding context is complex.
     */
    private String stripStyleCommands(String code) {
        String stripped = STYLE_COMMAND_LINE.matcher(code).replaceAll("");
        if (!stripped.equals(code)) {
            log.debug("Stripped inline styling commands from Mermaid output");
        }
        return stripped;
    }

    /**
     * Removes `: "annotation"` patterns that AI appends after node closings.
     * e.g. id["label"]: "description"  →  id["label"]
     * Colon-message syntax is only valid in sequenceDiagram, not flowcharts.
     */
    private String stripColonAnnotations(String code) {
        String stripped = COLON_ANNOTATION.matcher(code).replaceAll("");
        if (!stripped.equals(code)) {
            log.debug("Stripped colon annotations from Mermaid output");
        }
        return stripped;
    }

    /**
     * Finds unquoted Mermaid node labels that contain characters known to break the
     * Mermaid parser (parentheses, colons, slashes, non-ASCII / Korean, etc.) and
     * wraps them in double quotes automatically.
     *
     * Examples fixed:
     *   id[Petra (접근제어)]          →  id["Petra (접근제어)"]
     *   id{Yes/No}                   →  id{"Yes/No"}
     */
    private String quoteUnescapedLabels(String code) {
        Matcher m = UNQUOTED_LABEL.matcher(code);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String open  = m.group(1);
            String inner = m.group(2);
            String close = m.group(3);

            boolean needsQuoting = inner.chars().anyMatch(c ->
                    c > 127                    // non-ASCII (Korean, CJK, etc.)
                    || "()/\\:;,<>@#!?&*+=~^|".indexOf(c) >= 0  // Mermaid-breaking special chars
            );

            if (needsQuoting) {
                String escaped = inner.replace("\"", "'");
                m.appendReplacement(sb, Matcher.quoteReplacement(open + "\"" + escaped + "\"" + close));
                log.debug("Auto-quoted label: [{}] → [\"{}\"]]", inner, escaped);
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Validates that the output begins with a recognized Mermaid diagram type keyword.
     */
    private void validateMermaidSyntax(String code) {
        boolean valid = VALID_DIAGRAM_PREFIXES.stream().anyMatch(code::startsWith);
        if (!valid) {
            log.warn("Invalid Mermaid syntax detected. First 100 chars: {}",
                    code.substring(0, Math.min(100, code.length())));
            throw new DiagramGenerationException(
                    "AI generated invalid diagram code. Please rephrase your prompt and try again."
            );
        }
    }
}
