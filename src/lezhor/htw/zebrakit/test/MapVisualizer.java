package lezhor.htw.zebrakit.test;

import lenz.htw.zebrakit.e;
import lezhor.htw.zebrakit.nav.Navigator;
import lezhor.htw.zebrakit.nav.ThetaStarNavigator;
import lezhor.htw.zebrakit.nav.NavMeshNavigator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

public class MapVisualizer extends JPanel {
    // 100% AI generated just to test my pathfinding & pathfinding speed :)
    private static final int MAP_SIZE = 1024;

    private final BufferedImage mapImage;
    private final Navigator thetaStarNavigator;
    private final Navigator navMeshNavigator;

    private Navigator currentNavigator;

    private Point startPoint = null;
    private Point endPoint = null;
    private List<Point> currentPath = null;
    private long calculationTimeMs = 0;

    public MapVisualizer(long seed) {
        setPreferredSize(new Dimension(MAP_SIZE, MAP_SIZE));

        System.out.println("Initializing map with seed: " + seed);
        e board = new e(seed);

        mapImage = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < MAP_SIZE; x++) {
            for (int y = 0; y < MAP_SIZE; y++) {
                if (board.a(x, y) == 0) {
                    mapImage.setRGB(x, y, 0x000000); // Obstacle (Black)
                } else {
                    mapImage.setRGB(x, y, 0xFFFFFF); // Walkable (White)
                }
            }
        }

        System.out.println("Initializing navigators...");
        thetaStarNavigator = new ThetaStarNavigator(8, 4); // 8px inflation, scaled down to 512x512
        thetaStarNavigator.initialize(seed);

        navMeshNavigator = new NavMeshNavigator();
        navMeshNavigator.initialize(seed);

        currentNavigator = thetaStarNavigator;
        System.out.println("Initialization complete!");

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent evt) {
                if (SwingUtilities.isLeftMouseButton(evt)) {
                    startPoint = evt.getPoint();
                } else if (SwingUtilities.isRightMouseButton(evt)) {
                    endPoint = evt.getPoint();
                }
                recalculatePath();
            }
        });
    }

    private void recalculatePath() {
        if (startPoint != null && endPoint != null) {
            long startTime = System.nanoTime();
            currentPath = currentNavigator.findPath(startPoint, endPoint);
            long endTime = System.nanoTime();
            calculationTimeMs = (endTime - startTime) / 1000000;
        } else {
            currentPath = null;
        }
        repaint();
    }

    public void setNavigator(boolean useThetaStar) {
        currentNavigator = useThetaStar ? thetaStarNavigator : navMeshNavigator;
        recalculatePath();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(mapImage, 0, 0, null);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (currentPath != null && !currentPath.isEmpty()) {
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(3));
            for (int i = 0; i < currentPath.size() - 1; i++) {
                Point p1 = currentPath.get(i);
                Point p2 = currentPath.get(i + 1);
                g2.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
        }

        if (startPoint != null) {
            g2.setColor(Color.GREEN);
            g2.fillOval(startPoint.x - 5, startPoint.y - 5, 10, 10);
            g2.setColor(Color.BLACK);
            g2.drawString("A", startPoint.x - 3, startPoint.y + 4);
        }

        if (endPoint != null) {
            g2.setColor(Color.BLUE);
            g2.fillOval(endPoint.x - 5, endPoint.y - 5, 10, 10);
            g2.setColor(Color.WHITE);
            g2.drawString("B", endPoint.x - 3, endPoint.y + 4);
        }

        // Draw info text
        g2.setColor(Color.MAGENTA);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        String navName = (currentNavigator == thetaStarNavigator) ? "Theta* (Grid)" : "NavMesh (Stub)";
        g2.drawString("Navigator: " + navName, 10, 20);
        if (startPoint != null && endPoint != null) {
            if (currentPath != null) {
                g2.drawString("Status: Path Found in " + calculationTimeMs + " ms", 10, 40);
            } else {
                g2.drawString("Status: No Path (Unreachable) in " + calculationTimeMs + " ms", 10, 40);
            }
        } else {
            g2.drawString("Left click to set Start, Right click to set End.", 10, 40);
        }
    }

    public static void main(String[] args) {
        long seed = (args.length > 0) ? Long.parseLong(args[0]) : 42L;

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Zebrakit Map Visualizer - Seed: " + seed);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            MapVisualizer visualizer = new MapVisualizer(seed);
            frame.add(visualizer, BorderLayout.CENTER);

            JPanel controlPanel = new JPanel();
            JRadioButton thetaRadio = new JRadioButton("Theta* Navigator", true);
            JRadioButton navMeshRadio = new JRadioButton("NavMesh Navigator", false);

            ButtonGroup group = new ButtonGroup();
            group.add(thetaRadio);
            group.add(navMeshRadio);

            thetaRadio.addActionListener(e -> visualizer.setNavigator(true));
            navMeshRadio.addActionListener(e -> visualizer.setNavigator(false));

            controlPanel.add(thetaRadio);
            controlPanel.add(navMeshRadio);

            frame.add(controlPanel, BorderLayout.NORTH);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
