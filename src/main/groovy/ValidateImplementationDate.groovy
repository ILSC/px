import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IChange
import com.agile.api.IUser
import com.agile.api.IUserGroup
import com.agile.px.IUpdateEventInfo

import java.util.logging.Logger

import static com.agile.api.UserConstants.TABLE_USERGROUP
import static insight.sun.ams.AMSConfiguration.loadCfg
import static insight.sun.ams.AMSConfiguration.readKey

void invokeScript(IBaseScriptObj obj) {
    IUpdateEventInfo eventInfo = obj.PXEventInfo
    Logger logger = Logger.getLogger('insight.sun.ams.ValidateImplementationDate')

    IUser user = obj.agileSDKSession.currentUser

    loadCfg()
    def impDateVal = readKey('impDateVal')

    if (isMemberOf(user, impDateVal.overrideGrpName))
        return

    IChange change = eventInfo.dataObject

    Integer atrId = change.agileClass.getAttribute('Page Three.Implementation Schedule Date').id
    def dirtyCell = eventInfo.getCell(atrId)

    if (dirtyCell) {
        Date createDate = change.getValue('dateOriginated')
        logger.info("Creation date for AAS $change.name is $createDate")

        def implDate = createDate + impDateVal.offsetInDays
        logger.info("Implementation date for AAS $change.name should be on or after $implDate")

        Date newImplDate = dirtyCell.getValue()
        logger.info("New Implementation date for AAS $change.name is being set to $newImplDate")

        if (implDate >= newImplDate)
            throw new Exception("Implementation date should be at least $impDateVal.offsetInDays days after AAS creation date")
    }
}

boolean isMemberOf(IUser user, String groupName) {
    user.getTable(TABLE_USERGROUP).referentIterator.find { IUserGroup ug -> ug.name == groupName }
}