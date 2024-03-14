package practicari;

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

        String index;
        String field;
        int docId;
        int top;
        String outfile;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    index = args[++i];
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
    }
}
