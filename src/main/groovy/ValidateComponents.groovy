import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.ChangeConstants
import com.agile.api.ICell
import com.agile.api.IChange
import com.agile.api.IDataObject
import com.agile.api.IItem
import com.agile.api.IRow
import com.agile.px.ISignOffEventInfo
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.DataTypeConstants.TYPE_MULTILIST
import static com.agile.api.DataTypeConstants.TYPE_SINGLELIST

void invokeScript(IBaseScriptObj obj) {
    Logger logger = JLogger.getLogger('insight.sun.ams.ValidateComponents')
    try {
        ISignOffEventInfo eventInfo = obj.PXEventInfo
        logger.info('Getting AAS from event info')
        IChange aas = eventInfo.dataObject

        logger.info("Validate components on AAS $aas.name")

        List cmpTypes = atrValue(aas,'Page Three.Components Impacted')

        def cmpList = aas.getTable(ChangeConstants.TABLE_AFFECTEDITEMS).referentIterator.collect { IItem i ->
            i.atrValue('Page Three.Component')
        }.unique()

        if (cmpTypes != cmpList) {
            def ex = new Exception("Impacted components list on $aas.agileClass.name $aas.name does not match artworks on affected items table")
            throw ex
        }
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to validate components.', ex)
        throw (ex)
    }
}

def atrValue(IDataObject dObj, def atrId) {
    dObj.getCell(atrId)?.atrValue()
}

def atrValue(IRow row, def atrId) {
    row.getCell(atrId)?.atrValue()
}

def atrValue(ICell cell) {
    if (cell.dataType == TYPE_SINGLELIST) {
        def list = cell.value.selection*.value
        list?.isEmpty() ? null : list.first()
    } else if (cell.dataType == TYPE_MULTILIST) {
        cell.value.selection*.value
    } else {
        cell.value
    }
}