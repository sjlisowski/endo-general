package com.veeva.vault.custom.actions.document;

import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.job.JobParameters;
import com.veeva.vault.sdk.api.job.JobRunResult;
import com.veeva.vault.sdk.api.job.JobService;

/**
 * This is a test harness for testing the Job that executes the Expiration Pending workflow for Promo Jobs approaching
 * their expiration dates.
 */

@DocumentActionInfo(
  name = "vsdk_test_expiration_notification_task__c",
  label = "Test Expiration Notification Task",
  lifecycle = "job_processing__c",
  usages = {Usage.USER_ACTION}
)
public class TestExpirationNotificationTask implements DocumentAction {
	
    public void execute(DocumentActionContext documentActionContext) {

      JobService jobService = ServiceLocator.locate(JobService.class);
      JobParameters jobParameters = jobService.newJobParameters("expiration_pending_workflow__c");

      JobRunResult result = jobService.runJob(jobParameters);
    }

	public boolean isExecutable(DocumentActionContext documentActionContext) {
	    return true;
	}
}