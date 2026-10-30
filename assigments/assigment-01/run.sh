#!/usr/bin/env bash
# Build, index, run every analyzer x similarity combination and evaluate with trec_eval.
#   ./run.sh                 full experiment
#   ./run.sh demo [a] [s]    interactive search (default: custom bm25)
set -euo pipefail
cd "$(dirname "$0")"

JAR=target/cran-search.jar
JAVA="java --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dorg.apache.lucene.vectorization.upperJavaFeatureVersion=99"
TREC=trec_eval/trec_eval

[ -f "$JAR" ] || mvn -q package -DskipTests

if [ "${1:-}" = "demo" ]; then
    a=${2:-custom}; s=${3:-bm25}
    [ -d "index/$a" ] || $JAVA -jar "$JAR" index "$a"
    exec $JAVA -jar "$JAR" interactive "$a" "$s"
fi

if [ ! -x "$TREC" ]; then
    git clone -q --depth 1 https://github.com/usnistgov/trec_eval.git trec_eval
    make -s -C trec_eval
fi

$JAVA -jar "$JAR" all

QRELS=results/cran.qrels
printf "\n%-12s %-12s %7s %7s %7s %7s %9s %8s\n" analyzer similarity MAP P@5 P@10 R@100 R@1000 nDCG@10
for run in results/*.run; do
    tag=$(basename "$run" .run)
    "$TREC" -m map -m P.5,10 -m recall.100,1000 -m ndcg_cut.10 "$QRELS" "$run" > "results/$tag.eval"
    get() { awk -v m="$1" '$1==m && $2=="all" {print $3}' "results/$tag.eval"; }
    printf "%-12s %-12s %7s %7s %7s %7s %9s %8s\n" "${tag%_*}" "${tag#*_}" \
        "$(get map)" "$(get P_5)" "$(get P_10)" "$(get recall_100)" "$(get recall_1000)" "$(get ndcg_cut_10)"
done | LC_ALL=C sort -b -k3,3gr | tee results/summary.txt
