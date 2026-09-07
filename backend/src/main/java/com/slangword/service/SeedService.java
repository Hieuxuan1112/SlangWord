package com.slangword.service;

import com.slangword.config.SeedProperties;
import com.slangword.domain.SlangWord;
import com.slangword.repository.SlangWordRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the dictionary from the original backtick-delimited {@code slang.txt}.
 * Also backs the admin reset endpoint, which is the REST equivalent of the
 * Swing app's Reset button.
 */
@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private static final String FIELD_SEPARATOR = "`";
    private static final Pattern SENSE_SEPARATOR = Pattern.compile(Pattern.quote(" | "));
    private static final int BATCH_SIZE = 500;

    private final SlangWordRepository slangWordRepository;
    private final ResourceLoader resourceLoader;
    private final String seedLocation;

    public SeedService(SlangWordRepository slangWordRepository,
                       ResourceLoader resourceLoader,
                       SeedProperties properties) {
        this.slangWordRepository = slangWordRepository;
        this.resourceLoader = resourceLoader;
        this.seedLocation = properties.file();
    }

    public record SeedEntry(String word, List<String> definitions) {
    }

    /**
     * Splits each line into a word and its definitions. Malformed lines are dropped;
     * a repeated word keeps its last occurrence, matching the original app's
     * {@code HashMap.put} behaviour.
     */
    static List<SeedEntry> parse(List<String> lines) {
        Map<String, List<String>> byWord = new LinkedHashMap<>();
        for (String line : lines) {
            int separator = line.indexOf(FIELD_SEPARATOR);
            if (separator <= 0 || separator == line.length() - 1) {
                continue;
            }
            String word = line.substring(0, separator).trim();
            String rawDefinition = line.substring(separator + 1).trim();
            if (word.isEmpty() || rawDefinition.isEmpty()) {
                continue;
            }
            List<String> senses = new ArrayList<>();
            for (String sense : SENSE_SEPARATOR.split(rawDefinition)) {
                String trimmed = sense.trim();
                if (!trimmed.isEmpty()) {
                    senses.add(trimmed);
                }
            }
            if (!senses.isEmpty()) {
                byWord.put(word, senses);
            }
        }
        return byWord.entrySet().stream()
                .map(entry -> new SeedEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional
    public int seedIfEmpty() {
        if (slangWordRepository.count() > 0) {
            log.info("Dictionary already populated, skipping seed");
            return 0;
        }
        return load();
    }

    @Transactional
    public int reset() {
        slangWordRepository.deleteAllInBatch();
        return load();
    }

    private int load() {
        List<SeedEntry> entries = parse(readLines());
        List<SlangWord> batch = new ArrayList<>(BATCH_SIZE);
        int saved = 0;
        for (SeedEntry entry : entries) {
            SlangWord word = new SlangWord(entry.word());
            word.replaceDefinitions(entry.definitions());
            batch.add(word);
            if (batch.size() == BATCH_SIZE) {
                slangWordRepository.saveAll(batch);
                saved += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            slangWordRepository.saveAll(batch);
            saved += batch.size();
        }
        log.info("Seeded {} slang words from {}", saved, seedLocation);
        return saved;
    }

    private List<String> readLines() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resourceLoader.getResource(seedLocation).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read seed file " + seedLocation, ex);
        }
    }
}
