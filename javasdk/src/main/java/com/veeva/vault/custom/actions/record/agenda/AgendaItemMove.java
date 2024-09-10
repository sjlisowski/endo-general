package com.veeva.vault.custom.actions.record.agenda;

import com.veeva.vault.custom.udc.AgendaApp;
import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.action.RecordAction;
import com.veeva.vault.sdk.api.action.RecordActionContext;
import com.veeva.vault.sdk.api.action.RecordActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.document.DocumentService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.job.JobParameters;
import com.veeva.vault.sdk.api.job.JobService;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * This record action implements the User Action to move an Agenda Item to a different
 * Agenda.
 */

@RecordActionInfo (
  label="Move to Another Agenda",
  object="agenda_item__c",
  usages={Usage.USER_ACTION},
  user_input_object = "agenda_item_move__c"
)
public class AgendaItemMove implements RecordAction {

    public void execute(RecordActionContext recordActionContext) {

      RecordService recordService = ServiceLocator.locate(RecordService.class);

      Record thisRecord = recordActionContext.getRecords().get(0);
      Record inputRecord = recordActionContext.getUserInputRecord();

      String oldAgendaId = thisRecord.getValue("agenda__c", ValueType.STRING);
      String newAgendaId = inputRecord.getValue("agenda__c", ValueType.STRING);

      if (newAgendaId.equals(oldAgendaId)) {
        throw new RollbackException(ErrorType.OPERATION_DENIED, "Select a different Agenda");
      }

      BigDecimal docId = thisRecord.getValue("document_unbound__c", ValueType.NUMBER);

      /////////////////////////////////////////////////////////////
      // Update the Document's agenda__c field ...
      /////////////////////////////////////////////////////////////
      if (docId != null) {
        QueryExecutionResult result = QueryUtil.queryOne(
          "select agenda__c, version_id from documents where id = " + docId
        );
        List<String> agendaIds = result.getValue("agenda__c", ValueType.REFERENCES);
        String docVersionId = result.getValue("version_id", ValueType.STRING);

        agendaIds.set(agendaIds.indexOf(oldAgendaId), newAgendaId);

        DocumentService documentService = ServiceLocator.locate(DocumentService.class);
        DocumentVersion documentVersion = documentService.newVersionWithId(docVersionId);
        documentVersion.setValue("agenda__c", agendaIds);
        documentService.saveDocumentVersions(VaultCollections.asList(documentVersion));
      }

      // This suppresses AFTER trigger processing for the Agenda Item object, so that we
      // can control calculation of the agenda item start/end times.
      // See 'triggers/AgendaItemAfter.java'.
      RequestContext.get().setValue(AgendaApp.AGENDA_ITEM_SEMAPHORE, true);

      /////////////////////////////////////////////////////////////
      // Add the item to the other agenda ...
      /////////////////////////////////////////////////////////////
      Record recordToSave = recordService.newRecord("agenda_item__c");
      recordToSave.setValue("agenda__c", newAgendaId);
      recordToSave.setValue("document_unbound__c", docId);
      recordToSave.setValue("topic__c", thisRecord.getValue("topic__c", ValueType.STRING));
      recordToSave.setValue("duration__c", thisRecord.getValue("duration__c", ValueType.NUMBER));
      recordToSave.setValue("project_owner__c", thisRecord.getValue("project_owner__c", ValueType.STRING));
      recordToSave.setValue("document_owner__c", thisRecord.getValue("document_owner__c", ValueType.STRING));
      recordToSave.setValue("notes__c", thisRecord.getValue("notes__c", ValueType.STRING));
      Util.saveRecord(recordToSave);

      String recordId;

      /////////////////////////////////////////////////////////////
      // Remove the item from its current agenda ...
      /////////////////////////////////////////////////////////////
      recordId = thisRecord.getValue("id", ValueType.STRING);
      Util.deleteRecord(recordService.newRecordWithId("agenda_item__c", recordId));

      /////////////////////////////////////////////////////////////
      // And delete the input record -- it's no longer needed ...
      /////////////////////////////////////////////////////////////
      recordId = inputRecord.getValue("id", ValueType.STRING);
      Util.deleteRecord(recordService.newRecordWithId("agenda_item_move__c", recordId));

      ////////////////////////////////////////////////////////////////////////////
      // recalculate start/end times for the Agenda Items in the old Agenda ...
      ////////////////////////////////////////////////////////////////////////////
      String agendaMeetingTime = AgendaApp.getAgendaMeetingTime(oldAgendaId);
      if (agendaMeetingTime != null) {
        JobService jobService = ServiceLocator.locate(JobService.class);
        JobParameters jobParameters = jobService.newJobParameters("agenda_item_recalc__c");
        jobParameters.setValue(AgendaApp.AGENDA_ID, oldAgendaId);
        jobParameters.setValue(AgendaApp.AGENDA_MEETNG_TIME, agendaMeetingTime);
        jobService.runJob(jobParameters);
      }

    }

    public boolean isExecutable(RecordActionContext recordActionContext) {
        return true;
    }
}