package org.example;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.Rectangle;

public final class GameFrame extends JFrame {
    private boolean fullscreen;
    private Rectangle windowBounds;
    private int windowState;

    public GameFrame() {
        setTitle("ISSOU THE BEST");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);

        GamePanel panel = new GamePanel(this);
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        windowBounds = getBounds();
        windowState = getExtendedState();
        setVisible(true);
        panel.requestFocusInWindow();
    }

    public void toggleFullscreen() {
        if (!fullscreen) {
            windowBounds = getBounds();
            windowState = getExtendedState();
            setResizable(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            fullscreen = true;
        } else {
            setExtendedState(windowState);
            if (windowBounds != null) {
                setBounds(windowBounds);
            }
            setResizable(false);
            fullscreen = false;
        }
    }
}
