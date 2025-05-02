package com.veeva.vault.custom.triggers.agenda;

import com.veeva.vault.custom.udc.AgendaApp;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordTriggerInfo;
import com.veeva.vault.sdk.api.data.RecordEvent;
import com.veeva.vault.sdk.api.data.RecordTrigger;
import com.veeva.vault.sdk.api.data.RecordTriggerContext;
import com.veeva.vault.sdk.api.data.RecordChange;

import java.math.BigDecimal;
import java.util.List;

/**
 * This trigger updates the project_manager__c field and sets the topic__c field to the Document Number if a Document
 * is selected in the document__c field.
 */

@RecordTriggerInfo(
  object = "agenda_item__c",
  events = {
    RecordEvent.BEFORE_INSERT,
    RecordEvent.BEFORE_UPDATE

  },
  order = TriggerOrder.NUMBER_1
)
public class AgendaItemBefore implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      List<RecordChange> recordChanges = recordTriggerContext.getRecordChanges();

      if (recordChanges.size() > 1) {
        return; // This trigger supports single-record operations only (but DON'T throw and exception)
      }

      RecordChange inputRecord = recordChanges.get(0);

      RecordEvent recordEvent = recordTriggerContext.getRecordEvent();

      if (recordEvent == RecordEvent.BEFORE_INSERT) {

        Record newRecord = inputRecord.getNew();
        BigDecimal docId = newRecord.getValue("document_unbound__c", ValueType.NUMBER);
        if (docId != null) {
          AgendaApp.setAgendaItemDocumentInfo(newRecord, docId);
        }

      } else if (recordEvent == RecordEvent.BEFORE_UPDATE) {

        Record newRecord = inputRecord.getNew();
        Record oldRecord = inputRecord.getOld();

        BigDecimal docIdNew = newRecord.getValue("document_unbound__c", ValueType.NUMBER);
        BigDecimal docIdOld = oldRecord.getValue("document_unbound__c", ValueType.NUMBER);

        if (
          (docIdOld == null && docIdNew != null) ||
          (docIdOld != null && docIdNew != null && !docIdNew.equals(docIdOld))
        ) {
          AgendaApp.setAgendaItemDocumentInfo(newRecord, docIdNew);
        } else if (docIdOld != null && docIdNew == null) {
          //clear out the PM field but leave topic__c alone
          newRecord.setValue("project_owner__c", null);
        }

      }

    }  // end execute()

}

