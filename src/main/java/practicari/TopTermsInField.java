package practicari;

import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.*;

public class TopTermsInField {

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
        String usage = "Usage: java TopTermsInField -index path -field campo -top n -outfile path";

        if (args.length != 8) {
			System.out.println(usage);
			return;
		}

        String indexPath = null;
        String field = null;
        int top = -1;
        String outFilePath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-field":
                    field = args[++i];
                    break;
                case "-top":
                    top = tryParse(args[++i], "Parámetro top no es un entero válido.");
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
            System.exit(1);
        } else if (field == null) {
            System.out.println("Parámetro \"field\" no válido");
            System.exit(1);
        } else if (outFilePath == null) {
            System.out.println("Parámetro \"outfile\" no válido");
            System.exit(1);
        } else if (top < 0) {
            System.out.println("Parámetro \"top\" no válido");
            System.exit(1);
        }

        FSDirectory indexDir;
        DirectoryReader indexReader;

        try {
            indexDir = FSDirectory.open(Paths.get(indexPath));
            indexReader = DirectoryReader.open(indexDir);

            final FieldInfos fieldinfos = FieldInfos.getMergedFieldInfos(indexReader);

            try (PrintWriter outFile = new PrintWriter(outFilePath)) {
                // intro que identifica el campo y nº max de filas en la tabla
                String intro = "Campo " + field + ", top " + top + " terms:\n";  // docId y nº terms en el top
                System.out.println(intro);
                outFile.print(intro);

                // cabecera de la tabla
                String header = String.format("%-30s%-20s\n", "TERM", "DF");
                System.out.print(header);
                outFile.print(header);

                if (fieldinfos.fieldInfo(field) != null) {
                    final Terms terms = MultiTerms.getTerms(indexReader, field);

                    if (terms != null) {
                        // Calcular el df de cada término
                        HashMap<String, Integer> map = new HashMap<>();
                        final TermsEnum termsEnum = terms.iterator();

                        while (termsEnum.next() != null) {
                            String termString = termsEnum.term().utf8ToString();
                            Term term = new Term(field, termString);

                            int df = indexReader.docFreq(term);
                            map.putIfAbsent(termString, df); // "TERM" "df"
                        }

                        // Ordenar según df
                        LinkedHashMap<String, Integer> sortedMap = getSortedMap(map);

                        int i = 0;
                        for (String key : sortedMap.keySet()) {
                            if(i >= top)
                                break;
                            String output = String.format("%-30s%-20s\n", key, sortedMap.get(key));
                            System.out.print(output);
                            outFile.print(output);
                            i++;
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Excepción IO: " + e);
                e.printStackTrace();
                System.exit(1);
            }
            indexReader.close();
        } catch (IOException e) {
            System.out.println("Excepción IO: " + e);
            e.printStackTrace();
            System.exit(1);
        }


    }

    private static LinkedHashMap<String, Integer> getSortedMap(HashMap<String, Integer> map) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        Comparator<Map.Entry<String, Integer>> comparator = new Comparator<>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        };
        list.sort(comparator);
        LinkedHashMap<String, Integer> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
    }
}
