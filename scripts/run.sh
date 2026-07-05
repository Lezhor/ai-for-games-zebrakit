#!/bin/bash

function print_help {
    echo "Usage: $0 [options] <strategyName>"
    echo ""
    echo "Arguments:"
    echo "  strategyName    The strategy to run (e.g. TerritoryPaint). Bot name defaults to this if --name is omitted."
    echo ""
    echo "Options:"
    echo "  --name <name>   The name of the bot to register with the server (default: the strategy name)"
    echo "  --host <host>   The server host (default: 127.0.0.1)"
    echo "  --port <port>   The server port (currently unused by java backend, default: 22135)"
    echo "  --nav <name>    Override the movement/navigator this strategy uses (see --list-strategies for choices)"
    echo "  --help          Print this help message"
    echo ""
    echo "Available strategies and navigators (asking the CLI directly, so this never drifts out of date):"
    (cd "$DIR/.." && gradle run -q --args="--help")
}

BOT_NAME=""
HOST=""
PORT=""
NAV=""
STRATEGY_NAME=""

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

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
    --nav)
      NAV="$2"
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
      if [ -z "$STRATEGY_NAME" ]; then
        STRATEGY_NAME="$1"
        shift
      else
        echo "Error: Multiple strategy names provided ($STRATEGY_NAME and $1)"
        print_help
        exit 1
      fi
      ;;
  esac
done

if [ -z "$STRATEGY_NAME" ]; then
    echo "Error: Strategy name is required."
    print_help
    exit 1
fi

# Build the args array; omit flags the user didn't pass so Main applies its own defaults
# (in particular, bot name defaults to the strategy name when --name is omitted).
ARGS=("$STRATEGY_NAME")
if [ -n "$BOT_NAME" ]; then ARGS+=(--name "$BOT_NAME"); fi
if [ -n "$HOST" ]; then ARGS+=(--host "$HOST"); fi
if [ -n "$PORT" ]; then ARGS+=(--port "$PORT"); fi
if [ -n "$NAV" ]; then ARGS+=(--nav "$NAV"); fi

cd "$DIR/.." || exit
gradle run -q --args="${ARGS[*]}"
