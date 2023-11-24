package ru.range;

import ru.range.clases.Searcher;

import java.util.ArrayList;
import java.util.List;

public class App {
    public static void main(String[] args) {
        try {
            Searcher mySearcher = new Searcher("src/main/resources/lenta.db");
            String query = "Москва России";
            List<Integer> wordIds = mySearcher.getWordsIds(query);
            List<List<Integer>> rowsLoc = mySearcher.getMatchRows(query);
            mySearcher.getSortedList(query);
            List<Integer> markedUrl = new ArrayList<>();
            for (List<Integer> row : rowsLoc) {
                int urlId = row.get(0);
                if (!markedUrl.contains(urlId)) {
                    markedUrl.add(urlId);
                }
            }

            System.out.println(markedUrl);
            System.out.println();

            List<String> testQueryList = new ArrayList<>();
            testQueryList.add("Новости");
            testQueryList.add("России");

            String markedHTMLFilename = "getMarkedHTML.html";
            mySearcher.createMarkedHtmlFile(markedHTMLFilename, markedUrl, testQueryList);

            mySearcher.calculatePageRank(2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
