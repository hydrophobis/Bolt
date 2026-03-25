@echo off
cd /d "%~dp0"
echo y | vsce package --no-dependencies
