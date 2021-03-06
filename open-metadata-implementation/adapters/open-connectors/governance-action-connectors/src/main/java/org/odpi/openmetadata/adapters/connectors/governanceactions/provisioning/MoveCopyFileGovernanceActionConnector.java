/* SPDX-License-Identifier: Apache 2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.openmetadata.adapters.connectors.governanceactions.provisioning;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.odpi.openmetadata.adapters.connectors.governanceactions.ffdc.GovernanceActionConnectorsAuditCode;
import org.odpi.openmetadata.frameworks.auditlog.AuditLog;
import org.odpi.openmetadata.frameworks.connectors.ffdc.*;
import org.odpi.openmetadata.frameworks.governanceaction.ProvisioningGovernanceActionService;
import org.odpi.openmetadata.frameworks.governanceaction.properties.ActionTargetElement;
import org.odpi.openmetadata.frameworks.governanceaction.properties.CompletionStatus;
import org.odpi.openmetadata.frameworks.governanceaction.properties.ElementStatus;
import org.odpi.openmetadata.frameworks.governanceaction.properties.OpenMetadataElement;
import org.odpi.openmetadata.frameworks.governanceaction.search.ElementProperties;
import org.odpi.openmetadata.frameworks.governanceaction.search.PropertyHelper;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

/**
 * MoveCopyFileGovernanceActionConnector moves or copies files from one location to another and optionally creates lineage between them.
 */
class MoveCopyFileGovernanceActionConnector extends ProvisioningGovernanceActionService
{
    /*
     * This map remembers the index of the last file that was created in a destination folder.
     */
    private static volatile Map<String, Integer> fileIndexMap = new HashMap<>();

    private PropertyHelper propertyHelper = new PropertyHelper();


    /**
     * Generate a destination file name based on the input.
     *
     * @param previousDestinationFileName the file name tried on a previous iteration of the the loop
     * @param destinationFolderName folder name where the file is to be copied to
     * @param sourceFile File object pointing to the source file
     * @param fileNamePattern pattern to generate the destination filename (or null to use the source file name)
     * @return next file name to try - or null if no more
     */
    private static synchronized String getDestinationFileName(String previousDestinationFileName,
                                                              String destinationFolderName,
                                                              File   sourceFile,
                                                              String fileNamePattern)
    {
         int fileIndex;

         if (fileIndexMap.get(destinationFolderName) != null)
         {
             fileIndex = fileIndexMap.get(destinationFolderName) + 1;
         }
         else
         {
             fileIndex = 0;
         }

         String nextDestinationFileName;

         if (fileNamePattern != null)
         {
             nextDestinationFileName = MessageFormat.format(fileNamePattern, sourceFile.getName(), fileIndex);
         }
         else
         {
             nextDestinationFileName = sourceFile.getName();
         }

         if ((previousDestinationFileName != null) && (previousDestinationFileName.equals(nextDestinationFileName)))
         {
             /*
              * The file name is no longer changing as the index increases so return null to show that there
              * are no more options.
              */
             return null;
         }
         else
         {
             /*
              * A new file name has been created so return it to try.
              */
             fileIndexMap.put(destinationFolderName, fileIndex);
             return FilenameUtils.concat(destinationFolderName, nextDestinationFileName);
         }
    }



    /**
     * Perform the file provisioning.
     *
     * @param governanceServiceName name of requesting service
     * @param destinationFolderName name of the folder where the file is to be provisioned into
     * @param sourceFilePathName full path name of the source file
     * @param fileNamePattern patten for generating the name of the destination file
     * @param copyFile is this a copy of a move?
     * @param auditLog logging destination
     * @return name of new file
     */
    private static synchronized String provisionFile(String   governanceServiceName,
                                                     String   destinationFolderName,
                                                     String   sourceFilePathName,
                                                     String   fileNamePattern,
                                                     boolean  copyFile,
                                                     AuditLog auditLog) throws IOException
    {
        final String methodName = "provisionFile";

        File   sourceFile          = new File(sourceFilePathName);
        File   destinationFolder   = new File(destinationFolderName);

        String destinationFileName = getDestinationFileName(null, destinationFolderName, sourceFile, fileNamePattern);

        while (destinationFileName != null)
        {
            File destinationFile = new File(destinationFileName);
            if (! FileUtils.directoryContains(destinationFolder, destinationFile))
            {
                if (copyFile)
                {
                    if (auditLog != null)
                    {
                        auditLog.logMessage(methodName,
                                            GovernanceActionConnectorsAuditCode.COPY_FILE.getMessageDefinition(governanceServiceName,
                                                                                                               sourceFilePathName,
                                                                                                               destinationFileName));
                    }

                    FileUtils.copyFile(sourceFile, destinationFile, true);
                }
                else
                {
                    if (auditLog != null)
                    {
                        auditLog.logMessage(methodName,
                                            GovernanceActionConnectorsAuditCode.MOVE_FILE.getMessageDefinition(governanceServiceName,
                                                                                                               sourceFilePathName,
                                                                                                               destinationFileName));
                    }

                    FileUtils.moveFile(sourceFile, destinationFile);
                }

                return destinationFileName;
            }

            destinationFileName = getDestinationFileName(destinationFileName, destinationFolderName, sourceFile, fileNamePattern);
        }

        /*
         * No suitable file name can be found
         */
        if (auditLog != null)
        {
            auditLog.logMessage(methodName,
                                GovernanceActionConnectorsAuditCode.PROVISIONING_FAILURE.getMessageDefinition(governanceServiceName,
                                                                                                              sourceFilePathName,
                                                                                                              destinationFolderName,
                                                                                                              fileNamePattern));
        }

        return null;
    }


    /**
     * Indicates that the governance action service is completely configured and can begin processing.
     *
     * This is a standard method from the Open Connector Framework (OCF) so
     * be sure to call super.start() at the start of your overriding version.
     *
     * @throws ConnectorCheckedException there is a problem within the governance action service.
     */
    @Override
    public void start() throws ConnectorCheckedException
    {
        final String methodName = "start";

        super.start();

        List<String>     outputGuards = new ArrayList<>();
        CompletionStatus completionStatus;

        String  lineageProcessName = this.getClass().getName();
        String  destinationFileNamePattern = "{0}";
        String  sourceFileName = null;
        String  sourceFileGUID = null;
        String  destinationFolderName = null;
        boolean copyFile = true;
        boolean createLineage = true;

        Map<String, Object> configurationProperties = connectionProperties.getConfigurationProperties();

        /*
         * Retrieve the configuration properties from the Connection object.  These properties affect all requests to this connector.
         */
        if (configurationProperties != null)
        {
            Object provisionExternalFiles = configurationProperties.get(MoveCopyFileGovernanceActionProvider.PROVISION_UNCATALOGUED_FILES_CONFIGURATION_PROPERTY);

            /*
             * The name of the source file many be passed explicitly in the request parameters.
             */
            if (provisionExternalFiles != null)
            {
                if (governanceContext.getRequestParameters() != null)
                {
                    sourceFileName = governanceContext.getRequestParameters().get(MoveCopyFileGovernanceActionProvider.SOURCE_FILE_PARAMETER);
                    destinationFolderName = governanceContext.getRequestParameters().get(MoveCopyFileGovernanceActionProvider.DESTINATION_FOLDER_PARAMETER);
                }
            }

            Object noLineageOption = configurationProperties.get(MoveCopyFileGovernanceActionProvider.NO_LINEAGE_CONFIGURATION_PROPERTY);

            if (noLineageOption != null)
            {
                createLineage = false;
            }

            Object processNameOption = configurationProperties.get(MoveCopyFileGovernanceActionProvider.LINEAGE_PROCESS_NAME_CONFIGURATION_PROPERTY);

            if (processNameOption != null)
            {
                lineageProcessName = processNameOption.toString();
            }

            Object fileNamePatternOption = configurationProperties.get(MoveCopyFileGovernanceActionProvider.TARGET_FILE_NAME_PATTERN_CONFIGURATION_PROPERTY);

            if (processNameOption != null)
            {
                destinationFileNamePattern = fileNamePatternOption.toString();
            }
        }

        if (MoveCopyFileGovernanceActionProvider.MOVE_REQUEST_TYPE.equals(governanceContext.getRequestType()))
        {
            copyFile = false;
        }

        if (governanceContext.getActionTargetElements() != null)
        {
            for (ActionTargetElement actionTargetElement : governanceContext.getActionTargetElements())
            {
                if (actionTargetElement != null)
                {
                    if (MoveCopyFileGovernanceActionProvider.SOURCE_FILE_ACTION_TARGET.equals(actionTargetElement.getActionTargetName()))
                    {
                        OpenMetadataElement sourceMetadataElement = actionTargetElement.getTargetElement();

                        if (sourceMetadataElement != null)
                        {
                            sourceFileName = this.getPathName(sourceMetadataElement);
                            sourceFileGUID = sourceMetadataElement.getElementGUID();
                        }
                    }
                    else if (MoveCopyFileGovernanceActionProvider.DESTINATION_FOLDER_ACTION_TARGET.equals(actionTargetElement.getActionTargetName()))
                    {
                        OpenMetadataElement destinationMetadataElement = actionTargetElement.getTargetElement();

                        if (destinationMetadataElement != null)
                        {
                            destinationFolderName = this.getPathName(destinationMetadataElement);
                        }
                    }
                }
            }
        }

        try
        {
            String destinationFileName = provisionFile(governanceServiceName,
                                                       destinationFolderName,
                                                       sourceFileName,
                                                       destinationFileNamePattern,
                                                       copyFile,
                                                       auditLog);

            if (destinationFileName != null)
            {
                if (createLineage)
                {
                    createLineage(sourceFileGUID, destinationFileName, lineageProcessName);
                }

                outputGuards.add(MoveCopyFileGovernanceActionProvider.PROVISIONING_COMPLETE_GUARD);
                completionStatus = CompletionStatus.ACTIONED;
            }
            else
            {
                outputGuards.add(MoveCopyFileGovernanceActionProvider.PROVISIONING_FAILED_GUARD);
                completionStatus = CompletionStatus.FAILED;
            }
        }
        catch (Exception  error)
        {
            if (auditLog != null)
            {
                auditLog.logMessage(methodName,
                                    GovernanceActionConnectorsAuditCode.PROVISIONING_EXCEPTION.getMessageDefinition(governanceServiceName,
                                                                                                                    error.getClass().getName(),
                                                                                                                    sourceFileName,
                                                                                                                    destinationFolderName,
                                                                                                                    destinationFileNamePattern,
                                                                                                                    error.getMessage()));
            }

            outputGuards.add(MoveCopyFileGovernanceActionProvider.PROVISIONING_FAILED_GUARD);
            completionStatus = CompletionStatus.FAILED;
        }

        try
        {
            governanceContext.recordCompletionStatus(completionStatus, outputGuards, null);
        }
        catch (OCFCheckedExceptionBase error)
        {
            throw new ConnectorCheckedException(error.getReportedErrorMessage(), error);
        }
    }


    private String getPathName(OpenMetadataElement metadataElement)
    {
        final String methodName = "getPathName";
        // todo navigate to Endpoint and only use qualifiedName if Endpoint not available

        ElementProperties properties = metadataElement.getElementProperties();

        return propertyHelper.getStringProperty(governanceServiceName,
                                                "qualifiedName",
                                                properties,
                                                methodName);
    }


    /**
     * Create the lineage mapping for the provisioning process.
     *
     * @param sourceFileGUID open metadata unique identifier for the source file asset
     * @param destinationFilePathName name of the file that was created
     * @param lineageProcessName name of this provisioning process
     *
     * @throws InvalidParameterException one of the parameters passed to open metadata is invalid (probably a bug in this code)
     * @throws UserNotAuthorizedException the userId for the connector does not have the authority it needs
     * @throws PropertyServerException there is a problem with the metadata server(s)
     */
    private void createLineage(String  sourceFileGUID,
                               String  destinationFilePathName,
                               String  lineageProcessName) throws InvalidParameterException,
                                                                  UserNotAuthorizedException,
                                                                  PropertyServerException
    {
        final String processTypeName = "TransientEmbeddedProcess";

        String fileName = FilenameUtils.getName(destinationFilePathName);
        String fileExtension = FilenameUtils.getExtension(destinationFilePathName);
        String assetTypeName = "DataFile";

        switch (fileExtension)
        {
            case "csv":
                assetTypeName = "CSVFile";
                break;

            case "json":
                assetTypeName = "JSONFile";
                break;

            case "avro":
                assetTypeName = "AvroFileName";
                break;

            case "pdf":
            case "doc":
            case "docx":
            case "ppt":
            case "pptx":
            case "xls":
            case "xlsx":
            case "md":
                assetTypeName = "Document";
                break;

            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "mp3":
            case "mp4":
                assetTypeName = "MediaFile";
                break;
        }

        String newFileGUID = governanceContext.createAsset(assetTypeName,
                                                           destinationFilePathName,
                                                           fileName,
                                                           null,
                                                           null);

        String processGUID = governanceContext.createProcess(processTypeName,
                                                             ElementStatus.ACTIVE,
                                                             lineageProcessName + ":" + UUID.randomUUID().toString(),
                                                             lineageProcessName,
                                                             null,
                                                             null);

        governanceContext.createLineageMapping(sourceFileGUID, processGUID);
        governanceContext.createLineageMapping(processGUID, newFileGUID);
    }
}
