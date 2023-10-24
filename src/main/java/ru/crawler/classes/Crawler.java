package ru.crawler.classes;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import ru.crawler.enums.Words;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.*;

public class Crawler {
    private final Connection conn;

    public Crawler(String dbFileName) {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFileName);
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка в подключении БД", e);
        }
    }

    public void close() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка в закрытие БД", e);
        }
    }

    public void initDB() {
        // Создание таблиц в базе данных
        createTables();
    }

    private void createTables() {
        Statement statement;
        try {
            statement = conn.createStatement();
        System.out.println("Создание URLList");
        statement.execute("CREATE TABLE IF NOT EXISTS urllist (id INTEGER PRIMARY KEY, url TEXT)");

        System.out.println("Создание WordList");
        statement.execute("CREATE TABLE IF NOT EXISTS wordlist (id INTEGER PRIMARY KEY, word TEXT, is_filtered INTEGER)");

        System.out.println("Создание WordLocation");
        statement.execute("CREATE TABLE IF NOT EXISTS wordlocation (id INTEGER PRIMARY KEY, wordId INTEGER, URLId INTEGER, location INTEGER, FOREIGN KEY (wordId) REFERENCES wordlist(id), FOREIGN KEY (URLId) REFERENCES urllist(id))");

        System.out.println("Создание Link");
        statement.execute("CREATE TABLE IF NOT EXISTS linkBetweenURL (id INTEGER PRIMARY KEY, fromURLId INTEGER, toURLId INTEGER, FOREIGN KEY (fromURLId) REFERENCES urllist(id), FOREIGN KEY (toURLId) REFERENCES urllist(id))");

        System.out.println("Создание LinkWords");
        statement.execute("CREATE TABLE IF NOT EXISTS linkwords (id INTEGER PRIMARY KEY, wordId INTEGER, linkId INTEGER, FOREIGN KEY (wordId) REFERENCES wordlist(id), FOREIGN KEY (linkId) REFERENCES linkBetweenURL(id))");
        } catch (SQLException e) {
            System.out.println("Ошибка в создании таблиц");
        }
    }

    public void crawl(List<String> startUrl, int maxDepth) {
        // Начать процесс сбора данных, начиная с заданного списка страниц
        // и выполнять поиск в ширину до заданной глубины
        Set<String> visitedUrls = new HashSet<>();
        Queue<String> queue = new LinkedList<>(startUrl);

        try {
            for (int depth = 0; depth <= maxDepth; depth++) {
                Queue<String> nextLevel = new LinkedList<>();
                System.out.println("Следующий уровень");

                while (!queue.isEmpty()) {
                    String url = queue.poll();

                    if (!visitedUrls.contains(url)) {
                        visitedUrls.add(url);
                        System.out.println(url);
                        try {
                            String html = downloadHtml(url);
                            if (html != null) {
                                Document doc = Jsoup.parse(html);
                                Elements links = doc.select("a");
                                addIndex(doc, url);
                                System.out.println("addIndex забрал: " + url);
                                for (Element link : links) {
                                    String href = link.attr("href");
                                    if (isValidHref(href)) {
                                        String absoluteUrl = makeAbsoluteUrl(url, href);
                                        if(isIndexedUrl(absoluteUrl)) {
                                            System.out.println("Новые: " + absoluteUrl);
                                            nextLevel.add(absoluteUrl);
                                            addLinkRef(url, absoluteUrl);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                queue.addAll(nextLevel);
            }
        } finally {
            close();
        }
    }



    private String downloadHtml(String url) {
        try {
            URL urlObj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("GET");

            // Установите таймаут для соединения
            connection.setConnectTimeout(5000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    content.append(line).append('\n');
                }

                reader.close();
                connection.disconnect();

                return content.toString();
            } else {
                System.err.println("Ошибка при получении HTML-контента. Код ответа: " + responseCode);
            }
        } catch (Exception e) {
            System.err.println("Ошибка при загрузке HTML-контента: " + e.getMessage());
        }

        return null;
    }

    private String getTextOnly(Document doc) {
        // Извлеките только текстовое содержимое из элементов документа
        // Возвращайте текст в виде строки
        return doc.text();
    }

    private String[] separateWords(String text) {
        // Разделите текст на слова, например, с использованием пробелов в качестве разделителей
        // Верните массив слов
        return text.split("\\P{L}+");
    }

    private void addIndex(Document doc, String url) {
        if (isIndexed(url)) {
            return;
        }

        // Получаем список слов из индексируемой страницы
        String text = getTextOnly(doc);
        List<String> words = List.of(separateWords(text));


        // Получаем идентификатор URL
        int urlId = getEntryId("urllist", "url", url, true);
        int i = 0;
        // Связываем каждое слово с этим URL
        for (String word : words) {
            if(isFiltred(word)){
                continue;
            }
            // 1) если слово не входит в список игнорируемых слов ignoreWords
            // 2) то добавляем запись в таблицу wordlist
            int wordId = getEntryId("wordlist", "word", word, true);
            // 3) добавляем запись в wordlocation
            if (wordId != -1) {
                addWordLocation(wordId, urlId, i);
            }
            i++;

        }
    }

    public boolean isIndexed(String url) {
        // Проверяем, есть ли запись в таблице urllist
        int urlId = getEntryId("urllist", "url", url, false);
        if (urlId != -1) {
            // Проверяем, что есть слова в таблице wordlocation, связанные с этим URL
            int wordCount = getWordCountForURL(urlId);
            return wordCount > 0;
        }
        return false;
    }

    public boolean isIndexedUrl(String url){
        int urlId = getEntryId("urllist", "url", url, false);
        if(url.startsWith("https://lenta.ru")){
            if (urlId == -1)
                return true;
            else
                return false;
        } else
            return false;
    }

    private boolean isValidHref(String href) {
        return href != null && !href.isEmpty() && !href.contains("#") && !href.equals("/");
    }

    private String makeAbsoluteUrl(String baseUrl, String href) {
        if (!href.startsWith("http")) {
            if (href.startsWith("/")) {
                int slashIndex = baseUrl.indexOf('/', 8); // Игнорируем "http://" или "https://"
                if (slashIndex != -1) {
                    baseUrl = baseUrl.substring(0, slashIndex);
                }
                return baseUrl + href;
            } else {
                return baseUrl + "/" + href;
            }
        }
        return href;
    }

    private void addLinkRef(String urlFrom, String urlTo) {
        try {
            // Получите идентификаторы URL для страниц, с которых и на которые есть ссылка
            int fromURLId = getEntryId("urllist", "url", urlFrom, true);
            int toURLId = getEntryId("urllist", "url", urlTo, true);

            // Вставьте информацию о ссылке в таблицу linkBetweenURL
            PreparedStatement insertLinkStatement = conn.prepareStatement("INSERT INTO linkBetweenURL (fromURLId, toURLId) VALUES (?, ?)");
            insertLinkStatement.setInt(1, fromURLId);
            insertLinkStatement.setInt(2, toURLId);
            insertLinkStatement.executeUpdate();
            int linkWord = insertLinkStatement.getGeneratedKeys().getInt(1);

            addIndex(Jsoup.connect(urlTo).get(), urlTo);

            List<Integer> wordIds = getWordIdsForURL(toURLId);
            for (int wordId : wordIds) {
                addLinkWord(wordId, linkWord);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int getEntryId(String table, String field, String value, boolean createNew) {
        try {
            PreparedStatement stmt = conn.prepareStatement("SELECT id FROM " + table + " WHERE " + field + " = ?");
            stmt.setString(1, value);
            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            } else if (createNew) {
                // Если записи нет и createNew равно true, то создаем новую запись
                PreparedStatement insertStatement = conn.prepareStatement("INSERT INTO " + table + " (" + field + ") VALUES (?)");
                insertStatement.setString(1, value);
                insertStatement.executeUpdate();
                return getEntryId(table, field, value, false);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private int getWordCountForURL(int urlId) {
        try {
            PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(DISTINCT wordId) FROM wordlocation WHERE URLId = ?");
            stmt.setInt(1, urlId);
            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private List<Integer> getWordIdsForURL(int urlId) {
        List<Integer> wordIds = new ArrayList<>();

        try {
            PreparedStatement stmt = conn.prepareStatement("SELECT DISTINCT wordId FROM wordlocation WHERE URLId = ?");
            stmt.setInt(1, urlId);
            ResultSet resultSet = stmt.executeQuery();

            while (resultSet.next()) {
                int wordId = resultSet.getInt("wordId");
                wordIds.add(wordId);
            }

            return wordIds;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // В случае ошибки, вернуть пустой массив или обработать ошибку по вашему усмотрению
        return new ArrayList<>(0);
    }

    private void addWordLocation(int wordId, int urlId, int location) {
        try {
            PreparedStatement insertLocationStatement = conn.prepareStatement("INSERT INTO wordlocation (wordId, URLId, location) VALUES (?, ?, ?)");
            insertLocationStatement.setInt(1, wordId);
            insertLocationStatement.setInt(2, urlId);
            insertLocationStatement.setInt(3, location);
            insertLocationStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void addLinkWord(int wordId, int linkId) {
        try {
            // Вставьте информацию о связи между словом и ссылкой в таблицу linkwords
            PreparedStatement insertLinkWordStatement = conn.prepareStatement("INSERT INTO linkwords (wordId, linkId) VALUES (?, ?)");
            insertLinkWordStatement.setInt(1, wordId);
            insertLinkWordStatement.setInt(2, linkId);
            insertLinkWordStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isFiltred(String word){
        for (Words properties : Words.values()){
            if (properties.getValue().equals(word)){
                return true;
            }
        }
        return false;
    };

}
