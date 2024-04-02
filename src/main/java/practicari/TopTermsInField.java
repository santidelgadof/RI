package practicari;

public class TopTermsInField {

    public static int tryParse(String text, String errorMessage) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(errorMessage);
        }
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
        String outfile = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-field":
                    field = args[++i];
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
        } else if (top == -1) {
            System.out.println("Parámetro \"top\" no válido");
            System.exit(1);
        }


    }
}
