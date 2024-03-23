package org.pprrun.pprrregdownloader;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.controlsfx.dialog.Wizard;
import org.controlsfx.dialog.Wizard.LinearFlow;
import org.controlsfx.dialog.WizardPane;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX PPRRRegDownloader
 */
public class PPRRRegDownloader extends Application {

    private static final Logger logger = LoggerFactory.getLogger(PPRRRegDownloader.class);
    private static final UserPrefs userPrefs = UserPrefs.getInstance();

    private JSONObject parsePPRRScoreFieldList(File pprrscoreConfDir, String s) {
        JSONObject fieldList = new JSONObject();
        File confFile = new File(pprrscoreConfDir.getAbsolutePath() + "/" + s);

        try {
            // Parse the file and extract the following:
            // Event Name
            // Event Date
            // Divisions (from BeginDivision:Name )
            // registration file field list (from BeginRegFields)
            String section = "";
            for (String fl : Files.lines(confFile.toPath()).toList()) {
                fl = fl.trim();
                if (!fl.startsWith("#") && !fl.isBlank()) {
                    if (section.isEmpty()) {
                        if (fl.startsWith("Begin")) {
                            section = fl.replace("Begin", "");
                        }

                    } else if (fl.startsWith("End")) {
                        logger.debug("parsePPRRScoreFieldList: End Section: " + fl);
                        section = "";
                    } else {
                        if (section.equals("BasicInfo")) {
                            String[] line = fl.split(":", 2);
                            if (line.length == 2) {
                                fieldList.put(line[0].trim(), line[1].trim());
                                logger.debug("parsePPRRScoreFieldList: Basic Info: {} -> {}", line[0].trim(), line[1].trim());
                            } else {
                                logger.debug("parsePPRRScoreFieldList: Unable to parse {}", fl);
                            }
                        } else if (section.equals("RegFields")) {
                            String[] line = fl.split(",");
                            JSONArray regFields = new JSONArray();
                            if (fieldList.has(section)) {
                                regFields = fieldList.getJSONArray(section);
                            } else {
                                fieldList.put(section, regFields);
                            }
                            regFields.put(line[0].trim());
                        } else if (section.equals("Division")) {
                            String[] line = fl.split(":", 2);
                            if (line.length == 2 && line[0].trim().equals("Name")) {
                                JSONArray divs = new JSONArray();
                                if (fieldList.has(section)) {
                                    divs = fieldList.getJSONArray(section);
                                } else {
                                    fieldList.put(section, divs);
                                }
                                divs.put(line[1].trim());
                            }
                        }
                    }
                }
            }

        } catch (IOException ex) {
            logger.error("Error reading {}", confFile.getAbsolutePath());
        }

        // Create a few composite entries
        if (!fieldList.isEmpty()) {
            // Event Date:
            String date = fieldList.optString("Year") + "-" + fieldList.optString("RaceDay").replace("/", "-");
            fieldList.put("EventDate", date);

            // Registration File Location
            String regFile = fieldList.optString("Year") + fieldList.optString("Abbrev") + "AllRegistrations.csv";
            fieldList.put("RegFile", regFile);
        }

        logger.debug("parsePPRRScoreFieldList: resulting JSONObject: {}", fieldList.toString(4));
        // Return as a JSONObject that we can use downstream

        return fieldList;
    }

    //private static Stage mainStage;
    record Registration(
            String div,
            String bib,
            String first_name,
            String last_name,
            String city,
            String state,
            String ctry,
            String sex,
            Integer age,
            Map<String, String> questions) {

    }
    ;
    
    
    
    private static final ObservableList<Registration> regList = FXCollections.observableArrayList();

    //private Preferences prefs = Preferences.getInstance();
    @Override
    public void start(Stage primaryStage) {
        JSONObject config = new JSONObject();

        primaryStage.setTitle("RSU Reg Downloader");

        primaryStage.setWidth(600);
        primaryStage.setHeight(400);

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.TOP_CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(4, 4, 4, 4));
        grid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        grid.setGridLinesVisible(false);
        ColumnConstraints cc = new ColumnConstraints();
        cc.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(cc);
        RowConstraints rc0 = new RowConstraints();
        RowConstraints rc1 = new RowConstraints();
        RowConstraints rc2 = new RowConstraints();
        rc2.setVgrow(Priority.ALWAYS);
        RowConstraints rc3 = new RowConstraints();
        grid.getRowConstraints().addAll(rc0, rc1, rc2, rc3);

        // title bar
        Label title = new Label("PPRR Registration Downloader");
        title.setFont(new Font(24));
        HBox titleHBox = new HBox(title);
        titleHBox.setAlignment(Pos.CENTER);
        grid.add(titleHBox, 0, 0);

        // Action / Search Bar
        Button setupButton = new Button("Setup");
        TextField searchTextField = new TextField();
        searchTextField.setPrefWidth(200);
        Pane spring1 = new Pane();
        HBox actionHBox = new HBox(setupButton, spring1, new Label("Search:"), searchTextField);
        actionHBox.setAlignment(Pos.CENTER);
        actionHBox.setSpacing(2);
        HBox.setHgrow(spring1, Priority.ALWAYS);
        actionHBox.setMaxWidth(Double.MAX_VALUE);
        grid.add(actionHBox, 0, 1);

        // Participants Table
        TableView registrationsTableView = new TableView();
        grid.add(registrationsTableView, 0, 2);

        // Bottom Bar
        Button refreshButton = new Button("Refresh");
        Button saveButton = new Button("Save");

        Pane spring2 = new Pane();
        HBox.setHgrow(spring2, Priority.ALWAYS);

        HBox bottomHBox = new HBox(refreshButton, spring2, saveButton);
        grid.add(bottomHBox, 0, 3);

        Scene scene = new Scene(grid);

        Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();

        //set Stage boundaries so that the main screen is centered.                
        primaryStage.setX((primaryScreenBounds.getWidth() - primaryStage.getWidth()) / 2);
        primaryStage.setY((primaryScreenBounds.getHeight() - primaryStage.getHeight()) / 2);

        // F11 to toggle fullscreen mode
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F11), () -> {
            primaryStage.setFullScreen(primaryStage.fullScreenProperty().not().get());
        });

//        // Icons
//        String[] sizes = {"256","128","64","48","32"};
//        for(String s: sizes){
//            primaryStage.getIcons().add(new Image("resources/icons/Pika_"+s+".ico"));
//            primaryStage.getIcons().add(new Image("resources/icons/Pika_"+s+".png"));
//        }
        // Action setup
        // Setup Button
        setupButton.setOnAction((event) -> showSetupWizard());

        primaryStage.setScene(scene);
        primaryStage.show();

    }

    public static void main(String[] args) {
        launch();
    }

    //Utility method for the ControlsFX Wizard
    private TextField createTextField(String id) {
        TextField textField = new TextField();
        textField.setId(id);
        GridPane.setHgrow(textField, Priority.ALWAYS);
        return textField;
    }

    private void saveRegToFile() {

    }

    private void downloadReg() {
        // clear existing registrations

        // background thread time
        Thread.ofVirtual().start(() -> {
            // login if we don't have a valid token

            // for each race / event map entry, snag the participants
            // crete the registration record
            // add it to the registration array
            // add the registration array to the observable list
            Platform.runLater(() -> {
            });

        });

    }

    private void showSetupWizard() {
        // Wizard flow:
        // Page 1: PPRRScore Location
        // Page 2: RSU Login
        // Page 3: RSU Race Selection
        // Page 4: Map RSU Event -> Div
        // Page 5: Map RSU Attributes to the registration field list

        // Wizard variables
        final JSONObject setupData = new JSONObject();
        List<WizardPane> wizardPanes = new ArrayList();

        Wizard wizard = new Wizard();
        wizard.setTitle("Setup");

        BooleanProperty pane1OkayToGo = new SimpleBooleanProperty(false);

        //////////////////
        // Wizard Pane 1: PPRRScore Directory Location
        // 
        // Request the target dir
        // Flip the okayToGo flag if it contains the PPRRScore config files
        // Display the Race Name and Date 
        TextField pprrscoreDirTextField = createTextField("pprrscoreDir");
        Button targetDirButton = new Button("Select");
        Label raceNameLabel = new Label("");
        Label raceDateLabel = new Label("");

        targetDirButton.setOnAction((event) -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("PPRRScore Directory");
            chooser.setInitialDirectory(userPrefs.getPPRRScoreDir());
            File selectedDirectory = chooser.showDialog(targetDirButton.getScene().getWindow());
            if (selectedDirectory != null) {
                pprrscoreDirTextField.setText(selectedDirectory.getAbsolutePath());
            }
            logger.debug("Selected PPRRScoreDir {}", selectedDirectory.getAbsoluteFile());
        });
        pprrscoreDirTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            logger.debug("pprrscoreDirTextField changed from " + oldValue + " to " + newValue);
            pane1OkayToGo.setValue(false);
            File pprrscoreConfDir = new File(newValue + "/config/");
            if (pprrscoreConfDir.exists() && pprrscoreConfDir.isDirectory()) {
                for (String s : pprrscoreConfDir.list()) {
                    if (s.contains("FieldList.txt")) {
                        logger.info("Found PPRRScore FieldList file at {}/{}", pprrscoreConfDir.getAbsolutePath(), s);
                        pane1OkayToGo.setValue(true);
                        setupData.put("PPRRScoreDir", pprrscoreDirTextField.getText());
                        setupData.put("PPRRScoreConfFile", s);
                        JSONObject fieldList = parsePPRRScoreFieldList(pprrscoreConfDir, s);
                        setupData.put("PPRRScoreFieldList", fieldList);
                        raceNameLabel.setText(fieldList.optString("Racename"));
                        raceDateLabel.setText(fieldList.getString("EventDate"));

                        break;
                    } else if (s.equals("rsuDownloaderConfig.json")) {
                        // open the rsuDownloaderConfig.json file 
                        //setupData.put("rsuConfig", Files.lines(sdaf));
                    }
                }
            } else {
                logger.debug("{} is not a valid directory path", newValue);
                pane1OkayToGo.setValue(false);
                raceNameLabel.setText("");
                raceDateLabel.setText("");
            }
        });

        int row = 0;

        GridPane page1Grid = new GridPane();
        page1Grid.setVgap(10);
        page1Grid.setHgap(10);

        Label pd = new Label("PPRRScore Directory:");
        GridPane.setHalignment(pd, HPos.RIGHT);
        page1Grid.add(pd, 0, row);

        HBox targetLocationHBox = new HBox(pprrscoreDirTextField, targetDirButton);
        targetLocationHBox.setSpacing(4);
        page1Grid.add(targetLocationHBox, 1, row++);

        Label rl = new Label("Race Name:");
        GridPane.setHalignment(rl, HPos.RIGHT);

        page1Grid.add(rl, 0, row);
        page1Grid.add(raceNameLabel, 1, row++);
        Label rd = new Label("Race Date:");
        GridPane.setHalignment(rd, HPos.RIGHT);

        page1Grid.add(rd, 0, row);
        page1Grid.add(raceDateLabel, 1, row++);

        GridPane.setHalignment(raceNameLabel, HPos.LEFT);
        GridPane.setHalignment(raceDateLabel, HPos.LEFT);

        WizardPane page1 = new WizardPane() {
            @Override
            public void onEnteringPage(Wizard wizard) {
                wizard.invalidProperty().bind(pane1OkayToGo.not());

            }

            @Override
            public void onExitingPage(Wizard wizard) {
                // Save pprrscoreDir to pprrscoreDir
                wizard.invalidProperty().unbind();
                // If there is an existing app config, read it in
                // Read in the field list and save any changes to the config
            }
        };
        wizardPanes.add(page1);
        page1.setHeaderText("Select the PPRRScore Directory");
        page1.setContent(page1Grid);

        //////////////////
        // Wizard Pane 2: RSU Login Information
        // 
        // Username, password
        // onExit, do a login and stash the temp key and secret
        BooleanProperty pane2OkayToGo = new SimpleBooleanProperty(false);
        row = 0;

        GridPane page2Grid = new GridPane();
        page2Grid.setVgap(10);
        page2Grid.setHgap(10);

        page2Grid.add(new Label("Username:"), 0, row);
        TextField rsuUsernameTextField = createTextField("rsuUsername");
        rsuUsernameTextField.setText(userPrefs.getGlobalPrefs("Username"));
        page2Grid.add(rsuUsernameTextField, 1, row++);

        page2Grid.add(new Label("Password:"), 0, row);
        TextField rsuPasswordTextField = createTextField("rsuPassword");
        rsuPasswordTextField.setText(userPrefs.getRSUPassword());
        page2Grid.add(rsuPasswordTextField, 1, row++);

        page2Grid.add(new Label("Status:"), 0, row);
        Button validateButton = new Button("Validate");
        Label loginSuccessLabel = new Label("Unchecked");
        Pane loginSpring = new Pane();
        HBox.setHgrow(loginSpring, Priority.ALWAYS);
        HBox validateHBox = new HBox(loginSuccessLabel, loginSpring, validateButton);
        validateHBox.setSpacing(4);
        page2Grid.add(validateHBox, 1, row++);

        // If the username or password fields change, force a re-validation
        rsuUsernameTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            pane2OkayToGo.setValue(false);
        });
        rsuPasswordTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            pane2OkayToGo.setValue(false);
        });

        validateButton.setOnAction((event) -> {
            StringBuilder postData = new StringBuilder();
            postData.append("email=");
            postData.append(URLEncoder.encode(rsuUsernameTextField.getText(), StandardCharsets.UTF_8));
            postData.append("&password=");
            postData.append(URLEncoder.encode(rsuPasswordTextField.getText(), StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://runsignup.com/Rest/login?format=json&supports_nb=T"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(BodyPublishers.ofString(postData.toString()))
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JSONObject rsuResponse = new JSONObject(response.body());
                    if (rsuResponse.has("tmp_key")) {
                        pane2OkayToGo.setValue(true);
                        loginSuccessLabel.setText("Valid");
                        logger.debug("RSU Response: {}", response.body());

                        setupData.put("rsuUsername", rsuUsernameTextField.getText());
                        setupData.put("rsuPassword", rsuPasswordTextField.getText());
                        setupData.put("rsuTempKey", rsuResponse.get("tmp_key"));
                        setupData.put("rsuTempSecret", rsuResponse.get("tmp_secret"));
                        logger.debug(" RSU Temp Key/Secret: {} / {}", rsuResponse.get("tmp_key"), rsuResponse.get("tmp_secret"));
                    } else {
                        logger.error("Error in RSU Login: {} ", response.body());
                        pane2OkayToGo.setValue(false);
                        loginSuccessLabel.setText("Invalid Username or Password!");
                    }
                } else {
                    logger.error("Error in RSU Login: {} ", response.body());
                    pane2OkayToGo.setValue(false);
                    loginSuccessLabel.setText("Invalid Username or Password!");
                }

            } catch (Exception ex) {
                logger.error("Exception in HttpClient response: ", ex);
                pane2OkayToGo.setValue(false);
                loginSuccessLabel.setText("Error in RSU Login Request");
            }

        });

        final WizardPane page2 = new WizardPane() {
            @Override
            public void onEnteringPage(Wizard wizard) {
                wizard.invalidProperty().bind(pane2OkayToGo.not());
            }

            @Override
            public void onExitingPage(Wizard wizard) {
                wizard.invalidProperty().unbind();
            }
        };
        wizardPanes.add(page2);

        page2.setContent(page2Grid);
        page2.setHeaderText("RunSignUp Login");

        // Page 3: Get list of races from RSU 
        // And prompt the user to select the Race. Snag the race_event_days_id that is between the start_date and end_date for the event
        row = 0;

        GridPane page3Grid = new GridPane();
        page3Grid.setVgap(10);
        page3Grid.setHgap(10);

        record races(String name, Integer raceID, JSONObject details) {

            @Override
            public String toString() {
                return name;
            }
        }

        ObservableList<races> raceList = FXCollections.observableArrayList();
        ListView<races> raceListView = new ListView(raceList);
        raceListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        raceListView.setPrefHeight(250);
        raceListView.setMinHeight(250);
        raceListView.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(raceListView, Priority.ALWAYS);

        page3Grid.add(raceListView, 0, row);

        setupData.put("race_event_days_id", 0);
        raceListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            LocalDate raceDate = LocalDate.parse(setupData.getJSONObject("PPRRScoreFieldList").getString("EventDate"), DateTimeFormatter.ofPattern("yyyy-M-d"));
            newValue.details.getJSONArray("race_event_days").forEach((r) -> {
                if (r instanceof JSONObject eventDays) {
                    LocalDate eventStart = LocalDate.parse(eventDays.getString("start_date"), DateTimeFormatter.ofPattern("M/d/yyyy 00:00"));
                    LocalDate eventEnd = LocalDate.parse(eventDays.getString("end_date"), DateTimeFormatter.ofPattern("M/d/yyyy 00:00"));
                    Integer raceEventDaysId = eventDays.getInt("race_event_days_id");
                    if (eventStart.compareTo(raceDate) <= 0 && eventEnd.compareTo(raceDate) >= 0) {
                        logger.debug("Event Days: {} ({}) is between {} and {}", raceDate, raceEventDaysId, eventStart, eventEnd);
                        setupData.put("race_event_days_id", raceEventDaysId);
                        setupData.put("race_id", newValue.raceID);
                    } else {
                        logger.debug("Event Days: {} ({}) is NOT between {} and {}", raceDate, raceEventDaysId, eventStart, eventEnd);
                    }
                }
            });
        });

        final WizardPane page3 = new WizardPane() {
            @Override
            public void onEnteringPage(Wizard wizard) {
                wizard.invalidProperty().bind(raceListView.getSelectionModel().selectedItemProperty().isNull());

                raceList.clear();

                // get the list of from RSU
                // https://runsignup.com/Rest/races?
                // tmp_key=KEY&tmp_secret=SECRET
                // &format=json&include_event_days=F&page=1&results_per_page=50&sort=name+ASC
                // &start_date=2024-05-27&end_date=2024-05-27
                StringBuilder requestURL = new StringBuilder();
                requestURL.append("https://runsignup.com/Rest/races");
                requestURL.append("?tmp_key=").append(setupData.get("rsuTempKey"));
                requestURL.append("&tmp_secret=").append(setupData.get("rsuTempSecret"));
                requestURL.append("&format=json").append("&include_event_days=T");
                requestURL.append("&page=1&results_per_page=50&sort=name+ASC");
                requestURL.append("&start_date=").append(setupData.getJSONObject("PPRRScoreFieldList").getString("EventDate"));
                requestURL.append("&end_date=").append(setupData.getJSONObject("PPRRScoreFieldList").getString("EventDate"));

                logger.debug("RSU Get Races Request URL: {}", requestURL.toString());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(requestURL.toString()))
                        .build();

                HttpClient client = HttpClient.newHttpClient();

                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    logger.debug("RSU Response: {} ", response.body());

                    if (response.statusCode() == 200) {
                        JSONObject rsuResponse = new JSONObject(response.body());
                        if (rsuResponse.has("races")) {
                            rsuResponse.getJSONArray("races").forEach((r) -> {
                                if (r instanceof JSONObject rObj) {
                                    JSONObject race = rObj.getJSONObject("race"); // FFS
                                    races raceRecord = new races(URLDecoder.decode(race.getString("name"), StandardCharsets.UTF_8), race.getInt("race_id"), race);
                                    logger.debug("Found Race: {} ({})", URLDecoder.decode(race.getString("name"), StandardCharsets.UTF_8), race.getInt("race_id"));
                                    raceList.add(raceRecord);
                                }
                            });
                        } else {
                            logger.error("Error in RSU Login: {} ", response.body());
                        }
                    } else {
                        logger.error("Error in RSU Login: {} ", response.body());
                    }
                } catch (Exception ex) {
                    logger.error("Exception in HttpClient response: ", ex);
                }
            }

            @Override
            public void onExitingPage(Wizard wizard) {
                wizard.invalidProperty().unbind();

            }
        };
        wizardPanes.add(page3);

        page3.setContent(page3Grid);
        page3.setHeaderText("Select RSU Race");

        ///////////
        // Page 4: Get the race details based on the race_event_days_id from #3
        // Filter event_id's based on the start_end times and create event records
        // Show each event and prompt the user to map each the PPRRScore Division (or set to Ignore)
        GridPane page4Grid = new GridPane();
        page4Grid.setVgap(10);
        page4Grid.setHgap(10);

        // https://runsignup.com/Rest/race/4863
        // ?tmp_key=V7LlOhvsBQAriLsq8P3aG3xITwaj819R&tmp_secret=b2pjHi20LLAzE11dTRUkUantIPFjHmQy
        // &format=json&future_events_only=F&most_recent_events_only=F
        // &race_event_days_id=283700
        // &race_headings=F&race_links=F&include_waiver=F&include_multiple_waivers=F
        // &include_participant_caps=F&include_age_based_pricing=F&include_giveaway_details=T
        //&include_questions=T&include_addons=F&include_membership_settings=F
        //&include_corral_settings=F&include_donation_settings=F&include_extra_date_info=T
        // build up the list of available questions for step 5
        record event(String Name, Integer eventID, StringProperty div) {

        }

        ObservableList<event> eventList = FXCollections.observableArrayList();

        TableView<event> eventTable = new TableView(eventList);
        eventTable.setEditable(true);
        eventTable.setPrefHeight(250);
        eventTable.setMinHeight(250);
        eventTable.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(eventTable, Priority.ALWAYS);

        TableColumn<event, String> eventNameTablecolumn = new TableColumn<>("Event");
        eventNameTablecolumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().Name));

        TableColumn<event, String> divisionTableColumn = new TableColumn<>("PPRRScore Division");
        divisionTableColumn.setCellValueFactory(cellData -> cellData.getValue().div);
        divisionTableColumn.setEditable(true);

        eventTable.getColumns().add(eventNameTablecolumn);
        eventTable.getColumns().add(divisionTableColumn);

        page4Grid.add(eventTable, 0, 0);

        final WizardPane page4 = new WizardPane() {
            @Override
            public void onEnteringPage(Wizard wizard) {

                // List of possible divisions from PPRRScore: 
                List<String> pprrscoreDivisions = new ArrayList();

                setupData.getJSONObject("PPRRScoreFieldList").getJSONArray("Division").iterator().forEachRemaining(e -> {
                    if (e instanceof String div) {
                        pprrscoreDivisions.add(div);
                    }
                });
                pprrscoreDivisions.add("IGNORE");

                // Setup the cell factory for the divisions
                divisionTableColumn.setCellFactory(tc -> {
                    ComboBox<String> combo = new ComboBox<>();
                    combo.getItems().addAll(pprrscoreDivisions);
                    TableCell<event, String> cell = new TableCell<event, String>() {
                        @Override
                        protected void updateItem(String reason, boolean empty) {
                            super.updateItem(reason, empty);
                            if (empty) {
                                setGraphic(null);
                            } else {
                                combo.setValue(reason);
                                setGraphic(combo);
                            }
                        }
                    };
                    combo.setOnAction(e -> {
                        tc.getTableView().getItems().get(cell.getIndex()).div.setValue(combo.getValue());
                    });
                    return cell;
                });

                eventList.clear();

                // get the Race Details from RSU
                // https://runsignup.com/Rest/race/4863
                // ?tmp_key=V7LlOhvsBQAriLsq8P3aG3xITwaj819R&tmp_secret=b2pjHi20LLAzE11dTRUkUantIPFjHmQy
                // &format=json&future_events_only=F&most_recent_events_only=F
                // &race_event_days_id=283700
                // &race_headings=F&race_links=F&include_waiver=F&include_multiple_waivers=F
                // &include_participant_caps=F&include_age_based_pricing=F&include_giveaway_details=T
                //&include_questions=T&include_addons=F&include_membership_settings=F
                //&include_corral_settings=F&include_donation_settings=F&include_extra_date_info=T
                StringBuilder requestURL = new StringBuilder();
                requestURL.append("https://runsignup.com/Rest/race/");
                requestURL.append(setupData.get("race_id"));
                requestURL.append("?tmp_key=").append(setupData.get("rsuTempKey"));
                requestURL.append("&tmp_secret=").append(setupData.get("rsuTempSecret"));
                requestURL.append("&format=json").append("&future_events_only=F&most_recent_events_only=F");
                requestURL.append("&race_event_days_id=").append(setupData.get("race_event_days_id"));
                requestURL.append("&include_questions=T");

                logger.debug("RSU Get Race Request URL: {}", requestURL.toString());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(requestURL.toString()))
                        .build();

                HttpClient client = HttpClient.newHttpClient();

                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    logger.debug("RSU Response: {} ", response.body());

                    if (response.statusCode() == 200) {
                        JSONObject rsuResponse = new JSONObject(response.body());
                        if (rsuResponse.has("race")) {
                            LocalDate raceDate = LocalDate.parse(setupData.getJSONObject("PPRRScoreFieldList").getString("EventDate"), DateTimeFormatter.ofPattern("yyyy-M-d"));
                            rsuResponse.getJSONObject("race").getJSONArray("events").forEach((r) -> {
                                if (r instanceof JSONObject event) {
                                    LocalDate eventStart = LocalDate.parse(event.getString("start_time").replaceAll(" ..:..", ""), DateTimeFormatter.ofPattern("M/d/yyyy"));
                                    LocalDate eventEnd = LocalDate.parse(event.getString("end_time").replaceAll(" ..:..", ""), DateTimeFormatter.ofPattern("M/d/yyyy"));
                                    if (eventStart.compareTo(raceDate) <= 0 && eventEnd.compareTo(raceDate) >= 0) {
                                        event eventRecord = new event(URLDecoder.decode(event.getString("name"), StandardCharsets.UTF_8), event.getInt("event_id"), new SimpleStringProperty("IGNORE"));
                                        // TODO: use event -> Div map for the event division
                                        logger.debug("Found Event: {} ({})", URLDecoder.decode(event.getString("name"), StandardCharsets.UTF_8), event.getInt("event_id"));
                                        eventList.add(eventRecord);
                                    } else {
                                        logger.debug("Event {} ({}) is NOT on {}", event.getString("name"), event.getInt("event_id"), eventStart, eventEnd);
                                    }
                                }
                            });
                        } else {
                            logger.error("Error in RSU Login: {} ", response.body());
                        }
                    } else {
                        logger.error("Error in RSU Login: {} ", response.body());
                    }
                } catch (Exception ex) {
                    logger.error("Exception in HttpClient response: ", ex);
                }
            }

            @Override
            public void onExitingPage(Wizard wizard) {
                wizard.invalidProperty().unbind();
                eventList.forEach(e -> logger.debug(" Event: {} -> {}", e.Name, e.div.getValue()));
            }
        };
        wizardPanes.add(page4);

        page4.setContent(page4Grid);
        page4.setHeaderText("Select RSU Race");

        /////////////////////////////////
        // Page 5: Map the RSU attributes 
        // For each registration attribute, select an RSU source (native registration field or question/givaway source if available)
        // Set all of the panes to the same height to make this a bit nicer
//        Double width = page1.getWidth();
//        Double height = page1.getHeight();
//        for (WizardPane p : wizardPanes) {
//            width = (width > p.getWidth()) ? width : p.getWidth();
//            height = (height > p.getHeight()) ? height : p.getHeight();
//        }
        for (WizardPane p : wizardPanes) {
            p.setMinSize(600, 400);
        }

        wizard.setFlow(new LinearFlow(wizardPanes));

        // show wizard and wait for response
        wizard.showAndWait();

        // Save the rsu Username/Password to the global prefs
        // Save the PPRRScore Race directory to the global prefs
        // Save the mapping config to the rsuDownloader.json file in the PPRRScore race directory
    }

}
