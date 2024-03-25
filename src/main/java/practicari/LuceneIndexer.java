package practicari;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;

public class LuceneIndexer {

    private static final String INDEX_DIR = "Index/";

    public static void main(String[] args) throws IOException {
        // Directorio donde se almacenará el índice Lucene
        Directory indexDirectory = FSDirectory.open(Paths.get(INDEX_DIR));

        // Configurar el analizador de texto (en este caso, el StandardAnalyzer)
        Analyzer analyzer = new StandardAnalyzer();

        // Configurar el esquema de índice Lucene
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
        IndexWriter indexWriter = new IndexWriter(indexDirectory, indexWriterConfig);

        // Ejemplo de cómo indexar un documento
        indexDocument(indexWriter, "example.com", "Thread-1", 1024, 512, Instant.now(), Instant.now(), Instant.now(), Instant.now(), Instant.now(), Instant.now(), "Example Title", "Example body content");

        // Cerrar el IndexWriter después de indexar todos los documentos
        indexWriter.close();
    }

    private static void indexDocument(IndexWriter indexWriter, String hostname, String thread, long locKb, long notagsKb, Instant creationTime, Instant lastAccessTime, Instant lastModifiedTime, Instant creationTimeLucene, Instant lastAccessTimeLucene, Instant lastModifiedTimeLucene, String title, String body) throws IOException {
        Document doc = new Document();
        doc.add(new StringField("hostname", hostname, Field.Store.YES));
        doc.add(new StringField("thread", thread, Field.Store.YES));
        doc.add(new StringField("locKb", String.valueOf(locKb), Field.Store.YES));
        doc.add(new StringField("notagsKb", String.valueOf(notagsKb), Field.Store.YES));
        doc.add(new StringField("creationTime", creationTime.toString(), Field.Store.YES));
        doc.add(new StringField("lastAccessTime", lastAccessTime.toString(), Field.Store.YES));
        doc.add(new StringField("lastModifiedTime", lastModifiedTime.toString(), Field.Store.YES));
        doc.add(new StringField("creationTimeLucene", creationTimeLucene.toString(), Field.Store.YES));
        doc.add(new StringField("lastAccessTimeLucene", lastAccessTimeLucene.toString(), Field.Store.YES));
        doc.add(new StringField("lastModifiedTimeLucene", lastModifiedTimeLucene.toString(), Field.Store.YES));
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("body", body, Field.Store.YES));

        // Indexar el documento
        indexWriter.addDocument(doc);
    }
}
