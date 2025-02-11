package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.StringUtils;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.query.QueryExecutionResponse;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;

/**
 *  This class manages records of Object "Agenda Item" (agenda_item__c).
 *
 *  Public methods include:
 *    - shiftDownAfter
 *    - shiftUpBefore
 *    - compressAgendaItemOrdering
 *    - updateStartEndTimes
 *    - saveChanged Records
 *
 *  Time manipulation logic in this class depend on a time string that's in a valid
 *  format, either in 12-hour or 24-hour format.  The Agenda (agenda__c) object in Vault
 *  has a validation rule that enforces this requirement.  The full text of the validation
 *  rule is:
 *
 *    IsBlank(meeting_time__c)
 *     ||
 *    Regex(meeting_time__c, "^0*[1-9]:[0-5][0-9] (AM|PM).*$")
 *     ||
 *    Regex(meeting_time__c, "^1[0-2]:[0-5][0-9] (AM|PM).*$")
 *     ||
 *    Regex(meeting_time__c, "^0[0-9]:[0-5][0-9].*$")
 *     ||
 *    Regex(meeting_time__c, "^1[0-9]:[0-5][0-9].*$")
 *     ||
 *    Regex(meeting_time__c, "^2[0-3]:[0-5][0-9].*$")
 *
 */

@UserDefinedClassInfo
public class AgendaItemsList {

    private static final int TIME_FORMAT_12 = 1;
    private static final int TIME_FORMAT_24 = 2;

    private List<AgendaItem> agendaItems;
    private int timeFormat = 0;

    public AgendaItemsList(String agendaId) {

      this.agendaItems = VaultCollections.newList();

      // the 'order by' clause is crucial to the operation of methods in this class
      QueryExecutionResponse response = QueryUtil.query(
        "select id, order__c, duration__c, start_time__c, end_time__c from agenda_item__c" +
          " where agenda__c = '"+agendaId+"'" +
          "order by order__c asc"
      );
      Iterator<QueryExecutionResult> iterator = response.streamResults().iterator();

      while (iterator.hasNext()) {
        QueryExecutionResult result = iterator.next();
        this.agendaItems.add(
          new AgendaItem(
            result.getValue("id", ValueType.STRING),
            result.getValue("order__c", ValueType.NUMBER),
            result.getValue("duration__c", ValueType.NUMBER),
            result.getValue("start_time__c", ValueType.STRING),
            result.getValue("end_time__c", ValueType.STRING)
          )
        );
      }
    }

    /**
     * An item has been inserted (or moved up) in the Agenda List.  Push the order
     * of Items after the inserted one down the list (make Order higher).
     */
    public void shiftDownAfter(String agendaItemId) {

      AgendaItem afterItem = this.find(agendaItemId);
      BigDecimal afterOrder = afterItem.getOrder();

      if (afterOrder == null) {
        return;  //this should never happen; just to be safe
      }
      int order = afterOrder.intValue();

      Iterator<AgendaItem> iter = this.agendaItems.iterator();

      while (iter.hasNext()) {
        AgendaItem item = iter.next();
        if (item.getOrder() == null) {
          continue;
        }
        if (item.equals(afterItem)) {
          continue;
        }
        if (item.getOrder().intValue() < order) {
          continue;
        }
        if (item.getOrder().intValue() == order) {
          item.setOrder(order + 1);
          order++;
        } else {
          break;
        }
      }

    }  // end shiftDownAfter()

  /**
   * An item has been moved down (Order made higher) in the Agenda List.  Push the order
   * of Items before the moved one up the list (make their Order lower).
   */
  public void shiftUpBefore(String agendaItemId) {

    AgendaItem beforeItem = this.find(agendaItemId);
    BigDecimal beforeOrder = beforeItem.getOrder();

    if (beforeOrder == null) {
      return;  //this should never happen; just to be safe
    }
    int order = beforeOrder.intValue();

    for (int i=this.agendaItems.size() - 1; i > -1; i--) {
      AgendaItem item = this.agendaItems.get(i);
      if (item.getOrder() == null) {
        continue;
      }
      if (item.equals(beforeItem)) {
        continue;
      }
      if (item.getOrder().intValue() > order) {
        continue;
      }
      if (item.getOrder().intValue() == order) {
        item.setOrder(order - 1);
        order--;
      } else {
        break;
      }
    }

  }  // end shiftUpBefore()

  // Make the order numbers contiguous -- remove gaps between numbers.
  public void compressAgendaItemOrdering() {

    Iterator<AgendaItem> iter = this.agendaItems.iterator();

    int order = 1;

    while (iter.hasNext()) {
      AgendaItem item = iter.next();
      BigDecimal bdOrder = item.getOrder();
      if (bdOrder == null) {
        continue;
      }
      if (bdOrder.intValue() > order) {
        item.setOrder(order);
      }
      order++;
    }
  }

  /**
   * Set/update the start/end times for relevant Agenda items.
   *
   * @param agendaMeetingTime - String.  The meeting time field from the agenda__c Object.
   *   The meeting time is assumed to be in format "h:mm [AM|PM]".  This is validated by
   *   a validation rule in the agenda__c object.
   */
    public void updateStartEndTimes(String agendaMeetingTime) {

      this.agendaItems.sort(new AgendaItemComparator());

      Iterator<AgendaItem> iter = this.agendaItems.iterator();

      double dblTime = 0d;

      if (agendaMeetingTime != null) {
        this.timeFormat = getTimeFormat(agendaMeetingTime.toUpperCase());
        dblTime = this.timeStringToDouble(agendaMeetingTime);
      }

      boolean stopCalculating = false;

      while (iter.hasNext()) {

        AgendaItem item = iter.next();

        if (agendaMeetingTime == null) {
          item.setStartTime(null);
          item.setEndTime(null);
          continue;
        }

        if (item.getOrder() == null) {
          item.setStartTime(null);
          item.setEndTime(null);
          continue;
        }

        if (item.getDuration() == null) {
          stopCalculating = true;
        }

        if (stopCalculating) {
          item.setStartTime(null);
          item.setEndTime(null);
        } else {
          item.setStartTime(timeDoubleToString((dblTime)));
          dblTime += (double) (item.getDuration().intValue() / 60d);
          item.setEndTime(timeDoubleToString(dblTime));
        }

      }

      return;
    }

    public void saveChangedRecords() {
      List<Record> records = VaultCollections.newList();
      Iterator<AgendaItem> iter  = this.agendaItems.iterator();

      while (iter.hasNext()) {
        AgendaItem item = iter.next();
        if (item.isChanged()) {
          records.add(item.getRecord());
        }
      }
      if (records.size() > 0) {
        Util.batchSaveRecords(records);
      }
    }

    private AgendaItem find(String agendaItemId) {
      Iterator<AgendaItem> iter = this.agendaItems.iterator();
      while (iter.hasNext()) {
        AgendaItem item = iter.next();
        if (item.getRecordId().equals(agendaItemId)) {
          return item;
        }
      }
      return null;  // this should never happen
    }

    // Meeting time will be in 12-hour format ("h:mm [AM|PM] ...") or 24-hour format ("hh:mm ...").
    // Examples:
    //   - "10:30 AM ET" or "10:30 CET"
    //   - "1:30 PM ET" or "13:30 CET"
    // Return a numeric representation of the time of day, for example:
    //    1:45 PM ==> 13.75
    private double timeStringToDouble(String stringTime) {

      // split into [hh:mm, AM, ET]
      String[] timeParts = StringUtils.split(stringTime, " ");

      // split into [hh, mm]
      String[] hhmm = StringUtils.split(timeParts[0], ":");

      // get minutes as fraction of an hour
      double mm = ((double)Integer.parseInt(hhmm[1])) / 60;

      // get hh, and add 12 if in the afternoon
      double hh = (double) Integer.parseInt(hhmm[0]);
      if (this.timeFormat == TIME_FORMAT_12) {
        if (hh < 12.0 && timeParts[1].equals("PM")) {
          hh += 12.0;
        }
      }

      return hh + mm;
    }

    // convert numeric representation of time to a String, for example:
    //   13.75 ==> "1:45"
    private String timeDoubleToString(double time) {

      BigDecimal decTime = new BigDecimal(String.valueOf(time));

      int hour = decTime.intValue();

      int min = (int) Math.round(decTime.subtract(new BigDecimal(hour)).multiply(new BigDecimal(60)).doubleValue());

      if (min == 60) {
        min = 0;
        hour++;
      }

      if (this.timeFormat == TIME_FORMAT_12) {
        if (hour >= 13) {
          hour -= 12;
        }
      }

      return String.valueOf(hour) + ":" + (min < 10 ? "0" : "") + String.valueOf(min);
    }

    private int getTimeFormat(String agendaMeetingTime) {
      if (agendaMeetingTime == null)
        return 0;
      if (agendaMeetingTime.contains("AM") || agendaMeetingTime.contains("PM")) {
        return TIME_FORMAT_12;
      } else {
        return TIME_FORMAT_24;
      }
    }

}