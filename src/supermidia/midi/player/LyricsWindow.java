package supermidia.midi.player;

import javax.swing.*;
import java.awt.*;

public class LyricsWindow {
    private final JFrame window;
    private final JLabel songLabel;
    private final JLabel countLabel;
    private final JTextArea previousLine;
    private final JTextArea currentLine;
    private final JTextArea nextLine;

    private MidiLyrics lyrics = MidiLyrics.empty();
    private String songName = "No file loaded";

    public LyricsWindow(JFrame owner) {
        window = new JFrame("Lyrics");
        window.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        JPanel root = GMTheme.rootPanel(new BorderLayout(18, 18), 22, 24, 24, 24);
        window.setContentPane(root);

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setOpaque(false);

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.add(GMTheme.eyebrow("LYRICS"));
        titleStack.add(Box.createVerticalStrut(5));
        songLabel = GMTheme.title(songName, 18f);
        titleStack.add(songLabel);
        header.add(titleStack, BorderLayout.WEST);

        countLabel = GMTheme.badge("No lyrics");
        header.add(countLabel, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        GMTheme.SurfacePanel stage = GMTheme.surfacePanel(new GridBagLayout());
        root.add(stage, BorderLayout.CENTER);

        previousLine = makeLineArea(GMTheme.MUTED, 20f, Font.PLAIN, 1);
        currentLine = makeLineArea(GMTheme.TEXT, 34f, Font.BOLD, 2);
        nextLine = makeLineArea(GMTheme.ACCENT, 22f, Font.PLAIN, 1);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 6, 8, 6);

        gbc.gridy = 0;
        gbc.weighty = 0.25;
        stage.add(previousLine, gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.5;
        stage.add(currentLine, gbc);

        gbc.gridy = 2;
        gbc.weighty = 0.25;
        stage.add(nextLine, gbc);

        window.setMinimumSize(new Dimension(620, 340));
        window.setSize(760, 440);
        window.setLocationRelativeTo(owner);
        updateAtTick(0);
    }

    public void showWindow() {
        window.setVisible(true);
        window.toFront();
    }

    public void setSong(String songName, MidiLyrics lyrics) {
        this.songName = songName != null ? songName : "No file loaded";
        this.lyrics = lyrics != null ? lyrics : MidiLyrics.empty();
        songLabel.setText(this.songName);
        updateAtTick(0);
    }

    public void updateAtTick(long tick) {
        if (lyrics.isEmpty()) {
            previousLine.setText("");
            currentLine.setText("No embedded lyrics found");
            nextLine.setText("");
            countLabel.setText("No lyrics");
            return;
        }

        int index = lyrics.currentIndex(tick);
        if (index < 0) {
            previousLine.setText("");
            currentLine.setText("Waiting for lyrics...");
            nextLine.setText(textOf(lyrics.lineAt(0)));
            countLabel.setText("0 / " + lyrics.size());
            return;
        }

        previousLine.setText(textOf(lyrics.lineAt(index - 1)));
        currentLine.setText(textOf(lyrics.lineAt(index)));
        nextLine.setText(textOf(lyrics.lineAt(index + 1)));
        countLabel.setText((index + 1) + " / " + lyrics.size());
    }

    public void dispose() {
        window.dispose();
    }

    private JTextArea makeLineArea(Color color, float size, int style, int rows) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setForeground(color);
        area.setFont(area.getFont().deriveFont(style, size));
        area.setRows(rows);
        area.setMargin(new Insets(0, 0, 0, 0));
        return area;
    }

    private String textOf(MidiLyrics.Line line) {
        return line != null ? line.getText() : "";
    }
}
