package practicari;

import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.*;

public class TopTermsInDoc {
    public static final String usage = "Usage: java TopTermsInDoc -index path -field campo (-docID int | -url url) -top n -outfile path\n" +
            "\t-index path: indica una carpeta con un índice\n" +
            "\t-field campo: indica un campo del índice\n" +
            "\t-docID int: indica que para el documentos con ese docID se debe obtener los términos de ese documento y campo," +
            " su tf y su idf,\n" +
            "\t\t  y presentar el top n que se le indica en un argumento -top n, ordenados por (raw tf) x idflog10.\n" +
            "\t-url url: indica la url de la página que se corresponde con ese documento del que se quieren obtener los datos.\n" +
            "\t\t  Es una alternativa a -docID int. No se pueden usar ambos parámetros a la vez.\n" +
            "\t-top n: indica cuántos términos han de mostrarse por pantalla e imprimirse en el archivo outfile\n" +
            "\t-outfile path: indica el archivo en el que se almacenará el output\n";
    public static int tryParse(String text, String errorMessage) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            System.err.println(errorMessage);
            System.exit(-1);
        }
        return -1;
    }

    public static void main(String[] args) {
        if (args.length < 10) {
            System.out.println(usage);
            return;
        }

        String indexPath = null;
        String field = null;
        int docId = -1;
        int top = -1;
        String url = null;
        String outFilePath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-field":
                    field = args[++i];
                    break;
                case "-docID":
                    docId = tryParse(args[++i], "Parámetro docID no es un entero válido.");
                    break;
                case "-top":
                    top = tryParse(args[++i], "Parámetro top no es un entero válido.\"");
                    break;
                case "-url":
                    url = args[++i];
                    break;
                case "-outfile":
                    outFilePath = args[++i];
                    break;
                default:
                    System.out.println("Parámetro desconocido: " + args[i]);
                    System.out.println(usage);
                    return;
            }
        }

        if (indexPath == null) {
            System.out.println("Parámetro \"index\" no válido");
            System.exit(-1);
        } else if (field == null) {
            System.out.println("Parámetro \"field\" no válido");
            System.exit(-1);
        } else if (outFilePath == null) {
            System.out.println("Parámetro \"outfile\" no válido");
            System.exit(-1);
        } else if (docId == -1 && url == null) {
            System.out.println("Parámetro \"docId\"/\"url\" no válido");
            System.exit(-1);
        } else if (docId != -1 && url != null) {
            System.out.println("Elija entre el parámetro \"docID\" y el parámetro \"url\". No se aceptan ambos.");
            return;
        } else if (docId < 0) {
            System.out.println("Parámetro \"docId\" no válido");
            System.exit(-1);
        } else if (top == -1) {
            System.out.println("Parámetro \"top\" no válido");
            System.exit(-1);
        }

        FSDirectory indexDir;
        DirectoryReader indexReader;

        try {
            indexDir = FSDirectory.open(Paths.get(indexPath));
            indexReader = DirectoryReader.open(indexDir);

            final FieldInfos fieldinfos = FieldInfos.getMergedFieldInfos(indexReader);

            if (url != null) {
                // si nos dan una url la convertimos en el .loc (en cualquier carpeta, "*") y buscamos en el campo path
                IndexSearcher searcher = new IndexSearcher(indexReader);
                Query query = new WildcardQuery(new Term("path", urlToPathField(url)));
                TopDocs topDocs = searcher.search(query, 1);
                docId = topDocs.scoreDocs[0].doc;
            }

            try (PrintWriter outFile = new PrintWriter(outFilePath)) {
                if (fieldinfos.fieldInfo(field) != null) {
                    final Terms terms = MultiTerms.getTerms(indexReader, field);

                    // intro que identifica el doc y nº max de filas en la tabla
                    String intro = "Documento " + docId + ", top " + top + " terms:\n";  // docId y nº terms en el top
                    System.out.println(intro);
                    outFile.print(intro);

                    // cabecera de la tabla
                    String header = String.format("%-30s%-20s%-20s%-80s\n", "TERM", "TF", "DF", "tf x idflog10");
                    System.out.print(header);
                    outFile.print(header);

                    if (terms != null) {
                        // Calcular el tf x idflog10 de cada término
                        HashMap<String, Double> map = new HashMap<>();
                        TFIDFSimilarity tfidf = new ClassicSimilarity();
                        final TermsEnum termsEnum = terms.iterator();
                        PostingsEnum postings = null;
                        int docCount = indexReader.numDocs();

                        while (termsEnum.next() != null) {
                            String termString = termsEnum.term().utf8ToString();
                            Term term = new Term(field, termString);

                            int df = indexReader.docFreq(term);
                            float idf = tfidf.idf(df, docCount);
                            postings = termsEnum.postings(postings, PostingsEnum.FREQS);
                            postings.nextDoc();
                            int freq = postings.freq();
                            float tf = tfidf.tf(freq);
                            double order = (tf * Math.log10(idf));

                            map.putIfAbsent(termString, order); // "TERM" "tf x idflog10"
                        }

                        // Ordenar según tf x idflog10 (order)
                        LinkedHashMap<String, String> sortedMap = getSortedMap(map);

                        int currentTerm = 0;
                        for (String key : sortedMap.keySet()) {
                            PostingsEnum posting = MultiTerms.getTermPostingsEnum(indexReader, field, new BytesRef(key));
                            Term term = new Term(field, key);

                            if (posting != null) {
                                int currentDocId;

                                while ((currentDocId = posting.nextDoc()) != PostingsEnum.NO_MORE_DOCS) {
                                    if (currentDocId == docId) {
                                        int freq = posting.freq();
                                        float tf = tfidf.tf(freq);
                                        int df = indexReader.docFreq(term);
                                        String output = String.format("%-30s%-20s%-20s%-80s\n", key, tf, df, sortedMap.get(key));
                                        System.out.print(output);
                                        outFile.print(output);
                                        currentTerm++;
                                    }
                                }
                            }
                            if (currentTerm >= top)
                                break;
                        }
                    } else {
                        System.err.println("El campo indicado no existe o no tiene términos.\n");
                        System.exit(0);
                    }
                }
            } catch (IOException e) {
                System.out.println("Excepción de IO: " + e);
                e.printStackTrace();
                System.exit(-1);
            }
            indexReader.close();
        } catch (IOException e) {
            System.out.println("Excepción de IO" + e);
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static String urlToPathField(String url) {
        if(url.charAt(url.length() - 1) == '/')
            url = url.substring(url.indexOf("://") + 3, url.length() - 1);
        else
            url = url.substring(url.indexOf("://") + 3);

        return "*" + url + ".loc";    // url pasó de https://www.zzz.com a *www.zzz.com.loc
    }

    private static LinkedHashMap<String, String> getSortedMap(HashMap<String, Double> map) {
        List<Map.Entry<String, Double>> list = new ArrayList<>(map.entrySet());
        Comparator<Map.Entry<String, Double>> comparator = new Comparator<>() {
            @Override
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        };
        list.sort(comparator);
        LinkedHashMap<String, String> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : list) {
            sortedMap.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return sortedMap;
    }
}
