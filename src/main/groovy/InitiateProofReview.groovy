import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.ExceptionConstants
import com.agile.api.IAgileList
import com.agile.api.IChange
import com.agile.api.IItem
import com.agile.px.IObjectEventInfo
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.CLASS_CHANGE_ORDERS_CLASS
import static com.agile.api.ItemConstants.ATT_TITLE_BLOCK_LATEST_RELEASE_DATE
import static com.agile.api.ItemConstants.TABLE_PENDINGCHANGES

void invokeScript(IBaseScriptObj obj) {
    Logger logger = JLogger.getLogger('insight.sun.ams.InitiateProofReview')
    try {

        IObjectEventInfo eventInfo = obj.PXEventInfo
        IItem aw = eventInfo.dataObject
        ExceptionConstants
        if (aw.getValue(ATT_TITLE_BLOCK_LATEST_RELEASE_DATE) == null)
            throw new Exception('Artwork should have been released before it can be sent to Printing Vendors')
        if (aw.getTable(TABLE_PENDINGCHANGES).referentIterator.find {
            IChange c -> c.agileClass.isSubclassOf(obj.agileSDKSession.adminInstance.getAgileClass(CLASS_CHANGE_ORDERS_CLASS))
        })
            throw new Exception('Artwork is currently under revision.')
        IAgileList ml = aw.getCell(1566).value
        ml.selection.each {
            // it.
        }

    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to propagate attributes from AAS to Artwork', ex)
        throw (ex)
    }
}


