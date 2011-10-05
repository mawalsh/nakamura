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

package org.sakaiproject.nakamura.upgrade.servlet;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.sakaiproject.nakamura.api.lite.Feedback;
import org.sakaiproject.nakamura.api.lite.MigrateContentService;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(paths = {"/system/sparseupgrade"}, generateComponent = true, generateService = true, methods = {"POST"})
public class SparseUpgradeServlet extends SlingAllMethodsServlet {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparseUpgradeServlet.class);

  @Reference
  private MigrateContentService migrationService;

  @Override
  protected void doPost(SlingHttpServletRequest request, final SlingHttpServletResponse response) throws ServletException, IOException {
    try {

      // make sure user's an admin
      Session currentSession = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(javax.jcr.Session.class));
      AuthorizableManager authorizableManager = currentSession.getAuthorizableManager();
      User currentUser = (User) authorizableManager.findAuthorizable(currentSession.getUserId());
      if (!currentUser.isAdmin()) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "You must be an admin to run upgrades");
        return;
      }

      // collect our parameters
      boolean dryRun = true;
      boolean reindexAll = false;
      Integer limit = Integer.MAX_VALUE;

      RequestParameter dryRunParam = request.getRequestParameter("dryRun");
      if (dryRunParam != null) {
        dryRun = Boolean.valueOf(dryRunParam.getString());
      }
      RequestParameter reindexAllParam = request.getRequestParameter("reindexAll");
      if (reindexAllParam != null) {
        reindexAll = Boolean.valueOf(reindexAllParam.getString());
      }
      RequestParameter limitParam = request.getRequestParameter("limit");
      if (limitParam != null) {
        limit = Integer.parseInt(limitParam.getString());
      }

      String msg = "About to call migration service with dryRun = " + dryRun + "; limit = " + limit
              + "; reindexAll = " + reindexAll + "; check your server log for more detailed information.";
      writeToResponse(msg, response);
      LOGGER.info(msg);

      // do the actual migration
      this.migrationService.migrate(dryRun, limit, reindexAll, getFeedback(response));

    } catch (Exception e) {
      LOGGER.error("Got exception processing sparse upgrade", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  private Feedback getFeedback(final SlingHttpServletResponse response) {
    return new Feedback() {
      public void log(String format, Object... params) {
        LOGGER.info(MessageFormat.format(format, params));
      }

      public void exception(Throwable e) {
        String msg = "An exception occurred while migrating: " + e.getClass().getName() + ": " +
                e.getMessage() + "; check server log for more details.";
        writeToResponse(msg, response);
      }

      public void newLogFile(File currentFile) {
        LOGGER.info("Opening New Upgrade Log File {}  ", currentFile.getAbsoluteFile());
      }

      public void progress(boolean dryRun, long done, long toDo) {
        String msg = "Processed " + done + " of " + toDo + ", " + ((done * 100) / toDo) + "% complete, dryRun=" + dryRun;
        writeToResponse(msg, response);
      }

    };
  }

  private void writeToResponse(String msg, SlingHttpServletResponse response) {
    try {
      response.getWriter().write(msg + "\n");
      response.getWriter().flush(); // so the client sees updates
    } catch (IOException ioe) {
      LOGGER.error("Got IOException trying to write http response", ioe);
    }
  }
}
