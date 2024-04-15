package practicari;

import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.classic.ClassicAnalyzer;
import org.apache.lucene.analysis.core.*;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.gl.GalicianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.store.FSDirectory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.lineSeparator;


public class WebIndexer {

    private static final int HTTP_OK = 200;
    private static final String urlFilePath = "src" + File.separator + "test" + File.separator + "resources"
            + File.separator + "urls";
    
    private static final String CONFIG_FILE_PATH = "src" + File.separator + "main" + File.separator + "resources"
            + File.separator + "config.properties";  

    public static final String usage = "Usage: java WebIndexer -index INDEX_PATH -docs DOCS_PATH [-create]" +
            "[-numThreads int] [-h] [-p] [-titleTermVectors] [-bodyTermVectors] [-analyzer Analyzer]\n\n" +
            "-index INDEX_PATH: indica la carpeta donde se almacenará el índice\n" +
            "-docs DOCS_PATH: indica la carpeta donde se almacenan los archivos .loc y .loc.notags\n" +
            "-create: pide que el índice se abra con CREATE, por defecto con CREATE_OR_APPEND\n" +
            "-numThreads int: indica el número de hilos. Si no se indica esta opción, se " +
            "usarán por defecto tantos hilos como el número de cores que se puede obtener con " +
            "Runtime.getRuntime().availableProcessors()\n" +
            "-h: hace que cada hilo informe del comienzo y fin de su trabajo: «Hilo xxx comienzo url yyy» «Hilo" +
            "xxx fin url yyy»\n" +
            "-p: hace que la aplicación informe del fin de su trabajo: «Creado índice zzz en mmm msecs»\n" +
            "-titleTermVectors: indica que el campo title debe almacenar Term Vectors\n" +
            "-bodyTermVectors: indica que el campo body debe almacenar Term Vectors\n" +
            "-analyzer Analyzer: pide que se use uno de los Analyzers ya proporcionados por " +
            "Lucene, por defecto se usará el Standard Analyzer. Los analyzers aceptados son:\n" +
            "     StandardAnalyzer, ClassicAnalyzer, GalicianAnalyzer, EnglishAnalyzer, KeywordAnalyzer, " +
            "SimpleAnalyzer, SpanishAnalyzer, UnicodeWhitespaceAnalyzer, y WhitespaceAnalyzer";

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println(usage);
            return;
        }

        // Guardar y validar opciones
        String indexPath = null;
        String docsPath = null;
        boolean create = false;
        int numThreads = Runtime.getRuntime().availableProcessors();
        boolean showThreadInfo = false;
        boolean showIndexCreatTime = false;
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
                    numThreads = tryParse(args[++i], "Parámetro numThreads no es un entero válido.");
                    break;
                case "-h":
                    showThreadInfo = true;
                    break;
                case "-p":
                    showIndexCreatTime = true;
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
                    System.out.println("Parámetro desconocido: " + args[i]);
                    System.out.println(usage);
                    return;
            }
        }

        if(indexPath == null) {
            System.out.println("El argumento \"index\" es obligatorio.");
            System.exit(-1);
        }

        if(docsPath == null){
            System.out.println("El argumento \"docs\" es obligatorio.");
            System.exit(-1);
        }

        if(numThreads < 1) {
            System.out.println("El argumento \"numThreads\" ha de ser un número positivo.");
            System.exit(-1);
        }

        Path indexDir = Paths.get(indexPath);
        Path docsDir = Paths.get(docsPath);

        if (!Files.exists(indexDir) || !Files.isDirectory(indexDir)) {
            System.out.println("El directorio para el índice \"" + indexPath + "\" no existe.");
            System.exit(-1);
        }
        if (!Files.exists(docsDir) || !Files.isDirectory(docsDir)) {
            System.out.println("El directorio para los documentos \"" + docsPath + "\" no existe.");
            System.exit(-1);
        }

        IndexWriter indexWriter = getWriter(indexPath, analyzer, create);

        // Creamos el pool de threads
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Obtenemos la lista de archivos de la carpeta urls
        File folder = new File(urlFilePath);
        File[] listOfFiles = folder.listFiles();
        List<Path> listOfPaths = new LinkedList<>();

        // Si no hay archivos en la carpeta urls, salimos
        if(listOfFiles == null) {
            System.out.println("No se han encontrado arhivos en la carpeta urls.");
            System.exit(0);
        }
    
        // Añadimos a la lista de paths los archivos que sean ficheros y se puedan leer
        for (File file : listOfFiles) {
            if(file.isFile() && file.canRead())
                listOfPaths.add(file.toPath());
        }

        for (Path path : listOfPaths) {
            final Runnable worker = new WorkerThread(path, docsPath, showThreadInfo,
                    showIndexCreatTime, create, titleTermVectors, bodyTermVectors, indexWriter);
            /* Send the thread to the ThreadPool. It will be processed eventually. */
            executor.execute(worker);
        }

            /* Close the ThreadPool; no more jobs will be accepted, but all the previously
               submitted jobs will be processed. */
        executor.shutdown();

        /* Wait up to 1 hour to finish all the previously submitted jobs */
        // Si no termina en una hora, salimos
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (final InterruptedException e) {
            System.out.println("Thread interrumpido: "+ e.getMessage());
            e.printStackTrace();
            System.exit(-2);
        }

        // Cerramos el indexWriter
        try {
            indexWriter.close();
        } catch (IOException e) {
            System.out.println("IOException al cerrar el indexWriter: "+ e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }

    // Convertir un String a un entero
    private static int tryParse(String text, String errorMessage) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            System.err.println(errorMessage);
            System.exit(-1);
        }
        return 0;
    }

    // Leer las urls de un archivo
    private static List<String> readUrlsFromFile(Path filePath){
        List<String> urls = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("http://") || line.startsWith("https://")) {
                    urls.add(line.trim());
                }
            }
        } catch (IOException e) {
            System.out.println("Excepción de IO al leer las urls del archivo: " + e);
            e.printStackTrace();
            System.exit(-1);
        }
        return urls;
    }

    public static class WorkerThread implements Runnable {

		private final Path path;
		private final String docsPath;
        private final boolean showThreadInfo;
        private final boolean showIndexCreatTime;
        private final boolean create;
        private final boolean titleTermVectors;
        private final boolean bodyTermVectors;
        private final IndexWriter indexWriter;

		public WorkerThread(final Path path, final String docsPath, final boolean showThreadInfo, final boolean showIndexCreatTime,
                            final boolean create, final boolean titleTermVectors, final boolean bodyTermVectors,
                            final IndexWriter indexWriter) {
			this.path = path;
			this.docsPath = docsPath;
            this.showThreadInfo = showThreadInfo;
            this.showIndexCreatTime = showIndexCreatTime;
            this.create = create;  
            this.titleTermVectors = titleTermVectors;
            this.bodyTermVectors = bodyTermVectors;
            this.indexWriter = indexWriter;
		}

		/**
		 * This is the work that the current thread will do when processed by the pool.
		 */

        // Método que se ejecuta en cada hilo
		@Override
		public void run() {
            List<String> urls = readUrlsFromFile(path);
            Properties configProperties = loadConfigProperties();

            if (configProperties != null) {
                String onlyDoms = configProperties.getProperty("onlyDoms");
                if (onlyDoms != null && !onlyDoms.isEmpty()) {
                    // Filtrar las URLs según el dominio especificado en onlyDoms
                    urls = filterUrlsByDomain(urls, onlyDoms);
                }
            }

            for(String url : urls) {
                if(showThreadInfo)
                    System.out.printf("Hilo '%s' comienzo url '%s'%n",
                            Thread.currentThread().getName(), url);

                processUrl(url, docsPath, showIndexCreatTime, create, titleTermVectors,
                        bodyTermVectors, indexWriter);

                if(showThreadInfo)
                    System.out.printf("Hilo '%s' fin url '%s'%n",
                            Thread.currentThread().getName(), url);
            }
        }
        
        // Procesar url
		static void processUrl(String url, String docsPath,
                               boolean showIndexCreatTime, boolean create, boolean titleTermVectors,
                               boolean bodyTermVectors, IndexWriter indexWriter) {
            // Timeout de 5 minutos
        	HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(5)).build();
            
            // Descargar la página web
			try {
                // Realizar petición GET a la URL
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
                        writer.close();

                        indexUrl(locFilePath, title, body, showIndexCreatTime, create,
                                titleTermVectors, bodyTermVectors, indexWriter);
                        
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("Error al descargar la url " + url + ". Status code: " + statusCode);
                }
            } catch (URISyntaxException e) {
                System.err.println("La sintaxis de la URL es inválida: " + url);
                e.printStackTrace();
            } catch (IOException e) {
                System.err.println("Error leyendo o escribiendo la url: " + e.getMessage());
                e.printStackTrace();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrumpido mientras esperaba respuesta: " + e.getMessage());
                e.printStackTrace();
            }
		}
        
        // Indexar url
        static void indexUrl(Path locPath, String title, String body, boolean showIndexCreatTime,
                             boolean create, boolean titleTermVectors, boolean bodyTermVectors, IndexWriter writer) {
            long t1 = 0;
            if (showIndexCreatTime)
                t1 = currentTimeMillis();
            Path locNotagsPath = Path.of(locPath.toString() + ".notags");
            
            // index document
            try (InputStream stream = Files.newInputStream(locPath)) {
                org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
                
                // Añadir campos al documento
                FileTime creationTime = (FileTime) Files.getAttribute(locPath, "creationTime");
                FileTime lastAccessTime = (FileTime) Files.getAttribute(locPath, "lastAccessTime");
                FileTime lastModifiedTime = (FileTime) Files.getAttribute(locPath, "lastModifiedTime");
                String creationTimeLucene = DateTools.dateToString(Date.from(creationTime.toInstant()), Resolution.MILLISECOND);
                String lastAccessTimeLucene = DateTools.dateToString(Date.from(lastAccessTime.toInstant()), Resolution.MILLISECOND);
                String lastModifiedTimeLucene = DateTools.dateToString(Date.from(lastModifiedTime.toInstant()), Resolution.MILLISECOND);

                doc.add(new KeywordField("path", locPath.toString(), Field.Store.YES));

                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                StringBuilder contentsBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    contentsBuilder.append(line);
                    contentsBuilder.append(lineSeparator());
                }
                reader.close();

                doc.add(new TextField("contents", contentsBuilder.toString(), Field.Store.YES));
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

                // title & body guardan o no term vectors dependiendo de los argumentos
                if(!titleTermVectors) {
                    doc.add(new TextField("title", title, Field.Store.YES));
                } else {
                    FieldType titleVect = new FieldType();
                    titleVect.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
                    titleVect.setStored(true);
                    titleVect.setTokenized(true);
                    titleVect.setOmitNorms(false);
                    titleVect.setStoreTermVectors(true);
                    doc.add(new Field("title", title, titleVect));
                }

                if(!bodyTermVectors) {
                    doc.add(new TextField("body", body, Field.Store.YES));
                } else {
                    FieldType bodyVect = new FieldType();
                    bodyVect.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
                    bodyVect.setStored(true);
                    bodyVect.setTokenized(true);
                    bodyVect.setOmitNorms(true);
                    bodyVect.setStoreTermVectors(true);
                    doc.add(new Field("body", body, bodyVect));
                }

                // add to index and close
                if (create) {
                    // se borran los archivos anteriores y quedan sólo los nuevos
                    writer.addDocument(doc);
                } else {
                    /* si había archivos en el índice se dejan ahí. Los nuevos se crean, si hay alguno que
                    quedaría repetido se actualiza el viejo. */
                    writer.updateDocument(new Term("path", locPath.toString()), doc);
                    writer.commit();
                }

                if (showIndexCreatTime) {
                    long t2 = currentTimeMillis();
                    System.out.println("Creado índice \"" + title + "\" en " + (t2-t1) + " msec");
                }
            } catch (IOException e) {
                System.err.println("IOException en el thread " + Thread.currentThread().getName() + ": " + e);
            }
        }

	}

    // Método para obtener un IndexWriter
    static IndexWriter getWriter (String indexPath, String analyzer, boolean create) {
        
        try {
            IndexWriterConfig config = null;

            // Seleccionar el analyzer
            switch(analyzer) {
                case "StandardAnalyzer": config = new IndexWriterConfig(new StandardAnalyzer()); break;
                case "EnglishAnalyzer": config = new IndexWriterConfig(new EnglishAnalyzer()); break;
                case "KeywordAnalyzer": config = new IndexWriterConfig(new KeywordAnalyzer()); break;
                case "SimpleAnalyzer": config = new IndexWriterConfig(new SimpleAnalyzer()); break;
                case "UnicodeWhitespaceAnalyzer": config = new IndexWriterConfig(new UnicodeWhitespaceAnalyzer()); break;
                case "WhitespaceAnalyzer": config = new IndexWriterConfig(new WhitespaceAnalyzer()); break;
                case "ClassicAnalyzer": config = new IndexWriterConfig(new ClassicAnalyzer()); break;
                case "GalicianAnalyzer": config = new IndexWriterConfig(new GalicianAnalyzer()); break;
                case "SpanishAnalyzer": config = new IndexWriterConfig(new SpanishAnalyzer()); break;
                default:
                    System.out.println("Invalid analyzer: " + analyzer); System.exit(-1); break;
            }

            // Configurar el modo de apertura del índice
            if (create)
                config.setOpenMode(OpenMode.CREATE);
            else
                config.setOpenMode(OpenMode.CREATE_OR_APPEND);

            return new IndexWriter(FSDirectory.open(Paths.get(indexPath)), config);

        } catch(IOException e) {

            // si el writer ya está siendo usado por otro hilo, esperamos y volvemos a intentarlo
            try {
                Thread.sleep(Thread.currentThread().getId());
            } catch (InterruptedException ex) {
                System.err.println("Thread interrumpido: " + e.getMessage());
                e.printStackTrace();
            }
            return getWriter(indexPath, analyzer, create);
        }
    }
    
    // Método para cargar las propiedades del archivo de configuración
    private static Properties loadConfigProperties() {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE_PATH)) {
            properties.load(fis);
        } catch (IOException e) {
            System.err.println("Error al cargar el archivo de configuración: " + e.getMessage());
            return null;
        }
        return properties;
    }

    // Método para filtrar las URLs por dominio
    private static List<String> filterUrlsByDomain(List<String> urls, String domains) {
        List<String> filteredUrls = new ArrayList<>();
        String[] domainArray = domains.split("\\s+");
        for (String url : urls) {
            for (String domain : domainArray) {
                if (url.endsWith(domain) || url.endsWith(domain + "/")) {
                    filteredUrls.add(url);
                    break; // No necesitamos verificar otros dominios una vez que encontramos una coincidencia
                }
            }
        }
        return filteredUrls;
    }
    
}
