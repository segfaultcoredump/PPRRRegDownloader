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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.controlsfx.dialog.Wizard;
import org.controlsfx.dialog.Wizard.LinearFlow;
import org.controlsfx.dialog.WizardPane;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

/**
 * JavaFX PPRRRegDownloader
 */
public class PPRRRegDownloader extends Application {

    private static final Logger logger = LoggerFactory.getLogger(PPRRRegDownloader.class);
    private static final UserPrefs userPrefs = UserPrefs.getInstance();
    private static final JSONObject mainConfig = new JSONObject();

    private static final StringProperty eventName = new SimpleStringProperty("TBD");
    private static final StringProperty eventDate = new SimpleStringProperty("TBD");

    //private static Stage mainStage;
    record Registration(Map<String, String> fieldlist, String searchString) {

        String getVal(String attr) {
            return fieldlist.containsKey(attr) ? fieldlist.get(attr) : "";
        }
    }
    ;
    
    private static final ObservableList<Registration> registrationList = FXCollections.observableArrayList();
    private static final FilteredList<Registration> filteredRegistrationList = new FilteredList<>(registrationList, p -> true);
    private static final TableView<Registration> registrationsTableView = new TableView();
    private static final TextField searchTextField = new TextField();
    
    private static final Map<String, String> cityTyposMap = new HashMap();


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
        RowConstraints rc3 = new RowConstraints();
        rc3.setVgrow(Priority.ALWAYS);
        grid.getRowConstraints().addAll(rc0, rc1, rc2, rc3);

        int row = 0;
        // title bar
        Label title = new Label("PPRR Registration Downloader");
        title.setFont(new Font(24));
        HBox titleHBox = new HBox(title);
        titleHBox.setAlignment(Pos.CENTER);
        grid.add(titleHBox, 0, row++);

        // Event details
        Label eventNameLabel = new Label();
        eventNameLabel.textProperty().bind(eventName);
        Label eventDateLabel = new Label();
        eventDateLabel.textProperty().bind(eventDate);
        eventNameLabel.setFont(new Font(16));
        eventDateLabel.setFont(new Font(16));
        HBox eventInfoHBox = new HBox(eventNameLabel, eventDateLabel);
        eventInfoHBox.setSpacing(5);
        eventInfoHBox.setAlignment(Pos.CENTER);
        grid.add(eventInfoHBox, 0, row++);

        // Action / Search Bar
        Button setupButton = new Button("Setup");

        searchTextField.setPrefWidth(200);
        Pane spring1 = new Pane();
        HBox actionHBox = new HBox(setupButton, spring1, new Label("Search:"), searchTextField);
        actionHBox.setAlignment(Pos.CENTER);
        actionHBox.setSpacing(2);
        HBox.setHgrow(spring1, Priority.ALWAYS);
        actionHBox.setMaxWidth(Double.MAX_VALUE);
        grid.add(actionHBox, 0, row++);

        // Participants Table
        Label filteredSizeLabel = new Label();
        Label listSizeLabel = new Label();
        HBox counterHBox = new HBox(filteredSizeLabel, new Label("/"), listSizeLabel);
        counterHBox.setSpacing(1);
        counterHBox.setAlignment(Pos.CENTER_RIGHT);

        VBox tableVBox = new VBox(registrationsTableView, counterHBox);
        grid.add(tableVBox, 0, row++);

        // Bottom Bar
        Button refreshButton = new Button("Refresh from RSU");
        refreshButton.setOnAction(event -> downloadReg());
        Button saveButton = new Button("Save to AllReg");
        saveButton.setOnAction(event -> saveRegToFile());

        Pane spring2 = new Pane();
        HBox.setHgrow(spring2, Priority.ALWAYS);

        HBox bottomHBox = new HBox(refreshButton, spring2, saveButton);
        grid.add(bottomHBox, 0, row++);

        Scene scene = new Scene(grid);

        Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();

        //set Stage boundaries so that the main screen is centered.                
        primaryStage.setX((primaryScreenBounds.getWidth() - primaryStage.getWidth()) / 2);
        primaryStage.setY((primaryScreenBounds.getHeight() - primaryStage.getHeight()) / 2);

        // F11 to toggle fullscreen mode
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F11), () -> {
            primaryStage.setFullScreen(primaryStage.fullScreenProperty().not().get());
        });

        // Icons
        primaryStage.getIcons().add(new Image(getClass().getResource("/icons/icon.png").toExternalForm()));
        primaryStage.getIcons().add(new Image(getClass().getResource("/icons/icon.ico").toExternalForm()));

        // Search Box stuff
        // 2. Set the filter Predicate whenever the filter changes.
        searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            updateFilterPredicate();
        });

        // 3. Wrap the FilteredList in a SortedList. 
        SortedList<Registration> sortedData = new SortedList<>(filteredRegistrationList);

        // 4. Bind the SortedList comparator to the TableView comparator.
        sortedData.comparatorProperty().bind(registrationsTableView.comparatorProperty());

        // 5. Add sorted (and filtered) data to the table.
        registrationsTableView.setItems(sortedData);
        // Set the filter up

        // Bind the count to the regCountLabel
        listSizeLabel.textProperty().bind(Bindings.size(registrationList).asString());
        filteredSizeLabel.textProperty().bind(Bindings.size(filteredRegistrationList).asString());

        // Action setup
        // Setup Button
        setupButton.setOnAction((event) -> showSetupWizard());

        primaryStage.setScene(scene);
        primaryStage.show();

        showSetupWizard();

    }

    public static void main(String[] args) {
        launch();
    }

    private void saveRegToFile() {

        Path regFile = Paths.get(mainConfig.get("PPRRScoreDir") + "/Regs/" + mainConfig.getString("RegFile"));
        logger.debug("Saving Registrations to " + regFile.toString());

        List<String> output = new ArrayList();
        List<String> fieldlist = new ArrayList();

        // Headder line
        StringBuilder line = new StringBuilder();
        mainConfig.getJSONArray("fieldlist").forEach(f -> {
            if (f instanceof String x) {
                line.append(x).append(",");
                fieldlist.add(x);
            }

        });
        output.add(line.toString().substring(0, line.length() - 1));

        registrationList.forEach(r -> {
            StringBuilder reg = new StringBuilder();
            fieldlist.forEach(f -> {
                reg.append(r.getVal(f)).append(",");
            });
            output.add(StringUtils.stripAccents(reg.toString().substring(0, reg.length() - 1)));

        });

        try {
            // Save registration data to the allreg file

            Files.write(regFile, output);
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("File Saved");
            alert.setHeaderText(null);
            alert.setContentText("Saved registrations to " + regFile.toString());

            alert.showAndWait();
        } catch (IOException ex) {
            logger.error("ERROR: Unable to write to {}", regFile.toString(), ex);
        }

    }

    private void setupTableColumns() {
        // Clear the existing table columns
        registrationsTableView.getColumns().clear();

        mainConfig.getJSONArray("fieldlist").forEach(s -> {
            if (s instanceof String col) {
                TableColumn<Registration, String> newTC = new TableColumn<>(col);
                newTC.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getVal(col)));
                registrationsTableView.getColumns().add(newTC);
            }
        });
        registrationsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    private void updateFilterPredicate() {
        logger.debug("updateFilterPredicate: {}", searchTextField.textProperty().getValueSafe());

        // I have zero idea why I need to clear it for it to work
        filteredRegistrationList.setPredicate(reg -> true);

        filteredRegistrationList.setPredicate(reg -> {
            // If filter text is empty, display all persons.
            if (searchTextField.textProperty().getValueSafe().isEmpty()) {
                logger.debug("Empty search text field");
                return true;
            }
            // Compare first name and last name of every person with filter text.
            String lowerCaseFilter = "(.*)(" + searchTextField.textProperty().getValueSafe() + ")(.*)";
            try {
                Pattern pattern = Pattern.compile(lowerCaseFilter, Pattern.CASE_INSENSITIVE);

                if (pattern.matcher(reg.searchString).matches()) {
                    logger.debug("{} matches {}", lowerCaseFilter, reg.searchString);
                    return true; // Filter matches first/last/bib.
                } else {
                    logger.debug("{} does not match {}", lowerCaseFilter, reg.searchString);
                }

            } catch (PatternSyntaxException e) {
                logger.debug("Pattern exception ", e);
                return true;
            }
            return false; // Does not match.
        });
    }

    private void updateRSUKeys() {

        StringBuilder postData = new StringBuilder();
        postData.append("email=");
        postData.append(URLEncoder.encode(mainConfig.getString("rsuUsername"), StandardCharsets.UTF_8));
        postData.append("&password=");
        postData.append(URLEncoder.encode(mainConfig.getString("rsuPassword"), StandardCharsets.UTF_8));
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

                    logger.debug("updateRSUKeys -> RSU Response: {}", response.body());
                    mainConfig.put("rsuTempKey", rsuResponse.get("tmp_key"));
                    mainConfig.put("rsuTempSecret", rsuResponse.get("tmp_secret"));
                    logger.debug(" RSU Temp Key/Secret: {} / {}", rsuResponse.get("tmp_key"), rsuResponse.get("tmp_secret"));
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

    private void downloadReg() {
        // clear existing registrations
        registrationList.clear();

        // background thread time
        Thread.ofVirtual().start(() -> {
            Map<String, Object> fieldlistMap = mainConfig.getJSONObject("fieldlist_mapping").toMap();

            // for each race / event map entry, snag the participants
            // Registrations
            // For each Event_ID that is not set to "IGNORE", download the participants
            mainConfig.getJSONObject("event_mapping").keySet().forEach(event -> {
                if (!mainConfig.getJSONObject("event_mapping").getString(event).equalsIgnoreCase("IGNORE")) {
                    Integer regReturned = 0;
                    Integer page = 1;
                    String div = mainConfig.getJSONObject("event_mapping").getString(event);
                    logger.info("Getting registrations for {} division (event_id {})", div, event);
                    try {
                        do {

                            if (!mainConfig.has("rsuTempKey") && !mainConfig.get("rsuLoginType").equals("API")) {
                                updateRSUKeys();
                            }

                            StringBuilder rsuURL = new StringBuilder();
                            rsuURL.append("https://runsignup.com/Rest/race/").append(mainConfig.get("race_id"));
                            rsuURL.append("/participants?format=json&event_id=").append(event);
                            rsuURL.append("&page=").append(page.toString()).append("&results_per_page=1000");
                            rsuURL.append("&include_user_anonymous_flag=T&include_questions=T&include_registration_addons=T&supports_nb=T");
                            if (mainConfig.get("rsuLoginType").equals("API")) {
                                rsuURL.append("&api_key=").append(mainConfig.get("rsuUsername"));
                                rsuURL.append("&api_secret=").append(mainConfig.get("rsuPassword"));
                            } else {
                                rsuURL.append("&tmp_key=").append(mainConfig.get("rsuTempKey"));
                                rsuURL.append("&tmp_secret=").append(mainConfig.get("rsuTempSecret"));
                            }

                            logger.debug("Participant request for race_id={} and event_id={}: {}", mainConfig.get("race_id"), event, rsuURL.toString());
                            HttpRequest request = HttpRequest.newBuilder()
                                    .uri(URI.create(rsuURL.toString()))
                                    .build();

                            HttpClient client = HttpClient.newHttpClient();

                            try {
                                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                                if (response.statusCode() == 200) {

                                    if (response.body().startsWith("[{")) { // We have a json array....
                                        try {
                                            JSONArray results = new JSONArray(response.body()).getJSONObject(0).getJSONArray("participants");
                                            //logger.debug(results.toString(4));

                                            regReturned = results.length();
                                            for (int j = 0; j < results.length(); j++) {
                                                JSONObject rsuReg = results.getJSONObject(j);

                                                // Create a search string of bib, first, and last name
                                                String searchString = rsuReg.optString("bib_num") + " "
                                                        + rsuReg.getJSONObject("user").optString("first_name") + " "
                                                        + rsuReg.getJSONObject("user").optString("last_name");
                                                Registration r = new Registration(new HashMap(), StringUtils.stripAccents(searchString.toLowerCase()));

                                                // Stash the division
                                                r.fieldlist.put("Div", div);

                                                // Basic RSU Attributes
                                                // ("First_Name", "Middle_Name", "Last_Name"));
                                                // ("Gender", "Age", "DOB", "Bib"));
                                                // ("City", "State", "Country"));
                                                // ("EMail", "Giveaway", "isAnonymous", "Team_Name"));
                                                //
                                                // Everything else is a question prompt text
                                                fieldlistMap.keySet().forEach(k -> {
                                                    if (fieldlistMap.get(k) instanceof String source) {
                                                        switch (source) {
                                                            case "First_Name" ->
                                                                r.fieldlist.put(k, titleCase(rsuReg.getJSONObject("user").optString("first_name")));
                                                            case "Middle_Name" ->
                                                                r.fieldlist.put(k, titleCase(rsuReg.getJSONObject("user").optString("middle_name")));
                                                            case "Last_Name" ->
                                                                r.fieldlist.put(k, titleCase(rsuReg.getJSONObject("user").optString("last_name")));
                                                            case "EMail" ->
                                                                r.fieldlist.put(k, rsuReg.getJSONObject("user").optString("email"));
                                                            case "Bib" ->
                                                                r.fieldlist.put(k, rsuReg.optString("bib_num"));
                                                            case "Age" ->
                                                                r.fieldlist.put(k, rsuReg.optString("age"));
                                                            case "Date_of_Birth" ->
                                                                r.fieldlist.put(k, fixDOB(rsuReg.getJSONObject("user").optString("dob")));
                                                            case "Gender" ->
                                                                r.fieldlist.put(k, fixGender(rsuReg.getJSONObject("user").optString("gender")));
                                                            case "City" ->
                                                                r.fieldlist.put(k, titleCase(normalizeCities(rsuReg.getJSONObject("user").getJSONObject("address").optString("city"))));
                                                            case "State" ->
                                                                r.fieldlist.put(k, rsuReg.getJSONObject("user").getJSONObject("address").optString("state"));
                                                            case "Country" ->
                                                                r.fieldlist.put(k, rsuReg.getJSONObject("user").getJSONObject("address").optString("country_code"));
                                                            case "Giveaway" ->
                                                                r.fieldlist.put(k, rsuReg.optString("giveaway"));
                                                            case "Team_Name" ->
                                                                r.fieldlist.put(k, rsuReg.optString("team_name"));
                                                            case "isAnonymous" ->
                                                                r.fieldlist.put(k, "F".equals(rsuReg.getJSONObject("user").optString("is_anonymous")) ? "No" : "Yes");
                                                            case "Registration_ID" ->
                                                                r.fieldlist.put(k, rsuReg.optString("registration_id"));
                                                            case "BLANK" -> {
                                                            }
                                                            default -> {
                                                                logger.debug("Question lookup for question ID {}", source);
                                                                if (rsuReg.has("question_responses")) {
                                                                    JSONArray responses = rsuReg.getJSONArray("question_responses");
                                                                    responses.forEach(qr -> {
                                                                        if (qr instanceof JSONObject q) {
                                                                            logger.debug("The response is a JSONObject...");
                                                                            if (source.equals(q.optString("question_text"))) {
                                                                                logger.debug("We have a matching question");
                                                                                r.fieldlist.put(k, q.optString("response"));
                                                                            }
                                                                        }
                                                                    });
                                                                }
                                                                //}
                                                            }
                                                        }
                                                    }
                                                });

                                                Platform.runLater(() -> {
                                                    registrationList.add(r);
                                                });
                                            }
                                            logger.debug("Found " + results.length() + " registrations\n\n");
                                        } catch (org.json.JSONException exJ) {
                                            regReturned = 0;
                                        }
                                        page++;
                                    } else {
                                        logger.error("Error in RSU Participant request: {} ", response.body());
                                        updateRSUKeys();
                                    }
                                } else {
                                    logger.error("Error in RSU response: {} ", response.body());
                                }
                            } catch (Exception ex) {
                                logger.error("Exception in HttpClient response: ", ex);
                            }
                        } while (regReturned >= 1000);
                    } catch (Exception ex) {
                        logger.debug("RSU sync Exception: " + ex.getMessage());
                    }
                }
            });
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

        // Default RSU -> PPRRScore mappings
        Map<String, String> defaultMappings = new HashMap();
        defaultMappings.put("FirstName", "First_Name");
        defaultMappings.put("MiddleName", "Middle_Name");
        defaultMappings.put("LastName", "Last_Name");
        defaultMappings.put("Sex", "Gender");
        defaultMappings.put("Age", "Age");
        defaultMappings.put("DateOfBirth", "Date_of_Birth");
        defaultMappings.put("City", "City");
        defaultMappings.put("St", "State");
        defaultMappings.put("Country", "Country");
        defaultMappings.put("E-Mail", "EMail");
        defaultMappings.put("Anonymous", "isAnonymous");
        defaultMappings.put("Swag", "Giveaway");
        defaultMappings.put("Bib", "Bib");
        defaultMappings.put("RegID", "Registration_ID");

        // Wizard variables
        final JSONObject setupData = new JSONObject();
        final Map<Integer, String> eventDivisionMap = new HashMap();
        final Map<String, String> pprrscoreFieldMap = new HashMap();
        List<WizardPane> wizardPanes = new ArrayList();

        Wizard wizard = new Wizard();
        wizard.setTitle("Setup RSU Downloader");

        BooleanProperty pane1OkayToGo = new SimpleBooleanProperty(false);

        /////////////////////////////////
        //
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
            File startingDir = new File(pprrscoreDirTextField.getText());
            if (!startingDir.exists() || !startingDir.isDirectory()) {
                startingDir = userPrefs.getPPRRScoreDir();
            }
            chooser.setInitialDirectory(startingDir);
            File selectedDirectory = chooser.showDialog(targetDirButton.getScene().getWindow());
            if (selectedDirectory != null) {
                pprrscoreDirTextField.setText(selectedDirectory.getAbsolutePath());
                logger.debug("Selected PPRRScoreDir {}", selectedDirectory.getAbsoluteFile());
            }

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
                    }
                }
            } else {
                logger.debug("{} is not a valid directory path", newValue);
                pane1OkayToGo.setValue(false);
                raceNameLabel.setText("");
                raceDateLabel.setText("");
            }
        });

        pprrscoreDirTextField.setText(userPrefs.getPPRRScoreDir().getAbsolutePath());

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

                Path downloaderConf = Paths.get(setupData.get("PPRRScoreDir") + "/Config/rsuDownloader.json");
                if (downloaderConf.toFile().canRead()) {
                    try {
                        JSONObject confFile = new JSONObject(Files.readString(downloaderConf));
                        logger.debug("Existing conf file: {}", confFile.toString(4));
                        if (confFile.has("fieldlist_mapping")) {
                            JSONObject map = confFile.getJSONObject("fieldlist_mapping");
                            map.keySet().forEach(k -> {
                                defaultMappings.put(k, map.optString(k));
                                logger.debug("Setting default fieldlist map {} -> {}", k, map.optString(k));
                            });
                        }
                        confFile.keySet().forEach(k -> setupData.put(k, confFile.get(k)));
                        logger.debug("Loaded initial config from {}", downloaderConf.toString());
                    } catch (IOException ex) {
                        logger.error("Unable to read {}", downloaderConf.toString(), ex);
                    }
                }
            }
        };
        wizardPanes.add(page1);
        page1.setHeaderText("Select the PPRRScore Directory");
        page1.setContent(page1Grid);

        /////////////////////////////////
        //
        // Wizard Pane 2: RSU Login Information
        // 
        // Username, password
        // onExit, do a login and stash the temp key and secret
        BooleanProperty pane2OkayToGo = new SimpleBooleanProperty(false);
        row = 0;

        GridPane page2Grid = new GridPane();
        page2Grid.setVgap(10);
        page2Grid.setHgap(10);

        page2Grid.add(new Label("Login Method:"), 0, row);
        ComboBox<String> rsuLoginTypeComboBox = new ComboBox<>();
        rsuLoginTypeComboBox.getItems().addAll("Username / Password", "API Key/Secret");
        if (userPrefs.getGlobalPrefs("rsuLoginType").equals("API")) {
            rsuLoginTypeComboBox.getSelectionModel().select("API Key/Secret");
        } else {
            rsuLoginTypeComboBox.getSelectionModel().select("Username / Password");
        }
        GridPane.setHgrow(rsuLoginTypeComboBox, Priority.ALWAYS);
        page2Grid.add(rsuLoginTypeComboBox, 1, row++);

        page2Grid.add(new Label("Username / Key:"), 0, row);
        TextField rsuUsernameTextField = createTextField("rsuUsername");
        rsuUsernameTextField.setText(userPrefs.getGlobalPrefs("rsuUsername"));
        GridPane.setHgrow(rsuUsernameTextField, Priority.ALWAYS);
        page2Grid.add(rsuUsernameTextField, 1, row++);

        page2Grid.add(new Label("Password / Secret:"), 0, row);
        PasswordField rsuPasswordTextField = new PasswordField();
        GridPane.setHgrow(rsuPasswordTextField, Priority.ALWAYS);
        rsuPasswordTextField.setText(userPrefs.getRSUPassword());
        GridPane.setHgrow(rsuPasswordTextField, Priority.ALWAYS);
        page2Grid.add(rsuPasswordTextField, 1, row++);

        page2Grid.add(new Label("Status:"), 0, row);
        Button validateButton = new Button("Validate");
        Label loginSuccessLabel = new Label("Unchecked");
        Pane loginSpring = new Pane();
        HBox.setHgrow(loginSpring, Priority.ALWAYS);
        HBox validateHBox = new HBox(loginSuccessLabel, loginSpring, validateButton);
        validateHBox.setSpacing(4);
        validateHBox.setAlignment(Pos.CENTER_LEFT);
        page2Grid.add(validateHBox, 1, row++);

        // If the username or password fields change, force a re-validation
        rsuUsernameTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            pane2OkayToGo.setValue(false);
        });
        rsuPasswordTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            pane2OkayToGo.setValue(false);
        });

        validateButton.setOnAction((event) -> {

            // Are we using an api_key/secret or a username / password
            if (rsuLoginTypeComboBox.getSelectionModel().getSelectedItem().contains("Username")) {
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

                            setupData.put("rsuLoginType", "PASSWORD");
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
            } else {
                // There is no login equivalent for the key/secret
                // so we will just make a call to /Rest/races/ and
                // see how many we get back
                // get the list of from RSU
                // https://runsignup.com/Rest/races?
                // tmp_key=KEY&tmp_secret=SECRET
                // &format=json&include_event_days=F&page=1&results_per_page=50&sort=name+ASC
                // &start_date=2024-05-27&end_date=2024-05-27
                StringBuilder requestURL = new StringBuilder();
                requestURL.append("https://runsignup.com/Rest/races");
                requestURL.append("?api_key=").append(rsuUsernameTextField.getText());
                requestURL.append("&api_secret=").append(rsuPasswordTextField.getText());
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
                            int responseSize = rsuResponse.getJSONArray("races").length();
                            if (responseSize > 0 && responseSize < 50) {
                                pane2OkayToGo.setValue(true);
                                loginSuccessLabel.setText("Valid");
                                setupData.put("rsuLoginType", "API");
                                setupData.put("rsuUsername", rsuUsernameTextField.getText());
                                setupData.put("rsuPassword", rsuPasswordTextField.getText());
                            } else {
                                loginSuccessLabel.setText("Invalid API Secret / Key");
                            }
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

        /////////////////////////////////
        // 
        // Wizard Pane 3: Get list of races from RSU 
        // 
        // Prompt the user to select the Race. Snag the race_event_days_id that is between the start_date and end_date for the event
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

                if (setupData.get("rsuLoginType").equals("API")) {
                    requestURL.append("?api_key=").append(setupData.get("rsuUsername"));
                    requestURL.append("&api_secret=").append(setupData.get("rsuPassword"));
                } else {
                    requestURL.append("?tmp_key=").append(setupData.get("rsuTempKey"));
                    requestURL.append("&tmp_secret=").append(setupData.get("rsuTempSecret"));
                }
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

                            if (setupData.has("race_id")) {
                                Integer raceID = setupData.getInt("race_id");
                                raceList.forEach(r -> {
                                    if (raceID.equals(r.raceID)) {
                                        raceListView.getSelectionModel().select(r);
                                    }
                                });
                            }
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

        /////////////////////////////////
        //
        // Page 4: Get the race details based on the race_event_days_id from #3
        // Filter event_id's based on the start_end times and create event records
        // Show each event and prompt the user to map each the PPRRScore Division (or set to Ignore)
        GridPane page4Grid = new GridPane();
        page4Grid.setVgap(10);
        page4Grid.setHgap(10);

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

                // Read in the existing event_mapping to a map
                Map<Integer, String> eventMap = new HashMap();
                if (setupData.has("event_mapping")) {
                    JSONObject map = setupData.getJSONObject("event_mapping");
                    map.keySet().forEach(k -> {
                        eventMap.put(Integer.valueOf(k), map.optString(k));
                    });
                }

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

                StringBuilder requestURL = new StringBuilder();
                requestURL.append("https://runsignup.com/Rest/race/");
                requestURL.append(setupData.get("race_id"));
                if (setupData.get("rsuLoginType").equals("API")) {
                    requestURL.append("?api_key=").append(setupData.get("rsuUsername"));
                    requestURL.append("&api_secret=").append(setupData.get("rsuPassword"));
                } else {
                    requestURL.append("?tmp_key=").append(setupData.get("rsuTempKey"));
                    requestURL.append("&tmp_secret=").append(setupData.get("rsuTempSecret"));
                }
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

                            // Get the events
                            LocalDate raceDate = LocalDate.parse(setupData.getJSONObject("PPRRScoreFieldList").getString("EventDate"), DateTimeFormatter.ofPattern("yyyy-M-d"));
                            rsuResponse.getJSONObject("race").getJSONArray("events").forEach((r) -> {
                                if (r instanceof JSONObject event) {
                                    LocalDate eventStart = LocalDate.parse(event.getString("start_time").replaceAll(" ..:..", ""), DateTimeFormatter.ofPattern("M/d/yyyy"));
                                    LocalDate eventEnd = LocalDate.parse(event.getString("end_time").replaceAll(" ..:..", ""), DateTimeFormatter.ofPattern("M/d/yyyy"));
                                    if (eventStart.compareTo(raceDate) <= 0 && eventEnd.compareTo(raceDate) >= 0) {
                                        event eventRecord = new event(URLDecoder.decode(event.getString("name"), StandardCharsets.UTF_8), event.getInt("event_id"), new SimpleStringProperty("IGNORE"));

                                        if (eventMap.containsKey(event.getInt("event_id"))) {
                                            eventRecord.div.setValue(eventMap.get(eventRecord.eventID));
                                        }

                                        logger.debug("Found Event: {} ({})", URLDecoder.decode(event.getString("name"), StandardCharsets.UTF_8), event.getInt("event_id"));
                                        eventList.add(eventRecord);
                                    } else {
                                        logger.debug("Event {} ({}) is NOT on {}", event.getString("name"), event.getInt("event_id"), eventStart, eventEnd);
                                    }
                                }
                            });

                            // Stash the questions
                            if (rsuResponse.getJSONObject("race").has("questions")) {
                                Map<Integer, String> rsuQuestions = new HashMap();
                                rsuResponse.getJSONObject("race").getJSONArray("questions").forEach((q) -> {
                                    if (q instanceof JSONObject question) {
                                        rsuQuestions.put(question.getInt("question_id"), question.getString("question_text"));
                                    }
                                });
                                setupData.put("rsuQuestions", rsuQuestions);
                            }
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
                eventList.forEach(e -> {
                    eventDivisionMap.put(e.eventID, e.div.getValue());
                    logger.debug(" Event: {} -> {}", e.Name, e.div.getValue());
                });
                setupData.put("event_mapping", eventDivisionMap);
                logger.debug("setupData Dump: {}", setupData.toString(4));
            }
        };
        wizardPanes.add(page4);

        page4.setContent(page4Grid);
        page4.setHeaderText("Map RSU Event to PPRRScore Division");

        /////////////////////////////////
        //
        // Page 5: Map the RSU attributes 
        // For each registration attribute, select an RSU source (native registration field or question/givaway source if available)
        // Set all of the panes to the same height to make this a bit nicer
        GridPane page5Grid = new GridPane();
        page5Grid.setVgap(10);
        page5Grid.setHgap(10);

        record regAttribute(String pprrField, StringProperty rsuField) {

        }

        ObservableList<regAttribute> regAttributeList = FXCollections.observableArrayList();

        TableView<regAttribute> regAttributeTable = new TableView(regAttributeList);
        regAttributeTable.setEditable(true);
        regAttributeTable.setPrefHeight(250);
        regAttributeTable.setMinHeight(250);
        regAttributeTable.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(regAttributeTable, Priority.ALWAYS);
        regAttributeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<regAttribute, String> pprrAttributeTablecolumn = new TableColumn<>("PPRRScore Field");
        pprrAttributeTablecolumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().pprrField));

        TableColumn<regAttribute, String> rsuAttributeTableColumn = new TableColumn<>("RSU Attribute");
        rsuAttributeTableColumn.setCellValueFactory(cellData -> cellData.getValue().rsuField);
        rsuAttributeTableColumn.setEditable(true);

        regAttributeTable.getColumns().add(pprrAttributeTablecolumn);
        regAttributeTable.getColumns().add(rsuAttributeTableColumn);

        page5Grid.add(regAttributeTable, 0, 0);

        final WizardPane page5 = new WizardPane() {
            @Override
            public void onEnteringPage(Wizard wizard) {

                regAttributeList.clear();

                // Setup the PPRRScore attributes that we need to map
                setupData.getJSONObject("PPRRScoreFieldList").getJSONArray("RegFields").iterator().forEachRemaining(e -> {
                    if (e instanceof String regField) {
                        if (!"Div".equals(regField)) {
                            String def = defaultMappings.containsKey(regField) ? defaultMappings.get(regField) : "BLANK";
                            logger.debug("Setting {} to {}", regField, def);
                            regAttributeList.add(new regAttribute(regField, new SimpleStringProperty(def)));
                        }
                    }
                });

                // List of possible RSU fields 
                List<String> rsuAttributesList = new ArrayList();

                // Basic RSU Attributes
                rsuAttributesList.addAll(Arrays.asList("First_Name", "Middle_Name", "Last_Name"));
                rsuAttributesList.addAll(Arrays.asList("Gender", "Age", "Date_of_Birth", "Bib"));
                rsuAttributesList.addAll(Arrays.asList("City", "State", "Country"));
                rsuAttributesList.addAll(Arrays.asList("EMail", "Giveaway", "isAnonymous", "Team_Name", "Registration_ID"));

                // Question Responses
                if (setupData.has("rsuQuestions")) {
                    JSONObject rsuQuestions = setupData.getJSONObject("rsuQuestions");
                    rsuQuestions.keySet().forEach((q) -> {
                        rsuAttributesList.add(rsuQuestions.optString(q));
                    });
                }

                // CatchAll for when we just dont care
                rsuAttributesList.add("BLANK");

                // Setup the cell factory for the attribute map
                rsuAttributeTableColumn.setCellFactory(tc -> {
                    ComboBox<String> combo = new ComboBox<>();
                    combo.getItems().addAll(rsuAttributesList);
                    TableCell<regAttribute, String> cell = new TableCell<regAttribute, String>() {
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
                        tc.getTableView().getItems().get(cell.getIndex()).rsuField.setValue(combo.getValue());
                    });
                    return cell;
                });
            }

            @Override
            public void onExitingPage(Wizard wizard) {
                wizard.invalidProperty().unbind();
                regAttributeList.forEach(e -> {
                    pprrscoreFieldMap.put(e.pprrField, e.rsuField.getValue());
                    logger.debug(" PPRRScore Fieldlist: {} -> {}", e.pprrField, e.rsuField.getValue());
                });
                setupData.put("fieldlist_mapping", pprrscoreFieldMap);
            }
        };

        wizardPanes.add(page5);

        page5.setContent(page5Grid);
        page5.setHeaderText("Map RSU Attributes to PPRRScore Fieldlist");

        //////////////////////////////////
        //
        // Showtime....
        //
        for (WizardPane p : wizardPanes) {
            p.setMinSize(450, 350);
        }

        wizard.setFlow(new LinearFlow(wizardPanes));

        // show wizard and wait for response
        wizard.showAndWait().ifPresent(result -> {
            if (result == ButtonType.FINISH) {
                logger.debug("setupData: {} ", setupData.toString(4));

                mainConfig.clear();

                // Save the rsu Username/Password to the global prefs
                userPrefs.setGlobalPrefs("rsuLoginType", setupData.getString("rsuLoginType"));
                userPrefs.setGlobalPrefs("rsuUsername", setupData.getString("rsuUsername"));
                userPrefs.setRSUPassword(setupData.getString("rsuPassword"));

                // Save the PPRRScore Race directory to the global prefs
                userPrefs.setPPRRScoreDir(new File(setupData.getString("PPRRScoreDir")));

                // Create config mapping
                JSONObject rsuConfig = new JSONObject();
                rsuConfig.put("fieldlist", setupData.getJSONObject("PPRRScoreFieldList").getJSONArray("RegFields"));
                rsuConfig.put("fieldlist_mapping", setupData.get("fieldlist_mapping"));
                rsuConfig.put("event_mapping", setupData.get("event_mapping"));
                rsuConfig.put("event_name", setupData.getJSONObject("PPRRScoreFieldList").getString("Racename"));
                rsuConfig.put("race_id", setupData.get("race_id"));
                rsuConfig.put("event_days_id", setupData.get("race_event_days_id"));
                rsuConfig.put("event_date", setupData.getJSONObject("PPRRScoreFieldList").getString("EventDate"));

                logger.debug("Setup Wizard RSU Config File:  {}", rsuConfig.toString(4));
                try {
                    // Save the mapping config to the rsuDownloader.json file in the PPRRScore race directory
                    Files.write(Paths.get(setupData.get("PPRRScoreDir") + "/Config/rsuDownloader.json"), rsuConfig.toString(4).getBytes());
                } catch (IOException ex) {
                    logger.error("ERROR: Unable to write to {}", Paths.get(setupData.get("PPRRScoreDir") + "/Config/rsuDownloader.json").toString(), ex);
                }
                // copy the new config up to the main config
                rsuConfig.keySet().forEach(k -> mainConfig.put(k, rsuConfig.get(k)));
                mainConfig.put("PPRRScoreDir", setupData.get("PPRRScoreDir"));

                // Set the event name and date for the main display
                eventName.setValue(mainConfig.getString("event_name"));
                eventDate.setValue(mainConfig.getString("event_date"));

                // Stash the race short name
                mainConfig.put("event_name_short", setupData.getJSONObject("PPRRScoreFieldList").getString("RacenameShort"));
                mainConfig.put("RegFile", setupData.getJSONObject("PPRRScoreFieldList").getString("RegFile"));

                // Stash the username and password
                mainConfig.put("rsuLoginType", setupData.get("rsuLoginType"));
                mainConfig.put("rsuUsername", setupData.get("rsuUsername"));
                mainConfig.put("rsuPassword", setupData.get("rsuPassword"));
                if (!setupData.getString("rsuLoginType").equals("API")) {
                    mainConfig.put("rsuTempKey", setupData.get("rsuTempKey"));
                    mainConfig.put("rsuTempSecret", setupData.get("rsuTempSecret"));
                }

                logger.debug("Main Config File:  {}", mainConfig.toString(4));

                // Setup the city -> name map
                cityTyposMap.put("co springs", "Colorado Springs");
                cityTyposMap.put("c/s", "Colorado Springs");
                cityTyposMap.put("colorado spgs", "Colorado Springs");
                cityTyposMap.put("co spgs", "Colorado Springs");
                cityTyposMap.put("colo spgs", "Colorado Springs");
                cityTyposMap.put("colorado sprgs", "Colorado Springs");
                cityTyposMap.put("cs", "Colorado Springs");
                cityTyposMap.put("colorado spring", "Colorado Springs");
                cityTyposMap.put("cos", "Colorado Springs");

                // if the <pprrscoredir>/../../CityNames.txt file exists, suck it into the map
                // This file is from a windows system so it uses Windows-1252 and not UTF-8 :-/
                Path pprrScoreDir = Paths.get(mainConfig.getString("PPRRScoreDir")).getParent().getParent();
                Path cityNames = Paths.get(pprrScoreDir.toString() + "/CityNames.txt");
                if (Files.exists(cityNames)) {
                    logger.debug("CityNames.txt file found at {}", cityNames.toString());
                    try {
                        Files.lines(cityNames,Charset.forName("windows-1252")).toList().forEach(l -> {
                            //logger.debug("CityNames.txt readline: {}", l);
                            String[] m = l.split(",", 2);
                            if (m.length == 2) {
                                cityTyposMap.put(m[0], m[1]);
                                logger.debug("cityTypos Map addition: {} -> {}", m[0], m[1]);
                            }
                        });
                    } catch (Exception ex) {
                        logger.error("Unable to read CityNames.txt", ex);
                    }
                }

                // setup the main table columns
                setupTableColumns();

                // download the registrations
                downloadReg();
            }
        });

    }

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

    //Utility method for the ControlsFX Wizard
    private TextField createTextField(String id) {
        TextField textField = new TextField();
        textField.setId(id);
        GridPane.setHgrow(textField, Priority.ALWAYS);
        return textField;
    }

    // Fix name/city capitalization for those who SHOUT or don't 
    // know what the shift key does :-/
    public String titleCase(String s) {
        //logger.debug("Entered titleCase({})", s);
        if (s == null || s.isEmpty()) {
            return "";
        }

        if (s.equals(s.toLowerCase()) || s.equals(s.toUpperCase())) {
            String n = capitalizeSentence(s);
            n = n.replace(" Iii", " III").replace(" Ii", " II");
            if (n.length() == 2) {
                n = upperIfNoVowel(n);
            }
            if (!s.equals(n)) {
                logger.debug("titleCase() Changed: " + s + " -> " + n);
            }
            return n;
        }
        return s;
    }

    // This is borrowed from https://stackoverflow.com/questions/32249723/how-to-capitalize-first-letter-after-period-in-each-sentence-using-java
    private String capitalizeSentence(String sentence) {
        StringBuilder result = new StringBuilder();
        boolean capitalize = true; //state
        for (char c : sentence.toCharArray()) {
            if (capitalize) {
                //this is the capitalize state
                result.append(Character.toUpperCase(c));
                if (!Character.isWhitespace(c) && c != '.' && c != '\'' && c != '-') {
                    capitalize = false; //change state
                }
            } else {
                //this is the don't capitalize state
                result.append(Character.toLowerCase(c));
                if (c == '.' || Character.isWhitespace(c) || c == '\'' && c == '-') {
                    capitalize = true; //change state
                }
            }
        }
        return result.toString();
    }

    private String upperIfNoVowel(String n) {
        // if both letters are constanants (e.g, "JT" or "MC"), 
        // then uppercase them
        if (n.length() != 2) {
            return n;
        }
        char[] a = n.toCharArray();
        if (a[0] != 'a' && a[0] != 'e' && a[0] != 'i' && a[0] != 'o' && a[0] != 'u'
                && a[1] != 'a' && a[1] != 'e' && a[1] != 'i' && a[1] != 'o' && a[1] != 'u') {
            return n.toUpperCase();
        }

        return n;
    }

    private String fixDOB(String dob) {
        String[] d = dob.split("-");
        return d[1] + "/" + d[2] + "/" + d[0];
    }

    private String fixGender(String gend) {
        if (gend.isEmpty()) {
            return "PNTS";
        }
        if (gend.equals("X")) {
            return "NB";
        }
        return gend;
    }

    private static String normalizeCities(String city) {
        String c = city.toLowerCase();

        // look for inadvertant reduplication
        // e.g "Colorado SpringsColorado Springs"
        // Actual reduplicated names will be odd (like "Walla Walla")
        if (c.length() % 2 == 0) {
            if (c.substring(0, (c.length() / 2)).equals(c.substring(c.length() / 2, c.length()))) {
                c = c.substring(0, (c.length() / 2));
            }
        }

        if (cityTyposMap.containsKey(c)) {
            return cityTyposMap.get(c);
        }
        if (c.equals(city.toLowerCase())) {
            return city;
        }
        return c;
    }
}
