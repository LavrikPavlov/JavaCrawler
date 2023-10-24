package ru.crawler.models;

public class LinkWord {

    private int wordId;
    private int linkId;

    public LinkWord(int wordId, int linkId) {
        this.wordId = wordId;
        this.linkId = linkId;
    }

    public int getWordId() {
        return wordId;
    }

    public void setWordId(int wordId) {
        this.wordId = wordId;
    }

    public int getLinkId() {
        return linkId;
    }

    public void setLinkId(int linkId) {
        this.linkId = linkId;
    }
}
