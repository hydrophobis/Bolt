#!/bin/bash

# Ensure we are running from the project root (where this script is located)
cd "$(dirname "$0")" || exit 1

# Ensure build directory exists
echo "Creating build directory in $(pwd)/tests/build"
mkdir -p tests/build

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "Running Bolt Test Suite..."
echo "--------------------------"

PASSED=0
TOTAL=0

# Loop through all .bolt files in tests/
for bolt_file in tests/*.bolt; do
    # Check if files exist (handle case where no .bolt files found)
    [ -e "$bolt_file" ] || continue

    base=$(basename "$bolt_file" .bolt)
    expected_file="tests/$base.out"
    output_c="tests/build/$base/$base.c"
    output_bin="tests/build/$base/$base"
    actual_out="tests/build/$base/$base.actual"

    ((TOTAL++))

    mkdir -p "tests/build/$base"

    printf "Test %-20s: " "$base"

    # 1. Transpile using Gradle (specifying :app:run to execute from root)
    # Output logs to build dir to keep output clean
    ./gradlew :app:run --quiet --args="../$bolt_file ../$output_c" > "tests/build/${base}/${base}_transpile.log" 2>&1
    if [ $? -ne 0 ]; then
        echo -e "${RED}FAILED (Transpilation)${NC}"
        echo "  See tests/build/${base}/${base}_transpile.log for details"
        continue
    fi

    # 2. Compile C
    clang "$output_c" -o "$output_bin" > "tests/build/${base}/${base}_compile.log" 2>&1
    if [ $? -ne 0 ]; then
        echo -e "${RED}FAILED (C Compilation)${NC}"
        echo "  See tests/build/${base}/${base}_compile.log for details"
        continue
    fi

    # 3. Run and compare
    # Execute the binary
    "$output_bin" > "$actual_out" 2>&1
    
    # Check return code of execution? (Optional, assuming tests return 0)
    
    # Compare output, ignoring carriage returns (WSL vs Windows issue)
    if diff --strip-trailing-cr -q "$actual_out" "$expected_file" > /dev/null; then
        echo -e "${GREEN}PASSED${NC}"
        ((PASSED++))
    else
        echo -e "${RED}FAILED (Output Mismatch)${NC}"
        echo "  Diff (Expected < vs Actual >):"
        diff --strip-trailing-cr "$expected_file" "$actual_out" | head -n 20
    fi
done

echo "--------------------------"
echo "Summary: $PASSED/$TOTAL tests passed."

if [ $TOTAL -gt 0 ] && [ $PASSED -eq $TOTAL ]; then
    exit 0
else
    exit 1
fi
