package lezhor.htw.zebrakit.agents;

import lenz.htw.zebrakit.net.NetworkClient;
import lenz.htw.zebrakit.Update;
import lenz.htw.zebrakit.PowerupType;
import java.util.Random;

public class RandomAgent {

    public static void main(String[] args) {
        // Strategy: Change on hit
        String name = args.length > 0 ? args[0] : "Trivial";
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        NetworkClient client = new NetworkClient(host, name, "Trivial Win!");

        int myPlayerNumber = client.getMyPlayerNumber();
        Random rand = new Random();

        float[] dx = new float[3];
        float[] dy = new float[3];

        for (int i = 0; i < 3; i++) {
            dx[i] = rand.nextFloat() * 2 - 1; // Random between -1.0 and 1.0
            dy[i] = rand.nextFloat() * 2 - 1;
            client.setMoveDirection(i, dx[i], dy[i]);
        }

        while (client.isAlive()) {
            Update update;
            while ((update = client.pullNextUpdate()) != null) {
                if (update.player == myPlayerNumber && update.bot >= 0 && update.bot < 3) {
                    int bot = update.bot;
                    int x = update.x;
                    int y = update.y;

                    // Normalize the current direction vector
                    float len = (float) Math.sqrt(dx[bot] * dx[bot] + dy[bot] * dy[bot]);
                    if (len == 0) {
                        dx[bot] = 1; 
                        len = 1;
                    }
                    float nx = dx[bot] / len;
                    float ny = dy[bot] / len;

                    boolean willHit = false;
                    for (int step = 5; step <= 25; step += 5) {
                        int checkX = x + (int) (nx * step);
                        int checkY = y + (int) (ny * step);
                        
                        if (!client.isWalkable(checkX, checkY)) {
                            willHit = true;
                            break;
                        }
                    }

                    // If we are about to hit an obstacle, change to a new random direction
                    if (willHit) {
                        dx[bot] = rand.nextFloat() * 2 - 1;
                        dy[bot] = rand.nextFloat() * 2 - 1;
                        client.setMoveDirection(bot, dx[bot], dy[bot]);
                    }
                }
            }
            
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
