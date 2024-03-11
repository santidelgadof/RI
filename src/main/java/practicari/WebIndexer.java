package practicari;

import java.io.IOException;
import java.nio.file.Paths;

public class WebIndexer {
    public static void main(String[] args) {
        String usage = "Usage: java WebIndexer -index INDEX_PATH -docs DOCS_PATH [-create] [-numThreads int]" +
        "[-h] [-p] [-titleTermVectors] [-bodyTermVectors] [-analyzer Analyzer]";
        
        if (args.length < 4) {
			System.out.println(usage);
			return;
		}

        String indexPath;
        String docsPath;
        boolean create = false;
        int numThreads = Runtime.getRuntime().availableProcessors();
        boolean threadInfo = false;
        boolean appInfo = false;
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
                    numThreads = args[++i];
                    break;
                case "-h":
                    threadInfo = true;
                    break;
                case "-p":
                    appInfo = true;
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
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
          }
      
          if (docsPath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
          }
    }
}
