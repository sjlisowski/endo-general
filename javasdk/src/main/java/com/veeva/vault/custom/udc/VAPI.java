package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.http.HttpMethod;
import com.veeva.vault.sdk.api.job.JobLogger;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;

import java.math.BigDecimal;
import java.util.List;

/*
 This class contains methods that wrap the Vault API in a convenient way.

 Methods in this class do not throw exceptions.  Successful completion of
 a method is determined by calling the failed() method.

 Example usage:

      VAPI vapi = new VAPI("local_connection__c");
      vapi
        .setJobLogger(logger)
        .executeUserAction(docVersionId, "expiration_pending_autostart", workflowStartCriteria);
      if (vapi.failed()) {
        ...
      }

 Methods in this class include:
   - cancelWorkflowTasks: initiate workflow actions on one or more workflows - cancel tasks
   - executeUserAction: execute a document lifecycle user action
   - query: execute a Vault API query
 */

@UserDefinedClassInfo()
public class VAPI {

  static final String APIVersion = "v21.3";

  private boolean succeeded;
  private String errorType;
  private String errorMessage;

  private String connection;

  private Logger logger = new Logger();

  // use localHttpRequest to access the api
  public VAPI() {
    this.connection = null;
  }

  // Use a connection to access the api
  public VAPI(String connection) {
    this.connection = connection;
  }

  public VAPI setJobLogger(JobLogger jobLogger) {
    //replace the default Logger with a new Logger that will include job logs
    this.logger = new Logger(jobLogger);
    return this;
  }

  public boolean failed() {
    return !this.succeeded;
  }

  public String getErrorType() {
    return this.errorType;
  }

  public String getErrorMessage() {
    return this.errorMessage;
  }

  /**
   * Initiate workflow actions on one or more workflows - cancel tasks.
   * Return the initiated Job ID as type 'long'.
   * @param taskIds - List<String> - list of one or more taskIds
   * @return long - initiated Job ID
   */
  public BigDecimal cancelWorkflowTasks(List<String> taskIds) {

    HttpCallout httpCallout = new HttpCallout(this.connection);
    List<HttpParam> params = VaultCollections.newList();
    HttpResult httpResult;

    this.succeeded = true;

    String path = "/api/"+APIVersion+"/object/workflow/actions/canceltasks";

    params.add(new HttpParam("task_ids", Util.stringifyList(taskIds, "")));

    httpResult = httpCallout.requestJson(HttpMethod.POST, path.toString(), params, this.logger);

    if (httpResult.isError()) {
      this.succeeded = false;
      this.errorType = httpResult.getErrorType();
      this.errorMessage = httpResult.getErrorMessage();
      return null;
    }

    return httpResult
            .getJsonObject()
            .getValue("data", JsonValueType.OBJECT)
            .getValue("job_id", JsonValueType.NUMBER);
  }

  /**
   * Execute a Document Lifecycle User Action (workflow or state change).
   *
   * @param docVersionId of the document
   * @param actionLabel  label of the action as it appears on the actions menu in the UI
	 * @param params  Optional through overloads. for entry criteria fields
   */
  public void executeUserAction(String docVersionId, String actionLabel, List<HttpParam> params) {

    DocVersionIdParts docVersionIdParts = new DocVersionIdParts(docVersionId);
    HttpResult httpResult;

    this.succeeded = true;

    StringBuilder path = new StringBuilder();
    path
      .append("/api/").append(APIVersion).append("/objects/documents/")
      .append(docVersionIdParts.id)
      .append("/versions/")
      .append(docVersionIdParts.major)
      .append("/")
      .append(docVersionIdParts.minor)
      .append("/lifecycle_actions");

    HttpCallout httpCallout = new HttpCallout(this.connection);

    httpResult = httpCallout.requestJson(HttpMethod.GET, path.toString(), this.logger);

    if (httpResult.isError()) {
      this.succeeded = false;
      this.errorType = httpResult.getErrorType();
      this.errorMessage = httpResult.getErrorMessage();
      return;
    }

    String actionName = null;

    JsonArray lifecycleActions = httpResult.getJsonObject().getValue("lifecycle_actions__v", JsonValueType.ARRAY);

    for (int i = 0; i < lifecycleActions.getSize(); i++) {
      JsonObject action = lifecycleActions.getValue(i, JsonValueType.OBJECT);
      String label = action.getValue("label__v", JsonValueType.STRING);
      if (label.equals(actionLabel)) {
        actionName = action.getValue("name__v", JsonValueType.STRING);
        break;
      }
    }

    if (actionName == null) {
      this.succeeded = false;
      this.errorType = ErrorType.OPERATION_FAILED;
      this.errorMessage = "An error occurred accessing Vault API \"Retrieve User Actions\".  " +
        "Unable to find action \"" + actionLabel + "\"";
      return;
    }

    path.append("/").append(actionName);

    httpResult = httpCallout.requestJson(HttpMethod.PUT, path.toString(), params, this.logger);

    if (httpResult.isError()) {
      this.succeeded = false;
      this.errorType = httpResult.getErrorType();
      this.errorMessage = httpResult.getErrorMessage();
    }
  }
  public void executeUserAction(String docVersionId, String actionLabel) {
    List<HttpParam> emptyParamsList = VaultCollections.newList();
    this.executeUserAction(docVersionId, actionLabel, emptyParamsList);
  }

  /**
   * Execute a Vault API query.
   *
   * @param query - String - the query
   */
  public JsonArray executeQuery(String query) {

    HttpCallout httpCallout = new HttpCallout(this.connection);
    List<HttpParam> params = VaultCollections.newList();
    HttpResult httpResult;

    this.succeeded = true;

    String path = "/api/"+APIVersion+"/query";

    params.add(new HttpParam("q", query));

    httpResult = httpCallout.requestJson(HttpMethod.POST, path.toString(), params, this.logger);

    if (httpResult.isError()) {
      this.succeeded = false;
      this.errorType = httpResult.getErrorType();
      this.errorMessage = httpResult.getErrorMessage();
      return null;
    }

    return httpResult.getJsonObject().getValue("data", JsonValueType.ARRAY);
  }

}

