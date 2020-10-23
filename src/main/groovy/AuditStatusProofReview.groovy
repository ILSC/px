import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.IObjectEventInfo
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.CommonConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE
import static com.agile.api.CommonConstants.TABLE_ATTACHMENTS
import static com.agile.api.DataTypeConstants.*
import static com.agile.api.ExceptionConstants.APDM_MISSINGFIELDS_WARNING
import static com.agile.api.ExceptionConstants.API_SEE_ROOT_CAUSE
import static insight.sun.ams.AMSConfiguration.loadCfg
import static insight.sun.ams.AMSConfiguration.readKey

void invokeScript(IBaseScriptObj obj) {
    Logger logger = JLogger.getLogger('insight.sun.ams.ProofReviewAuditStatus')
    try {
        logger.info('Loading AMS Configuration')
        loadCfg()
        IObjectEventInfo eventInfo = obj.PXEventInfo
        logger.info('Getting change from event info')
        IChange pr = eventInfo.dataObject
        logger.info("Performing Audit on change $pr.name, status: $pr.status")

        auditPR(pr, logger)

    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to perform audit', ex)
        throw (ex)
    }
}

void auditPR(IChange pr, Logger logger) {
    def errors = []
    pr.audit(false).values().flatten().each { APIException e ->
        if (e.errorCode == APDM_MISSINGFIELDS_WARNING) {
            if (!e.message.contains('You have insufficient privileges to resolve this audit issue.'))
                errors << e.errorCode + ':' + e.message
        } else if (e.errorCode == API_SEE_ROOT_CAUSE) {
            if (e.rootCause && !e.message.contains('You have insufficient privileges to resolve this audit issue.') &&
                    !e.rootCause.message.contains('You have insufficient privileges to resolve this audit issue.'))
                errors << e.errorCode + ':' + e.rootCause.message
        } else if (!(e.errorCode in readKey('warningsToSkip'))) {
            errors << e.errorCode + ':' + e.message
        }
    }

    if (errors) {
        logger.info("Found ${errors.size()} issues in $pr.name")
        logger.info(errors.join(''))
        def ex = new Exception("$pr.agileClass.name $pr.name cannot be promoted to next status. Please perform audit " +
                "Status to get details of the issues preventing status change. ${errors.join()}")
        logger.log(Level.SEVERE, ex.message, ex)
        throw ex
    }
    validateAttachments(pr, logger)
}

void validateAttachments(IChange pr, Logger logger) {
    def missing = checkAttachments(pr, ["Proof"], logger)
    if (missing) {
        throw new Exception("$pr.agileClass.name $pr.name cannot be promoted to next status. " +
                "Attachment of type(s) $missing not found on Proof Review.")
    }
}

def checkAttachments(IDataObject dataObject, List<String> attTypes, Logger logger) {
    def types = dataObject.getTable(TABLE_ATTACHMENTS).collect { IRow r ->
        getVal(r, ATT_ATTACHMENTS_ATTACHMENT_TYPE)
    }
    logger.info("Found files of type $types attached to $dataObject.name")
    attTypes - types
}

List<IItem> getArtWorks(IChange aas) {
    aas.getTable(TABLE_AFFECTEDITEMS).referentIterator.collect { it }
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