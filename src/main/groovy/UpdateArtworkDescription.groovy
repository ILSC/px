import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IAgileClass
import com.agile.api.IAgileSession
import com.agile.api.IAutoNumber
import com.agile.api.IChange
import com.agile.api.IDataObject
import com.agile.api.IItem
import com.agile.api.IRow
import com.agile.api.ISupplier
import com.agile.api.ITable
import com.agile.px.IObjectEventInfo
import com.agile.px.IUpdateEventInfo
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
        initiateProofReview(obj)
        logger.info('Loading AMS Configuration')
        loadCfg()

        IObjectEventInfo eventInfo = obj.PXEventInfo
        def item = eventInfo.dataObject

        String desc = getDescription(item)
        item.setValue(ATT_TITLE_BLOCK_DESCRIPTION, desc)
        obj.logMonitor('Description Updated')
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to update description on Artwork', ex)
        throw (ex)
    }
}

String getDescription(IDataObject item) {
    def itemCode = ATT_PAGE_THREE_TEXT07
    String desc = null
    ITable pendingChg = item.getTable(TABLE_PENDINGCHANGES)
    if (pendingChg.size()) {
        pendingChg.referentIterator.each { IChange chg ->
            IRow afItmRow = chg.getTable(TABLE_AFFECTEDITEMS).tableIterator
                    .find { IRow r -> r.getValue(ATT_AFFECTED_ITEMS_ITEM_NUMBER) == item.name }
            String itmCode = afItmRow.referent.getTable(TABLE_REDLINEPAGETHREE)[0].getCell(itemCode).value

            if (itmCode) {
                desc = getDescriptionFromSAP(itmCode)
            }
            if (!desc) {
                desc = getValues(item).findAll { it }.join(', ')
            }
            if (itmCode)
                desc = itmCode + ' - ' + desc
        }
    } else {
        String itmCode = item.getValue(itemCode)

        if (itmCode) {
            desc = getDescriptionFromSAP(itmCode)
        }
        if (!desc) {
            desc = getValues(item).findAll { it }.join(', ')
        }
        if (itmCode)
            desc = itmCode + ' - ' + desc

    }
    desc
}


List getValues(IItem item) {
    def productName = ATT_PAGE_THREE_LIST04, strength = ATT_PAGE_THREE_TEXT01, component = ATT_PAGE_TWO_LIST12,
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
            if(out?.MT_ItemMaster)
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

void initiateProofReview(IBaseScriptObj obj){
    Logger logger = JLogger.getLogger('insight.sun.ams.InitiateProofReview')
    try {

        IUpdateEventInfo eventInfo = obj.PXEventInfo
        IItem aw = eventInfo.dataObject

        Integer atrId = aw.agileClass.getAttribute('Page Three.*Printer').id
        def dirtyCell = eventInfo.getCell(atrId)

        if (dirtyCell) {
            if (aw.getValue(ATT_TITLE_BLOCK_REV_RELEASE_DATE) == null)
                throw new Exception('Artwork should have been released before it can be sent to Printing Vendors')

            ITable pendingChanges = aw.getTable(TABLE_PENDINGCHANGES)
            if (pendingChanges.referentIterator.find {
                IChange c -> c.agileClass.isSubclassOf(obj.agileSDKSession.adminInstance.getAgileClass(CLASS_CHANGE_ORDERS_CLASS))
            })
                throw new Exception('Artwork is currently under revision.')

            def newList = dirtyCell.value.selection.collect { it.value }
            def oldPrinterNumbers = aw.getCell(atrId).value.selection.collect { it.value.name }

            def newPrinters = newList.findAll { ISupplier printer -> !oldPrinterNumbers.contains(printer.name) }

            if (newPrinters) {
                IAgileSession session = obj.agileSDKSession
                IAgileClass cls = session.adminInstance.getAgileClass('Proof Review')
                IAutoNumber number = cls.autoNumberSources.first()
                String mfgLocation = aw.getValue('ManufacturingLocation')
                String description = aw.change.getValue(ATT_COVER_PAGE_DESCRIPTION_OF_CHANGE)
                String proofReq = aw.getValue('Page Three.Is Proof Required From Printer')

                newPrinters.each { ISupplier printer ->
                    IChange pc = pendingChanges.referentIterator.find { IChange c ->
                        logger.log(Level.INFO, "classname =${c.agileClass.name}," +
                                "printer =${c.getValue('Printer').toString()},printer from list =${printer.name}")
                        c.agileClass.name == 'Proof Review' && c.getCell('Printer').referent.name == printer.name
                    }

                    if (pc) {
                        obj.logMonitor("Proof Review $pc.name for Printer $printer.name already exisits")
                    } else {
                        IChange change = session.createObject('Proof Review', number.nextNumber)
                        ITable affectedItemTable = change.getTable(TABLE_AFFECTEDITEMS)
                        affectedItemTable.createRow(aw)
                        change.setValue('Printer', printer.name)
                        change.setValue('ManufacturingLocation', mfgLocation)
                        change.setValue(ATT_COVER_PAGE_DESCRIPTION_OF_CHANGE, description)
                        change.setValue('Page Three.Proof Required', proofReq ?: 'No')
                        obj.logMonitor('Proof Review ' + change.name + ' created successfully')
                    }
                }
                session.enableAllWarnings()
            }
        }
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to update printers for Artwork', ex)
        throw (ex)
    }
}