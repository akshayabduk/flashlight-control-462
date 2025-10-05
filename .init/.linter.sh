#!/bin/bash
cd /home/kavia/workspace/code-generation/flashlight-control-462/flashlight_frontend
./gradlew lint
LINT_EXIT_CODE=$?
if [ $LINT_EXIT_CODE -ne 0 ]; then
   exit 1
fi

