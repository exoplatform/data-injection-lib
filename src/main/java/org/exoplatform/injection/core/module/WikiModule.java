package org.exoplatform.injection.core.module;


import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.injection.helper.InjectorUtils;
import org.exoplatform.injection.services.AbstractModule;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.wiki.WikiException;
import org.exoplatform.wiki.mow.api.Page;
import org.exoplatform.wiki.mow.api.Wiki;
import org.exoplatform.wiki.resolver.TitleResolver;
import org.exoplatform.wiki.service.WikiService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xwiki.rendering.syntax.Syntax;

import java.io.IOException;

public class WikiModule extends AbstractModule {

    /**
     * The log.
     */
    private final Log LOG = ExoLogger.getLogger(WikiModule.class);

    protected WikiService wikiService;
    protected SpaceService spaceService;

    public WikiModule(WikiService wikiService, SpaceService spaceService) {
        this.wikiService = wikiService;
        this.spaceService = spaceService;

    }


    /**
     * Creates the user wiki.
     *
     * @param wikis                 the wikis
     * @param defaultDataFolderPath the default data folder path
     */
    public void createUserWiki(JSONArray wikis, String defaultDataFolderPath) {
        String spacePrefix = "";
        if(PropertyManager.getProperty(SPACE_MODULE_PREFIX_PATTERN_VALUE) != null) {
            spacePrefix = PropertyManager.getProperty(SPACE_MODULE_PREFIX_PATTERN_VALUE);
        }
        for (int i = 0; i < wikis.length(); i++) {
            RequestLifeCycle.begin(PortalContainer.getInstance());
            try {
                JSONObject wiki = wikis.getJSONObject(i);
                createOrEditPage(wiki, wiki.has("parent") ? wiki.getString("parent") : "", defaultDataFolderPath, spacePrefix);
            } catch (JSONException e) {
                LOG.error("Syntax error on wiki n°" + i, e);

            } finally {
                RequestLifeCycle.end();
            }
        }
    }

    /**
     * Creates the or edit page.
     *
     * @param wiki                  the wiki
     * @param parentTitle           the parent title
     * @param defaultDataFolderPath the default data folder path
     * @throws JSONException the JSON exception
     */
    private void createOrEditPage(JSONObject wiki, String parentTitle, String defaultDataFolderPath, String spacePrefix) throws JSONException {
        boolean forceNew = wiki.has("new") && wiki.getBoolean("new");
        String title = wiki.getString("title");
        String filename = wiki.has("filename") ? wiki.getString("filename") : "";
        String parent = parentTitle;
        String type = wiki.has("type") ? wiki.getString("type") : "";
        Space target = null;
        String owner = wiki.has("owner") ? wiki.getString("owner") : "";
        if ("group".equals(type)) {
            type = PortalConfig.GROUP_TYPE;
            // When wiki's type is a group we should compute the owner
            // Get target space
            target = spaceService.getSpaceByDisplayName(owner);
            //--- Needed to convert space displayName to PrettyName
            owner = target.getGroupId();

        } else if ("portal".equals(type)) {
            type = PortalConfig.PORTAL_TYPE;
        } else {
            type = PortalConfig.USER_TYPE;
        }


        try {
            // does wiki exists ?
            if (wikiService.getWikiByTypeAndOwner(type, owner) == null) {
                wikiService.createWiki(type, owner);
            }

            if (forceNew && !title.equals("WikiHome")) {
                if (wikiService.isExisting(type, owner, TitleResolver.getId(title, false))) {
                    wikiService.deletePage(type, owner, TitleResolver.getId(title, false));
                }
            }

            Page page;
            if (wikiService.isExisting(type, owner, TitleResolver.getId(title, false))) {
                page = wikiService.getPageOfWikiByName(type, owner, TitleResolver.getId(title, false));
            } else {
                page = wikiService.createPage(new Wiki(type, owner), TitleResolver.getId(parent, false), new Page(title, title));
            }

            String content = "= " + title + " =";
            if (filename != null && !filename.equals(""))
                content = InjectorUtils.getWikiPage(filename, defaultDataFolderPath);
            page.setContent(content);
            page.setSyntax(Syntax.XWIKI_2_1.toIdString());
            wikiService.updatePage(page, null);
            // wikiService_.createVersionOfPage(page);

            if (wiki.has("wikis") && wiki.getJSONArray("wikis").length() > 0) {
                for (int j = 0; j < wiki.getJSONArray("wikis").length(); j++) {
                    JSONObject childWiki = wiki.getJSONArray("wikis").getJSONObject(j);
                    createOrEditPage(childWiki, wiki.getString("title"), defaultDataFolderPath,spacePrefix);
                }
            }

        } catch (WikiException e) {
            LOG.error("Error when creating wiki page", e); // To change body of catch statement use File | Settings
            // | File Templates.
        } catch (IOException e) {
            LOG.error("Error when reading wiki content", e);
        }

    }
}
