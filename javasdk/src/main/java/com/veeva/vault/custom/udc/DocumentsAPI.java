package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.http.HttpMethod;
import com.veeva.vault.sdk.api.job.JobLogger;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;

import java.util.List;

/*
 This class contains methods that wrap the Vault Documents API in a convenient way.
 */

@UserDefinedClassInfo()
public class DocumentsAPI {

  static final String APIVersion = "v21.2";

  private boolean succeeded;
  private String errorType;
  private String errorMessage;

  private String connection;

  private Logger logger = new Logger();

  // use localHttpRequest to access the api
  public DocumentsAPI() {
    this.connection = null;
  }

  // Use a connection to access the api
  public DocumentsAPI(String connection) {
    this.connection = connection;
  }

  public DocumentsAPI setJobLogger(JobLogger jobLogger) {
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
   * Execute a User Action (workflow or state change).
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

    HttpCallout httpCallout = new HttpCallout(connection);

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

}

