package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.DocumentsAPI;
import com.veeva.vault.custom.udc.HttpParam;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.job.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

/*
 This Job executes Expiration Notification workflows for Jobs that are approaching their Expiration Date.
*/

@JobInfo(adminConfigurable = true)
public class ExpirationUpcomingTasks implements Job {

    private static final long PENDING_RANGE_HIGH = 60;
    private static final long PENDING_RANGE_LOW = 55;
    // the number of days before a Job's Expiration Date that the pending expiration task should be cancelled...
    private static final long TASK_KILL_THRESHOLD_DAYS = 15;

    public JobInputSupplier init(JobInitContext jobInitContext) {

        QueryService queryService = ServiceLocator.locate(QueryService.class);

        JobLogger logger = jobInitContext.getJobLogger();

        List<JobItem> jobItems = VaultCollections.newList();

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
          jobItem.setValue("docNumber", docNbr);
          jobItem.setValue("docVersionId", docVersionId);
          jobItem.setValue("expirationDate", expirationDate);
          jobItems.add(jobItem);
        }

        return jobInitContext.newJobInput(jobItems);
    }

    public void process(JobProcessContext jobProcessContext) {

      JobLogger logger = jobProcessContext.getJobLogger();

      List<JobItem> jobItems = jobProcessContext.getCurrentTask().getItems();

      boolean errorsEncountered = false;

      for (JobItem jobItem : jobItems) {

        String docNumber = jobItem.getValue("docNumber", JobValueType.STRING);
        String docVersionId = jobItem.getValue("docVersionId", JobValueType.STRING);
        LocalDate expirationDate = jobItem.getValue("expirationDate", JobValueType.DATE);

        LocalDate taskKillDate = expirationDate.minusDays(TASK_KILL_THRESHOLD_DAYS);

        logger.log("Processing material: " + docNumber);

        List<String> userIds = Util.getDocumentUsersInRole(docVersionId, "project_manager__c");

        List<HttpParam> workflowStartCriteria = VaultCollections.newList();

        for (String userId : userIds) {
          logger.log("Found user in role Project Manager: " + userId);
          workflowStartCriteria.add(new HttpParam("user_control_multiple__c", "user:" + userId));
        }

        workflowStartCriteria.add(new HttpParam("date_control1__c", taskKillDate.toString()));

        logger.log("Executing workflow for " + docNumber);
        DocumentsAPI api = new DocumentsAPI("local_connection__c");
        api
          .setJobLogger(logger)
          .executeUserAction(docVersionId, "expiration_pending_autostart", workflowStartCriteria);
        if (api.failed()) {
          logger.log(api.getErrorType() + ": " + api.getErrorMessage());
          logger.log("Unable to execute workflow for "+docNumber+".");
          errorsEncountered = true;
        } else {
          logger.log("Successfully executed workflow for "+docNumber+".");
        }
      }

      TaskOutput taskOutput = jobProcessContext.getCurrentTask().getTaskOutput();

      if (errorsEncountered) {
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
}