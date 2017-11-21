package org.exoplatform.injection.services.module;


import org.apache.commons.lang.StringUtils;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.injection.services.helper.InjectorUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.model.AvatarAttachment;
import org.exoplatform.social.core.space.SpaceUtils;
import org.exoplatform.social.core.space.impl.DefaultSpaceApplicationHandler;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.persistence.EntityManager;

public class SpaceModule {

    /**
     * The log.
     */
    private final Log LOG = ExoLogger.getLogger(SpaceModule.class);

    protected SpaceService spaceService;

    protected IdentityManager identityManager;

    public SpaceModule(SpaceService spaceService, IdentityManager identityManager) {
        this.spaceService = spaceService;
        this.identityManager = identityManager;

    }

    /**
     * Creates the spaces.
     *
     * @param spaces                the spaces
     * @param defaultDataFolderPath the default data folder path
     */
    public void createSpaces(JSONArray spaces, String defaultDataFolderPath) {
        for (int i = 0; i < spaces.length(); i++) {

            try {
                JSONObject space = spaces.getJSONObject(i);
                //RequestLifeCycle.begin(ExoContainerContext.getCurrentContainer());
                boolean created = createSpace(space.getString("displayName"), space.getString("creator"));
                //---Create Avatar/Add members only when a space is created
                if (created) {

                    if (space.has("members")) {
                        JSONArray members = space.getJSONArray("members");
                        for (int j = 0; j < members.length(); j++) {
                            Space spacet = spaceService.getSpaceByDisplayName(space.getString("displayName"));
                            if (spacet != null) {
                                spaceService.addMember(spacet, members.getString(j));
                            }

                        }
                    }
                    createSpaceAvatar(space.getString("displayName"), space.getString("creator"), space.getString("avatar"), defaultDataFolderPath);

                }
                //RequestLifeCycle.end();

            } catch (JSONException e) {
                LOG.error("Syntax error on space n°" + i, e);
            }
        }
    }

    /**
     * Creates the space avatar.
     *
     * @param name                  the name
     * @param editor                the editor
     * @param avatarFile            the avatar file
     * @param defaultDataFolderPath the default data folder path
     */
    private void createSpaceAvatar(String name, String editor, String avatarFile, String defaultDataFolderPath) {
        Space space = null;
        try {
            space = spaceService.getSpaceByDisplayName(name);
            if (space != null) {
                try {
                    AvatarAttachment avatarAttachment = InjectorUtils.getAvatarAttachment(avatarFile, defaultDataFolderPath);
                    space.setAvatarAttachment(avatarAttachment);
                    spaceService.updateSpace(space);
                    space.setEditor(editor);
                    spaceService.updateSpaceAvatar(space);
                } catch (Exception e) {
                    LOG.error("Unable to set avatar for space " + space.getDisplayName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.error("Unable to create space " + space.getDisplayName(), e.getMessage());
        }

    }

    /**
     * Creates the space.
     *
     * @param name    the name
     * @param creator the creator
     */
    private boolean createSpace(String name, String creator) {
        Space target = null;
        boolean spaceCreated = true;
        try {
            target = spaceService.getSpaceByDisplayName(name);

            if (target != null) {
                return false;
            }

            Space space = new Space();
            // space.setId(name);
            space.setDisplayName(name);
            space.setPrettyName(name);
            space.setDescription(StringUtils.EMPTY);
            space.setGroupId("/spaces/" + space.getPrettyName());
            space.setRegistration(Space.OPEN);
            space.setVisibility(Space.PRIVATE);
            space.setPriority(Space.INTERMEDIATE_PRIORITY);

            Identity identity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), true);
            if (identity != null) {
                space.setPrettyName(SpaceUtils.buildPrettyName(space));
            }
            space.setType(DefaultSpaceApplicationHandler.NAME);
            spaceService.createSpace(space, creator);
        } catch (Exception E) {
            LOG.error("========= ERROR when create space {} ", target.getPrettyName(), E);
            return false;

        } finally {

        }
        return spaceCreated;


    }

    public void purgeSpaces(JSONArray spaces) {
        for (int i = 0; i < spaces.length(); i++) {

            try {
                JSONObject space = spaces.getJSONObject(i);

                purgeSpace(space.getString("displayName"));

            } catch (JSONException e) {
                LOG.error("Syntax error on space n°" + i, e);
            }
        }
    }

    private void purgeSpace(String displayName) {
        Space target = null;
        boolean begunTx = false;
        try {

            begunTx = startTx();
            target = spaceService.getSpaceByDisplayName(displayName);
            if (target != null) {
                spaceService.deleteSpace(target);

            }
        } catch (Exception E) {
            LOG.error("Space {} can't be deleted ", target.getPrettyName(), E);

        } finally {
            try {
                endTx(begunTx);
            } catch (Exception ex) {

            }
            //RequestLifeCycle.end();

        }

    }
    protected boolean startTx() {
        EntityManager em = CommonsUtils.getService(EntityManagerService.class).getEntityManager();
        if (!em.getTransaction().isActive()) {
            em.getTransaction().begin();
            LOG.debug("started new transaction");
            return true;
        }
        return false;
    }

    /**
     * Stops the transaction
     *
     * @param requestClose true if need to do really comment
     */
    public void endTx(boolean requestClose) {
        EntityManager em = CommonsUtils.getService(EntityManagerService.class).getEntityManager();
        try {
            if (requestClose && em.getTransaction().isActive()) {
                em.getTransaction().commit();
                LOG.debug("commited transaction");
            }
        } catch (RuntimeException e) {
            LOG.error("Failed to commit to DB::" + e.getMessage(), e);
            em.getTransaction().rollback();
        }
    }
}
