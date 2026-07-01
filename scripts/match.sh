#!/bin/bash

function print_help {
    echo "Usage: $0 [options] <agent1> [agent2] [agent3]"
    echo ""
    echo "Arguments:"
    echo "  agent1, agent2, agent3    The agent strategies to play. If fewer than 3 are"
    echo "                            provided, the last provided strategy fills the remaining slots."
    echo ""
    echo "Options:"
    echo "  --host <host>             The server host (default: 127.0.0.1)"
    echo "  --port <port>             The server port (default: 22135)"
    echo "  --help                    Print this help message"
    echo ""
    echo "Available Strategies:"
    find src/lezhor/htw/zebrakit/agents -name "*.java" 2>/dev/null | sed 's|.*/||;s|\.java||' | while read agent; do
        echo "  - $agent"
    done
}

HOST="127.0.0.1"
PORT="22135"
AGENTS=()

while [[ $# -gt 0 ]]; do
  case $1 in
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
      AGENTS+=("$1")
      shift
      ;;
  esac
done

if [ ${#AGENTS[@]} -eq 0 ]; then
    echo "Error: At least one agent name is required."
    print_help
    exit 1
fi

# Extrapolate agents
AGENT1="${AGENTS[0]}"
AGENT2="${AGENTS[1]:-$AGENT1}"
AGENT3="${AGENTS[2]:-$AGENT2}"

# Dynamic naming: lowercasing the strategy names and removing "Agent"
function get_bot_name {
    local bot_num=$1
    local strategy=$2
    local strategy_name=$(echo "$strategy" | sed -E 's/Agent$//i' | tr '[:upper:]' '[:lower:]')
    echo "bot${bot_num}-${strategy_name}"
}

NAME1=$(get_bot_name 1 "$AGENT1")
NAME2=$(get_bot_name 2 "$AGENT2")
NAME3=$(get_bot_name 3 "$AGENT3")

echo "Starting Match: $AGENT1 vs $AGENT2 vs $AGENT3"
echo "Host: $HOST:$PORT"

# Get absolute path to script directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Launch 3 clients in parallel
"$DIR/run.sh" --name "$NAME1" --host "$HOST" --port "$PORT" "$AGENT1" &
PID1=$!

"$DIR/run.sh" --name "$NAME2" --host "$HOST" --port "$PORT" "$AGENT2" &
PID2=$!

"$DIR/run.sh" --name "$NAME3" --host "$HOST" --port "$PORT" "$AGENT3" &
PID3=$!

echo "Clients launched. Waiting for them to finish..."
wait $PID1 $PID2 $PID3
echo "Match finished."
