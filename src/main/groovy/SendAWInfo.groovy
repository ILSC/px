import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IChange
import com.agile.api.IDataObject
import com.agile.api.IItem
import com.agile.api.IRow
import com.agile.px.IObjectEventInfo
import insight.common.logging.JLogger
import insight.sun.ams.AMSConfiguration

import java.util.logging.Level
import java.util.logging.Logger

import groovy.xml.XmlParser
import groovy.xml.XmlUtil

import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.CommonConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE
import static insight.sun.ams.AMSConfiguration.readKey

void invokeScript(IBaseScriptObj obj) {
    Logger logger = JLogger.getLogger('insight.sun.ams.DeleteOnRejection')
    try {
        IObjectEventInfo eventInfo = obj.PXEventInfo
        def aas = eventInfo.dataObject

        processAAS(aas)
       
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to remove process files on Artworks attached to AAS', ex)
        throw (ex)
    }
}

Iterator<Object> processAAS(IDataObject aas) {
    aas.getTable(TABLE_AFFECTEDITEMS).referentIterator.each { IItem aw ->
        Node xml = new XmlParser().parse(readKey('sendAWInfo.templatePath'))
        def cellList = xml.Worksheet.Table.Row.first().Cell.asList()
        def headerMap = cellList.collectEntries {
            [(it.Data.text()): cellList.indexOf(it)]
        }
        def tbl = xml.Worksheet.Table.first()
        tbl.append(new NodeBuilder().Row { headerMap.collect { k, v -> Cell { Data('ss:Type': 'String', k) } } })
        new File(getParentDir(), aw.name + '.xls') << XmlUtil.serialize(xml)
    }
}


File getParentDir(){
    File file = new File(readKey('sendAWInfo.staging'))
    file.mkdirs()
    file
}
