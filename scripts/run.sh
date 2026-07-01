#!/bin/bash

function print_help {
    echo "Usage: $0 [options] <agentName>"
    echo ""
    echo "Arguments:"
    echo "  agentName       The name of the agent strategy to run (e.g., RandomAgent)"
    echo ""
    echo "Options:"
    echo "  --name <name>   The name of the bot to register with the server (default: myBot)"
    echo "  --host <host>   The server host (default: 127.0.0.1)"
    echo "  --port <port>   The server port (currently unused by java backend, default: 22135)"
    echo "  --help          Print this help message"
    echo ""
    echo "Available Strategies:"
    find src/lezhor/htw/zebrakit/agents -name "*.java" 2>/dev/null | sed 's|.*/||;s|\.java||' | while read agent; do
        echo "  - $agent"
    done
}

BOT_NAME="myBot"
HOST="127.0.0.1"
PORT="22135"
AGENT_NAME=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --name)
      BOT_NAME="$2"
      shift 2
      ;;
    --host)
      HOST="$2"
      shift 2
      ;;
    --port)
      PORT="$2"
      shift 2
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
      if [ -z "$AGENT_NAME" ]; then
        AGENT_NAME="$1"
        shift
      else
        echo "Error: Multiple agent names provided ($AGENT_NAME and $1)"
        print_help
        exit 1
      fi
      ;;
  esac
done

if [ -z "$AGENT_NAME" ]; then
    echo "Error: Agent name is required."
    print_help
    exit 1
fi

gradle run -q -Pagent="$AGENT_NAME" --args="$BOT_NAME $HOST $PORT"
