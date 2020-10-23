import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.IUpdateEventInfo
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.ChangeConstants.CLASS_CHANGE_ORDERS_CLASS
import static com.agile.api.ChangeConstants.ATT_COVER_PAGE_DESCRIPTION_OF_CHANGE
import static com.agile.api.ItemConstants.ATT_TITLE_BLOCK_REV_RELEASE_DATE
import static com.agile.api.ItemConstants.TABLE_PENDINGCHANGES

void invokeScript(IBaseScriptObj obj) {
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

                        logger.info("Setting Printer to $printer")
                        IAgileList printerList = change.getCell('Printer').availableValues
                        printerList.selection = [printer] as Object[]
                        change.setValue('Printer', printerList)

                        logger.info("Setting ManufacturingLocation to $mfgLocation")
                        IAgileList mfgLoc = change.getCell('ManufacturingLocation').availableValues
                        mfgLoc.selection = [mfgLocation] as Object[]
                        change.setValue('ManufacturingLocation', mfgLoc)

                        change.setValue(ATT_COVER_PAGE_DESCRIPTION_OF_CHANGE, description)

                        logger.info("Setting ManufacturingLocation to $mfgLocation")
                        IAgileList proofReqList = change.getCell('Page Three.Proof Required').availableValues
                        proofReqList.selection = [ proofReq ?: 'No'] as Object[]
                        change.setValue('Page Three.Proof Required', proofReqList)
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
