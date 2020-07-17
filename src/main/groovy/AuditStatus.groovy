import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.ISignOffEventInfo
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.CommonConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE
import static com.agile.api.DataTypeConstants.*
import static com.agile.api.ExceptionConstants.API_SEE_ROOT_CAUSE
import static insight.sun.ams.AMSConfiguration.loadCfg
import static insight.sun.ams.AMSConfiguration.readKey

void invokeScript(IBaseScriptObj obj) {
    Logger logger = JLogger.getLogger('insight.sun.ams.AuditStatus')
    try {
        logger.info('Loading AMS Configuration')
        loadCfg()
        ISignOffEventInfo eventInfo = obj.PXEventInfo
        logger.info('Getting AAS from event info')
        IChange aas = eventInfo.dataObject
        logger.info("Performing Audit on AAS $aas.name")

        def errors = []
        aas.audit(false).values().flatten().each { APIException e ->
            if (e.errorCode == API_SEE_ROOT_CAUSE) {
                if (e.rootCause)
                    errors << e.rootCause.message
            } else{
                errors << e.message
            }
        }

        if (errors) {
            logger.info("Found ${errors.size()} issues in $aas.name")
            logger.info(errors.join(''))
            def ex = new Exception("$aas.agileClass.name $aas.name cannot be promoted to next status. Please perform audit Status to get details of the issues preventing status change. ${errors.join()}")
            logger.log(Level.SEVERE, ex.message, ex)
            throw ex
        }

        logger.info("Getting attached artwork from $aas.name")
        IItem aw = getAW(aas)

        if (!aw) {
            def ex = new Exception("$aas.agileClass.name $aas.name cannot be promoted to next status. Couldn't find any atrwork attached to the AAS.")
            logger.log(Level.SEVERE, ex.message, ex)
            throw ex
        }

        logger.info("Validating attributes on $aw.name and $aas.name")
        validateAttrs(aw, aas, logger)

        logger.info("Validating attachments on $aw.name")
        validateAttachments(aas, aw, logger)
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to perform audit', ex)
        throw (ex)
    }
}

boolean validateAttrs(IItem aw, IChange aas, Logger logger) {
    readKey('propagateAttrs')?.each { atr ->
        if (getVal(aw, atr.aw) != getVal(aas, atr.aas)) {
            logger.info("Value for attribute $atr.aw not matching")
            def ex = new Exception("$aas.agileClass.name $aas.name cannot be promoted to next status. Value for attribute $atr.aw on artwork is not matching with AAS")
            logger.log(Level.SEVERE, ex.message, ex)
            throw ex
        }
    }
    return true
}

void validateAttachments(IChange aas, IItem aw, Logger logger) {
    def mfgLoc = getVal(aas, "Page Three.Manufacturing Location").find { true }.toString()
    logger.info("Looking up rule for status $aas.status.name and manufacturing location $mfgLoc")
    def rule = readKey('validateAttachments')?.find { it.step == aas.status.name && mfgLoc in it.mfgLocations }
    if (rule) {
        def types = aw.attachments.collect { IRow r ->
            logger.info("reading type from attachment row")
            getVal(r, ATT_ATTACHMENTS_ATTACHMENT_TYPE).toString()
        }
        logger.info("Found files of type $types attached to $aw.name, $aw.revision")
        rule.reqTypes.each { type ->
            logger.info("Checking for file type $type on $aw.name, $aw.revision")
            if (!(type in types)) {
                throw new Exception("$aas.agileClass.name $aas.name cannot be promoted to next status. Attachment of type $type was not found on artwork $aw.name.")
            }
        }
    } else {
        logger.info("No rule defined for status $aas.status.name and manufacturing location $mfgLoc")
    }
}

IItem getAW(IChange aas) {
    aas.getTable(TABLE_AFFECTEDITEMS).referentIterator.find { true }
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
