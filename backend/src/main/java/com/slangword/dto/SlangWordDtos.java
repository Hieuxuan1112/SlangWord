package com.slangword.dto;

import com.slangword.domain.SlangWord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class SlangWordDtos {

    private SlangWordDtos() {
    }

    public record SlangWordResponse(
            Long id,
            String word,
            List<String> definitions,
            Instant createdAt,
            Instant updatedAt) {

        public static SlangWordResponse from(SlangWord entity) {
            return new SlangWordResponse(
                    entity.getId(),
                    entity.getWord(),
                    entity.getDefinitions().stream().map(d -> d.getText()).toList(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt());
        }
    }

    public record UpsertSlangWordRequest(
            @NotBlank @Size(max = 128) String word,
            @NotEmpty List<@NotBlank @Size(max = 2000) String> definitions) {
    }

    public record UpdateSlangWordRequest(
            @NotEmpty List<@NotBlank @Size(max = 2000) String> definitions) {
    }
}
