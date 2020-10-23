import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.IEventDirtyRowUpdate
import com.agile.px.IUpdateTableEventInfo
import insight.common.logging.JLogger

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.DataTypeConstants.*
import static insight.sun.ams.AMSConfiguration.loadCfg
import static insight.sun.ams.AMSConfiguration.readKey

void invokeScript(IBaseScriptObj obj) {
    Logger logger = JLogger.getLogger('insight.sun.ams.PropagateAttributes')
    try {

        IUpdateTableEventInfo eventInfo = obj.PXEventInfo
        IChange aas = eventInfo.dataObject
        eventInfo.table.iterator().each { IEventDirtyRowUpdate r ->
            if (r.action == 0) {
                IItem aw = r.referent
                copyValues(aas, aw, logger)
                aw.refresh()
            }
        }
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to propagate attributes from AAS to Artwork', ex)
        throw (ex)
    }
}

void copyValues(IDataObject source, IDataObject target, Logger logger) {
    String srcClass = source.agileClass.APIName
    String tarClass = target.agileClass.APIName

    logger.info('Loading AMS Configuration')
    loadCfg()

    readKey('propagateAttrs')?.each { atr ->
        logger.info("Copying attribute ${atr."$srcClass"} from $source.name")
        def val = getVal(source, atr."$srcClass")
        logger.info("Setting attribute ${atr."$srcClass"} on $target.name to $val")
        setVal(target, atr."$tarClass", val)
    }
}

def getVal(IDataObject obj, def atrId) {
    ICell cell = obj.getCell(atrId)

    switch (cell.dataType) {
        case TYPE_INTEGER:
        case TYPE_DOUBLE:
        case TYPE_STRING:
        case TYPE_DATE:
        case TYPE_MONEY:
            return cell.value
        case TYPE_SINGLELIST:
            IAgileList sList = cell.value
            if (cell.attribute.cascadedList) {
                return sList.toString()
            } else {
                return sList.selection?.find { true }
            }
        case TYPE_MULTILIST:
            IAgileList mList = cell.value
            return mList.toString().split(';')
        default:
            return cell.value
    }
}

def setVal(IDataObject obj, def atrId, def atrVal) {
    ICell cell = obj.getCell(atrId)

    switch (cell.dataType) {
        case TYPE_INTEGER:
        case TYPE_DOUBLE:
        case TYPE_STRING:
        case TYPE_DATE:
        case TYPE_MONEY:
            cell.value = atrVal
            break
        case TYPE_SINGLELIST:
            IAgileList sList = cell.availableValues
            sList.selection = (atrVal ? [atrVal] : []) as Object[]
            cell.value = sList
            break
        case TYPE_MULTILIST:
            IAgileList mList = cell.availableValues
            mList.selection = (atrVal ?: []) as Object[]
            cell.value = mList
            break
        default:
            return cell.value
    }
}


