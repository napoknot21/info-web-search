# Assignment part 1 — Lucene search engine on Cranfield

Requirements: Java 21+, Maven, gcc/make (to build trec_eval), git.

```sh
./run.sh                      # build, fetch+build trec_eval, index, run all 225 queries
                              # for every analyzer x similarity, evaluate -> results/summary.txt
./run.sh demo [analyzer] [sim]   # interactive search (default: custom bm25)
```

Manual use of the jar:

```sh
mvn package
java -jar target/cran-search.jar qrels                 # results/cran.qrels (TREC format)
java -jar target/cran-search.jar index custom          # index/custom
java -jar target/cran-search.jar search custom bm25    # results/custom_bm25.run
trec_eval/trec_eval -m map -m recall.1000 results/cran.qrels results/custom_bm25.run
```

Analyzers: `whitespace`, `standard`, `english`, `kstem`, `custom`.
Similarities: `classic` (TF-IDF VSM), `bm25`, `lmdirichlet`, `lmjm`, `dfr`.
Tuning overrides: `-Dk1=1.2 -Db=0.75 -DtitleBoost=1.0`.

## Layout

| File | Role |
|---|---|
| `src/main/java/cran/CranParser.java` | parses `.I/.T/.A/.B/.W` records; renumbers queries 1..225 |
| `src/main/java/cran/Config.java` | paths, fields, analyzer and similarity definitions |
| `src/main/java/cran/Indexer.java` | builds one index per analyzer |
| `src/main/java/cran/Searcher.java` | batch TREC run generation + interactive mode |
| `src/main/java/cran/Main.java` | CLI; converts `cranqrel` to TREC qrels |

## Notes on the data

* `cran.qry` ids are not contiguous (001, 002, 004, ... 365) but `cranqrel` numbers
  queries 1..225 in file order, so queries are renumbered by position.
* `cranqrel` grades are 1 (most relevant) .. 4 (least); they are inverted to 4..1 for
  trec_eval (higher = more relevant). The 225 `-1` entries are dropped.
