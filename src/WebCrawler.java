import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class WebCrawler {
    public static final int THREAD_COUNT = 8;
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
    private final Phaser phaser = new Phaser(1);
    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, LongAdder> wordCounts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final LongAdder totalCount = new LongAdder();
    public void crawl(String[] urls) {
        scheduler.scheduleAtFixedRate(this::wordRanking, 5, 30, TimeUnit.SECONDS);
        try {
            for (String url : urls) {
                phaser.register();
                executorService.submit(() -> crawlChild(url, executorService));
            }
            phaser.arriveAndAwaitAdvance();
            phaser.arriveAndDeregister();

        } catch (RejectedExecutionException e) {
            System.out.println(e.getMessage());
        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    public void wordRanking() {
        wordCounts.entrySet().stream()
                .filter((e) -> e.getValue().longValue() >= 100)
                .sorted(Comparator.comparingLong((Map.Entry<String, LongAdder> e) -> e.getValue().longValue()))
                .map(e -> String.format("%s %d", e.getKey(), e.getValue().longValue()))
                .forEach(System.out::println);
        System.out.printf("Total words seen: %d%n", totalCount.sum());
    }

    private void crawlChild(String url, ExecutorService executorService) {
        if (!visited.add(url)) {
            return;
        }
        System.out.println(url);
        try {
            URL uri = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) uri.openConnection();
            conn.setRequestMethod("GET");
            conn.setReadTimeout(5000);
            conn.setConnectTimeout(5000);
            conn.setRequestProperty("User-Agent", "SimpleCrawler/1.0");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                String page = sb.toString();
                ArrayList<String> urls = extractURLs(page, url);
                for (String childUrl : urls) {
                    phaser.register();
                    try {
                        executorService.submit(() -> crawlChild(childUrl, executorService));
                    } catch (RejectedExecutionException e) {
                        phaser.arriveAndDeregister();
                    }
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            phaser.arriveAndDeregister();
        }

    }
    private ArrayList<String> extractURLs(String page, String baseUrl) {
        Document doc = Jsoup.parse(page, baseUrl);
        Elements links = doc.select("a[href]");
        String text = doc.text();
        for (String word : text.split("\\W+")) {
            if (!word.isEmpty()) {
                totalCount.increment();
                wordCounts.computeIfAbsent(word, k -> new LongAdder()).increment();
            }
        }
        ArrayList<String> urls = new ArrayList<>();
        for (Element link : links) {
            String href = link.attr("abs:href");
            if (href.startsWith("http")) {
                int i = href.indexOf('#');
                if (i != -1) href = href.substring(0, i);
                urls.add(href);
            }
        }
        return urls;
    }
}



