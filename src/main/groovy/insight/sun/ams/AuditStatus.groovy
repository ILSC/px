package insight.sun.ams

import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.IObjectEventInfo
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.CommonConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE
import static com.agile.api.CommonConstants.TABLE_ATTACHMENTS
import static com.agile.api.DataTypeConstants.*
import static com.agile.api.ExceptionConstants.*
import static com.agile.api.ChangeConstants.*
import static com.agile.px.EventConstants.EVENT_APPROVE_FOR_WORKFLOW

class AuditStatus {
    private static final Logger logger = Logger.getLogger(AuditStatus.class.name)
    private AMSConfig cfg = null

    void invokeScript(IBaseScriptObj obj) {
        try {
            cfg = new AMSConfig()
            logger.info('Loading AMS Configuration')

            IObjectEventInfo eventInfo = obj.PXEventInfo as IObjectEventInfo
            logger.info('Getting AAS from event info')
            IChange aas = eventInfo.dataObject as IChange
            logger.info("Performing Audit on AAS $aas.name, status: $aas.status")

            auditAAS(aas, eventInfo.eventType == EVENT_APPROVE_FOR_WORKFLOW, logger)
        } catch (Exception ex) {
            obj.logFatal([ex.message, ex.cause?.message].join(' '))
            logger.log(Level.SEVERE, 'Failed to perform audit', ex)
            throw (ex)
        }
    }

    void auditAAS(IChange aas, boolean isApprovalEvent, Logger logger) {
        def errors = []
        aas.audit(false).values().flatten().each { APIException e ->
            if (e.errorCode == APDM_MISSINGFIELDS_WARNING) {
                if (!e.message.contains('You have insufficient privileges to resolve this audit issue.')) errors << e.errorCode + ':' + e.message
            } else if (e.errorCode == API_SEE_ROOT_CAUSE) {
                if (e.rootCause && !e.message.contains('You have insufficient privileges to resolve this audit issue.') && !e.rootCause.message.contains('You have insufficient privileges to resolve this audit issue.')) errors << e.errorCode + ':' + e.rootCause.message
            } else if (!(e.errorCode in cfg.readKey('warningsToSkip'))) {
                errors << e.errorCode + ':' + e.message
            }
        }

        if (errors) {
            logger.info("Found ${errors.size()} issues in $aas.name")
            logger.info(errors.join(''))
            def ex = new Exception("$aas.agileClass.name $aas.name cannot be promoted to next status. Please perform audit Status to get details of the issues preventing status change. ${errors.join()}")
            logger.log(Level.SEVERE, ex.message, ex)
            throw ex
        }
        def aasInfo = getAASInfo(aas)
        if (isApprovalEvent) {
            logger.info("Getting attached artwork from $aas.name")
            def awList = getArtWorks(aas)

            if (!awList?.size()) {
                def ex = new Exception("$aas.agileClass.name $aas.name cannot be promoted to next status. Couldn't find any atrwork attached to the AAS.")
                logger.log(Level.SEVERE, ex.message, ex)
                throw ex
            }
            validateAttrs(awList, aas, logger)

            validateAttachments(aas, awList, aasInfo, logger)
            if (aas.status.name in ['CF Review', 'QA Review', 'Post Approval CF Review'])
                validateChecklist(aas, awList, aasInfo, logger)
        } else {
            validateAttachments(aas, [], aasInfo, logger)
        }
    }

    Map getAASInfo(IChange aas) {
        [workflow: ATT_COVER_PAGE_WORKFLOW, status: ATT_COVER_PAGE_STATUS, chgCat: ATT_COVER_PAGE_CHANGE_CATEGORY,
         mfgLoc  : 'Page Three.*Manufacturing Location', grid: 'Page Three.Type of Grid',
         markets : 'Page Three.*Market', release: 'Page Three.*Type of Release'].collectEntries { k, v -> [(k): getVal(aas, v)]
        }
    }

    boolean validateAttrs(List<IItem> awList, IChange aas, Logger logger) {
        List attrs = cfg.readKey('propagateAttrs') as List
        String aasClass = aas.agileClass.APIName
        awList.each { aw ->
            String awClass = aw.agileClass.APIName
            logger.info("Validating attributes on $aw.name and $aas.name")
            attrs?.each { atr ->
                if (getVal(aw, atr."$awClass") != getVal(aas, atr."$aasClass")) {
                    logger.info("Value for attribute ${atr."$awClass"} not matching")
                    def ex = new Exception("$aas.agileClass.name $aas.name cannot be promoted to next status. " + "Value for attribute ${atr."$awClass"} on artwork is not matching with AAS")
                    logger.log(Level.SEVERE, ex.message, ex)
                    throw ex
                }
            }
        }
        return true
    }

    void validateChecklist(IChange aas, List<IItem> awList, Map ai, Logger logger) {
        List<String> roles = getRoles(aas.session.currentUser) as List<String>

        if (roles) {
            String coNum = aas.name, status = aas.status.name
            awList?.each { aw ->
                aw.attachments.findAll { IRow r -> getVal(r, ATT_ATTACHMENTS_ATTACHMENT_TYPE) == 'Draft File'
                }.each { IAttachmentRow ar ->
                    def fileId = [ar.referent.name, ar.referent.currentVersion, ar.fileId].join('_')
                    roles.each { role ->
                        if (!isCheckListComplete(coNum, status, role, fileId)) {
                            def ex = new Exception("Checklist not completed for $aw.name, $coNum cannot be promoted to next status.")
                            logger.log(Level.SEVERE, ex.message, ex)
                            throw ex
                        }
                    }
                }
            }
        }
    }

    boolean isCheckListComplete(String aasNum, String aasStatus, String role, String fileId) {
        try {
            URL serviceUrl = (cfg.readKey('ext.baseUrl') + cfg.readKey('ext.api.checklistStatus')).toURL()

            def message = JsonOutput.toJson([
                    coNum        : aasNum,
                    role         : role,
                    workflowState: aasStatus,
                    fileId       : fileId])

            def post = serviceUrl.openConnection()
            post.requestMethod = 'POST'
            post.doOutput = true
            post.setRequestProperty('Content-Type', 'application/json')
            post.outputStream.write(message.getBytes('UTF-8'))

            if (post.responseCode == 200) {
                def out = new JsonSlurper().parse(post.inputStream)
                return out.success && out.result?.exists
            }
            throw new Exception("Failed to get checklist staus for AAS $aasNum. Status Code: $post.responseCode")
        } catch (Exception ex) {
            throw new Exception("Failed to get checklist staus for AAS $aasNum", ex)
        }
    }

    List getMatchingRules(String key, Map ai, Logger logger) {
        logger.info("Looking up rule for workflow $ai.workflow, status $ai.status, change category $ai.chgCat, " + "manufacturing location $ai.mfgLoc, type of grid $ai.grid, type of release $ai.release, markets $ai.markets")
        cfg.readKey(key)?.findAll { rule -> rule.wf == ai.workflow && rule.step == ai.status && rule.chgCat == ai.chgCat && rule.mfg.intersect(ai.mfgLoc) && rule.typeOfGrid == ai.grid && rule.typeOfRelease == ai.release && ((rule.market.in && rule.market.in.intersect(ai.markets)) || (rule.market.notIn && !rule.market.notIn.intersect(ai.markets)))
        }
    }

    void validateAttachments(IChange aas, List<IItem> awList, Map ai, Logger logger) {
        def rules = getMatchingRules('validateAttachments', ai, logger)

        if (rules) {
            List<String> reqAttOnAW = rules*.attOnAW.flatten()

            if (reqAttOnAW) {
                awList?.each { aw ->
                    aw.setRevision(aas)
                    def missing = checkAttachments(aw, reqAttOnAW, logger)
                    if (missing) {
                        throw new Exception("$aas.agileClass.name $aas.name cannot be promoted to next status. " + "Attachment of type(s) $missing not found on artwork $aw.name.")
                    }
                }
            }

            List<String> reqAttOnAAS = rules*.attOnAAS.flatten()
            if (reqAttOnAAS) {
                def missing = checkAttachments(aas, reqAttOnAAS, logger)
                if (missing) {
                    throw new Exception("$aas.agileClass.name $aas.name cannot be promoted to next status. " + "Attachment of type(s) $missing not found on AAS.")
                }
            }
        } else {
            logger.info("No rule defined for for workflow $ai.workflow, status $ai.status, change category $ai.chgCat, " + "manufacturing location $ai.mfgLoc, type of grid $ai.grid, type of release $ai.release, markets $ai.markets")
        }
    }

    def checkAttachments(IDataObject dataObject, List<String> attTypes, Logger logger) {
        def types = dataObject.getTable(TABLE_ATTACHMENTS).collect { IRow r -> getVal(r, ATT_ATTACHMENTS_ATTACHMENT_TYPE)
        }
        logger.info("Found files of type $types attached to $dataObject.name")
        attTypes - types
    }

    List<IItem> getArtWorks(IChange aas) {
        aas.getTable(TABLE_AFFECTEDITEMS).referentIterator.collect { it as IItem }
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

    def getRoles(IUser user) {
        def roles = []
        def config = cfg.readKey('checkListGroups')
        user.getTable(UserConstants.TABLE_USERGROUP).referentIterator.each { IUserGroup ug ->
            if (ug.name in config.raGroups) roles << 'RA'
            if (ug.name in config.qaGroups) roles << 'QA'
            if (ug.name in config.pddGroups) roles << 'PDD'
        }
        roles.unique()
    }
}

class AMSConfig {
    def config
    static Logger logger = Logger.getLogger(AMSConfiguration.class.name)

    AMSConfig() {
        loadCfg('/sw/ams/oracle/spil/a936/agileDomain/config/amsConfig')
    }

    void readConfiguration(File cfgFile) {
        try {
            println "Reading config from $cfgFile.absolutePath"
            config = new JsonSlurper().parse(new FileInputStream(cfgFile))
            println "Sucessfully parsed config file $cfgFile.absolutePath"
        } catch (Exception ex) {
            throw new Exception('Failed to read AMS configuration.', ex)
        }
    }

    private void loadCfg(String path) {
        println "Loading config from file $path"
        File cfgFile = new File(path)
        if (cfgFile.exists() && cfgFile.canRead()) {
            println "Found config file $path"
            readConfiguration(cfgFile)
        } else {
            throw new Exception("Failed to read AMS configuration. Configuration file could not be located at $cfgFile.absolutePath.")
        }
    }

    def readKey(String key, def defaultVal) {
        readKey(key, true) ?: defaultVal
    }

    def readKey(String key, boolean ignoreError = false) {
        def base = config
        try {
            key.split('\\.').each {
                base = base["$it"]
            }
            base
        } catch (Exception ex) {
            String msg = "Failed to read key $key from AMS config"
            logger.severe(msg)
            if (ignoreError) return null else throw (new Exception(msg, ex))
        }
    }
}