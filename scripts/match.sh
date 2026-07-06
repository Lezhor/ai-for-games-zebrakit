#!/bin/bash

function print_help {
    echo "Usage: $0 [options] <strategy1> [strategy2] [strategy3]"
    echo ""
    echo "Arguments:"
    echo "  strategy1, strategy2, strategy3    The strategies to play. If fewer than 3 are"
    echo "                                     provided, the last provided strategy fills the remaining slots."
    echo ""
    echo "Options:"
    echo "  --host <host>             The server host (default: 127.0.0.1)"
    echo "  --port <port>             The server port (default: 22135)"
    echo "  --nav <nav1[,nav2,nav3]>  Override the navigator each strategy uses. Same fill-forward rule as strategies:"
    echo "                            one name applies to all three, or give up to three comma-separated."
    echo "  --time <secs>             Assumed match length for time-aware strategies (default: 60)"
    echo "  --help                    Print this help message"
    echo ""
    # Ask the CLI directly instead of guessing, so this listing can't drift out of date.
    (cd "$DIR/.." && gradle run -q --args="--list")
}

HOST="127.0.0.1"
PORT="22135"
NAV=""
TIME=""
STRATEGIES=()

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

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
    --nav)
      NAV="$2"
      shift 2
      ;;
    --time)
      TIME="$2"
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
      STRATEGIES+=("$1")
      shift
      ;;
  esac
done

if [ ${#STRATEGIES[@]} -eq 0 ]; then
    echo "Error: At least one strategy name is required."
    print_help
    exit 1
fi

# Extrapolate strategies
STRATEGY1="${STRATEGIES[0]}"
STRATEGY2="${STRATEGIES[1]:-$STRATEGY1}"
STRATEGY3="${STRATEGIES[2]:-$STRATEGY2}"

# Extrapolate navigator overrides the same way, from a comma-separated --nav value (if given at all)
IFS=',' read -r -a NAVS <<< "$NAV"
NAV1="${NAVS[0]}"
NAV2="${NAVS[1]:-$NAV1}"
NAV3="${NAVS[2]:-$NAV2}"

# Dynamic naming: lowercasing the strategy name (bot name otherwise defaults to it as-is)
function get_bot_name {
    local bot_num=$1
    local strategy=$2
    local strategy_name=$(echo "$strategy" | tr '[:upper:]' '[:lower:]')
    echo "bot${bot_num}-${strategy_name}"
}

NAME1=$(get_bot_name 1 "$STRATEGY1")
NAME2=$(get_bot_name 2 "$STRATEGY2")
NAME3=$(get_bot_name 3 "$STRATEGY3")

echo "Starting Match: $STRATEGY1 vs $STRATEGY2 vs $STRATEGY3"
echo "Host: $HOST:$PORT"

# Launch 3 clients in parallel
NAV1_ARGS=(); if [ -n "$NAV1" ]; then NAV1_ARGS=(--nav "$NAV1"); fi
NAV2_ARGS=(); if [ -n "$NAV2" ]; then NAV2_ARGS=(--nav "$NAV2"); fi
NAV3_ARGS=(); if [ -n "$NAV3" ]; then NAV3_ARGS=(--nav "$NAV3"); fi

# One match length applies to all three players.
TIME_ARGS=(); if [ -n "$TIME" ]; then TIME_ARGS=(--time "$TIME"); fi

"$DIR/run.sh" --name "$NAME1" --host "$HOST" --port "$PORT" "${NAV1_ARGS[@]}" "${TIME_ARGS[@]}" "$STRATEGY1" &
PID1=$!

"$DIR/run.sh" --name "$NAME2" --host "$HOST" --port "$PORT" "${NAV2_ARGS[@]}" "${TIME_ARGS[@]}" "$STRATEGY2" &
PID2=$!

"$DIR/run.sh" --name "$NAME3" --host "$HOST" --port "$PORT" "${NAV3_ARGS[@]}" "${TIME_ARGS[@]}" "$STRATEGY3" &
PID3=$!

echo "Clients launched. Waiting for them to finish..."
wait $PID1 $PID2 $PID3
echo "Match finished."
