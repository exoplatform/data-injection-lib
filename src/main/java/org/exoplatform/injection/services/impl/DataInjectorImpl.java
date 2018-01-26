package org.exoplatform.injection.services.impl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.injection.core.module.*;
import org.exoplatform.injection.helper.InjectorMonitor;
import org.exoplatform.injection.helper.InjectorUtils;
import org.exoplatform.injection.services.DataInjector;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class DataInjectorImpl implements DataInjector {

    private static final Log LOG = ExoLogger.getLogger(DataInjectorImpl.class);

    /**
     * The scenario folder.
     */
    public static final String SCENARIOS_FOLDER = "/scenarios";

    /**
     * The scenario name attribute.
     */
    public static final String SCENARIO_NAME_ATTRIBUTE = "scenarioName";

    /**
     * The scenarios.
     */
    private static Map<String, JSONObject> scenarios;

    /**
     * Default data folder path
     */
    private final static String DATA_INJECTION_FOLDER_PATH = "data-injection-folder-path";

    UserModule userModule_;

    /**
     * The space service.
     */
    SpaceModule spaceModule_;

    /**
     * The calendar service.
     */
    CalendarModule calendarModule_;

    /**
     * The wiki service.
     */
    WikiModule wikiModule_;

    /**
     * The forum service.
     */
    ForumModule forumModule_;

    /**
     * The document service.
     */
    DocumentModule documentModule_;

    /**
     * The activity service.
     */
    ActivityModule activityModule_;

    private String dataFolderPath = "";
    private Map<String, Integer> completion;

    public DataInjectorImpl(InitParams params, UserModule userModule, SpaceModule spaceModule, CalendarModule calendarModule, WikiModule wikiModule, ForumModule forumModule, DocumentModule documentModule, ActivityModule activityModule) {

        userModule_ = userModule;
        spaceModule_ = spaceModule;
        calendarModule_ = calendarModule;
        wikiModule_ = wikiModule;
        forumModule_ = forumModule;
        documentModule_ = documentModule;
        activityModule_ = activityModule;

        //--- Get default data folder
        ValueParam dataFolderPathParam = params.getValueParam(DATA_INJECTION_FOLDER_PATH);
        if (dataFolderPathParam != null) {
            dataFolderPath = dataFolderPathParam.getValue();
        }

        //--- Launch setup process
        setup(dataFolderPath);

    }


    /**
     * Load injection scripts
     */
    public Map setup(String dataFolderPath) {
        scenarios = new HashMap<String, JSONObject>();
        try {

            //--- Get injection usescase
            File scenariosFolder = new File(InjectorUtils.getConfigPath(dataFolderPath) + SCENARIOS_FOLDER);

            for (String fileName : scenariosFolder.list()) {

                if (fileName.endsWith(".json")) {
                    InputStream stream = FileUtils.openInputStream(new File(InjectorUtils.getConfigPath(dataFolderPath) + SCENARIOS_FOLDER + "/" + fileName));

                    String fileContent = getData(stream);
                    try {
                        JSONObject json = new JSONObject(fileContent);
                        String name = json.getString(SCENARIO_NAME_ATTRIBUTE);
                        scenarios.put(name, json);
                    } catch (JSONException e) {
                        LOG.error("Syntax error in scenario " + fileName, e);
                    }
                }


            }
        } catch (URISyntaxException use) {
            LOG.error("Unable to read scenario file", use);
        } catch (Exception e) {
            LOG.error("Unable to find scenario file", e);
        }
        return scenarios;
    }

    @Override
    public void inject(Map<String, Integer> completion) throws Exception {
        //--- Inject Data into the Store
        scenarios.forEach((k, v) -> {
            inject(k, completion);
        });

    }

    @Override
    public void purge(Map<String, Integer> completion) throws Exception {
        //--- Purge Data into the Store
        for (Map.Entry<String, JSONObject> scenarioEntry : scenarios.entrySet()) {
            purge(scenarioEntry.getKey(),completion);
        }


    }

    public void inject(String scenarioName, Map<String, Integer> completion) {
        PortalContainer portalContainer = PortalContainer.getInstance();
        ExoContainerContext.setCurrentContainer(portalContainer);
        enforceCloseTransaction();
        LOG.info("Start {} .............", this.getClass().getName());
        InjectorMonitor injectorMonitor = new InjectorMonitor("Data Injection Process");
        //--- Start data injection
        String downloadUrl = "";
        try {
            JSONObject scenarioData = scenarios.get(scenarioName).getJSONObject("data");
            if (scenarioData.has("users")) {
                LOG.info("Create " + scenarioData.getJSONArray("users").length() + " users.");
                completion.put("Users", 0);
                injectorMonitor.start("Processing users data");
                userModule_.createUsers(scenarioData.getJSONArray("users"), dataFolderPath);
                completion.put("Users", 100);
                injectorMonitor.stop();

            }

            if (scenarioData.has("relations")) {
                LOG.info("Create " + scenarioData.getJSONArray("relations").length() + " relations.");
                injectorMonitor.start("Processing relations data");
                userModule_.createRelations(scenarioData.getJSONArray("relations"));
                injectorMonitor.stop();
            }

            if (scenarioData.has("spaces")) {
                LOG.info("Create " + scenarioData.getJSONArray("spaces").length() + " spaces.");
                completion.put("Spaces", 0);
                injectorMonitor.start("Processing spaces data");
                spaceModule_.createSpaces(scenarioData.getJSONArray("spaces"), dataFolderPath);
                completion.put("Spaces", 100);
                injectorMonitor.stop();
            }
/**
 if (scenarioData.has("calendars")) {
 LOG.info("Create " + scenarioData.getJSONArray("calendars").length() + " calendars.");
 calendarModule_.setCalendarColors(scenarioData.getJSONArray("calendars"));
 calendarModule_.createEvents(scenarioData.getJSONArray("calendars"));
 }
 */

            if (scenarioData.has("wikis")) {
                LOG.info("Create " + scenarioData.getJSONArray("wikis").length() + " wikis.");
                completion.put("Wiki", 0);
                injectorMonitor.start("Processing wikis data");
                wikiModule_.createUserWiki(scenarioData.getJSONArray("wikis"), dataFolderPath);
                completion.put("Wiki", 100);
                injectorMonitor.stop();
            }


            if (scenarioData.has("activities")) {

                LOG.info("Create " + scenarioData.getJSONArray("activities").length() + " activities.");
                injectorMonitor.start("Processing activities data");
                activityModule_.pushActivities(scenarioData.getJSONArray("activities"));
                injectorMonitor.stop();
            }
            if (scenarioData.has("documents")) {
                LOG.info("Create " + scenarioData.getJSONArray("documents").length() + " documents.");
                completion.put("Documents", 0);
                injectorMonitor.start("Processing documents data");
                documentModule_.uploadDocuments(scenarioData.getJSONArray("documents"), dataFolderPath);
                completion.put("Documents", 100);
                injectorMonitor.stop();
            }
            if (scenarioData.has("forums")) {
                LOG.info("Create " + scenarioData.getJSONArray("forums").length() + " forums.");
                completion.put("Forums", 0);
                injectorMonitor.start("Processing forums data");
                forumModule_.createForumContents(scenarioData.getJSONArray("forums"));
                completion.put("Forums", 100);
                injectorMonitor.stop();
            }

            /**

             if (scenarios.get(datasetUsesCaseName).has("scriptData")) {
             try {
             downloadUrl = documentModule_.storeScript(scenarios.get(datasetUsesCaseName).getString("scriptData"));

             } catch (Exception E) {
             LOG.error("Error to store Data Script {}",  scenarios.get(datasetUsesCaseName).getString("scriptData"), E);
             } finally {

             }

             }
             */
            LOG.info("Data Injection has been done successfully.............");
            LOG.info(injectorMonitor.prettyPrint());

        } catch (JSONException e) {
            LOG.error("Syntax error when reading scenario " + scenarioName, e);
        } finally {
            enforceCloseTransaction();
            RequestLifeCycle.begin(portalContainer);
        }
    }

    public void purge(String scenarioName, Map<String, Integer> completion) {

        PortalContainer portalContainer = PortalContainer.getInstance();
        ExoContainerContext.setCurrentContainer(portalContainer);
        enforceCloseTransaction();
        LOG.info("Purge {} .............", this.getClass().getName());
        InjectorMonitor injectorMonitor = new InjectorMonitor("Data Injection Purge Process");
        try {

            JSONObject scenarioData = scenarios.get(scenarioName).getJSONObject("data");

            if (scenarioData.has("spaces")) {
                LOG.info("Purging " + scenarioData.getJSONArray("spaces").length() + " spaces.");
                injectorMonitor.start("Purging spaces");
                spaceModule_.purgeSpaces(scenarioData.getJSONArray("spaces"));
                injectorMonitor.stop();
            }
            if (scenarioData.has("relations")) {
                LOG.info("Purging " + scenarioData.getJSONArray("relations").length() + " relations.");
                injectorMonitor.start("Purging relations");
                userModule_.purgeRelations(scenarioData.getJSONArray("relations"));
                injectorMonitor.stop();
            }


            LOG.info("Data purging has been done successfully.............");
            LOG.info(injectorMonitor.prettyPrint());

        } catch (JSONException e) {
            LOG.error("Syntax error when reading scenario " + scenarioName, e);
        } finally {
            enforceCloseTransaction();
            RequestLifeCycle.begin(PortalContainer.getInstance());
        }
    }

    /**
     * Gets the data.
     *
     * @param inputStream the input stream
     * @return the data
     */
    public static String getData(InputStream inputStream) {
        String out = "";
        StringWriter writer = new StringWriter();
        try {
            IOUtils.copy(inputStream, writer);
            out = writer.toString();

        } catch (IOException e) {
            LOG.error("Error to load data ", e, e.getMessage());
        }

        return out;
    }

    private void enforceCloseTransaction() {
        while (true) {
            try {
                RequestLifeCycle.end();
            } catch (Exception e) {
                // All transaction are closed
                break;
            }
        }
    }
}
