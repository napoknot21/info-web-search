package cran;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the Cranfield collection's SMART-style format:
 * records start with ".I id" and contain ".T", ".A", ".B", ".W" sections.
 */
public final class CranParser {

    private CranParser() {}

    public record CranDoc(String id, String title, String author, String biblio, String content) {}

    /**
     * 
     * @param id     sequential query number (1..225) as used in cranqrel
     * @param origId the (non-contiguous) number written in cran.qry
     */
    public record CranQuery(String id, String origId, String text) {}

    public static List<CranDoc> parseDocuments(Path path) throws IOException {
        List<CranDoc> docs = new ArrayList<>();
        for (Record r : parseRecords(path)) {
            docs.add(new CranDoc(r.id, r.get('T'), r.get('A'), r.get('B'), r.get('W')));
        }
        return docs;
    }

    /**
     * Query ids in cran.qry are not contiguous (001, 002, 004, ...) whereas
     * cranqrel numbers them 1..225 in file order, so queries are renumbered
     * by position.
     */
    public static List<CranQuery> parseQueries(Path path) throws IOException {
        
        List<CranQuery> queries = new ArrayList<>();
        
        int n = 1;
        for (Record r : parseRecords(path)) {
            queries.add(new CranQuery(String.valueOf(n++), r.id, r.get('W')));
        }
        
        return queries;
    }

    private static final class Record {
        
        final String id;
        final StringBuilder[] fields = new StringBuilder[128];

        Record(String id) { this.id = id; }

        void append(char field, String line) {
            if (fields[field] == null) fields[field] = new StringBuilder();
            if (!fields[field].isEmpty()) fields[field].append(' ');
            fields[field].append(line.strip());
        }

        String get(char field) {
            return fields[field] == null ? "" : fields[field].toString();
        }
    }

    private static List<Record> parseRecords(Path path) throws IOException {
        List<Record> records = new ArrayList<>();
        Record current = null;
        char field = 0;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.startsWith(".I")) {
                current = new Record(line.substring(2).strip());
                records.add(current);
                field = 0;
            } else if (line.length() == 2 && line.charAt(0) == '.' && "TABW".indexOf(line.charAt(1)) >= 0) {
                field = line.charAt(1);
            } else if (current != null && field != 0) {
                current.append(field, line);
            }
        }
        return records;
    }
}
