package supermidia.midi.player;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.Locale;

public class MidiPlayerApp {
    private static final int SLIDER_MAX = 10_000;

    private final MidiPlayer player;
    private MidiLyrics currentLyrics = MidiLyrics.empty();
    private LyricsWindow lyricsWindow;
    private ChannelMixerWindow channelMixerWindow;
    private String currentSongName = "No file loaded";
    private boolean expectingToPlay = false;
    private boolean updatingSlider = false;
    private File currentDirectory = new File(".").getAbsoluteFile();

    private JFrame frame;
    private JLabel fileLabel;
    private JLabel statusLabel;
    private JLabel timeLabel;
    private JSlider seekSlider;
    private JButton openButton;
    private JButton playButton;
    private JButton pauseButton;
    private JButton stopButton;
    private JButton lyricsButton;
    private JButton tracksButton;
    private javax.swing.Timer uiTimer;

    public MidiPlayerApp() throws MidiUnavailableException {
        player = new MidiPlayer();
        buildUI();
    }

    private void buildUI() {
        frame = new JFrame("GM Player Studio");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        JPanel root = GMTheme.rootPanel(new BorderLayout(18, 18), 22, 24, 22, 24);
        frame.setContentPane(root);

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setOpaque(false);

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.add(GMTheme.eyebrow("GM PLAYER STUDIO"));
        titleStack.add(Box.createVerticalStrut(5));
        fileLabel = GMTheme.title("No file loaded", 22f);
        titleStack.add(fileLabel);
        header.add(titleStack, BorderLayout.WEST);

        statusLabel = GMTheme.badge("Ready");
        header.add(statusLabel, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        GMTheme.SurfacePanel playerPanel = GMTheme.surfacePanel(new BorderLayout(16, 16));
        root.add(playerPanel, BorderLayout.CENTER);

        seekSlider = new JSlider(0, SLIDER_MAX, 0);
        seekSlider.setEnabled(false);
        seekSlider.setFocusable(false);
        seekSlider.setToolTipText("Seek");
        GMTheme.styleSlider(seekSlider);
        seekSlider.addChangeListener(e -> onSeekSliderChanged());

        timeLabel = GMTheme.mutedLabel(formatTime(0, 0));
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        timeLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        JPanel scrubber = new JPanel(new BorderLayout(12, 8));
        scrubber.setOpaque(false);
        scrubber.add(seekSlider, BorderLayout.CENTER);
        scrubber.add(timeLabel, BorderLayout.EAST);
        playerPanel.add(scrubber, BorderLayout.NORTH);

        JPanel transport = new JPanel(new BorderLayout(14, 0));
        transport.setOpaque(false);

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftActions.setOpaque(false);
        openButton = GMTheme.actionButton("Open", GMTheme.IconType.FOLDER, "Open MIDI file");
        leftActions.add(openButton);
        transport.add(leftActions, BorderLayout.WEST);

        JPanel mainControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        mainControls.setOpaque(false);
        pauseButton = GMTheme.iconButton(GMTheme.IconType.PAUSE, "Pause", 46, GMTheme.SURFACE_2);
        playButton = GMTheme.iconButton(GMTheme.IconType.PLAY, "Play", 58, GMTheme.ACCENT);
        stopButton = GMTheme.iconButton(GMTheme.IconType.STOP, "Stop", 46, GMTheme.SURFACE_2);
        mainControls.add(pauseButton);
        mainControls.add(playButton);
        mainControls.add(stopButton);
        transport.add(mainControls, BorderLayout.CENTER);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.setOpaque(false);
        lyricsButton = GMTheme.actionButton("Lyrics", GMTheme.IconType.LYRICS, "Show lyrics");
        tracksButton = GMTheme.actionButton("Tracks", GMTheme.IconType.MIXER, "Show 16-channel mixer");
        rightActions.add(lyricsButton);
        rightActions.add(tracksButton);
        transport.add(rightActions, BorderLayout.EAST);

        playerPanel.add(transport, BorderLayout.CENTER);

        openButton.setMnemonic(KeyEvent.VK_O);
        playButton.setMnemonic(KeyEvent.VK_P);
        pauseButton.setMnemonic(KeyEvent.VK_A);
        stopButton.setMnemonic(KeyEvent.VK_S);
        lyricsButton.setMnemonic(KeyEvent.VK_L);
        tracksButton.setMnemonic(KeyEvent.VK_T);

        openButton.addActionListener(e -> onOpen());
        playButton.addActionListener(e -> onPlay());
        pauseButton.addActionListener(e -> onPause());
        stopButton.addActionListener(e -> onStop());
        lyricsButton.addActionListener(e -> onLyrics());
        tracksButton.addActionListener(e -> onTracks());

        installKeyboardActions();
        installFileDrop();
        updateControlState();

        uiTimer = new javax.swing.Timer(150, e -> refresh());
        uiTimer.start();

        frame.setMinimumSize(new Dimension(700, 260));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void onOpen() {
        JFileChooser chooser = new JFileChooser(currentDirectory);
        chooser.setFileFilter(new FileNameExtensionFilter("MIDI files (*.mid, *.midi)", "mid", "midi"));

        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile());
        }
    }

    private void openFile(File file) {
        try {
            player.pause();
            expectingToPlay = false;
            player.load(file);
            currentLyrics = loadLyrics(file);
            currentSongName = file.getName();

            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) {
                currentDirectory = parent;
            }

            fileLabel.setText(currentSongName);
            fileLabel.setToolTipText(file.getAbsolutePath());
            statusLabel.setText(loadedStatusText());
            setSliderFromPosition(0, player.getDurationSeconds());
            timeLabel.setText(formatTime(0, player.getDurationSeconds()));
            if (lyricsWindow != null) {
                lyricsWindow.setSong(currentSongName, currentLyrics);
            }
            updateLyricsWindow();
            updateChannelMixerWindow();
            updateControlState();
        } catch (Exception ex) {
            showLoadError(file, ex);
        }
    }

    private void onPlay() {
        if (!player.hasSequence()) {
            return;
        }

        expectingToPlay = true;
        player.play();
        statusLabel.setText("Playing");
        updateControlState();
    }

    private void onPause() {
        if (!player.hasSequence()) {
            return;
        }

        expectingToPlay = false;
        player.pause();
        statusLabel.setText("Paused");
        updateControlState();
    }

    private void onStop() {
        if (!player.hasSequence()) {
            return;
        }

        expectingToPlay = false;
        player.stop();
        statusLabel.setText("Stopped");
        setSliderFromPosition(0, player.getDurationSeconds());
        timeLabel.setText(formatTime(0, player.getDurationSeconds()));
        updateLyricsWindow();
        updateChannelMixerWindow();
        updateControlState();
    }

    private void onPlayPause() {
        if (!player.hasSequence()) {
            return;
        }

        if (player.isPlaying()) {
            onPause();
        } else {
            onPlay();
        }
    }

    private void onSeekSliderChanged() {
        if (updatingSlider || !player.hasSequence()) {
            return;
        }

        double duration = player.getDurationSeconds();
        if (duration <= 0) {
            return;
        }

        double target = sliderToSeconds(duration);
        timeLabel.setText(formatTime(target, duration));

        if (!seekSlider.getValueIsAdjusting()) {
            player.setPositionSeconds(target);
            updateLyricsWindow();
            updateChannelMixerWindow();
            updateControlState();
        }
    }

    private void onLyrics() {
        if (lyricsWindow == null) {
            lyricsWindow = new LyricsWindow(frame);
        }

        lyricsWindow.setSong(currentSongName, currentLyrics);
        updateLyricsWindow();
        lyricsWindow.showWindow();
    }

    private void onTracks() {
        if (channelMixerWindow == null) {
            channelMixerWindow = new ChannelMixerWindow(frame, player);
        }

        updateChannelMixerWindow();
        channelMixerWindow.showWindow();
    }

    private void refresh() {
        if (!player.hasSequence()) {
            updateControlState();
            return;
        }

        double duration = player.getDurationSeconds();
        double position = player.getPositionSeconds();

        if (expectingToPlay && duration > 0 && !player.isPlaying() && position >= duration - 0.25) {
            expectingToPlay = false;
            player.stop();
            position = 0;
            statusLabel.setText("Finished");
        }

        if (!seekSlider.getValueIsAdjusting()) {
            setSliderFromPosition(position, duration);
            timeLabel.setText(formatTime(position, duration));
        }

        updateLyricsWindow();
        updateChannelMixerWindow();
        updateControlState();
    }

    private void updateControlState() {
        boolean hasSong = player.hasSequence();
        boolean playing = player.isPlaying();
        boolean canStop = hasSong && (playing || player.getPositionSeconds() > 0.05);

        seekSlider.setEnabled(hasSong);
        playButton.setEnabled(hasSong && !playing);
        pauseButton.setEnabled(hasSong && playing);
        stopButton.setEnabled(canStop);
    }

    private void installKeyboardActions() {
        JComponent root = frame.getRootPane();
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                "open", this::onOpen);
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_L, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                "lyrics", this::onLyrics);
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_T, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                "tracks", this::onTracks);
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "playPause", this::onPlayPause);
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "stop", this::onStop);
    }

    private void bindKey(JComponent component, KeyStroke keyStroke, String name, Runnable action) {
        component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void installFileDrop() {
        TransferHandler handler = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }

                try {
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File file : files) {
                        if (isMidiFile(file)) {
                            openFile(file);
                            return true;
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame,
                            "Could not open dropped file:\n" + safeMessage(ex),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
                return false;
            }
        };

        frame.setTransferHandler(handler);
        frame.getRootPane().setTransferHandler(handler);
    }

    private void setSliderFromPosition(double position, double duration) {
        updatingSlider = true;
        try {
            int value = duration > 0
                    ? (int) Math.round((Math.max(0, Math.min(position, duration)) / duration) * SLIDER_MAX)
                    : 0;
            seekSlider.setValue(Math.max(0, Math.min(value, SLIDER_MAX)));
        } finally {
            updatingSlider = false;
        }
    }

    private double sliderToSeconds(double duration) {
        return (seekSlider.getValue() / (double) SLIDER_MAX) * duration;
    }

    private String formatTime(double position, double duration) {
        return formatSeconds(position) + " / " + formatSeconds(duration);
    }

    private String formatSeconds(double totalSeconds) {
        int rounded = (int) Math.round(Math.max(0, totalSeconds));
        return String.format("%d:%02d", rounded / 60, rounded % 60);
    }

    private MidiLyrics loadLyrics(File file) {
        try {
            return MidiLyrics.fromFile(file);
        } catch (Exception ex) {
            return MidiLyrics.empty();
        }
    }

    private String loadedStatusText() {
        if (currentLyrics.isEmpty()) {
            return "Loaded (no embedded lyrics)";
        }

        int count = currentLyrics.size();
        return "Loaded (" + count + " lyric " + (count == 1 ? "sentence" : "sentences") + ")";
    }

    private void updateLyricsWindow() {
        if (lyricsWindow == null) {
            return;
        }

        lyricsWindow.updateAtTick(player.getTickPosition());
    }

    private void updateChannelMixerWindow() {
        if (channelMixerWindow != null) {
            channelMixerWindow.updateState();
        }
    }

    private boolean isMidiFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".mid") || name.endsWith(".midi");
    }

    private void showLoadError(File file, Exception ex) {
        statusLabel.setText("Could not load file");
        JOptionPane.showMessageDialog(frame,
                "Could not load " + file.getName() + ":\n" + safeMessage(ex),
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private void shutdown() {
        if (uiTimer != null) {
            uiTimer.stop();
        }
        if (lyricsWindow != null) {
            lyricsWindow.dispose();
        }
        if (channelMixerWindow != null) {
            channelMixerWindow.dispose();
        }
        player.close();
        frame.dispose();
        System.exit(0);
    }

    public static void main(String[] args) {
        GMTheme.install();

        SwingUtilities.invokeLater(() -> {
            try {
                new MidiPlayerApp();
            } catch (MidiUnavailableException e) {
                JOptionPane.showMessageDialog(null,
                        "MIDI system unavailable: " + e.getMessage(),
                        "Fatal", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
