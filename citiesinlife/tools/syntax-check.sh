#!/usr/bin/env bash
# Pre-CI syntax check.
#
# The dev environment cannot reach maven.neoforged.net, so the real compile happens in CI (~3.5 min a
# round trip). This runs javac with no Minecraft on the classpath and filters out the resulting flood of
# "cannot find symbol" / "package does not exist" noise, leaving only errors that are real regardless of
# the classpath: syntax errors, duplicate classes, malformed generics, bad modifiers, missing returns.
#
# Green here does not mean it compiles. Red here always means it does not.
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1
SRC_DIR="src/main/java"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

mapfile -t SOURCES < <(find "$SRC_DIR" -name '*.java' | sort)
echo "checking ${#SOURCES[@]} source files"

javac -d "$OUT" -proc:none -nowarn -XDshould-stop.ifError=FLOW "${SOURCES[@]}" \
  > "$OUT/javac.txt" 2>&1

# Errors that a missing classpath fully explains, and which CI will resolve.
cat > "$OUT/expected.txt" <<'PATTERNS'
cannot find symbol
package [a-zA-Z0-9_.]+ does not exist
cannot access
bad class file
class file for .* not found
has private access
is not abstract and does not override
no suitable method found
method does not override or implement a method from a supertype
incompatible types
cannot be applied to given types
is not public
constructor .* cannot be applied
unreported exception
might not have been initialized
non-static (variable|method)
static import only from classes and interfaces
cannot be dereferenced
array required, but
inconvertible types
PATTERNS

grep -E '\.java:[0-9]+: error:' "$OUT/javac.txt" \
  | grep -vE -f "$OUT/expected.txt" \
  > "$OUT/real.txt"

if [ -s "$OUT/real.txt" ]; then
  echo "--- classpath-independent errors ---"
  cat "$OUT/real.txt"
  echo "-----------------------------------"
  echo "FAIL: $(wc -l < "$OUT/real.txt") structural error(s)"
  exit 1
fi

echo "OK: no structural errors (real type checking still happens in CI)"
