package cran;

import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.KStemFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;

import org.apache.lucene.search.similarities.AfterEffectB;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicModelIn;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.DFRSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.NormalizationH2;
import org.apache.lucene.search.similarities.Similarity;

/**
 * Central place for paths, field names, and the analyzer / similarity
 * choices used in the experiments.
 */
public final class Config {

    private Config() {}

    // Field names
    public static final String F_ID      = "id";
    public static final String F_TITLE   = "title";
    public static final String F_AUTHOR  = "author";
    public static final String F_BIBLIO  = "biblio";
    public static final String F_CONTENT = "content";

    // Default paths
    public static final String DOCS_PATH    = "cran/cran.all.1400";
    public static final String QUERIES_PATH = "cran/cran.qry";
    public static final String QRELS_PATH   = "cran/cranqrel";
    public static final String INDEX_ROOT   = "index";
    public static final String RESULTS_DIR  = "results";

    // Number of documents returned per query in the TREC run file.
    public static final int MAX_RESULTS = 1000;

    // Field boosts used when searching (title words are more indicative). */
    public static final Map<String, Float> FIELD_BOOSTS = Map.of(
        F_TITLE,   Float.parseFloat(System.getProperty("titleBoost", "1.0")),
        F_CONTENT, 1.0f
    );

    public static final List<String> ANALYZERS = List.of("whitespace", "standard", "english", "kstem", "custom");
    public static final List<String> SIMILARITIES = List.of("classic", "bm25", "lmdirichlet", "lmjm", "dfr");

    /**
     * Stop words: Lucene's default English set plus words that are very
     * common in Cranfield queries but carry no topical meaning.
     */
    private static final CharArraySet CUSTOM_STOPWORDS = buildStopwords();

    private static CharArraySet buildStopwords() {

        CharArraySet set = new CharArraySet(EnglishAnalyzer.ENGLISH_STOP_WORDS_SET, true);
        
        set.addAll(List.of(
            "what", "which", "how", "why", "when", "where", "who", "whom",
            "can", "could", "do", "does", "did", "has", "have", "had",
            "been", "being", "am", "is", "are", "was", "were",
            "any", "some", "about", "from", "so", "far", "also", "its",
            "should", "would", "may", "might", "must", "shall", "between",
            "obtained", "available", "given", "possible", "find", "made",
            "i", "me", "my", "we", "our", "you", "your", "he", "she", "him", "her"
        ));
        return CharArraySet.unmodifiableSet(set);
    }

    /**
     * Custom pipeline: StandardTokenizer -> possessive removal -> lowercase
     * -> ASCII folding -> stop words -> Porter stemming.
     */
    static final class CustomAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
        
            Tokenizer source = new StandardTokenizer();
            TokenStream ts = new EnglishPossessiveFilter(source);
        
            ts = new LowerCaseFilter(ts);
            ts = new ASCIIFoldingFilter(ts);
            ts = new StopFilter(ts, CUSTOM_STOPWORDS);
            ts = new PorterStemFilter(ts);
        
            return new TokenStreamComponents(source, ts);
        
        }

        @Override
        protected TokenStream normalize(String fieldName, TokenStream in) {
            return new ASCIIFoldingFilter(new LowerCaseFilter(in));
        }
    }


    /** Same as custom but with the less aggressive Krovetz stemmer. */
    static final class KStemAnalyzer extends Analyzer {
       
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            
            Tokenizer source = new StandardTokenizer();
            TokenStream ts = new EnglishPossessiveFilter(source);
            
            ts = new LowerCaseFilter(ts);
            ts = new ASCIIFoldingFilter(ts);
            ts = new StopFilter(ts, CUSTOM_STOPWORDS);
            ts = new KStemFilter(ts);
            
            return new TokenStreamComponents(source, ts);
        
        }
    
    }

    
    public static Analyzer analyzer(String name) {
    
        return switch (name) {
    
            case "whitespace" -> new WhitespaceAnalyzer();
            case "standard"   -> new StandardAnalyzer();               // tokenise + lowercase, no stop words
            case "english"    -> new EnglishAnalyzer();                // + stop words + Porter
            case "custom"     -> new CustomAnalyzer();
            case "kstem"      -> new KStemAnalyzer();
            default -> throw new IllegalArgumentException("Unknown analyzer: " + name);
    
        };
    
    }

    public static Similarity similarity(String name) {
    
        return switch (name) {
    
            case "classic"     -> new ClassicSimilarity();             // TF-IDF vector space model
    
            case "bm25"        -> new BM25Similarity(
                Float.parseFloat(System.getProperty("k1", "1.2")),
                Float.parseFloat(System.getProperty("b", "0.75")));
    
            case "lmdirichlet" -> new LMDirichletSimilarity(1000f);
            case "lmjm"        -> new LMJelinekMercerSimilarity(0.7f);
            case "dfr"         -> new DFRSimilarity(new BasicModelIn(), new AfterEffectB(), new NormalizationH2());
    
            default -> throw new IllegalArgumentException("Unknown similarity: " + name);
    
        };
    
    }

    public static String indexPath(String analyzer) {
        return INDEX_ROOT + "/" + analyzer;
    }
}
