import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IChange
import com.agile.api.IItem
import com.agile.api.IRow
import com.agile.api.ITable
import com.agile.px.IObjectEventInfo
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.*
import static com.agile.api.ItemConstants.*
import static insight.sun.ams.AMSConfiguration.loadCfg
import static insight.sun.ams.AMSConfiguration.logger
import static insight.sun.ams.AMSConfiguration.readKey

void invokeScript(IBaseScriptObj obj) {
    try {
        Logger logger = JLogger.getLogger('insight.sun.ams.ArtworkDescriptionUpdate')

        logger.info('Loading AMS Configuration')
        loadCfg()

        IObjectEventInfo eventInfo = obj.PXEventInfo
        def item = eventInfo.dataObject

        def itemCode = ATT_PAGE_THREE_TEXT07

        ITable pendingChg = item.getTable(TABLE_PENDINGCHANGES)
        if (pendingChg.size()) {
            pendingChg.referentIterator.each { IChange chg ->
                IRow afItmRow = chg.getTable(TABLE_AFFECTEDITEMS).tableIterator
                        .find { IRow r -> r.getValue(ATT_AFFECTED_ITEMS_ITEM_NUMBER) == item.name }
                String itmCode = afItmRow.referent.getTable(TABLE_REDLINEPAGETHREE)[0].getCell(itemCode).value
                def desc = null
                if (itmCode) {
                    desc = getDescriptionFromSAP(itmCode)
                }
                if (!desc) {
                    desc = [getValues(item).findAll { it }.join(', '), itmCode?.toString()].findAll { it }.join(' - ')
                }
                afItmRow.setValue(ATT_AFFECTED_ITEMS_ITEM_DESCRIPTION, desc)
            }
        } else {
            String itmCode = item.getValue(itemCode)

            def desc = null
            if (itmCode) {
                desc = getDescriptionFromSAP(itmCode)
            }
            if (!desc) {
                desc = [getValues(item).findAll { it }.join(', '), itmCode?.toString()].findAll { it }.join(' - ')
            }
            item.setValue(ATT_TITLE_BLOCK_DESCRIPTION, desc)
        }
        obj.logMonitor('Description Updated')
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to update description on Artwork', ex)
        throw (ex)
    }
}

List getValues(IItem item) {
    def productName = ATT_PAGE_THREE_LIST04, strength = ATT_PAGE_THREE_TEXT01, component = ATT_PAGE_THREE_LIST02,
        markets = ATT_PAGE_THREE_MULTILIST02

    [item.getValue(productName)?.toString(), item.getValue(strength)?.toString(), item.getValue(component)?.toString(),
     item.getValue(markets)?.toString()?.toUpperCase()?.replaceAll(';', ' ')]
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
            out?.MT_ItemMaster?.Response?.description
        } else {
            null
        }
    } catch (Exception ex) {
        throw new Exception("Failed to read description for item $itemCode from SAP", ex)
    }
}