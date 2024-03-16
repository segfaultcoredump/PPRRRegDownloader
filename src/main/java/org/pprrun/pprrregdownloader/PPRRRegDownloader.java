package org.pprrun.pprrregdownloader;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

        Wizard wizard = new Wizard();
        wizard.setTitle("Setup");

        BooleanProperty pane1OkayToGo = new SimpleBooleanProperty(false);

        // Wizard Pane 1: PPRRScore Directory Location
        // 
        // Request the target dir
        // Flip the okayToGo flag if it contains the PPRRScore config files
        // Display the Race Name and Date 
        TextField pprrscoreDirTextField = createTextField("pprrscoreDir");
        Button targetDirButton = new Button("Select");
        Label raceName = new Label("");
        Label raceDate = new Label("");

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
                        setupData.put("fieldList", fieldList);
                        raceName.setText(fieldList.optString("Racename"));
                        raceDate.setText(fieldList.getString("EventDate"));

                        break;
                    } else if (s.equals("rsuDownloaderConfig.json")) {
                        // open the rsuDownloaderConfig.json file 
                        //setupData.put("rsuConfig", Files.lines(sdaf));
                    }
                }
            } else {
                logger.debug("{} is not a valid directory path", newValue);
                pane1OkayToGo.setValue(false);
                raceName.setText("");
                raceDate.setText("");
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
        page1Grid.add(raceName, 1, row++);
        Label rd = new Label("Race Date:");
        GridPane.setHalignment(rd, HPos.RIGHT);

        page1Grid.add(rd, 0, row);
        page1Grid.add(raceDate, 1, row++);

        GridPane.setHalignment(raceName, HPos.LEFT);
        GridPane.setHalignment(raceDate, HPos.LEFT);

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
        page1.setHeaderText("Select the PPRRScore Directory");
        page1.setContent(page1Grid);

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

        page2.setContent(page2Grid);
        page2.setHeaderText("RunSignUp Login");
        
        
        // Page 3: RSU Races
        // https://runsignup.com/Rest/races?tmp_key=rx9KR5gaAZk03emb8KomXwqplfMEA9op&tmp_secret=KSRL1YzegL2o2O09pHWQ7codmJDCWgs7&format=json&events=F&race_headings=F&race_links=F&include_waiver=F&include_multiple_waivers=F&include_event_days=F&include_extra_date_info=F&page=1&results_per_page=50&sort=name+ASC&start_date=2024-05-27&end_date=2024-05-27&only_partner_races=F&search_start_date_only=F&only_races_with_results=F&distance_units=K

        // Setup the Wizard Flow Rules
        wizard.setFlow(new LinearFlow(page1, page2));

        // show wizard and wait for response
        wizard.showAndWait();

        // Save the rsu Username/Password to the global prefs
        // Save the PPRRScore Race directory to the global prefs
        // Save the mapping config to the rsuDownloader.json file in the PPRRScore race directory
    }

}
