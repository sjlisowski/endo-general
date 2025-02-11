package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.AgendaApp;
import com.veeva.vault.custom.udc.Logger;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.job.*;

import java.util.List;

/**
 *  Deactivate Agenda (agenda__c) records whose Meeting Date field (meeting_date__c)
 *  is set to a date before the current date.
 */

@JobInfo(adminConfigurable = true)
public class AgendaDeactivation implements Job {

    private static final String TASK_ERROR_MSG = "taskErrorMsg";

    // Initialize custom job and set job input values
    public JobInputSupplier init(JobInitContext jobInitContext) {
      List<JobItem> jobItems = VaultCollections.newList();
      jobItems.add(jobInitContext.newJobItem());
      return jobInitContext.newJobInput(jobItems);
    }

    // Process Job Items and set task output status
    public void process(JobProcessContext jobProcessContext) {

      Logger logger = new Logger(jobProcessContext.getJobLogger());

      logger.info("Starting deactivation...");
      AgendaApp.deactivatePastAgendas(logger);
      logger.info("...deactivation complete");

      JobTask task = jobProcessContext.getCurrentTask();
      TaskOutput taskOutput = task.getTaskOutput();
      taskOutput.setState(TaskState.SUCCESS);

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
                logger.log(task.getTaskId() + " failed with error message " + taskOutput.getValue(TASK_ERROR_MSG, JobValueType.STRING));
            }
        }
    }
}
