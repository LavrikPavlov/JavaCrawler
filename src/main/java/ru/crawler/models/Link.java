package ru.crawler.models;

public class Link {

    private Urls fromId;
    private Urls toId;

    public Link(Urls fromId, Urls toId) {
        this.fromId = fromId;
        this.toId = toId;
    }

    public Urls getFromId() {
        return fromId;
    }

    public void setFromId(Urls fromId) {
        this.fromId = fromId;
    }

    public Urls getToId() {
        return toId;
    }

    public void setToId(Urls toId) {
        this.toId = toId;
    }
}
