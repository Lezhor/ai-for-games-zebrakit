package lezhor.htw.zebrakit.runtime;

import lenz.htw.zebrakit.net.NetworkClient;
import lezhor.htw.zebrakit.cli.ArgParser;
import lezhor.htw.zebrakit.cli.RunConfig;
import lezhor.htw.zebrakit.cli.StrategyFactory;
import lezhor.htw.zebrakit.core.BotContext;
import lezhor.htw.zebrakit.core.BotRoles;
import lezhor.htw.zebrakit.core.GameState;
import lezhor.htw.zebrakit.strategy.Strategy;

/** Single entry point for any strategy: parse CLI args, connect, wire up, run. */
public class Main {
    public static void main(String[] args) {
        if (ArgParser.isHelpRequested(args)) {
            ArgParser.printHelp();
            return;
        }
        if (ArgParser.isListRequested(args)) {
            ArgParser.printAvailable();
            return;
        }

        RunConfig config = ArgParser.parse(args);

        NetworkClient client = new NetworkClient(config.host(), config.botName(), config.winPhrase());
        GameState state = new GameState(client);
        BotContext[] bots = BotRoles.buildContexts(client);
        Strategy strategy = StrategyFactory.create(config.strategyName(), client, config.navOverride(),
                config.matchLengthSeconds(), config.strategyConfigPath());

        new GameLoop(client, state, bots, strategy, 10).run();
    }
}
