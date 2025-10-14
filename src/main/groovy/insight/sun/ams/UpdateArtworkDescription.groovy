package insight.sun.ams

import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IAgileClass
import com.agile.api.IItem
import com.agile.px.IEventDirtyCell
import com.agile.px.IUpdateTitleBlockEventInfo
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ItemConstants.ATT_TITLE_BLOCK_DESCRIPTION

class UpdateArtworkDescription {
    private static final Logger logger = Logger.getLogger(UpdateArtworkDescription.class.name)
    private AMSConfig cfg = null

    void invokeScript(IBaseScriptObj obj) {
        Logger logger = Logger.getLogger(UpdateArtworkDescription.class.name)
        try {
            logger.info('Loading AMS Configuration')
            IUpdateTitleBlockEventInfo eventInfo = obj.PXEventInfo as IUpdateTitleBlockEventInfo
            IItem item = eventInfo.dataObject as IItem

            IAgileClass itmClass = item.agileClass
            Map<String, Integer> attrMap = [productName: 'ProductNameBrandName',
                                            genericName: 'GenericName',
                                            strength   : 'Strength',
                                            itemCode   : 'SAPItemCodeMetisItemCode'].collectEntries { k, v ->
                [(k): itmClass.getAttribute(v).id]
            }

            if (eventInfo.cells*.attributeId.intersect(attrMap.values())) {
                String desc = getDescription(eventInfo, item, attrMap, logger)
                item.setValue(ATT_TITLE_BLOCK_DESCRIPTION, desc)
                obj.logMonitor("Description Updated: $desc")
            }
        } catch (Exception ex) {
            obj.logFatal([ex.message, ex.cause?.message].join(' '))
            logger.log(Level.SEVERE, 'Failed to update description on Artwork', ex)
            throw (ex)
        }
    }

    String getDescription(IUpdateTitleBlockEventInfo eventInfo, IItem item, Map<String, Integer> attrMap, Logger logger) {
        String desc = null
        String itmCode = getValue(attrMap.itemCode, eventInfo, item)

        String msg = "Description updating for itemCode: $itmCode, revision: ${item.revision}".toString()
        logger.log(Level.SEVERE, msg)
        item.logAction(msg)
        if (itmCode)
            desc = getDescriptionFromSAP(itmCode)

        if (!desc)
            desc = getValues(eventInfo, item, attrMap).findAll { it }.join(', ')

        if (itmCode)
            desc = itmCode + ' - ' + desc

        desc
    }

    List getValues(IUpdateTitleBlockEventInfo eventInfo, IItem item, Map<String, Integer> attrMap) {
        [getValue(attrMap.productName, eventInfo, item), getValue(attrMap.genericName, eventInfo, item),
         getValue(attrMap.strength, eventInfo, item)]
    }

    String getValue(Integer atrId, IUpdateTitleBlockEventInfo eventInfo, IItem item) {
        IEventDirtyCell cell = eventInfo.getCell(atrId)
        cell?.value?.toString() ?: item.getValue(atrId)?.toString()
    }

    String getDescriptionFromSAP(String itemCode) {
        try {
            URL serviceUrl = (cfg.readKey('sap.po.baseUrl') + '/' + cfg.readKey('sap.po.itmService')).toURL()
            def authToken = cfg.readKey('sap.po.authToken')

            def message = JsonOutput.toJson([Request: [number: itemCode], Response: [number: [], description: []]])

            def post = serviceUrl.openConnection()
            post.requestMethod = 'POST'
            post.doOutput = true
            post.setRequestProperty('Content-Type', 'application/json')
            post.setRequestProperty('Authorization', "Basic $authToken")
            post.outputStream.write(message.getBytes('UTF-8'))

            if (post.responseCode == 200) {
                def out = new JsonSlurper().parse(post.inputStream)
                if (out?.MT_ItemMaster)
                    out?.MT_ItemMaster.Response?.description
                else
                    null
            } else {
                null
            }
        } catch (Exception ex) {
            throw new Exception("Failed to read description for item $itemCode from SAP", ex)
        }
    }
}