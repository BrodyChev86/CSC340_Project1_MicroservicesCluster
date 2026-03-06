import java.util.*;
import java.util.stream.Collectors;

public class TOPK {

    private final List<List<String>> corpus; // tokenized corpus: list of documents
    private final int totalDocs; // number of documents in the corpus
    // Basic list of filler words to ignore
    private final Set<String> stopWords = new HashSet<>(Arrays.asList(
        "is", "the", "a", "an", "and", "or", "it", "to", "of", "i", "in", "with", "for"
    ));



    public TOPK(List<String> documents) { // build the internal tokenized corpus
        this.corpus = documents.stream() // collection of text documents
                .map(this::tokenize)
                .collect(Collectors.toList());
        this.totalDocs = corpus.size();
    }

    public List<Map.Entry<String, Double>> getTopKTerms(String userRequest, int k) { // compute top-k by TF-IDF
        List<String> tokens = tokenize(userRequest); // tokens from the user request
        Map<String, Double> tfIdfMap = new HashMap<>(); // term -> tf-idf score

        for (String term : new HashSet<>(tokens)) {
            // Skip common stop words to keep results meaningful
            if (stopWords.contains(term)) continue;

            double tf = calculateTF(tokens, term);
            double idf = calculateIDF(term);
            tfIdfMap.put(term, tf * idf);
        }

        return tfIdfMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(k)
                .collect(Collectors.toList());
    }

    private double calculateTF(List<String> tokens, String term) { //calculates term frequency (TF) formula
        double count = 0;
        for (String s : tokens) {
            if (s.equalsIgnoreCase(term)) count++;
        }
        return count / tokens.size();
    }

    private double calculateIDF(String term) { //calculates inverse document frequency (IDF) formula
        double docsWithTerm = 0;
        for (List<String> doc : corpus) {
            if (doc.contains(term.toLowerCase())) docsWithTerm++;
        }
        // log(Total / (Found + 1)) helps balance the weight
        return Math.log((double) totalDocs / (1.0 + docsWithTerm));
    }

    private List<String> tokenize(String text) {
        return Arrays.asList(text.toLowerCase().replaceAll("[^a-zA-Z0-9 ]", "").split("\\s+")); // lowercase, remove punctuation, split on whitespace
    }

    public static void main(String[] args) {
        // This is our "Reference Library"
        List<String> dataset = Arrays.asList( // small example corpus
            "Java is a high-level, class-based, object-oriented programming language.",
            "Python is an interpreted high-level general-purpose programming language.",
            "JavaScript is a language that is one of the core technologies of the World Wide Web.",
            "C++ is an extension of the C programming language.",
            "The coffee bean comes from the Java region and is very famous worldwide."
        );

    TOPK extractor = new TOPK(dataset);
    Scanner scanner = new Scanner(System.in); // console input reader

        System.out.println("--- TF-IDF Term Extractor ---");
        System.out.println("Type a sentence to find the most 'important' words (or type 'exit'):");

        while (true) {
            System.out.print("\nYour Input: ");
            String input = scanner.nextLine();

            if (input.equalsIgnoreCase("exit")) break;

            // We ask for the Top 3 terms
            List<Map.Entry<String, Double>> results = extractor.getTopKTerms(input, 3); // compute top-3

            if (results.isEmpty()) {
                System.out.println("No significant terms found.");
            } else {
                System.out.println("Top Terms (Ranked by TF-IDF):");
                results.forEach(e -> System.out.printf("- %s (Score: %.4f)%n", e.getKey(), e.getValue()));
            }
        }
        
        scanner.close();
        System.out.println("Goodbye!");
    }
}