#!/usr/bin/env bash
# Fetches the open-source TS corpus defined in corpus.json into benchmarks/corpus/.
# Requires: git, jq.
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p corpus

jq -c '.projects[]' corpus.json | while read -r project; do
    name=$(jq -r '.name' <<< "$project")
    url=$(jq -r '.url' <<< "$project")
    commit=$(jq -r '.commit' <<< "$project")

    if [[ -d "corpus/$name" ]]; then
        echo "[skip] corpus/$name already exists"
        continue
    fi

    echo "[clone] $name ($url @ $commit)"
    git clone --filter=blob:none "$url" "corpus/$name"
    git -C "corpus/$name" checkout --quiet "$commit"
    echo "[ok] corpus/$name @ $(git -C "corpus/$name" rev-parse --short HEAD)"
done

echo "Corpus ready under $(pwd)/corpus"
