package org.exoplatform.injection.core.module;

import org.apache.commons.lang3.RandomStringUtils;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.ComponentRequestLifecycle;
import org.exoplatform.injection.services.AbstractModule;
import org.exoplatform.injection.helper.InjectorUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.MembershipType;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.image.ImageUtils;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.manager.RelationshipManager;
import org.exoplatform.social.core.model.AvatarAttachment;
import org.exoplatform.social.core.relationship.model.Relationship;
import org.exoplatform.webui.exception.MessageException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Map;

public class UserModule extends AbstractModule {

    /**
     * The log.
     */
    private final Log LOG = ExoLogger.getLogger(UserModule.class);

    protected IdentityManager identityManager;
    protected OrganizationService organizationService;
    protected RelationshipManager relationshipManager;

    public UserModule(OrganizationService organizationService, IdentityManager identityManager, RelationshipManager relationshipManager) {
        this.organizationService = organizationService;
        this.identityManager = identityManager;
        this.relationshipManager = relationshipManager;

    }

    /**
     * The Constant PLATFORM_USERS_GROUP.
     */
    private final static String PLATFORM_USERS_GROUP = "/platform/administrators";

    private static Boolean requestStarted = false;

    /**
     * The Constant MEMBERSHIP_TYPE_MANAGER.
     */
    private final static String MEMBERSHIP_TYPE_MANAGER = "*";

    /**
     * The Constant WIDTH.
     */
    private final static int WIDTH = 200;

    /**
     * Creates the users.
     *
     * @param users             the users
     * @param defaultFolderPath
     */
    public void createUsers(JSONArray users, String defaultFolderPath) {

        for (int i = 0; i < users.length(); i++) {
            try {
                startRequest();
                JSONObject user = users.getJSONObject(i);
                boolean created = createUser(user.getString("username"),
                        user.getString("position"),
                        user.getString("firstname"),
                        user.getString("lastname"),
                        user.getString("email"),
                        user.getString("password"),
                        user.getString("isadmin"));
                if (created) {
                    saveUserAvatar(user.getString("username"), user.getString("avatar"), defaultFolderPath);
                }
                endRequest();


            } catch (JSONException e) {
                LOG.error("Syntax error on user n°" + i, e);
            }
        }

    }

    /**
     * Creates the user.
     *
     * @param username  the username
     * @param position  the position
     * @param firstname the firstname
     * @param lastname  the lastname
     * @param email     the email
     * @param password  the password
     * @param isAdmin   the is admin
     * @return true, if successful
     */
    private boolean createUser(String username,
                               String position,
                               String firstname,
                               String lastname,
                               String email,
                               String password,
                               String isAdmin) {
        Boolean ok = true;

        User user = null;
        try {
            user = organizationService.getUserHandler().findUserByName(username);
        } catch (Exception e) {
            LOG.info(e.getMessage());
        }

        if (user != null) {
            return false;
        }

        user = organizationService.getUserHandler().createUserInstance(username);
        user.setDisplayName(firstname + " " + lastname);
        user.setEmail(email);
        user.setFirstName(firstname);
        user.setLastName(lastname);
        user.setLastName(password);
        //
        // Creates a 32 chars length of string from the defined array of
        // characters including numeric and alphabetic characters.
        //
        if (PropertyManager.getProperty(USER_MODULE_RANDOM_PASSWORD_PROPERTY) != null) {
            if (PropertyManager.getProperty(USER_MODULE_RANDOM_PASSWORD_PROPERTY).equalsIgnoreCase(USER_MODULE_ENABLE)) {
                user.setPassword(RandomStringUtils.random(32, 0, 8, true, true, "eXoTribe".toCharArray()));
            }


        }
        try {
            organizationService.getUserHandler().createUser(user, true);
        } catch (Exception e) {
            LOG.info(e.getMessage());
            ok = false;
        }

        if (isAdmin != null && isAdmin.equals("true")) {
            // Assign the membership "*:/platform/administrators" to the created user
            try {
                Group group = organizationService.getGroupHandler().findGroupById(PLATFORM_USERS_GROUP);
                MembershipType membershipType = organizationService.getMembershipTypeHandler()
                        .findMembershipType(MEMBERSHIP_TYPE_MANAGER);
                organizationService.getMembershipHandler().linkMembership(user, group, membershipType, true);
            } catch (Exception e) {
                LOG.warn("Can not assign *:/platform/administrators membership to the created user");
                ok = false;
            }

        }

        if (!"".equals(position)) {
            Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username, true);
            if (identity != null) {
                Profile profile = identity.getProfile();
                profile.setProperty(Profile.POSITION, position);
                profile.setListUpdateTypes(Arrays.asList(Profile.UpdateType.CONTACT));
                try {
                    identityManager.updateProfile(profile);
                } catch (MessageException e) {
                    LOG.error("Error ro update user profile", e, e.getMessage());
                }
            }
        }

        return ok;
    }

    /**
     * Save user avatar.
     *
     * @param username      the username
     * @param fileName      the file name
     * @param defaultFolder the data folder path
     */
    private void saveUserAvatar(String username, String fileName, String defaultFolder) {
        try {

            AvatarAttachment avatarAttachment = InjectorUtils.getAvatarAttachment(fileName, defaultFolder);
            Profile p = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username, true).getProfile();
            p.setProperty(Profile.AVATAR, avatarAttachment);
            p.setListUpdateTypes(Arrays.asList(Profile.UpdateType.AVATAR));


            Map<String, Object> props = p.getProperties();

            // Removes avatar url and resized avatar
            for (String key : props.keySet()) {
                if (key.startsWith(Profile.AVATAR + ImageUtils.KEY_SEPARATOR)) {
                    p.removeProperty(key);
                }
            }

            identityManager.updateProfile(p);

        } catch (Exception e) {
            LOG.info(e.getMessage());
        }
    }

    /**
     * Creates the relations.
     *
     * @param relations the relations
     */
    public void createRelations(JSONArray relations) {
        for (int i = 0; i < relations.length(); i++) {

            try {
                JSONObject relation = relations.getJSONObject(i);
                Identity idInviting = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, relation.getString("inviting"), false);
                Identity idInvited = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, relation.getString("invited"), false);
                relationshipManager.inviteToConnect(idInviting, idInvited);
                if (relation.has("confirmed") && relation.getBoolean("confirmed")) {
                    relationshipManager.confirm(idInvited, idInviting);
                }
            } catch (JSONException e) {
                LOG.error("Syntax error on relation n°" + i, e);
            }
        }
    }

    private void endRequest() {
        if (requestStarted && organizationService instanceof ComponentRequestLifecycle) {
            try {
                ((ComponentRequestLifecycle) organizationService).endRequest(PortalContainer.getInstance());
            } catch (Exception e) {
                LOG.warn(e.getMessage(), e);
            }
            requestStarted = false;
        }
    }

    private void startRequest() {
        if (organizationService instanceof ComponentRequestLifecycle) {
            ((ComponentRequestLifecycle) organizationService).startRequest(PortalContainer.getInstance());
            requestStarted = true;
        }
    }

    public void purgeUsers(JSONArray users) {
        for (int i = 0; i < users.length(); i++) {
            try {
                startRequest();
                JSONObject user = users.getJSONObject(i);
                purgeUser(user.getString("username"));

                endRequest();


            } catch (JSONException e) {
                LOG.error("Syntax error on user n°" + i, e);
            }

        }
    }

    private void purgeUser(String username) {


        User user = null;
        try {
            user = organizationService.getUserHandler().findUserByName(username);
        } catch (Exception e) {
            LOG.info("User {} doesn't exist in eXo store", username);
        }

        if (user != null) {
            try {
                organizationService.getUserHandler().removeUser(username, true);

            } catch (Exception e) {
                LOG.warn("Enable to drop User {} from eXo store", username);

            }


        }
    }

    public void purgeRelations(JSONArray relations) {
        for (int i = 0; i < relations.length(); i++) {

            try {
                JSONObject relation = relations.getJSONObject(i);
                Identity idInviting = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, relation.getString("inviting"), false);
                Identity idInvited = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, relation.getString("invited"), false);

                Relationship oldRelationShip = relationshipManager.get(idInviting, idInvited);
                if (oldRelationShip != null) {
                    relationshipManager.delete(oldRelationShip);
                }
            } catch (JSONException e) {
                LOG.error("Syntax error on relation n°" + i, e);
            }
        }

    }
}
