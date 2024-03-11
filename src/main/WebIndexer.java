import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class WebIndexer {

    public static void main(String[] args) throws Exception {
        // Configurar pool de threads
        int numThreads = Runtime.getRuntime().availableProcessors(); // Puedes ajustar este valor según tus necesidades
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Leer archivos .url y procesarlos en hilos
        try (BufferedReader reader = new BufferedReader(new FileReader("src/test/resources/urls/archivo1.url"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String url = line.trim();
                executor.submit(() -> processURL(url));
            }
        }

        // Apagar el pool de threads después de terminar
        executor.shutdown();
    }

    private static void processURL(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Procesar la página
                String html = response.body();
                Document doc = Jsoup.parse(html);

                // Extraer título y cuerpo
                String title = doc.title();
                String body = extractBody(doc);

                // Guardar página y contenido en archivos
                savePage(url, html);
                saveContent(url, title, body);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String extractBody(Document doc) {
        StringBuilder body = new StringBuilder();
        Elements elements = doc.body().select("*");
        for (Element element : elements) {
            if (!element.tagName().equals("script")) {
                body.append(element.text()).append("\n");
            }
        }
        return body.toString();
    }

    private static void savePage(String url, String html) throws Exception {
        String fileName = url.replaceFirst("https?://", "").replaceAll("/", "_").concat(".loc");
        Path path = Paths.get(fileName);
        Files.writeString(path, html);
    }

    private static void saveContent(String url, String title, String body) throws Exception {
        String fileName = url.replaceFirst("https?://", "").replaceAll("/", "_").concat(".loc.notags");
        Path path = Paths.get(fileName);
        String content = title + "\n" + body;
        Files.writeString(path, content);
    }
}
