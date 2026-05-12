#!/bin/bash

# Test runner for Bolt compiler

echo "=== Bolt Test Suite ==="
echo ""

FAILED=0
PASSED=0

# Pre-transpile helper files for imports
mkdir -p tests/build/imports/helper
java -jar app/build/libs/app.jar tests/imports/helper.bolt -o tests/build/imports/helper/helper.c 2>/dev/null

for test_file in tests/*.bolt; do
    test_name=$(basename "$test_file" .bolt)
    echo "Testing: $test_name"
    
    # Create build directory
    mkdir -p "tests/build/$test_name"
    
    # Transpile
    if ! java -jar app/build/libs/app.jar "$test_file" -o "tests/build/$test_name/$test_name.c" 2>&1 | grep -q "Error"; then
        # Compile
        compile_cmd="clang tests/build/$test_name/$test_name.c -o tests/build/$test_name/$test_name.exe"
        
        # Add helper.c for imports_main
        if [ "$test_name" = "imports_main" ]; then
            compile_cmd="$compile_cmd tests/build/imports/helper/helper.c"
            # Create include directory structure
            mkdir -p tests/build/$test_name/imports
            cp tests/build/imports/helper/helper.h tests/build/$test_name/imports/helper.h
        fi
        
        if eval "$compile_cmd" 2>/dev/null; then
            # Run
            "tests/build/$test_name/$test_name.exe" > /dev/null 2>&1
            exit_code=$?
            
            # visibility_block returns 3 (expected)
            if [ "$test_name" = "visibility_block" ]; then
                if [ $exit_code -eq 3 ]; then
                    echo "  ✓ PASSED (exit code 3)"
                    PASSED=$((PASSED + 1))
                else
                    echo "  ✗ FAILED (runtime, exit code $exit_code)"
                    FAILED=$((FAILED + 1))
                fi
            elif [ $exit_code -eq 0 ]; then
                echo "  ✓ PASSED"
                PASSED=$((PASSED + 1))
            else
                echo "  ✗ FAILED (runtime, exit code $exit_code)"
                FAILED=$((FAILED + 1))
            fi
        else
            echo "  ✗ FAILED (compile)"
            FAILED=$((FAILED + 1))
        fi
    else
        echo "  ✗ FAILED (transpile)"
        FAILED=$((FAILED + 1))
    fi
done

echo ""
echo "=== Results ==="
echo "Passed: $PASSED"
echo "Failed: $FAILED"
echo "Total: $((PASSED + FAILED))"

if [ $FAILED -eq 0 ]; then
    echo "All tests passed!"
    exit 0
else
    echo "Some tests failed!"
    exit 1
fi
