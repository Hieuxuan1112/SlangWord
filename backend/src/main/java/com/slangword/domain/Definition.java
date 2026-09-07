package com.slangword.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "definition")
public class Definition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slang_word_id", nullable = false)
    private SlangWord slangWord;

    @Column(nullable = false, length = 2000)
    private String text;

    protected Definition() {
    }

    public Definition(SlangWord slangWord, String text) {
        this.slangWord = slangWord;
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public SlangWord getSlangWord() {
        return slangWord;
    }

    public String getText() {
        return text;
    }
}
