import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.ChangeConstants
import com.agile.api.IAgileSession
import com.agile.api.IChange
import com.agile.api.IUser
import com.agile.px.ICreateEventInfo
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

void invokeScript(IBaseScriptObj obj) {
    try {
        Logger logger = JLogger.getLogger('insight.sun.ams.CheckCreateAccess')
        ICreateEventInfo info = obj.PXEventInfo
        IChange aas = info.dataObject
        IAgileSession session = obj.agileSDKSession
        IUser user = session.currentUser

        aas.getTables(ChangeConstants.TABLE_WORKFLOW).getValue(ChangeConstants.ATT_WORKFLOW_SIGNOFF_USER)
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to propagate attributes from AAS to Artwork', ex)
        throw (ex)
    }
}