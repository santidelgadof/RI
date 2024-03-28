package practicari;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Date;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.document.LongField;

public class WebIndexer {

    private static final String INDEX_DIR = "Index/";   // TODO: quitar carpetas por defecto al acabar la práctica
    private static final String DOCS_DIR = "Doc/";
    private static final int HTTP_OK = 200;
    public static final String usage = "Usage: java WebIndexer -index INDEX_PATH -docs DOCS_PATH [-create] [-numThreads int] [-h] [-p] [-titleTermVectors] [-bodyTermVectors] [-analyzer Analyzer]";

    public static void main(String[] args) {
        /*if (args.length < 4) {
            System.out.println(usage);
            return;
        }*/

        // Guardar y validar opciones
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
        //IndexWriterConfig config;

        if (!Files.exists(indexDir) || !Files.isDirectory(indexDir)) {
            throw new IllegalArgumentException("Index directory does not exist: " + indexPath);
        }
        if (!Files.exists(docsDir) || !Files.isDirectory(docsDir)) {
            throw new IllegalArgumentException("Docs directory does not exist: " + docsPath);
        }
        if (!analyzer.equals("StandardAnalyzer") && !analyzer.equals("EnglishAnalyzer")) {
            throw new IllegalArgumentException("Invalid analyzer: " + analyzer);
        }
        /*if (analyzer.equals("StandardAnalyzer")) {
            config = new IndexWriterConfig(new StandardAnalyzer());
        } else if (analyzer.equals("EnglishAnalyzer")) {
            config = new IndexWriterConfig(new EnglishAnalyzer());  // TODO: añadir el resto de analyzers
        } else {
            throw new IllegalArgumentException("Invalid analyzer: " + analyzer);
        }*/

        // Creamos el pool de threads
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        try {
            String urlFilePath = "src/test/resources/urls/sites.url";
            List<String> urls = readUrlsFromFile(Paths.get(urlFilePath));

            for (final String url : urls) {
                final Runnable worker = new WorkerThread(url, docsPath, indexPath, analyzer);
                /*
                * Send the thread to the ThreadPool. It will be processed eventually.
                */
                executor.execute(worker);
            }

            /*
            * Close the ThreadPool; no more jobs will be accepted, but all the previously
            * submitted jobs will be processed.
            */
            executor.shutdown();

            /* Wait up to 1 hour to finish all the previously submitted jobs */
            try {
                executor.awaitTermination(1, TimeUnit.HOURS);
            } catch (final InterruptedException e) {
                e.printStackTrace();
                System.exit(-2);
            }

            System.out.println("Finished all threads");
            
        } catch (IOException e) {
            System.err.println("Error reading URLs file: " + e.getMessage());
            e.printStackTrace();
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

    public static class WorkerThread implements Runnable {

		private final String url;
		private final String docsPath;
        private final String indexPath;
        private final String analyzer;

		public WorkerThread(final String url, final String docsPath, final String indexPath, final String analyzer) {
			this.url = url;
			this.docsPath = docsPath;
            this.indexPath = indexPath;
            this.analyzer = analyzer;
		}

		/**
		 * This is the work that the current thread will do when processed by the pool.
		 */
		@Override
		public void run() {
			System.out.println(String.format("I am the thread '%s' and I am responsible for url '%s'",
					Thread.currentThread().getName(), url));

			// Aquí va el trabajo del thread (PROCESAMIENTO URL)
			processUrl(url, docsPath, indexPath, analyzer);
		}

		static void processUrl(String url, String docsPath, String indexPath, String analyzer) {
            // Timeout de 5 minutos
        	HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(5)).build(); // TODO: handle timeout exception?

			try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                int statusCode = response.statusCode();
                if (statusCode == HTTP_OK) {
                    // Crear archivo .loc
                    String responseBody = response.body();
                    String fileName;
                    if(url.charAt(url.length() - 1) == '/')
                        fileName = url.substring(url.indexOf("://") + 3, url.length() - 1);
                    else 
                        fileName = url.substring(url.indexOf("://") + 3);
                    Path locFilePath = Paths.get(docsPath + FileSystems.getDefault().getSeparator() + fileName + ".loc");
                    Files.writeString(locFilePath, responseBody);

                    // Crear archivo .loc.notags
                    try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(docsPath, fileName + ".loc.notags"))) {
                        String content = new String(Files.readAllBytes(locFilePath));
        
                        // Extraer el título y el cuerpo de la página utilizando Jsoup
                        Document doc = Jsoup.parse(content);
                        String title = doc.title();
                        String body = doc.body().text(); // Obtener el texto del cuerpo sin etiquetas HTML
                        
                        // Escribir el título y el cuerpo en el archivo .loc.notags
                        writer.write(title);
                        writer.newLine();
                        writer.write(body);

                        System.out.println("Page " + url + " downloaded and saved.");

                        indexUrl(locFilePath, docsPath, analyzer, title, body);
                        
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("Failed to download page " + url + ". Status code: " + statusCode);
                }
            } catch (URISyntaxException e) {
                System.err.println("Invalid URL syntax: " + url);
                e.printStackTrace();
            } catch (IOException e) {
                System.err.println("Error reading or writing file: " + e.getMessage());
                e.printStackTrace();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrupted while waiting for response: " + e.getMessage());
                e.printStackTrace();
            }
		}

        static void indexUrl(Path locPath, String indexPath, String analyzer, String title, String body) {
            Path locNotagsPath = Path.of(locPath.toString() + ".notags");
            IndexWriter writer = null;

            /*
            * Creates a new index if one does not exist, otherwise it opens the index and
            * documents will be appended with writer.addDocument(doc).
            */

            // index document
            try (InputStream stream = Files.newInputStream(locPath)) {
                org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();

                writer = getWriter(indexPath, analyzer);

                FileTime creationTime = (FileTime) Files.getAttribute(locPath, "creationTime");
                FileTime lastAccessTime = (FileTime) Files.getAttribute(locPath, "lastAccessTime");
                FileTime lastModifiedTime = (FileTime) Files.getAttribute(locPath, "lastModifiedTime");
                String creationTimeLucene = DateTools.dateToString(Date.from(creationTime.toInstant()), Resolution.MILLISECOND);
                String lastAccessTimeLucene = DateTools.dateToString(Date.from(lastAccessTime.toInstant()), Resolution.MILLISECOND);
                String lastModifiedTimeLucene = DateTools.dateToString(Date.from(lastModifiedTime.toInstant()), Resolution.MILLISECOND);

                doc.add(new KeywordField("path", locPath.toString(), Field.Store.YES));
                doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))));
                doc.add(new StringField("hostname", InetAddress.getLocalHost().getHostName(), Field.Store.YES));
                doc.add(new StringField("thread", Thread.currentThread().getName(), Field.Store.YES));
                doc.add(new LongField("locKb", Files.size(locPath)/1024, Field.Store.YES));
                doc.add(new LongField("notagsKb", Files.size(locNotagsPath)/1024, Field.Store.YES));
                doc.add(new StringField("creationTime", creationTime.toString(), Field.Store.YES));
                doc.add(new StringField("lastAccessTime", lastAccessTime.toString(), Field.Store.YES));
                doc.add(new StringField("lastModifiedTime", lastModifiedTime.toString(), Field.Store.YES));
                doc.add(new StringField("creationTimeLucene", creationTimeLucene, Field.Store.YES));
                doc.add(new StringField("lastAccessTimeLucene", lastAccessTimeLucene, Field.Store.YES));
                doc.add(new StringField("lastModifiedTimeLucene", lastModifiedTimeLucene, Field.Store.YES));
                
                doc.add(new TextField("title", title, Field.Store.YES));
                doc.add(new TextField("body", body, Field.Store.YES));
                
                // add to index and close

                try {
                    writer.addDocument(doc);
                    writer.commit();
                    System.out.println("Wrote document \"" + title + "\" in the index");    // TODO: arreglar mensajes excepciones (en toda la práctica)
                    writer.close();
                } catch (IOException e) {
                    System.out.println("Graceful message: exception " + e);
                    e.printStackTrace();
                }
            } catch (IOException e) {
                System.err.println("IOException on thread " + Thread.currentThread().getName() + ": " + e);
            }
        }
        static IndexWriter getWriter (String indexPath, String analyzer) {

            try {
                IndexWriterConfig config;
                if (analyzer.equals("StandardAnalyzer")) {
                    config = new IndexWriterConfig(new StandardAnalyzer());
                } else if (analyzer.equals("EnglishAnalyzer")) {
                    config = new IndexWriterConfig(new EnglishAnalyzer());  // TODO: añadir el resto de analyzers
                } else {
                    throw new IllegalArgumentException("Invalid analyzer: " + analyzer);
                }
                return new IndexWriter(FSDirectory.open(Paths.get(indexPath)), config);
            } catch(IOException e) {
                try {
                    Thread.sleep(100 * Thread.currentThread().getId());
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                return getWriter(indexPath, analyzer);
            }
        }

	}
}
