package supermidia.midi.player;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MidiLyrics {
    private static final int LYRIC_EVENT = 0x05;
    private static final int TEXT_EVENT = 0x01;
    private static final Charset MIDI_TEXT_CHARSET = Charset.forName("windows-1252");
    private static final MidiLyrics EMPTY = new MidiLyrics(Collections.emptyList());

    private final List<Line> lines;

    private MidiLyrics(List<Line> lines) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public static MidiLyrics empty() {
        return EMPTY;
    }

    public static MidiLyrics fromFile(File file) throws Exception {
        Objects.requireNonNull(file, "file");
        Sequence sequence = MidiSystem.getSequence(file);

        List<Segment> lyricSegments = collectSegments(sequence, LYRIC_EVENT);
        if (!lyricSegments.isEmpty()) {
            return new MidiLyrics(buildLines(lyricSegments, false));
        }

        List<Segment> textSegments = collectSegments(sequence, TEXT_EVENT);
        if (!textSegments.isEmpty()) {
            return new MidiLyrics(buildLines(textSegments, true));
        }

        return empty();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int size() {
        return lines.size();
    }

    public int currentIndex(long tick) {
        if (lines.isEmpty() || tick < lines.get(0).getTick()) {
            return -1;
        }

        int low = 0;
        int high = lines.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            long lineTick = lines.get(middle).getTick();
            if (lineTick <= tick) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return high;
    }

    public Line lineAt(int index) {
        if (index < 0 || index >= lines.size()) {
            return null;
        }
        return lines.get(index);
    }

    public Line currentLine(long tick) {
        return lineAt(currentIndex(tick));
    }

    public List<Line> lines() {
        return lines;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java MidiLyrics <file.mid> [more files...]");
            return;
        }

        for (String name : args) {
            MidiLyrics lyrics = fromFile(new File(name));
            System.out.printf("%s: %d lyric sentences%n", name, lyrics.size());
            int previewCount = Math.min(3, lyrics.size());
            for (int i = 0; i < previewCount; i++) {
                Line line = lyrics.lineAt(i);
                System.out.printf("  %d: %s%n", line.getTick(), line.getText());
            }
        }
    }

    private static List<Segment> collectSegments(Sequence sequence, int metaType) {
        List<Segment> segments = new ArrayList<>();
        int order = 0;

        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();
                if (message instanceof MetaMessage meta && meta.getType() == metaType) {
                    String text = decodeText(meta.getData());
                    if (!text.isBlank() || hasLineMarker(text)) {
                        segments.add(new Segment(event.getTick(), order++, text));
                    }
                }
            }
        }

        segments.sort(Comparator
                .comparingLong(Segment::tick)
                .thenComparingInt(Segment::order));
        return segments;
    }

    private static String decodeText(byte[] data) {
        return new String(data, MIDI_TEXT_CHARSET)
                .replace('\u0000', ' ')
                .replace('\u00a0', ' ');
    }

    private static List<Line> buildLines(List<Segment> segments, boolean requireKaraokeMarker) {
        List<Line> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        long currentTick = -1;
        boolean acceptedLyrics = !requireKaraokeMarker;
        boolean sawLineMarker = false;

        for (Segment segment : segments) {
            String raw = segment.text();
            if (isMetadata(raw) && !hasLineMarker(raw)) {
                continue;
            }

            boolean hasMarker = hasLineMarker(raw);
            if (requireKaraokeMarker && !acceptedLyrics && !hasMarker) {
                continue;
            }
            if (hasMarker) {
                acceptedLyrics = true;
                sawLineMarker = true;
            }
            if (!acceptedLyrics) {
                continue;
            }

            for (int i = 0; i < raw.length(); i++) {
                char ch = raw.charAt(i);
                if (isLineMarker(ch)) {
                    addLine(lines, currentTick, current);
                    current.setLength(0);
                    currentTick = -1;
                } else if (!Character.isISOControl(ch)) {
                    if (currentTick < 0) {
                        currentTick = segment.tick();
                    }
                    current.append(ch);
                }
            }
        }

        addLine(lines, currentTick, current);

        if (!requireKaraokeMarker && !sawLineMarker && segments.size() > 1) {
            return buildPhraseLines(segments);
        }

        return lines;
    }

    private static List<Line> buildPhraseLines(List<Segment> segments) {
        List<Line> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        long currentTick = -1;
        long previousTick = -1;

        for (Segment segment : segments) {
            if (!isMetadata(segment.text())) {
                String text = segment.text();
                if (text.isBlank()) {
                    continue;
                }

                if (currentTick >= 0 && isLikelyPhraseBreak(previousTick, segment.tick(), current)) {
                    addLine(lines, currentTick, current);
                    current.setLength(0);
                    currentTick = -1;
                }

                if (currentTick < 0) {
                    currentTick = segment.tick();
                }
                current.append(text);
                previousTick = segment.tick();
            }
        }

        addLine(lines, currentTick, current);
        return lines;
    }

    private static boolean isLikelyPhraseBreak(long previousTick, long tick, StringBuilder current) {
        if (previousTick < 0) {
            return false;
        }

        String text = normalizeLine(current.toString());
        if (text.endsWith(".") || text.endsWith("!") || text.endsWith("?") || text.endsWith(",") || text.endsWith(";")) {
            return tick - previousTick >= 240;
        }

        return text.length() >= 55 && tick - previousTick >= 120;
    }

    private static void addLine(List<Line> lines, long tick, StringBuilder text) {
        String normalized = normalizeLine(text.toString());
        if (tick >= 0 && !normalized.isBlank()) {
            lines.add(new Line(tick, normalized));
        }
    }

    private static String normalizeLine(String text) {
        return text.replace('_', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean isMetadata(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() || trimmed.startsWith("@");
    }

    private static boolean hasLineMarker(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (isLineMarker(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLineMarker(char ch) {
        return ch == '\\' || ch == '/' || ch == '\r' || ch == '\n';
    }

    private record Segment(long tick, int order, String text) {
    }

    public static final class Line {
        private final long tick;
        private final String text;

        private Line(long tick, String text) {
            this.tick = tick;
            this.text = text;
        }

        public long getTick() {
            return tick;
        }

        public String getText() {
            return text;
        }
    }
}
