#!/bin/sh
REPO_ROOT=$(git rev-parse --show-toplevel)
SRC_DIR="$REPO_ROOT/scripts/hooks"
DST_DIR="$REPO_ROOT/.git/hooks"

for hook in pre-push post-commit; do
  if [ -f "$SRC_DIR/$hook" ]; then
    cp "$SRC_DIR/$hook" "$DST_DIR/$hook"
    chmod +x "$DST_DIR/$hook"
    echo "Installed: $hook"
  fi
done
