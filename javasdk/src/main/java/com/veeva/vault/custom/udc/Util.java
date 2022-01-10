package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.document.DocumentService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.json.JsonData;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonService;
import com.veeva.vault.sdk.api.json.JsonValueType;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;
import com.veeva.vault.sdk.api.role.DocumentRole;
import com.veeva.vault.sdk.api.role.DocumentRoleService;
import com.veeva.vault.sdk.api.role.GetDocumentRolesResponse;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/*
  Static methods in this class:

  vqlContains - Return a String containing a VQL 'contains' filter surrounded by parenthises, e.g.:
     "('this', 'that', 'the other')".
  stringifyList - Return a comma-delimited string build from a list of strings.
  getRecordID - Return the ID of a record where the identified field contains the identified value.
  getRecordValue - Return a field value from an Object Record identified by the Record's ID.
  getTypeName - Return the API name of an object record's Object Type.
  getRoleId - Return the object record ID from the Application Role object where the record is for the Regulatory role.
  getUSCountryId - Return the record ID from the Country Object record for the United States.
  getDocumentUsersInRole - Return the list of users currently occupying the role for a give document.
  getSinglePicklistValue - Return the value from a single-pick picklist field, or null if the field value is null.
  stringifyFieldValues - Concatenates a field value across one or more records in a query response.
  difference - Return a list of Strings from list1/set1 that are not also in list2/set2.
  getVaultDomain - Get the Domain Name part of the Vault's URL
 */

@UserDefinedClassInfo
public class Util {

  /**
   * Return a String containing a VQL 'contains' filter surrounded by parenthises, e.g.:
   *      "('this', 'that', 'the other')".  The list is assumed to contain elements.
   * @param list - List<String> list it items to be included in the 'contains' filter.
   * @return String - the 'contains' filter.
   */
  public static String vqlContains(List<String> list) {

    StringBuilder contains = new StringBuilder();
    Iterator<String> iter = list.iterator();

    contains.append("(");
    while (iter.hasNext()) {
      contains.append("'").append(iter.next()).append("'");
      if (iter.hasNext()) {
        contains.append(",");
      }
    }
    contains.append(")");

    return contains.toString();
  }

  /**
   * Return a comma-delimited string build from a list of strings.
   * @param list - List<String>.  the list
   * @return String
   */
  public static String stringifyList(List<String> list) {

    StringBuilder sb = new StringBuilder();

    Iterator<String> iter = list.iterator();

    while (iter.hasNext()) {
      sb.append(iter.next());
      if (iter.hasNext()) {
        sb.append(", ");
      }
    }

    return sb.toString();
  }

  /**
   * Return the ID of a record where the identified field contains the identified value.  fieldName should
   * be the name of a unique Text field on the Object.  If the Text field is not unique, the ID of the
   * first record is returned.
   * @param objectName - String.  Name of the Vault Object.
   * @param fieldName - String.  Name of the field on the Vault Object to be queried.
   * @param fieldValue - String.  The value to use as a filter for the field in the query.
   * @return String.  ID of record.
   */
  public static String getRecordID(String objectName, String fieldName, String fieldValue) {
    QueryService qs = ServiceLocator.locate(QueryService.class);
    QueryResponse qr = qs.query("select id from "+objectName+" where "+fieldName+" = '"+fieldValue+"'");
    return qr.streamResults().iterator().next().getValue("id", ValueType.STRING);
  }

  /**
   * Return a field value from a record.  Assumes the value of recordID is a valid ID of a Record
   * that currently exists in the Object.
   * @param objectName - String.  Name of the Vault Object.
   * @param fieldName - String.  Name of the Field on the Vault Object.
   * @param recordID - String.  ID of the Object Record to select.
   * @param valueType - ValueType<T>. ValueType of the field value.
   * @return <T> T - Field value to return
   */
    public static <T> T getRecordValue(String objectName, String fieldName, String recordID, ValueType<T> valueType) {
      QueryService qs = ServiceLocator.locate(QueryService.class);
      QueryResponse qr = qs.query("select "+fieldName+" from "+objectName+" where id = '"+recordID+"'");
      return qr.streamResults().iterator().next().getValue(fieldName, valueType);
    }

  /**
   * Return the API name of an object record's Object Type.
   * @param objectTypeID - from the record's object_type__v field value.
   * @return String - api name of the object type
   */
    public static String getTypeName(String objectTypeID) {

      QueryService qs = ServiceLocator.locate(QueryService.class);
      String query = "select api_name__v from object_type__v where id = '"+objectTypeID+"'";
      QueryResponse qr = qs.query(query);
      return qr.streamResults().iterator().next().getValue("api_name__v", ValueType.STRING);
    }

   /**
     * Return the API name of an object record's Object Type.
     * @param record - Object record
     * @return String - api name of the object type
     */
    public static String getTypeName(Record record) {
      return Util.getTypeName(record.getValue("object_type__v", ValueType.STRING));
    }

  /**
   * Return the object record ID from the Application Role object where the record is for the Regulatory role.
   * @return
   */
    public static String getRoleId(String roleName) {
      QueryService qs = ServiceLocator.locate(QueryService.class);
      QueryResponse qr = qs.query("select id from application_role__v where api_name__v = '"+roleName+"'");
      return qr.streamResults().iterator().next().getValue("id", ValueType.STRING);
    }

  /**
   * Return the record ID from the Country Object record for the United States.
   * @return String. record ID
   */
    public static String getUSCountryId() {
      QueryService qs = ServiceLocator.locate(QueryService.class);
      QueryResponse qr = qs.query("select id from country__v where abbreviation__c = 'US'");
      return qr.streamResults().iterator().next().getValue("id", ValueType.STRING);
    }

    /**
     *  Return the list of users currently occupying the role for a give document.
     * @param docVersionId in the form "id_major_minor", e.g. "539_0_6"
     * @param roleName - the API name of the role
     * @return
     */
    public static List<String> getDocumentUsersInRole(String docVersionId, String roleName) {

      DocumentService documentService = ServiceLocator.locate(DocumentService.class);
      DocumentRoleService documentRoleService = ServiceLocator.locate(DocumentRoleService.class);

      DocumentVersion docVersion = documentService.newVersionWithId(docVersionId);
      GetDocumentRolesResponse response =
        documentRoleService.getDocumentRoles(VaultCollections.asList(docVersion), roleName);
      DocumentRole docRole = response.getDocumentRole(docVersion);

      return docRole.getUsers();
    }

  /**
   * Return the value from a single-pick picklist field, or null if the field value is null.
   * @param values - a value returned from .getValue(fieldname, ValueType.PICKLIST_VALUES)
   * @return String - the 1st value in the picklist, or null
   */
    public static String getSinglePicklistValue(List<String> values) {
      if (values == null) {
        return null;
      } else {
        return values.get(0);
      }
    }

    /**
     * stringifyFieldValues.  Concatenates a field value across one or more records in a query response
     * and returns the field values as a delimited string.
     *
     * @param qResponse - a QueryResponse object
     * @param fieldName - the field name from which to pull values
     * @param fieldLength - the maximum length of the String to return
     * @param delimiter - a string to use to delimit the values
     * @return a String containing the field values delimited by the provided delimiter
     */
    public static String stringifyFieldValues(
            QueryResponse qResponse, String fieldName, int fieldLength, String delimiter
    ) {
        StringBuilder stringBuilder = new StringBuilder(fieldLength);

        Iterator<QueryResult> iterator = qResponse.streamResults().iterator();

        while (iterator.hasNext()) {
            QueryResult qr = iterator.next();
            stringBuilder.append(qr.getValue(fieldName, ValueType.STRING));
            if (iterator.hasNext()) {
                stringBuilder.append(delimiter);
            }
        }

        String fieldString = stringBuilder.toString();

        return fieldString.length() > fieldLength ? fieldString.substring(0, fieldLength) : fieldString;
    }

    /**
     * Return a list of Strings from list1 that are not also in list2.
     * @param list1
     * @param list2
     * @return List<String>
     */
    public static List<String> difference(List<String> list1, List<String> list2) {

      List<String> result = VaultCollections.newList();
      Iterator<String> iter1 = list1.iterator();

      while (iter1.hasNext()) {

        String item1 = iter1.next();
        Iterator<String> iter2 = list2.iterator();
        boolean found = false;

        while (iter2.hasNext()) {
          String item2 = iter2.next();
          if (item1.equals(item2)) {
            found = true;
            break;
          }
        }

        if (!found) {
          result.add(item1);
        }
      }

      return result;
    }

  /**
   * Return a list of Strings from set1 that are not also in set2.
   * @param set1 - Set<String>
   * @param set2 - Set<String>
   * @return List<String>
   */
    public static List<String> difference(Set<String> set1, Set<String> set2) {
      return difference(toList(set1), toList(set2));
    }

  /**
   * Return a List<String> containing the elements of a Key<String>
   * @param set
   * @return list
   */
    public static List<String> toList(Set<String> set) {
      List<String> list = VaultCollections.newList();
      for (String item : set) {
        list.add(item);
      }
      return list;
    }

    /**
     * Return a Vault UI URL to the Object Record in the format:
     *    e.g.: https://sb-galderma-galderma-sandbox.veevavault.com/ui/#object/pmf__c/V4400000000A001
     * @param objectName
     * @param recordID
     * @return
     */
    public static String getObjectRecordURL(String objectName, String recordID) {
      StringBuilder sbURL = new StringBuilder();
      sbURL
        .append("https://")
        .append((getVaultDomain()))
        .append("/ui/#object/")
        .append((objectName)).append("/")
        .append(recordID);
      return sbURL.toString();
    }

    /**
     * Return the Domain part of the vault's URL.
     * @return - String.
     */
    public static String getVaultDomain() {
      return getPmfParameters().getValue("vaultDomain", JsonValueType.STRING);
    }

    private static JsonObject getPmfParameters() {
      JsonService jsonSerice = ServiceLocator.locate(JsonService.class);
      QueryService queryService = ServiceLocator.locate(QueryService.class);
      QueryResponse queryResponse = queryService.query(
        "select parameters__c from vproc_parameter_set__c where name__v = 'pmf'"
      );
      QueryResult queryResult = queryResponse.streamResults().iterator().next();
      JsonData jsonData = jsonSerice.readJson(queryResult.getValue("parameters__c", ValueType.STRING));
      return jsonData.getJsonObject();
    }

}