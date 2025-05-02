package com.veeva.vault.custom.triggers.agenda;

import com.veeva.vault.custom.udc.AgendaApp;
import com.veeva.vault.custom.udc.AgendaItemsList;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.RequestContextValueType;
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
 * This trigger manages updates to the collections of Agenda Item records based on changes
 * to Order (order__c) or Duration (duration__c).
 */

@RecordTriggerInfo(
  object = "agenda_item__c",
  events = {
    RecordEvent.AFTER_INSERT,
    RecordEvent.AFTER_UPDATE,
    RecordEvent.AFTER_DELETE
  },
  order = TriggerOrder.NUMBER_1
)
public class AgendaItemAfter implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      if (!setSemaphore()) {
        return;  //this is not the initial request in the Context
      }

      List<RecordChange> recordChanges = recordTriggerContext.getRecordChanges();

      if (recordChanges.size() > 1) {
        return; // This trigger supports single-record operations only.
      }

      RecordChange inputRecord = recordChanges.get(0);

      Record newRecord = null;
      Record oldRecord = null;

      RecordEvent recordEvent = recordTriggerContext.getRecordEvent();

      if (recordEvent == RecordEvent.AFTER_INSERT || recordEvent == RecordEvent.AFTER_UPDATE) {
        newRecord = inputRecord.getNew();
      }
      if (recordEvent == RecordEvent.AFTER_UPDATE || recordEvent == RecordEvent.AFTER_DELETE) {
        oldRecord = inputRecord.getOld();
      }

      String agendaId;
      String recordId;
      BigDecimal oldOrder = null;
      BigDecimal newOrder = null;
      BigDecimal oldDuration = null;
      BigDecimal newDuration = null;

      if (newRecord == null) {
        agendaId = oldRecord.getValue("agenda__c", ValueType.STRING);
        recordId = oldRecord.getValue("id", ValueType.STRING);
      } else {
        agendaId = newRecord.getValue("agenda__c", ValueType.STRING);
        recordId = newRecord.getValue("id", ValueType.STRING);
      }

      if (newRecord != null) {
        newOrder = newRecord.getValue("order__c", ValueType.NUMBER);
        newDuration = newRecord.getValue("duration__c", ValueType.NUMBER);
      }

      if (oldRecord != null) {
        oldOrder = oldRecord.getValue("order__c", ValueType.NUMBER);
        oldDuration = oldRecord.getValue("duration__c", ValueType.NUMBER);
      }

      if (oldOrder == null && newOrder != null) {
        AgendaItemsList agendaItems = new AgendaItemsList(agendaId);
        agendaItems.shiftDownAfter(recordId);
        String agendaMeetingTime = getAgendaMeetingTime(agendaId);
        if (agendaMeetingTime != null) {
          agendaItems.updateStartEndTimes(agendaMeetingTime);
        }
        agendaItems.saveChangedRecords();
      }
      else if (oldOrder != null && newOrder != null) {
        int iNewOrder = newOrder.intValue();
        int iOldOrder = oldOrder.intValue();
        if (iNewOrder != iOldOrder) {
          AgendaItemsList agendaItems = new AgendaItemsList(agendaId);
          if (iNewOrder < iOldOrder) {
            agendaItems.shiftDownAfter(recordId);
          } else if (iNewOrder > iOldOrder) {
            agendaItems.shiftUpBefore(recordId);
          }
          String agendaMeetingTime = getAgendaMeetingTime(agendaId);
          if (agendaMeetingTime != null) {
            agendaItems.updateStartEndTimes(agendaMeetingTime);
          }
          agendaItems.saveChangedRecords();
        }
      }

      if (
        (oldOrder != null && newOrder == null) &&
        (oldDuration != null)
      ) {
        AgendaItemsList agendaItems = new AgendaItemsList(agendaId);
        String agendaMeetingTime = getAgendaMeetingTime(agendaId);
        if (agendaMeetingTime != null) {
          agendaItems.updateStartEndTimes(agendaMeetingTime);
          agendaItems.saveChangedRecords();
        }
      }

      if (   // no other change except a change in duration
        (
          (oldOrder == null && newOrder == null) ||
          (oldOrder != null && newOrder != null && newOrder.intValue() == oldOrder.intValue())
        )
        &&
        (
          (oldDuration == null && newDuration != null) ||
          (oldDuration != null && newDuration == null) ||
          (oldDuration != null && newDuration != null && newDuration.intValue() != oldDuration.intValue())
        )
      ) {
        AgendaItemsList agendaItems = new AgendaItemsList(agendaId);
        String agendaMeetingTime = getAgendaMeetingTime(agendaId);
        if (agendaMeetingTime != null) {
          agendaItems.updateStartEndTimes(agendaMeetingTime);
          agendaItems.saveChangedRecords();
        }
      }

    } //end execute()

    private String getAgendaMeetingTime(String agendaId) {
      return AgendaApp.getAgendaMeetingTime(agendaId);
    }

    /*
      The "semaphore" insures that the trigger is executed only on the initial request within
      the Context.
     */
    private boolean setSemaphore() {
      Boolean semaphore = RequestContext.get().getValue(
        AgendaApp.AGENDA_ITEM_SEMAPHORE,
        RequestContextValueType.BOOLEAN
      );
      if (semaphore != null) {
        return false;  //this is not the initial request in the Context
      }
      RequestContext.get().setValue(AgendaApp.AGENDA_ITEM_SEMAPHORE, true);
      return true;
    }

}

