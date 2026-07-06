## Zebrakit Agent

A Java client for **Zebrakit**, a 3-player real-time territory-painting game: each player controls 3 bots that move around a shared board and paint it in their color, and whoever holds the most board value when time runs out wins.

## Prerequisites

- JDK 17+ (tested on 21/26)
- Gradle installed globally — no wrapper is checked in, so `gradle` must be on your `PATH`

## Setup

```bash
git clone <this-repo>
cd ai-zebra
gradle build
```
`server/zebrakit.jar` (the vendor game server) is already vendored — nothing else to fetch.

## Running a match

### 1. Start the server
```bash
./scripts/server.sh
```
Blocks until 3 clients connect, then opens the game window — click it and press **space** to start the match. Flags: `--width`, `--height`, `--time` (match length, default 60s), `--seed`, `--loop` (auto-restart), `--help`. The board itself is always a fixed 1024×1024 grid regardless of window size.

### 2. Connect bots
Single bot:
```bash
./scripts/run.sh <strategyName> [--name <botName>] [--host <host>] [--port <port>] [--nav <navName>]
```
All 3 at once:
```bash
./scripts/match.sh <strategy1> [strategy2] [strategy3] [--nav <nav1[,nav2,nav3]>] [--host <host>] [--port <port>]
```
Fewer than 3 strategies fills the rest by repeating the last one; `--nav` follows the same rule. `--name` defaults to the strategy name. Run `--help` on either script (or `gradle run -q --args="--list"`) for the live list of registered strategies/navigators.

Example:
```bash
./scripts/server.sh &
./scripts/match.sh TerritoryPaint PowerupHunt RandomWalk
# click the server window, press space
```

## Game rules

**Board & scoring** — 1024×1024 grid, walls outside a "flower"-shaped playable area. Each cell holds an RGB-style triplet, one channel per player, starting neutral at 255/255/255. Your score is the sum of your channel over the whole board.

Painting a cell raises your channel (capped at 255) and lowers each other channel by the same amount, but only down to a floor of 0 — a channel that's already 0 there simply can't drop further. So painting a neutral (white) cell drains both opponents equally (both start at 255) but doesn't grow your score (you're already capped); painting a cell fully claimed by one opponent only drains that opponent, since the third channel there is already 0 — and it also grows your score if you weren't already capped there.

**Bots** — each player has 3 bots with fixed, index-based roles:

| Bot | Speed | Paint radius | Notes |
|---|---|---|---|
| 0 | 6.3 | 40 | fast, wide, weak paint |
| 1 | 3.0 | 15 | slow, narrow, strong paint |
| 2 | 2.0 | 15–30 | slowest, medium |

Each bot paints a soft brush centered on itself every tick (strongest at center, fading to the edge). Movement is direction-only at a fixed per-bot speed; zero vector = stop. Walls block movement and can't be painted.

**Powerups** — 6 spawn per match (2 each, random order/timing/location); picked up automatically within ~20px:
- **BOMB** — one large splash (~200px) at pickup location, in your color.
- **RAIN** — ~35 small splashes scattered randomly across the whole board, in your color.
- **SLOW** — halves your *entire team's* speed for ~6s. Only hurts whoever grabs it — avoid it, don't chase it.

**Match end** — ends at the time limit or when all 3 players disconnect; highest score wins, exact ties have no winner.

## Code structure

Under `src/lezhor/htw/zebrakit/`: `core` (domain model — `GameState`, `BotContext`/`BotRoles`, `Vector2`), `movement` (`MovementProvider` abstraction: `nav` for pathfinding, `steering` for steering behaviors, plus `HybridMovementProvider`), `analysis` (shared scoring/sensing: `InfluenceMap`, `TerritoryUtils`, `OpponentWeights`), `strategy` (one `Strategy` per player controlling all 3 bots — `Idle`, `Dummy`, `RandomWalk`, `RandomTarget`, `PowerupHunt`, `TerritoryPaint`), `cli` (name-based registries for runtime strategy/navigator selection), `runtime` (`Main` entry point, shared `GameLoop`). `agents/Dummy.java` is the original course-provided reference, kept untouched.

Parts of this README.md were coassisted with AI.
