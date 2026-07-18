package supermidia.midi.player;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

public final class GMTheme {
    public static final Color APP_BG = new Color(11, 13, 17);
    public static final Color SURFACE = new Color(24, 28, 36);
    public static final Color SURFACE_2 = new Color(31, 36, 46);
    public static final Color TRACK = new Color(50, 57, 70);
    public static final Color TEXT = new Color(242, 246, 250);
    public static final Color MUTED = new Color(150, 158, 170);
    public static final Color ACCENT = new Color(76, 201, 240);
    public static final Color GREEN = new Color(67, 214, 133);
    public static final Color AMBER = new Color(245, 190, 72);
    public static final Color RED = new Color(232, 91, 91);
    public static final Color BLUE = new Color(92, 141, 255);

    private static final int RADIUS = 8;

    private GMTheme() {
    }

    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The custom components still carry the app's visual style.
        }

        UIManager.put("ToolTip.background", SURFACE_2);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(TRACK));
    }

    public static JPanel rootPanel(LayoutManager layout, int top, int left, int bottom, int right) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(APP_BG);
        panel.setBorder(new EmptyBorder(top, left, bottom, right));
        return panel;
    }

    public static SurfacePanel surfacePanel(LayoutManager layout) {
        SurfacePanel panel = new SurfacePanel(layout);
        panel.setBorder(new EmptyBorder(16, 18, 16, 18));
        return panel;
    }

    public static JLabel eyebrow(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(ACCENT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

    public static JLabel title(String text, float size) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, size));
        return label;
    }

    public static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        return label;
    }

    public static BadgeLabel badge(String text) {
        return new BadgeLabel(text);
    }

    public static JButton iconButton(IconType type, String tooltip, int size, Color background) {
        StyledButton button = new StyledButton("", type, size, background, true);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(size, size));
        button.setMinimumSize(new Dimension(size, size));
        button.setMaximumSize(new Dimension(size, size));
        return button;
    }

    public static JButton actionButton(String text, IconType type, String tooltip) {
        StyledButton button = new StyledButton(text, type, 38, SURFACE_2, false);
        button.setToolTipText(tooltip);
        button.setBorder(new EmptyBorder(8, 12, 8, 12));
        return button;
    }

    public static JButton textButton(String text, String tooltip) {
        StyledButton button = new StyledButton(text, null, 38, SURFACE_2, false);
        button.setToolTipText(tooltip);
        button.setBorder(new EmptyBorder(8, 12, 8, 12));
        return button;
    }

    public static JToggleButton toggleButton(String text, Color selectedColor, String tooltip) {
        ToggleButton button = new ToggleButton(text, selectedColor);
        button.setToolTipText(tooltip);
        return button;
    }

    public static void styleSlider(JSlider slider) {
        slider.setOpaque(false);
        slider.setUI(new ModernSliderUI(slider));
    }

    public static Icon icon(IconType type, int size, Color color) {
        return new VectorIcon(type, size, color);
    }

    public enum IconType {
        PLAY, PAUSE, STOP, FOLDER, LYRICS, MIXER
    }

    public static class SurfacePanel extends JPanel {
        public SurfacePanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SURFACE);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS, RADIUS));
            g.setColor(new Color(48, 55, 68));
            g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, RADIUS, RADIUS));
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    public static class BadgeLabel extends JLabel {
        public BadgeLabel(String text) {
            super(text);
            setOpaque(false);
            setForeground(TEXT);
            setFont(getFont().deriveFont(Font.BOLD, 12f));
            setBorder(new EmptyBorder(5, 10, 5, 10));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(37, 43, 54));
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS, RADIUS));
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    public static class ActivityLight extends JComponent {
        private boolean active = false;

        public ActivityLight() {
            setPreferredSize(new Dimension(32, 18));
            setMinimumSize(new Dimension(32, 18));
        }

        public void setActive(boolean active) {
            if (this.active != active) {
                this.active = active;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int diameter = 12;
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;
            g.setColor(active ? new Color(37, 90, 56) : new Color(45, 50, 61));
            g.fill(new Ellipse2D.Float(x - 3, y - 3, diameter + 6, diameter + 6));
            g.setColor(active ? GREEN : new Color(94, 101, 114));
            g.fill(new Ellipse2D.Float(x, y, diameter, diameter));
            g.dispose();
        }
    }

    private static class StyledButton extends JButton {
        private final int radius;
        private final Color base;
        private final boolean circular;

        StyledButton(String text, IconType type, int size, Color base, boolean circular) {
            super(text);
            this.base = base;
            this.circular = circular;
            this.radius = circular ? size : RADIUS;
            if (type != null) {
                setIcon(icon(type, Math.max(16, size / 2), TEXT));
                setDisabledIcon(icon(type, Math.max(16, size / 2), new Color(102, 110, 123)));
            }
            setForeground(TEXT);
            setFont(getFont().deriveFont(Font.BOLD, 13f));
            setHorizontalTextPosition(SwingConstants.RIGHT);
            setIconTextGap(text.isEmpty() ? 0 : 8);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel model = getModel();
            Color color = isEnabled() ? base : new Color(31, 35, 43);
            if (isEnabled() && model.isPressed()) {
                color = color.darker();
            } else if (isEnabled() && model.isRollover()) {
                color = blend(color, Color.WHITE, 0.08f);
            }
            g.setColor(color);
            if (circular) {
                int size = Math.min(getWidth(), getHeight());
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                g.fillOval(x, y, size, size);
            } else {
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static class ToggleButton extends JToggleButton {
        private final Color selectedColor;

        ToggleButton(String text, Color selectedColor) {
            super(text);
            this.selectedColor = selectedColor;
            setForeground(TEXT);
            setFont(getFont().deriveFont(Font.BOLD, 12f));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(38, 28));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = isSelected() ? selectedColor : SURFACE_2;
            if (!isEnabled()) {
                color = new Color(31, 35, 43);
            } else if (getModel().isRollover()) {
                color = blend(color, Color.WHITE, 0.08f);
            }
            g.setColor(color);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS, RADIUS));
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static class ModernSliderUI extends BasicSliderUI {
        ModernSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(18, 18);
        }

        @Override
        public void paintTrack(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = trackRect.y + (trackRect.height - 8) / 2;
            int x = trackRect.x;
            int width = trackRect.width;
            g.setColor(TRACK);
            g.fill(new RoundRectangle2D.Float(x, y, width, 8, 8, 8));

            double pct = (slider.getValue() - slider.getMinimum())
                    / (double) Math.max(1, slider.getMaximum() - slider.getMinimum());
            int fillWidth = (int) Math.round(width * pct);
            g.setColor(slider.isEnabled() ? ACCENT : new Color(89, 96, 109));
            g.fill(new RoundRectangle2D.Float(x, y, fillWidth, 8, 8, 8));
            g.dispose();
        }

        @Override
        public void paintThumb(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color outer = slider.isEnabled() ? ACCENT : new Color(94, 101, 114);
            g.setColor(outer);
            g.fillOval(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height);
            g.setColor(TEXT);
            g.fillOval(thumbRect.x + 5, thumbRect.y + 5, thumbRect.width - 10, thumbRect.height - 10);
            g.dispose();
        }

        @Override
        public void paintFocus(Graphics graphics) {
            // The thumb and track provide the focus cue for this compact control.
        }
    }

    private static class VectorIcon implements Icon {
        private final IconType type;
        private final int size;
        private final Color color;

        VectorIcon(IconType type, int size, Color color) {
            this.type = type;
            this.size = size;
            this.color = color;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.translate(x, y);
            float s = size;

            switch (type) {
                case PLAY -> {
                    Polygon triangle = new Polygon();
                    triangle.addPoint(Math.round(s * 0.36f), Math.round(s * 0.24f));
                    triangle.addPoint(Math.round(s * 0.36f), Math.round(s * 0.76f));
                    triangle.addPoint(Math.round(s * 0.78f), Math.round(s * 0.50f));
                    g.fillPolygon(triangle);
                }
                case PAUSE -> {
                    g.fillRoundRect(Math.round(s * 0.30f), Math.round(s * 0.24f), Math.round(s * 0.14f),
                            Math.round(s * 0.52f), 3, 3);
                    g.fillRoundRect(Math.round(s * 0.56f), Math.round(s * 0.24f), Math.round(s * 0.14f),
                            Math.round(s * 0.52f), 3, 3);
                }
                case STOP -> g.fillRoundRect(Math.round(s * 0.30f), Math.round(s * 0.30f),
                        Math.round(s * 0.40f), Math.round(s * 0.40f), 4, 4);
                case FOLDER -> {
                    g.fillRoundRect(Math.round(s * 0.12f), Math.round(s * 0.34f), Math.round(s * 0.76f),
                            Math.round(s * 0.46f), 4, 4);
                    g.fillRoundRect(Math.round(s * 0.16f), Math.round(s * 0.24f), Math.round(s * 0.30f),
                            Math.round(s * 0.18f), 4, 4);
                }
                case LYRICS -> {
                    int left = Math.round(s * 0.22f);
                    int right = Math.round(s * 0.78f);
                    for (int i = 0; i < 3; i++) {
                        int yy = Math.round(s * (0.30f + i * 0.20f));
                        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.drawLine(left, yy, right - (i == 1 ? Math.round(s * 0.13f) : 0), yy);
                    }
                }
                case MIXER -> {
                    g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int[] xs = {Math.round(s * 0.28f), Math.round(s * 0.50f), Math.round(s * 0.72f)};
                    int[] knobs = {Math.round(s * 0.38f), Math.round(s * 0.62f), Math.round(s * 0.48f)};
                    for (int i = 0; i < xs.length; i++) {
                        g.drawLine(xs[i], Math.round(s * 0.22f), xs[i], Math.round(s * 0.78f));
                        g.fillOval(xs[i] - Math.round(s * 0.07f), knobs[i] - Math.round(s * 0.07f),
                                Math.round(s * 0.14f), Math.round(s * 0.14f));
                    }
                }
            }
            g.dispose();
        }
    }

    private static Color blend(Color base, Color top, float amount) {
        float keep = 1f - amount;
        return new Color(
                Math.round(base.getRed() * keep + top.getRed() * amount),
                Math.round(base.getGreen() * keep + top.getGreen() * amount),
                Math.round(base.getBlue() * keep + top.getBlue() * amount));
    }
}
