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

import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_ITEMS_PER_PAGE;

import com.google.common.collect.Lists;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchUtil;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component(inherit = true, metatype=true, immediate = true)
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = SolrSearchConstants.REG_BATCH_PROCESSOR_NAMES, value = "RandomContent")
})
@Service(value = SolrSearchBatchResultProcessor.class)
public class RandomContentSearchBatchResultProcessor extends LiteFileSearchBatchResultProcessor {


  /*
   * Increase the numbers of items the solr query returns by this amount.
   */
  @Property(name = "solrItemsMultiplier", intValue = 4)
  private int solrItemsMultiplier;

  public static final Logger LOGGER = LoggerFactory
  .getLogger(RandomContentSearchBatchResultProcessor.class);


  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query) throws SolrSearchException {

    Map<String, String> options = query.getOptions();

    // find the number of items solr has be requested to return.
    String originalItems = options.get(PARAMS_ITEMS_PER_PAGE); // items
    int originalItemsInt = Integer.parseInt(originalItems);

    // increase the number of items solr will return.
    int newItemsInt = originalItemsInt * solrItemsMultiplier;

    // increase the maximum items returned by solr
    query.getOptions().put(PARAMS_ITEMS_PER_PAGE, Integer.toString(newItemsInt));

    // do the query
    SolrSearchResultSet rs = searchServiceFactory.getSearchResultSet(request, query);

    // get all the results back from solr, and stuff into a List.
    List<Result> solrResults = Lists.newArrayList(rs.getResultSetIterator());

    // randomise the results, by shuffling the List
    Collections.shuffle(solrResults);

    // reduce solr returned list size, to the originally asked for size.  
    if(solrResults.size() > originalItemsInt) {
      solrResults = solrResults.subList(0,originalItemsInt);
    }

    // create new SolrSearchResultSet object, to be returned by this method.
    SolrSearchResultSet randomSolrResultSet = new RandomContentSolrSearchResultSetImpl(solrResults);

    return randomSolrResultSet;
  }

  /**
   * When the bundle gets activated we retrieve the OSGi properties.
   *
   * @param context
   */
  @SuppressWarnings("rawtypes")
  protected void activate(ComponentContext context) {
    // Get the properties from the console.
    Dictionary props = context.getProperties();
    solrItemsMultiplier = OsgiUtil.toInteger(props.get("solrItemsMultiplier"), 4);
  }

  // inner class, use by method getSearchResultSet(..),
  // to return a object of type SolrSearchResultSet
  private class RandomContentSolrSearchResultSetImpl implements SolrSearchResultSet {
    private List<Result> results;

    public RandomContentSolrSearchResultSetImpl(List<Result> results) {
      this.results = results;
    }

    public Iterator<Result> getResultSetIterator() {
      return results.iterator();
    }

    public long getSize() {
      return results.size();
    }
  }

}
