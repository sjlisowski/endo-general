package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;

/**
 * What this does...
 */

@UserDefinedClassInfo
public class ErrorType {
    public static final String OPERATION_FAILED = "OPERATION_FAILED";
    public static final String UPDATE_DENIED = "UPDATE_DENIED";
    public static final String INVALID_DOCUMENT = "INVALID_DOCUMENT";
    public static final String DUPLICATE_DOCUMENT = "DUPLICATE_DOCUMENT";
}