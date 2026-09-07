package com.slangword.service;

import com.slangword.domain.SlangWord;
import com.slangword.dto.PageResponse;
import com.slangword.dto.SlangWordDtos.SlangWordResponse;
import com.slangword.dto.SlangWordDtos.UpdateSlangWordRequest;
import com.slangword.dto.SlangWordDtos.UpsertSlangWordRequest;
import com.slangword.exception.ConflictException;
import com.slangword.exception.NotFoundException;
import com.slangword.repository.SlangWordRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SlangWordService {

    public enum SearchField {
        WORD,
        DEFINITION
    }

    private final SlangWordRepository repository;

    public SlangWordService(SlangWordRepository repository) {
        this.repository = repository;
    }

    public SlangWordResponse getByWord(String word) {
        return repository.findByWordIgnoreCase(word)
                .map(SlangWordResponse::from)
                .orElseThrow(() -> new NotFoundException("Slang word not found: " + word));
    }

    public PageResponse<SlangWordResponse> search(String query, SearchField field, Pageable pageable) {
        String fragment = query == null ? "" : query.trim();
        Page<SlangWord> page = fragment.isEmpty()
                ? repository.findAll(pageable)
                : switch (field) {
                    case WORD -> repository.findByWordContainingIgnoreCase(fragment, pageable);
                    case DEFINITION -> repository.searchByDefinition(fragment, pageable);
                };
        return PageResponse.of(page, SlangWordResponse::from);
    }

    public SlangWordResponse random() {
        return repository.findRandom()
                .map(SlangWordResponse::from)
                .orElseThrow(() -> new NotFoundException("Dictionary is empty"));
    }

    /**
     * Adding a word that already exists is refused unless the caller explicitly asks
     * to overwrite — the REST form of the Swing app's "already exists, overwrite?" dialog.
     */
    @Transactional
    public SlangWordResponse create(UpsertSlangWordRequest request, boolean overwrite) {
        String word = request.word().trim();
        Optional<SlangWord> existing = repository.findByWordIgnoreCase(word);
        if (existing.isPresent() && !overwrite) {
            throw new ConflictException("Slang word already exists: " + word);
        }
        SlangWord entity = existing.orElseGet(() -> new SlangWord(word));
        entity.replaceDefinitions(request.definitions().stream().map(String::trim).toList());
        return SlangWordResponse.from(repository.save(entity));
    }

    @Transactional
    public SlangWordResponse update(String word, UpdateSlangWordRequest request) {
        SlangWord entity = repository.findByWordIgnoreCase(word)
                .orElseThrow(() -> new NotFoundException("Slang word not found: " + word));
        entity.replaceDefinitions(request.definitions().stream().map(String::trim).toList());
        return SlangWordResponse.from(repository.save(entity));
    }

    @Transactional
    public void delete(String word) {
        SlangWord entity = repository.findByWordIgnoreCase(word)
                .orElseThrow(() -> new NotFoundException("Slang word not found: " + word));
        repository.delete(entity);
    }
}
