import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IChange
import com.agile.api.IItem
import com.agile.api.IRow
import com.agile.px.IObjectEventInfo
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.CommonConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE
import static insight.sun.ams.AMSConfiguration.logger

void invokeScript(IBaseScriptObj obj) {
    try {
        Logger logger = JLogger.getLogger('insight.sun.ams.DeleteOnRejection')

        IObjectEventInfo eventInfo = obj.PXEventInfo
        def aas = eventInfo.dataObject
        removeAttachments(aas)
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to remove process files on Artworks attached to AAS', ex)
        throw (ex)
    }
}

void removeAttachments(IChange aas){
    aas.getTable(TABLE_AFFECTEDITEMS).referentIterator.each { IItem aw ->
        aw.setRevision(aas)
        def tblAttachments = aw.attachments
        List<IRow> rows = tblAttachments.findAll { IRow row ->
            row.getValue(ATT_ATTACHMENTS_ATTACHMENT_TYPE).toString() == 'Process File'
        }

        rows.each { IRow row ->
            tblAttachments.removeRow(row)
        }
    }
}