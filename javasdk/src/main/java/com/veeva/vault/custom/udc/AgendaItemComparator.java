package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;

import java.math.BigDecimal;

/**
 *  For sorting a list of Agenda Items by Order.
 */

@UserDefinedClassInfo
class AgendaItemComparator implements java.util.Comparator<AgendaItem> {
    @Override
    public int compare(AgendaItem a, AgendaItem b) {

        BigDecimal orderA = a.getOrder();
        BigDecimal orderB = b.getOrder();

        if (orderA == null || orderB == null) {
            return 0;
        } else {
            return orderA.intValue() - orderB.intValue();
        }
    }
}