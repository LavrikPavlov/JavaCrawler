package ru.crawler.models;

public class Word {
    private String word;
    private boolean isFiltered;

    public String getWord() {
        return word;
    }

    public Word(String word, boolean isFiltered) {
        this.word = word;
        this.isFiltered = isFiltered;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public boolean isFiltered() {
        return isFiltered;
    }

    public void setFiltered(boolean filtered) {
        isFiltered = filtered;
    }
}
