package org.exoplatform.injection.core.module;


import org.apache.commons.lang.StringUtils;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.ComponentRequestLifecycle;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.injection.helper.InjectorUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.OrganizationService;
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

    private static Boolean requestStarted = false;

    protected SpaceService spaceService;

    protected IdentityManager identityManager;

    protected OrganizationService organizationService;

    public SpaceModule(SpaceService spaceService, IdentityManager identityManager, OrganizationService organizationService) {
        this.spaceService = spaceService;
        this.identityManager = identityManager;
        this.organizationService = organizationService;

    }

    /**
     * Creates the spaces.
     *
     * @param spaces                the spaces
     * @param defaultDataFolderPath the default data folder path
     */
    public void createSpaces(JSONArray spaces, String defaultDataFolderPath) {
        for (int i = 0; i < spaces.length(); i++) {
            RequestLifeCycle.begin(PortalContainer.getInstance());

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
            } finally {
                RequestLifeCycle.end();
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
                    LOG.error("Unable to set avatar for space " + space.getDisplayName(), e);
                }
            }
        } catch (Exception e) {
            LOG.error("Unable to create space " + space.getDisplayName(), e);
        }

    }

    /**
     * Creates the space.
     *
     * @param name    the name
     * @param creator the creator
     */
    public boolean createSpace(String name, String creator) {
        Space target = null;
        boolean spaceCreated = true;
        try {
            target = spaceService.getSpaceByDisplayName(name);

            if (target != null) {
                return false;
            }
            String groupId = "/spaces/" + SpaceUtils.cleanString(name);
            forceDeleteGroupOfSpaceIfExists(groupId);

            Space space = new Space();
            // space.setId(name);
            space.setDisplayName(name);
            space.setPrettyName(name);
            space.setDescription(StringUtils.EMPTY);
            space.setGroupId("/spaces/" + space.getPrettyName());
            space.setGroupId(groupId);
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
            LOG.error("========= ERROR when create space " + name, E);
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
        RequestLifeCycle.begin(PortalContainer.getInstance());
        try {

            //begunTx = startTx();
            target = spaceService.getSpaceByDisplayName(displayName);
            if (target != null) {
                spaceService.deleteSpace(target);
            }
        } catch (Exception E) {
            LOG.error("Space " + target.getPrettyName() + " can't be deleted ", E);

        } finally {
            RequestLifeCycle.end();
        }
    }

    private void forceDeleteGroupOfSpaceIfExists(String groupId) throws Exception {
        Group spaceGroup = organizationService.getGroupHandler().findGroupById(groupId);
        if (spaceGroup != null) {
            LOG.warn("Space group {} already exists, it will be removed before injecting space", groupId);
            organizationService.getGroupHandler().removeGroup(spaceGroup, true);
        }
    }
}
