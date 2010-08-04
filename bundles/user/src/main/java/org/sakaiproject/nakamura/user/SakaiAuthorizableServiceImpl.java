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
package org.sakaiproject.nakamura.user;

import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_AUTHORIZABLE_PATH;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_REPO_LOCATION;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.principal.ItemBasedPrincipal;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.apache.sling.jcr.resource.JcrResourceUtil;
import org.apache.sling.servlets.post.Modification;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.user.AuthorizablePostProcessService;
import org.sakaiproject.nakamura.api.user.SakaiAuthorizableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

/**
 *
 */
@Component(immediate=true)
@Service
public class SakaiAuthorizableServiceImpl implements SakaiAuthorizableService {
  private static final Logger LOGGER = LoggerFactory.getLogger(SakaiAuthorizableServiceImpl.class);

  @Reference
  protected transient AuthorizablePostProcessService authorizablePostProcessService;

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.user.SakaiAuthorizableService#createUser(java.lang.String, java.lang.String, java.util.Map, javax.jcr.Session)
   */
  public User createUser(String userId, String password,
      Map<String, Object> extraProperties, Session session) throws RepositoryException {
    LOGGER.info("Creating user {}", userId);
    UserManager userManager = AccessControlUtil.getUserManager(session);
    User user = userManager.createUser(userId, password);
    if (extraProperties != null) {
      Set<Entry<String, Object>> entrySet = extraProperties.entrySet();
      for (Entry<String, Object> entry : entrySet) {
        Value value = JcrResourceUtil.createValue(entry.getValue(), session);
        user.setProperty(entry.getKey(), value);
      }
    }
    postprocess(user, session);
    return user;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.user.SakaiAuthorizableService#createUser(java.lang.String, java.lang.String, javax.jcr.Session)
   */
  public User createUser(String userId, String password, Session session)
      throws RepositoryException {
    return createUser(userId, password, null, session);
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.user.SakaiAuthorizableService#postprocess(Authorizable, javax.jcr.Session)
   */
  public void postprocess(Authorizable authorizable, Session session) throws RepositoryException {
    if (!authorizable.isGroup()) {
      ensurePath((User) authorizable, session);
    }

    if (authorizablePostProcessService != null) {
      try {
        authorizablePostProcessService.process(authorizable, session, Modification.onCreated(authorizable.getID()));
      } catch (Exception e) {
        LOGGER.error("Postprocessing for user " + authorizable.getID() + " failed", e);
      }
    }
  }

  public void notifyBinding() {
    // TODO Auto-generated method stub

  }

  @Activate
  protected void activate(ComponentContext componentContext) {
    authorizablePostProcessService.bindSakaiAuthorizableService(this);
  }

  @Deactivate
  protected void deactivate(ComponentContext componentContext) {
    authorizablePostProcessService.unbindSakaiAuthorizableService(this);
  }

  /**
   * Initialize the Sakai-3-specific "path" property.
   *
   * TODO It looks like the current code base recreates this logic more often
   * than it uses this property. We should enforce one approach or the other.
   *
   * @param user
   * @param session
   * @throws RepositoryException
   */
  private void ensurePath(User user, Session session) throws RepositoryException {
    if (!user.hasProperty(PROP_AUTHORIZABLE_PATH)) {
      Principal principal = user.getPrincipal();
      if (principal instanceof ItemBasedPrincipal) {
        String itemPath = ((ItemBasedPrincipal) principal).getPath();
        String path = itemPath.substring(USER_REPO_LOCATION.length());
        ValueFactory valueFactory = session.getValueFactory();
        user.setProperty(PROP_AUTHORIZABLE_PATH, valueFactory.createValue(path));
        LOGGER.info("User {} path set to {} ",user.getID(), path);
      } else {
        LOGGER.warn("User {} has no available path", user.getID());
      }
    }
  }
}
