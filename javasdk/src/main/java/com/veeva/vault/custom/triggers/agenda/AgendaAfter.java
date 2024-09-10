package com.veeva.vault.custom.triggers.agenda;

import com.veeva.vault.custom.udc.AgendaApp;
import com.veeva.vault.custom.udc.AgendaItemsList;
import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

@RecordTriggerInfo(
  object = "agenda__c",
  events = {RecordEvent.AFTER_UPDATE},
  order = TriggerOrder.NUMBER_1
)
public class AgendaAfter implements RecordTrigger {

    private static final String MeetingTimeFieldName = "meeting_time__c";
    private static final String MeetingDateFieldName = "meeting_date__c";

    public void execute(RecordTriggerContext recordTriggerContext) {

      List<RecordChange> recordChanges = recordTriggerContext.getRecordChanges();

      if (recordChanges.size() > 1) {
        return; // This trigger supports single-record operations only. But DO NOT throw an exception.
      }

      RecordChange inputRecord = recordChanges.get(0);

      Record newRecord = inputRecord.getNew();
      Record oldRecord = inputRecord.getOld();

      String agendaId = newRecord.getValue("id", ValueType.STRING);

      String newMeetingTime = newRecord.getValue(MeetingTimeFieldName, ValueType.STRING);
      String oldMeetingTime = oldRecord.getValue(MeetingTimeFieldName, ValueType.STRING);

      if (
           (oldMeetingTime != null && newMeetingTime == null) ||
           (oldMeetingTime == null && newMeetingTime != null) ||
           (newMeetingTime != null && !newMeetingTime.equals(oldMeetingTime))
         )
      {
        AgendaItemsList items = new AgendaItemsList(agendaId);
        items.updateStartEndTimes(newMeetingTime);
        items.saveChangedRecords();
      }

      LocalDate newMeetingDate = newRecord.getValue(MeetingDateFieldName, ValueType.DATE);
      LocalDate oldMeetingDate = oldRecord.getValue(MeetingDateFieldName, ValueType.DATE);

      if (
          (oldMeetingDate != null && newMeetingDate == null) ||
          (oldMeetingDate == null && newMeetingDate != null) ||
          (newMeetingDate != null && !newMeetingDate.equals(oldMeetingDate))
        ) {
        AgendaApp.updateDocumentMeetingDate(newMeetingDate, getDocIds(agendaId));
      }

    }  // end execute()

    private static List<String> getDocIds(String agendaId) {
      List<String> docIds = VaultCollections.newList();

      Iterator<QueryExecutionResult> iter = QueryUtil.query(
        "select id from documents where agenda__c = '"+agendaId+"'"
      ).streamResults().iterator();

      while (iter.hasNext()) {
        QueryExecutionResult result = iter.next();
        String docId = result.getValue("id", ValueType.STRING);
        docIds.add(docId);
      }

      return docIds;
    }

}

