package ru.crawler.models;

public class WordLocation {
    private Word wordId;
    private Urls urlId;
    private int location;

    public WordLocation(Word wordId, Urls urlId, int location) {
        this.wordId = wordId;
        this.urlId = urlId;
        this.location = location;
    }

    public Word getWordId() {
        return wordId;
    }

    public void setWordId(Word wordId) {
        this.wordId = wordId;
    }

    public Urls getUrlId() {
        return urlId;
    }

    public void setUrlId(Urls urlId) {
        this.urlId = urlId;
    }

    public int getLocation() {
        return location;
    }

    public void setLocation(int location) {
        this.location = location;
    }
}
