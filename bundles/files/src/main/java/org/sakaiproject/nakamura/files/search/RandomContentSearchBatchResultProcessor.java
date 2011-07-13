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

import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.DEFAULT_PAGED_ITEMS;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_ITEMS_PER_PAGE;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component(immediate = true, metatype=true)
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = SolrSearchConstants.REG_BATCH_PROCESSOR_NAMES, value = "RandomContent")
})
@Service(value = SolrSearchBatchResultProcessor.class)
public class RandomContentSearchBatchResultProcessor implements SolrSearchBatchResultProcessor {

  private int solrItemsMultipler = 4;
  private int maximumSolrSearchResultSetSize = 16;
  public static final Logger LOGGER = LoggerFactory
  .getLogger(RandomContentSearchBatchResultProcessor.class);

  @Reference
  protected SolrSearchServiceFactory searchServiceFactory;

  /**
   * The non component constructor
   * @param searchServiceFactory
   */
  RandomContentSearchBatchResultProcessor(SolrSearchServiceFactory searchServiceFactory) {
    if ( searchServiceFactory == null ) {
      throw new IllegalArgumentException("Search Service Factory must be set when not using as a component");
    }
    this.searchServiceFactory = searchServiceFactory;
  }


  /**
   * Component Constructor.
   */
  public RandomContentSearchBatchResultProcessor() {
  }



  public void writeResults(SlingHttpServletRequest request, JSONWriter write,
      Iterator<Result> iterator) throws JSONException {


    long nitems = SolrSearchUtil.longRequestParameter(request,
        PARAMS_ITEMS_PER_PAGE, DEFAULT_PAGED_ITEMS);



    for (long i = 0; i < nitems && iterator.hasNext(); i++) {
      Result result = iterator.next();
      ExtendedJSONWriter.writeValueMap(write,result.getProperties());
    }
  }


  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query) throws SolrSearchException {

    Map<String, String> options = query.getOptions();

    // find the number of items solr has be requested to return.
    String originalItems = options.get(PARAMS_ITEMS_PER_PAGE); // items
    int originalItemsInt = Integer.parseInt(originalItems);

    // increase the number of items solr will return.
    int newItemsInt = originalItemsInt * solrItemsMultipler;

    // set upper limit for items that query is expected to return.
    // as per jira kern-1998 set upper value to 16.
    if (newItemsInt > maximumSolrSearchResultSetSize) {
      newItemsInt = maximumSolrSearchResultSetSize;
    }
    // Note method "searchServiceFactory.getSearchResultSet(request, query)"
    // ultimately sets the upper limit to defaultMaxResults (which is set to 100 in 
    // SearchServiceFactoryImpl)

    // increase the maximum items returned by solr
    query.getOptions().put(PARAMS_ITEMS_PER_PAGE, Integer.toString(newItemsInt));

    // do the query
    SolrSearchResultSet rs = searchServiceFactory.getSearchResultSet(request, query);

    // reduce the number of items to be returned by solr to its original value
    query.getOptions().put(PARAMS_ITEMS_PER_PAGE, originalItems);

    // get all the results back from solr, and stuff into a List.
    Iterator<Result> iterator = rs.getResultSetIterator();
    List<Result> solrResults = new ArrayList<Result>();

    while (iterator.hasNext()) {
      Result result = iterator.next();
      solrResults.add(result);
    }

    // shuffle the results List
    Collections.shuffle(solrResults);

    // get the number of items originally requested (ie originalItemsInt) 
    // from the shuffled list.  
    List<Result> randomSolrResults = new ArrayList<Result>();
    for(Result result : solrResults) {
      randomSolrResults.add(result);
      if (randomSolrResults.size() >= originalItemsInt ) {
        break;
      }
    }

    // create new SolrSearchResultSet object, to be returned by this method.
    SolrSearchResultSet randomSolrResultSet = new RandomContentSolrSearchResultSetImpl(randomSolrResults);

    return randomSolrResultSet;
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
