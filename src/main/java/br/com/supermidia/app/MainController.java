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
import javafx.animation.Interpolator;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
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
import javafx.scene.control.MenuButton;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.prefs.Preferences;

public final class MainController {
    private static final int MIDI_CHANNEL_COUNT = 16;
    private static final int MIXER_BANK_SIZE = 8;
    private static final Duration LYRIC_SCROLL_DURATION = Duration.millis(260);
    private static final String AUTOPLAY_PREFERENCE_KEY = "autoplayEnabled";
    @FXML
    private Label currentSongLabel;
    @FXML
    private Label nextSongLabel;
    @FXML
    private Label stageLyricsStatusLabel;
    @FXML
    private Label stagePreviousLyricLabel;
    @FXML
    private Label stageCurrentLyricLabel;
    @FXML
    private Label stageNextLyricLabel;
    @FXML
    private Label stageIncomingLyricLabel;
    @FXML
    private Label statusLabel;
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
    private Label chordValueLabel;

    @FXML
    private ComboBox<MidiOutputDevice> midiOutputComboBox;
    @FXML
    private ListView<PlaylistItem> playlistListView;

    @FXML
    private ToggleButton automaticModeToggle;
    @FXML
    private ToggleButton presentationNavButton;
    @FXML
    private ToggleButton playlistNavButton;
    @FXML
    private ToggleButton settingsNavButton;

    @FXML
    private Slider transposeSlider;
    @FXML
    private Slider speedSlider;
    @FXML
    private Slider masterVolumeSlider;
    @FXML
    private MenuButton transposeMenuButton;
    @FXML
    private MenuButton speedMenuButton;
    @FXML
    private MenuButton masterVolumeMenuButton;
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
    private VBox presentationView;
    @FXML
    private VBox playlistView;
    @FXML
    private VBox settingsView;
    @FXML
    private StackPane lyricsViewport;
    @FXML
    private VBox lyricsLinesContainer;
    @FXML
    private HBox mixerBankOneContainer;
    @FXML
    private HBox mixerBankTwoContainer;

    private final PlaylistManager playlist = new PlaylistManager();
    private final PlaylistFileService playlistFileService = new PlaylistFileService();
    private final Preferences preferences = Preferences.userNodeForPackage(MainController.class);
    private final VBox[] mixerChannelStrips = new VBox[MIDI_CHANNEL_COUNT];
    private final Slider[] channelVolumeSliders = new Slider[MIDI_CHANNEL_COUNT];
    private final Label[] channelVolumeLabels = new Label[MIDI_CHANNEL_COUNT];
    private final Label[] channelInstrumentLabels = new Label[MIDI_CHANNEL_COUNT];
    private final Label[] channelActivityLabels = new Label[MIDI_CHANNEL_COUNT];
    private final ToggleButton[] channelMuteButtons = new ToggleButton[MIDI_CHANNEL_COUNT];
    private final ToggleButton[] channelSoloButtons = new ToggleButton[MIDI_CHANNEL_COUNT];

    private PlaybackMode playbackMode = PlaybackMode.MANUAL;
    private MidiPlaybackEngine engine;
    private Timeline progressTimeline;
    private TranslateTransition lyricScrollTransition;
    private Path currentPlaylistFile;
    private MidiLyrics currentLyrics = MidiLyrics.empty();
    private MidiSongAnalysis currentSongAnalysis = MidiSongAnalysis.empty();
    private int displayedLyricIndex = Integer.MIN_VALUE;
    private boolean updatingMixerControls;
    private boolean playlistDirty;
    private boolean refreshingOutputs;
    private Stage pianoStage;

    @FXML
    private void initialize() {
        configureControlListeners();
        configurePlaylistView();
        configureMixerView();
        configureLyricsViewport();
        setLyricLabels("", "", "");
        playbackMode = loadPlaybackModePreference();
        refreshPlaybackMode();
        refreshPlaylistView(-1);

        try {
            engine = new MidiPlaybackEngine();
            engine.setTranspose((int) Math.round(transposeSlider.getValue()));
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
        stopLyricScrollAnimation();
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
        confirmation.setTitle("SuperMídia MIDI Player");
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
        settingsView.setVisible(false);
        settingsView.setManaged(false);
        playlistView.setVisible(true);
        playlistView.setManaged(true);
        playlistNavButton.setSelected(true);
    }

    @FXML
    private void handleShowSettings() {
        presentationView.setVisible(false);
        presentationView.setManaged(false);
        playlistView.setVisible(false);
        playlistView.setManaged(false);
        settingsView.setVisible(true);
        settingsView.setManaged(true);
        settingsNavButton.setSelected(true);
    }

    @FXML
    private void handleOpenPiano() {
        if (pianoStage == null) {
            Label title = new Label("Piano");
            title.getStyleClass().add("page-title");
            Label message = new Label(
                    "A visualização das notas ativas será adicionada na próxima etapa.");
            message.getStyleClass().add("muted-label");
            message.setWrapText(true);

            VBox content = new VBox(12, title, message);
            content.setAlignment(Pos.CENTER);
            content.getStyleClass().addAll("app-shell", "piano-placeholder");

            Scene scene = new Scene(content, 760, 260);
            scene.getStylesheets().add(
                    getClass().getResource("theme.css").toExternalForm());

            pianoStage = new Stage();
            pianoStage.setTitle("Piano — SuperMídia MIDI Player");
            pianoStage.initOwner(currentSongLabel.getScene().getWindow());
            pianoStage.setScene(scene);
            pianoStage.setMinWidth(520);
            pianoStage.setMinHeight(220);
            pianoStage.setOnHidden(ignored -> pianoStage = null);
        }
        pianoStage.show();
        pianoStage.toFront();
    }

    @FXML
    private void handleShowAbout() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("Sobre o SuperMídia MIDI Player");
        about.setHeaderText("SuperMídia MIDI Player · versão 0.1.0");
        about.setContentText("Player MIDI desenvolvido para apresentações ao vivo.\n\n"
                + "Desenvolvedor: Denis Antonio Rocha\n"
                + "Projeto: https://github.com/neny777/supermidia-midi-player\n\n"
                + "Tipografia Source Sans 3 · licença OFL 1.1");
        about.initOwner(currentSongLabel.getScene().getWindow());
        about.showAndWait();
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
            String outputName = selection == null ? "desconectado" : selection.name();
            midiOutputStatusLabel.setText("MIDI: " + outputName);
            midiOutputStatusLabel.setTooltip(new Tooltip("Saída MIDI: " + outputName));
            updateTransportControls();
        } catch (MidiUnavailableException exception) {
            midiOutputStatusLabel.setText("MIDI: falha de conexão");
            midiOutputStatusLabel.setTooltip(null);
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
        savePlaybackModePreference();
        refreshPlaybackMode();
    }

    private PlaybackMode loadPlaybackModePreference() {
        try {
            return preferences.getBoolean(AUTOPLAY_PREFERENCE_KEY, false)
                    ? PlaybackMode.AUTOMATIC : PlaybackMode.MANUAL;
        } catch (SecurityException exception) {
            return PlaybackMode.MANUAL;
        }
    }

    private void savePlaybackModePreference() {
        try {
            preferences.putBoolean(
                    AUTOPLAY_PREFERENCE_KEY, playbackMode == PlaybackMode.AUTOMATIC);
        } catch (SecurityException ignored) {
            // A preferência é opcional; o player continua funcionando sem persistência.
        }
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
            statusLabel.setText("PANIC");
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
        updateSongCards();
    }

    private void resetLoadedSong() {
        if (engine != null) {
            engine.unload();
        }
        currentSongLabel.setText("Nenhuma música carregada");
        currentSongLabel.setTooltip(null);
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
        refreshLyricsAtTick(0);
        updateSongCards();
        updateTransportControls();
    }

    private void configureControlListeners() {
        configureScaledSlider(transposeSlider, transposeMenuButton, 1,
                MainController::formatSemitones);
        configureScaledSlider(speedSlider, speedMenuButton, 5,
                value -> value + "%");
        configureScaledSlider(masterVolumeSlider, masterVolumeMenuButton, 5,
                value -> value + "%");

        transposeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            int value = roundedSliderValue(newValue.doubleValue(), 1);
            transposeMenuButton.setText(formatSemitones(value));
            if (engine != null) {
                engine.setTranspose(value);
            }
        });
        speedSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            int value = roundedSliderValue(newValue.doubleValue(), 5);
            speedMenuButton.setText(value + "%");
            if (engine != null) {
                engine.setTempoFactor(value / 100.0f);
            }
        });
        masterVolumeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            int value = roundedSliderValue(newValue.doubleValue(), 5);
            masterVolumeMenuButton.setText(value + "%");
            if (engine != null) {
                engine.setMasterVolume(value / 100.0);
            }
        });
        progressSlider.setOnMouseReleased(ignored -> seekToSliderPosition());
    }

    private void configureScaledSlider(Slider slider,
                                       MenuButton menuButton,
                                       int step,
                                       IntFunction<String> formatter) {
        int value = roundedSliderValue(slider.getValue(), step);
        slider.setValue(value);
        menuButton.setText(formatter.apply(value));
    }

    private int roundedSliderValue(double value, int step) {
        return (int) Math.round(value / step) * step;
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

    private void configureMixerView() {
        for (int channel = 0; channel < MIDI_CHANNEL_COUNT; channel++) {
            mixerChannelStrips[channel] = createMixerChannelStrip(channel);
            HBox bank = channel < MIXER_BANK_SIZE
                    ? mixerBankOneContainer : mixerBankTwoContainer;
            bank.getChildren().add(mixerChannelStrips[channel]);
        }
        updateMixerControls("Nenhuma música carregada");
    }

    private VBox createMixerChannelStrip(int channel) {
        Label activity = new Label("●");
        activity.getStyleClass().add("activity-dot");
        channelActivityLabels[channel] = activity;

        Label channelLabel = new Label(String.format("%02d", channel + 1));
        channelLabel.getStyleClass().add("mixer-channel-number");

        Label instrumentLabel = new Label("SEM SOM");
        instrumentLabel.setWrapText(true);
        instrumentLabel.setMaxWidth(Double.MAX_VALUE);
        instrumentLabel.setAlignment(Pos.CENTER);
        instrumentLabel.getStyleClass().add("mixer-instrument-name");
        channelInstrumentLabels[channel] = instrumentLabel;

        Slider volumeSlider = new Slider(0, 127, 100);
        volumeSlider.setOrientation(Orientation.VERTICAL);
        volumeSlider.setBlockIncrement(1);
        volumeSlider.setMajorTickUnit(127.0 / 4.0);
        volumeSlider.setMinorTickCount(1);
        volumeSlider.setShowTickMarks(true);
        volumeSlider.setShowTickLabels(false);
        volumeSlider.setPrefHeight(150);
        volumeSlider.setMaxHeight(170);
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

        HBox stateButtons = new HBox(3, muteButton, soloButton);
        HBox.setHgrow(muteButton, Priority.ALWAYS);
        HBox.setHgrow(soloButton, Priority.ALWAYS);

        volumeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            int volume = (int) Math.round(newValue.doubleValue());
            volumeLabel.setText(Integer.toString(volume));
            if (engine != null && !updatingMixerControls) {
                engine.setChannelVolume(channel, volume);
            }
        });

        VBox strip = new VBox(4,
                channelLabel, instrumentLabel, volumeSlider,
                volumeLabel, stateButtons, activity);
        strip.setAlignment(Pos.TOP_CENTER);
        strip.setMaxWidth(90);
        strip.getStyleClass().add("mixer-channel-strip");
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
            boolean songLoaded = engine != null && engine.hasSequence();
            for (int channel = 0; channel < MIDI_CHANNEL_COUNT; channel++) {
                MidiChannelInfo info = currentSongAnalysis.channel(channel);
                String instrument = songLoaded && info.used()
                        ? abbreviatedInstrument(info, channel) : "";
                channelInstrumentLabels[channel].setText(instrument);
                channelInstrumentLabels[channel].setTooltip(songLoaded && info.used()
                        ? new Tooltip(info.instrumentName() + "\n" + info.trackName()) : null);
                channelVolumeSliders[channel].setValue(
                        songLoaded ? info.initialVolume() : 100);
                channelVolumeLabels[channel].setText(
                        songLoaded ? Integer.toString(info.initialVolume()) : "");
                channelVolumeSliders[channel].setDisable(!songLoaded);
                channelMuteButtons[channel].setDisable(!songLoaded);
                channelSoloButtons[channel].setDisable(!songLoaded);
                channelMuteButtons[channel].setSelected(false);
                channelSoloButtons[channel].setSelected(false);
                channelActivityLabels[channel].setVisible(songLoaded);
                channelActivityLabels[channel].getStyleClass().remove("mixer-active");
                mixerChannelStrips[channel].getStyleClass().remove("mixer-unused-channel");
                if (songLoaded && !info.used()) {
                    mixerChannelStrips[channel].getStyleClass().add("mixer-unused-channel");
                }
            }
        } finally {
            updatingMixerControls = false;
        }
    }

    private String abbreviatedInstrument(MidiChannelInfo info, int channel) {
        if (channel == 9) {
            return "DRUM";
        }
        String name = info.instrumentName().toUpperCase(Locale.ROOT);
        if (name.contains("ELECTRIC PIANO")) {
            return "E.PIANO";
        }
        if (name.contains("PIANO")) {
            return "PIANO";
        }
        if (name.contains("ELECTRIC") && name.contains("BASS")) {
            return "E.BASS";
        }
        if (name.contains("BASS")) {
            return "BASS";
        }
        if (name.contains("GUITAR")) {
            return name.contains("ELECTRIC") ? "E.GUIT" : "GUITAR";
        }
        if (name.contains("STRING")) {
            return "STRINGS";
        }
        if (name.contains("ORGAN")) {
            return "ORGAN";
        }
        if (name.contains("BRASS")) {
            return "BRASS";
        }
        if (name.contains("CHOIR") || name.contains("VOICE")) {
            return "CHOIR";
        }
        if (name.contains("SYNTH")) {
            return "SYNTH";
        }
        String firstWord = name.split("\\s+", 2)[0];
        return firstWord.length() <= 8 ? firstWord : firstWord.substring(0, 8);
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
            nextSongLabel.setTooltip(new Tooltip(item.displayName()));
        }, () -> {
            nextSongLabel.setText(playlist.isEmpty()
                    ? "Playlist vazia" : "Fim da playlist");
            nextSongLabel.setTooltip(null);
        });
        playlistListView.refresh();
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
        updateSongCards();
    }

    private void showPresentation() {
        playlistView.setVisible(false);
        playlistView.setManaged(false);
        settingsView.setVisible(false);
        settingsView.setManaged(false);
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
        refreshLyricsAtTick(0);
    }

    private void refreshLyricsAtTick(long tick) {
        int lyricIndex = currentLyrics.currentIndex(tick);
        if (lyricIndex == displayedLyricIndex) {
            return;
        }

        if (lyricScrollTransition != null) {
            showLyricsImmediately(displayedLyricIndex);
        }
        int previousIndex = displayedLyricIndex;
        displayedLyricIndex = lyricIndex;

        if (currentLyrics.isEmpty()) {
            stopLyricScrollAnimation();
            setLyricLabels("", "", "");
            stageLyricsStatusLabel.setText("SEM LETRA INCORPORADA");
            return;
        }

        if (lyricIndex < 0) {
            stopLyricScrollAnimation();
            setLyricLabels("", "", lyricText(0));
            stageLyricsStatusLabel.setText("LETRA SINCRONIZADA · 0 / " + currentLyrics.size());
            return;
        }

        boolean nextSequentialLine = previousIndex >= -1
                && lyricIndex == previousIndex + 1
                && lyricsLinesContainer.getScene() != null;
        if (nextSequentialLine) {
            animateLyricsForward(lyricIndex);
        } else {
            showLyricsImmediately(lyricIndex);
        }
        stageLyricsStatusLabel.setText("LETRA SINCRONIZADA · "
                + (lyricIndex + 1) + " / " + currentLyrics.size());
    }

    private void configureLyricsViewport() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(lyricsViewport.widthProperty());
        clip.heightProperty().bind(lyricsViewport.heightProperty());
        lyricsViewport.setClip(clip);
    }

    private void animateLyricsForward(int lyricIndex) {
        stopLyricScrollAnimation();
        stageIncomingLyricLabel.setText(lyricText(lyricIndex + 1));

        double lineDistance = stageCurrentLyricLabel.getLayoutY()
                - stagePreviousLyricLabel.getLayoutY();
        if (lineDistance <= 0) {
            lineDistance = 60;
        }

        lyricScrollTransition = new TranslateTransition(
                LYRIC_SCROLL_DURATION, lyricsLinesContainer);
        lyricScrollTransition.setFromY(0);
        lyricScrollTransition.setToY(-lineDistance);
        lyricScrollTransition.setInterpolator(Interpolator.EASE_BOTH);
        lyricScrollTransition.setOnFinished(ignored -> {
            lyricScrollTransition = null;
            lyricsLinesContainer.setTranslateY(0);
            setLyricLabelsForIndex(lyricIndex);
        });
        lyricScrollTransition.play();
    }

    private void showLyricsImmediately(int lyricIndex) {
        stopLyricScrollAnimation();
        if (lyricIndex < 0) {
            setLyricLabels("", "", lyricText(0));
            return;
        }
        setLyricLabelsForIndex(lyricIndex);
    }

    private void setLyricLabelsForIndex(int lyricIndex) {
        setLyricLabels(
                lyricText(lyricIndex - 1),
                lyricText(lyricIndex),
                lyricText(lyricIndex + 1));
    }

    private void stopLyricScrollAnimation() {
        if (lyricScrollTransition != null) {
            lyricScrollTransition.stop();
            lyricScrollTransition = null;
        }
        if (lyricsLinesContainer != null) {
            lyricsLinesContainer.setTranslateY(0);
        }
        if (stageIncomingLyricLabel != null) {
            stageIncomingLyricLabel.setText("");
        }
    }

    private void setLyricLabels(String previous,
                                String current,
                                String next) {
        stagePreviousLyricLabel.setText(previous);
        stageCurrentLyricLabel.setText(current);
        stageCurrentLyricLabel.setVisible(!current.isBlank());
        stageNextLyricLabel.setText(next);
        stageIncomingLyricLabel.setText("");
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
        confirmation.setTitle("SuperMídia MIDI Player");
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
        alert.setTitle("SuperMídia MIDI Player");
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
