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

        String index;
        String field;
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
