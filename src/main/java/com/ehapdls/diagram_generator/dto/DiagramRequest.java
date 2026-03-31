package com.ehapdls.diagram_generator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiagramRequest {

    @NotBlank(message = "Prompt must not be empty")
    @Size(max = 2000, message = "Prompt must be 2000 characters or fewer")
    private String prompt;

    @Pattern(regexp = "^(ko|en)$", message = "Language must be 'ko' or 'en'")
    private String language = "en";
}
