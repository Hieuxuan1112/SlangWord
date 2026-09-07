package com.slangword.service;

import com.slangword.domain.SlangWord;
import com.slangword.dto.PageResponse;
import com.slangword.dto.SlangWordDtos.SlangWordResponse;
import com.slangword.dto.SlangWordDtos.UpdateSlangWordRequest;
import com.slangword.dto.SlangWordDtos.UpsertSlangWordRequest;
import com.slangword.exception.ConflictException;
import com.slangword.exception.NotFoundException;
import com.slangword.repository.SlangWordRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        Page<Long> ids = fragment.isEmpty()
                ? repository.findAllIds(pageable)
                : switch (field) {
                    case WORD -> repository.findIdsByWordFragment(fragment, pageable);
                    case DEFINITION -> repository.findIdsByDefinitionFragment(fragment, pageable);
                };
        return fetchPage(ids);
    }

    /**
     * Second half of the two-query search: the database has already applied
     * LIMIT/OFFSET to the ids, so this fetches exactly one page of entities
     * with their definitions. {@code findByIdIn} does not preserve order, so
     * the order of the id page is restored here.
     */
    private PageResponse<SlangWordResponse> fetchPage(Page<Long> ids) {
        if (ids.isEmpty()) {
            return new PageResponse<>(List.of(), ids.getNumber(), ids.getSize(),
                    ids.getTotalElements(), ids.getTotalPages(), ids.isLast());
        }
        Map<Long, SlangWord> byId = repository.findByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(SlangWord::getId, Function.identity()));
        List<SlangWordResponse> content = ids.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(SlangWordResponse::from)
                .toList();
        return new PageResponse<>(content, ids.getNumber(), ids.getSize(),
                ids.getTotalElements(), ids.getTotalPages(), ids.isLast());
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
