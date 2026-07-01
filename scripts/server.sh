#!/bin/bash

function print_help {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  --width <w>     Window width"
    echo "  --height <h>    Window height"
    echo "  --time <t>      Play time in seconds"
    echo "  --seed <s>      Random seed (if omitted, server uses a random seed)"
    echo "  --loop          Run the server in an infinite loop"
    echo "  --help          Print this help message"
    echo ""
    echo "Press Ctrl+C at any time to exit."
}

WIDTH=""
HEIGHT=""
TIME=""
SEED=""
LOOP=0

while [[ $# -gt 0 ]]; do
  case $1 in
    --width)
      WIDTH="$2"
      shift 2
      ;;
    --height)
      HEIGHT="$2"
      shift 2
      ;;
    --time)
      TIME="$2"
      shift 2
      ;;
    --seed)
      SEED="$2"
      shift 2
      ;;
    --loop)
      LOOP=1
      shift
      ;;
    --help)
      print_help
      exit 0
      ;;
    -*)
      echo "Unknown option $1"
      print_help
      exit 1
      ;;
    *)
      echo "Unknown argument $1"
      print_help
      exit 1
      ;;
  esac
done

# Trap Ctrl+C (SIGINT) so that it gracefully exits the entire script
trap "echo -e '\nStopping server script...'; exit 0" SIGINT

# Build arguments array with key=value format for the server
ARGS=()
if [ -n "$WIDTH" ]; then
    ARGS+=("w=$WIDTH")
fi
if [ -n "$HEIGHT" ]; then
    ARGS+=("h=$HEIGHT")
fi
if [ -n "$TIME" ]; then
    ARGS+=("time=$TIME")
fi
if [ -n "$SEED" ]; then
    ARGS+=("seed=$SEED")
fi

# Ensure we are running from the project root
cd "$(dirname "$0")/.." || exit

if [ "$LOOP" -eq 1 ]; then
    echo "Starting server loop... Press Ctrl+C to stop."
    while true; do
        echo "----------------------------------------"
        echo "Starting Server Instance"
        echo "----------------------------------------"

        java -jar server/zebrakit.jar "${ARGS[@]}"

        echo "Server instance exited."
        echo "Restarting in 1 seconds..."
        sleep 1
    done
else
    java -jar server/zebrakit.jar "${ARGS[@]}"
fi
