import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.EventConstants
import com.agile.px.IObjectEventInfo
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.CommonConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE
import static com.agile.api.DataTypeConstants.*
import static insight.sun.ams.AMSConfiguration.loadCfg
import static insight.sun.ams.AMSConfiguration.readKey

void invokeScript(IBaseScriptObj obj) {
    Logger logger = JLogger.getLogger('insight.sun.ams.AuditStatus')
    try {
        logger.info('Loading AMS Configuration')
        loadCfg()
        def eventInfo = obj.PXEventInfo
        logger.info('Getting AAS from event info')
        IChange aas = eventInfo.dataObject
        logger.info("Performing Ckeck on AAS $aas.name, status: $aas.status")

        checkAAS(aas, logger)

    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to perform audit', ex)
        throw (ex)
    }
}


void checkAAS(IChange aas, Logger logger) {
    def workflow = aas.workflow.name
    def status = aas.status.name
    def chgCat = getVal(aas, "Cover Page.Change Category")
    List mfgLoc = getVal(aas, "Page Three.*Manufacturing Location") ?: []
    def grid = getVal(aas, "Page Three.Type of Grid")
    def release = getVal(aas, "Page Three.*Type of Release")
    def markets = getVal(aas, "Page Three.*Market")

    logger.info("Looking up rule for workflow $workflow, status $status, change category $chgCat, " +
            "manufacturing location $mfgLoc, type of grid $grid, type of release $release, markets $markets")

    def rules = readKey('checklist')?.findAll { rule ->
        rule.wf == workflow &&
                rule.step == status &&
                rule.chgCat == chgCat &&
                rule.mfg.intersect(mfgLoc) &&
                rule.typeOfGrid == grid &&
                rule.typeOfRelease == release &&
                ((rule.market.in && rule.market.in.intersect(markets)) ||
                        (rule.market.notIn && !rule.market.notIn.intersect(markets)))
    }

    if (rules) {
        rules*.roles.flatten().each{String role->
            aas.relationship.find{

            }
        }
    }
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
            return mList.toString().split(';').toList()
        default:
            return cell.value
    }
}