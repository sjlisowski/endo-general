package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.AgendaApp;
import com.veeva.vault.custom.udc.AgendaItemsList;
import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.job.*;

import java.util.List;

/*
  This job recalculates the Start/End Times for all Agenda Items (agenda_item__c)
  belonging to a specific Agenda (agenda__c).
 */
  
  @JobInfo(adminConfigurable = true)
  public class AgendaItemRecalc implements Job {

    public JobInputSupplier init(JobInitContext jobInitContext) {

      JobLogger logger = jobInitContext.getJobLogger();

      String agendaId = jobInitContext.getJobParameter(AgendaApp.AGENDA_ID, JobParamValueType.STRING);
      String agendaMeetingTime = jobInitContext.getJobParameter(AgendaApp.AGENDA_MEETNG_TIME, JobParamValueType.STRING);

      String agendaName = QueryUtil.queryOne(
        "select name__v from agenda__c where id = '"+agendaId+"'"
      ).getValue("name__v", ValueType.STRING);

      logger.log("Processing agenda: \"" + agendaName + "\" ("+agendaId+")");

      List<JobItem> jobItems = VaultCollections.newList();

      JobItem jobItem = jobInitContext.newJobItem();
      jobItem.setValue(AgendaApp.AGENDA_ID, agendaId);
      jobItem.setValue(AgendaApp.AGENDA_MEETNG_TIME, agendaMeetingTime);
      jobItem.setValue(AgendaApp.AGENDA_NAME, agendaName);
      jobItems.add(jobItem);

      return jobInitContext.newJobInput(jobItems);
    }

    public void process(JobProcessContext jobProcessContext) {

      JobLogger logger = jobProcessContext.getJobLogger();

      // This Job is not expected to support multiple items...
      JobItem jobItem = jobProcessContext.getCurrentTask().getItems().get(0);

      String agendaId = jobItem.getValue(AgendaApp.AGENDA_ID, JobValueType.STRING);
      String agendaMeetingTime = jobItem.getValue(AgendaApp.AGENDA_MEETNG_TIME, JobValueType.STRING);
      String agendaName = jobItem.getValue(AgendaApp.AGENDA_NAME, JobValueType.STRING);

      logger.log("Updating start/end times for agenda: \"" + agendaName + "\" ("+agendaId+")");

      // This suppresses AFTER trigger processing for the Agenda Item object, so that we
      // can control calculation of the agenda item start/end times.
      // See 'triggers/AgendaItemAfter.java'.
      RequestContext.get().setValue(AgendaApp.AGENDA_ITEM_SEMAPHORE, true);

      AgendaItemsList agendaItemsList = new AgendaItemsList(agendaId);
      agendaItemsList.compressAgendaItemOrdering();
      agendaItemsList.updateStartEndTimes(agendaMeetingTime);
      agendaItemsList.saveChangedRecords();

      logger.log("Completed start/end times update for agenda: \"" + agendaName + "\" ("+agendaId+")");

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
       logger.log("completeWithError: " + result.getNumberFailedTasks() + "tasks failed out of " + result.getNumberTasks());

       List<JobTask> tasks = jobCompletionContext.getTasks();
       for (JobTask task : tasks) {
           TaskOutput taskOutput = task.getTaskOutput();
           if (TaskState.ERRORS_ENCOUNTERED.equals(taskOutput.getState())) {
              logger.log(task.getTaskId() + " failed with error message " + taskOutput.getValue("firstError", JobValueType.STRING));
           }
       }
    }
  }