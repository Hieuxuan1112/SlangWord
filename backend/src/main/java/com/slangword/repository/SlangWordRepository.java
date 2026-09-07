package com.slangword.repository;

import com.slangword.domain.SlangWord;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlangWordRepository extends JpaRepository<SlangWord, Long> {

    @EntityGraph(attributePaths = "definitions")
    Optional<SlangWord> findByWordIgnoreCase(String word);

    boolean existsByWordIgnoreCase(String word);

    @EntityGraph(attributePaths = "definitions")
    Page<SlangWord> findByWordContainingIgnoreCase(String fragment, Pageable pageable);

    @EntityGraph(attributePaths = "definitions")
    @Query("""
            select distinct s from SlangWord s
            join s.definitions d
            where lower(d.text) like lower(concat('%', :fragment, '%'))
            """)
    Page<SlangWord> searchByDefinition(@Param("fragment") String fragment, Pageable pageable);

    @Query(value = "select * from slang_word order by random() limit 1", nativeQuery = true)
    Optional<SlangWord> findRandom();

    @Query(value = """
            select s.word from slang_word s
            where s.id <> :excludeId
            order by random() limit :count
            """, nativeQuery = true)
    java.util.List<String> findRandomWordsExcluding(@Param("excludeId") Long excludeId, @Param("count") int count);
}
