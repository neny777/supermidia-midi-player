package br.com.supermidia.app;

import br.com.supermidia.core.PlaybackMode;
import br.com.supermidia.lyrics.LyricLine;
import br.com.supermidia.lyrics.MidiLyrics;
import br.com.supermidia.midi.MidiOutputDevice;
import br.com.supermidia.midi.MidiPlaybackEngine;
import br.com.supermidia.mixer.MidiChannelInfo;
import br.com.supermidia.mixer.MidiSongAnalysis;
import br.com.supermidia.playlist.PlaylistFileService;
import br.com.supermidia.playlist.PlaylistItem;
import br.com.supermidia.playlist.PlaylistManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class MainController {
    private static final int MIDI_CHANNEL_COUNT = 16;
    private static final int MIXER_BANK_SIZE = 8;
    @FXML
    private Label currentSongLabel;
    @FXML
    private Label currentSongHintLabel;
    @FXML
    private Label nextSongLabel;
    @FXML
    private Label nextSongHintLabel;
    @FXML
    private Label stageLyricsStatusLabel;
    @FXML
    private Label stagePreviousLyricLabel;
    @FXML
    private Label stageCurrentLyricLabel;
    @FXML
    private Label stageNextLyricLabel;
    @FXML
    private Label lyricsSongLabel;
    @FXML
    private Label lyricsCountLabel;
    @FXML
    private Label lyricsPreviousLabel;
    @FXML
    private Label lyricsCurrentLabel;
    @FXML
    private Label lyricsNextLabel;
    @FXML
    private Label playbackModeLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label transposeValueLabel;
    @FXML
    private Label speedValueLabel;
    @FXML
    private Label masterVolumeValueLabel;
    @FXML
    private Label currentTimeLabel;
    @FXML
    private Label durationTimeLabel;
    @FXML
    private Label midiOutputStatusLabel;
    @FXML
    private Label playlistFileLabel;
    @FXML
    private Label playlistCountLabel;
    @FXML
    private Label mixerSongLabel;

    @FXML
    private ComboBox<MidiOutputDevice> midiOutputComboBox;
    @FXML
    private ListView<PlaylistItem> playlistListView;
    @FXML
    private ListView<LyricLine> lyricsListView;

    @FXML
    private ToggleButton automaticModeToggle;
    @FXML
    private ToggleButton presentationNavButton;
    @FXML
    private ToggleButton playlistNavButton;
    @FXML
    private ToggleButton lyricsNavButton;
    @FXML
    private ToggleButton mixerNavButton;
    @FXML
    private ToggleButton mixerBankOneButton;
    @FXML
    private ToggleButton mixerBankTwoButton;

    @FXML
    private Slider transposeSlider;
    @FXML
    private Slider speedSlider;
    @FXML
    private Slider masterVolumeSlider;
    @FXML
    private Slider progressSlider;

    @FXML
    private Button playButton;
    @FXML
    private Button pauseButton;
    @FXML
    private Button stopButton;
    @FXML
    private Button previousButton;
    @FXML
    private Button nextButton;
    @FXML
    private Button panicButton;
    @FXML
    private Button savePlaylistButton;
    @FXML
    private Button moveUpButton;
    @FXML
    private Button moveDownButton;
    @FXML
    private Button removePlaylistButton;
    @FXML
    private Button resetMixerButton;

    @FXML
    private ScrollPane presentationView;
    @FXML
    private VBox playlistView;
    @FXML
    private VBox lyricsView;
    @FXML
    private ScrollPane mixerView;
    @FXML
    private HBox mixerChannelsContainer;

    private final PlaylistManager playlist = new PlaylistManager();
    private final PlaylistFileService playlistFileService = new PlaylistFileService();
    private final VBox[] mixerChannelStrips = new VBox[MIDI_CHANNEL_COUNT];
    private final Slider[] channelVolumeSliders = new Slider[MIDI_CHANNEL_COUNT];
    private final Label[] channelVolumeLabels = new Label[MIDI_CHANNEL_COUNT];
    private final Label[] channelNameLabels = new Label[MIDI_CHANNEL_COUNT];
    private final Label[] channelInstrumentLabels = new Label[MIDI_CHANNEL_COUNT];
    private final Label[] channelActivityLabels = new Label[MIDI_CHANNEL_COUNT];
    private final ToggleButton[] channelMuteButtons = new ToggleButton[MIDI_CHANNEL_COUNT];
    private final ToggleButton[] channelSoloButtons = new ToggleButton[MIDI_CHANNEL_COUNT];

    private PlaybackMode playbackMode = PlaybackMode.MANUAL;
    private MidiPlaybackEngine engine;
    private Timeline progressTimeline;
    private Path currentPlaylistFile;
    private MidiLyrics currentLyrics = MidiLyrics.empty();
    private MidiSongAnalysis currentSongAnalysis = MidiSongAnalysis.empty();
    private int displayedLyricIndex = Integer.MIN_VALUE;
    private int currentMixerBank;
    private boolean updatingMixerControls;
    private boolean playlistDirty;
    private boolean refreshingOutputs;

    @FXML
    private void initialize() {
        configureControlListeners();
        configurePlaylistView();
        configureLyricsView();
        configureMixerView();
        refreshPlaybackMode();
        refreshPlaylistView(-1);

        try {
            engine = new MidiPlaybackEngine();
            engine.setTranspose((int) transposeSlider.getValue());
            engine.setTempoFactor((float) (speedSlider.getValue() / 100.0));
            engine.setMasterVolume(masterVolumeSlider.getValue() / 100.0);
            engine.setPlaybackFinishedHandler(
                    () -> Platform.runLater(this::handlePlaybackFinished));
            startProgressUpdates();
            refreshMidiOutputs();
        } catch (MidiUnavailableException exception) {
            statusLabel.setText("MIDI INDISPONÍVEL");
            midiOutputStatusLabel.setText("Sequenciador MIDI não encontrado");
            disableMidiControls();
        }
        updateTransportControls();
    }

    public void shutdown() {
        if (progressTimeline != null) {
            progressTimeline.stop();
        }
        if (engine != null) {
            engine.close();
        }
    }

    public boolean confirmClose() {
        if (!playlistDirty || playlist.isEmpty()) {
            return true;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "A playlist possui alterações não salvas. Deseja fechar mesmo assim?",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("SuperMidia Live");
        confirmation.setHeaderText("Fechar o aplicativo");
        confirmation.initOwner(currentSongLabel.getScene().getWindow());
        return confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    @FXML
    private void handleShowPresentation() {
        showPresentation();
    }

    @FXML
    private void handleShowPlaylist() {
        presentationView.setVisible(false);
        presentationView.setManaged(false);
        lyricsView.setVisible(false);
        lyricsView.setManaged(false);
        mixerView.setVisible(false);
        mixerView.setManaged(false);
        playlistView.setVisible(true);
        playlistView.setManaged(true);
        playlistNavButton.setSelected(true);
    }

    @FXML
    private void handleShowLyrics() {
        presentationView.setVisible(false);
        presentationView.setManaged(false);
        playlistView.setVisible(false);
        playlistView.setManaged(false);
        mixerView.setVisible(false);
        mixerView.setManaged(false);
        lyricsView.setVisible(true);
        lyricsView.setManaged(true);
        lyricsNavButton.setSelected(true);
    }

    @FXML
    private void handleShowMixer() {
        presentationView.setVisible(false);
        presentationView.setManaged(false);
        playlistView.setVisible(false);
        playlistView.setManaged(false);
        lyricsView.setVisible(false);
        lyricsView.setManaged(false);
        mixerView.setVisible(true);
        mixerView.setManaged(true);
        mixerNavButton.setSelected(true);
    }

    @FXML
    private void handleMixerBankOne() {
        showMixerBank(0);
    }

    @FXML
    private void handleMixerBankTwo() {
        showMixerBank(1);
    }

    @FXML
    private void handleResetMixer() {
        applyOriginalMixerState();
    }

    @FXML
    private void handleOpenMidi() {
        if (engine == null) {
            return;
        }
        FileChooser chooser = midiFileChooser("Abrir arquivo MIDI");
        setInitialDirectory(chooser);
        File selectedFile = chooser.showOpenDialog(currentSongLabel.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        int previousSize = playlist.size();
        int index = playlist.add(selectedFile.toPath());
        if (playlist.size() != previousSize) {
            markPlaylistModified();
        }
        refreshPlaylistView(index);
        loadPlaylistIndex(index);
    }

    @FXML
    private void handleAddPlaylistItems() {
        FileChooser chooser = midiFileChooser("Adicionar músicas à playlist");
        setInitialDirectory(chooser);
        List<File> selectedFiles = chooser.showOpenMultipleDialog(playlistListView.getScene().getWindow());
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            return;
        }

        int previousSize = playlist.size();
        for (File file : selectedFiles) {
            playlist.add(file.toPath());
        }
        if (playlist.size() != previousSize) {
            markPlaylistModified();
        }

        if (playlist.currentIndex() < 0 && !playlist.isEmpty()) {
            loadPlaylistIndex(0);
        }
        refreshPlaylistView(playlistListView.getSelectionModel().getSelectedIndex());
    }

    @FXML
    private void handleOpenPlaylist() {
        if (!confirmDiscardPlaylistChanges()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir playlist");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Playlists M3U", "*.m3u8", "*.m3u", "*.M3U8", "*.M3U"));
        setInitialDirectory(chooser);
        File selectedFile = chooser.showOpenDialog(playlistListView.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        try {
            List<PlaylistItem> loadedItems = playlistFileService.load(selectedFile.toPath());
            resetLoadedSong();
            playlist.replaceAll(loadedItems);
            currentPlaylistFile = selectedFile.toPath().toAbsolutePath().normalize();
            playlistDirty = false;

            int firstPlayable = firstExistingItemIndex();
            if (firstPlayable >= 0) {
                loadPlaylistIndex(firstPlayable);
            }
            refreshPlaylistView(firstPlayable);
        } catch (IOException | RuntimeException exception) {
            showError("Não foi possível abrir a playlist",
                    "O arquivo selecionado não pôde ser lido.", exception);
        }
    }

    @FXML
    private void handleSavePlaylist() {
        if (playlist.isEmpty()) {
            return;
        }
        Path destination = currentPlaylistFile;
        if (destination == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Salvar playlist");
            chooser.setInitialFileName("repertorio.m3u8");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Playlist M3U8", "*.m3u8"));
            setInitialDirectory(chooser);
            File selectedFile = chooser.showSaveDialog(playlistListView.getScene().getWindow());
            if (selectedFile == null) {
                return;
            }
            destination = ensureM3u8Extension(selectedFile.toPath());
        }

        try {
            playlistFileService.save(destination, playlist.items());
            currentPlaylistFile = destination.toAbsolutePath().normalize();
            playlistDirty = false;
            updatePlaylistFileLabel();
        } catch (IOException exception) {
            showError("Não foi possível salvar a playlist",
                    "Verifique se a pasta permite gravação.", exception);
        }
    }

    @FXML
    private void handleRemovePlaylistItem() {
        int selectedIndex = playlistListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }
        boolean removedCurrent = playlist.removeAt(selectedIndex);
        markPlaylistModified();

        if (removedCurrent) {
            resetLoadedSong();
            if (playlist.currentIndex() >= 0) {
                loadPlaylistIndex(playlist.currentIndex());
            }
        }
        int nextSelection = playlist.isEmpty() ? -1 : Math.min(selectedIndex, playlist.size() - 1);
        refreshPlaylistView(nextSelection);
    }

    @FXML
    private void handleMovePlaylistItemUp() {
        int selectedIndex = playlistListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex > 0) {
            int newIndex = playlist.moveUp(selectedIndex);
            markPlaylistModified();
            refreshPlaylistView(newIndex);
        }
    }

    @FXML
    private void handleMovePlaylistItemDown() {
        int selectedIndex = playlistListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < playlist.size() - 1) {
            int newIndex = playlist.moveDown(selectedIndex);
            markPlaylistModified();
            refreshPlaylistView(newIndex);
        }
    }

    @FXML
    private void handlePlaylistClick(MouseEvent event) {
        if (event.getClickCount() == 2) {
            int selectedIndex = playlistListView.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0 && loadPlaylistIndex(selectedIndex)) {
                showPresentation();
            }
        }
    }

    @FXML
    private void handlePrevious() {
        if (playlist.hasPrevious()) {
            loadPlaylistIndex(playlist.currentIndex() - 1);
        }
    }

    @FXML
    private void handleNext() {
        if (playlist.hasNext()) {
            loadPlaylistIndex(playlist.currentIndex() + 1);
        }
    }

    @FXML
    private void handleRefreshMidiOutputs() {
        refreshMidiOutputs();
    }

    @FXML
    private void handleMidiOutputSelection() {
        if (engine == null || refreshingOutputs) {
            return;
        }
        MidiOutputDevice selection = midiOutputComboBox.getSelectionModel().getSelectedItem();
        try {
            engine.selectOutput(selection);
            midiOutputStatusLabel.setText(selection == null
                    ? "Saída MIDI desconectada" : "Saída: " + selection.name());
            updateCurrentSongHint();
            updateTransportControls();
        } catch (MidiUnavailableException exception) {
            midiOutputStatusLabel.setText("Falha ao conectar a saída MIDI");
            refreshingOutputs = true;
            midiOutputComboBox.getSelectionModel().clearSelection();
            refreshingOutputs = false;
            updateTransportControls();
            showError("Saída MIDI indisponível",
                    "O dispositivo pode estar sendo usado por outro programa.", exception);
        }
    }

    @FXML
    private void handlePlaybackMode() {
        playbackMode = automaticModeToggle.isSelected()
                ? PlaybackMode.AUTOMATIC : PlaybackMode.MANUAL;
        refreshPlaybackMode();
    }

    @FXML
    private void handlePlay() {
        if (engine == null) {
            return;
        }
        try {
            engine.play();
            statusLabel.setText("TOCANDO");
            updateTransportControls();
        } catch (IllegalStateException exception) {
            showError("Não foi possível iniciar", exception.getMessage(), exception);
        }
    }

    @FXML
    private void handlePause() {
        if (engine != null) {
            engine.pause();
            statusLabel.setText("PAUSADA");
            updateTransportControls();
        }
    }

    @FXML
    private void handleStop() {
        if (engine != null) {
            engine.stop();
            progressSlider.setValue(0);
            currentTimeLabel.setText("00:00");
            refreshLyricsAtTick(0);
            statusLabel.setText("PRONTA");
            updateTransportControls();
        }
    }

    @FXML
    private void handlePanic() {
        if (engine != null) {
            engine.panic();
            midiOutputStatusLabel.setText("Panic enviado à saída MIDI");
        }
    }

    private boolean loadPlaylistIndex(int index) {
        if (engine == null || index < 0 || index >= playlist.size()) {
            return false;
        }
        PlaylistItem item = playlist.get(index);
        if (!item.exists()) {
            showError("Arquivo não encontrado",
                    "A música não está mais no caminho salvo:\n" + item.path(),
                    new IOException(item.path().toString()));
            return false;
        }

        try {
            engine.load(item.path().toFile());
            playlist.select(index);
            loadMixer(item);
            loadLyrics(item);
            setLoadedSongUi(item);
            statusLabel.setText("PRONTA");
            refreshPlaylistView(index);
            updateTransportControls();
            return true;
        } catch (IOException | InvalidMidiDataException exception) {
            showError("Não foi possível abrir o MIDI",
                    "O arquivo selecionado não pôde ser lido como MIDI válido.", exception);
            return false;
        }
    }

    private void setLoadedSongUi(PlaylistItem item) {
        currentSongLabel.setText(item.displayName());
        currentSongLabel.setTooltip(new Tooltip(item.path().toString()));
        long duration = engine.getDurationMicroseconds();
        progressSlider.setMax(Math.max(1, duration));
        progressSlider.setValue(0);
        currentTimeLabel.setText("00:00");
        durationTimeLabel.setText(formatTime(duration));
        updateCurrentSongHint();
        updateSongCards();
    }

    private void resetLoadedSong() {
        if (engine != null) {
            engine.unload();
        }
        currentSongLabel.setText("Nenhuma música carregada");
        currentSongLabel.setTooltip(null);
        currentSongHintLabel.setText("Escolha uma música na playlist");
        progressSlider.setValue(0);
        progressSlider.setMax(1);
        currentTimeLabel.setText("00:00");
        durationTimeLabel.setText("00:00");
        statusLabel.setText("SEM MÚSICA");
        currentLyrics = MidiLyrics.empty();
        currentSongAnalysis = MidiSongAnalysis.empty();
        if (engine != null) {
            engine.resetChannelMix(currentSongAnalysis.initialVolumes());
        }
        updateMixerControls("Nenhuma música carregada");
        displayedLyricIndex = Integer.MIN_VALUE;
        lyricsListView.getItems().clear();
        lyricsSongLabel.setText("Nenhuma música carregada");
        refreshLyricsAtTick(0);
        updateSongCards();
        updateTransportControls();
    }

    private void configureControlListeners() {
        transposeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            int transpose = newValue.intValue();
            transposeValueLabel.setText(formatSemitones(transpose));
            if (engine != null) {
                engine.setTranspose(transpose);
            }
        });
        speedSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            long percent = Math.round(newValue.doubleValue());
            speedValueLabel.setText(percent + "%");
            if (engine != null) {
                engine.setTempoFactor((float) (percent / 100.0));
            }
        });
        masterVolumeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            long percent = Math.round(newValue.doubleValue());
            masterVolumeValueLabel.setText(percent + "%");
            if (engine != null) {
                engine.setMasterVolume(percent / 100.0);
            }
        });
        progressSlider.setOnMouseReleased(ignored -> seekToSliderPosition());
    }

    private void configurePlaylistView() {
        playlistListView.setItems(FXCollections.observableArrayList());
        playlistListView.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(PlaylistItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("playlist-current-item");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                boolean current = getIndex() == playlist.currentIndex();
                String missing = item.exists() ? "" : "  · arquivo não encontrado";
                setText((current ? "▶  " : "    ")
                        + String.format("%02d", getIndex() + 1) + "  " + item.displayName() + missing);
                if (current) {
                    getStyleClass().add("playlist-current-item");
                }
            }
        });
        playlistListView.getSelectionModel().selectedIndexProperty().addListener(
                (ignored, oldValue, newValue) -> updatePlaylistEditingButtons());
    }

    private void configureLyricsView() {
        lyricsListView.setItems(FXCollections.observableArrayList());
        lyricsListView.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(LyricLine line, boolean empty) {
                super.updateItem(line, empty);
                if (empty || line == null) {
                    setText(null);
                    return;
                }
                setText(String.format("%02d  %s", getIndex() + 1, line.text()));
            }
        });
    }

    private void configureMixerView() {
        for (int channel = 0; channel < MIDI_CHANNEL_COUNT; channel++) {
            mixerChannelStrips[channel] = createMixerChannelStrip(channel);
        }
        updateMixerControls("Nenhuma música carregada");
        showMixerBank(0);
    }

    private VBox createMixerChannelStrip(int channel) {
        Label activity = new Label("●");
        activity.getStyleClass().add("activity-dot");
        channelActivityLabels[channel] = activity;

        Label channelLabel = new Label(String.format("CH %02d", channel + 1));
        channelLabel.getStyleClass().add("mixer-channel-number");

        Label nameLabel = new Label("Canal " + (channel + 1));
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.getStyleClass().add("mixer-track-name");
        channelNameLabels[channel] = nameLabel;

        Label instrumentLabel = new Label("Sem eventos");
        instrumentLabel.setWrapText(true);
        instrumentLabel.setMaxWidth(Double.MAX_VALUE);
        instrumentLabel.setAlignment(Pos.CENTER);
        instrumentLabel.getStyleClass().add("mixer-instrument-name");
        channelInstrumentLabels[channel] = instrumentLabel;

        Slider volumeSlider = new Slider(0, 127, 100);
        volumeSlider.setOrientation(Orientation.VERTICAL);
        volumeSlider.setBlockIncrement(1);
        volumeSlider.setMajorTickUnit(16);
        volumeSlider.setMaxHeight(Double.MAX_VALUE);
        channelVolumeSliders[channel] = volumeSlider;

        Label volumeLabel = new Label("100");
        volumeLabel.getStyleClass().add("mixer-volume-value");
        channelVolumeLabels[channel] = volumeLabel;

        ToggleButton muteButton = new ToggleButton("M");
        muteButton.getStyleClass().addAll("mixer-state-button", "mute-button");
        muteButton.setMaxWidth(Double.MAX_VALUE);
        muteButton.setOnAction(ignored -> {
            if (engine != null && !updatingMixerControls) {
                engine.setChannelMuted(channel, muteButton.isSelected());
            }
        });
        channelMuteButtons[channel] = muteButton;

        ToggleButton soloButton = new ToggleButton("S");
        soloButton.getStyleClass().addAll("mixer-state-button", "solo-button");
        soloButton.setMaxWidth(Double.MAX_VALUE);
        soloButton.setOnAction(ignored -> {
            if (engine != null && !updatingMixerControls) {
                engine.setChannelSolo(channel, soloButton.isSelected());
            }
        });
        channelSoloButtons[channel] = soloButton;

        HBox stateButtons = new HBox(6, muteButton, soloButton);
        HBox.setHgrow(muteButton, Priority.ALWAYS);
        HBox.setHgrow(soloButton, Priority.ALWAYS);

        volumeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            int volume = (int) Math.round(newValue.doubleValue());
            volumeLabel.setText(Integer.toString(volume));
            if (engine != null && !updatingMixerControls) {
                engine.setChannelVolume(channel, volume);
            }
        });

        VBox strip = new VBox(7,
                activity, channelLabel, nameLabel, instrumentLabel,
                volumeSlider, volumeLabel, stateButtons);
        strip.setAlignment(Pos.TOP_CENTER);
        strip.setMaxWidth(Double.MAX_VALUE);
        strip.getStyleClass().add("mixer-channel-strip");
        VBox.setVgrow(volumeSlider, Priority.ALWAYS);
        HBox.setHgrow(strip, Priority.ALWAYS);
        return strip;
    }

    private void loadMixer(PlaylistItem item) {
        try {
            currentSongAnalysis = MidiSongAnalysis.fromFile(item.path().toFile());
        } catch (IOException | InvalidMidiDataException exception) {
            currentSongAnalysis = MidiSongAnalysis.empty();
        }
        engine.resetChannelMix(currentSongAnalysis.initialVolumes());
        updateMixerControls(item.displayName());
    }

    private void applyOriginalMixerState() {
        if (engine == null || !engine.hasSequence()) {
            return;
        }
        engine.resetChannelMix(currentSongAnalysis.initialVolumes());
        updateMixerControls(playlist.current()
                .map(PlaylistItem::displayName)
                .orElse("Música carregada"));
        statusLabel.setText("MIX ORIGINAL");
    }

    private void updateMixerControls(String songName) {
        updatingMixerControls = true;
        try {
            mixerSongLabel.setText(songName);
            for (int channel = 0; channel < MIDI_CHANNEL_COUNT; channel++) {
                MidiChannelInfo info = currentSongAnalysis.channel(channel);
                channelNameLabels[channel].setText(info.trackName());
                channelNameLabels[channel].setTooltip(new Tooltip(info.trackName()));
                channelInstrumentLabels[channel].setText(info.instrumentName());
                channelInstrumentLabels[channel].setTooltip(new Tooltip(info.instrumentName()));
                channelVolumeSliders[channel].setValue(info.initialVolume());
                channelVolumeLabels[channel].setText(Integer.toString(info.initialVolume()));
                channelMuteButtons[channel].setSelected(false);
                channelSoloButtons[channel].setSelected(false);
                channelActivityLabels[channel].getStyleClass().remove("mixer-active");
                mixerChannelStrips[channel].getStyleClass().remove("mixer-unused-channel");
                if (!info.used()) {
                    mixerChannelStrips[channel].getStyleClass().add("mixer-unused-channel");
                }
            }
        } finally {
            updatingMixerControls = false;
        }
        resetMixerButton.setDisable(engine == null || !engine.hasSequence());
    }

    private void showMixerBank(int bank) {
        currentMixerBank = Math.max(0, Math.min(1, bank));
        mixerBankOneButton.setSelected(currentMixerBank == 0);
        mixerBankTwoButton.setSelected(currentMixerBank == 1);
        mixerChannelsContainer.getChildren().clear();
        int firstChannel = currentMixerBank * MIXER_BANK_SIZE;
        for (int channel = firstChannel; channel < firstChannel + MIXER_BANK_SIZE; channel++) {
            mixerChannelsContainer.getChildren().add(mixerChannelStrips[channel]);
        }
    }

    private void refreshMixerActivity() {
        if (engine == null) {
            return;
        }
        for (int channel = 0; channel < MIDI_CHANNEL_COUNT; channel++) {
            boolean active = engine.isChannelActive(channel);
            List<String> styleClasses = channelActivityLabels[channel].getStyleClass();
            if (active && !styleClasses.contains("mixer-active")) {
                styleClasses.add("mixer-active");
            } else if (!active) {
                styleClasses.remove("mixer-active");
            }
        }
    }

    private void refreshMidiOutputs() {
        if (engine == null) {
            return;
        }
        refreshingOutputs = true;
        try {
            String previousName = midiOutputComboBox.getValue() == null
                    ? null : midiOutputComboBox.getValue().name();
            List<MidiOutputDevice> outputs = engine.listOutputDevices();
            midiOutputComboBox.setItems(FXCollections.observableArrayList(outputs));
            midiOutputComboBox.getSelectionModel().select(findPreferredOutput(outputs, previousName));
            if (outputs.isEmpty()) {
                midiOutputStatusLabel.setText("Nenhuma saída MIDI encontrada");
            }
        } finally {
            refreshingOutputs = false;
        }
        handleMidiOutputSelection();
    }

    private MidiOutputDevice findPreferredOutput(List<MidiOutputDevice> outputs, String previousName) {
        if (previousName != null) {
            for (MidiOutputDevice output : outputs) {
                if (output.name().equals(previousName)) {
                    return output;
                }
            }
        }
        for (MidiOutputDevice output : outputs) {
            String normalizedName = output.name().toLowerCase(Locale.ROOT);
            if (normalizedName.contains("jm-5") || normalizedName.contains("vima")) {
                return output;
            }
        }
        return outputs.isEmpty() ? null : outputs.getFirst();
    }

    private void startProgressUpdates() {
        progressTimeline = new Timeline(new KeyFrame(Duration.millis(150), ignored -> refreshProgress()));
        progressTimeline.setCycleCount(Timeline.INDEFINITE);
        progressTimeline.play();
    }

    private void refreshProgress() {
        if (engine == null || !engine.hasSequence() || progressSlider.isValueChanging()) {
            return;
        }
        long position = engine.getPositionMicroseconds();
        progressSlider.setValue(position);
        currentTimeLabel.setText(formatTime(position));
        refreshLyricsAtTick(engine.getTickPosition());
        refreshMixerActivity();
    }

    private void seekToSliderPosition() {
        if (engine != null && engine.hasSequence()) {
            engine.setPositionMicroseconds((long) progressSlider.getValue());
            currentTimeLabel.setText(formatTime((long) progressSlider.getValue()));
            refreshLyricsAtTick(engine.getTickPosition());
        }
    }

    private void handlePlaybackFinished() {
        if (engine == null || !engine.hasSequence()) {
            return;
        }
        if (playlist.hasNext() && loadPlaylistIndex(playlist.currentIndex() + 1)) {
            if (playbackMode == PlaybackMode.AUTOMATIC) {
                engine.play();
                statusLabel.setText("TOCANDO");
            } else {
                statusLabel.setText("PRONTA");
            }
            updateTransportControls();
            return;
        }

        statusLabel.setText("FINALIZADA");
        progressSlider.setValue(engine.getDurationMicroseconds());
        currentTimeLabel.setText(formatTime(engine.getDurationMicroseconds()));
        updateTransportControls();
    }

    private void refreshPlaylistView(int selectedIndex) {
        playlistListView.getItems().setAll(playlist.items());
        playlistListView.refresh();
        if (selectedIndex >= 0 && selectedIndex < playlist.size()) {
            playlistListView.getSelectionModel().select(selectedIndex);
            playlistListView.scrollTo(selectedIndex);
        }
        playlistCountLabel.setText(playlist.size() == 1
                ? "1 música" : playlist.size() + " músicas");
        savePlaylistButton.setDisable(playlist.isEmpty());
        updatePlaylistFileLabel();
        updatePlaylistEditingButtons();
        updateSongCards();
        updateTransportControls();
    }

    private void updatePlaylistEditingButtons() {
        int selectedIndex = playlistListView.getSelectionModel().getSelectedIndex();
        boolean selected = selectedIndex >= 0;
        removePlaylistButton.setDisable(!selected);
        moveUpButton.setDisable(!selected || selectedIndex == 0);
        moveDownButton.setDisable(!selected || selectedIndex >= playlist.size() - 1);
    }

    private void updateSongCards() {
        playlist.next().ifPresentOrElse(item -> {
            nextSongLabel.setText(item.displayName());
            nextSongHintLabel.setText(playbackMode == PlaybackMode.AUTOMATIC
                    ? "Iniciará automaticamente" : "Ficará preparada ao final");
        }, () -> {
            nextSongLabel.setText(playlist.isEmpty()
                    ? "A playlist ainda está vazia" : "Fim da playlist");
            nextSongHintLabel.setText(playlist.isEmpty()
                    ? "Adicione músicas na área Playlist" : "Nenhuma música depois desta");
        });
        playlistListView.refresh();
    }

    private void updateCurrentSongHint() {
        if (engine != null && engine.hasSequence()) {
            currentSongHintLabel.setText(formatTime(engine.getDurationMicroseconds())
                    + (engine.hasOutput() ? " · pronta para tocar" : " · selecione uma saída MIDI"));
        }
    }

    private void updateTransportControls() {
        boolean available = engine != null;
        boolean loaded = available && engine.hasSequence();
        boolean outputConnected = available && engine.hasOutput();
        boolean running = available && engine.isRunning();

        playButton.setDisable(!loaded || !outputConnected || running);
        pauseButton.setDisable(!running);
        stopButton.setDisable(!loaded);
        progressSlider.setDisable(!loaded);
        panicButton.setDisable(!outputConnected);
        previousButton.setDisable(!playlist.hasPrevious());
        nextButton.setDisable(!playlist.hasNext());
    }

    private void disableMidiControls() {
        midiOutputComboBox.setDisable(true);
        playButton.setDisable(true);
        pauseButton.setDisable(true);
        stopButton.setDisable(true);
        panicButton.setDisable(true);
        progressSlider.setDisable(true);
    }

    private void refreshPlaybackMode() {
        automaticModeToggle.setSelected(playbackMode == PlaybackMode.AUTOMATIC);
        automaticModeToggle.setText(playbackMode.label());
        playbackModeLabel.setText(playbackMode.description());
        updateSongCards();
    }

    private void showPresentation() {
        playlistView.setVisible(false);
        playlistView.setManaged(false);
        lyricsView.setVisible(false);
        lyricsView.setManaged(false);
        mixerView.setVisible(false);
        mixerView.setManaged(false);
        presentationView.setVisible(true);
        presentationView.setManaged(true);
        presentationNavButton.setSelected(true);
    }

    private void loadLyrics(PlaylistItem item) {
        try {
            currentLyrics = MidiLyrics.fromFile(item.path().toFile());
        } catch (IOException | InvalidMidiDataException exception) {
            currentLyrics = MidiLyrics.empty();
        }
        displayedLyricIndex = Integer.MIN_VALUE;
        lyricsSongLabel.setText(item.displayName());
        lyricsListView.getItems().setAll(currentLyrics.lines());
        refreshLyricsAtTick(0);
    }

    private void refreshLyricsAtTick(long tick) {
        int lyricIndex = currentLyrics.currentIndex(tick);
        if (lyricIndex == displayedLyricIndex) {
            return;
        }
        displayedLyricIndex = lyricIndex;

        if (currentLyrics.isEmpty()) {
            setLyricLabels("", "Este arquivo MIDI não contém letras incorporadas", "");
            stageLyricsStatusLabel.setText("SEM LETRA INCORPORADA");
            lyricsCountLabel.setText("Sem letra");
            lyricsListView.getSelectionModel().clearSelection();
            return;
        }

        if (lyricIndex < 0) {
            setLyricLabels("", "Aguardando entrada da letra…", lyricText(0));
            stageLyricsStatusLabel.setText("LETRA SINCRONIZADA · 0 / " + currentLyrics.size());
            lyricsCountLabel.setText("0 / " + currentLyrics.size() + " linhas");
            lyricsListView.getSelectionModel().clearSelection();
            return;
        }

        setLyricLabels(lyricText(lyricIndex - 1), lyricText(lyricIndex), lyricText(lyricIndex + 1));
        stageLyricsStatusLabel.setText("LETRA SINCRONIZADA · "
                + (lyricIndex + 1) + " / " + currentLyrics.size());
        lyricsCountLabel.setText((lyricIndex + 1) + " / " + currentLyrics.size() + " linhas");
        lyricsListView.getSelectionModel().select(lyricIndex);
        lyricsListView.scrollTo(Math.max(0, lyricIndex - 2));
    }

    private void setLyricLabels(String previous, String current, String next) {
        stagePreviousLyricLabel.setText(previous);
        stageCurrentLyricLabel.setText(current);
        stageNextLyricLabel.setText(next);
        lyricsPreviousLabel.setText(previous);
        lyricsCurrentLabel.setText(current);
        lyricsNextLabel.setText(next);
    }

    private String lyricText(int index) {
        return currentLyrics.lineAt(index).map(LyricLine::text).orElse("");
    }

    private void markPlaylistModified() {
        playlistDirty = true;
        updatePlaylistFileLabel();
    }

    private void updatePlaylistFileLabel() {
        String label = currentPlaylistFile == null
                ? "Playlist não salva" : currentPlaylistFile.getFileName().toString();
        playlistFileLabel.setText(label + (playlistDirty ? " · alterações não salvas" : ""));
    }

    private boolean confirmDiscardPlaylistChanges() {
        if (!playlistDirty || playlist.isEmpty()) {
            return true;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "A playlist atual possui alterações não salvas. Deseja descartá-las?",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("SuperMidia Live");
        confirmation.setHeaderText("Abrir outra playlist");
        confirmation.initOwner(playlistListView.getScene().getWindow());
        return confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private int firstExistingItemIndex() {
        for (int index = 0; index < playlist.size(); index++) {
            if (playlist.get(index).exists()) {
                return index;
            }
        }
        return -1;
    }

    private FileChooser midiFileChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Arquivos MIDI", "*.mid", "*.midi", "*.kar", "*.MID", "*.MIDI", "*.KAR"));
        return chooser;
    }

    private void setInitialDirectory(FileChooser chooser) {
        File directory = null;
        if (currentPlaylistFile != null && currentPlaylistFile.getParent() != null) {
            directory = currentPlaylistFile.getParent().toFile();
        } else if (engine != null && engine.getLoadedFile() != null) {
            directory = engine.getLoadedFile().getParentFile();
        }
        if (directory != null && directory.isDirectory()) {
            chooser.setInitialDirectory(directory);
        }
    }

    private Path ensureM3u8Extension(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".m3u8") ? path : Path.of(path.toString() + ".m3u8");
    }

    private void showError(String title, String message, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("SuperMidia Live");
        alert.setHeaderText(title);
        alert.setContentText(message + (exception.getMessage() == null
                ? "" : "\n\nDetalhe: " + exception.getMessage()));
        if (currentSongLabel.getScene() != null) {
            alert.initOwner(currentSongLabel.getScene().getWindow());
        }
        alert.showAndWait();
    }

    private static String formatSemitones(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String formatTime(long microseconds) {
        long totalSeconds = Math.max(0, microseconds) / 1_000_000;
        long hours = totalSeconds / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }
}
