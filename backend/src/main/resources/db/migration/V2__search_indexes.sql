-- Substring search (`... LIKE '%fragment%'`) cannot use a B-tree index: a B-tree
-- is ordered by prefix, and a leading wildcard has no prefix to seek on. Every
-- search was therefore a sequential scan of the whole table.
--
-- pg_trgm splits each value into three-character sequences and indexes those, so
-- a fragment can be looked up by the trigrams it contains. A GIN index over those
-- trigrams turns the leading-wildcard search into an index lookup.
--
-- Cost of this choice: GIN indexes are larger and slower to update than B-trees.
-- That trade is right here because the dictionary is read constantly and written
-- rarely — the opposite workload would not justify it.
create extension if not exists pg_trgm;

create index idx_slang_word_word_trgm
    on slang_word using gin (lower(word) gin_trgm_ops);

create index idx_definition_text_trgm
    on definition using gin (lower(text) gin_trgm_ops);
