import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IChange
import com.agile.px.IUpdateEventInfo

import java.util.logging.Logger

void invokeScript(IBaseScriptObj obj) {
    IUpdateEventInfo eventInfo = obj.PXEventInfo
    Logger logger = Logger.getLogger('insight.sun.ams.ValidateImplementationDate')

    IChange change = eventInfo.dataObject
    Date createDate = change.getValue('dateOriginated')

    logger.info("Creation date for AAS $change.name is $createDate")

    def implDate = createDate + 30
    logger.info("Implementation date for AAS $change.name should be on or after $implDate")

    Integer atrId =  change.agileClass.getAttribute('Page Three.Implementation Schedule Date').id
    def dirtyCell = eventInfo.getCell(atrId)

    if(dirtyCell) {
        Date newImplDate =dirtyCell.getValue()
        logger.info("New Implementation date for AAS $change.name is being set to $newImplDate")

        if (implDate >= newImplDate) {
            throw new Exception("Implementation date should be at least one month after AAS creation date")
        }
    }
}