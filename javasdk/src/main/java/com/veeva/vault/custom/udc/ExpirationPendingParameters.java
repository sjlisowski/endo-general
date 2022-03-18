package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;

import java.math.BigDecimal;

/**
 * Provides parameter values for the ExpirationPending application
 * from the parameters record in object "VPROC Parameter Set".
 */

@UserDefinedClassInfo
public class ExpirationPendingParameters {

    private JsonObject parametersJson;

    public ExpirationPendingParameters() {
        this.parametersJson = Util.getParameters("ExpirationPendingWorkflow");
    }

    /**
     * Return the number of days before a Job's Expiration date to determine when to execute the ExpirationPending
     * workflow for the Job.
     * @return
     */
    public BigDecimal workflowStartDays() {
        return this.parametersJson.getValue("workflowStartDays", JsonValueType.NUMBER);
    }

    /**
     * Return the number of days before a Job's Expiration Date to determine a Due Date for the ExpirationPending
     * workflow task.
     * @return
     */
    public BigDecimal taskDueDays() {
        return this.parametersJson.getValue("taskDueDays", JsonValueType.NUMBER);
    }

    /**
     * Return the number of days before a Jobs Expiration Date to determine a date on which to cancel the
     * active ExpirationPending workflow.
     * @return
     */
    public BigDecimal workflowKillDays() {
        return this.parametersJson.getValue("workflowKillDays", JsonValueType.NUMBER);
    }
}
