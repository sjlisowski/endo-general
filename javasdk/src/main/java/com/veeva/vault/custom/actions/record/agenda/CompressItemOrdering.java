package com.veeva.vault.custom.actions.record.agenda;

import com.veeva.vault.custom.udc.AgendaItemsList;
import com.veeva.vault.sdk.api.action.RecordAction;
import com.veeva.vault.sdk.api.action.RecordActionContext;
import com.veeva.vault.sdk.api.action.RecordActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.Record;

/**
 * This action will update the order__c field on the Agenda Item (agenda_item__c)
 * records so that the order numbers are contiguous, for example:
 *    "1, 2, 3, 4, 5" instead of "2, 3, 6, 7, 11"
 */

@RecordActionInfo(
  label="Compress Item Ordering",
  object="agenda__c",
  usages={Usage.USER_ACTION}
)
public class CompressItemOrdering implements RecordAction {

    public void execute(RecordActionContext recordActionContext) {
      Record record = recordActionContext.getRecords().get(0);
      String agendaId = record.getValue("id", ValueType.STRING);
      AgendaItemsList agendaItems = new AgendaItemsList(agendaId);
      agendaItems.compressAgendaItemOrdering();
      agendaItems.saveChangedRecords();
    }

    public boolean isExecutable(RecordActionContext recordActionContext) {
        return true;
    }
}