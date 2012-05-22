/*
 * Copyright (c) 2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.tenant.mgt.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

import org.wso2.carbon.stratos.common.beans.TenantInfoBean;
import org.wso2.carbon.stratos.common.constants.StratosConstants;
import org.wso2.carbon.stratos.common.listeners.TenantMgtListener;
import org.wso2.carbon.stratos.common.util.ClaimsMgtUtil;
import org.wso2.carbon.stratos.common.util.CommonUtil;
import org.wso2.carbon.email.verification.util.EmailVerifcationSubscriber;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.registry.core.utils.UUIDGenerator;
import org.wso2.carbon.tenant.mgt.internal.TenantMgtServiceComponent;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.api.TenantMgtConfiguration;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.config.multitenancy.MultiTenantRealmConfigBuilder;
import org.wso2.carbon.user.core.tenant.Tenant;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.utils.AuthenticationObserver;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility methods for tenant management.
 */
public class TenantMgtUtil {

    private static final Log log = LogFactory.getLog(TenantMgtUtil.class);
    private static final String ILLEGAL_CHARACTERS_FOR_TENANT_DOMAIN = ".*[^a-zA-Z0-9\\._\\-].*";
    
    

    /**
     * Activates the tenant
     *
     * @param tenantDomain tenant domain
     * @throws Exception , UserStoreException in retrieving the tenant id or
     *                   activating the tenant.
     */
    public static void activateTenant(String tenantDomain) throws Exception {
        TenantManager tenantManager = TenantMgtServiceComponent.getTenantManager();
        int tenantId;
        try {
            tenantId = tenantManager.getTenantId(tenantDomain);
        } catch (UserStoreException e) {
            String msg = "Error in retrieving the tenant id for the tenant domain: " + tenantDomain
                         + ".";
            log.error(msg);
            throw new Exception(msg, e);
        }

        try {
            tenantManager.activateTenant(tenantId);
        } catch (UserStoreException e) {
            String msg = "Error in activating the tenant for tenant domain: " + tenantDomain + ".";
            log.error(msg);
            throw new Exception(msg, e);
        }
    }

    /**
     * Prepares string to show theme management page.
     *
     * @param tenantId - tenant id
     * @return UUID
     * @throws RegistryException, if failed.
     */
    public static String prepareStringToShowThemeMgtPage(int tenantId) throws RegistryException {
        // first we generate a UUID
        UserRegistry systemRegistry =
                TenantMgtServiceComponent.getRegistryService().getGovernanceSystemRegistry();
        String uuid = UUIDGenerator.generateUUID();
        // store it in the registry.
        Resource resource = systemRegistry.newResource();
        String tenantIdStr = Integer.toString(tenantId);
        resource.setProperty(MultitenantConstants.TENANT_ID, tenantIdStr);
        String uuidPath = StratosConstants.TENANT_CREATION_THEME_PAGE_TOKEN
                          + RegistryConstants.PATH_SEPARATOR + uuid;
        systemRegistry.put(uuidPath, resource);

        // restrict access
        CommonUtil.denyAnonAuthorization(uuidPath, systemRegistry.getUserRealm());
        return uuid;
    }

    /**
     * Triggers adding the tenant for TenantMgtListener
     *
     * @param tenantInfo tenant
     * @throws UserStoreException - exception not handled here.
     */
    public static void triggerAddTenant(TenantInfoBean tenantInfo) throws UserStoreException {
        // initializeRegistry(tenantInfoBean.getTenantId());
        for (TenantMgtListener tenantMgtListener :
                TenantMgtServiceComponent.getTenantMgtListeners()) {
            tenantMgtListener.addTenant(tenantInfo);
        }
    }

    /**
     * Triggers an update for the tenant for TenantMgtListener
     *
     * @param tenantInfoBean tenantInfoBean
     * @throws UserStoreException - exception not handled, throw as it is.
     */
    public static void triggerUpdateTenant(
            TenantInfoBean tenantInfoBean) throws UserStoreException {
        for (TenantMgtListener tenantMgtListener :
                TenantMgtServiceComponent.getTenantMgtListeners()) {
            tenantMgtListener.updateTenant(tenantInfoBean);
        }
    }

    /**
     * Validate the tenant domain
     *
     * @param domainName tenant domain
     * @throws Exception , if invalid tenant domain name is given
     */
    public static void validateDomain(String domainName) throws Exception {
        if (domainName == null || domainName.equals("")) {
            String msg = "Provided domain name is empty.";
            log.error(msg);
            throw new Exception(msg);
        }
        // ensures the .ext for the public clouds.
        if (CommonUtil.isPublicCloudSetup()) {
            int lastIndexOfDot = domainName.lastIndexOf(".");
            if (lastIndexOfDot <= 0) {
                String msg = "You should have an extension to your domain.";
                log.error(msg);
                throw new Exception(msg);
            }
        }
        int indexOfDot = domainName.indexOf(".");
        if (indexOfDot == 0) {
            // can't start a domain starting with ".";
            String msg = "Invalid domain, starting with '.'";
            log.error(msg);
            throw new Exception(msg);
        }
        // check the tenant domain contains any illegal characters
        if (domainName.matches(ILLEGAL_CHARACTERS_FOR_TENANT_DOMAIN)) {
            String msg = "The tenant domain ' " + domainName +
                         " ' contains one or more illegal characters. the valid characters are " +
                         "letters, numbers, '.', '-' and '_'";
            log.error(msg);
            throw new Exception(msg);
        }
    }

    /**
     * Check whether a tenant exist with the givne tenantInfoBean and TenantManager.
     *
     * @param tenant tenant
     * @return true, if the chosen name is available to register
     * @throws Exception, if unable to get the tenant id or if a tenant with same domain exists.
     */
    public static boolean isDomainNameAvailable(Tenant tenant) throws Exception {
        TenantManager tenantManager = TenantMgtServiceComponent.getTenantManager();
        String tenantDomain = tenant.getDomain();

        // The registry reserved words are checked first.
        if (tenantDomain.equals("atom") || tenantDomain.equals("registry") ||
            tenantDomain.equals("resource")) {
            String msg = "You can not use a registry reserved word:" + tenantDomain +
                         ":as a tenant domain. Please choose a different one.";
            log.error(msg);
            throw new Exception(msg);
        }

        int tenantId;
        try {
            tenantId = tenantManager.getTenantId(tenantDomain);
        } catch (UserStoreException e) {
            String msg = "Error in getting the tenant id for the given domain  " +
                         tenant.getDomain() + ".";
            log.error(msg);
            throw new Exception(msg, e);
        }

        // check a tenant with same domain exist.
        if (tenantId > 0 || tenant.getDomain().equals(MultitenantConstants.SUPER_TENANT_NAME)) {
            String msg = "A tenant with same domain already exist. Please use a different domain." +
                         " Chosen tenant domain: " + tenant.getDomain() + ".";
            log.info(msg);
            return false;
        }
        return true;
    }

    /**
     * gets the UserStoreManager for a tenant
     *
     * @param tenant   - a tenant
     * @param tenantId - tenant Id. To avoid the sequences where tenant.getId() may
     *                 produce the super tenant's tenant Id.
     * @return UserStoreManager
     * @throws Exception UserStoreException
     */
    public static UserStoreManager getUserStoreManager(Tenant tenant, int tenantId)
            throws Exception {
        // get the system registry for the tenant
        RealmConfiguration realmConfig = TenantMgtServiceComponent.getBootstrapRealmConfiguration();
        TenantMgtConfiguration tenantMgtConfiguration =
                TenantMgtServiceComponent.getRealmService().getTenantMgtConfiguration();
        UserRealm userRealm;
        try {
            MultiTenantRealmConfigBuilder builder =
                    TenantMgtServiceComponent.getRealmService().
                            getMultiTenantRealmConfigBuilder();
            RealmConfiguration realmConfigToPersist =
                    builder.getRealmConfigForTenantToPersist(realmConfig, tenantMgtConfiguration,
                                                             tenant,
                                                             tenantId);

            RealmConfiguration realmConfigToCreate =
                    builder.
                            getRealmConfigForTenantToCreateRealmOnTenantCreation(realmConfig,
                                                                                 realmConfigToPersist,
                                                                                 tenantId);
            userRealm =
                    TenantMgtServiceComponent.getRealmService().
                            getUserRealm(realmConfigToCreate);
        } catch (UserStoreException e) {
            String msg = "Error in creating Realm for tenant, tenant domain: " + tenant.getDomain();
            log.error(msg, e);
            throw new Exception(msg, e);
        }

        UserStoreManager userStoreManager;
        try {
            userStoreManager = userRealm.getUserStoreManager();

            return userStoreManager;
        } catch (UserStoreException e) {
            String msg = "Error in getting the userstore/authorization manager for tenant: " +
                         tenant.getDomain();
            log.error(msg);
            throw new Exception(msg, e);
        }
    }

    /**
     * Emails the tenant admin notifying the account creation.
     *
     * @param domainName tenant domain
     * @param adminName  tenant admin
     * @param email      associated tenant email address
     */
    public static void notifyTenantCreation(String domainName, String adminName, String email) {
        TenantManager tenantManager = TenantMgtServiceComponent.getTenantManager();
        String firstName = "";
        try {
            int tenantId = tenantManager.getTenantId(domainName);
            Tenant tenant = (Tenant) tenantManager.getTenant(tenantId);
            firstName = ClaimsMgtUtil.getFirstName(TenantMgtServiceComponent.getRealmService(),
                                                   tenant, tenantId);
        } catch (Exception e) {
            String msg = "Unable to get the tenant with the tenant domain";
            log.error(msg, e);
            // just catch from here.
        }

        // load the mail configuration
        Map<String, String> userParams = new HashMap<String, String>();
        userParams.put("first-name", firstName);
        userParams.put("admin-name", adminName);
        userParams.put("domain-name", domainName);

        try {
            TenantMgtServiceComponent.getSuccessMsgSender().sendEmail(email, userParams);
        } catch (Exception e) {
            // just catch from here..
            String msg = "Error in sending the notification email.";
            log.error(msg, e);
        }
    }


    /**
     * Emails the super admin notifying the account creation for a new tenant.
     *
     * @param domainName tenant domain
     * @param adminName  tenant admin
     * @param email      tenant's email address
     */
    public static void notifyTenantCreationToSuperAdmin(
            String domainName, String adminName, String email) {
        String notificationEmailAddress = CommonUtil.getNotificationEmailAddress();

        if (notificationEmailAddress.trim().equals("")) {
            if (log.isDebugEnabled()) {
                log.debug("No super-admin notification email address is set to notify upon a" +
                          " tenant registration");
            }
            return;
        }

        Map<String, String> userParams = initializeSuperTenantNotificationParams(
                domainName, adminName, email);

        try {
            TenantMgtServiceComponent.getTenantCreationNotifier().
                    sendEmail(notificationEmailAddress, userParams);
        } catch (Exception e) {
            // just catch from here..
            String msg = "Error in sending the notification email.";
            log.error(msg, e);
        }
    }


    /**
     * Emails the super admin notifying the account activation for an unactivated tenant.
     *
     * @param domainName tenant domain
     * @param adminName  tenant admin
     * @param email      tenant's email address
     */
    public static void notifyTenantActivationToSuperAdmin(
            String domainName, String adminName, String email) {
        String notificationEmailAddress = CommonUtil.getNotificationEmailAddress();

        if (notificationEmailAddress.trim().equals("")) {
            if (log.isDebugEnabled()) {
                log.debug("No super-admin notification email address is set to notify upon a" +
                          " tenant activation");
            }
            return;
        }

        Map<String, String> userParams = initializeSuperTenantNotificationParams(
                domainName, adminName, email);

        try {
            TenantMgtServiceComponent.getTenantActivationNotifier().
                    sendEmail(notificationEmailAddress, userParams);
        } catch (Exception e) {
            // just catch from here..
            String msg = "Error in sending the notification email.";
            log.error(msg, e);
        }
    }

    /**
     * Initializes the super tenant notification parameters
     *
     * @param domainName - tenant domain
     * @param adminName  - tenant admin
     * @param email      - tenant email
     * @return the parameters
     */
    private static Map<String, String> initializeSuperTenantNotificationParams(
            String domainName, String adminName, String email) {
        TenantManager tenantManager = TenantMgtServiceComponent.getTenantManager();
        String firstName = "";
        String lastName = "";
        try {
            int tenantId = tenantManager.getTenantId(domainName);
            Tenant tenant = (Tenant) tenantManager.getTenant(tenantId);
            firstName = ClaimsMgtUtil.getFirstName(TenantMgtServiceComponent.getRealmService(),
                                                   tenant, tenantId);
            lastName = ClaimsMgtUtil.getLastName(TenantMgtServiceComponent.getRealmService(),
                                                 tenant, tenantId);

        } catch (Exception e) {
            String msg = "Unable to get the tenant with the tenant domain";
            log.error(msg, e);
            // just catch from here.
        }

        // load the mail configuration
        Map<String, String> userParams = new HashMap<String, String>();
        userParams.put("admin-name", adminName);
        userParams.put("domain-name", domainName);
        userParams.put("email-address", email);
        userParams.put("first-name", firstName);
        userParams.put("last-name", lastName);
        return userParams;
    }


    /**
     * initializes tenant from the user input (tenant info bean)
     *
     * @param tenantInfoBean input
     * @return tenant
     */
    public static Tenant initializeTenant(TenantInfoBean tenantInfoBean) {
        Tenant tenant = new Tenant();
        tenant.setDomain(tenantInfoBean.getTenantDomain());
        tenant.setEmail(tenantInfoBean.getEmail());
        tenant.setAdminName(tenantInfoBean.getAdmin());

        // we are duplicating the params stored in the claims here as well; they
        // are in Tenant class
        // to make it work with LDAP; but they do not make it to the databases.
        tenant.setAdminFirstName(tenantInfoBean.getFirstname());
        tenant.setAdminLastName(tenantInfoBean.getLastname());

        tenant.setAdminPassword(tenantInfoBean.getAdminPassword());

        // sets created date.
        Calendar createdDateCal = tenantInfoBean.getCreatedDate();
        long createdDate;
        if (createdDateCal != null) {
            createdDate = createdDateCal.getTimeInMillis();
        } else {
            createdDate = System.currentTimeMillis();
        }
        tenant.setCreatedDate(new Date(createdDate));

        if (log.isDebugEnabled()) {
            log.debug("Tenant object Initialized from the TenantInfoBean");
        }
        return tenant;
    }

    /**
     * Initializes a tenantInfoBean object for a given tenant.
     *
     * @param tenantId tenant id.
     * @param tenant   a tenant.
     * @return tenantInfoBean
     * @throws Exception , exception in getting the adminUserName from tenantId
     */
    public static TenantInfoBean initializeTenantInfoBean(
            int tenantId, Tenant tenant) throws Exception {
        TenantInfoBean bean = getTenantInfoBeanfromTenant(tenantId, tenant);
        if (tenant != null) {
            bean.setAdmin(ClaimsMgtUtil.getAdminUserNameFromTenantId(
                    TenantMgtServiceComponent.getRealmService(), tenantId));
        }
        return bean;
    }

    /**
     * initializes a TenantInfoBean object from the tenant
     * @param tenantId, tenant id
     * @param tenant, tenant
     * @return TenantInfoBean.
     */
    public static TenantInfoBean getTenantInfoBeanfromTenant(int tenantId, Tenant tenant) {
        TenantInfoBean bean = new TenantInfoBean();
        if (tenant != null) {
            bean.setTenantId(tenantId);
            bean.setTenantDomain(tenant.getDomain());
            bean.setEmail(tenant.getEmail());

            /*gets the created date*/
            Calendar createdDate = Calendar.getInstance();
            createdDate.setTimeInMillis(tenant.getCreatedDate().getTime());
            bean.setCreatedDate(createdDate);

            bean.setActive(tenant.isActive());
            if(log.isDebugEnabled()) {
                log.debug("The TenantInfoBean object has been created from the tenant.");
            }
        } else {
            if(log.isDebugEnabled()) {
                log.debug("The tenant is null.");
            }
        }
        return bean;
    }

    /**
     * Sends validation mail to the tenant admin upon the tenant creation
     *
     * @param tenant            - the registered tenant
     * @param originatedService - originated service of the registration request
     * @throws Exception, if the sending mail failed
     */
    public static void sendEmail(Tenant tenant, String originatedService) throws Exception {
        String firstname = ClaimsMgtUtil.getFirstName(TenantMgtServiceComponent.getRealmService(),
                                                      tenant, tenant.getId());
        String adminName = ClaimsMgtUtil.getAdminUserNameFromTenantId(
                TenantMgtServiceComponent.getRealmService(), tenant.getId());

        String confirmationKey = generateConfirmationKey(
                tenant, originatedService, TenantMgtServiceComponent.getConfigSystemRegistry(
                MultitenantConstants.SUPER_TENANT_ID), tenant.getId());

        if (CommonUtil.isTenantActivationModerated()) {
            requestSuperTenantModification(tenant, confirmationKey, firstname, adminName);
        } else {
            //request for verification
            requestUserVerification(tenant, confirmationKey, firstname, adminName);
        }

        // If Email Validation is made optional, tenant will be activated now.
        if (!CommonUtil.isEmailValidationMandatory()) {
            TenantMgtServiceComponent.getTenantManager().activateTenant(tenant.getId());
            if (log.isDebugEnabled()) {
                log.debug("Activated the tenant during the tenant creation: " + tenant.getId());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Tenant Successfully added and persisted.");
        }
    }

    /**
     * generates the confirmation key for the tenant
     *
     * @param tenant            - a tenant
     * @param originatedService - originated service of the registration
     * @param superTenantConfigSystemRegistry
     *                          - super tenant config system registry.
     * @param tenantId          tenantId
     * @return confirmation key
     * @throws RegistryException if generation of the confirmation key failed.
     */
    private static String generateConfirmationKey(Tenant tenant, String originatedService,
                                                  UserRegistry superTenantConfigSystemRegistry,
                                                  int tenantId) throws RegistryException {
        // generating the confirmation key
        String confirmationKey = UUIDGenerator.generateUUID();
        UserRegistry superTenantGovernanceSystemRegistry;
        try {
            superTenantGovernanceSystemRegistry =
                    TenantMgtServiceComponent.
                            getGovernanceSystemRegistry(MultitenantConstants.SUPER_TENANT_ID);
        } catch (RegistryException e) {
            String msg = "Exception in getting the governance system registry for the super tenant";
            log.error(msg, e);
            throw new RegistryException(msg, e);
        }
        Resource resource;
        String emailVerificationPath = StratosConstants.ADMIN_EMAIL_VERIFICATION_FLAG_PATH +
                                       RegistryConstants.PATH_SEPARATOR + tenantId;
        try {
            if (superTenantGovernanceSystemRegistry.resourceExists(emailVerificationPath)) {
                resource = superTenantGovernanceSystemRegistry.get(emailVerificationPath);
            } else {
                resource = superTenantGovernanceSystemRegistry.newResource();
            }
            resource.setContent(confirmationKey);
        } catch (RegistryException e) {
            String msg = "Error in creating the resource or getting the resource" +
                         "from the email verification path";
            log.error(msg, e);
            throw new RegistryException(msg, e);
        }
        // email is not validated yet, this prop is used to activate the tenant
        // later.
        resource.addProperty(StratosConstants.IS_EMAIL_VALIDATED, "false");
        resource.addProperty(StratosConstants.TENANT_ADMIN, tenant.getAdminName());
        try {
            superTenantGovernanceSystemRegistry.put(emailVerificationPath, resource);
        } catch (RegistryException e) {
            String msg = "Error in putting the resource to the super tenant registry" +
                         " for the email verification path";
            log.error(msg, e);
            throw new RegistryException(msg, e);
        }

        // Used for * as a Service impl.
        // Store the cloud service from which the register req. is originated.
        if (originatedService != null) {
            String originatedServicePath =
                    StratosConstants.ORIGINATED_SERVICE_PATH +
                    StratosConstants.PATH_SEPARATOR +
                    StratosConstants.ORIGINATED_SERVICE +
                    StratosConstants.PATH_SEPARATOR + tenantId;
            try {
                Resource origServiceRes = superTenantConfigSystemRegistry.newResource();
                origServiceRes.setContent(originatedService);
                superTenantGovernanceSystemRegistry.put(originatedServicePath, origServiceRes);
            } catch (RegistryException e) {
                String msg = "Error in putting the originated service resource "
                             + "to the governance registry";
                log.error(msg, e);
                throw new RegistryException(msg, e);
            }
        }
        initializeRegistry(tenant.getId());
        if (log.isDebugEnabled()) {
            log.debug("Successfully generated the confirmation key.");
        }
        return confirmationKey;
    }

    /**
     * Initializes the registry for the tenant.
     *
     * @param tenantId tenant id.
     */
    private static void initializeRegistry(int tenantId) {
        BundleContext bundleContext = TenantMgtServiceComponent.getBundleContext();
        if (bundleContext != null) {
            ServiceTracker tracker =
                    new ServiceTracker(bundleContext,
                                       AuthenticationObserver.class.getName(),
                                       null);
            tracker.open();
            Object[] services = tracker.getServices();
            if (services != null) {
                for (Object service : services) {
                    ((AuthenticationObserver) service).startedAuthentication(tenantId);
                }
            }
            tracker.close();
        }
    }

    /**
     * request email verification from the user.
     *
     * @param tenant          a tenant
     * @param confirmationKey confirmation key.
     * @param firstname       calling name
     * @throws Exception if an exception is thrown from EmailVerificationSubscriber.
     */
    private static void requestUserVerification(Tenant tenant, String confirmationKey,
                                                String firstname, String adminName) throws Exception {
        try {
            Map<String, String> dataToStore = new HashMap<String, String>();
            dataToStore.put("email", tenant.getEmail());
            dataToStore.put("first-name", firstname);
            dataToStore.put("admin", adminName);
            dataToStore.put("tenantDomain", tenant.getDomain());
            dataToStore.put("confirmationKey", confirmationKey);

            EmailVerifcationSubscriber emailVerifier =
                    TenantMgtServiceComponent.getEmailVerificationService();
            emailVerifier.requestUserVerification(
                    dataToStore, TenantMgtServiceComponent.getEmailVerifierConfig());
            if (log.isDebugEnabled()) {
                log.debug("Email verification for the tenant registration.");
            }
        } catch (Exception e) {
            String msg = "Error in notifying tenant of domain: " + tenant.getDomain();
            log.error(msg);
            throw new Exception(msg, e);
        }
    }

    /**
     * Sends mail for the super tenant for the account moderation. Once super tenant clicks the
     * link provided in the email, the tenant will be activated.
     *
     * @param tenant          the tenant who registered an account
     * @param confirmationKey confirmation key.
     * @param firstname       calling name of the tenant
     * @param adminName       the tenant admin name
     * @throws Exception if an exception is thrown from EmailVerificationSubscriber.
     */
    private static void requestSuperTenantModification(Tenant tenant, String confirmationKey,
                                                String firstname,
                                                String adminName) throws Exception {
        try {
            Map<String, String> dataToStore = new HashMap<String, String>();
            dataToStore.put("email", CommonUtil.getSuperAdminEmail());
            dataToStore.put("first-name", firstname);
            dataToStore.put("admin", adminName);
            dataToStore.put("tenantDomain", tenant.getDomain());
            dataToStore.put("confirmationKey", confirmationKey);

            EmailVerifcationSubscriber emailVerifier =
                    TenantMgtServiceComponent.getEmailVerificationService();
            emailVerifier.requestUserVerification(
                    dataToStore, TenantMgtServiceComponent.getSuperTenantEmailVerifierConfig());
            if (log.isDebugEnabled()) {
                log.debug("Email verification for the tenant registration.");
            }
        } catch (Exception e) {
            String msg = "Error in notifying the super tenant on the account creation for " +
                         "the domain: " + tenant.getDomain();
            log.error(msg);
            throw new Exception(msg, e);
        }
    }

    /**
     * Adds claims to UserStoreManager
     *
     * @param tenant a tenant
     * @throws Exception if error in adding claims to the user.
     */
    public static void addClaimsToUserStoreManager(Tenant tenant) throws Exception {
        try {
            Map<String, String> claimsMap = new HashMap<String, String>();

            claimsMap.put(UserCoreConstants.ClaimTypeURIs.GIVEN_NAME, tenant.getAdminFirstName());
            claimsMap.put(UserCoreConstants.ClaimTypeURIs.SURNAME, tenant.getAdminLastName());

            // can be extended to store other user information.
            UserStoreManager userStoreManager =
                    (UserStoreManager) TenantMgtServiceComponent.getRealmService().
                            getTenantUserRealm(tenant.getId()).getUserStoreManager();
            userStoreManager.setUserClaimValues(tenant.getAdminName(), claimsMap,
                                                UserCoreConstants.DEFAULT_PROFILE);

        } catch (Exception e) {
            String msg = "Error in adding claims to the user.";
            log.error(msg, e);
            throw new Exception(msg, e);
        }
    }


}
