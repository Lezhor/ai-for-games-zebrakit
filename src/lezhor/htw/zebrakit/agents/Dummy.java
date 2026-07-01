package lezhor.htw.zebrakit.agents;

import lenz.htw.zebrakit.net.NetworkClient;
import lenz.htw.zebrakit.Update;
import lenz.htw.zebrakit.PowerupType;

public class Dummy {

    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "unnamed player";
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        NetworkClient client = new NetworkClient(host, name, "yeah!");

        client.getMyPlayerNumber();
        client.getScore(0);

        client.getInfluenceRadiusForBot(0);

        while (client.isAlive()) {
            client.setMoveDirection(0, 0, 1);
            client.getBoard(0, 0);
            client.isWalkable(0, 0);
            Update update;
            while ((update = client.pullNextUpdate()) != null) {
                PowerupType type = update.type;
                // auswerten
            }
        }
    }
}
