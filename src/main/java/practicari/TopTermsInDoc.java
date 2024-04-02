package practicari;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.FSDirectory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;

public class TopTermsInDoc {

    public static int tryParse(String text, String errorMessage) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public static void main(String[] args) {
        String usage = "Usage: java TopTermsInDoc -index path -field campo -docID int -top n -outfile path";
        
        if (args.length != 10) {
			System.out.println(usage);
			return;
		}

        String indexPath = null;
        String field = null;
        int docId = -1;
        int top = -1;
        String outfile = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-field":
                    field = args[++i];
                    break;
                case "-docID":
                    docId = tryParse(args[++i], "docID paramenter is not a valid integer.");
                    break;
                case "-top":
                    top = tryParse(args[++i], "top paramenter is not a valid integer.");
                    break;
                case "-outfile":
                    outfile = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        if(indexPath == null) {
            System.out.println("Parámetro \"index\" no válido");
            System.exit(1);
        } else if(field == null) {
            System.out.println("Parámetro \"field\" no válido");
            System.exit(1);
        } else if (outfile == null) {
            System.out.println("Parámetro \"outfile\" no válido");
            System.exit(1);
        } else if (docId == -1) {
            System.out.println("Parámetro \"docId\" no válido");
            System.exit(1);
        } else if (top == -1) {
            System.out.println("Parámetro \"top\" no válido");
            System.exit(1);
        }

        FSDirectory indexDir;
        DirectoryReader indexReader;
        try {
            indexDir = FSDirectory.open(Paths.get(indexPath));
            indexReader = DirectoryReader.open(indexDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final FieldInfos fields = FieldInfos.getMergedFieldInfos(indexReader);
        try {
            Terms terms = MultiTerms.getTerms(indexReader, field);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (PrintWriter writer = new PrintWriter(outfile)){

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
