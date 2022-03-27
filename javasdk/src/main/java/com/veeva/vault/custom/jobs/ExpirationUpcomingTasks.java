package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.*;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

/*
  This Job:
     - executes "Expiration Pending" workflows for Materials that are approaching their Expiration Date
     - cancels "Expiration Pending" workflow tasks for active workflows where the Material's expiration date
       is before some number of day(s) in the future.
  A record named "ExpirationPendingWorkflow" in object "VPROC Parameter Set" contains the parameters
  that determine when a workflow is started, and canceled, as well as the due date for the workflow task.
  See legacy document workflow "Expiration Pending" in lifecycle "Job Processing".
*/

@JobInfo(adminConfigurable = true)
public class ExpirationUpcomingTasks implements Job {

    // number of days  before the WORKFLOW_START_DAYS to create a range of dates to find jobs approaching expiration
    private static final long WORKFLOW_START_BUFFER_DAYS = 30;

    private static final String ACTION = "action";
    private static final String ACTION_START = "start";
    private static final String ACTION_CANCEL = "cancel";

    public JobInputSupplier init(JobInitContext jobInitContext) {
      List<JobItem> jobItems = VaultCollections.newList();
      ExpirationPendingParameters appParams = new ExpirationPendingParameters();
      findStartItems(jobInitContext, jobItems, appParams);
      findCancelItems(jobInitContext, jobItems, appParams);
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
    private void findStartItems(
      JobInitContext jobInitContext,
      List<JobItem> jobItems,
      ExpirationPendingParameters appParams)
    {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      JobLogger logger = jobInitContext.getJobLogger();

      logger.log("Looking for materials pending expiration to start workflows...");

      LocalDate dateNow = LocalDate.now();
      LocalDate dateTo = dateNow.plusDays(appParams.workflowStartDays().longValue());
      LocalDate dateFrom = dateTo.minusDays(WORKFLOW_START_BUFFER_DAYS);

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
        jobItem.setValue("taskDueDays", appParams.taskDueDays());
        jobItems.add(jobItem);
      }

    }  // end findStartItems()

    ////////////////////////////////////////////////////////////////////////
    // Find Materials for which to cancel existing "Expiration Pending"
    // workflows.
    ////////////////////////////////////////////////////////////////////////
    private void findCancelItems(
      JobInitContext jobInitContext,
      List<JobItem> jobItems,
      ExpirationPendingParameters appParams)
    {

      QueryResponse queryResponse;
      Iterator<QueryResult> iter;

      JobLogger logger = jobInitContext.getJobLogger();

      long workflowKillDays = appParams.workflowKillDays().longValue();

      logger.log(
        "Looking for active workflows for Materials with expiration date " +
          workflowKillDays + " day(s) in the future..."
      );

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      LocalDate workflowKillDate = LocalDate.now().plusDays(workflowKillDays);

      queryResponse = queryService.query(
        "select id from documents" +
        " where toName(lifecycle__v) = 'job_processing__c'" +
        "   and expiration_date__c <= '"+workflowKillDate.toString()+"'"
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
          logger.log("Expiration Date is imminent for " + docId);
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
      BigDecimal taskDueDays = jobItem.getValue("taskDueDays", JobValueType.NUMBER);

      logger.log("Starting 'Expiration Pending' workflow for: " + docNumber);

      List<String> userIds = Util.getDocumentUsersInRole(docVersionId, "project_manager__c");

      List<HttpParam> workflowStartCriteria = VaultCollections.newList();

      for (String userId : userIds) {
        logger.log(docNumber + ": Found user in role Project Manager: " + userId);
        workflowStartCriteria.add(new HttpParam("user_control_multiple__c", "user:" + userId));
      }

      // Calculate the task Due Date...
      LocalDate taskDueDate = expirationDate.minusDays(taskDueDays.longValue());
      LocalDate dateNow = LocalDate.now();
      if (taskDueDate.isBefore(dateNow)) {
        taskDueDate = dateNow;
      }

      workflowStartCriteria.add(new HttpParam("date_control__c", taskDueDate.toString()));

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
