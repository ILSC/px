import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IAgileClass
import com.agile.api.IAgileList
import com.agile.api.IAutoNumber
import com.agile.api.ICell
import com.agile.api.IChange
import com.agile.api.IItem
import com.agile.api.IRow
import com.agile.px.IObjectEventInfo
import com.agile.px.IUpdateEventInfo
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.ChangeConstants.ATT_COVER_PAGE_CHANGE_CATEGORY
import static com.agile.api.ChangeConstants.ATT_AFFECTED_ITEMS_LIFECYCLE_PHASE
import static insight.sun.ams.AMSConfiguration.logger

void invokeScript(IBaseScriptObj obj) {
    try {
        Logger logger = JLogger.getLogger('insight.sun.ams.RegistrationToCommercial')
        IUpdateEventInfo eventInfo = obj.PXEventInfo

        IChange aas = eventInfo.dataObject

        Integer atrId = aas.agileClass.getAttribute('Page Three.*Printer').id
        def dirtyCell = eventInfo.getCell(atrId)

        if (dirtyCell) {
            def clsName = dirtyCell.value.toString() == 'No' ? 'ArtworkApprovalSheet' : 'Artwork Approval Sheet-Registration to Commercial'
            IAgileClass agClass = obj.agileSDKSession.adminInstance.getAgileClass(clsName)
            IAutoNumber autoNumber = agClass.autoNumberSources.first()

            aas.saveAs(agClass, autoNumber.nextNumber)
            setValues(aas)
        }
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to remove process files on Artworks attached to AAS', ex)
        throw (ex)
    }
}

void setValues(IChange aas) {
    ICell cell = aas.getCell(ATT_COVER_PAGE_CHANGE_CATEGORY)
    IAgileList list = cell.availableValues
    list.selection = ['Commercial'] as Object[]
    cell.setValue(list)

    aas.getTable(TABLE_AFFECTEDITEMS).each { IRow r ->
        ICell lcCell = r.getCell(ATT_AFFECTED_ITEMS_LIFECYCLE_PHASE)
        IAgileList lcList = lcCell.availableValues
        lcList.selection = ['Production'] as Object[]
        cell.setValue(lcList)
    }
}

