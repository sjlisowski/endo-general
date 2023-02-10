package com.veeva.vault.custom.actions.document;

import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.role.DocumentRole;
import com.veeva.vault.sdk.api.role.DocumentRoleService;
import com.veeva.vault.sdk.api.role.DocumentRoleUpdate;
import com.veeva.vault.sdk.api.role.GetDocumentRolesResponse;

import java.util.List;

/**
 * This action is used to clear all users out of the Approver role for a Document.
 */

@DocumentActionInfo(
  label = "Clear Document Role 'Approver'"
)
public class ClearDocumentRoleApprover implements DocumentAction {
	
    public void execute(DocumentActionContext documentActionContext) {

			DocumentRoleService documentRoleService = ServiceLocator.locate(DocumentRoleService.class);

		 	List<DocumentVersion> documentVersions = documentActionContext.getDocumentVersions();

			String roleName = "approver__c";

			GetDocumentRolesResponse getDocumentRolesResponse = documentRoleService.getDocumentRoles(
				documentVersions, roleName
			);

			DocumentRole documentRole = getDocumentRolesResponse.getDocumentRole(documentVersions.get(0));

			List<String> usersInRole = documentRole.getUsers();

			if (usersInRole.size() > 0) {

				DocumentRoleUpdate documentRoleUpdate = documentRoleService.newDocumentRoleUpdate(
					roleName, documentVersions.get(0)
				);

				documentRoleUpdate.removeUsers(usersInRole);

				documentRoleService.batchUpdateDocumentRoles(VaultCollections.asList(documentRoleUpdate))
					.rollbackOnErrors()
					.execute();
			}

    }

	public boolean isExecutable(DocumentActionContext documentActionContext) {
	    return true;
	}
}