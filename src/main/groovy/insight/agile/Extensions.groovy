package insight.agile

import com.agile.api.ICell
import com.agile.api.IDataObject
import com.agile.api.IRow

import static com.agile.api.DataTypeConstants.TYPE_MULTILIST
import static com.agile.api.DataTypeConstants.TYPE_SINGLELIST

class Extensions {

    static def atrValue(IDataObject dObj, def atrId) {
        dObj.getCell(atrId)?.atrValue()
    }

    static def atrValue(IRow row, def atrId) {
        row.getCell(atrId)?.atrValue()
    }

    static def atrValue(ICell cell) {
        if (cell.dataType == TYPE_SINGLELIST) {
            def list = cell.value.selection*.value
            list?.isEmpty() ? null : list.first()
        } else if (cell.dataType == TYPE_MULTILIST) {
            cell.value.selection*.value
        } else {
            cell.value
        }
    }
}
