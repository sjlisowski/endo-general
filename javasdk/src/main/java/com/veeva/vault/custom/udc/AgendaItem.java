package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;

import java.math.BigDecimal;

/**
 * This class models the 'Agenda Item' (agenda_item__c) Object and is used for
 * managing updates to the item's Order (order__c), 'Start Time' (start_time__c)
 * and 'End Time' (end_time__c).
 */

@UserDefinedClassInfo
public class AgendaItem {

    private String recordId;
    private BigDecimal order;
    private BigDecimal duration;

    private String startTime;
    private String endTime;

    private boolean changed;

    public AgendaItem(String recordId, BigDecimal order, BigDecimal duration, String startTime, String endTime) {
      this.recordId = recordId;
      this.order = order;
      this.duration = duration;
      this.startTime = startTime;
      this.endTime = endTime;
      this.changed = false;
    }

    public String getRecordId() {
      return this.recordId;
    }

    public BigDecimal getOrder() {
      return this.order;
    }

    public void setOrder(BigDecimal order) {
      if (this.order == null || order.intValue() != this.order.intValue()) {
        // note: in the context of this App, the incoming value will never be null.
        this.order = new BigDecimal(order.intValue());
        this.changed = true;
      }
    }
    public void setOrder(int order) {
      this.setOrder(new BigDecimal(order));
    }

    public BigDecimal getDuration() {
      return this.duration;
    }

    public void setStartTime(String startTime) {
      if (
        (this.startTime == null && startTime != null ) ||
        (this.startTime != null && startTime == null) ||
        (startTime != null && !startTime.equals(this.startTime))
      ) {
        this.startTime = startTime;
        this.changed = true;
      }
    }

    public void setEndTime(String endTime) {
      if (
        (this.endTime == null && endTime != null ) ||
        (this.endTime != null && endTime == null) ||
        (endTime != null && !endTime.equals(this.endTime))
      ) {
        this.endTime = endTime;
        this.changed = true;
      }
    }

    public boolean isChanged() {
        return this.changed;
    }

    @Override
    public boolean equals(Object otherItem) {
      return ((AgendaItem) otherItem).recordId.equals(this.recordId);
    }

    public Record getRecord() {
      RecordService recordService = ServiceLocator.locate(RecordService.class);
      Record record = recordService.newRecordWithId("agenda_item__c", this.recordId);
      record.setValue("order__c", this.order);
      record.setValue("start_time__c", this.startTime);
      record.setValue("end_time__c", this.endTime);
      return record;
    }
}