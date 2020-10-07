import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.IObjectEventInfo

import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.CommonConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE
import static com.agile.api.DataTypeConstants.*

void invokeScript(IBaseScriptObj obj) {
    IObjectEventInfo eventInfo = obj.PXEventInfo
    IChange change = eventInfo.dataObject

    IStatus status = change.getStatus()
    if (status.toString() == 'CFT Review') {

    }
}

def awList = getArtWorks(change)

if (awList?.size() < 1) {
    def ex = new Exception(" Artwork not found")
    throw ex
}
awList.each { aw ->
    removeProcessFile(pr, aw)
}

void removeProcessFile(IChange pr, IItem aw) {
    def type = 'Proof'
    def types = aw.attachments.collect { IRow r ->

        getVal(r, ATT_ATTACHMENTS_ATTACHMENT_TYPE).toString()
    }
    if (!(type == types)) {

    }
}


List<IItem> getArtWorks(IChange change) {
    change.getTable(TABLE_AFFECTEDITEMS).referentIterator.collect { it }
}

def getVal(IAgileObject obj, def atrId) {
    ICell cell = obj.getCell(atrId)
    switch (cell.dataType) {
        case TYPE_INTEGER:
        case TYPE_DOUBLE:
        case TYPE_STRING:
        case TYPE_DATE:
        case TYPE_MONEY:
            return cell.value
        case TYPE_SINGLELIST:
            return cell.value.toString()
        case TYPE_MULTILIST:
            IAgileList mList = cell.value
            return mList.toString().split(';')
        default:
            return cell.value
    }
}





