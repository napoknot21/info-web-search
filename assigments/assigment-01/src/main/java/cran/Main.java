package cran;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    private static void usage() {
        System.err.println("""
            Usage: java -jar cran-search.jar <command> [options]

              qrels                         convert cranqrel into TREC qrels format (results/cran.qrels)
              index  [analyzer]             build index (default: custom)
              search [analyzer] [sim]       run all 225 queries, write results/<analyzer>_<sim>.run
              all                           index + search every analyzer x similarity combination
              interactive [analyzer] [sim]  type queries and see the top 10 results

            analyzers:    %s
            similarities: %s
            """.formatted(Config.ANALYZERS, Config.SIMILARITIES));
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            System.exit(1);
        }
        String analyzer = args.length > 1 ? args[1] : "custom";
        String sim = args.length > 2 ? args[2] : "bm25";

        switch (args[0]) {
            case "qrels" -> writeQrels();
            case "index" -> Indexer.index(analyzer);
            case "search" -> Searcher.runQueries(analyzer, sim);
            case "interactive" -> Searcher.interactive(analyzer, sim);
            case "all" -> {
                writeQrels();
                for (String a : Config.ANALYZERS) {
                    Indexer.index(a);
                    for (String s : Config.SIMILARITIES) Searcher.runQueries(a, s);
                }
            }
            default -> {
                usage();
                System.exit(1);
            }
        }
    }

    /**
     * cranqrel lines are "qid docid grade" where grade 1 is the most relevant
     * and 4 the least; -1 entries are not real judgements. trec_eval expects
     * "qid 0 docid rel" with higher rel = more relevant, so grades are
     * inverted (1->4 ... 4->1) and -1 entries dropped.
     */
    private static void writeQrels() throws IOException {
        Path out = Paths.get(Config.RESULTS_DIR, "cran.qrels");
        Files.createDirectories(out.getParent());
        List<String> lines = Files.readAllLines(Paths.get(Config.QRELS_PATH), StandardCharsets.UTF_8);
        int written = 0;
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            for (String line : lines) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 3) continue;
                int grade = Integer.parseInt(p[2]);
                if (grade < 1 || grade > 4) continue;
                pw.printf("%s 0 %s %d%n", p[0], p[1], 5 - grade);
                written++;
            }
        }
        System.out.printf("Wrote %s (%d judgements)%n", out, written);
    }
}
