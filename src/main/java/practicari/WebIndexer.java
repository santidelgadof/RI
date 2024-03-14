    package practicari;

    import java.io.BufferedReader;
    import java.io.IOException;
    import java.net.URI;
    import java.net.URISyntaxException;
    import java.net.http.HttpClient;
    import java.net.http.HttpRequest;
    import java.net.http.HttpResponse;
    import java.nio.file.Files;
    import java.nio.file.Path;
    import java.nio.file.Paths;
    import java.util.ArrayList;
    import java.util.List;
    
    public class WebIndexer {
    
        private static final String INDEX_DIR = "Index/";
        private static final String DOCS_DIR = "Doc/";
    
        public static void main(String[] args) {
            if (args.length < 4) {
                System.out.println("Usage: java WebIndexer -index INDEX_PATH -docs DOCS_PATH [-create] [-numThreads int] [-h] [-p] [-titleTermVectors] [-bodyTermVectors] [-analyzer Analyzer]");
                return;
            }
    
            String indexPath = INDEX_DIR;
            String docsPath = DOCS_DIR;
            boolean create = false;
            int numThreads = Runtime.getRuntime().availableProcessors();
            boolean showThreadInfo = false;
            boolean showAppInfo = false;
            boolean titleTermVectors = false;
            boolean bodyTermVectors = false;
            String analyzer = "StandardAnalyzer";
    
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-index":
                        indexPath = args[++i];
                        break;
                    case "-docs":
                        docsPath = args[++i];
                        break;
                    case "-create":
                        create = true;
                        break;
                    case "-numThreads":
                        numThreads = tryParse(args[++i], "numThreads parameter is not a valid integer.");
                        break;
                    case "-h":
                        showThreadInfo = true;
                        break;
                    case "-p":
                        showAppInfo = true;
                        break;
                    case "-titleTermVectors":
                        titleTermVectors = true;
                        break;
                    case "-bodyTermVectors":
                        bodyTermVectors = true;
                        break;
                    case "-analyzer":
                        analyzer = args[++i];
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown parameter: " + args[i]);
                }
            }
    
            Path indexDir = Paths.get(indexPath);
            Path docsDir = Paths.get(docsPath);
    
            if (!Files.exists(indexDir) || !Files.isDirectory(indexDir)) {
                throw new IllegalArgumentException("Index directory does not exist: " + indexPath);
            }
            if (!Files.exists(docsDir) || !Files.isDirectory(docsDir)) {
                throw new IllegalArgumentException("Docs directory does not exist: " + docsPath);
            }
    
            try {
                String urlFilePath = "src/test/resources/urls/urls.txt";
                List<String> urls = readUrlsFromFile(Paths.get(urlFilePath));
                processUrls(urls);
            } catch (IOException e) {
                System.err.println("Error reading URLs file: " + e.getMessage());
            }
        }
    
        private static int tryParse(String text, String errorMessage) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(errorMessage);
            }
        }
    
        private static List<String> readUrlsFromFile(Path filePath) throws IOException {
            List<String> urls = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(filePath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("http://") || line.startsWith("https://")) {
                        urls.add(line.trim());
                    }
                }
            }
            return urls;
        }
    
        private static void processUrls(List<String> urls) {
            HttpClient httpClient = HttpClient.newHttpClient();
    
            for (String url : urls) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(new URI(url))
                            .build();
    
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    
                    int statusCode = response.statusCode();
                    if (statusCode == 200) {
                        String responseBody = response.body();
                        String fileName = url.substring(url.lastIndexOf('/') + 1).replaceAll("^(http://|https://)", "");
                        Path locFilePath = Paths.get(DOCS_DIR + fileName + ".loc");
                        Files.writeString(locFilePath, responseBody);
                        System.out.println("Page " + url + " downloaded and saved as " + fileName + ".loc");
                    } else {
                        System.err.println("Failed to download page " + url + ". Status code: " + statusCode);
                    }
                } catch (URISyntaxException | IOException | InterruptedException e) {
                    System.err.println("Error processing URL " + url + ": " + e.getMessage());
                }
            }
        }
    }
    