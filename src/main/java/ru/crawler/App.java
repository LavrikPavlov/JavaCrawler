package ru.crawler;


import ru.crawler.classes.Crawler;

import java.util.ArrayList;
import java.util.List;

public class App
{

    public static void main(String[] args) {
        Crawler crawler = new Crawler("Crawler.db");
        crawler.initDB();
        List<String> urlList = new ArrayList<>();
        urlList.add("https://lenta.ru");
        crawler.crawl(urlList, 2);
    }

}
