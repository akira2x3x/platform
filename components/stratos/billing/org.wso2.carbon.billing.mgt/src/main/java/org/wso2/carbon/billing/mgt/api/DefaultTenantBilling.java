package org.wso2.carbon.billing.mgt.api;

import java.util.Calendar;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.billing.core.dataobjects.Customer;
import org.wso2.carbon.billing.core.dataobjects.Item;
import org.wso2.carbon.billing.core.dataobjects.Subscription;
import org.wso2.carbon.billing.mgt.services.BillingDataAccessService;
import org.wso2.carbon.stratos.common.beans.TenantBillingService;
import org.wso2.carbon.stratos.common.exception.StratosException;
import org.wso2.carbon.stratos.common.internal.CloudCommonServiceComponent;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.TenantManager;

public class DefaultTenantBilling implements TenantBillingService {
    
    private static final Log log = LogFactory.getLog(DefaultTenantBilling.class);
    
    public void addUsagePlan(Tenant tenant, String usagePlan) throws StratosException {

        Customer customer = new Customer();
        customer.setName(tenant.getDomain());
        customer.setEmail(tenant.getEmail());
        customer.setStartedDate(tenant.getCreatedDate());
        customer.setFullName(tenant.getAdminFirstName() + " " + tenant.getAdminLastName());

        customer.setId(tenant.getId());
        Subscription subscription = new Subscription();
        subscription.setCustomer(customer);
        subscription.setActive(false);
        subscription.setActiveSince(Calendar.getInstance().getTime());
        Item item = new Item();
        subscription.setItem(item);
        String planName = usagePlan;
        subscription.setSubscriptionPlan(planName);
        int subscriptionId = 0;
        try {
            BillingDataAccessService dataAccessService = new BillingDataAccessService();
            subscriptionId = dataAccessService.addSubscription(subscription);
        } catch (Exception e) {
            log.error("Could not add new subscription for tenant: " +
                      tenant.getDomain() + " " + e.getMessage(), e);
            return;
        }
    }

    public String getActiveUsagePlan(String tenantDomain) throws StratosException {
        Subscription subscription;
        try {
            TenantManager tenantMan = CloudCommonServiceComponent.getRealmService().getTenantManager();
            int tenantId = tenantMan.getTenantId(tenantDomain);
            BillingDataAccessService billingDataAccessService = new BillingDataAccessService();
            subscription = billingDataAccessService.getActiveSubscriptionOfCustomer(tenantId);
        } catch (Exception e) {
            String msg = "Error occurred while getting the usage plan for tenant: " + 
                         tenantDomain + " " + e.getMessage();
            log.error(msg);
            throw new StratosException(msg, e);
        }

        return subscription.getSubscriptionPlan();
    }

    public void updateUsagePlan(String tenantDomain, String usagePlan) throws StratosException {
        try {
            TenantManager tenantMan = CloudCommonServiceComponent.getRealmService().getTenantManager();
            int tenantId = tenantMan.getTenantId(tenantDomain);
            BillingDataAccessService billingDataAccessService = new BillingDataAccessService();
            Subscription currentSubscription = billingDataAccessService.getActiveSubscriptionOfCustomer(tenantId);
            if (currentSubscription != null && currentSubscription.getSubscriptionPlan() != null) {
                if (!currentSubscription.getSubscriptionPlan().equals(usagePlan)) {
                    boolean updated = billingDataAccessService.changeSubscription(tenantId, usagePlan);
                    if (updated) {
                        log.debug("Usage plan was changed successfully from " + currentSubscription.getSubscriptionPlan() +
                                  " to " + usagePlan);
                    } else {
                        log.debug("Usage plan was not changed");
                    }
                }
            }else{
                //tenant doesnot have an active subscription. First we have to check whether the tenant
                //is active. If he is active only we will add a new usage plan. Otherwise it is useless
                //to add a usage plan to an inactive tenant
                Tenant tenant = tenantMan.getTenant(tenantId);
                if(tenant.isActive()){
                    //we add a new subscription
                    Subscription subscription = new Subscription();
                    subscription.setActive(true);
                    subscription.setSubscriptionPlan(usagePlan);
                    subscription.setActiveSince(null);
                    subscription.setActiveUntil(null);
                    Customer customer = new Customer();
                    customer.setName(tenantDomain);
                    customer.setId(tenantId);
                    subscription.setCustomer(customer);

                    int subsId = billingDataAccessService.addSubscription(subscription);
                    if(subsId>0){
                        log.info("Added a new " + subscription.getSubscriptionPlan() + " plan for the tenant " +
                                 tenantDomain);
                    }
                }
            }
        } catch (Exception e) {
            String msg = "Error occurred while changing the subscription plan for tenant: " + tenantDomain;
            log.error(msg, e);
            throw new StratosException(msg, e);
        }
    }

    public void activateUsagePlan(String tenantDomain) throws StratosException {
        try {
            TenantManager tenantMan = CloudCommonServiceComponent.getRealmService().getTenantManager();
            int tenantId = tenantMan.getTenantId(tenantDomain);
            BillingDataAccessService dataAccessService = new BillingDataAccessService();
            Subscription subscription = dataAccessService.getActiveSubscriptionOfCustomer(tenantId);
            if (subscription != null) {
                String msg = "Cant activate subscription for tenant: " + tenantId +
                             ". An active subscription already exists";
                log.info(msg);
            } else {
                Subscription[] inactiveSubscriptions = dataAccessService.getInactiveSubscriptionsOfCustomer(tenantId);
                if (inactiveSubscriptions.length == 1) {
                    //This is the scenario where the tenant has registered, but not activated yet
                    subscription = inactiveSubscriptions[0];
                    boolean activated = dataAccessService.activateSubscription(subscription.getId());
                    if (activated) {
                        log.info("Subscription was activated for tenant: " + tenantId);
                    }
                }else if(inactiveSubscriptions.length > 1){
                    //this is the scenario where the tenant has been deactivated by admin and
                    //again activated. Here, I am adding a new active subscription which is similar to the
                    //last existed one
                    Subscription subscriptionToAdd = inactiveSubscriptions[0];
                    subscriptionToAdd.setActive(true);
                    subscriptionToAdd.setActiveSince(null);
                    subscriptionToAdd.setActiveUntil(null);

                    int subsId = dataAccessService.addSubscription(subscriptionToAdd);
                    if(subsId>0){
                        log.info("New subscription: " + subscriptionToAdd.getSubscriptionPlan() +
                                " added and it was activated for tenant: " + tenantId);
                    }
                }else{
                    //this means there are no subscriptions. Lets handle this later
                }
            }
        } catch (Exception e) {
            String msg = "Error occurred while activating the subscription for tenant: " + tenantDomain +
                         " " + e.getMessage();
            log.error(msg);
            throw new StratosException(msg, e);
        }
    }

    public void deactivateActiveUsagePlan(String tenantDomain) throws StratosException{
        try{
            TenantManager tenantMan = CloudCommonServiceComponent.getRealmService().getTenantManager();
            int tenantId = tenantMan.getTenantId(tenantDomain);
            BillingDataAccessService dataAccessService = new BillingDataAccessService();
            boolean deactivated = dataAccessService.deactivateActiveSubscription(tenantId);
            if(deactivated){
                log.info("Active subscription of tenant " + tenantId + " was deactivated");
            }
        }catch (Exception e){
            log.error("Error occurred while deactivating the active subscription of tenant: " + tenantDomain +
                        " " + e.getMessage(), e);
        }
    }


}
