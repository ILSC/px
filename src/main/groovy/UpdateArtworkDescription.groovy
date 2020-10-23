import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IItem
import com.agile.px.IEventDirtyCell
import com.agile.px.IUpdateTitleBlockEventInfo
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.CommonConstants.*
import static com.agile.api.ItemConstants.ATT_TITLE_BLOCK_DESCRIPTION
import static insight.sun.ams.AMSConfiguration.*

void invokeScript(IBaseScriptObj obj) {
    try {
        Logger logger = JLogger.getLogger('insight.sun.ams.ArtworkDescriptionUpdate')

        logger.info('Loading AMS Configuration')
        loadCfg()

        IUpdateTitleBlockEventInfo eventInfo = obj.PXEventInfo
        IItem item = eventInfo.dataObject
        def productName = ATT_PAGE_THREE_LIST04, strength = ATT_PAGE_THREE_TEXT01, component = ATT_PAGE_TWO_LIST12,
            markets = ATT_PAGE_THREE_MULTILIST02, itemCode = ATT_PAGE_THREE_TEXT07

        if (eventInfo.cells*.attributeId.intersect([productName, strength, component, markets, itemCode])) {
            String desc = getDescription(eventInfo, item)
            eventInfo.setCell(ATT_TITLE_BLOCK_DESCRIPTION, desc)
            obj.logMonitor("Description Updated: $desc")
        }

    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to update description on Artwork', ex)
        throw (ex)
    }
}

String getDescription(IUpdateTitleBlockEventInfo eventInfo, IItem item) {
    def itemCode = ATT_PAGE_THREE_TEXT07
    String desc = null
    String itmCode = eventInfo.getCell(itemCode).value

    if (itmCode)
        desc = getDescriptionFromSAP(itmCode)

    if (!desc)
        desc = getValues(eventInfo, item).findAll { it }.join(', ')

    if (itmCode)
        desc = itmCode + ' - ' + desc

    desc
}

List getValues(IUpdateTitleBlockEventInfo eventInfo, IItem item) {
    Integer productName = ATT_PAGE_THREE_LIST04, strength = ATT_PAGE_THREE_TEXT01, component = ATT_PAGE_TWO_LIST12,
            markets = ATT_PAGE_THREE_MULTILIST02
    [getValue(productName, eventInfo, item), getValue(strength, eventInfo, item), getValue(component, eventInfo, item),
     getValue(markets, eventInfo, item)?.toUpperCase()?.replaceAll(';', ' ')]
}

String getValue(Integer atrId, IUpdateTitleBlockEventInfo eventInfo, IItem item) {
    IEventDirtyCell cell = eventInfo.getCell(atrId)

    cell ? cell.value?.toString() : item.getValue(atrId)?.toString()
}

String getDescriptionFromSAP(String itemCode) {
    try {
        URL serviceUrl = (readKey('sap.po.baseUrl') + '/' + readKey('sap.po.itmService')).toURL()
        def authToken = readKey('sap.po.authToken')

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