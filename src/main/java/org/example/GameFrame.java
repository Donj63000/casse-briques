package org.example;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

public final class GameFrame extends JFrame {
    public GameFrame() {
        setTitle("ISSOU THE BEST");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);

        GamePanel panel = new GamePanel();
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        panel.requestFocusInWindow();
    }
}
