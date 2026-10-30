package cran;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/** Builds a Lucene index of the Cranfield documents with a given analyzer. */
public final class Indexer {

    private Indexer() {}

    public static void index(String analyzerName) throws IOException {
        Path indexPath = Paths.get(Config.indexPath(analyzerName));
        List<CranParser.CranDoc> docs = CranParser.parseDocuments(Paths.get(Config.DOCS_PATH));

        Analyzer analyzer = Config.analyzer(analyzerName);
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        try (Directory dir = FSDirectory.open(indexPath);
             IndexWriter writer = new IndexWriter(dir, iwc)) {
            for (CranParser.CranDoc d : docs) {
                Document doc = new Document();
                doc.add(new StringField(Config.F_ID, d.id(), Field.Store.YES));
                doc.add(new TextField(Config.F_TITLE, d.title(), Field.Store.YES));
                doc.add(new TextField(Config.F_AUTHOR, d.author(), Field.Store.YES));
                doc.add(new TextField(Config.F_BIBLIO, d.biblio(), Field.Store.YES));
                doc.add(new TextField(Config.F_CONTENT, d.content(), Field.Store.YES));
                writer.addDocument(doc);
            }
            writer.commit();
        }
        System.out.printf("Indexed %d documents with '%s' analyzer into %s%n",
            docs.size(), analyzerName, indexPath);
    }
}
