@echo off
cd /d d:\skills exchange
findstr "firebase-auth:24.1.0" deps.txt > firebase_lines.txt 2>&1
type firebase_lines.txt
