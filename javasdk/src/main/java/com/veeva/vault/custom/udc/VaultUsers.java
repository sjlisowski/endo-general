package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.util.Iterator;
import java.util.Map;

/**
 *  Collects information about all users both active and inactive, and provides methods
 *  for extracting specific data about a user based on a given user id.
 */

@UserDefinedClassInfo
public class VaultUsers {

    private Map<String, QueryExecutionResult> usersMap = VaultCollections.newMap();

    public VaultUsers() {
      Iterator<QueryExecutionResult> iter =  QueryUtil.query(
          "select " +
          "    id, " +
          "    first_name__sys, " +
          "    last_name__sys, " +
          "    status__v " +
          "  from user__sys"
      ).streamResults().iterator();

      while (iter.hasNext()) {
          QueryExecutionResult result = iter.next();
          String userId = result.getValue("id", ValueType.STRING);
          usersMap.put(userId, result);
      }
    }

    public String getUserName(String userId) {
        QueryExecutionResult userData = usersMap.get(userId);
        String firstName = userData.getValue("first_name__sys", ValueType.STRING);
        String lastName = userData.getValue("last_name__sys", ValueType.STRING);
        return firstName + " " + lastName;
    }
}