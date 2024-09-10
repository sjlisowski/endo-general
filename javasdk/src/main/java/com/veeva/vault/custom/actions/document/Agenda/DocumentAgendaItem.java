package com.veeva.vault.custom.actions.document.Agenda;

import com.veeva.vault.custom.udc.AgendaApp;
import com.veeva.vault.custom.udc.DocVersionIdParts;
import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.ReadRecordsResponse;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * This class creates or updates Agenda Items (agenda_item__c) as needed for the contextual Document.
 */

@DocumentActionInfo(
	label="Document Agenda Item",
	lifecycle="job_processing__c",
	usages={Usage.LIFECYCLE_ENTRY_ACTION}
)
public class DocumentAgendaItem implements DocumentAction {
	
    public void execute(DocumentActionContext documentActionContext) {

    	DocumentVersion docVersion = documentActionContext.getDocumentVersions().get(0);
			String docVersionId = docVersion.getValue("version_id", ValueType.STRING);

			List<String> agendaIds = docVersion.getValue("agenda__c", ValueType.REFERENCES);
			if (agendaIds == null || agendaIds.size() == 0) {
				updateDocumentMeetingDate(null, docVersionId);
				return;
			}
			String agendaId = agendaIds.get(0);

			String agendaItemId = getAssociatedAgendaItemId(agendaId, docVersionId);

			if (!associatedAgendaItemExists(agendaItemId)) {
				createAgendaItem(agendaId, docVersionId);
				updateDocumentMeetingDate(agendaId, docVersionId);
			} else {
				updateAgendaItem(agendaItemId, docVersionId);
			}

    }

		private static void createAgendaItem(String agendaId, String docVersionId) {

			DocVersionIdParts docVersionIdParts = new DocVersionIdParts(docVersionId);
			BigDecimal docId = BigDecimal.valueOf((long)(Integer.parseInt(docVersionIdParts.id)));

			RecordService recordService = ServiceLocator.locate(RecordService.class);
			Record record = recordService.newRecord("agenda_item__c");
			record.setValue("agenda__c", agendaId);
			record.setValue("document_unbound__c", docId);
			Util.saveRecord(record);

		}

		private static void updateAgendaItem(String agendaItemId, String docVersionId) {

			DocVersionIdParts docVersionIdParts = new DocVersionIdParts(docVersionId);
			BigDecimal docId = BigDecimal.valueOf((long)(Integer.parseInt(docVersionIdParts.id)));

			RecordService recordService = ServiceLocator.locate(RecordService.class);
			Record record = recordService.newRecordWithId("agenda_item__c", agendaItemId);

			AgendaApp.setAgendaItemDocumentInfo(record, docId);

			Util.saveRecord(record);

		}

		private static void updateDocumentMeetingDate(String agendaId, String docVersionId) {

			DocVersionIdParts docVersionIdParts = new DocVersionIdParts(docVersionId);
			LocalDate meetingDate = null;

			if (agendaId != null) {
				RecordService recordService = ServiceLocator.locate(RecordService.class);
				Record record = recordService.newRecordWithId("agenda__c", agendaId);
				ReadRecordsResponse response = recordService.readRecords(VaultCollections.asList(record));
				record = response.getRecords().get(agendaId);
				meetingDate = record.getValue("meeting_date__c", ValueType.DATE);
			}

			AgendaApp.updateDocumentMeetingDate(meetingDate, VaultCollections.asList(docVersionIdParts.id));
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

  	public boolean isExecutable(DocumentActionContext documentActionContext) {
	    return true;
	  }
}