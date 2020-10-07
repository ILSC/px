import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.IObjectEventInfo
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.CLASS_CHANGE_ORDERS_CLASS
import static com.agile.api.ItemConstants.TABLE_PENDINGCHANGES

void invokeScript(IBaseScriptObj obj) {
    Logger logger = JLogger.getLogger('insight.sun.ams.InitiateProofReview')
    try {
        IObjectEventInfo eventInfo = obj.PXEventInfo
        IItem aw = eventInfo.dataObject
        if (aw.getValue('revReleaseDate') == null)
            throw new Exception('Artwork should have been released before it can be sent to Printing Vendors')
        ITable pendingChanges = aw.getTable(TABLE_PENDINGCHANGES)

        if (pendingChanges.referentIterator.find {
            IChange c -> c.agileClass.isSubclassOf(obj.agileSDKSession.adminInstance.getAgileClass(CLASS_CHANGE_ORDERS_CLASS))
        })
            throw new Exception('Artwork is currently under revision.')
        IAgileList ml = aw.getCell('Printer').value
        IAgileSession session = obj.getAgileSDKSession()
        IAgileClass cls = session.getAdminInstance().getAgileClass('Proof Review')
        IAutoNumber number = cls.getAutoNumberSources().first()
        String mfgLocation = aw.getValue('ManufacturingLocation')
        String description = aw.getChange().getValue(ChangeConstants.ATT_COVER_PAGE_DESCRIPTION_OF_CHANGE)
        session.disableAllWarnings()
        ml.selection.each {
            IChange pc = pendingChanges.referentIterator.find { IChange c ->
                logger.log(Level.INFO, "classname =${c.agileClass.name},printer =${c.getValue('Printer').toString()},printer from list =${it.value.toString()}")
                c.agileClass.name == 'Proof Review' && c.getValue('Printer').toString() == it.value.toString()
            }
            if (pc) {
                obj.logMonitor("Proof Review $pc.name for Printer $it.value already exisits")
            } else {
                IChange change = session.createObject('Proof Review', number.getNextNumber())
                ITable affectedItemTable = change.getTable(ChangeConstants.TABLE_AFFECTEDITEMS)
                IRow row = affectedItemTable.createRow(aw)
                change.setValue('Printer', it.value)
                change.setValue('ManufacturingLocation', mfgLocation)
                change.setValue(ChangeConstants.ATT_COVER_PAGE_DESCRIPTION_OF_CHANGE, description)
                obj.logMonitor('Proof Review ' + change.name + ' created successfully')
            }

        }
        session.enableAllWarnings()
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to propagate attributes from AAS to Proof', ex)
        throw (ex)
    }
}
