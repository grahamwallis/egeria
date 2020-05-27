import React, { useContext, useState } from "react";

/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */

import PropTypes                       from "prop-types";

import { InstancesContext }            from "../../contexts/InstancesContext";

import { RepoServerContext }           from "../../contexts/RepoServerContext";

import FilterManager                   from "./FilterManager";

import SearchResultHandler             from "./SearchResultHandler";

import "./instance-retriever.scss"


export default function InstanceSearch(props) {


  const repositoryServerContext = useContext(RepoServerContext);

  const instancesContext        = useContext(InstancesContext);


  const [status, setStatus] = useState("idle");  // { "idle", "pending", "cancelled:", "complete" }

  
  // Search settings may be updated by callbacks from the FilterManager - note that when the user selects a filter type
  // this MAY alter the searchCategory to the category of the selected type. 
  // If the user has selected an entity type - the searchCategory will be set to Entity.
  // If the user has selected an relationship type - the searchCategory will be set to Relationship.
  // If the user selects a classification type it is added to the map, no resets are performed.
  
  const [searchCategory,        setSearchCategory]          = useState("Entity");    
  const [searchText,            setSearchText]              = useState("");   
  const [searchType,            setSearchType]              = useState("");    // name of selected type 
  const [searchClassifications, setSearchClassifications]   = useState({});    // map of selected class'ns
  const [searchResults,         setSearchResults]           = useState([]); 

  
  
  const filterTypeSelected = (category, typeName) => {      
    setSearchType(typeName);
    setSearchCategory(category); 
  };


  /*
   * Add/remove this classification to/from the map...
   */
  const filterClassificationChanged = (typeName, checked) => {    
    let currentClassifications = Object.assign(searchClassifications);
    if (checked) {
      // The value is irrelevant, it is presence of the key that is significant
      currentClassifications[typeName] = true; 
    }
    else {
      delete currentClassifications[typeName];
    }   
    setSearchClassifications(currentClassifications);
  };


  

  /*
   * handler for radio buttons to select category for insatnce search
   */
  const changeSearchCategory = (evt) => {
    if (searchCategory === "Entity") {
      setSearchCategory("Relationship");        
    }      
    else {
      setSearchCategory("Entity");        
    }
  } 

  /*
   * Handler for change to search text field
   */
  const updatedSearchText = (evt) => {    
    setSearchText(evt.target.value);    
  }

  /*
   * Handler for search button - depending on search category, initiate a search
   * either for entities or relationships
   */
  const searchForInstances = () => {    
    setStatus("pending");

    if (searchCategory === "Entity") {
      findEntities();
    }
    else { // Relationships...
      findRelationships();
    }
    
  };
  

  /*
   * Function to find entities in repository using searchText
   */
  const findEntities = () => {    
    
    //let searchSpec = {};
    //searchSpec.searchText = searchText;
    //searchSpec.serverName = repositoryServerContext.repositoryServerName;
    //searchSpec.searchCategory = "Entity";
    let typeName = searchType || null;
    //searchSpec.typeName = typeName;
    let classificationList = Object.keys(searchClassifications);
    //searchSpec.classifications = classificationList;   
    //setSearchSpecification(searchSpec);    
    console.log("InstanceSearch: dispatch entity search");   
    repositoryServerContext.repositoryPOST("instances/entities/by-property-value", 
      { searchText           : searchText, 
        typeName             : typeName,
        classificationNames  : classificationList
       }, _findEntities); 
  };

  /*
   * Handle completion of entity search
   */
  const _findEntities = (json) => {    
    console.log("_findEntities status is "+status);

    if (status !== "cancelled" && status !== "complete") {
      if (json !== null) {
        let entityDigests = json.entities;           
        let instances = [];
        for (var guid in entityDigests) {
          var entityDigest = entityDigests[guid];
          entityDigest.checked = false;
          instances.push(entityDigest);
        }     
      
        console.log("I-R: searchCategory "+searchCategory);
        console.log("I-R: searchText "+searchText);
        console.log("I-R: searchType "+searchType);
        console.log("I-R: searchClassifications: ");
        let classificationList = Object.keys(searchClassifications);
        classificationList.forEach(c => {
          console.log("classification: "+c);        
        });
      
        console.log("I-R: found instances, count "+instances.length);
      
        // Store the results locally to I-R
        setSearchResults(instances);
      }
      else {
        console.log("_findEntities error detected should display ");
      }

      console.log("_findEntities set status to complete");
      setStatus("complete");
    }

    else {
      setStatus("idle");
    }
   
  };

 

  /*
   * Function to find relationships in repository using searchText
   */
  const findRelationships = () => {   
    
    //let searchSpec = {};
    //searchSpec.searchText = searchText;
    //searchSpec.serverName = repositoryServerContext.repositoryServerName;
    //searchSpec.searchCategory = "Relationship";
    let typeName = searchType || null;
    //searchSpec.typeName = typeName;
    //setSearchSpecification(searchSpec);

    /* 
     * Add the typeName and classifications list to the body here....
     */   
    repositoryServerContext.repositoryPOST("instances/relationships/by-property-value", 
      { searchText : searchText, 
        typeName   : typeName 
      }, _findRelationships); 
  };

  /*
   * Handle completion of relationship search
   */
  const _findRelationships = (json) => {   
   
    if (status !== "cancelled" && status !== "complete") {
      if (json !== null) {
        let relationshipDigests = json.relationships;      
        //console.log("_findRelationships: got back ..."+relationshipDigests);
        let instances = [];
        for (var guid in relationshipDigests) {
          var relationshipDigest = relationshipDigests[guid];
          relationshipDigest.checked = false;
          instances.push(relationshipDigest);
        }
      
        console.log("I-R: found instances, count "+instances.length);

       // Store the results locally to I-R
       setSearchResults(instances);
      }
      else {
        console.log("_findEntities error detected should display ");
      }

      console.log("_findEntities set status to complete");
      setStatus("complete");
    }

    else {
      setStatus("idle");
    }
 
  
  };
  

  /*
   * Handler for submit of search results modal
   */
  const submitSearchModal = (evt) => {    

    let selectedInstances = searchResults.filter(function(instance) {
      return instance['checked'];
    });  

    /*
     * At this point you could process the list of selectedInstances and ask instancesContext
     * to perform a loadEntity or loadRelationship - which would get them all and make the last
     * one loaded the focus instances. Search results don't work like that though - even if there
     * is a single instance selected. When a search completes, Rex will get all the selected 
     * search results (one or multiple) and will not make any of them the focus. They are digests 
     * only at this stage, and are added to the diagram and maps. If the user subsequently clicks
     * on one of them it will then be retrieved and will become the focus.
     */

    if (selectedInstances !== undefined && 
       selectedInstances !== null       && 
       selectedInstances.length  > 0     ) {

      /*
       *  If there is one entity (or relationship) it becomes the selected instance - which means
       *  we have to retrieve the entity detail for the details panel.
       *  If there are multiple instances in the search result then Rex does not know which to select
       *  and does not select any - so rootInstance is not set.
       */
  
      const numInstancesFound = selectedInstances.length;

      let searchUnique         = false;
      let searchUniqueCategory = "";
      let searchUniqueGUID     = "";
      let searchUniqueInstance = null;
      if (numInstancesFound === 1) {
        searchUnique = true;
        if (searchCategory === "Entity") {
          searchUniqueCategory   = "Entity";
          searchUniqueInstance   = selectedInstances[0];
          searchUniqueGUID       = searchUniqueInstance.entityGUID;
        }
        else {  // searchCategory is "Relationship"
          searchUniqueCategory   = "Relationship";
          searchUniqueInstance   = selectedInstances[0];
          searchUniqueGUID       = searchUniqueInstance.relationshipGUID;
        }
      }

      /*
       *  Process the list of digests. Any that are not already known
       *  are added to a traversal that will be added as a new gen.....
       */
      let rexTraversal             = {};
      rexTraversal.entities        = {};
      rexTraversal.relationships   = {};

      /*
       *  Set the traversal operation to show how this result was generated -
       *  provides informative summary in history
       */
      if (searchCategory === "Entity") {
        rexTraversal.operation = "entitySearch";
      }
      else {
        rexTraversal.operation = "relationshipSearch";
      }
      rexTraversal.serverName = repositoryServerContext.repositoryServerName;      
      rexTraversal.searchText = searchText;
      

      /*
       *  Do not select any of the search results.
       *  Populate the gen with the digests. No focus change request needed.
       */

      selectedInstances.forEach(instance => {
    
        /*
         *  Do not select any of the search results.
         *  Check which (if any) are not already known and populate a gen with their digests.
         *  No focus change request needed.
         */
      
        if (searchCategory === "Entity") {
          const entityGUID = instance.entityGUID;
          rexTraversal.entities[entityGUID] = instance;                  
        }
        else {
          var relationshipGUID = instance.relationshipGUID;
          rexTraversal.relationships[relationshipGUID] = instance;        
        }
    
      });

     
      // Pass the traversal to the InstancesContext which will add a gen for it to extend the graph
      instancesContext.processRetrievedTraversal(rexTraversal);

   
      // If the search resulted in a single instance being selected, optimise
      // the flow by proactively requesting that it is loaded and becomes the focus instance.
      if (searchUnique) {
        if (searchUniqueCategory === "Entity") {
          //instancesContext.setFocusEntity(searchUniqueGUID);
          instancesContext.loadEntity(searchUniqueGUID);
        }
        else {
          //instancesContext.setFocusRelationship(searchUniqueGUID);
          instancesContext.loadRelationship(searchUniqueGUID);
        }
      }
 
    }

    // Clear the search results
    setSearchResults( [] );    
    setStatus("idle");
    
  };

  
  /*
   * Handler for cancel of search results dialog.
   * The results are hidden but not cleared - so if needed can be re-displayed and the results
   * of the previous search will still be available.
   */
  const cancelSearchModal = (evt) => {
    console.log("InstanceSearch: cancel callback invoked status is "+status);
    if (status === "cancelled") {
      console.log("InstanceSearch: cancel callback setting status to idle");
      setStatus("idle");
    }
    else if (status === "complete") {
      console.log("InstanceSearch: cancel callback setting status to idle");
      setStatus("idle");
    }
    else {
      console.log("InstanceSearch: cancel callback setting status to cancelled");
      setStatus("cancelled");
    }
    
  };




  /*
   * Handler for updating search results when user checks or unchecks an instance in the search results.
   * Toggles the checked state of an individual instance.
   */
  const selectCallback = (guid) => {
    let list = null;
    if (searchCategory === "Entity") {
      list = searchResults.map((item) => {
        if (item.entityGUID === guid) {
          const prevChecked = item.checked;
          return Object.assign(item, { checked : !prevChecked });            
        }
        else {
          return item;
        }
      });   
    } 
    else { // Relationships...
      list = searchResults.map((item) => {
        if (item.relationshipGUID === guid) {
          const prevChecked = item.checked;
          return Object.assign(item, { checked : !prevChecked });            
        }
        else {
          return item;
        }
      });   
    }
    setSearchResults( list );    
  }


  /*
   * Set or clear all searched instances to checked or unchecked...
   */
  const setAllCallback = (checked) => {   
    let updates = [];      
    searchResults.forEach((instance) => {
      let newInst = Object.assign(instance, {checked : checked});                                     
      updates.push( newInst );                
    });   
    setSearchResults(updates);
  }

  

  return (
    
    <div className={props.className}>
    

      <div className="retrieval-controls">          
        <p>
        Search for instances
        </p>        
        <label htmlFor="category">Category: </label>
        <input type="radio" 
               id="searchCatEntity" 
               name="searchCategory" 
               value="Entity" 
               checked={searchCategory === "Entity"}
               onChange={changeSearchCategory}/>
        <label htmlFor="searchCatEntity">Entity</label>
        
        <input type="radio" 
               id="searchCatRelationship" 
               name="searchCategory" 
               value="Relationship" 
               checked={searchCategory === "Relationship"}
               onChange={changeSearchCategory} />
        <label htmlFor="searchCatRelationship">Relationship</label>

        <br />

        <label htmlFor="searchTextField">Search text : </label>
        <input name="searchTextField"
               value = { searchText }      
               onChange = { updatedSearchText } >
        </input>        
       
      

        <FilterManager searchCategory={searchCategory} typeSelected={filterTypeSelected} clsChanged={filterClassificationChanged} />
       
        <br />
    

        <button className="top-control-button" onClick = { searchForInstances } >
          Search for instances
        </button>
      </div>

        <SearchResultHandler status                = { status }
                             searchCategory        = { searchCategory }  
                             searchType            = { searchType }  
                             searchText            = { searchText }  
                             searchClassifications = { Object.keys(searchClassifications) }  
                             serverName            = { repositoryServerContext.repositoryServerName }
                             results               = { searchResults } 
                             selectCallback        = { selectCallback }
                             setAllCallback        = { setAllCallback }
                             onCancel              = { cancelSearchModal }
                             onSubmit              = { submitSearchModal } />
        
    </div>      
       
  
  );

}

InstanceSearch.propTypes = { 
  className  : PropTypes.string
}
