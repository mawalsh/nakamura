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
package org.sakaiproject.nakamura.files.search;

import static org.apache.sling.jcr.resource.JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.files.FileUtils;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * Formats the files search results.
 */
@Component(immediate = true, metatype = true)
@Properties(value = { @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = SolrSearchConstants.REG_BATCH_PROCESSOR_NAMES, value = "LiteFiles") })
@Service(value = SolrSearchBatchResultProcessor.class)
public class LiteFileSearchBatchResultProcessor implements SolrSearchBatchResultProcessor {

  public static final Logger LOGGER = LoggerFactory
      .getLogger(LiteFileSearchBatchResultProcessor.class);

  @Reference
  private SolrSearchServiceFactory searchServiceFactory;

  public LiteFileSearchBatchResultProcessor(SolrSearchServiceFactory searchServiceFactory) {
    this.searchServiceFactory = searchServiceFactory;
  }

  public LiteFileSearchBatchResultProcessor() {
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchResultProcessor#getSearchResultSet(org.apache.sling.api.SlingHttpServletRequest,
   *      org.sakaiproject.nakamura.api.search.solr.Query)
   */
  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request,
      Query query) throws SolrSearchException {

    return searchServiceFactory.getSearchResultSet(request, query);
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchResultProcessor#writeResults(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.commons.json.io.JSONWriter, java.util.Iterator)
   */
  public void writeResults(SlingHttpServletRequest request, JSONWriter write,
      Iterator<Result> iterator) throws JSONException {
    final Integer iDepth = (Integer) request.getAttribute("depth");
    int depth = 0;
    if (iDepth != null) {
      depth = iDepth.intValue();
    }
    try {
      final Session session = StorageClientUtils.adaptToSession(request
          .getResourceResolver().adaptTo(javax.jcr.Session.class));
      while (iterator.hasNext()) {
        final Result result = iterator.next();
        final Content content = session.getContentManager().get(result.getPath());
        handleContent(content, session, write, depth);
      }
    } catch (StorageClientException e) {
      throw new JSONException(e);
    } catch (AccessDeniedException e) {
      throw new JSONException(e);
    }
  }

  /**
   * Give a JSON representation of the content.
   * 
   * @param content
   * @param session
   * @param write
   *          The {@link JSONWriter} to output to.
   * @param depth
   * @throws JSONException
   * @throws StorageClientException
   */
  protected void handleContent(final Content content, final Session session,
      final JSONWriter write, final int depth) throws JSONException,
      StorageClientException {

    final String type = (String) content.getProperty(SLING_RESOURCE_TYPE_PROPERTY);
    if (FilesConstants.RT_SAKAI_LINK.equals(type)) {
      FileUtils.writeLinkNode(content, session, write);
    } else {
      FileUtils.writeFileNode(content, session, write, depth);
    }
  }

}
