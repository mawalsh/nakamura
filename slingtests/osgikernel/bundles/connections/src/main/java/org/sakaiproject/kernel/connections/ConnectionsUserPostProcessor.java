/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.kernel.connections;

import static org.sakaiproject.kernel.api.user.UserConstants.SYSTEM_USER_MANAGER_USER_PATH;
import static org.sakaiproject.kernel.util.ACLUtils.ADD_CHILD_NODES_GRANTED;
import static org.sakaiproject.kernel.util.ACLUtils.READ_DENIED;
import static org.sakaiproject.kernel.util.ACLUtils.WRITE_DENIED;
import static org.sakaiproject.kernel.util.ACLUtils.MODIFY_PROPERTIES_GRANTED;
import static org.sakaiproject.kernel.util.ACLUtils.REMOVE_CHILD_NODES_GRANTED;
import static org.sakaiproject.kernel.util.ACLUtils.REMOVE_NODE_GRANTED;
import static org.sakaiproject.kernel.util.ACLUtils.WRITE_GRANTED;
import static org.sakaiproject.kernel.util.ACLUtils.READ_GRANTED;
import static org.sakaiproject.kernel.util.ACLUtils.addEntry;

import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.sakaiproject.kernel.api.connections.ConnectionConstants;
import org.sakaiproject.kernel.api.user.UserConstants;
import org.sakaiproject.kernel.api.user.UserPostProcessor;
import org.sakaiproject.kernel.util.JcrUtils;
import org.sakaiproject.kernel.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.jcr.Node;
import javax.jcr.Session;

/**
 * This PostProcessor listens to post operations on User objects and creates a connection
 * store.
 * 
 * @scr.service interface="org.sakaiproject.kernel.api.user.UserPostProcessor"
 * @scr.property name="service.vendor" value="The Sakai Foundation"
 * @scr.component immediate="true" label="ConnectionsUserPostProcessor" description=
 *                "Post Processor for User and Group operations to create a connection store"
 *                metatype="no"
 * @scr.property name="service.description"
 *               value="Post Processes User and Group operations"
 * 
 */
public class ConnectionsUserPostProcessor implements UserPostProcessor {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(ConnectionsUserPostProcessor.class);

  public void process(Session session, SlingHttpServletRequest request,
      List<Modification> changes) throws Exception {
    String resourcePath = request.getRequestPathInfo().getResourcePath();
    if (resourcePath.equals(SYSTEM_USER_MANAGER_USER_PATH)) {
      String principalName = null;
      UserManager userManager = AccessControlUtil.getUserManager(session);
      PrincipalManager principalManager = AccessControlUtil.getPrincipalManager(session);
      RequestParameter rpid = request
          .getRequestParameter(SlingPostConstants.RP_NODE_NAME);
      if (rpid != null) {
        principalName = rpid.getString();
        Authorizable authorizable = userManager.getAuthorizable(principalName);
        if (authorizable != null) {

          String path = PathUtils.toInternalHashedPath(
              ConnectionUtils.CONNECTION_PATH_ROOT, principalName, "");
          LOGGER.debug("Creating connections store: {}", path);

          Node store = JcrUtils.deepGetOrCreateNode(session, path);
          store.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
              ConnectionConstants.SAKAI_CONTACT_RT);
          // ACL's are managed by the Personal User Post processor.
          Authorizable anon = userManager.getAuthorizable(UserConstants.ANON_USERID);
          Authorizable everyone = userManager.getAuthorizable(principalManager
              .getEveryone());

          addEntry(store.getPath(), authorizable, session, READ_GRANTED, WRITE_GRANTED,
              REMOVE_CHILD_NODES_GRANTED, MODIFY_PROPERTIES_GRANTED,
              ADD_CHILD_NODES_GRANTED, REMOVE_NODE_GRANTED);

          // explicitly deny anon and everyone, this is private space.
          addEntry(store.getPath(), anon, session, READ_DENIED, WRITE_DENIED);
          addEntry(store.getPath(), everyone, session, READ_DENIED, WRITE_DENIED);

        }
      }
    }

  }

}
