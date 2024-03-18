    package practicari;

    import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
    import java.net.URI;
    import java.net.URISyntaxException;
    import java.net.http.HttpClient;
    import java.net.http.HttpRequest;
    import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
    import java.nio.file.Path;
    import java.nio.file.Paths;
    import java.util.ArrayList;
    import java.util.List;
    
    public class WebIndexer {
    
        private static final String INDEX_DIR = "Index/";
        private static final String DOCS_DIR = "Doc/";
        private static final int HTTP_OK = 200;
        public static final String usage = "Usage: java WebIndexer -index INDEX_PATH -docs DOCS_PATH [-create] [-numThreads int] [-h] [-p] [-titleTermVectors] [-bodyTermVectors] [-analyzer Analyzer]";

        public static void main(String[] args) {
            if (args.length < 4) {
                System.out.println(usage);
                return;
            }
    
            String indexPath = INDEX_DIR;
            String docsPath = DOCS_DIR;
            boolean create;
            int numThreads = Runtime.getRuntime().availableProcessors();
            boolean showThreadInfo;
            boolean showAppInfo;
            boolean titleTermVectors;
            boolean bodyTermVectors;
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
                String urlFilePath = "src/test/resources/urls/sites.url";
                List<String> urls = readUrlsFromFile(Paths.get(urlFilePath));
                processUrls(urls, docsPath);
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
    
    
        static void processUrls(List<String> urls, String docsPath) {
            HttpClient httpClient = HttpClient.newHttpClient();

            for (String url : urls) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(new URI(url))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    int statusCode = response.statusCode();
                    if (statusCode == HTTP_OK) {
                        saveResponseToFile(response, url, docsPath);
                        System.out.println("Page " + url + " downloaded and saved.");
                    } else {
                        System.err.println("Failed to download page " + url + ". Status code: " + statusCode);
                    }
                } catch (URISyntaxException e) {
                    System.err.println("Invalid URL syntax: " + url);
                } catch (IOException e) {
                    System.err.println("Error reading or writing file: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Thread interrupted while waiting for response: " + e.getMessage());
                }
            }
        }

        private static void saveResponseToFile(HttpResponse<String> response, String url, String docsPath) throws IOException {
            String responseBody = response.body();
            //String fileName = url.substring(url.lastIndexOf('/') + 1).replaceAll("http://|https://", "");
            String fileName;
            if(url.charAt(url.length() - 1) == '/')
                fileName = url.substring(url.indexOf("://") + 3, url.length() - 1);
            else 
                fileName = url.substring(url.indexOf("://") + 3);
            Path locFilePath = Paths.get(docsPath + FileSystems.getDefault().getSeparator() + fileName + ".loc");
            Files.writeString(locFilePath, responseBody);
        }

    }
    