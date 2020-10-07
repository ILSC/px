import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.ISignOffEventInfo
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.CommonConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE
import static com.agile.api.DataTypeConstants.*
import static com.agile.api.ExceptionConstants.*

void invokeScript(IBaseScriptObj obj) {
    Logger logger = JLogger.getLogger('insight.sun.ams.ProofReviewAuditStatus')
    try {
        logger.info('Loading AMS Configuration')
        ISignOffEventInfo eventInfo = obj.PXEventInfo
        logger.info('Getting AAS from event info')
        IChange pr = eventInfo.dataObject
        logger.info("Performing Audit on AAS $pr.name")
        def errors = []
        pr.audit(false).values().flatten().each { APIException e ->
            if (e.errorCode == APDM_MISSINGFIELDS_WARNING) {
                if (!e.message.contains('You have insufficient privileges to resolve this audit issue.'))
                    errors << e.errorCode + ':' + e.message
            } else if (e.errorCode == API_SEE_ROOT_CAUSE) {
                if (e.rootCause)
                    errors << e.errorCode + ':' + e.rootCause.message
            } else if (!(e.errorCode in [APDM_PENDINGCHANGE_ITEM_WARNING, 1099])) {
                errors << e.errorCode + ':' + e.message
            }
        }

        if (errors) {
            logger.info("Found ${errors.size()} issues in $pr.name")
            logger.info(errors.join(''))
            def ex = new Exception("$pr.agileClass.name $pr.name cannot be promoted to next status. Please perform audit Status to get details of the issues preventing status change. ${errors.join()}")
            logger.log(Level.SEVERE, ex.message, ex)
            throw ex
        }
        logger.info("Getting attached artwork from $pr.name")
        def awList = getArtWorks(pr)
        logger.info("Artwork list $awList")
        if (awList?.size() < 1) {
            def ex = new Exception("$pr.agileClass.name $pr.name cannot be promoted to next status. Couldn't find any atrwork attached to the  $pr.name.")
            logger.log(Level.SEVERE, ex.message, ex)
            throw ex
        }
        awList.each { aw ->
            logger.info("Validating attachments on $aw.name")
            validateAttachments(pr, aw, logger)
        }
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to perform audit', ex)
        throw (ex)
    }
}

void validateAttachments(IChange pr, IItem aw, Logger logger) {
    def type = 'Proof'
    def types = aw.attachments.collect { IRow r ->
        logger.info("reading type from attachment row")
        getVal(r, ATT_ATTACHMENTS_ATTACHMENT_TYPE).toString()
    }
    logger.info("Found files of type $types attached to $aw.name, $aw.revision")
    if (!(type in types)) {
        throw new Exception("$pr.agileClass.name $pr.name cannot be promoted to next status. Attachment of type $type was not found on artwork $aw.name.")
    }
}

List<IItem> getArtWorks(IChange pr) {
    pr.getTable(TABLE_AFFECTEDITEMS).referentIterator.collect { it }
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
