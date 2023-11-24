package ru.range.clases;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;

public class Searcher {
    private Connection con;

    public Searcher(String dbFileName) throws SQLException {
        con = DriverManager.getConnection("jdbc:sqlite:" + dbFileName);
    }

    public void dbcommit() throws SQLException {
        con.commit();
    }

    public List<Integer> getWordsIds(String queryString) throws Exception {
        String[] queryWordsList = queryString.split(" ");
        List<Integer> rowidList = new ArrayList<>();

        for (String word : queryWordsList) {
            String sql = "SELECT id FROM wordlist WHERE word = ? LIMIT 1;";
            try (PreparedStatement stmt = con.prepareStatement(sql)) {
                stmt.setString(1, word);
                try (ResultSet resultSet = stmt.executeQuery()) {
                    if (resultSet.next()) {
                        int wordRowId = resultSet.getInt(1);
                        rowidList.add(wordRowId);
                        System.out.println("Ответ: " + word + " " + wordRowId);
                    } else {
                        throw new Exception("Одно из слов поискового запроса не найдено: " + word);
                    }
                }
            }
        }

        return rowidList;
    }

    public List<List<Integer>> getMatchRows(String queryString) throws Exception {
        String[] wordsList = queryString.split(" ");

        List<Integer> wordsIdList = getWordsIds(queryString);

        StringBuilder sqlFullQuery = new StringBuilder();

        List<String> sqlpartName = new ArrayList<>();
        List<String> sqlpartJoin = new ArrayList<>();
        List<String> sqlpartCondition = new ArrayList<>();

        for (int wordIndex = 0; wordIndex < wordsList.length; wordIndex++) {
            int wordID = wordsIdList.get(wordIndex);

            if (wordIndex == 0) {
                sqlpartName.add("w0.URLId");
                sqlpartName.add(" , w0.location w0_location");
                sqlpartCondition.add("WHERE w0.wordId=" + wordID);
            } else {
                if (wordsList.length >= 2) {
                    sqlpartName.add(" , w" + wordIndex + ".location w" + wordIndex + "_location");
                    sqlpartJoin.add("INNER JOIN wordlocation w" + wordIndex + " ON w0.URLId=w" + wordIndex + ".URLId");
                    sqlpartCondition.add("AND w" + wordIndex + ".wordId=" + wordID);
                }
            }
        }

        sqlFullQuery.append("SELECT ");

        for (String sqlpart : sqlpartName) {
            sqlFullQuery.append("\n");
            sqlFullQuery.append(sqlpart);
        }

        sqlFullQuery.append("\n");
        sqlFullQuery.append("FROM wordlocation w0 ");

        for (String sqlpart : sqlpartJoin) {
            sqlFullQuery.append("\n");
            sqlFullQuery.append(sqlpart);
        }

        for (String sqlpart : sqlpartCondition) {
            sqlFullQuery.append("\n");
            sqlFullQuery.append(sqlpart);
        }

        try (Statement stmt = con.createStatement();
             ResultSet resultSet = stmt.executeQuery(sqlFullQuery.toString())) {
            List<List<Integer>> rows = new ArrayList<>();

            while (resultSet.next()) {
                List<Integer> row = new ArrayList<>();
                for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                    row.add(resultSet.getInt(i));
                }
                rows.add(row);
            }

            System.out.println("Количество уникальных URL, содержащих слова поискового запроса:");
            List<Integer> distinctUrls = new ArrayList<>();
            for (List<Integer> row : rows) {
                distinctUrls.add(row.get(0));
            }
            System.out.println(distinctUrls.size());

            return rows;
        }
    }

    public Map<Integer, Double> distanceScore(List<List<Integer>> rowsLoc) {
        Map<Integer, Double> mindistanceDict = new HashMap<>();

        if (rowsLoc.get(0).size() == 2) {
            for (List<Integer> location : rowsLoc) {
                mindistanceDict.put(location.get(0), 1.0);
            }
            return mindistanceDict;
        } else if (rowsLoc.get(0).size() > 2) {
            for (List<Integer> location : rowsLoc) {
                mindistanceDict.put(location.get(0), 1000000.0);
            }
            for (List<Integer> location : rowsLoc) {
                double distSum = 0;
                for (int i = 1; i < location.size() - 1; i++) {
                    int dist = Math.abs(location.get(i) - location.get(i + 1));
                    distSum += dist;
                }
                if (distSum < mindistanceDict.get(location.get(0))) {
                    mindistanceDict.put(location.get(0), distSum);
                }
            }
        }

        return normalizeScores(mindistanceDict, true);
    }

    public Map<Integer, Double> normalizeScores(Map<Integer, Double> scores, boolean smallIsBetter) {
        Map<Integer, Double> normalizedScores = new HashMap<>();
        double vsmall = 0.00001;
        double minscore = Collections.min(scores.values());
        double maxscore = Collections.max(scores.values());

        for (Entry<Integer, Double> entry : scores.entrySet()) {
            int key = entry.getKey();
            double val = entry.getValue();
            double normalizedValue;
            if (smallIsBetter) {
                normalizedValue = minscore / Math.max(vsmall, val);
            } else {
                normalizedValue = val / maxscore;
            }
            normalizedScores.put(key, normalizedValue);
        }

        return normalizedScores;
    }

    public Map<Integer, Double> pagerankScore(List<List<Integer>> rows) throws SQLException {
        Map<Integer, Double> pagerank = new HashMap<>();

        for (List<Integer> row : rows) {
            String sql = "SELECT score FROM pageRank WHERE urlId=" + row.get(0);
            try (ResultSet resultSet = con.createStatement().executeQuery(sql)) {
                if (resultSet.next()) {
                    double score = resultSet.getDouble(1);
                    pagerank.put(row.get(0), score);
                }
            }
        }

        double maxrank = pagerank.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        for (Entry<Integer, Double> entry : pagerank.entrySet()) {
            entry.setValue(entry.getValue() / maxrank);
        }

        List<Entry<Integer, Double>> normalizedscores = new ArrayList<>(pagerank.entrySet());
        normalizedscores.sort((entry1, entry2) -> Double.compare(entry2.getValue(), entry1.getValue()));

        System.out.println("M2. РАНЖИРОВАНИЕ НА ОСНОВЕ ВНЕШНИХ ССЫЛОК (АЛГОРИТМ PAGERANK)");
        System.out.println("score urlId getUrlName");

        for (int i = 0; i < Math.min(normalizedscores.size(), 10); i++) {
            Entry<Integer, Double> entry = normalizedscores.get(i);
            int urlId = entry.getKey();
            double score = entry.getValue();
            String urlName = getUrlName(urlId);

            System.out.printf("%.2f\t%5d\t%s%n", score, urlId, urlName);
        }

        return pagerank;
    }

    public void getSortedList(String queryString) {
        try {
            List<List<Integer>> rowsLoc = getMatchRows(queryString);
            Map<Integer, Double> m1Scores = distanceScore(rowsLoc);
            List<Map.Entry<Integer, Double>> rankedScoresList = new ArrayList<>(m1Scores.entrySet());

            rankedScoresList.sort((entry1, entry2) -> Double.compare(entry2.getValue(), entry1.getValue()));

            System.out.println("M1. РАНЖИРОВАНИЕ ПО СОДЕРЖИМОМУ (РАССТОЯНИЕ МЕЖДУ СЛОВАМИ)");
            System.out.println("score\turlId\tgetUrlName");
            int count = 0;
            for (Map.Entry<Integer, Double> entry : rankedScoresList) {
                int urlId = entry.getKey();
                double score = entry.getValue();
                System.out.printf("%.2f\t%5d\t%s%n", score, urlId, getUrlName(urlId));
                count++;
                if (count >= 10) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void calculatePageRank(int iterations) throws SQLException {
        con.setAutoCommit(false); // Выключаем автоматическое подтверждение транзакций
        try {

            con.createStatement().execute("DROP TABLE IF EXISTS pageRank");
            con.createStatement().execute("CREATE TABLE IF NOT EXISTS pageRank(" +
                    "rowId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "urlId INTEGER, " +
                    "score REAL);");

            con.createStatement().execute("DROP INDEX IF EXISTS wordidx;");
            con.createStatement().execute("DROP INDEX IF EXISTS urlidx;");
            con.createStatement().execute("DROP INDEX IF EXISTS wordurlidx;");
            con.createStatement().execute("DROP INDEX IF EXISTS urltoidx;");
            con.createStatement().execute("DROP INDEX IF EXISTS urlfromidx;");

            // Остальные операторы DROP INDEX и CREATE INDEX по аналогии

            if (checkIfIndexExists(con, "rankurlididx")) {
                con.createStatement().execute("REINDEX rankurlididx;");
            } else {
                // Индекс rankurlididx отсутствует, создаем его
                con.createStatement().execute("CREATE INDEX IF NOT EXISTS rankurlididx ON pageRank(urlId);");
            }

            con.createStatement().execute("INSERT INTO pageRank (urlId, score) SELECT id, 1 FROM URLList");
            dbcommit();

            for (int i = 0; i < iterations; i++) {
                ResultSet urlListResultSet = con.createStatement().executeQuery("SELECT id FROM URLList");
                while (urlListResultSet.next()) {
                    int urlId = urlListResultSet.getInt("id");
                    double pr = 0.15;

                    ResultSet fromUrlResultSet = con.createStatement()
                            .executeQuery("SELECT DISTINCT fromURLId FROM linkBetweenURL WHERE toURLId=" + urlId);
                    while (fromUrlResultSet.next()) {
                        int fromUrlId = fromUrlResultSet.getInt("fromURLId");
                        ResultSet linkingPrResultSet = con.createStatement()
                                .executeQuery("SELECT score FROM pageRank WHERE urlId=" + fromUrlId);
                        ResultSet linkingCountResultSet = con.createStatement()
                                .executeQuery("SELECT count(*) FROM linkBetweenURL WHERE fromURLId=" + fromUrlId);

                        if (linkingPrResultSet.next() && linkingCountResultSet.next()) {
                            double linkingPr = linkingPrResultSet.getDouble("score");
                            int linkingCount = linkingCountResultSet.getInt(1);
                            pr += 0.85 * (linkingPr / linkingCount);
                        }
                    }
                    con.createStatement().execute("UPDATE pageRank SET score=" + pr + " WHERE urlId=" + urlId);
                }
            }
                con.commit(); // Явно подтверждаем изменения
            } catch(SQLException e){
                con.rollback(); // Откатываем транзакцию в случае ошибки
                System.out.println("Ошибка: " + e);
            } finally{
                con.setAutoCommit(true); // Включаем автоматическое подтверждение обратно
                System.out.println("Отработал");
            }
    }



    public List<List<String>> getWordList(List<Integer> markedUrl) throws SQLException {
        List<List<String>> wordList = new ArrayList<>();
        for (int url : markedUrl) {
            String sql = "SELECT word FROM wordlist INNER JOIN wordlocation ON wordlist.id = wordlocation.wordId WHERE wordlocation.URLId = " + url;
            try (Statement stmt = con.createStatement();
                 ResultSet resultSet = stmt.executeQuery(sql)) {
                List<String> words = new ArrayList<>();
                while (resultSet.next()) {
                    words.add(resultSet.getString(1));
                }
                wordList.add(words);
            }
        }
        return wordList;
    }

    public boolean checkIfIndexExists(Connection con, String indexName) throws SQLException {
        String query = "PRAGMA index_list('pageRank');";
        Statement stmt = con.createStatement();
        ResultSet resultSet = stmt.executeQuery(query);

        while (resultSet.next()) {
            String existingIndexName = resultSet.getString("name");
            if (existingIndexName.equals(indexName)) {
                return true;
            }
        }

        return false;
    }

    public String getMarkedHTML(List<List<String>> wordList, List<String> queryList, List<Integer> markedUrl) {
        StringBuilder resultHTML = new StringBuilder("<html>");
        int countUrl = 1;

        for (List<String> words : wordList) {
            resultHTML.append("<body>");
            resultHTML.append("<h3>");
            resultHTML.append("Страница № ").append(countUrl).append(" ID URL: ")
                    .append(markedUrl.get(countUrl-1));
            resultHTML.append("</h3>");
            resultHTML.append("<p>");

            for (String word : words) {
                String lowerCaseWord = word.toLowerCase(); // Приводим к нижнему регистру

                if (queryList.contains(lowerCaseWord)) {
                    if(queryList.get(0).equals(lowerCaseWord))
                        resultHTML.append("<span style=\"color:red\">");
                    else
                        resultHTML.append("<span style=\"color:blue\">");

                    resultHTML.append(word.toUpperCase());
                    resultHTML.append("</span>");
                } else {
                    resultHTML.append(word);
                }
                resultHTML.append(" ");
            }

            resultHTML.append("</p>");
            resultHTML.append("</body>");
            resultHTML.append("<br>");
            countUrl++;
        }

        resultHTML.append("</html>");
        return resultHTML.toString();
    }

    public void createMarkedHtmlFile(String markedHTMLFilename, List<Integer> markedUrl, List<String> testQueryList) throws IOException {
        for (int i = 0; i < testQueryList.size(); i++) {
            testQueryList.set(i, testQueryList.get(i).toLowerCase());
        }
        System.out.println(testQueryList);
        List<List<String>> wordList = null;
        try {
            wordList = getWordList(markedUrl);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        String htmlCode = getMarkedHTML(wordList, testQueryList, markedUrl);

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(markedHTMLFilename, Boolean.parseBoolean("utf-8")));
            writer.write(htmlCode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getUrlName(int id) throws SQLException {
        String sql = "SELECT url FROM urllist WHERE id = " + id;
        try (Statement stmt = con.createStatement();
             ResultSet resultSet = stmt.executeQuery(sql)) {
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
        }
        return "";
    }
}
