@echo off
setlocal enabledelayedexpansion

if exist tests\build\*.log del /q tests\build\*.log

echo Running Bolt Test Suite...
echo --------------------------

if not exist tests\build mkdir tests\build

set PASSED=0
set TOTAL=0

for %%f in (tests\*.bolt) do (
    set /a TOTAL+=1

    set "bolt_file=%%f"
    set "base=%%~nf"
    set "expected_file=tests\!base!.out"
    set "output_c=tests\build\!base!.c"
    set "output_bin=tests\build\!base!.exe"
    set "actual_out=tests\build\!base!.actual"

    <nul set /p="Test !base!: "

    call gradlew.bat run --args="../!bolt_file! ../!output_c!" > "tests\build\!base!_transpile.log" 2>&1
    if errorlevel 1 (
        echo FAILED ^(Transpilation^)
    ) else (
        gcc "!output_c!" -o "!output_bin!" > "tests\build\!base!_compile.log" 2>&1
        if errorlevel 1 (
            echo FAILED ^(C Compilation^)
        ) else (
            "!output_bin!" > "!actual_out!"

            fc "!actual_out!" "!expected_file!" > nul
            if errorlevel 1 (
                echo FAILED ^(Output Mismatch^)
                echo Expected:
                type "!expected_file!"
                echo Actual:
                type "!actual_out!"
            ) else (
                echo PASSED
                set /a PASSED+=1
            )
        )
    )
)

echo --------------------------
echo Summary: !PASSED!/!TOTAL! tests passed.

if "!PASSED!"=="!TOTAL!" (
    exit /b 0
) else (
    exit /b 1
)
