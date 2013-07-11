
/*
 * Copyright (c) 2005-2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.apimgt.interceptor.valve.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.apimgt.interceptor.valve.APIManagerInterceptorValve;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.tomcat.ext.valves.CarbonTomcatValve;
import org.wso2.carbon.tomcat.ext.valves.TomcatValveContainer;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.ConfigurationContextService;

import java.util.ArrayList;

/**

 * @scr.component name="org.wso2.carbon.apimgt.interceptor.valve.internal" immediate="true"
 
 * @scr.reference name="config.context.service" interface="org.wso2.carbon.utils.ConfigurationContextService"
 
 * cardinality="1..1" policy="dynamic" bind="setConfigurationContextService" unbind="unsetConfigurationContextService"
 * 
 * @scr.reference name="registry.service" interface="org.wso2.carbon.registry.core.service.RegistryService"
 * cardinality="1..1" policy="dynamic" bind="setRegistryService" unbind="unsetRegistryService"
 */
public class APIManagerInterceptorComponent {
	private static final Log log = LogFactory.getLog(APIManagerInterceptorComponent.class);
    private static String apiManagementEnabled;
    private static String externalAPIManagerURL;

    protected void activate(ComponentContext ctx) {
    	if (log.isDebugEnabled()) {
    		log.info("Activating API Manager Interceptor Component");
    	}
        apiManagementEnabled = CarbonUtils.getServerConfiguration().getFirstProperty("EnableAPIManagement");
        externalAPIManagerURL = CarbonUtils.getServerConfiguration().getFirstProperty("APIGateway");

    	//Register the valves with Tomcat
        if (apiManagementEnabled.equalsIgnoreCase("true")) {
            ArrayList<CarbonTomcatValve> valves = new ArrayList<CarbonTomcatValve>();
            valves.add(new APIManagerInterceptorValve());
            TomcatValveContainer.addValves(valves);
        }
    }
    
    protected void deactivate(ComponentContext componentContext) {
    	
    }
    protected void setConfigurationContextService(ConfigurationContextService contextService) {
        DataHolder.setServerConfigContext(contextService.getServerConfigContext());
    }

    protected void unsetConfigurationContextService(ConfigurationContextService contextService) {
        DataHolder.setServerConfigContext(null);
    }
    
    protected void setRegistryService(RegistryService registryService) {
        if (registryService != null && log.isDebugEnabled()) {
            log.debug("Registry service initialized");
        }
        DataHolder.setRegistryService(registryService);
    }

    protected void unsetRegistryService(RegistryService registryService) {
    	DataHolder.setRegistryService(null);
    }

    public static String getAPIManagementEnabled() {
        return apiManagementEnabled;
    }

    public static String getExternalAPIManagerURL() {
        return externalAPIManagerURL;
    }

}

