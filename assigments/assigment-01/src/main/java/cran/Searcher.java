package cran;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

/** Runs queries against an index with a chosen similarity. */
public final class Searcher implements AutoCloseable {

    private final DirectoryReader reader;
    private final IndexSearcher searcher;
    private final QueryParser parser;

    public Searcher(String analyzerName, String similarityName) throws IOException {
        this.reader = DirectoryReader.open(FSDirectory.open(Paths.get(Config.indexPath(analyzerName))));
        this.searcher = new IndexSearcher(reader);
        this.searcher.setSimilarity(Config.similarity(similarityName));

        Analyzer analyzer = Config.analyzer(analyzerName);
        String[] fields = Config.FIELD_BOOSTS.keySet().toArray(new String[0]);
        this.parser = new MultiFieldQueryParser(fields, analyzer, Config.FIELD_BOOSTS);
        // Cranfield queries are long natural-language questions: OR semantics
        this.parser.setDefaultOperator(QueryParser.Operator.OR);
    }

    public Query parse(String text) throws ParseException {
        return parser.parse(QueryParser.escape(text));
    }

    public TopDocs search(String text, int n) throws IOException, ParseException {
        return searcher.search(parse(text), n);
    }

    public Document doc(int docId) throws IOException {
        return searcher.storedFields().document(docId);
    }

    /** Runs all Cranfield queries and writes a TREC-format run file. */
    public static Path runQueries(String analyzerName, String similarityName) throws Exception {
        List<CranParser.CranQuery> queries = CranParser.parseQueries(Paths.get(Config.QUERIES_PATH));
        String runTag = analyzerName + "_" + similarityName;
        Path out = Paths.get(Config.RESULTS_DIR, runTag + ".run");
        Files.createDirectories(out.getParent());

        try (Searcher s = new Searcher(analyzerName, similarityName);
             PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            for (CranParser.CranQuery q : queries) {
                ScoreDoc[] hits = s.search(q.text(), Config.MAX_RESULTS).scoreDocs;
                for (int rank = 0; rank < hits.length; rank++) {
                    String docId = s.doc(hits[rank].doc).get(Config.F_ID);
                    // qid Q0 docno rank score tag
                    pw.printf("%s Q0 %s %d %.6f %s%n", q.id(), docId, rank + 1, hits[rank].score, runTag);
                }
            }
        }
        System.out.printf("Wrote %s (%d queries)%n", out, queries.size());
        return out;
    }

    /** Interactive search loop for demonstrating the engine. */
    public static void interactive(String analyzerName, String similarityName) throws Exception {
        try (Searcher s = new Searcher(analyzerName, similarityName)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            System.out.printf("Cranfield search [analyzer=%s, similarity=%s]. Empty line to quit.%n",
                analyzerName, similarityName);
            while (true) {
                System.out.print("\nquery> ");
                String line = in.readLine();
                if (line == null || line.isBlank()) break;
                Query q = s.parse(line);
                System.out.println("parsed: " + q);
                TopDocs top = s.searcher.search(q, 10);
                System.out.printf("%d matching documents%n", top.totalHits.value());
                for (int i = 0; i < top.scoreDocs.length; i++) {
                    Document d = s.doc(top.scoreDocs[i].doc);
                    System.out.printf("%2d. [%s] %.4f  %s%n", i + 1, d.get(Config.F_ID),
                        top.scoreDocs[i].score, d.get(Config.F_TITLE));
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
