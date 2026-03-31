package com.ehapdls.diagram_generator.controller;

import com.ehapdls.diagram_generator.dto.DiagramRequest;
import com.ehapdls.diagram_generator.dto.DiagramResponse;
import com.ehapdls.diagram_generator.service.DiagramService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DiagramController {

    private final DiagramService diagramService;

    @PostMapping("/diagram")
    public ResponseEntity<DiagramResponse> generateDiagram(
            @Valid @RequestBody DiagramRequest request) {

        String mermaidCode = diagramService.generate(request.getPrompt(), request.getLanguage());
        return ResponseEntity.ok(new DiagramResponse(mermaidCode));
    }
}
