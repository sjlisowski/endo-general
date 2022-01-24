package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.HttpParam;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.custom.udc.VAPI;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.job.*;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

/*
  This Job:
     - executes "Expiration Pending" workflows for Materials that are approaching their Expiration Date
     - cancels "Expiration Pending" workflow tasks for active workflows where the Material's expiration date
       is before 15 days in the future.
  See legacy document workflow "Expiration Pending" in lifecycle "Job Processing".
*/

@JobInfo(adminConfigurable = true)
public class ExpirationUpcomingTasks implements Job {

    // Expiration date threshold values for selecting materials for which to start "Expiration Pending" workflows...
    private static final long PENDING_RANGE_HIGH = 60;
    private static final long PENDING_RANGE_LOW = 55;
    // the number of days before a Job's Expiration Date that the pending expiration task should be cancelled...
    private static final long TASK_KILL_THRESHOLD_DAYS = 15;
    // for finding selecting materials whose expiration date is before the current date plus this number of days...
    private static final long PENDING_EXPIRATION_THRESHOLD_DAYS = 15;

    private static final String ACTION = "action";
    private static final String ACTION_START = "start";
    private static final String ACTION_CANCEL = "cancel";

    public JobInputSupplier init(JobInitContext jobInitContext) {
      List<JobItem> jobItems = VaultCollections.newList();
      findStartItems(jobInitContext, jobItems);
      findCancelItems(jobInitContext, jobItems);
      return jobInitContext.newJobInput(jobItems);
    }

    public void process(JobProcessContext jobProcessContext) {

      JobLogger logger = jobProcessContext.getJobLogger();

      List<JobItem> jobItems = jobProcessContext.getCurrentTask().getItems();

      int errorsEncountered = 0;

      for (JobItem jobItem : jobItems) {
        String action = jobItem.getValue(ACTION, JobValueType.STRING);
        if (action.equals(ACTION_START)) {
          errorsEncountered += startExpirationPendingWorkflow(jobItem, logger);
        }
        else if (action.equals(ACTION_CANCEL)) {
          errorsEncountered += cancelExpirationPendingTask(jobItem, logger);
        }
        else {
          errorsEncountered++;
          logger.log("Job item has no associated action.");
        }
      }

      TaskOutput taskOutput = jobProcessContext.getCurrentTask().getTaskOutput();

      if (errorsEncountered > 0) {
        taskOutput.setState(TaskState.ERRORS_ENCOUNTERED);
        taskOutput.setValue("firstError", "At least one error occurred.");
      } else {
        taskOutput.setState(TaskState.SUCCESS);
        logger.log("Task successful");
      }
    }

    public void completeWithSuccess(JobCompletionContext jobCompletionContext) {
        JobLogger logger = jobCompletionContext.getJobLogger();
        logger.log("All tasks completed successfully");
    }

    public void completeWithError(JobCompletionContext jobCompletionContext) {
        JobResult result = jobCompletionContext.getJobResult();

        JobLogger logger = jobCompletionContext.getJobLogger();
        logger.log("completeWithError: " + result.getNumberFailedTasks() + " tasks failed out of " + result.getNumberTasks());

        List<JobTask> tasks = jobCompletionContext.getTasks();
        for (JobTask task : tasks) {
            TaskOutput taskOutput = task.getTaskOutput();
            if (TaskState.ERRORS_ENCOUNTERED.equals(taskOutput.getState())) {
                logger.log(task.getTaskId() + " failed with error message " + taskOutput.getValue("firstError", JobValueType.STRING));
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // Find materials for which to start new "Expiration Pending" workflows.
    ////////////////////////////////////////////////////////////////////////
    private void findStartItems(JobInitContext jobInitContext, List<JobItem> jobItems) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      JobLogger logger = jobInitContext.getJobLogger();

      logger.log("Looking for materials pending expiration to start workflows...");

      LocalDate dateNow = LocalDate.now();
      LocalDate dateFrom = dateNow.plusDays(PENDING_RANGE_LOW);
      LocalDate dateTo = dateNow.plusDays(PENDING_RANGE_HIGH);

      StringBuilder sbQuery = new StringBuilder()
        .append("select version_id, document_number__v, expiration_date__c")
        .append("  from documents")
        .append(" where toName(type__v) contains ('jobs__c', 'nprc__c', 'par__c', 'endoaesthetics__c')")
        .append("   and status__v = steadyState()")
        .append("   and pending_expiration_task_sent__c != true")
        .append("   and expiration_date__c between ")
        .append("'").append(dateFrom.toString()).append("'")
        .append(" and ")
        .append("'").append(dateTo.toString()).append("'");

      logger.log("Executing VQL Query to find candidate materials: " + sbQuery.toString());

      QueryResponse queryResponse = queryService.query(sbQuery.toString());

      logger.log("Found " + queryResponse.getResultCount() + " jobs in range.");

      Iterator<QueryResult> iter = queryResponse.streamResults().iterator();

      while (iter.hasNext()) {
        QueryResult qr = iter.next();
        String docNbr = qr.getValue("document_number__v", ValueType.STRING);
        String docVersionId = qr.getValue("version_id", ValueType.STRING);
        LocalDate expirationDate = qr.getValue("expiration_date__c", ValueType.DATE);
        logger.log("Found " + docNbr + " v" + docVersionId + " with expiration date " + expirationDate.toString());
        JobItem jobItem = jobInitContext.newJobItem();
        jobItem.setValue(ACTION, ACTION_START);
        jobItem.setValue("docNumber", docNbr);
        jobItem.setValue("docVersionId", docVersionId);
        jobItem.setValue("expirationDate", expirationDate);
        jobItems.add(jobItem);
      }

    }  // end findStartItems()

    ////////////////////////////////////////////////////////////////////////
    // Find Materials for which to cancel existing "Expiration Pending"
    // workflows.
    ////////////////////////////////////////////////////////////////////////
    private void findCancelItems(JobInitContext jobInitContext, List<JobItem> jobItems) {

      QueryResponse queryResponse;
      Iterator<QueryResult> iter;

      JobLogger logger = jobInitContext.getJobLogger();

      logger.log(
        "Looking for active Expiration Pending workflows for Materials with expiration date less than " +
          PENDING_EXPIRATION_THRESHOLD_DAYS + " in the future..."
      );

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      LocalDate expirationThresholdDate = LocalDate.now().plusDays(PENDING_EXPIRATION_THRESHOLD_DAYS);

      queryResponse = queryService.query(
        "select id from documents" +
        " where toName(lifecycle__v) = 'job_processing__c'" +
        "   and expiration_date__c < '"+expirationThresholdDate.toString()+"'"
      );
      iter = queryResponse.streamResults().iterator();

      List<String> docIds = VaultCollections.newList();

      while (iter.hasNext()) {
        QueryResult queryResult = iter.next();
        docIds.add(queryResult.getValue("id", ValueType.STRING));
      }

      // QueryService does not support queries on the workflows object, so we need to use HTTP Callout...
      VAPI vapi = new VAPI("local_connection__c");
      vapi.setJobLogger(logger);
      JsonArray data = vapi.executeQuery(
        "select workflow_document_id__v, task_id__v" +
        "  from workflows" +
        " where workflow_name__v = 'Expiration Pending' and workflow_status__v = 'Active'"
      );
      if (vapi.failed()) {
        String msg = "An error occurred executing workflows query: " +
          vapi.getErrorType() + ": " + vapi.getErrorMessage();
        logger.log(msg);
        throw new RollbackException(ErrorType.OPERATION_FAILED, msg);
      }

      for (int i=0; i<data.getSize(); i++) {
        JsonObject jsonObject = data.getValue(i, JsonValueType.OBJECT);
        String docId = jsonObject.getValue("workflow_document_id__v", JsonValueType.NUMBER).toString();
        if (docIds.contains(docId)) {
          JobItem jobItem = jobInitContext.newJobItem();
          jobItem.setValue(ACTION, ACTION_CANCEL);
          jobItem.setValue("docId", docId);
          jobItem.setValue("taskId", jsonObject.getValue("task_id__v", JsonValueType.NUMBER).toString());
          jobItems.add(jobItem);
        }
      }

    }  // end findCancelItems()

    ////////////////////////////////////////////////////////////////////////
    // Start a new "Pending Expiration" workflow.
    // Return:
    //   0 if successful
    //   1 if an error occurred
    ////////////////////////////////////////////////////////////////////////
    private int startExpirationPendingWorkflow(JobItem jobItem, JobLogger logger) {

      String docNumber = jobItem.getValue("docNumber", JobValueType.STRING);
      String docVersionId = jobItem.getValue("docVersionId", JobValueType.STRING);
      LocalDate expirationDate = jobItem.getValue("expirationDate", JobValueType.DATE);

      LocalDate taskKillDate = expirationDate.minusDays(TASK_KILL_THRESHOLD_DAYS);

      logger.log("Starting 'Expiration Pending' workflow for: " + docNumber);

      List<String> userIds = Util.getDocumentUsersInRole(docVersionId, "project_manager__c");

      List<HttpParam> workflowStartCriteria = VaultCollections.newList();

      for (String userId : userIds) {
        logger.log(docNumber + ": Found user in role Project Manager: " + userId);
        workflowStartCriteria.add(new HttpParam("user_control_multiple__c", "user:" + userId));
      }

      workflowStartCriteria.add(new HttpParam("date_control1__c", taskKillDate.toString()));

      logger.log("Executing 'Expiration Pending' workflow for " + docNumber);
      VAPI vapi = new VAPI("local_connection__c");
      vapi
        .setJobLogger(logger)
        .executeUserAction(docVersionId, "expiration_pending_autostart", workflowStartCriteria);
      if (vapi.failed()) {
        logger.log(vapi.getErrorType() + ": " + vapi.getErrorMessage());
        logger.log("Unable to execute workflow for "+docNumber+".");
        return 1;
      } else {
        logger.log("Successfully executed workflow for "+docNumber+".");
        return 0;
      }

    }  // end startExpirationPendingWorkflow()

    ////////////////////////////////////////////////////////////////////////
    // Cancel a "Pending Expiration" workflow task.
    // Return:
    //   0, if successful
    //   1, if an error occurred
    ////////////////////////////////////////////////////////////////////////
    private int cancelExpirationPendingTask(JobItem jobItem, JobLogger logger) {

      String docId = jobItem.getValue("docId", JobValueType.STRING);
      String taskId = jobItem.getValue("taskId", JobValueType.STRING);

      logger.log("Cancelling Pending Expiration task for " + docId);

      VAPI vapi = new VAPI("local_connection__c");
      vapi
        .setJobLogger(logger)
        .cancelWorkflowTasks(VaultCollections.asList(taskId));

      if (vapi.failed()) {
        logger.log("Unable to cancel workflow task for "+docId+".");
        logger.log(vapi.getErrorType() + ": " + vapi.getErrorMessage());
        return 1;
      } else {
        logger.log("Successfully canceled workflow task for "+docId+".");
        return 0;
      }

    }  // end cancelExpirationPendingTask()
  }
