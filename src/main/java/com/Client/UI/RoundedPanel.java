package com.Client.UI;

import javax.swing.*;
import java.awt.*;

class RoundedPanel extends JPanel {
    private final int cornerRadius;

    public RoundedPanel(int radius) {
        super();
        cornerRadius = radius;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Dimension arcs = new Dimension(cornerRadius, cornerRadius);
        int width = getWidth();
        int height = getHeight();
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw the rounded panel with background color
        g2d.setColor(getBackground());
        g2d.fillRoundRect(0, 0, width - 1, height - 1, arcs.width, arcs.height);
    }
}