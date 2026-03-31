@echo off
setlocal enabledelayedexpansion

if exist tests\build\ del /s /q tests\build\*.log > nul 2>&1

echo Running Bolt Test Suite...
echo --------------------------

if not exist tests\build mkdir tests\build

set PASSED=0
set TOTAL=0

for %%f in (tests\*.bolt) do (
    set /a TOTAL+=1

    set "bolt_file=%%f"
    set "base=%%~nf"

    set "pkg="
    for /f "tokens=2 delims=; " %%P in ('findstr /b "package " "%%f" 2^>nul') do (
        set "pkg=%%P"
    )

    if defined pkg (
        set "pkgdir=!pkg:.=\!"
    ) else (
        set "pkgdir=!base!"
    )

    set "output_dir=tests\build\!pkgdir!"
    if not exist "!output_dir!" mkdir "!output_dir!"

    set "expected_file=tests\!base!.out"
    set "output_c=!output_dir!\!base!.c"
    set "output_bin=!output_dir!\!base!.exe"
    set "actual_out=!output_dir!\!base!.actual"


    <nul set /p="Test !base!: "

    set "dep_objs="
    for /f "tokens=2 delims=; " %%I in ('findstr /b "import " "%%f" 2^>nul') do (
        set "imp=%%I"
        if not "!imp:~0,4!"=="std." (
            set "dep_path=!imp:.=\!"
            set "dep_bolt=tests\!dep_path!.bolt"
            if exist "!dep_bolt!" (
                set "dep_output=tests\build\!dep_path!.c"
                for %%A in ("!dep_output!") do set "dep_output_dir=%%~dpA"
                if not exist "!dep_output_dir!" mkdir "!dep_output_dir!"
                
                call gradlew.bat run --quiet --args="../!dep_bolt! ../!dep_output!" > "!output_dir!\!base!_import_!imp:.=_!_transpile.log" 2>&1
                if not errorlevel 1 (
                    clang -I tests\build "!dep_output!" -c -o "tests\build\!dep_path!.o" > "!output_dir!\!base!_import_!imp:.=_!_compile.log" 2>&1
                    if not errorlevel 1 (
                        set "dep_objs=!dep_objs! tests\build\!dep_path!.o"
                    )
                )
            )
        )
    )

    call gradlew.bat run --quiet --args="../!bolt_file! ../!output_c!" > "!output_dir!\!base!_transpile.log" 2>&1
    if errorlevel 1 (
        echo FAILED ^(Transpilation^)
    ) else (
        clang -I tests\build "!output_c!" !dep_objs! -o "!output_bin!" > "!output_dir!\!base!_compile.log" 2>&1
        if errorlevel 1 (
            echo FAILED ^(C Compilation^)
        ) else (
            "!output_bin!" > "!actual_out!"

            if not exist "!expected_file!" (
                for %%F in ("!actual_out!") do set size=%%~zF
                if !size! gtr 0 (
                    echo FAILED ^(Unexpected output^)
                    echo Actual:
                    type "!actual_out!"
                ) else (
                    echo PASSED
                    set /a PASSED+=1
                )
            ) else (
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
)

echo --------------------------
echo Summary: !PASSED!/!TOTAL! tests passed.

if "!PASSED!"=="!TOTAL!" (
    exit /b 0
) else (
    exit /b 1
)
