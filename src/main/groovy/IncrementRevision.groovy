import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.*

import static com.agile.api.ChangeConstants.ATT_AFFECTED_ITEMS_ITEM_NUMBER
import static com.agile.api.ChangeConstants.ATT_AFFECTED_ITEMS_NEW_REV
import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.ChangeConstants.ATT_AFFECTED_ITEMS_OLD_REV
import static com.agile.px.EventConstants.EVENT_SAVE_AS_OBJECT
import static com.agile.px.EventConstants.EVENT_TRIGGER_POST
import static com.agile.px.EventConstants.EVENT_UPDATE_TABLE
import static com.agile.px.EventConstants.DIRTY_ROW_ACTION_ADD

/*
 * Automatically set the revision field when adding an item to the affected item tab- Post trigger
 */

void invokeScript(IBaseScriptObj obj) {
    try {
        IEventInfo info = obj.PXEventInfo
        if (info.eventType == EVENT_UPDATE_TABLE && info.eventTriggerType == EVENT_TRIGGER_POST) {
            handleUpdateEvent(info, obj)
        }else if(info.eventType == EVENT_SAVE_AS_OBJECT && info.eventTriggerType == EVENT_TRIGGER_POST){
            handleSaveAsEvent(info, obj)
        }

    } catch (Exception e) {
        e.printStackTrace()
    }
}

void handleSaveAsEvent(ISaveAsEventInfo info, IBaseScriptObj obj){
    IChange newAAS = obj.agileSDKSession.getObject(info.newSubclassId, info.newNumber)
    newAAS.getTable(TABLE_AFFECTEDITEMS).each{IRow row ->
        def oldRev = row.getValue(ATT_AFFECTED_ITEMS_OLD_REV)?:''

        String newRev = oldRev ? getNewRev(oldRev.toString()) : 'A'
        row.setValue(ATT_AFFECTED_ITEMS_NEW_REV, newRev)

        obj.logMonitor("Successful set rev to $newRev for ${row.getValue(ATT_AFFECTED_ITEMS_ITEM_NUMBER)}");
    }
}

void handleUpdateEvent(IUpdateTableEventInfo info, IBaseScriptObj obj){
    IChange aas = info.dataObject
    List<IRow> afRows = aas.getTable(TABLE_AFFECTEDITEMS).collect { IRow r -> r }

    info.table.iterator().each { IEventDirtyRowUpdate dirtyRow ->
        if (dirtyRow.action == DIRTY_ROW_ACTION_ADD) {
            IRow row = afRows.find{it.rowId == dirtyRow.rowId}
            incrementRev(row, obj)
        }
    }
}

private void incrementRev(IRow row, IBaseScriptObj obj) {
    def oldRev = row.getValue(ATT_AFFECTED_ITEMS_OLD_REV) ?: ''

    String newRev = oldRev ? getNewRev(oldRev.toString()) : 'A'
    row.setValue(ATT_AFFECTED_ITEMS_NEW_REV, newRev)

    obj.logMonitor("Successful set rev to $newRev for ${row.getValue(ATT_AFFECTED_ITEMS_ITEM_NUMBER)}");
}

private String getNewRev(String oldRev) {
    String newRev = null
    if (oldRev.matches(/^\d*$/))
        newRev = (oldRev.toInteger() + 1).toString()
    else if (oldRev.matches(/^.*\d$/)) {
        newRev = oldRev[0..(oldRev.length()-2)] + (oldRev[-1..-1].toInteger() + 1)
    } else if (oldRev.matches(/^[A-Z]*$/)){
        char[] chrAry = oldRev.chars
        (oldRev.length()..1).any { idx ->
            char chr = oldRev.charAt(idx - 1)
            if (chr < 90) {
                chrAry[idx - 1] = (char)(chr + 1)
                newRev = chrAry.toString()
                true
            } else {
                if(idx==1){
                    chrAry[idx - 1] = (char)65
                    newRev = 'A' + chrAry.toString()
                    true
                }
                chrAry [idx - 1] =(char) 65
                false
            }
        }
    }
    newRev
}