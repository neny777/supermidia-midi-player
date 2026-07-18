package br.com.supermidia.lyrics;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MidiLyrics {
    private static final int TEXT_EVENT = 0x01;
    private static final int LYRIC_EVENT = 0x05;
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final MidiLyrics EMPTY = new MidiLyrics(List.of());

    private final List<LyricLine> lines;

    private MidiLyrics(List<LyricLine> lines) {
        this.lines = List.copyOf(lines);
    }

    public static MidiLyrics empty() {
        return EMPTY;
    }

    public static MidiLyrics fromFile(File file) throws IOException, InvalidMidiDataException {
        Objects.requireNonNull(file, "file");
        return fromSequence(MidiSystem.getSequence(file));
    }

    public static MidiLyrics fromSequence(Sequence sequence) {
        Objects.requireNonNull(sequence, "sequence");

        List<Segment> lyricSegments = collectBestTrack(sequence, LYRIC_EVENT, false);
        if (!lyricSegments.isEmpty()) {
            List<LyricLine> lyricLines = buildLines(
                    lyricSegments, false, sequence.getResolution());
            if (!lyricLines.isEmpty()) {
                return new MidiLyrics(lyricLines);
            }
        }

        List<Segment> textSegments = collectBestTrack(sequence, TEXT_EVENT, true);
        if (!textSegments.isEmpty()) {
            List<LyricLine> textLines = buildLines(
                    textSegments, true, sequence.getResolution());
            if (!textLines.isEmpty()) {
                return new MidiLyrics(textLines);
            }
        }
        return empty();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int size() {
        return lines.size();
    }

    public List<LyricLine> lines() {
        return lines;
    }

    public Optional<LyricLine> lineAt(int index) {
        return index >= 0 && index < lines.size()
                ? Optional.of(lines.get(index)) : Optional.empty();
    }

    public int currentIndex(long tick) {
        if (lines.isEmpty() || tick < lines.getFirst().tick()) {
            return -1;
        }

        int low = 0;
        int high = lines.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).tick() <= tick) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return high;
    }

    private static List<Segment> collectBestTrack(
            Sequence sequence, int metaType, boolean requireLineMarker) {
        List<Segment> best = List.of();
        int bestScore = -1;

        for (Track track : sequence.getTracks()) {
            List<Segment> candidate = new ArrayList<>();
            int markers = 0;
            for (int index = 0; index < track.size(); index++) {
                MidiEvent event = track.get(index);
                MidiMessage message = event.getMessage();
                if (message instanceof MetaMessage meta && meta.getType() == metaType) {
                    String text = decodeText(meta.getData());
                    if (!text.isBlank() || hasLineMarker(text)) {
                        candidate.add(new Segment(event.getTick(), index, text));
                        if (hasLineMarker(text)) {
                            markers++;
                        }
                    }
                }
            }

            if (candidate.isEmpty() || (requireLineMarker && markers == 0)) {
                continue;
            }
            int score = candidate.size() + markers * 10_000;
            if (score > bestScore) {
                candidate.sort(Comparator
                        .comparingLong(Segment::tick)
                        .thenComparingInt(Segment::order));
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static List<LyricLine> buildLines(
            List<Segment> segments, boolean requireKaraokeMarker, int resolution) {
        List<LyricLine> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        long currentTick = -1;
        boolean acceptedLyrics = !requireKaraokeMarker;
        boolean sawLineMarker = false;

        for (Segment segment : segments) {
            String raw = segment.text();
            if (isMetadata(raw) && !hasLineMarker(raw)) {
                continue;
            }

            boolean containsMarker = hasLineMarker(raw);
            if (requireKaraokeMarker && !acceptedLyrics && !containsMarker) {
                continue;
            }
            if (containsMarker) {
                acceptedLyrics = true;
                sawLineMarker = true;
            }
            if (!acceptedLyrics) {
                continue;
            }

            for (int index = 0; index < raw.length(); index++) {
                char character = raw.charAt(index);
                if (isLineMarker(character)) {
                    addLine(lines, currentTick, current);
                    current.setLength(0);
                    currentTick = -1;
                } else if (!Character.isISOControl(character)) {
                    if (currentTick < 0) {
                        currentTick = segment.tick();
                    }
                    current.append(character);
                }
            }
        }
        addLine(lines, currentTick, current);

        if (!requireKaraokeMarker && !sawLineMarker && segments.size() > 1) {
            return buildPhraseLines(segments, resolution);
        }
        return lines;
    }

    private static List<LyricLine> buildPhraseLines(List<Segment> segments, int resolution) {
        List<LyricLine> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        long currentTick = -1;
        long previousTick = -1;

        for (Segment segment : segments) {
            if (isMetadata(segment.text()) || segment.text().isBlank()) {
                continue;
            }
            if (currentTick >= 0
                    && isLikelyPhraseBreak(previousTick, segment.tick(), current, resolution)) {
                addLine(lines, currentTick, current);
                current.setLength(0);
                currentTick = -1;
            }
            if (currentTick < 0) {
                currentTick = segment.tick();
            }
            current.append(segment.text());
            previousTick = segment.tick();
        }
        addLine(lines, currentTick, current);
        return lines;
    }

    private static boolean isLikelyPhraseBreak(
            long previousTick, long tick, StringBuilder current, int resolution) {
        if (previousTick < 0) {
            return false;
        }
        long gap = tick - previousTick;
        long quarterNote = Math.max(1, resolution);
        if (gap >= quarterNote * 4L) {
            return true;
        }

        String text = normalizeLine(current.toString());
        boolean punctuation = text.endsWith(".") || text.endsWith("!")
                || text.endsWith("?") || text.endsWith(",") || text.endsWith(";");
        if (punctuation && gap >= quarterNote / 2L) {
            return true;
        }
        return text.length() >= 70 && gap >= quarterNote / 4L;
    }

    private static void addLine(List<LyricLine> lines, long tick, StringBuilder text) {
        String normalized = normalizeLine(text.toString());
        if (tick >= 0 && !normalized.isBlank()) {
            lines.add(new LyricLine(tick, normalized));
        }
    }

    private static String decodeText(byte[] data) {
        String decoded;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data))
                    .toString();
        } catch (CharacterCodingException exception) {
            decoded = new String(data, WINDOWS_1252);
        }
        return decoded.replace('\u0000', ' ').replace('\u00A0', ' ');
    }

    private static String normalizeLine(String text) {
        return text.replace('_', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean isMetadata(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() || trimmed.startsWith("@");
    }

    private static boolean hasLineMarker(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (isLineMarker(text.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLineMarker(char character) {
        return character == '\\' || character == '/'
                || character == '\r' || character == '\n';
    }

    private record Segment(long tick, int order, String text) {
    }
}
