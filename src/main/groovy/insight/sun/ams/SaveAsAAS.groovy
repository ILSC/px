package insight.sun.ams

import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IChange
import com.agile.api.IRow
import com.agile.px.IEventInfo
import com.agile.px.ISaveAsEventInfo

import static com.agile.api.ChangeConstants.ATT_AFFECTED_ITEMS_ITEM_NUMBER
import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.px.EventConstants.EVENT_SAVE_AS_OBJECT
import static com.agile.px.EventConstants.EVENT_TRIGGER_POST

class SaveAsAAS {
    void invokeScript(IBaseScriptObj obj) {
        try {
            IEventInfo info = obj.PXEventInfo
            if (info.eventType == EVENT_SAVE_AS_OBJECT && info.eventTriggerType == EVENT_TRIGGER_POST) {
                handleSaveAsEvent(info as ISaveAsEventInfo, obj)
            }

        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    void handleSaveAsEvent(ISaveAsEventInfo info, IBaseScriptObj obj) {
        IChange newAAS = obj.agileSDKSession.getObject(info.newSubclassId, info.newNumber) as IChange

        newAAS.getTable(TABLE_AFFECTEDITEMS).each { IRow row ->
            RevHelper.updateAFRow(newAAS, row)
            String num = row.getValue(ATT_AFFECTED_ITEMS_ITEM_NUMBER)
            obj.logMonitor("Successfully set revision and lifecycle phase for $num")
        }

        String category = newAAS.getValue('changeCategory')?.toString(),
               relType = newAAS.getValue('TypeOfRelease')?.toString()
        if (category == 'Commercial' && relType == 'Existing') {
            def rows = newAAS.attachments.collect { it }
            rows.each { r ->
                newAAS.attachments.removeRow(r as IRow)
            }
        }
    }
}