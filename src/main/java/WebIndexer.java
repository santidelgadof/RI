package main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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

public class WebIndexer {

    private static final int NUM_THREADS = 5; // Número de hilos a utilizar
    private static final ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

    public static void main(String[] args) {
        // Directorio que contiene los archivos .url
        String urlDirectory = "src/test/resources/urls";

        // Procesar cada archivo .url en un hilo separado
        try {
            Files.list(Paths.get(urlDirectory))
                    .filter(path -> path.toString().endsWith(".url"))
                    .forEach(WebIndexer::processUrlFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Apagar el ExecutorService después de que todos los hilos hayan completado
        executor.shutdown();
    }

    private static void processUrlFile(Path filePath) {
        executor.execute(() -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processUrl(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static void processUrl(String url) {
        try {
            URI uri = new URI(url);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Solo procesar si el código de respuesta es 200 OK
            if (response.statusCode() == 200) {
                String html = response.body();
                Document doc = Jsoup.parse(html);

                // Aquí puedes agregar la lógica para extraer el título y el cuerpo del documento
                // y almacenarlos en archivos .loc.notags y luego indexarlos con Lucene.
            } else {
                System.out.println("Error al procesar URL: " + url + ". Código de respuesta: " + response.statusCode());
            }
        } catch (IOException | InterruptedException | URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
