import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class WebCrawler {
    public static final int THREAD_COUNT = 8;
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
    private final Phaser phaser = new Phaser(1);
    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Integer> wordCounts = new ConcurrentHashMap<>();
    public void crawl(String[] urls) {
        try {
            for (String url : urls) {
                phaser.register();
                executorService.submit(() -> crawlChild(url, executorService));
            }
            phaser.arriveAndAwaitAdvance();

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

    private void crawlChild(String url, ExecutorService executorService) {
        if (visited.contains(url)) {
            return;
        }
        System.out.println(url);
        visited.add(url);
        try {
            URL uri = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) uri.openConnection();
            conn.setRequestMethod("GET");
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
                    executorService.submit(() -> crawlChild(childUrl, executorService));
                }
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
        ArrayList<String> urls = new ArrayList<>();
        for (Element link : links) {
            String href = link.attr("abs:href");
            if (href.startsWith("http")) {
                urls.add(href);
            }
        }
        return urls;
    }
}



