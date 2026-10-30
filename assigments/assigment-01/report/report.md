# CS7IS3 Assignment 1 — Lucene Search Engine for the Cranfield Collection

**Name:** _________  **Student ID:** _________

## 1. Implementation

The system is a Java 21 / Maven project built on Apache Lucene 10.3.2 (`lucene-core`,
`lucene-analysis-common`, `lucene-queryparser`). It has four components:

* **Parser** (`CranParser`). Reads the SMART-style `cran.all.1400` and `cran.qry`
  files, splitting records on `.I` and collecting the `.T` (title), `.A` (author),
  `.B` (bibliography) and `.W` (abstract) sections. The query ids in `cran.qry` are not
  contiguous (001, 002, 004, …, 365), while `cranqrel` numbers the queries 1–225 in file
  order. Queries are therefore renumbered by position; without this step most
  evaluation scores would be wrong.
* **Indexer**. Each document becomes a Lucene `Document` with a stored, untokenised
  `id` (`StringField`) and `TextField`s for title, author, bibliography and content.
  There is one index per analyzer, so analyzers can be compared side by side.
* **Searcher**. Each query is escaped and parsed by a `MultiFieldQueryParser` over
  `title` and `content` with OR semantics, using the same analyzer as at index time.
  The similarity is set on the `IndexSearcher` (Lucene 10 encodes norms independently
  of the similarity, so one index serves every scoring model). The top 1000 documents
  per query are written in TREC run format (`qid Q0 docno rank score tag`). An
  interactive mode prints the parsed query and the top 10 titles, for the demonstration.
* **Evaluation**. `cranqrel` is converted to TREC qrels format. Cleverdon's grades run
  from 1 (complete answer) to 4 (minimum interest), but trec_eval treats higher values
  as more relevant, so grades are mapped 1→4, 2→3, 3→2, 4→1. The 225 entries graded
  `-1` are not real judgements and are dropped. Runs are scored with NIST `trec_eval`
  (MAP, P@5, P@10, recall@100, recall@1000, nDCG@10). `run.sh` performs every step,
  from building to the summary table.

## 2. Analyzers

Five analysis chains were compared:

| Name | Pipeline |
|---|---|
| whitespace | split on whitespace only (baseline) |
| standard | `StandardTokenizer` (Unicode word breaks) + lowercase |
| english | Lucene `EnglishAnalyzer`: standard + possessive removal + English stop words + Porter stemmer |
| kstem | as *custom* but with the Krovetz (KStem) stemmer |
| **custom** | `StandardTokenizer` → `EnglishPossessiveFilter` → `LowerCaseFilter` → `ASCIIFoldingFilter` → `StopFilter` (Lucene English list + ~50 extra words) → `PorterStemFilter` |

The Cranfield queries are full natural-language questions ("*what similarity laws must
be obeyed when constructing aeroelastic models …*"). Many query words are question
words, auxiliaries or filler ("what", "can", "does", "obtained", "available", "so far")
that are not in Lucene's 33-word default stop list, yet match almost every abstract.
The custom stop list removes them. Because each query is an OR of its terms, these
words would otherwise add noise to the scores. Stemming is the largest single gain:
aerodynamics abstracts use many inflected forms (*plate/plates*, *heated/heating*).
Porter's aggressive conflation did slightly better than KStem here. The
`StandardTokenizer` also splits hyphenated compounds such as *boundary-layer* into their
parts, so they match unhyphenated query terms.

## 3. Scoring models

* **Vector Space Model**: `ClassicSimilarity`, Lucene's TF-IDF cosine model
  (tf = √freq, idf = 1 + ln(N/(df+1)), length norm 1/√|d|).
* **BM25**: `BM25Similarity` (k1 = 1.2, b = 0.75). A grid over k1 ∈ {0.6, 1.2, 2.0}
  and b ∈ {0.5, 0.75, 0.9} changed MAP by less than 0.012, so the defaults were kept.
* Three more models for comparison: a Dirichlet-smoothed language model (μ = 1000),
  a Jelinek–Mercer language model (λ = 0.7), and Divergence From Randomness (DFR
  I(n)-B-H2).

A title boost of 2 was tried first and then tuned. The `.W` field already repeats the
title, so extra title weight double-counts it. With custom + BM25, MAP was 0.305
(boost 0), **0.317 (boost 1)**, 0.301 (boost 2) and 0.281 (boost 4); boost 1.0 is used
for all reported runs.

## 4. Results

All 225 queries, top 1000 results per query, custom analyzer unless stated otherwise:

| Analyzer | Similarity | MAP | P@5 | P@10 | Recall@100 | Recall@1000 | nDCG@10 |
|---|---|---|---|---|---|---|---|
| custom | DFR (I(n)-B-H2) | **0.3327** | **0.3502** | **0.2573** | 0.7669 | 0.9499 | **0.3974** |
| custom | **BM25** | 0.3172 | 0.3316 | 0.2484 | 0.7482 | 0.9486 | 0.3854 |
| custom | LM Jelinek–Mercer | 0.3051 | 0.3218 | 0.2400 | 0.7381 | 0.9496 | 0.3770 |
| custom | **VSM (Classic TF-IDF)** | 0.2860 | 0.3022 | 0.2262 | 0.7249 | 0.9486 | 0.3554 |
| custom | LM Dirichlet | 0.2792 | 0.2942 | 0.2147 | 0.7161 | 0.9497 | 0.3443 |

Effect of the analyzer under BM25 / VSM (MAP):

| Analyzer | BM25 | VSM |
|---|---|---|
| whitespace | 0.2535 | 0.2108 |
| standard | 0.2848 | 0.2309 |
| english | 0.3107 | 0.2838 |
| kstem | 0.3135 | 0.2838 |
| custom | **0.3172** | **0.2860** |

**Discussion.** The probabilistic models beat the vector space model: BM25 improves MAP
by 11% relative to TF-IDF (0.317 vs 0.286), and DFR does best overall (0.333). BM25's
saturating term frequency and tunable length normalisation suit short abstracts, where
a repeated term should not dominate the score. DFR has no free parameters and is
robust on small collections. Dirichlet smoothing is the weakest probabilistic model;
with μ = 1000 and abstracts of ~160 words, the collection model outweighs the
document's own term statistics. Analysis
matters as much as the choice of scoring model: under BM25, going from whitespace
tokenisation to the custom chain raises MAP from 0.254 to 0.317 (+25%). Most of that
comes from lowercasing and punctuation handling (standard) and from stemming and stop
words (english). Recall@1000 is about 0.95 for every configuration, so the models
differ in how they rank relevant documents, not in whether they find them.

## Bibliography

1. Apache Software Foundation. *Apache Lucene 10.3 documentation*. https://lucene.apache.org/
2. C. Cleverdon. The Cranfield tests on index language devices. *Aslib Proceedings*, 19(6), 1967.
3. G. Salton, A. Wong, C. S. Yang. A vector space model for automatic indexing. *CACM* 18(11), 1975.
4. S. Robertson, H. Zaragoza. The probabilistic relevance framework: BM25 and beyond. *Found. Trends IR* 3(4), 2009.
5. G. Amati, C. J. van Rijsbergen. Probabilistic models of IR based on measuring the divergence from randomness. *ACM TOIS* 20(4), 2002.
6. C. Zhai, J. Lafferty. A study of smoothing methods for language models applied to IR. *ACM TOIS* 22(2), 2004.
7. M. F. Porter. An algorithm for suffix stripping. *Program* 14(3), 1980.
8. NIST. *trec_eval*. https://github.com/usnistgov/trec_eval
