/* SPDX-License-Identifier: Apache 2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.openmetadata.frameworks.governanceaction;

import org.odpi.openmetadata.frameworks.connectors.ffdc.InvalidParameterException;
import org.odpi.openmetadata.frameworks.connectors.ffdc.PropertyServerException;
import org.odpi.openmetadata.frameworks.connectors.ffdc.UserNotAuthorizedException;
import org.odpi.openmetadata.frameworks.governanceaction.properties.ElementStatus;
import org.odpi.openmetadata.frameworks.governanceaction.properties.OpenMetadataElement;
import org.odpi.openmetadata.frameworks.governanceaction.properties.RelatedMetadataElement;
import org.odpi.openmetadata.frameworks.governanceaction.properties.RelatedMetadataElements;
import org.odpi.openmetadata.frameworks.governanceaction.search.SearchClassifications;
import org.odpi.openmetadata.frameworks.governanceaction.search.SearchProperties;
import org.odpi.openmetadata.frameworks.governanceaction.search.SequencingOrder;

import java.util.Date;
import java.util.List;

/**
 * OpenMetadataStore provides access to metadata elements stored in the metadata repositories.  It is implemented by the
 * abstract class OpenMetadataClient and used by all of the governance action services to retrieve metadata.
 *
 * The concrete class for OpenMetadataClient is implemented by a metadata repository provider. In Egeria, this class is
 * implemented in the Governance Engine OMAS client.
 */
public interface OpenMetadataStore
{
    /**
     * Retrieve the metadata element using its unique identifier.
     *
     * @param elementGUID unique identifier for the metadata element
     *
     * @return metadata element properties
     * @throws InvalidParameterException the unique identifier is null or not known.
     * @throws UserNotAuthorizedException the governance action service is not able to access the element
     * @throws PropertyServerException there is a problem accessing the metadata store
     */
    OpenMetadataElement getMetadataElementByGUID(String elementGUID) throws InvalidParameterException,
                                                                            UserNotAuthorizedException,
                                                                            PropertyServerException;


    /**
     * Retrieve the metadata elements that contain the requested string.
     *
     * @param searchString name to retrieve
     * @param startFrom paging start point
     * @param pageSize maximum results that can be returned
     *
     * @return list of matching metadata elements (or null if no elements match the name)
     * @throws InvalidParameterException the qualified name is null
     * @throws UserNotAuthorizedException the governance action service is not able to access the element
     * @throws PropertyServerException there is a problem accessing the metadata store
     */
    List<OpenMetadataElement> findMetadataElementsWithString(String searchString,
                                                             int    startFrom,
                                                             int    pageSize) throws InvalidParameterException,
                                                                                         UserNotAuthorizedException,
                                                                                         PropertyServerException;


    /**
     * Retrieve the metadata elements connected to the supplied element.
     *
     * @param elementGUID unique identifier for the starting metadata element
     * @param relationshipTypeName type name of relationships to follow (or null for all)
     * @param startFrom paging start point
     * @param pageSize maximum results that can be returned
     *
     * @return list of related elements
     *
     * @throws InvalidParameterException the unique identifier is null or not known; the relationship type is invalid
     * @throws UserNotAuthorizedException the governance action service is not able to access the elements
     * @throws PropertyServerException there is a problem accessing the metadata store
     */
    List<RelatedMetadataElement> getRelatedMetadataElements(String elementGUID,
                                                            String relationshipTypeName,
                                                            int    startFrom,
                                                            int    pageSize) throws InvalidParameterException,
                                                                                    UserNotAuthorizedException,
                                                                                    PropertyServerException;


    /**
     * Return a list of metadata elements that match the supplied criteria.  The results can be returned over many pages.
     *
     * @param metadataElementTypeName type of interest (null means any element type)
     * @param metadataElementSubtypeName optional list of the subtypes of the metadataElementTypeName to
     *                           include in the search results. Null means all subtypes.
     * @param searchProperties Optional list of entity property conditions to match.
     * @param limitResultsByStatus By default, entities in all statuses (other than DELETE) are returned.  However, it is possible
     *                             to specify a list of statuses (eg ACTIVE) to restrict the results to.  Null means all status values.
     * @param matchClassifications Optional list of classifications to match.
     * @param sequencingProperty String name of the property that is to be used to sequence the results.
     *                           Null means do not sequence on a property name (see SequencingOrder).
     * @param sequencingOrder Enum defining how the results should be ordered.
     * @param startFrom paging start point
     * @param pageSize maximum results that can be returned
     *
     * @return a list of elements matching the supplied criteria; null means no matching elements in the metadata store.
     * @throws InvalidParameterException one of the search parameters are is invalid
     * @throws UserNotAuthorizedException the governance action service is not able to access the elements
     * @throws PropertyServerException there is a problem accessing the metadata store
     */
    List<OpenMetadataElement> findMetadataElements(String                metadataElementTypeName,
                                                   List<String>          metadataElementSubtypeName,
                                                   SearchProperties      searchProperties,
                                                   List<ElementStatus>   limitResultsByStatus,
                                                   SearchClassifications matchClassifications,
                                                   String                sequencingProperty,
                                                   SequencingOrder       sequencingOrder,
                                                   int                   startFrom,
                                                   int                   pageSize) throws InvalidParameterException,
                                                                                          UserNotAuthorizedException,
                                                                                          PropertyServerException;


    /**
     * Return a list of relationships that match the requested conditions.  The results can be received as a series of pages.
     *
     * @param relationshipTypeName relationship's type.  Null means all types
     *                             (but may be slow so not recommended).
     * @param matchProperties Optional list of relationship property conditions to match.
     * @param asOfTime Requests a historical query of the relationships for the entity.  Null means return the
     *                 present values.
     * @param sequencingProperty String name of the property that is to be used to sequence the results.
     *                           Null means do not sequence on a property name (see SequencingOrder).
     * @param sequencingOrder Enum defining how the results should be ordered.
     * @param startFrom paging start point
     * @param pageSize maximum results that can be returned
     *
     * @return a list of relationships.  Null means no matching relationships.
     * @throws InvalidParameterException one of the search parameters are is invalid
     * @throws UserNotAuthorizedException the governance action service is not able to access the elements
     * @throws PropertyServerException there is a problem accessing the metadata store
     */
    List<RelatedMetadataElements> findRelationshipsBetweenMetadataElements(String           relationshipTypeName,
                                                                           SearchProperties matchProperties,
                                                                           Date             asOfTime,
                                                                           String           sequencingProperty,
                                                                           SequencingOrder  sequencingOrder,
                                                                           int              startFrom,
                                                                           int              pageSize) throws InvalidParameterException,
                                                                                                             UserNotAuthorizedException,
                                                                                                             PropertyServerException;
}