package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.document.DocumentService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *  Methods needed to support the Review Agenda App.
 */

@UserDefinedClassInfo
public class AgendaApp {

  public static final String AGENDA_ID = "AgendaId";
  public static final String AGENDA_NAME = "AgendaName";
  public static final String AGENDA_MEETNG_TIME = "AgendaMeetingTime";
  public static final String AGENDA_ITEM_SEMAPHORE = "semaphore";

  public static String getAgendaMeetingTime(String agendaId) {
      return QueryUtil.queryOne(
        "select meeting_time__c from agenda__c where id = '"+agendaId+"'"
      ).getValue("meeting_time__c", ValueType.STRING);
    }

    /**
     *  Set Agenda records to inactive for Agendas whose meeting date is before Today.
     */
    public static void deactivatePastAgendas(Logger logger) {

      RecordService recordService = ServiceLocator.locate(RecordService.class);
      List<String> inactive__v = VaultCollections.asList("inactive__v");

      LocalDate dtToday = LocalDate.now();
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
      String strToday = dtToday.format(formatter);

      Iterator<QueryExecutionResult> iterator = QueryUtil.query(
        "select id, name__v " +
          "from agenda__c "+
         "where meeting_date__c < '"+strToday+"' " +
           "and status__v = 'active__v'"
      ).streamResults().iterator();

      if (! iterator.hasNext()) {
        logger.info("No past Agendas found.");
      }

      while (iterator.hasNext()) {
        QueryExecutionResult result = iterator.next();
        String id = result.getValue("id", ValueType.STRING);
        String name = result.getValue("name__v", ValueType.STRING);
        logger.info("Found agenda "+id+": '"+name+"'");
        Record record = recordService.newRecordWithId("agenda__c", id);
        record.setValue("status__v", inactive__v);
        Util.saveRecord(record);  // cannot update status__v in batch for parent objects
      }
    }

  /**
   This method updates an Agenda Item record (agenda_item__c) with field values
   from the Document.

    @param record - an Agenda Item record for a specific Agenda Item
    @param docId - the id of the Document from which to pull the data
   */
  public static void setAgendaItemDocumentInfo(Record record, BigDecimal docId) {

    int intDocId = docId.intValue();
    LocalDate date = null;

    QueryExecutionResult queryResult = QueryUtil.queryOne(
      "select document_number__v," +
      "    toName(marc_review_tier__c) as marc_review_tier," +
      "    planned_first_use_date__c," +
      "    review_due_date__c," +
      "    pm_review_due_date__c, " +
      "    discussion_time_in_minutes__c" +
      "  from documents where id = " + intDocId
    );
    String documentNumber = queryResult.getValue("document_number__v", ValueType.STRING);
    record.setValue("topic__c", documentNumber);
    List<String> reviewTier = queryResult.getValue("marc_review_tier", ValueType.PICKLIST_VALUES);
    record.setValue("marc_review_tier__c", reviewTier);
    date = queryResult.getValue("planned_first_use_date__c", ValueType.DATE);
    record.setValue("date_of_first_use__c", date);
    date = queryResult.getValue("review_due_date__c", ValueType.DATE);
    record.setValue("review_due_date__c", date);
    date = queryResult.getValue("pm_review_due_date__c", ValueType.DATE);
    record.setValue("pm_review_due_date__c", date);
    BigDecimal discussionTime = queryResult.getValue("discussion_time_in_minutes__c", ValueType.NUMBER);
    record.setValue("duration__c", discussionTime);

    List<String> roleNames = VaultCollections.newList();
    roleNames.add("owner__c");
    roleNames.add("project_manager__c");
    roleNames.add("medical__c");
    roleNames.add("legal__c");
    roleNames.add("regulatory__c");
    roleNames.add("compliance__c");
    roleNames.add("reviewer__c");
    Map<String, List<String>> roleUsersMap = Util.getUsersInDocumentRoles(intDocId, roleNames);
    List<String> usersInRole;  // list of user ID's

    VaultUsers vaultUsers = new VaultUsers();

    // set the Owner and Project Manager fields.  These are a User Object Reference fields...

    String projectOwner = null;
    usersInRole = roleUsersMap.get("project_manager__c");
    if (usersInRole != null) {
      for (String userId: usersInRole) {
        if (vaultUsers.isUserActive(userId)) {
          projectOwner = userId;
          break;
        }
      }
    }
    record.setValue("project_owner__c", projectOwner);

    usersInRole = roleUsersMap.get("owner__c");
    String documentOwner = usersInRole.get(0);  // owner role is always occupied
    if (projectOwner == null || !documentOwner.equals(projectOwner)) {
      if (vaultUsers.isUserActive(documentOwner)) {
        record.setValue("document_owner__c", documentOwner);
      }
    }


    // the remaining user fields are Text fields ...

    List<String> userNamesInRole = VaultCollections.newList();

    usersInRole = roleUsersMap.get("medical__c");
    if (usersInRole != null) {
      userNamesInRole.clear();
      for (String userId: usersInRole) {
        userNamesInRole.add(vaultUsers.getUserName(userId));
      }
      record.setValue("medical1__c", Util.stringifyList(userNamesInRole));
    }

    usersInRole = roleUsersMap.get("legal__c");
    if (usersInRole != null) {
      userNamesInRole.clear();
      for (String userId: usersInRole) {
        userNamesInRole.add(vaultUsers.getUserName(userId));
      }
      record.setValue("legal1__c", Util.stringifyList(userNamesInRole));
    }

    usersInRole = roleUsersMap.get("regulatory__c");
    if (usersInRole != null) {
      userNamesInRole.clear();
      for (String userId: usersInRole) {
        userNamesInRole.add(vaultUsers.getUserName(userId));
      }
      record.setValue("regulatory1__c", Util.stringifyList(userNamesInRole));
    }

    usersInRole = roleUsersMap.get("compliance__c");
    if (usersInRole != null) {
      userNamesInRole.clear();
      for (String userId: usersInRole) {
        userNamesInRole.add(vaultUsers.getUserName(userId));
      }
      record.setValue("compliance1__c", Util.stringifyList(userNamesInRole));
    }

    usersInRole = roleUsersMap.get("reviewer__c");
    if (usersInRole != null) {
      userNamesInRole.clear();
      for (String userId: usersInRole) {
        userNamesInRole.add(vaultUsers.getUserName(userId));
      }
      record.setValue("reviewer1__c", Util.stringifyList(userNamesInRole));
    }

  }

  /**
   * updateDocumentMeetingDate
   *
   * Update the 'Meeting Date' field (meeting_review_date__c) on the documents identified
   * by the list of document Ids.
   *
   * @param meetingDate - LocalDate.  The meeting date.
   * @param docIds  - List<String>.  The list of docIds.
   */
    public static void updateDocumentMeetingDate(LocalDate meetingDate, List<String> docIds) {

      DocumentService documentService = ServiceLocator.locate(DocumentService.class);
      List<DocumentVersion> documentVersions = VaultCollections.newList();

      for (String docId: docIds) {
        DocumentVersion documentVersion = documentService.newDocumentWithId(docId);
        documentVersion.setValue("meeting_review_date__c",meetingDate);
        documentVersions.add(documentVersion);
      }

      documentService.saveDocumentVersions(documentVersions);
    }

}
