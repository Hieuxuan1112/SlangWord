package com.slangword.repository;

import com.slangword.domain.SlangWord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlangWordRepository extends JpaRepository<SlangWord, Long> {

    /*
     * Written out rather than derived from the method name. Spring Data's
     * `IgnoreCase` keyword generates `upper(word) = upper(?)`, which cannot use
     * an index on `lower(word)` — every lookup was a sequential scan of the whole
     * table. Spelling the query out keeps it aligned with idx_slang_word_word_lower.
     */
    @EntityGraph(attributePaths = "definitions")
    @Query("select s from SlangWord s where lower(s.word) = lower(:word)")
    Optional<SlangWord> findByWordIgnoreCase(@Param("word") String word);

    /*
     * Paging is done over ids, never over entities that fetch their definitions.
     * Combining a collection fetch with firstResult/maxResults makes Hibernate load
     * every matching row and paginate in memory (warning HHH90003004): a search
     * matching a million words would read all of them to return twenty.
     * Each search is therefore two queries — one page of ids, then one fetch.
     */

    @Query("select s.id from SlangWord s")
    Page<Long> findAllIds(Pageable pageable);

    @Query("select s.id from SlangWord s where lower(s.word) like lower(concat('%', :fragment, '%'))")
    Page<Long> findIdsByWordFragment(@Param("fragment") String fragment, Pageable pageable);

    @Query("""
            select distinct s.id from SlangWord s
            join s.definitions d
            where lower(d.text) like lower(concat('%', :fragment, '%'))
            """)
    Page<Long> findIdsByDefinitionFragment(@Param("fragment") String fragment, Pageable pageable);

    /** Returns entities in arbitrary order; callers restore the order of the id page. */
    @EntityGraph(attributePaths = "definitions")
    List<SlangWord> findByIdIn(Collection<Long> ids);

    @Query(value = "select * from slang_word order by random() limit 1", nativeQuery = true)
    Optional<SlangWord> findRandom();

    @Query(value = """
            select s.word from slang_word s
            where s.id <> :excludeId
            order by random() limit :count
            """, nativeQuery = true)
    java.util.List<String> findRandomWordsExcluding(@Param("excludeId") Long excludeId, @Param("count") int count);
}
