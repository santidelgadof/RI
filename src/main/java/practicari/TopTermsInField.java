package practicari;

public class TopTermsInField {
    public static void main(String[] args) {
        String usage = "Usage: java TopTermsInField -index path -field campo -top n -outfile path";

        if (args.length != 8) {
			System.out.println(usage);
			return;
		}
    }
}
