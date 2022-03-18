package com.veeva.vault.custom.triggers.vproc_parameter_set;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.ExpirationPendingParameters;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

import java.math.BigDecimal;

/**
 *
 * This trigger protects parameters stored in object "VPROC Parameter Set" from unauthorized
 * updates and deletes.
 *
 * If a restricted update or delete must be made, then inactivate this trigger before making
 * the change.
 *
 */

@RecordTriggerInfo(
  object = "vproc_parameter_set__c",
  events = {
    RecordEvent.BEFORE_UPDATE,
    RecordEvent.AFTER_UPDATE,
    RecordEvent.BEFORE_DELETE
  },
  order = TriggerOrder.NUMBER_1
)
public class OnUpdateDeleteVprocParameterSet implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      RecordEvent recordEvent = recordTriggerContext.getRecordEvent();

      if (recordEvent == RecordEvent.BEFORE_DELETE) {
        throw new RollbackException(ErrorType.DELETION_DENIED, "Record deletions are not allowed in this object.");
      }

      if (recordTriggerContext.getRecordChanges().size() > 1) {
        throw new RollbackException(ErrorType.OPERATION_DENIED, "Bulk updates are not allowed");
      }

      Record newRecord = recordTriggerContext.getRecordChanges().get(0).getNew();
      Record oldRecord = recordTriggerContext.getRecordChanges().get(0).getOld();

      String oldName = oldRecord.getValue("name__v", ValueType.STRING);
      String newName = newRecord.getValue("name__v", ValueType.STRING);

      if (!newName.equals(oldName)) {
        throw new RollbackException(ErrorType.UPDATE_DENIED, "Cannot change the parameter set name.");
      }

      if (oldName.equals("ExpirationPendingWorkflow")) {
        checkPendingExpirationUpdate(newRecord, oldRecord, recordEvent);
      }

    }

    // check to make sure all of the parameters are numbers
    private void checkPendingExpirationUpdate(Record newRecord, Record oldRecord, RecordEvent recordEvent) {
      if (recordEvent == RecordEvent.AFTER_UPDATE) {
        BigDecimal bigDecimal;
        ExpirationPendingParameters appParams = new ExpirationPendingParameters();
        bigDecimal = appParams.workflowStartDays();
        bigDecimal = appParams.taskDueDays();
        bigDecimal = appParams.workflowKillDays();
      }
    }
}

