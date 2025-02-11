package com.veeva.vault.custom.actions.document.Agenda;

import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.util.List;

/**
 * This class deletes Agenda Items (agenda_item__c) for the contextual Document.
 */

@DocumentActionInfo(
	label="Document Agenda Item Delete",
	lifecycle="job_processing__c",
	usages={Usage.LIFECYCLE_ENTRY_ACTION}
)
public class DocumentAgendaItemDelete implements DocumentAction {
	
    public void execute(DocumentActionContext documentActionContext) {

    	DocumentVersion docVersion = documentActionContext.getDocumentVersions().get(0);

			List<String> agendaIds = docVersion.getValue("agenda__c", ValueType.REFERENCES);
			if (agendaIds == null || agendaIds.size() == 0) {
				return;
			}
			String agendaId = agendaIds.get(0);

			String docVersionId = docVersion.getValue("version_id", ValueType.STRING);
			String agendaItemId = getAssociatedAgendaItemId(agendaId, docVersionId);

			if (associatedAgendaItemExists(agendaItemId)) {
				deleteAssociatedAgendaItem(agendaItemId);
			}

    }

  	private static String getAssociatedAgendaItemId(String agendaId, String docVersionId) {
		  QueryExecutionResult result = QueryUtil.queryOne(
	  		"select id from agenda_item__c " +
	  			" where agenda__c = '"+agendaId+"' " +
	  			"   and document__c = '"+docVersionId+"'"
		  );
			if (result != null) {
				return result.getValue("id", ValueType.STRING);
			} else {
				return null;
			}
  	}

	  private static boolean associatedAgendaItemExists(String agendaItemId) {
		  return (agendaItemId != null);
	  }

	  private static void deleteAssociatedAgendaItem(String agendaItemId) {
			RecordService recordService = ServiceLocator.locate(RecordService.class);
			Record record = recordService.newRecordWithId("agenda_item__c", agendaItemId);
			Util.deleteRecord(record);
	  }

	  public boolean isExecutable(DocumentActionContext documentActionContext) {
	    return true;
	  }
}