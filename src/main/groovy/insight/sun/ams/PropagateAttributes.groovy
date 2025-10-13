package insight.sun.ams

import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.*

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.DataTypeConstants.*

class PropagateAttributes {
    private static final Logger logger = Logger.getLogger(PropagateAttributes.class.name)
    AMSConfig cfg = null

    void invokeScript(IBaseScriptObj obj) {
        try {
            cfg = new AMSConfig()
            def eventInfo = obj.PXEventInfo as IObjectEventInfo
            def aas = eventInfo.dataObject as IChange
            List<IDataObject> awList = []

            if (IUpdateTableEventInfo.isAssignableFrom(eventInfo.class)) {
                eventInfo.table.iterator().each { IEventDirtyRowUpdate r ->
                    if (r.action == EventConstants.DIRTY_ROW_ACTION_ADD) awList << r.referent
                }
            } else if (IWFChangeStatusEventInfo.isAssignableFrom(eventInfo.class)) {
                aas.getTable(ChangeConstants.TABLE_AFFECTEDITEMS).referentIterator.each { IItem aw ->
                    awList << aw
                }
            }

            awList.each { aw ->
                if (aas.getValue('TypeOfRelease')?.toString() == 'New') {
                    copyValues(aas, aw)
                    aw.refresh()
                } else {
                    ICell cell = aw.getCell('TypeOfRelease')
                    if (cell.value.toString() != 'Existing') {
                        def list = cell.availableValues
                        list.selection = ['Existing']
                        cell.value = list
                    }
                    copyValues(aw, aas)
                    aas.refresh()
                }
            }
        } catch (Exception ex) {
            obj.logFatal([ex.message, ex.cause?.message].join(' '))
            logger.log(Level.SEVERE, 'Failed to propagate attributes from AAS to Artwork', ex)
            throw (ex)
        }

    }

    void copyValues(IDataObject source, IDataObject target) {
        String srcClass = source.agileClass.APIName
        String tarClass = target.agileClass.APIName


        Map atrValuesToUpd = [:]

        cfg.readKey('propagateAttrs')?.each { atr ->
            logger.info("Copying attribute ${atr."$srcClass"} from $source.name")
            def val = getVal(source, atr."$srcClass")
            logger.info("Preparing to set attribute ${atr."$srcClass"} on $target.name to $val")
            try {
                atrValuesToUpd << [(atr."$tarClass"): prepVal(target, atr."$tarClass", val)]
            } catch (APIException ex) {
                logger.warning("Failed to set ${atr."$srcClass"} on $target.name to $val. $ex.message")
            }
        }

        setValues(target, atrValuesToUpd)
        target.refresh()
    }

    def setValues(IDataObject co, Map map) {
        try {
            co.setValues(map)
        } catch (APIException ex) {
            if (ex.errorCode == ExceptionConstants.API_ATTRIBUTE_NOT_FOUND) {
                logger.warning(ex.message)
                def m = ex.message =~ /Attribute "(.*)" not found\./
                map.remove(m.matches() ? m[0][1] : '')
                setValues(co, map)
            } else throw ex
        }
    }

    static def getVal(IDataObject obj, def atrId) {
        ICell cell = obj.getCell(atrId)
        switch (cell.dataType) {
            case TYPE_INTEGER:
            case TYPE_DOUBLE:
            case TYPE_STRING:
            case TYPE_DATE:
            case TYPE_MONEY:
                return cell.value
            case TYPE_SINGLELIST:
                IAgileList sList = cell.value as IAgileList
                if (cell.attribute.cascadedList) return sList.toString()
                else return sList.selection?.find { true }
            case TYPE_MULTILIST:
                IAgileList mList = cell.value as IAgileList
                return mList.toString().split(';')
            default:
                return cell.value
        }
    }

    static def prepVal(IDataObject obj, def atrId, def atrVal) {
        ICell cell = obj.getCell(atrId)
        if (cell.dataType == TYPE_SINGLELIST || cell.dataType == TYPE_MULTILIST) {
            IAgileList list = cell.availableValues
            logger.info(cell.name + " value:" + atrVal.toString())
            list.selection = (atrVal ? atrVal.class.isArray() || atrVal instanceof Collection ? atrVal : [atrVal] : []) as Object[]
            return list
        } else {
            return atrVal
        }
    }
}

