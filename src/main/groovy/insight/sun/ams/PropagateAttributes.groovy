package insight.sun.ams

import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.*

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.ChangeConstants.ATT_AFFECTED_ITEMS_ITEM_NUMBER
import static com.agile.api.ChangeConstants.ATT_AFFECTED_ITEMS_REVISION
import static com.agile.api.ChangeConstants.ATT_AFFECTED_ITEMS_LIFECYCLE_PHASE
import static com.agile.api.DataTypeConstants.*
import static com.agile.px.EventConstants.DIRTY_ROW_ACTION_ADD

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
                    if (r.action == DIRTY_ROW_ACTION_ADD) {
                        IRow row = aas.getTable(TABLE_AFFECTEDITEMS).tableIterator
                                .find { IRow row -> row.getValue(ATT_AFFECTED_ITEMS_ITEM_NUMBER) == r.referent.name }
                        RevHelper.updateAFRow(aas, row)
                        awList << row.referent
                    }
                }
            } else if (IWFChangeStatusEventInfo.isAssignableFrom(eventInfo.class)) {
                aas.getTable(TABLE_AFFECTEDITEMS).referentIterator.each { IItem aw ->
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

class RevHelper {

    static void updateAFRow(IChange aas, IRow row) {
        def aw = row.referent as IItem
        def lcPhase = row.getCell(ATT_AFFECTED_ITEMS_LIFECYCLE_PHASE).availableValues
        lcPhase.selection = [aas.getValue('changeCategory').toString()] as Object[]
        String highestRev = getHighestRev(aw.revisions.collect { k, v -> v.toString().replaceAll(/[()?]|Introductory/, '') })
        row.setValues([(ATT_AFFECTED_ITEMS_REVISION)       : getNextRev(highestRev),
                       (ATT_AFFECTED_ITEMS_LIFECYCLE_PHASE): lcPhase])
        aw.refresh()
    }

    static String getNextRev(String rev) {
        rev = rev?.trim()
        if (!rev) return 'A'

        if (rev ==~ /^\d+$/) {
            return (rev.toInteger() + 1).toString().padLeft(rev.length(), '0')
        }

        return numToAlpha(alphaToNum(rev) + 1)
    }

    static String getHighestRev(List<String> revs) {
        if (!revs) return null
        revs = revs.findAll { it?.trim() != '' }
        if (revs.size() == 1) return revs.get(0)

        List<String> numericRevs = revs.findAll { it.isNumber() }
        List<String> alphaRevs = revs.findAll { !it.isNumber() }

        if (!alphaRevs.isEmpty()) {
            return alphaRevs.max { a, b -> compareAlpha(a, b) }
        }

        return numericRevs.max { it.toInteger() }
    }

    static int compareAlpha(String a, String b) {
        return alphaToNum(a) <=> alphaToNum(b)
    }

    static int alphaToNum(String rev) {
        int num = 0
        rev.toUpperCase().each { ch ->
            num = num * 26 + ((ch.charAt(0) - 'A'.charAt(0)) + 1)
        }
        return num
    }

    static String numToAlpha(int num) {
        StringBuilder result = new StringBuilder()
        while (num > 0) {
            num--
            result.insert(0, (char) ('A'.charAt(0) + (num % 26)))
            num = (int) (num / 26)
        }
        return result.toString()
    }
}

