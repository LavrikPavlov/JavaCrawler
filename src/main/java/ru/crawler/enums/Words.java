package ru.crawler.enums;

import java.util.List;

public enum Words {
    В("в"),
    На("на"),
    Под("под"),
    За("за"),
    Перед("перед"),
    Между("между"),
    Над("над"),
    По("по"),
    С("с"),
    У("у"),
    От("от"),
    К("к"),
    О("о"),
    РядомС("рядом с"),
    Вблизи("вблизи"),
    Внутри("внутри"),
    Снаружи("снаружи"),
    Вокруг("вокруг"),
    Вдоль("вдоль"),
    Сквозь("сквозь");

    private final String value;

    Words(String value) {
        this.value = value;
    }


    public String getValue() {
        return value;
    }
}
