package supermidia.midi.player;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ChannelMixerWindow {
    private final MidiPlayer player;
    private final JFrame window;
    private final JLabel statusLabel;
    private final GMTheme.ActivityLight[] activityLights = new GMTheme.ActivityLight[MidiPlayer.CHANNEL_COUNT];
    private final JToggleButton[] muteButtons = new JToggleButton[MidiPlayer.CHANNEL_COUNT];
    private final JToggleButton[] soloButtons = new JToggleButton[MidiPlayer.CHANNEL_COUNT];
    private final JButton clearButton;
    private boolean updating = false;

    public ChannelMixerWindow(JFrame owner, MidiPlayer player) {
        this.player = player;
        window = new JFrame("16-Channel Mixer");
        window.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        JPanel root = GMTheme.rootPanel(new BorderLayout(18, 18), 22, 24, 24, 24);
        window.setContentPane(root);

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setOpaque(false);

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.add(GMTheme.eyebrow("TRACKS"));
        titleStack.add(Box.createVerticalStrut(5));
        titleStack.add(GMTheme.title("16-Channel Mixer", 18f));
        header.add(titleStack, BorderLayout.WEST);

        statusLabel = GMTheme.badge("No file loaded");
        header.add(statusLabel, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        GMTheme.SurfacePanel grid = GMTheme.surfacePanel(new GridBagLayout());
        root.add(grid, BorderLayout.CENTER);

        addHeaderRow(grid);
        for (int channel = 0; channel < MidiPlayer.CHANNEL_COUNT; channel++) {
            addChannelRow(grid, channel);
        }

        clearButton = GMTheme.textButton("Clear Mute/Solo", "Clear every muted and soloed channel");
        clearButton.addActionListener(e -> {
            player.clearChannelControls();
            updateState();
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.add(clearButton);
        root.add(footer, BorderLayout.SOUTH);

        window.setMinimumSize(new Dimension(480, 620));
        window.pack();
        window.setLocationRelativeTo(owner);
        updateState();
    }

    public void showWindow() {
        window.setVisible(true);
        window.toFront();
    }

    public void updateState() {
        updating = true;
        try {
            boolean[] activeChannels = player.getActiveChannels();
            boolean hasSong = player.hasSequence();
            statusLabel.setText(hasSong ? "Watching playback" : "No file loaded");
            clearButton.setEnabled(hasSong);

            for (int channel = 0; channel < MidiPlayer.CHANNEL_COUNT; channel++) {
                boolean muted = player.isChannelMuted(channel);
                boolean solo = player.isChannelSolo(channel);
                boolean active = hasSong && activeChannels[channel];

                activityLights[channel].setActive(active);
                muteButtons[channel].setSelected(muted);
                soloButtons[channel].setSelected(solo);
                muteButtons[channel].setEnabled(hasSong);
                soloButtons[channel].setEnabled(hasSong);
            }
        } finally {
            updating = false;
        }
    }

    public void dispose() {
        window.dispose();
    }

    private void addHeaderRow(JPanel grid) {
        addCell(grid, headerLabel("Channel"), 0, 0, 1.0, GridBagConstraints.WEST);
        addCell(grid, headerLabel("Playing"), 1, 0, 0.0, GridBagConstraints.CENTER);
        addCell(grid, headerLabel("Mute"), 2, 0, 0.0, GridBagConstraints.CENTER);
        addCell(grid, headerLabel("Solo"), 3, 0, 0.0, GridBagConstraints.CENTER);
    }

    private void addChannelRow(JPanel grid, int channel) {
        int row = channel + 1;
        JLabel channelLabel = new JLabel(channelName(channel));
        channelLabel.setForeground(GMTheme.TEXT);
        channelLabel.setFont(channelLabel.getFont().deriveFont(Font.BOLD, 13f));
        channelLabel.setBorder(new EmptyBorder(5, 0, 5, 16));

        GMTheme.ActivityLight light = new GMTheme.ActivityLight();
        activityLights[channel] = light;

        JToggleButton muteButton = GMTheme.toggleButton("M", GMTheme.RED, "Mute channel " + (channel + 1));
        muteButton.addActionListener(e -> {
            if (!updating) {
                player.setChannelMuted(channel, muteButton.isSelected());
                updateState();
            }
        });
        muteButtons[channel] = muteButton;

        JToggleButton soloButton = GMTheme.toggleButton("S", GMTheme.BLUE, "Solo channel " + (channel + 1));
        soloButton.addActionListener(e -> {
            if (!updating) {
                player.setChannelSolo(channel, soloButton.isSelected());
                updateState();
            }
        });
        soloButtons[channel] = soloButton;

        addCell(grid, channelLabel, 0, row, 1.0, GridBagConstraints.WEST);
        addCell(grid, light, 1, row, 0.0, GridBagConstraints.CENTER);
        addCell(grid, muteButton, 2, row, 0.0, GridBagConstraints.CENTER);
        addCell(grid, soloButton, 3, row, 0.0, GridBagConstraints.CENTER);
    }

    private JLabel headerLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(GMTheme.MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

    private void addCell(JPanel grid, Component component, int x, int y, double weightX, int anchor) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.weightx = weightX;
        gbc.fill = x == 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        gbc.anchor = anchor;
        gbc.insets = new Insets(4, 8, 4, 8);
        grid.add(component, gbc);
    }

    private String channelName(int channel) {
        int number = channel + 1;
        if (number == 10) {
            return "Channel 10 - Drums";
        }
        return "Channel " + number;
    }
}
